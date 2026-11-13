package vn.edu.auction.domain;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import vn.edu.auction.domain.auction.Auction;
import vn.edu.auction.domain.exception.InvalidBidException;
import vn.edu.auction.domain.auction.AuctionSnapshot;
import vn.edu.auction.domain.auction.AuctionStatus;
import vn.edu.auction.domain.auction.BidTransaction;
import vn.edu.auction.domain.factory.ArtFactory;
import vn.edu.auction.domain.factory.ElectronicsFactory;
import vn.edu.auction.domain.factory.ItemFactory;
import vn.edu.auction.domain.factory.VehicleFactory;
import vn.edu.auction.domain.item.Art;
import vn.edu.auction.domain.item.Electronics;
import vn.edu.auction.domain.item.Item;
import vn.edu.auction.domain.item.ItemType;
import vn.edu.auction.domain.item.Vehicle;
import vn.edu.auction.domain.user.Admin;
import vn.edu.auction.domain.user.Bidder;
import vn.edu.auction.domain.user.Seller;

/**
 * Chạy main để kiểm tra, KHÔNG phải JUnit.
 * Ném AssertionError khi sai; không cần bật -ea.
 */
public final class DomainModelSelfTest {
  private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");
  private static final Instant END = START.plusSeconds(60);
  private static final Seller SELLER = new Seller("seller", "Seller");
  private static final Bidder ALICE = new Bidder("alice", "Alice");
  private static final Bidder BOB = new Bidder("bob", "Bob");
  private static final Admin ADMIN = new Admin("admin", "Admin");
  private static int passed;
  private static int failed;

  private DomainModelSelfTest() {
  }

  public static void main(String[] args) throws Exception {
    testItems();
    testFactories();
    testBidTransactions();
    testAuctions();
    run("Concurrent same price: 20 rounds, 16 bidders", () -> {
      for (int i = 0; i < 20; i++) {
        concurrentSamePrice();
      }
    });
    run("Concurrent different prices: 20 rounds, 16 bidders", () -> {
      for (int i = 0; i < 20; i++) {
        concurrentDifferentPrices();
      }
    });
    run("Concurrent bid/close: 20 rounds", () -> {
      for (int i = 0; i < 20; i++) {
        concurrentClose();
      }
    });
    run("Snapshot readers see consistent state during bids", DomainModelSelfTest::concurrentRead);
    run("Concurrent payment/cancellation: only one transition", () -> {
      for (int i = 0; i < 20; i++) {
        concurrentSettlement();
      }
    });
    System.out.println("RESULT: " + passed + " passed, " + failed + " failed.");
    if (failed != 0) {
      throw new AssertionError("Some domain checks failed.");
    }
  }

