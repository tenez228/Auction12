package vn.edu.auction.domain.auction.event;

/** Keep callbacks short; never wait for a later event from inside a callback. */
@FunctionalInterface
public interface AuctionObserver {
  void onAuctionChanged(AuctionEvent event);
}
