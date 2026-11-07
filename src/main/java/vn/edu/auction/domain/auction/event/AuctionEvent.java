package vn.edu.auction.domain.auction.event;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import vn.edu.auction.domain.auction.AuctionSnapshot;
import vn.edu.auction.domain.auction.BidTransaction;

/** Immutable information about exactly one committed state change. */
public record AuctionEvent(
    AuctionEventType type, Instant occurredAt, AuctionSnapshot snapshot,
    Optional<BidTransaction> bid) {
  public AuctionEvent {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(snapshot, "snapshot");
    Objects.requireNonNull(bid, "bid");
    if ((type == AuctionEventType.BID_PLACED) != bid.isPresent()) {
      throw new IllegalArgumentException("Only BID_PLACED must carry a bid.");
    }
    if (bid.isPresent() && !bid.get().getAuctionId().equals(snapshot.auctionId())) {
      throw new IllegalArgumentException("Bid belongs to another auction.");
    }
  }

  public UUID auctionId() {
    return snapshot.auctionId();
  }

  public long version() {
    return snapshot.version();
  }
}
