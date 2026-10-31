package vn.edu.auction.domain.auction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Bản chụp nhất quán, chỉ đọc, được Auction tạo khi giữ khóa.
 * Dùng record nên getter có dạng currentPrice(), status(), ...
 */
public record AuctionSnapshot(
    UUID auctionId,
    BigDecimal currentPrice,
    AuctionStatus status,
    Optional<UUID> leadingBidderId,
    Optional<UUID> winnerId,
    List<BidTransaction> bidHistory,
    long version,
    Optional<String> cancellationReason) {

  public AuctionSnapshot {
    Objects.requireNonNull(auctionId, "auctionId");
    Objects.requireNonNull(currentPrice, "currentPrice");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(leadingBidderId, "leadingBidderId");
    Objects.requireNonNull(winnerId, "winnerId");
    Objects.requireNonNull(cancellationReason, "cancellationReason");
    bidHistory = List.copyOf(Objects.requireNonNull(bidHistory, "bidHistory"));
    if (version < 0) {
      throw new IllegalArgumentException("version không được âm.");
    }
  }
}