  private static void testItems() {
    run("Electronics fields and normalization", () -> {
      Electronics item = new Electronics(SELLER.getId(), " Laptop ", " Used ", " Dell ", " M1 ", 6);
      equal("Laptop", item.getName());
      equal("Used", item.getDescription());
      equal("Dell", item.getBrand());
      equal("M1", item.getModel());
      equal(6, item.getWarrantyMonths());
      equal(SELLER.getId(), item.getSellerId());
      equal(ItemType.ELECTRONICS, item.getType());
    });
    run("Art fields and normalization", () -> {
      Art item = new Art(SELLER.getId(), " Painting ", " Demo ", " Artist ", " Oil ");
      equal("Artist", item.getArtist());
      equal("Oil", item.getMaterial());
      equal(ItemType.ART, item.getType());
    });
    run("Vehicle fields and normalization", () -> {
      Vehicle item = new Vehicle(SELLER.getId(), " Bike ", " Demo ", " Honda ", " Wave ", 2020, 500);
      equal("Honda", item.getBrand());
      equal("Wave", item.getModel());
      equal(2020, item.getManufactureYear());
      equal(500, item.getMileageKm());
      equal(ItemType.VEHICLE, item.getType());
    });
    run("Item explicit IDs and inherited equality", () -> {
      UUID id = UUID.randomUUID();
      Electronics first = new Electronics(id, SELLER.getId(), "A", "D", "B", "M", 0);
      Electronics same = new Electronics(id, SELLER.getId(), "A2", "D2", "B2", "M2", 1);
      Art otherType = new Art(id, SELLER.getId(), "A", "D", "Artist", "Oil");
      equal(id, first.getId());
      check(first.equals(same), "same concrete type and ID");
      equal(first.hashCode(), same.hashCode());
      check(!first.equals(otherType), "different types cannot be equal");
    });
    run("Fresh items receive different IDs", () -> check(!item().getId().equals(item().getId()), "IDs"));
    run("Null item ID/owner are rejected", () -> {
      expect(NullPointerException.class,
          () -> new Art(null, SELLER.getId(), "N", "D", "A", "M"));
      expect(NullPointerException.class, () -> new Art(null, "N", "D", "A", "M"));
    });
    run("Blank common item fields are rejected", () -> {
      for (String value : new String[] {null, "", " \t "}) {
        expect(IllegalArgumentException.class, () -> new Art(SELLER.getId(), value, "D", "A", "M"));
        expect(IllegalArgumentException.class, () -> new Art(SELLER.getId(), "N", value, "A", "M"));
      }
    });
    run("Subtype validation rejects invalid fields", () -> {
      expect(IllegalArgumentException.class,
          () -> new Electronics(SELLER.getId(), "N", "D", " ", "M", 0));
      expect(IllegalArgumentException.class,
          () -> new Electronics(SELLER.getId(), "N", "D", "B", "M", -1));
      expect(IllegalArgumentException.class,
          () -> new Art(SELLER.getId(), "N", "D", null, "M"));
      expect(IllegalArgumentException.class,
          () -> new Art(SELLER.getId(), "N", "D", "A", " "));
      expect(IllegalArgumentException.class,
          () -> new Vehicle(SELLER.getId(), "N", "D", "B", "M", 0, 0));
      expect(IllegalArgumentException.class,
          () -> new Vehicle(SELLER.getId(), "N", "D", "B", "M", 2020, -1));
    });
    run("Polymorphic item descriptions", () -> {
      List<Item> items = List.of(item(),
          new Art(SELLER.getId(), "N", "D", "ArtistX", "Oil"),
          new Vehicle(SELLER.getId(), "N", "D", "Honda", "Wave", 2020, 0));
      check(items.get(0).describeDetails().contains("M1"), "electronics details");
      check(items.get(1).describeDetails().contains("ArtistX"), "art details");
      check(items.get(2).describeDetails().contains("Wave"), "vehicle details");
      for (Item value : items) {
        check(!value.getType().getLabel().isBlank(), "type label");
        check(value.toString().contains(value.getId().toString()), "toString ID");
      }
    });
    run("Item data fields are private and final", () -> {
      for (Class<?> type : List.of(Item.class, Electronics.class, Art.class, Vehicle.class)) {
        for (var field : type.getDeclaredFields()) {
          if (!field.isSynthetic() && !Modifier.isStatic(field.getModifiers())) {
            check(Modifier.isPrivate(field.getModifiers()), "private " + field);
            check(Modifier.isFinal(field.getModifiers()), "final " + field);
          }
        }
      }
    });
  }

