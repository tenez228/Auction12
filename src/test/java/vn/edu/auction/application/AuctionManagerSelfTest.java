package vn.edu.auction.application;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import vn.edu.auction.domain.auction.Auction;
import vn.edu.auction.domain.auction.AuctionSnapshot;
import vn.edu.auction.domain.auction.AuctionStatus;
import vn.edu.auction.domain.factory.ElectronicsFactory;
import vn.edu.auction.domain.item.Item;
import vn.edu.auction.domain.user.Bidder;
import vn.edu.auction.domain.user.Seller;

/**
 * Executable checks, not JUnit. Run main in a fresh JVM; no -ea flag is needed.
 * No production clear/reset method is added merely to isolate tests.
 */
public final class AuctionManagerSelfTest {
  private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(START.plusSeconds(1), ZoneOffset.UTC);
  private static final Seller SELLER = new Seller("seller-test", "Test Seller");
  private static final Bidder BIDDER = new Bidder("bidder-test", "Test Bidder");
  private static int passed;

  private AuctionManagerSelfTest() {
  }

  public static void main(String[] args) throws Exception {
    // Keep this first: no other test may initialize the singleton beforehand.
    run("Concurrent first access returns one instance (16 threads)", () -> {
      List<AuctionManager> managers = runTogether(16, index -> AuctionManager.getInstance());
      AuctionManager first = managers.get(0);
      check(first != null, "null instance");
      for (AuctionManager manager : managers) {
        same(first, manager);
      }
    });
    run("Repeated access returns the same instance", () ->
        same(manager(), manager()));
    run("Final class and private zero-argument constructor", () -> {
      check(Modifier.isFinal(AuctionManager.class.getModifiers()), "class is not final");
      var constructors = AuctionManager.class.getDeclaredConstructors();
      equal(1, constructors.length);
      check(Modifier.isPrivate(constructors[0].getModifiers()), "constructor is not private");
      equal(0, constructors[0].getParameterCount());
    });
    run("New JVM starts with an empty registry", () -> {
      equal(0, manager().getAuctionCount());
      check(manager().getAuctions().isEmpty(), "registry is not empty");
      check(manager().getSnapshots().isEmpty(), "snapshots are not empty");
    });
    run("Register, find and get preserve object identity", () -> {
      int before = manager().getAuctionCount();
      Auction auction = registerNew();
      equal(before + 1, manager().getAuctionCount());
      same(auction, manager().findAuction(auction.getId()).orElseThrow());
      same(auction, manager().getAuction(auction.getId()));
    });
    run("Missing ID gives an empty Optional", () ->
        check(manager().findAuction(UUID.randomUUID()).isEmpty(), "missing ID was found"));
    run("Required lookup of missing ID throws", () ->
        expectThrows(NoSuchElementException.class, () -> manager().getAuction(UUID.randomUUID())));
    run("Contains distinguishes known and unknown IDs", () -> {
      Auction auction = registerNew();
      check(manager().containsAuction(auction.getId()), "registered ID missing");
      check(!manager().containsAuction(UUID.randomUUID()), "unknown ID present");
    });
    run("Null registration is rejected without changing count", () -> {
      int before = manager().getAuctionCount();
      expectThrows(NullPointerException.class, () -> manager().registerAuction(null));
      equal(before, manager().getAuctionCount());
    });
    run("Null find ID is rejected", () ->
        expectThrows(NullPointerException.class, () -> manager().findAuction(null)));
    run("Null get ID is rejected", () ->
        expectThrows(NullPointerException.class, () -> manager().getAuction(null)));
    run("Null contains ID is rejected", () ->
        expectThrows(NullPointerException.class, () -> manager().containsAuction(null)));
    run("Registering the same object twice is rejected", () -> {
      Auction auction = registerNew();
      int before = manager().getAuctionCount();
      expectThrows(IllegalArgumentException.class, () -> manager().registerAuction(auction));
      equal(before, manager().getAuctionCount());
      same(auction, manager().getAuction(auction.getId()));
    });
    run("Duplicate ID cannot replace an auction or erase its bids", () -> {
      Auction original = registerNew();
      original.placeBid(BIDDER, money("1100000"));
      Auction differentObject = newAuction(original.getId());
      check(original != differentObject, "fixture must use different objects");
      int before = manager().getAuctionCount();
      expectThrows(IllegalArgumentException.class,
          () -> manager().registerAuction(differentObject));
      same(original, manager().getAuction(original.getId()));
      equal(money("1100000"), manager().getAuction(original.getId()).getCurrentPrice());
      equal(1, manager().getAuction(original.getId()).getBidHistory().size());
      equal(before, manager().getAuctionCount());
    });
    run("Auction list cannot be changed by callers", () -> {
      List<Auction> list = manager().getAuctions();
      expectThrows(UnsupportedOperationException.class, () -> list.add(newAuction()));
      expectThrows(UnsupportedOperationException.class, () -> list.remove(0));
      expectThrows(UnsupportedOperationException.class, list::clear);
    });
    run("Previously returned list membership is detached", () -> {
      List<Auction> oldList = manager().getAuctions();
      int size = oldList.size();
      Auction auction = registerNew();
      equal(size, oldList.size());
      check(oldList.stream().noneMatch(item -> item.getId().equals(auction.getId())),
          "old list gained a new entry");
      equal(size + 1, manager().getAuctions().size());
    });
    run("Auction listing is sorted by UUID and contains no duplicates", () -> {
      List<UUID> ids = manager().getAuctions().stream().map(Auction::getId).toList();
      equal(ids.stream().sorted().toList(), ids);
      equal(ids.size(), new HashSet<>(ids).size());
    });
    run("Read-only lists still contain the canonical mutable auctions", () -> {
      Auction original = registerNew();
      Auction fromList = manager().getAuctions().stream()
          .filter(auction -> auction.getId().equals(original.getId())).findFirst().orElseThrow();
      same(original, fromList);
      fromList.placeBid(BIDDER, money("1100000"));
      equal(money("1100000"), manager().getAuction(original.getId()).getCurrentPrice());
    });
    run("Snapshot list is immutable and covers the registered IDs", () -> {
      List<AuctionSnapshot> snapshots = manager().getSnapshots();
      expectThrows(UnsupportedOperationException.class, snapshots::clear);
      equal(manager().getAuctions().stream().map(Auction::getId).toList(),
          snapshots.stream().map(AuctionSnapshot::auctionId).toList());
    });
    run("Old snapshots and their histories do not change after a new bid", () -> {
      Auction auction = registerNew();
      auction.placeBid(BIDDER, money("1100000"));
      AuctionSnapshot old = snapshotOf(auction.getId());
      auction.placeBid(BIDDER, money("1200000"));
      AuctionSnapshot current = snapshotOf(auction.getId());
      equal(money("1100000"), old.currentPrice());
      equal(1, old.bidHistory().size());
      expectThrows(UnsupportedOperationException.class, old.bidHistory()::clear);
      equal(money("1200000"), current.currentPrice());
      equal(2, current.bidHistory().size());
      check(current.version() > old.version(), "version did not increase");
    });
    run("Manager reads do not silently advance auction state", () -> {
      Auction auction = registerNew();
      equal(AuctionStatus.OPEN, auction.getStatus());
      same(auction, manager().getAuction(auction.getId()));
      equal(AuctionStatus.OPEN, snapshotOf(auction.getId()).status());
      check(auction.refreshStatus(), "explicit refresh should change the state");
      equal(AuctionStatus.RUNNING, snapshotOf(auction.getId()).status());
    });
    run("Concurrent duplicate registration: one success (20 rounds x 16 threads)", () -> {
      for (int round = 0; round < 20; round++) {
        Auction auction = newAuction();
        int before = manager().getAuctionCount();
        List<Boolean> results = runTogether(16, index -> tryRegister(auction));
        equal(1L, results.stream().filter(Boolean::booleanValue).count());
        equal(before + 1, manager().getAuctionCount());
        same(auction, manager().getAuction(auction.getId()));
      }
    });
    run("Concurrent different objects sharing one ID cannot replace winner (20 rounds)", () -> {
      for (int round = 0; round < 20; round++) {
        UUID id = UUID.randomUUID();
        List<Auction> candidates = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
          candidates.add(newAuction(id));
        }
        int before = manager().getAuctionCount();
        List<Boolean> accepted = runTogether(16, index -> tryRegister(candidates.get(index)));
        equal(1L, accepted.stream().filter(Boolean::booleanValue).count());
        Auction winner = candidates.get(accepted.indexOf(Boolean.TRUE));
        same(winner, manager().getAuction(id));
        equal(before + 1, manager().getAuctionCount());
      }
    });
    run("Concurrent unique registrations lose no auctions (128 auctions)", () -> {
      int before = manager().getAuctionCount();
      List<List<Auction>> batches = runTogether(16, index -> {
        List<Auction> batch = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
          batch.add(registerNew());
        }
        return batch;
      });
      equal(before + 128, manager().getAuctionCount());
      for (List<Auction> batch : batches) {
        for (Auction auction : batch) {
          same(auction, manager().getAuction(auction.getId()));
        }
      }
    });
    run("Concurrent registry readers and writers complete without inconsistent entries", () -> {
      int before = manager().getAuctionCount();
      runTogether(16, index -> {
        if (index < 8) {
          for (int i = 0; i < 16; i++) {
            registerNew();
          }
        } else {
          for (int i = 0; i < 12; i++) {
            List<Auction> auctions = manager().getAuctions();
            equal(auctions.stream().sorted(Comparator.comparing(Auction::getId)).toList(), auctions);
            equal(auctions.size(), new HashSet<>(auctions).size());
            for (Auction auction : auctions) {
              same(auction, manager().getAuction(auction.getId()));
            }
            List<AuctionSnapshot> snapshots = manager().getSnapshots();
            List<UUID> ids = snapshots.stream().map(AuctionSnapshot::auctionId).toList();
            equal(ids.size(), new HashSet<>(ids).size());
          }
        }
        return Boolean.TRUE;
      });
      equal(before + 128, manager().getAuctionCount());
    });
    run("Bidders looking up one ID share its lock and history (20 rounds x 16 threads)", () -> {
      for (int round = 0; round < 20; round++) {
        Auction auction = registerNew();
        List<Boolean> accepted = runTogether(16, index -> {
          Auction found = manager().getAuction(auction.getId());
          same(auction, found);
          try {
            found.placeBid(new Bidder("bidder-" + index, "Bidder " + index), money("1100000"));
            return Boolean.TRUE;
          } catch (IllegalArgumentException expected) {
            return Boolean.FALSE;
          }
        });
        equal(1L, accepted.stream().filter(Boolean::booleanValue).count());
        AuctionSnapshot snapshot = snapshotOf(auction.getId());
        equal(money("1100000"), snapshot.currentPrice());
        equal(1, snapshot.bidHistory().size());
      }
    });
    System.out.println("RESULT: " + passed + " passed, 0 failed.");
  }

  private static AuctionManager manager() {
    return AuctionManager.getInstance();
  }

  private static Auction newAuction() {
    return newAuction(UUID.randomUUID());
  }

  private static Auction newAuction(UUID id) {
    Item item = new ElectronicsFactory("Dell", "Test model", 12)
        .createItem(SELLER.getId(), "Test laptop", "Test description");
    return new Auction(id, item, money("1000000"), START, START.plusSeconds(60), CLOCK);
  }

  private static Auction registerNew() {
    Auction auction = newAuction();
    manager().registerAuction(auction);
    return auction;
  }

  private static boolean tryRegister(Auction auction) {
    try {
      manager().registerAuction(auction);
      return true;
    } catch (IllegalArgumentException expected) {
      return false;
    }
  }

  private static AuctionSnapshot snapshotOf(UUID id) {
    return manager().getSnapshots().stream()
        .filter(snapshot -> snapshot.auctionId().equals(id)).findFirst().orElseThrow();
  }

  private static BigDecimal money(String value) {
    return new BigDecimal(value);
  }

  /** A bounded start gate avoids accidentally testing only sequential calls. */
  private static <T> List<T> runTogether(int workers, IntFunction<T> action) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(workers);
    CountDownLatch ready = new CountDownLatch(workers);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<T>> futures = new ArrayList<>();
    try {
      for (int index = 0; index < workers; index++) {
        final int workerIndex = index;
        futures.add(executor.submit(() -> {
          ready.countDown();
          if (!start.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("Start gate timed out");
          }
          return action.apply(workerIndex);
        }));
      }
      if (!ready.await(10, TimeUnit.SECONDS)) {
        throw new AssertionError("Workers did not become ready");
      }
      start.countDown();
      List<T> results = new ArrayList<>();
      for (Future<T> future : futures) {
        results.add(future.get(15, TimeUnit.SECONDS));
      }
      return results;
    } finally {
      start.countDown();
      executor.shutdownNow();
      if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
        throw new AssertionError("Executor did not stop");
      }
    }
  }

  private static void run(String name, CheckedRunnable action) throws Exception {
    try {
      action.run();
      passed++;
      System.out.println("PASS - " + name);
    } catch (Exception | AssertionError error) {
      throw new AssertionError("FAIL - " + name, error);
    }
  }

  private static void equal(Object expected, Object actual) {
    check(Objects.equals(expected, actual), "Expected " + expected + ", got " + actual);
  }

  private static void same(Object expected, Object actual) {
    check(expected == actual, "Different object references");
  }

  private static void check(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  private static void expectThrows(Class<? extends RuntimeException> expected, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException error) {
      if (expected.isInstance(error)) {
        return;
      }
      throw new AssertionError("Wrong exception type: " + error.getClass().getName(), error);
    }
    throw new AssertionError("Expected exception: " + expected.getName());
  }

  @FunctionalInterface
  private interface CheckedRunnable {
    void run() throws Exception;
  }
}
