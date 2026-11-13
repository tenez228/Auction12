package vn.edu.auction.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import vn.edu.auction.application.auth.DemoAuthService;
import vn.edu.auction.bootstrap.AppContext;
import vn.edu.auction.application.auth.UserSession;
import vn.edu.auction.domain.auction.Auction;
import vn.edu.auction.domain.auction.AuctionStatus;
import vn.edu.auction.domain.auction.event.AuctionEvent;
import vn.edu.auction.domain.auction.event.AuctionEventType;
import vn.edu.auction.domain.auction.event.Subscription;
import vn.edu.auction.domain.factory.ElectronicsFactory;
import vn.edu.auction.domain.item.Item;
import vn.edu.auction.domain.user.Bidder;
import vn.edu.auction.domain.user.Seller;

/** Standalone tests, run explicitly or by the supplied Maven test executions. */
public final class AuctionServiceSelfTest {
  private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");
  private static int passed;
  private static int failed;

  private AuctionServiceSelfTest() {
  }

  public static void main(String[] args) {
    run("Login authenticates four demo roles", () -> {
      try (DemoAuthService auth = new DemoAuthService()) {
        for (String name : auth.usernames()) {
          UserSession session = login(auth, name);
          check(auth.profile(session).username().equals(name), "Wrong profile");
        }
      }
    });
    run("Invalid password and username are rejected", () -> {
      try (DemoAuthService auth = new DemoAuthService()) {
        expect(SecurityException.class, () -> auth.login("bidder01", "wrong".toCharArray()));
        expect(SecurityException.class, () -> auth.login("unknown", "demo123".toCharArray()));
      }
    });
    run("Sessions are distinct; logout revokes only its own token", () -> {
      try (DemoAuthService auth = new DemoAuthService()) {
        UserSession a = login(auth, "bidder01");
        UserSession b = login(auth, "bidder01");
        check(!a.equals(b), "Tokens must differ");
        auth.logout(a);
        expect(SecurityException.class, () -> auth.profile(a));
        check(auth.isActive(b), "Other session was revoked");
        expect(SecurityException.class, () -> auth.profile(new UserSession(UUID.randomUUID())));
      }
    });
    run("Closed authentication service rejects new sessions", () -> {
      DemoAuthService auth = new DemoAuthService();
      UserSession session = login(auth, "admin01");
      auth.close();
      check(!auth.isActive(session), "Session survived close");
      expect(IllegalStateException.class, () -> login(auth, "admin01"));
    });
    run("Observer sees committed bid and matching snapshot", () -> {
      DomainFixture f = new DomainFixture();
      List<AuctionEvent> events = new ArrayList<>();
      f.auction.addObserver(events::add);
      f.auction.placeBid(f.bidder, money(110));
      check(events.size() == 1, "Expected one bid event");
      AuctionEvent e = events.getFirst();
      check(e.type() == AuctionEventType.BID_PLACED, "Wrong event type");
      check(e.bid().orElseThrow().getAmount().compareTo(money(110)) == 0, "Wrong bid");
      check(e.snapshot().bidHistory().size() == 1, "Missing committed bid");
      check(e.snapshot().currentPrice().compareTo(money(110)) == 0, "Wrong snapshot");
    });
    run("Invalid bids do not emit BID_PLACED", () -> {
      DomainFixture f = new DomainFixture();
      List<AuctionEvent> events = new ArrayList<>();
      f.auction.addObserver(events::add);
      expect(IllegalArgumentException.class, () -> f.auction.placeBid(f.bidder, money(100)));
      expect(IllegalArgumentException.class, () -> f.auction.placeBid(f.bidder, money(-1)));
      check(events.isEmpty(), "Rejected bid emitted an event");
      check(f.auction.getBidHistory().isEmpty(), "Rejected bid changed history");
    });
    run("OPEN to RUNNING and bid events have increasing versions", () -> {
      DomainFixture f = new DomainFixture(false);
      List<AuctionEvent> events = new ArrayList<>();
      f.auction.addObserver(events::add);
      f.clock.set(START);
      f.auction.placeBid(f.bidder, money(120));
      check(events.size() == 2, "Expected state then bid");
      check(events.get(0).type() == AuctionEventType.STATUS_CHANGED, "Missing start");
      check(events.get(0).snapshot().bidHistory().isEmpty(), "Start snapshot is mutable");
      check(events.get(1).type() == AuctionEventType.BID_PLACED, "Missing bid");
      ascending(events);
    });
    run("Bid at end time rejected but FINISHED event emitted", () -> {
      DomainFixture f = new DomainFixture();
      List<AuctionEvent> events = new ArrayList<>();
      f.auction.addObserver(events::add);
      f.clock.set(START.plusSeconds(60));
      expect(IllegalStateException.class, () -> f.auction.placeBid(f.bidder, money(150)));
      check(events.size() == 1, "Must emit one closing event");
      check(events.getFirst().snapshot().status() == AuctionStatus.FINISHED, "Not finished");
      check(events.getFirst().bid().isEmpty(), "Closing event carries a bid");
    });
    run("Repeated closing does not duplicate events", () -> {
      DomainFixture f = new DomainFixture();
      List<AuctionEvent> events = new ArrayList<>();
      f.auction.addObserver(events::add);
      f.clock.set(START.plusSeconds(60));
      check(f.auction.refreshStatus(), "First close should change state");
      check(!f.auction.refreshStatus(), "Second close should not change state");
      check(events.size() == 1, "Duplicate closing event");
    });
    run("Payment and cancellation emit state events", () -> {
      DomainFixture paid = new DomainFixture();
      List<AuctionEvent> events = new ArrayList<>();
      paid.auction.addObserver(events::add);
      paid.auction.placeBid(paid.bidder, money(150));
      paid.clock.set(START.plusSeconds(60));
      paid.auction.markPaid(paid.seller);
      check(events.getLast().snapshot().status() == AuctionStatus.PAID, "No paid event");
      DomainFixture canceled = new DomainFixture();
      List<AuctionEvent> canceledEvents = new ArrayList<>();
      canceled.auction.addObserver(canceledEvents::add);
      canceled.clock.set(START.plusSeconds(60));
      canceled.auction.cancel(canceled.seller, "No bids");
      check(canceledEvents.getLast().snapshot().status() == AuctionStatus.CANCELED,
          "No canceled event");
    });
    run("Unsubscribe is idempotent and stops future callbacks", () -> {
      DomainFixture f = new DomainFixture();
      AtomicInteger calls = new AtomicInteger();
      Subscription subscription = f.auction.addObserver(e -> calls.incrementAndGet());
      f.auction.placeBid(f.bidder, money(110));
      subscription.close();
      subscription.close();
      f.auction.placeBid(f.bidder, money(120));
      check(calls.get() == 1, "Unsubscribed observer was notified");
    });
    run("New observers do not replay old events", () -> {
      DomainFixture f = new DomainFixture();
      f.auction.placeBid(f.bidder, money(110));
      List<AuctionEvent> events = new ArrayList<>();
      f.auction.addObserver(events::add);
      check(events.isEmpty(), "Unexpected replay");
      f.auction.placeBid(f.bidder, money(120));
      check(events.size() == 1, "Missing new event");
    });
    run("Observer exception neither undoes bid nor blocks another observer", () -> {
      DomainFixture f = new DomainFixture();
      AtomicInteger received = new AtomicInteger();
      f.auction.addObserver(e -> { throw new IllegalStateException("Expected test fault"); });
      f.auction.addObserver(e -> received.incrementAndGet());
      quiet(Auction.class, () -> f.auction.placeBid(f.bidder, money(130)));
      check(received.get() == 1, "Healthy observer was skipped");
      check(f.auction.getCurrentPrice().compareTo(money(130)) == 0, "Accepted bid undone");
    });
    run("Callback runs outside auction lock", () -> {
      DomainFixture f = new DomainFixture();
      ExecutorService reader = Executors.newSingleThreadExecutor();
      AtomicBoolean success = new AtomicBoolean();
      try {
        f.auction.addObserver(e -> {
          try {
            success.set(reader.submit(() -> f.auction.snapshot().version() >= e.version())
                .get(2, TimeUnit.SECONDS));
          } catch (Exception ex) {
            throw new IllegalStateException("Callback was invoked under lock", ex);
          }
        });
        f.auction.placeBid(f.bidder, money(130));
        check(success.get(), "Reader could not acquire lock while callback was running");
      } finally {
        reader.shutdownNow();
      }
    });
    run("Reentrant callback queues another bid without recursive delivery", () -> {
      DomainFixture f = new DomainFixture();
      List<AuctionEvent> events = new ArrayList<>();
      AtomicInteger depth = new AtomicInteger();
      AtomicInteger maxDepth = new AtomicInteger();
      f.auction.addObserver(e -> {
        int currentDepth = depth.incrementAndGet();
        maxDepth.accumulateAndGet(currentDepth, Math::max);
        events.add(e);
        if (e.snapshot().currentPrice().compareTo(money(110)) == 0) {
          f.auction.placeBid(f.bidder, money(120));
        }
        depth.decrementAndGet();
      });
      f.auction.placeBid(f.bidder, money(110));
      check(events.size() == 2, "Missing reentrant bid event");
      check(maxDepth.get() == 1, "Recursive notification");
      ascending(events);
    });
    run("Slow observer does not block a concurrent bid's commit", () -> {
      DomainFixture f = new DomainFixture();
      CountDownLatch entered = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      List<AuctionEvent> events = Collections.synchronizedList(new ArrayList<>());
      f.auction.addObserver(e -> {
        events.add(e);
        if (e.snapshot().currentPrice().compareTo(money(110)) == 0) {
          entered.countDown();
          await(release);
        }
      });
      ExecutorService pool = Executors.newFixedThreadPool(2);
      try {
        Future<?> first = pool.submit(() -> f.auction.placeBid(f.bidder, money(110)));
        check(entered.await(2, TimeUnit.SECONDS), "First callback did not start");
        Future<?> second = pool.submit(() -> f.auction.placeBid(f.bidder, money(120)));
        second.get(2, TimeUnit.SECONDS);
        check(f.auction.getCurrentPrice().compareTo(money(120)) == 0, "Second commit blocked");
        release.countDown();
        first.get(2, TimeUnit.SECONDS);
        check(events.size() == 2, "Queued notification was lost");
        ascending(events);
      } finally {
        release.countDown();
        pool.shutdownNow();
      }
    });
    run("Unsubscribe suppresses a queued callback not yet started", () -> {
      DomainFixture f = new DomainFixture();
      CountDownLatch entered = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      f.auction.addObserver(e -> { entered.countDown(); await(release); });
      AtomicInteger received = new AtomicInteger();
      Subscription second = f.auction.addObserver(e -> received.incrementAndGet());
      ExecutorService pool = Executors.newSingleThreadExecutor();
      try {
        Future<?> task = pool.submit(() -> f.auction.placeBid(f.bidder, money(110)));
        check(entered.await(2, TimeUnit.SECONDS), "No dispatch");
        second.close();
        release.countDown();
        task.get(2, TimeUnit.SECONDS);
        check(received.get() == 0, "Closed subscription received queued callback");
      } finally {
        release.countDown();
        pool.shutdownNow();
      }
    });
    run("Concurrent equal bids: exactly one acceptance and one notification (20 rounds)", () -> {
      for (int round = 0; round < 20; round++) {
        DomainFixture f = new DomainFixture();
        List<AuctionEvent> events = Collections.synchronizedList(new ArrayList<>());
        f.auction.addObserver(events::add);
        List<Boolean> accepted = parallel(16, () -> {
          try {
            f.auction.placeBid(f.bidder, money(200));
            return true;
          } catch (IllegalArgumentException ex) {
            return false;
          }
        });
        check(accepted.stream().filter(Boolean::booleanValue).count() == 1, "Multiple winners");
        check(events.size() == 1, "Wrong notification count");
        check(f.auction.getBidHistory().size() == 1, "Wrong history size");
      }
    });
    run("Concurrent distinct bids: ordered events, max price, no lost history (20 rounds)", () -> {
      for (int round = 0; round < 20; round++) {
        DomainFixture f = new DomainFixture();
        List<AuctionEvent> events = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger next = new AtomicInteger(100);
        f.auction.addObserver(events::add);
        List<Boolean> accepted = parallel(24, () -> {
          int value = next.incrementAndGet();
          try {
            f.auction.placeBid(f.bidder, money(value));
            return true;
          } catch (IllegalArgumentException ex) {
            return false;
          }
        });
        long acceptedCount = accepted.stream().filter(Boolean::booleanValue).count();
        check(f.auction.getCurrentPrice().compareTo(money(124)) == 0, "Price rolled back");
        check(f.auction.getBidHistory().size() == acceptedCount, "Lost accepted bid");
        check(events.size() == acceptedCount, "Lost or duplicated notification");
        ascending(events);
        for (int i = 1; i < events.size(); i++) {
          check(events.get(i).snapshot().currentPrice()
              .compareTo(events.get(i - 1).snapshot().currentPrice()) > 0, "Out of order price");
        }
      }
    });
    run("Bid and closing race stays consistent (30 rounds)", () -> {
      for (int round = 0; round < 30; round++) {
        DomainFixture f = new DomainFixture();
        List<AuctionEvent> events = Collections.synchronizedList(new ArrayList<>());
        f.auction.addObserver(events::add);
        AtomicInteger index = new AtomicInteger();
        parallel(2, () -> {
          if (index.getAndIncrement() == 0) {
            try {
              f.auction.placeBid(f.bidder, money(200));
            } catch (IllegalStateException expected) {
              // Closing can win the race.
            }
          } else {
            f.clock.set(START.plusSeconds(60));
            f.auction.refreshStatus();
          }
          return true;
        });
        check(f.auction.getStatus() == AuctionStatus.FINISHED, "Still open");
        check(f.auction.getWinnerId().isPresent() == !f.auction.getBidHistory().isEmpty(),
            "Winner/history mismatch");
        ascending(events);
        check(events.getLast().snapshot().status() == AuctionStatus.FINISHED, "Last event not close");
      }
    });
    run("Views and event history do not expose mutable collections", () -> {
      DomainFixture f = new DomainFixture();
      List<AuctionEvent> events = new ArrayList<>();
      f.auction.addObserver(events::add);
      f.auction.placeBid(f.bidder, money(110));
      expect(UnsupportedOperationException.class, () -> events.getFirst().snapshot().bidHistory().clear());
      f.auction.placeBid(f.bidder, money(120));
      check(events.getFirst().snapshot().bidHistory().size() == 1, "Old snapshot changed");
    });
    run("Service creates and lists auctions with read-only data", () -> {
      try (ServiceFixture f = new ServiceFixture()) {
        AuctionView view = f.create();
        check(f.service.listAuctions(f.bidder).size() == 1, "Missing auction");
        check(view.state().status() == AuctionStatus.RUNNING, "Not running");
        expect(UnsupportedOperationException.class, () -> f.service.listAuctions(f.bidder).clear());
        check(f.service.getAuction(f.seller, view.id()).itemId().equals(f.item.getId()), "Wrong item");
      }
    });
    run("Only owning Seller can create an auction", () -> {
      try (ServiceFixture f = new ServiceFixture()) {
        expect(SecurityException.class, () -> f.service.createAuction(
            f.bidder, f.item, money(100), START, START.plusSeconds(60)));
        Item foreign = new ElectronicsFactory("Dell", "Test", 1)
            .createItem(UUID.randomUUID(), "Foreign", "Not owned");
        expect(SecurityException.class, () -> f.service.createAuction(
            f.seller, foreign, money(100), START, START.plusSeconds(60)));
      }
    });
    run("Invalid creation dates and amounts are rejected", () -> {
      try (ServiceFixture f = new ServiceFixture()) {
        expect(IllegalArgumentException.class, () -> f.service.createAuction(
            f.seller, f.item, money(100), START, START));
        expect(IllegalArgumentException.class, () -> f.service.createAuction(
            f.seller, f.item, money(0), START, START.plusSeconds(60)));
        check(f.service.listAuctions(f.seller).isEmpty(), "Invalid auction was registered");
      }
    });
    run("Concurrent creation for one item accepts only one session", () -> {
      try (ServiceFixture f = new ServiceFixture()) {
        List<Boolean> accepted = parallel(12, () -> {
          try {
            f.create();
            return true;
          } catch (IllegalStateException expected) {
            return false;
          }
        });
        check(accepted.stream().filter(Boolean::booleanValue).count() == 1, "Duplicate item auctions");
      }
    });
    run("Multiple services using same manager serialize item creation", () -> {
      try (ServiceFixture f = new ServiceFixture();
           DefaultAuctionService other = new DefaultAuctionService(
               AuctionManager.getInstance(), f.auth, f.clock)) {
        f.create();
        expect(IllegalStateException.class, () -> other.createAuction(
            f.seller, f.item, money(100), START, START.plusSeconds(60)));
      }
    });
    run("Service resolves bidder from session and rejects Seller bid", () -> {
      try (ServiceFixture f = new ServiceFixture()) {
        AuctionView view = f.create();
        expect(SecurityException.class, () -> f.service.placeBid(f.seller, view.id(), money(110)));
        f.service.placeBid(f.bidder, view.id(), money(110));
        check(f.service.getAuction(f.bidder, view.id()).state().leadingBidderId()
            .orElseThrow().equals(f.auth.profile(f.bidder).id()), "Wrong bidder identity");
      }
    });
    run("Logged-out and forged sessions cannot mutate auctions", () -> {
      try (ServiceFixture f = new ServiceFixture()) {
        AuctionView view = f.create();
        f.auth.logout(f.bidder);
        expect(SecurityException.class, () -> f.service.placeBid(f.bidder, view.id(), money(110)));
        expect(SecurityException.class, () -> f.service.placeBid(
            new UserSession(UUID.randomUUID()), view.id(), money(110)));
      }
    });
    run("Unknown auction IDs give a clear error", () -> {
      try (ServiceFixture f = new ServiceFixture()) {
        expect(NoSuchElementException.class, () -> f.service.getAuction(f.bidder, UUID.randomUUID()));
      }
    });
    run("Two active screen subscribers receive accepted bids", () -> {
      try (ServiceFixture f = new ServiceFixture()) {
        AuctionView view = f.create();
        List<AuctionView> first = new ArrayList<>();
        List<AuctionView> second = new ArrayList<>();
        f.service.subscribe(f.bidder, first::add);
        f.service.subscribe(f.seller, second::add);
        f.service.placeBid(f.bidder, view.id(), money(120));
        check(first.size() == 1 && second.size() == 1, "Not all screens updated");
        check(first.getFirst().state().currentPrice().compareTo(money(120)) == 0, "Wrong price");
      }
    });
    run("New auction is announced to an already open list", () -> {
      try (ServiceFixture f = new ServiceFixture()) {
        List<AuctionView> views = new ArrayList<>();
        f.service.subscribe(f.bidder, views::add);
        AuctionView created = f.create();
        check(views.stream().anyMatch(v -> v.id().equals(created.id())), "List did not see creation");
      }
    });
    run("Logged-out screen stops receiving updates", () -> {
      try (ServiceFixture f = new ServiceFixture()) {
        AuctionView view = f.create();
        List<AuctionView> received = new ArrayList<>();
        f.service.subscribe(f.bidder, received::add);
        f.auth.logout(f.bidder);
        UserSession other = login(f.auth, "bidder02");
        f.service.placeBid(other, view.id(), money(110));
        check(received.isEmpty(), "Logged-out screen updated");
      }
    });
    run("Service unsubscribe and close release observers", () -> {
      ServiceFixture f = new ServiceFixture();
      try {
        AuctionView view = f.create();
        AtomicInteger calls = new AtomicInteger();
        Subscription subscription = f.service.subscribe(f.bidder, v -> calls.incrementAndGet());
        subscription.close();
        f.service.placeBid(f.bidder, view.id(), money(110));
        check(calls.get() == 0, "Unsubscribed screen updated");
        f.service.close();
        expect(IllegalStateException.class, () -> f.service.listAuctions(f.seller));
      } finally {
        f.close();
      }
    });
    run("Application observer exception is isolated", () -> {
      try (ServiceFixture f = new ServiceFixture()) {
        AuctionView view = f.create();
        AtomicInteger good = new AtomicInteger();
        f.service.subscribe(f.bidder, v -> { throw new IllegalStateException("Expected test fault"); });
        f.service.subscribe(f.seller, v -> good.incrementAndGet());
        quiet(DefaultAuctionService.class, () -> f.service.placeBid(f.bidder, view.id(), money(120)));
        check(good.get() == 1, "Healthy screen missed event");
      }
    });
    run("Seller can mark winner paid; bidder cannot", () -> {
      try (ServiceFixture f = new ServiceFixture()) {
        AuctionView view = f.create();
        f.service.placeBid(f.bidder, view.id(), money(120));
        f.clock.set(START.plusSeconds(60));
        f.service.refreshAuctions();
        expect(SecurityException.class, () -> f.service.markPaid(f.bidder, view.id()));
        f.service.markPaid(f.seller, view.id());
        check(f.service.getAuction(f.seller, view.id()).state().status() == AuctionStatus.PAID,
            "Payment not recorded");
      }
    });
    run("No-bid finished auction has no winner and can be canceled by Admin", () -> {
      try (ServiceFixture f = new ServiceFixture()) {
        AuctionView view = f.create();
        f.clock.set(START.plusSeconds(60));
        f.service.refreshAuctions();
        check(f.service.getAuction(f.seller, view.id()).state().winnerId().isEmpty(), "Fake winner");
        expect(IllegalStateException.class, () -> f.service.markPaid(f.seller, view.id()));
        UserSession admin = login(f.auth, "admin01");
        f.service.cancel(admin, view.id(), "No buyer");
        check(f.service.getAuction(admin, view.id()).state().status() == AuctionStatus.CANCELED,
            "Cancel failed");
      }
    });
    run("Scheduler advances and closes auctions without any bid", () -> {
      try (ServiceFixture f = new ServiceFixture()) {
        f.clock.set(START.minusSeconds(10));
        AuctionView view = f.create();
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        f.service.subscribe(f.seller, v -> {
          if (v.state().status() == AuctionStatus.RUNNING) { running.countDown(); }
          if (v.state().status() == AuctionStatus.FINISHED) { finished.countDown(); }
        });
        AuctionScheduler scheduler = new AuctionScheduler(f.service, Duration.ofMillis(5));
        try {
          scheduler.start();
          scheduler.start();
          f.clock.set(START);
          check(running.await(3, TimeUnit.SECONDS), "Scheduler never started auction");
          f.clock.set(START.plusSeconds(60));
          check(finished.await(3, TimeUnit.SECONDS), "Scheduler never closed auction");
          check(f.service.getAuction(f.seller, view.id()).state().winnerId().isEmpty(), "Fake winner");
        } finally {
          scheduler.close();
        }
        check(scheduler.awaitTermination(Duration.ofSeconds(3)), "Scheduler thread leaked");
        expect(IllegalStateException.class, scheduler::start);
      }
    });
    run("Scheduler validates interval and closes before start safely", () -> {
      try (ServiceFixture f = new ServiceFixture()) {
        expect(IllegalArgumentException.class,
            () -> new AuctionScheduler(f.service, Duration.ZERO));
        AuctionScheduler scheduler = new AuctionScheduler(f.service, Duration.ofMillis(20));
        scheduler.close();
        scheduler.close();
        expect(IllegalStateException.class, scheduler::start);
      }
    });
    run("Independent auctions do not share their business locks", () -> {
      DomainFixture first = new DomainFixture();
      DomainFixture second = new DomainFixture();
      CountDownLatch entered = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      first.auction.addObserver(e -> { entered.countDown(); await(release); });
      ExecutorService pool = Executors.newFixedThreadPool(2);
      try {
        Future<?> a = pool.submit(() -> first.auction.placeBid(first.bidder, money(120)));
        check(entered.await(2, TimeUnit.SECONDS), "First auction not running");
        pool.submit(() -> second.auction.placeBid(second.bidder, money(130)))
            .get(2, TimeUnit.SECONDS);
        check(second.auction.getCurrentPrice().compareTo(money(130)) == 0, "Other auction blocked");
        release.countDown();
        a.get(2, TimeUnit.SECONDS);
      } finally {
        release.countDown();
        pool.shutdownNow();
      }
    });
    run("Application context seeds demo data and terminates all owned workers", () -> {
      AppContext context = new AppContext(Clock.systemUTC());
      try {
        UserSession bidder = login(context.auth(), "bidder01");
        check(context.auctions().listAuctions(bidder).size() == 3, "Wrong seed count");
        context.workers().submit(() -> "done").get(2, TimeUnit.SECONDS);
      } finally {
        context.close();
      }
      check(context.awaitStopped(Duration.ofSeconds(3)), "Context leaked worker threads");
      context.close();
    });
    System.out.printf("RESULT: %d passed, %d failed.%n", passed, failed);
    if (failed != 0) {
      throw new AssertionError("Auction service tests failed: " + failed);
    }
  }

