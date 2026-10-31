package vn.edu.auction.domain.auction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import vn.edu.auction.domain.Entity;

/**
 * Bản ghi bất biến của một lần đặt giá.
 * Tạo riêng bản ghi không đồng nghĩa với đặt giá thành công;
 * chỉ Auction.placeBid() mới thêm nó vào lịch sử của phiên.
 */
public final class BidTransaction extends Entity {
  private final UUID auctionId;
  private final UUID bidderId;
  private final BigDecimal amount;
  private final Instant placedAt;

  public BidTransaction(UUID auctionId, UUID bidderId, BigDecimal amount, Instant placedAt) {
    this(UUID.randomUUID(), auctionId, bidderId, amount, placedAt);
  }

  public BidTransaction(
      UUID id, UUID auctionId, UUID bidderId, BigDecimal amount, Instant placedAt) {
    super(id);
    this.auctionId = Objects.requireNonNull(auctionId, "auctionId không được null.");
    this.bidderId = Objects.requireNonNull(bidderId, "bidderId không được null.");
    this.amount = requirePositiveVnd(amount, "amount");
    this.placedAt = Objects.requireNonNull(placedAt, "placedAt không được null.");
  }

  public UUID getAuctionId() {
    return auctionId;
  }

  public UUID getBidderId() {
    return bidderId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public Instant getPlacedAt() {
    return placedAt;
  }

  /** Quy ước của bản này: số tiền VND nguyên đồng, lớn hơn 0. */
  static BigDecimal requirePositiveVnd(BigDecimal value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " không được null.");
    if (value.signum() <= 0) {
      throw new IllegalArgumentException(fieldName + " phải lớn hơn 0.");
    }
    try {
      return value.setScale(0, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException ex) {
      throw new IllegalArgumentException(fieldName + " phải là số nguyên đồng VND.", ex);
    }
  }

  @Override
  public String toString() {
    return "BidTransaction{id=" + getId()
        + ", auctionId=" + auctionId
        + ", bidderId=" + bidderId
        + ", amount=" + amount.toPlainString()
        + ", placedAt=" + placedAt + "}";
  }
}
