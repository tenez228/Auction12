package vn.edu.auction.contract;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import vn.edu.auction.application.AuctionScheduler;
import vn.edu.auction.application.AuctionView;
import vn.edu.auction.application.auth.AuthenticationException;
import vn.edu.auction.application.auth.UserSession;
import vn.edu.auction.application.exception.AuctionNotFoundException;
import vn.edu.auction.application.form.AuctionFormParser;
import vn.edu.auction.application.form.CreateAuctionInput;
import vn.edu.auction.application.form.InputParsers;
import vn.edu.auction.domain.auction.AuctionStatus;
import vn.edu.auction.domain.auction.BidTransaction;
import vn.edu.auction.domain.auction.event.AuctionEvent;
import vn.edu.auction.domain.auction.event.AuctionEventType;
import vn.edu.auction.domain.auction.event.Subscription;
import vn.edu.auction.domain.exception.AuctionClosedException;
import vn.edu.auction.domain.exception.AuthorizationException;
import vn.edu.auction.domain.exception.DataValidationException;
import vn.edu.auction.domain.exception.InvalidAuctionStateException;
import vn.edu.auction.domain.exception.InvalidBidException;
import vn.edu.auction.domain.item.ItemType;
import vn.edu.auction.support.AuctionTestFixture;
import vn.edu.auction.ui.model.UiErrorMapper;

import static vn.edu.auction.support.AuctionTestFixture.END;
import static vn.edu.auction.support.AuctionTestFixture.START;
import static vn.edu.auction.support.AuctionTestFixture.money;

/** Shared executable contracts. JUnit DynamicTest and the offline runner use THESE same cases. */
@SuppressWarnings("try") // Subscriptions are acquired for their close() side effect.
public final class AuctionScenarios {
  @FunctionalInterface
  public interface Check { void run() throws Exception; }
  public record Scenario(String name, Check check) { }

  private AuctionScenarios() {
  }