  private static void testFactories() {
    run("ElectronicsFactory produces configured Electronics", () -> {
      Electronics e = new ElectronicsFactory("Dell", "M1", 6)
          .createItem(SELLER.getId(), "Laptop", "Demo");
      equal("Dell", e.getBrand());
      equal("M1", e.getModel());
      equal(6, e.getWarrantyMonths());
    });
    run("ArtFactory produces configured Art", () -> {
      Art art = new ArtFactory("Artist", "Oil").createItem(SELLER.getId(), "N", "D");
      equal("Artist", art.getArtist());
      equal("Oil", art.getMaterial());
    });
    run("VehicleFactory produces configured Vehicle", () -> {
      Vehicle v = new VehicleFactory("Honda", "Wave", 2020, 50)
          .createItem(SELLER.getId(), "N", "D");
      equal("Honda", v.getBrand());
      equal("Wave", v.getModel());
      equal(2020, v.getManufactureYear());
      equal(50, v.getMileageKm());
    });
    run("Factory Method dispatch and explicit IDs", () -> {
      List<ItemFactory<? extends Item>> factories = List.of(
          new ElectronicsFactory("B", "M", 0), new ArtFactory("A", "M"),
          new VehicleFactory("B", "M", 2020, 0));
      List<ItemType> expected = List.of(ItemType.ELECTRONICS, ItemType.ART, ItemType.VEHICLE);
      for (int i = 0; i < factories.size(); i++) {
        UUID id = UUID.randomUUID();
        Item value = factories.get(i).createItem(id, SELLER.getId(), "N", "D");
        equal(id, value.getId());
        equal(expected.get(i), value.getType());
      }
    });
    run("A reusable factory creates independent product IDs", () -> {
      ArtFactory factory = new ArtFactory("A", "M");
      Item first = factory.createItem(SELLER.getId(), "N", "D");
      Item second = factory.createItem(SELLER.getId(), "N", "D");
      check(!first.getId().equals(second.getId()), "independent IDs");
    });
    run("Invalid factory configuration is rejected", () -> {
      expect(IllegalArgumentException.class, () -> new ElectronicsFactory("", "M", 0));
      expect(IllegalArgumentException.class, () -> new ElectronicsFactory("B", "M", -1));
      expect(IllegalArgumentException.class, () -> new ArtFactory(null, "M"));
      expect(IllegalArgumentException.class, () -> new ArtFactory("A", " "));
      expect(IllegalArgumentException.class, () -> new VehicleFactory("B", "M", -1, 0));
      expect(IllegalArgumentException.class, () -> new VehicleFactory("B", "M", 2020, -1));
    });
    run("Factory delegates common field validation", () -> {
      ArtFactory factory = new ArtFactory("A", "M");
      expect(IllegalArgumentException.class, () -> factory.createItem(SELLER.getId(), " ", "D"));
      expect(NullPointerException.class, () -> factory.createItem(null, "N", "D"));
    });
  }

  private static void testBidTransactions() {
    run("BidTransaction retains IDs, amount, timestamp", () -> {
      UUID id = UUID.randomUUID();
      UUID auctionId = UUID.randomUUID();
      BidTransaction bid = new BidTransaction(id, auctionId, ALICE.getId(), money("110.00"), START);
      equal(id, bid.getId());
      equal(auctionId, bid.getAuctionId());
      equal(ALICE.getId(), bid.getBidderId());
      equal(money("110"), bid.getAmount());
      equal(START, bid.getPlacedAt());
    });
    run("BidTransaction rejects zero, negative and fractional VND", () -> {
      for (String amount : List.of("0", "-1", "10.50")) {
        expect(IllegalArgumentException.class,
            () -> new BidTransaction(UUID.randomUUID(), ALICE.getId(), money(amount), START));
      }
    });
    run("BidTransaction rejects null input", () -> {
      expect(NullPointerException.class,
          () -> new BidTransaction(null, ALICE.getId(), money("1"), START));
      expect(NullPointerException.class,
          () -> new BidTransaction(UUID.randomUUID(), null, money("1"), START));
      expect(NullPointerException.class,
          () -> new BidTransaction(UUID.randomUUID(), ALICE.getId(), null, START));
      expect(NullPointerException.class,
          () -> new BidTransaction(UUID.randomUUID(), ALICE.getId(), money("1"), null));
    });
    run("BidTransaction fields are private and final", () -> {
      for (var field : BidTransaction.class.getDeclaredFields()) {
        check(Modifier.isPrivate(field.getModifiers()), "private " + field);
        check(Modifier.isFinal(field.getModifiers()), "final " + field);
      }
    });
  }

