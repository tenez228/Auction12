package vn.edu.auction.bootstrap;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import vn.edu.auction.application.AuctionManager;
import vn.edu.auction.application.AuctionScheduler;
import vn.edu.auction.application.AuctionService;
import vn.edu.auction.application.DefaultAuctionService;
import vn.edu.auction.application.auth.DemoAuthService;
import vn.edu.auction.application.auth.UserSession;
import vn.edu.auction.domain.factory.ArtFactory;
import vn.edu.auction.domain.factory.ElectronicsFactory;
import vn.edu.auction.domain.factory.VehicleFactory;
import vn.edu.auction.domain.item.Item;

/** One context shared by all windows of this running desktop application. */
public final class AppContext implements AutoCloseable {
  private final Clock clock;
  private final DemoAuthService auth = new DemoAuthService();
  private final AuctionService auctions;
  private final AuctionScheduler scheduler;
  private final AtomicInteger workerNumber = new AtomicInteger();
  private final ExecutorService workers = Executors.newFixedThreadPool(4,
      runnable -> new Thread(runnable, "auction-ui-worker-" + workerNumber.incrementAndGet()));
  private final AtomicBoolean closed = new AtomicBoolean();

  public AppContext(Clock clock) {
    this.clock = Objects.requireNonNull(clock);
    auctions = new DefaultAuctionService(AuctionManager.getInstance(), auth, clock);
    scheduler = new AuctionScheduler(auctions, Duration.ofMillis(200));
    try {
      seedDemo();
      scheduler.start();
    } catch (RuntimeException ex) {
      close();
      throw ex;
    }
  }

  private void seedDemo() {
    UserSession seller = auth.login("seller01", "demo123".toCharArray());
    try {
      var sellerId = auth.profile(seller).id();
      Instant now = clock.instant();
      Item laptop = new ElectronicsFactory("Dell", "Latitude 5420", 6)
          .createItem(sellerId, "Laptop học tập", "Máy demo cho phiên đấu giá.");
      Item painting = new ArtFactory("Tác giả demo", "Sơn dầu")
          .createItem(sellerId, "Tranh phong cảnh", "Tác phẩm minh họa.");
      Item bike = new VehicleFactory("Honda", "Wave", 2020, 15000)
          .createItem(sellerId, "Xe máy demo", "Dữ liệu thực hành, không giao dịch thật.");
      auctions.createAuction(seller, laptop, new BigDecimal("1000000"),
          now.minusSeconds(1), now.plusSeconds(180));
      auctions.createAuction(seller, painting, new BigDecimal("500000"),
          now.plusSeconds(20), now.plusSeconds(200));
      auctions.createAuction(seller, bike, new BigDecimal("2000000"),
          now.plusSeconds(5), now.plusSeconds(60));
    } finally {
      auth.logout(seller);
    }
  }

  public Clock clock() { return clock; }
  public DemoAuthService auth() { return auth; }
  public AuctionService auctions() { return auctions; }
  public ExecutorService workers() { return workers; }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      scheduler.close();
      workers.shutdownNow();
      auctions.close();
      auth.close();
    }
  }

  /** For shutdown verification; do not wait on this from an FX button handler. */
  public boolean awaitStopped(Duration timeout) throws InterruptedException {
    return scheduler.awaitTermination(timeout)
        && workers.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
  }
}