  public static List<Scenario> all() {
    List<Scenario> cases = new ArrayList<>();
    add(cases, "bid: valid amount updates price, leader, immutable history", f -> {
      BidTransaction bid = f.auction.placeBid(f.bidder, money(150));
      equal(money(150), f.auction.getCurrentPrice());
      equal(f.bidder.getId(), f.auction.getLeadingBidderId().orElseThrow());
      equal(bid, f.auction.getBidHistory().getFirst());
      expect(UnsupportedOperationException.class, () -> f.auction.getBidHistory().clear());
    });
    for (String amount : new String[] {"100", "99", "0", "-1", "100.50"}) {
      add(cases, "bid: reject " + amount + " with InvalidBidException, no mutation", f -> {
        f.auction.refreshStatus();
        var before = f.auction.snapshot();
        expect(InvalidBidException.class, () -> f.auction.placeBid(f.bidder, new BigDecimal(amount)));
        equal(before, f.auction.snapshot());
      });
    }
    add(cases, "bid: null amount has a typed error, not NullPointerException", f -> {
      expect(InvalidBidException.class, () -> f.auction.placeBid(f.bidder, null));
      equal(0, f.auction.getBidHistory().size());
    });
    add(cases, "bid: whole decimal amount is normalized to VND", f -> {
      equal(money(150), f.auction.placeBid(f.bidder, new BigDecimal("150.00")).getAmount());
    });
    add(cases, "bid: invalid input preserves the original validation cause", f -> {
      InvalidBidException ex = expect(InvalidBidException.class,
          () -> f.auction.placeBid(f.bidder, new BigDecimal("150.10")));
      check(ex.getCause() instanceof DataValidationException, "missing validation cause");
    });
    add(cases, "time: before start gives NOT_STARTED", f -> {
      f.clock.set(START.minusNanos(1));
      AuctionClosedException ex = expect(AuctionClosedException.class,
          () -> f.auction.placeBid(f.bidder, money(150)));
      equal(AuctionClosedException.Reason.NOT_STARTED, ex.getReason());
      equal(AuctionStatus.OPEN, f.auction.getStatus());
    });
    add(cases, "time: exact start accepts a bid", f -> {
      f.clock.set(START); f.auction.placeBid(f.bidder, money(150));
      equal(AuctionStatus.RUNNING, f.auction.getStatus());
    });
    add(cases, "time: one nanosecond before end still accepts a bid", f -> {
      f.clock.set(END.minusNanos(1)); f.auction.placeBid(f.bidder, money(150));
      equal(1, f.auction.getBidHistory().size());
    });
    add(cases, "time: exact end rejects without waiting for the scheduler", f -> {
      f.clock.set(END);
      AuctionClosedException ex = expect(AuctionClosedException.class,
          () -> f.auction.placeBid(f.bidder, money(150)));
      equal(AuctionClosedException.Reason.CLOSED, ex.getReason());
      equal(AuctionStatus.FINISHED, f.auction.getStatus());
      equal(0, f.auction.getBidHistory().size());
    });
    add(cases, "time: advancing far beyond the deadline rejects a bid", f -> {
      f.clock.set(END.plusSeconds(100));
      expect(AuctionClosedException.class, () -> f.auction.placeBid(f.bidder, money(150)));
    });
    add(cases, "state: finish chooses the highest accepted bidder", f -> {
      f.auction.placeBid(f.bidder, money(150)); f.auction.placeBid(f.other, money(200));
      f.clock.set(END); check(f.auction.refreshStatus(), "not finished");
      equal(f.other.getId(), f.auction.getWinnerId().orElseThrow());
    });
    add(cases, "state: no accepted bids means no winner", f -> {
      f.clock.set(END); f.auction.refreshStatus();
      check(f.auction.getWinnerId().isEmpty(), "unexpected winner");
      expect(InvalidAuctionStateException.class, () -> f.auction.markPaid(f.seller));
    });
    add(cases, "state: repeated finish does not change version or result", f -> {
      f.auction.placeBid(f.bidder, money(150)); f.clock.set(END); f.auction.refreshStatus();
      var snapshot = f.auction.snapshot();
      check(!f.auction.refreshStatus(), "duplicate transition"); equal(snapshot, f.auction.snapshot());
    });
    add(cases, "state: moving the clock backwards does not reopen a finished auction", f -> {
      f.clock.set(END); f.auction.refreshStatus(); f.clock.set(START);
      expect(AuctionClosedException.class, () -> f.auction.placeBid(f.bidder, money(150)));
      equal(AuctionStatus.FINISHED, f.auction.getStatus());
    });
    add(cases, "state: cannot pay a running auction", f -> {
      f.auction.placeBid(f.bidder, money(150));
      expect(InvalidAuctionStateException.class, () -> f.auction.markPaid(f.seller));
      equal(AuctionStatus.RUNNING, f.auction.getStatus());
    });
    add(cases, "state: seller can pay a finished auction with a winner", f -> {
      f.auction.placeBid(f.bidder, money(150)); f.clock.set(END); f.auction.markPaid(f.seller);
      equal(AuctionStatus.PAID, f.auction.getStatus());
      expect(AuctionClosedException.class, () -> f.auction.placeBid(f.other, money(200)));
    });
    add(cases, "state: bidder cannot confirm payment", f -> {
      f.auction.placeBid(f.bidder, money(150)); f.clock.set(END); f.auction.refreshStatus();
      expect(AuthorizationException.class, () -> f.auction.markPaid(f.bidder));
      equal(AuctionStatus.FINISHED, f.auction.getStatus());
    });
    add(cases, "state: cancellation requires a reason", f -> {
      f.clock.set(END); f.auction.refreshStatus();
      expect(DataValidationException.class, () -> f.auction.cancel(f.seller, "   "));
      equal(AuctionStatus.FINISHED, f.auction.getStatus());
    });
    add(cases, "state: canceled auctions do not accept bids", f -> {
      f.clock.set(END); f.auction.cancel(f.seller, "  No sale  ");
      equal("No sale", f.auction.getCancellationReason().orElseThrow());
      expect(AuctionClosedException.class, () -> f.auction.placeBid(f.bidder, money(150)));
    });
    add(cases, "auth: incorrect password is an AuthenticationException", f -> {
      expect(AuthenticationException.class, () -> f.auth.login("bidder01", "wrong".toCharArray()));
    });
    add(cases, "auth: unknown username gets the same error as wrong password", f -> {
      String a = expect(AuthenticationException.class,
          () -> f.auth.login("bidder01", "wrong".toCharArray())).getMessage();
      String b = expect(AuthenticationException.class,
          () -> f.auth.login("missing", "demo123".toCharArray())).getMessage();
      equal(a, b);
    });
    add(cases, "auth: blank and null credentials have typed failures", f -> {
      expect(AuthenticationException.class, () -> f.auth.login(null, null));
      expect(AuthenticationException.class, () -> f.auth.login(" ", new char[0]));
    });
    add(cases, "auth: missing session cannot access auctions", f -> {
      expect(AuthenticationException.class, () -> f.service.listAuctions(null));
    });
    add(cases, "auth: random token cannot place a bid", f -> {
      AuctionView view = f.create();
      expect(AuthenticationException.class, () -> f.service.placeBid(
          new UserSession(UUID.randomUUID()), view.id(), money(150)));
      equal(0, f.repository.getAuction(view.id()).getBidHistory().size());
    });
    add(cases, "auth: logout invalidates only that session", f -> {
      f.auth.logout(f.bidderSession);
      expect(AuthenticationException.class, () -> f.auth.profile(f.bidderSession));
      check(f.auth.isActive(f.otherSession), "unrelated session was invalidated");
    });
    add(cases, "service: seller cannot act as bidder", f -> {
      AuctionView view = f.create();
      expect(AuthorizationException.class, () -> f.service.placeBid(f.sellerSession, view.id(), money(150)));
    });
    add(cases, "service: unknown auction gives AuctionNotFoundException", f -> {
      expect(AuctionNotFoundException.class, () -> f.service.getAuction(f.bidderSession, UUID.randomUUID()));
    });
    add(cases, "service: null selection gives DataValidationException", f -> {
      expect(DataValidationException.class, () -> f.service.getAuction(f.bidderSession, null));
    });
    add(cases, "service: missing creation data is rejected before registration", f -> {
      expect(DataValidationException.class, () -> f.service.createAuction(
          f.sellerSession, f.item, null, START, END));
      equal(0, f.repository.getAuctions().size());
    });
    add(cases, "service: invalid time range is rejected before registration", f -> {
      expect(DataValidationException.class, () -> f.service.createAuction(
          f.sellerSession, f.item, money(100), END, START));
      equal(0, f.repository.getAuctions().size());
    });
    add(cases, "service: negative starting price is rejected before registration", f -> {
      expect(DataValidationException.class, () -> f.service.createAuction(
          f.sellerSession, f.item, money(-1), START, END));
      equal(0, f.repository.getAuctions().size());
    });
    add(cases, "service: no duplicate active auction for a product", f -> {
      f.create(); expect(InvalidAuctionStateException.class, f::create);
      equal(1, f.repository.getAuctions().size());
    });
    add(cases, "creation: seller identity is derived from the authenticated session", f -> {
      AuctionView view = f.creation.create(f.sellerSession, AuctionTestFixture.input());
      equal(f.seller.getId(), view.sellerId()); equal(AuctionStatus.RUNNING, view.state().status());
    });
    add(cases, "creation: bidder cannot create via the form service", f -> {
      expect(AuthorizationException.class, () -> f.creation.create(f.bidderSession, AuctionTestFixture.input()));
      equal(0, f.repository.getAuctions().size());
    });
    for (ItemType type : ItemType.values()) {
      cases.add(new Scenario("form: accepts " + type + " and chooses the correct Factory", () -> {
        CreateAuctionInput input = input(type, "100", "0", "60", "2020", "100");
        var draft = AuctionFormParser.parse(input);
        equal(type, draft.factory().createItem(UUID.randomUUID(), draft.name(), draft.description()).getType());
      }));
    }
    for (String amount : new String[] {"", "abc", "100.5", "1,000", "-1", "0", "1e3"}) {
      cases.add(new Scenario("input: reject invalid money syntax [" + amount + "]", () ->
          expect(DataValidationException.class, () -> InputParsers.money(amount))));
    }
    cases.add(new Scenario("input: reject null money with DataValidationException", () ->
        expect(DataValidationException.class, () -> InputParsers.money(null))));
    cases.add(new Scenario("input: normalize leading/trailing whitespace", () ->
        equal(money(1000), InputParsers.money("  1000  "))));
    for (String[] time : List.of(new String[] {"-1", "60"}, new String[] {"86401", "60"},
        new String[] {"0", "0"}, new String[] {"0", "86401"}, new String[] {"abc", "60"})) {
      cases.add(new Scenario("form: reject delay=" + time[0] + ", duration=" + time[1], () ->
          expect(DataValidationException.class, () -> AuctionFormParser.parse(
              input(ItemType.ELECTRONICS, "100", time[0], time[1], "6", "0")))));
    }
    cases.add(new Scenario("form: accepts inclusive time limits", () -> {
      var draft = AuctionFormParser.parse(input(ItemType.ART, "100", "86400", "86400", "ignored", "ignored"));
      equal(Duration.ofDays(1), draft.delay()); equal(Duration.ofDays(1), draft.duration());
    }));
    cases.add(new Scenario("form: rejects missing item type", () -> expect(DataValidationException.class,
        () -> AuctionFormParser.parse(input(null, "100", "0", "60", "6", "0")))));
    cases.add(new Scenario("form: rejects negative warranty", () -> expect(DataValidationException.class,
        () -> AuctionFormParser.parse(input(ItemType.ELECTRONICS, "100", "0", "60", "-1", "0")))));
    cases.add(new Scenario("form: rejects overflowing integer field", () -> expect(DataValidationException.class,
        () -> AuctionFormParser.parse(input(ItemType.VEHICLE, "100", "0", "60", "2020", "999999999999")))));
    cases.add(new Scenario("form: rejects blank required name", () -> expect(DataValidationException.class,
        () -> AuctionFormParser.parse(new CreateAuctionInput(ItemType.ART, " ", "D", "A", "M", "", "", "100", "0", "60")))));
    cases.add(new Scenario("UI: known bid error keeps its useful message", () ->
        equal("Bid too low", UiErrorMapper.message(new InvalidBidException("Bid too low")))));
    cases.add(new Scenario("UI: asynchronous wrappers are unwrapped", () ->
        equal("Login required", UiErrorMapper.message(new CompletionException(new AuthenticationException("Login required"))))));
    cases.add(new Scenario("UI: unexpected errors do not leak technical details", () ->
        equal(UiErrorMapper.UNEXPECTED, UiErrorMapper.message(new IllegalStateException("SECRET internal/path")))));
    cases.add(new Scenario("UI: null failure still produces a safe message", () ->
        equal(UiErrorMapper.UNEXPECTED, UiErrorMapper.message(null))));
    add(cases, "observer: accepted bid emits exactly one BID_PLACED event", f -> {
      List<AuctionEvent> events = new ArrayList<>();
      try (Subscription ignored = f.auction.addObserver(events::add)) {
        f.auction.placeBid(f.bidder, money(150));
        equal(1L, events.stream().filter(e -> e.type() == AuctionEventType.BID_PLACED).count());
        equal(money(150), events.getLast().snapshot().currentPrice());
      }
    });
    add(cases, "observer: rejected bid cannot create a false BID_PLACED event", f -> {
      f.auction.refreshStatus(); List<AuctionEvent> events = new ArrayList<>();
      try (Subscription ignored = f.auction.addObserver(events::add)) {
        expect(InvalidBidException.class, () -> f.auction.placeBid(f.bidder, money(100)));
        equal(0, events.size());
      }
    });
    add(cases, "observer: a throwing listener does not roll back an accepted bid", f -> {
      f.auction.addObserver(e -> { throw new IllegalArgumentException("Intentional observer failure"); });
      AtomicInteger delivered = new AtomicInteger();
      f.auction.addObserver(e -> delivered.incrementAndGet());
      f.auction.placeBid(f.bidder, money(150));
      equal(2, delivered.get()); equal(money(150), f.auction.getCurrentPrice());
    });
    add(cases, "observer: unsubscribe stops later events", f -> {
      AtomicInteger delivered = new AtomicInteger();
      Subscription subscription = f.auction.addObserver(e -> delivered.incrementAndGet());
      subscription.close(); f.auction.placeBid(f.bidder, money(150)); equal(0, delivered.get());
    });
    add(cases, "observer: a late rejected bid still publishes the FINISHED transition", f -> {
      f.auction.refreshStatus(); List<AuctionEvent> events = new ArrayList<>();
      f.auction.addObserver(events::add); f.clock.set(END);
      expect(AuctionClosedException.class, () -> f.auction.placeBid(f.bidder, money(150)));
      equal(1, events.size()); equal(AuctionEventType.STATUS_CHANGED, events.getFirst().type());
      equal(AuctionStatus.FINISHED, events.getFirst().snapshot().status());
    });
    cases.add(new Scenario("concurrency: equal bids have exactly one accepted transaction (10 rounds)", () -> {
      for (int round = 0; round < 10; round++) {
        try (AuctionTestFixture f = new AuctionTestFixture()) {
          AtomicInteger accepted = new AtomicInteger();
          together(12, index -> {
            try { f.auction.placeBid(f.bidder, money(150)); accepted.incrementAndGet(); }
            catch (InvalidBidException expected) { /* Only price conflicts may be rejected. */ }
          });
          equal(1, accepted.get()); equal(1, f.auction.getBidHistory().size());
        }
      }
    }));
    cases.add(new Scenario("concurrency: highest bid wins without lost history (10 rounds)", () -> {
      for (int round = 0; round < 10; round++) {
        try (AuctionTestFixture f = new AuctionTestFixture()) {
          AtomicInteger accepted = new AtomicInteger();
          together(16, index -> {
            try { f.auction.placeBid(f.bidder, money(101 + index)); accepted.incrementAndGet(); }
            catch (InvalidBidException expected) { /* A higher bid may already be committed. */ }
          });
          equal(money(116), f.auction.getCurrentPrice());
          equal(accepted.get(), f.auction.getBidHistory().size());
          BigDecimal previous = money(100);
          for (BidTransaction bid : f.auction.getBidHistory()) {
            check(bid.getAmount().compareTo(previous) > 0, "price rollback"); previous = bid.getAmount();
          }
        }
      }
    }));
    cases.add(new Scenario("concurrency: bidding versus closing has one consistent outcome (20 rounds)", () -> {
      for (int round = 0; round < 20; round++) {
        try (AuctionTestFixture f = new AuctionTestFixture()) {
          AtomicInteger accepted = new AtomicInteger();
          together(2, index -> {
            if (index == 0) {
              try { f.auction.placeBid(f.bidder, money(150)); accepted.incrementAndGet(); }
              catch (AuctionClosedException expected) { /* Closing may acquire the lock first. */ }
            } else { f.clock.set(END); f.auction.refreshStatus(); }
          });
          f.auction.refreshStatus(); equal(AuctionStatus.FINISHED, f.auction.getStatus());
          equal(accepted.get(), f.auction.getBidHistory().size());
          equal(accepted.get() == 1, f.auction.getWinnerId().isPresent());
          expect(AuctionClosedException.class, () -> f.auction.placeBid(f.other, money(200)));
        }
      }
    }));
    add(cases, "scheduler: closing and worker cleanup happen without UI involvement", f -> {
      AuctionView view = f.create(); CountDownLatch closed = new CountDownLatch(1);
      f.service.subscribe(f.bidderSession, next -> {
        if (next.id().equals(view.id()) && next.state().status() == AuctionStatus.FINISHED) { closed.countDown(); }
      });
      AuctionScheduler scheduler = new AuctionScheduler(f.service, Duration.ofMillis(10));
      try {
        scheduler.start(); f.clock.set(END);
        check(closed.await(3, TimeUnit.SECONDS), "scheduler did not close");
      } finally {
        scheduler.close();
        check(scheduler.awaitTermination(Duration.ofSeconds(3)), "scheduler thread leaked");
      }
    });
    add(cases, "service: closing subscriptions prevents further UI delivery", f -> {
      AuctionView view = f.create(); AtomicInteger count = new AtomicInteger();
      f.service.subscribe(f.bidderSession, ignored -> count.incrementAndGet());
      f.service.close(); f.repository.getAuction(view.id()).placeBid(f.bidder, money(150));
      equal(0, count.get());
    });
    return List.copyOf(cases);
  }