  private static void testAuctions() {
    run("New auction initial values", () -> {
      MutableClock clock = new MutableClock(START.minusSeconds(1));
      Item item = item();
      UUID id = UUID.randomUUID();
      Auction auction = new Auction(id, item, money("100"), START, END, clock);
      equal(id, auction.getId());
      equal(item.getId(), auction.getItemId());
      equal(SELLER.getId(), auction.getSellerId());
      equal(money("100"), auction.getStartingPrice());
      equal(money("100"), auction.getCurrentPrice());
      equal(START, auction.getStartTime());
      equal(END, auction.getEndTime());
      equal(AuctionStatus.OPEN, auction.getStatus());
      equal(0L, auction.getVersion());
      check(auction.getLeadingBidderId().isEmpty(), "no leader");
      check(auction.getWinnerId().isEmpty(), "no winner");
      check(auction.getBidHistory().isEmpty(), "empty history");
      check(auction.getCancellationReason().isEmpty(), "no cancellation");
    });
    run("Auction constructor rejects invalid data", () -> {
      MutableClock c = new MutableClock(START);
      expect(NullPointerException.class, () -> new Auction(null, money("1"), START, END, c));
      expect(NullPointerException.class, () -> new Auction(item(), null, START, END, c));
      expect(NullPointerException.class, () -> new Auction(item(), money("1"), null, END, c));
      expect(NullPointerException.class, () -> new Auction(item(), money("1"), START, null, c));
      expect(NullPointerException.class, () -> new Auction(item(), money("1"), START, END, null));
      expect(IllegalArgumentException.class, () -> new Auction(item(), money("0"), START, END, c));
      expect(IllegalArgumentException.class, () -> new Auction(item(), money("1.5"), START, END, c));
      expect(IllegalArgumentException.class, () -> new Auction(item(), money("1"), START, START, c));
      expect(IllegalArgumentException.class, () -> new Auction(item(), money("1"), END, START, c));
    });
    run("Bid before start is rejected", () -> {
      Auction a = auction(new MutableClock(START.minusNanos(1)));
      expect(IllegalStateException.class, () -> a.placeBid(ALICE, money("110")));
      equal(AuctionStatus.OPEN, a.getStatus());
      check(a.getBidHistory().isEmpty(), "history");
    });
    run("Start time is inclusive; bid refreshes status", () -> {
      Auction a = auction(new MutableClock(START));
      BidTransaction bid = a.placeBid(ALICE, money("110"));
      equal(AuctionStatus.RUNNING, a.getStatus());
      equal(START, bid.getPlacedAt());
      equal(a.getId(), bid.getAuctionId());
      equal(ALICE.getId(), bid.getBidderId());
      equal(2L, a.getVersion());
    });
    run("Initial bid must exceed starting price", () -> {
      Auction a = auction(new MutableClock(START));
      a.refreshStatus();
      AuctionSnapshot before = a.snapshot();
      expect(IllegalArgumentException.class, () -> a.placeBid(ALICE, money("100")));
      equal(before, a.snapshot());
    });
    run("Higher bid updates leader, price and history together", () -> {
      Auction a = auction(new MutableClock(START));
      BidTransaction first = a.placeBid(ALICE, money("110"));
      BidTransaction last = a.placeBid(BOB, money("120"));
      equal(money("120"), a.getCurrentPrice());
      equal(Optional.of(BOB.getId()), a.getLeadingBidderId());
      equal(List.of(first, last), a.getBidHistory());
      check(a.getWinnerId().isEmpty(), "leader is not yet winner");
    });
    run("Equal/lower bids do not mutate accepted history", () -> {
      Auction a = auction(new MutableClock(START));
      a.placeBid(ALICE, money("110"));
      AuctionSnapshot before = a.snapshot();
      expect(IllegalArgumentException.class, () -> a.placeBid(BOB, money("110.00")));
      expect(IllegalArgumentException.class, () -> a.placeBid(BOB, money("105")));
      equal(before, a.snapshot());
    });
    run("Bad amounts and null bidder do not enter history", () -> {
      Auction a = auction(new MutableClock(START));
      a.refreshStatus();
      AuctionSnapshot before = a.snapshot();
      for (String amount : List.of("0", "-1", "110.1")) {
        expect(IllegalArgumentException.class, () -> a.placeBid(ALICE, money(amount)));
      }
      expect(InvalidBidException.class, () -> a.placeBid(ALICE, null));
      expect(NullPointerException.class, () -> a.placeBid(null, money("110")));
      equal(before, a.snapshot());
    });
    run("Seller ID cannot bid even through a Bidder instance", () -> {
      Auction a = auction(new MutableClock(START));
      Bidder sameId = new Bidder(SELLER.getId(), "same", "Same ID");
      expect(IllegalArgumentException.class, () -> a.placeBid(sameId, money("110")));
      check(a.getBidHistory().isEmpty(), "no self bid");
    });
    run("Bid just before end is accepted", () -> {
      Auction a = auction(new MutableClock(END.minusNanos(1)));
      a.placeBid(ALICE, money("110"));
      equal(AuctionStatus.RUNNING, a.getStatus());
      equal(1, a.getBidHistory().size());
    });
    run("Exact end is rejected without a scheduler tick", () -> {
      MutableClock c = new MutableClock(START);
      Auction a = auction(c);
      a.placeBid(ALICE, money("110"));
      c.set(END);
      expect(IllegalStateException.class, () -> a.placeBid(BOB, money("120")));
      equal(AuctionStatus.FINISHED, a.getStatus());
      equal(Optional.of(ALICE.getId()), a.getWinnerId());
      equal(1, a.getBidHistory().size());
    });
    run("Expired OPEN auction reaches FINISHED with no winner", () -> {
      Auction a = auction(new MutableClock(END.plusSeconds(10)));
      check(a.refreshStatus(), "changed");
      equal(AuctionStatus.FINISHED, a.getStatus());
      equal(2L, a.getVersion());
      check(a.getWinnerId().isEmpty(), "no invented winner");
    });
    run("Refresh is idempotent within a state", () -> {
      MutableClock c = new MutableClock(START.minusSeconds(1));
      Auction a = auction(c);
      check(!a.refreshStatus(), "before start");
      c.set(START);
      check(a.refreshStatus(), "started");
      check(!a.refreshStatus(), "already running");
      c.set(END);
      check(a.refreshStatus(), "finished");
      long version = a.getVersion();
      check(!a.refreshStatus(), "already finished");
      equal(version, a.getVersion());
    });
    run("Finished auction never reopens after clock moves backward", () -> {
      MutableClock c = new MutableClock(START);
      Auction a = auction(c);
      a.placeBid(ALICE, money("110"));
      c.set(END);
      a.refreshStatus();
      c.set(START);
      expect(IllegalStateException.class, () -> a.placeBid(BOB, money("120")));
      equal(AuctionStatus.FINISHED, a.getStatus());
      equal(Optional.of(ALICE.getId()), a.getWinnerId());
    });
    run("Clock before start cannot accept bid in a previously running state", () -> {
      MutableClock c = new MutableClock(START);
      Auction a = auction(c);
      a.refreshStatus();
      c.set(START.minusSeconds(1));
      expect(IllegalStateException.class, () -> a.placeBid(ALICE, money("110")));
    });
    run("Bid history and snapshot are immutable copies", () -> {
      Auction a = auction(new MutableClock(START));
      a.placeBid(ALICE, money("110"));
      List<BidTransaction> oldHistory = a.getBidHistory();
      AuctionSnapshot old = a.snapshot();
      expect(UnsupportedOperationException.class, oldHistory::clear);
      expect(UnsupportedOperationException.class, () -> old.bidHistory().clear());
      a.placeBid(BOB, money("120"));
      equal(1, oldHistory.size());
      equal(1, old.bidHistory().size());
      equal(money("110"), old.currentPrice());
      equal(2, a.getBidHistory().size());
      check(a.snapshot().version() > old.version(), "version increased");
    });
    run("AuctionSnapshot copies an external list defensively", () -> {
      List<BidTransaction> input = new ArrayList<>();
      AuctionSnapshot snapshot = new AuctionSnapshot(UUID.randomUUID(), money("100"),
          AuctionStatus.OPEN, Optional.empty(), Optional.empty(), input, 0, Optional.empty());
      input.add(new BidTransaction(snapshot.auctionId(), ALICE.getId(), money("110"), START));
      check(snapshot.bidHistory().isEmpty(), "copy cannot change");
    });
    run("No bids means payment is rejected", () -> {
      Auction a = auction(new MutableClock(END));
      expect(IllegalStateException.class, () -> a.markPaid(SELLER));
      equal(AuctionStatus.FINISHED, a.getStatus());
      check(a.getWinnerId().isEmpty(), "no winner");
    });
    run("Owner can mark a finished auction as paid", () -> {
      Auction a = finishedAuction();
      a.markPaid(SELLER);
      equal(AuctionStatus.PAID, a.getStatus());
      equal(Optional.of(ALICE.getId()), a.getWinnerId());
    });
    run("Admin can mark a finished auction as paid", () -> {
      Auction a = finishedAuction();
      a.markPaid(ADMIN);
      equal(AuctionStatus.PAID, a.getStatus());
    });
    run("Other seller and bidder cannot settle auction", () -> {
      Auction a = finishedAuction();
      AuctionSnapshot before = a.snapshot();
      expect(SecurityException.class, () -> a.markPaid(new Seller("other", "Other")));
      expect(SecurityException.class, () -> a.markPaid(ALICE));
      expect(SecurityException.class, () -> a.cancel(BOB, "Reason"));
      expect(SecurityException.class, () -> a.cancel(new Seller("other", "Other"), "Reason"));
      equal(before, a.snapshot());
    });
    run("Cannot pay or cancel an OPEN/RUNNING auction", () -> {
      MutableClock c = new MutableClock(START.minusSeconds(1));
      Auction a = auction(c);
      expect(IllegalStateException.class, () -> a.markPaid(SELLER));
      expect(IllegalStateException.class, () -> a.cancel(SELLER, "Reason"));
      c.set(START);
      a.placeBid(ALICE, money("110"));
      expect(IllegalStateException.class, () -> a.markPaid(SELLER));
      expect(IllegalStateException.class, () -> a.cancel(SELLER, "Reason"));
    });
    run("Cancellation requires a reason and retains the auction result", () -> {
      Auction a = finishedAuction();
      AuctionSnapshot before = a.snapshot();
      expect(IllegalArgumentException.class, () -> a.cancel(SELLER, null));
      expect(IllegalArgumentException.class, () -> a.cancel(SELLER, "  "));
      equal(before, a.snapshot());
      a.cancel(SELLER, "  Buyer withdrew  ");
      equal(AuctionStatus.CANCELED, a.getStatus());
      equal(Optional.of("Buyer withdrew"), a.getCancellationReason());
      equal(Optional.of(ALICE.getId()), a.getWinnerId());
    });
    run("Admin can cancel a no-bid finished auction", () -> {
      Auction a = auction(new MutableClock(END));
      a.cancel(ADMIN, "No bids");
      equal(AuctionStatus.CANCELED, a.getStatus());
      check(a.getWinnerId().isEmpty(), "no winner");
    });
    run("PAID and CANCELED cannot transition again", () -> {
      Auction paid = finishedAuction();
      paid.markPaid(SELLER);
      expect(IllegalStateException.class, () -> paid.markPaid(SELLER));
      expect(IllegalStateException.class, () -> paid.cancel(ADMIN, "Reason"));
      Auction canceled = finishedAuction();
      canceled.cancel(SELLER, "Reason");
      expect(IllegalStateException.class, () -> canceled.markPaid(ADMIN));
      expect(IllegalStateException.class, () -> canceled.cancel(SELLER, "Again"));
      expect(IllegalStateException.class, () -> canceled.placeBid(BOB, money("150")));
    });
    run("Status labels and bidding-closed flags", () -> {
      for (AuctionStatus status : AuctionStatus.values()) {
        check(!status.getLabel().isBlank(), "label");
        boolean expected = status == AuctionStatus.FINISHED
            || status == AuctionStatus.PAID || status == AuctionStatus.CANCELED;
        equal(expected, status.isBiddingClosed());
      }
    });
    run("Two auctions maintain independent prices and histories", () -> {
      Auction a = auction(new MutableClock(START));
      Auction b = auction(new MutableClock(START));
      a.placeBid(ALICE, money("110"));
      b.placeBid(BOB, money("150"));
      equal(money("110"), a.getCurrentPrice());
      equal(money("150"), b.getCurrentPrice());
      check(!a.getId().equals(b.getId()), "different auctions");
      equal(a.getId(), a.getBidHistory().get(0).getAuctionId());
      equal(b.getId(), b.getBidHistory().get(0).getAuctionId());
    });
  }