  private static void ascending(List<AuctionEvent> events) {
    for (int i = 1; i < events.size(); i++) {
      check(events.get(i).version() > events.get(i - 1).version(), "Non-increasing event version");
    }
  }

  private static UserSession login(DemoAuthService auth, String username) {
    return auth.login(username, "demo123".toCharArray());
  }

  private static BigDecimal money(int value) {
    return BigDecimal.valueOf(value);
  }

  private static void run(String name, Checked action) {
    try {
      action.run();
      passed++;
      System.out.println("PASS - " + name);
    } catch (Throwable ex) {
      failed++;
      System.err.println("FAIL - " + name);
      ex.printStackTrace();
    }
  }

  private static void check(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  private static void expect(Class<? extends Throwable> expected, Checked action) throws Exception {
    try {
      action.run();
    } catch (Throwable ex) {
      if (expected.isInstance(ex)) {
        return;
      }
      throw new AssertionError("Expected " + expected.getName() + " but got " + ex, ex);
    }
    throw new AssertionError("Expected " + expected.getName());
  }

  private static void quiet(Class<?> type, Checked action) throws Exception {
    Logger logger = Logger.getLogger(type.getName());
    Level old = logger.getLevel();
    logger.setLevel(Level.OFF);
    try {
      action.run();
    } finally {
      logger.setLevel(old);
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for latch");
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted", ex);
    }
  }

  private static <T> List<T> parallel(int count, Callable<T> action) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(count);
    CountDownLatch ready = new CountDownLatch(count);
    CountDownLatch go = new CountDownLatch(1);
    List<Future<T>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < count; i++) {
        futures.add(pool.submit(() -> { ready.countDown(); await(go); return action.call(); }));
      }
      check(ready.await(5, TimeUnit.SECONDS), "Workers did not become ready");
      go.countDown();
      List<T> results = new ArrayList<>();
      for (Future<T> future : futures) {
        results.add(future.get(10, TimeUnit.SECONDS));
      }
      return results;
    } finally {
      go.countDown();
      pool.shutdownNow();
      check(pool.awaitTermination(5, TimeUnit.SECONDS), "Worker threads leaked");
    }
  }

  @FunctionalInterface
  private interface Checked {
    void run() throws Exception;
  }

  private static final class MutableClock extends Clock {
    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    private MutableClock(Instant now) {
      this(new AtomicReference<>(now), ZoneOffset.UTC);
    }

    private MutableClock(AtomicReference<Instant> now, ZoneId zone) {
      this.now = now;
      this.zone = zone;
    }

    void set(Instant instant) {
      now.set(instant);
    }

    @Override
    public ZoneId getZone() { return zone; }

    @Override
    public Clock withZone(ZoneId newZone) { return new MutableClock(now, newZone); }

    @Override
    public Instant instant() { return now.get(); }
  }

  private static final class DomainFixture {
    private final Seller seller = new Seller("seller", "Seller");
    private final Bidder bidder = new Bidder("bidder", "Bidder");
    private final MutableClock clock;
    private final Auction auction;

    private DomainFixture() { this(true); }

    private DomainFixture(boolean running) {
      clock = new MutableClock(running ? START : START.minusSeconds(1));
      Item item = new ElectronicsFactory("Dell", "Test", 1)
          .createItem(seller.getId(), "Test item", "Test description");
      auction = new Auction(item, money(100), START, START.plusSeconds(60), clock);
      if (running) { auction.refreshStatus(); }
    }
  }

  private static final class ServiceFixture implements AutoCloseable {
    private final DemoAuthService auth = new DemoAuthService();
    private final MutableClock clock = new MutableClock(START);
    private final DefaultAuctionService service =
        new DefaultAuctionService(AuctionManager.getInstance(), auth, clock);
    private final UserSession seller = login(auth, "seller01");
    private final UserSession bidder = login(auth, "bidder01");
    private final Item item = new ElectronicsFactory("Dell", "Test", 1)
        .createItem(auth.profile(seller).id(), "Test item", "Test description");

    AuctionView create() {
      return service.createAuction(seller, item, money(100), START, START.plusSeconds(60));
    }

    @Override
    public void close() {
      service.close();
      auth.close();
    }
  }
}