  private static CreateAuctionInput input(ItemType type, String price, String delay,
      String duration, String numberOne, String numberTwo) {
    return new CreateAuctionInput(type, "Demo", "Description", "Brand/Artist", "Model/Material",
        numberOne, numberTwo, price, delay, duration);
  }

  @FunctionalInterface
  private interface FixtureCheck { void run(AuctionTestFixture fixture) throws Exception; }

  private static void add(List<Scenario> cases, String name, FixtureCheck check) {
    cases.add(new Scenario(name, () -> {
      try (AuctionTestFixture fixture = new AuctionTestFixture()) { check.run(fixture); }
    }));
  }

  private static void together(int count, Consumer<Integer> operation) throws Exception {
    ExecutorService workers = Executors.newFixedThreadPool(count);
    CountDownLatch ready = new CountDownLatch(count);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < count; i++) {
        int index = i;
        futures.add(workers.submit(() -> {
          ready.countDown();
          try {
            if (!start.await(5, TimeUnit.SECONDS)) { throw new AssertionError("start timed out"); }
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new AssertionError("worker interrupted", ex);
          }
          operation.accept(index);
        }));
      }
      check(ready.await(5, TimeUnit.SECONDS), "workers not ready"); start.countDown();
      for (Future<?> future : futures) { future.get(8, TimeUnit.SECONDS); }
    } finally {
      start.countDown(); workers.shutdownNow();
      check(workers.awaitTermination(5, TimeUnit.SECONDS), "worker leak");
    }
  }

  public static <T extends Throwable> T expect(Class<T> type, Check operation) throws Exception {
    try { operation.run(); }
    catch (Throwable ex) {
      if (type.isInstance(ex)) { return type.cast(ex); }
      throw new AssertionError("Expected " + type.getSimpleName() + ", got " + ex, ex);
    }
    throw new AssertionError("Expected " + type.getSimpleName() + " but nothing was thrown");
  }

  private static void equal(Object expected, Object actual) {
    if (!java.util.Objects.equals(expected, actual)) {
      throw new AssertionError("Expected " + expected + " but got " + actual);
    }
  }

  private static void check(boolean value, String message) {
    if (!value) { throw new AssertionError(message); }
  }
}