  private static void concurrentSamePrice() throws Exception {
    MutableClock clock = new MutableClock(START);
    Auction auction = auction(clock);
    List<Callable<Optional<BidTransaction>>> tasks = new ArrayList<>();
    for (int i = 0; i < 16; i++) {
      Bidder bidder = new Bidder("b" + i, "Bidder " + i);
      tasks.add(() -> {
        try {
          return Optional.of(auction.placeBid(bidder, money("110")));
        } catch (IllegalArgumentException expected) {
          return Optional.empty();
        }
      });
    }
    List<Optional<BidTransaction>> results = parallel(tasks);
    equal(1L, results.stream().filter(Optional::isPresent).count());
    equal(1, auction.getBidHistory().size());
    equal(money("110"), auction.getCurrentPrice());
    clock.set(END);
    auction.refreshStatus();
    equal(Optional.of(auction.getBidHistory().get(0).getBidderId()), auction.getWinnerId());
  }

  private static void concurrentDifferentPrices() throws Exception {
    Auction auction = auction(new MutableClock(START));
    List<Callable<Optional<BidTransaction>>> tasks = new ArrayList<>();
    for (int i = 1; i <= 16; i++) {
      Bidder bidder = new Bidder("b" + i, "Bidder " + i);
      BigDecimal price = BigDecimal.valueOf(100L + i);
      tasks.add(() -> {
        try {
          return Optional.of(auction.placeBid(bidder, price));
        } catch (IllegalArgumentException expected) {
          return Optional.empty();
        }
      });
    }
    List<Optional<BidTransaction>> results = parallel(tasks);
    List<BidTransaction> accepted = results.stream().flatMap(Optional::stream).toList();
    equal(money("116"), auction.getCurrentPrice());
    equal(accepted.size(), auction.getBidHistory().size());
    equal(new HashSet<>(accepted), new HashSet<>(auction.getBidHistory()));
    checkSnapshot(auction.snapshot());
  }

