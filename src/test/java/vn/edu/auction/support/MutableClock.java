package vn.edu.auction.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Test time can advance without sleeping and is visible to all concurrent bidders. */
public final class MutableClock extends Clock {
  private final AtomicReference<Instant> now;
  private final ZoneId zone;

  public MutableClock(Instant initial) {
    this(new AtomicReference<>(Objects.requireNonNull(initial)), ZoneOffset.UTC);
  }

  private MutableClock(AtomicReference<Instant> now, ZoneId zone) {
    this.now = now;
    this.zone = Objects.requireNonNull(zone);
  }

  public void set(Instant instant) { now.set(Objects.requireNonNull(instant)); }
  @Override public Instant instant() { return now.get(); }
  @Override public ZoneId getZone() { return zone; }
  @Override public Clock withZone(ZoneId value) { return new MutableClock(now, value); }
}
