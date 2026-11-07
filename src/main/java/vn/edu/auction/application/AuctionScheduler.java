package vn.edu.auction.application;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Checks time on the application side. UI updates are Observer-driven. */
public final class AuctionScheduler implements AutoCloseable {
  private static final Logger LOGGER = Logger.getLogger(AuctionScheduler.class.getName());
  private final AuctionService service;
  private final long intervalMillis;
  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
      runnable -> new Thread(runnable, "auction-clock"));
  private boolean started;
  private boolean closed;

  public AuctionScheduler(AuctionService service, Duration interval) {
    this.service = Objects.requireNonNull(service, "service");
    Objects.requireNonNull(interval, "interval");
    intervalMillis = interval.toMillis();
    if (intervalMillis < 1) {
      throw new IllegalArgumentException("Interval must be at least one millisecond.");
    }
  }

  public synchronized void start() {
    if (closed) {
      throw new IllegalStateException("Scheduler is closed.");
    }
    if (!started) {
      started = true;
      executor.scheduleWithFixedDelay(this::tick, 0, intervalMillis, TimeUnit.MILLISECONDS);
    }
  }

  private void tick() {
    try {
      service.refreshAuctions();
    } catch (RuntimeException ex) {
      // A failed tick must not silently cancel every later tick.
      LOGGER.log(Level.WARNING, "Auction clock tick failed.", ex);
    }
  }

  @Override
  public synchronized void close() {
    closed = true;
    executor.shutdownNow();
  }

  public boolean awaitTermination(Duration timeout) throws InterruptedException {
    return executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
  }
}