  private static void concurrentClose() throws Exception {
    MutableClock clock = new MutableClock(START);
    Auction auction = auction(clock);
    List<Callable<Optional<BidTransaction>>> tasks = new ArrayList<>();
    for (int i = 1; i <= 16; i++) {
      Bidder bidder = new Bidder("b" + i, "Bidder " + i);
      BigDecimal price = BigDecimal.valueOf(100L + i);
      tasks.add(() -> {
        try {
          return Optional.of(auction.placeBid(bidder, price));
        } catch (IllegalArgumentException | IllegalStateException expected) {
          return Optional.empty();
        }
      });
    }
    tasks.add(() -> {
      clock.set(END);
      auction.refreshStatus();
      return Optional.empty();
    });
    List<Optional<BidTransaction>> results = parallel(tasks);
    equal(AuctionStatus.FINISHED, auction.getStatus());
    equal(results.stream().filter(Optional::isPresent).count(), (long) auction.getBidHistory().size());
    for (BidTransaction bid : auction.getBidHistory()) {
      check(!bid.getPlacedAt().isBefore(START), "before start");
      check(bid.getPlacedAt().isBefore(END), "late accepted bid");
    }
    checkSnapshot(auction.snapshot());
    equal(auction.getLeadingBidderId(), auction.getWinnerId());
    expect(IllegalStateException.class, () -> auction.placeBid(ALICE, money("1000")));
  }

