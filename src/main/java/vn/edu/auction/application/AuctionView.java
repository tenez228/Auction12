package vn.edu.auction.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import vn.edu.auction.domain.auction.AuctionSnapshot;
import vn.edu.auction.domain.item.ItemType;

/** Immutable data for one table row, including a consistent auction snapshot. */
public record AuctionView(
    UUID id, UUID itemId, String name, String details, ItemType type,
    UUID sellerId, String sellerName, BigDecimal startingPrice,
    Instant startTime, Instant endTime, String leaderName, String winnerName,
    AuctionSnapshot state) {
}
