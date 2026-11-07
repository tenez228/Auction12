package vn.edu.auction.domain.auction.event;

/** A subscription must be closed when its screen or service is disposed. */
@FunctionalInterface
public interface Subscription extends AutoCloseable {
  @Override
  void close();
}