  private static void concurrentRead() throws Exception {
    Auction auction = auction(new MutableClock(START));
    List<Callable<Void>> tasks = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      int offset = i;
      Bidder bidder = new Bidder("b" + i, "Bidder " + i);
      tasks.add(() -> {
        for (int j = 0; j < 100; j++) {
          try {
            auction.placeBid(bidder, BigDecimal.valueOf(101L + j * 8L + offset));
          } catch (IllegalArgumentException expected) {
            // A higher price from a competing worker may have been accepted first.
          }
        }
        return null;
      });
    }
    tasks.add(() -> {
      long previousVersion = -1;
      for (int j = 0; j < 2000; j++) {
        AuctionSnapshot snapshot = auction.snapshot();
        check(snapshot.version() >= previousVersion, "snapshot version rollback");
        previousVersion = snapshot.version();
        checkSnapshot(snapshot);
      }
      return null;
    });
    parallel(tasks);
    equal(money("900"), auction.getCurrentPrice());
    checkSnapshot(auction.snapshot());
  }

  private static void concurrentSettlement() throws Exception {
    Auction auction = finishedAuction();
    List<Callable<Boolean>> tasks = List.of(
        () -> {
          try {
            auction.markPaid(SELLER);
            return true;
          } catch (IllegalStateException expected) {
            return false;
          }
        },
        () -> {
          try {
            auction.cancel(ADMIN, "Reason");
            return true;
          } catch (IllegalStateException expected) {
            return false;
          }
        });
    equal(1L, parallel(tasks).stream().filter(Boolean::booleanValue).count());
    check(auction.getStatus() == AuctionStatus.PAID || auction.getStatus() == AuctionStatus.CANCELED,
        "one final state");
  }

  private static void checkSnapshot(AuctionSnapshot snapshot) {
    BigDecimal previous = money("100");
    for (BidTransaction bid : snapshot.bidHistory()) {
      equal(snapshot.auctionId(), bid.getAuctionId());
      check(bid.getAmount().compareTo(previous) > 0, "history price rollback");
      previous = bid.getAmount();
    }
    equal(previous, snapshot.currentPrice());
    if (snapshot.bidHistory().isEmpty()) {
      check(snapshot.leadingBidderId().isEmpty(), "leader without bid");
    } else {
      BidTransaction last = snapshot.bidHistory().get(snapshot.bidHistory().size() - 1);
      equal(Optional.of(last.getBidderId()), snapshot.leadingBidderId());
    }
  }

  /** All submitted workers reach the barrier; every future is observed with a timeout. */
  private static <T> List<T> parallel(List<Callable<T>> tasks) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
    CountDownLatch ready = new CountDownLatch(tasks.size());
    CountDownLatch start = new CountDownLatch(1);
    List<Future<T>> futures = new ArrayList<>();
    try {
      for (Callable<T> task : tasks) {
        futures.add(pool.submit(() -> {
          ready.countDown();
          if (!start.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("Start barrier timed out.");
          }
          return task.call();
        }));
      }
      check(ready.await(10, TimeUnit.SECONDS), "Workers did not become ready.");
      start.countDown();
      List<T> results = new ArrayList<>();
      for (Future<T> future : futures) {
        results.add(future.get(10, TimeUnit.SECONDS));
      }
      return results;
    } finally {
      start.countDown();
      pool.shutdownNow();
      check(pool.awaitTermination(10, TimeUnit.SECONDS), "Executor did not stop.");
    }
  }

  private static Item item() {
    return new Electronics(SELLER.getId(), "Laptop", "Demo", "Dell", "M1", 0);
  }

  private static Auction auction(Clock clock) {
    return new Auction(item(), money("100"), START, END, clock);
  }

  private static Auction finishedAuction() {
    MutableClock clock = new MutableClock(START);
    Auction auction = auction(clock);
    auction.placeBid(ALICE, money("110"));
    clock.set(END);
    auction.refreshStatus();
    return auction;
  }

  private static BigDecimal money(String value) {
    return new BigDecimal(value);
  }

  private static void equal(Object expected, Object actual) {
    if (!Objects.equals(expected, actual)) {
      throw new AssertionError("Expected " + expected + " but got " + actual);
    }
  }

  private static void check(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  private static void expect(Class<? extends Throwable> type, CheckedRunnable action) {
    try {
      action.run();
    } catch (Throwable error) {
      if (type.isInstance(error)) {
        return;
      }
      throw new AssertionError("Expected " + type.getSimpleName() + ", got " + error, error);
    }
    throw new AssertionError("Expected " + type.getSimpleName() + " but nothing was thrown.");
  }

  private static void run(String name, CheckedRunnable action) {
    try {
      action.run();
      passed++;
      System.out.println("PASS - " + name);
    } catch (Throwable error) {
      failed++;
      System.err.println("FAIL - " + name);
      error.printStackTrace(System.err);
    }
  }

  @FunctionalInterface
  private interface CheckedRunnable {
    void run() throws Exception;
  }

  private static final class MutableClock extends Clock {
    private final AtomicReference<Instant> current;
    private final ZoneId zone;

    MutableClock(Instant initial) {
      this(new AtomicReference<>(Objects.requireNonNull(initial)), ZoneOffset.UTC);
    }

    private MutableClock(AtomicReference<Instant> current, ZoneId zone) {
      this.current = current;
      this.zone = Objects.requireNonNull(zone);
    }

    void set(Instant instant) {
      current.set(Objects.requireNonNull(instant));
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
      return new MutableClock(current, newZone);
    }

    @Override
    public Instant instant() {
      return current.get();
    }
  }
}
