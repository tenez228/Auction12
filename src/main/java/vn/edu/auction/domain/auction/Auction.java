package vn.edu.auction.domain.auction;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import vn.edu.auction.domain.Entity;
import vn.edu.auction.domain.item.Item;
import vn.edu.auction.domain.user.Bidder;
import vn.edu.auction.domain.user.User;
import vn.edu.auction.domain.user.UserRole;

/**
 * Một phiên đấu giá với giá tăng nghiêm ngặt.
 *
 * <p>Mọi thay đổi và lần đọc trạng thái dùng cùng một khóa.
 * Getter không tự chuyển trạng thái theo thời gian: gọi refreshStatus()
 * để đồng bộ chủ động. placeBid(), markPaid(), cancel() cũng kiểm tra giờ.
 * Lớp này không tạo scheduler, không xác thực tài khoản, không phát event.
 */
public final class Auction extends Entity {
  private final UUID itemId;
  private final UUID sellerId;
  private final BigDecimal startingPrice;
  private final Instant startTime;
  private final Instant endTime;
  private final Clock clock;
  private final ReentrantLock lock = new ReentrantLock();
  private final List<BidTransaction> bidHistory = new ArrayList<>();

  // Tất cả field bên dưới được bảo vệ bởi lock.
  private AuctionStatus status = AuctionStatus.OPEN;
  private BigDecimal currentPrice;
  private UUID leadingBidderId;
  private UUID winnerId;
  private String cancellationReason;
  private long version;

  public Auction(Item item, BigDecimal startingPrice, Instant startTime, Instant endTime) {
    this(UUID.randomUUID(), item, startingPrice, startTime, endTime, Clock.systemUTC());
  }

  /** Truyền Clock để kiểm tra thời gian mà không phải chờ thật. */
  public Auction(
      Item item, BigDecimal startingPrice, Instant startTime, Instant endTime, Clock clock) {
    this(UUID.randomUUID(), item, startingPrice, startTime, endTime, clock);
  }

  /**
   * Tạo phiên MỚI với ID đã biết; không phải thao tác phục hồi lịch sử.
   */
  public Auction(
      UUID id, Item item, BigDecimal startingPrice,
      Instant startTime, Instant endTime, Clock clock) {
    super(id);
    Objects.requireNonNull(item, "item không được null.");
    this.itemId = item.getId();
    this.sellerId = item.getSellerId();
    this.startingPrice = BidTransaction.requirePositiveVnd(startingPrice, "startingPrice");
    this.startTime = Objects.requireNonNull(startTime, "startTime không được null.");
    this.endTime = Objects.requireNonNull(endTime, "endTime không được null.");
    this.clock = Objects.requireNonNull(clock, "clock không được null.");
    if (!startTime.isBefore(endTime)) {
      throw new IllegalArgumentException("Giờ bắt đầu phải trước giờ kết thúc.");
    }
    this.currentPrice = this.startingPrice;
  }

  public UUID getItemId() {
    return itemId;
  }

  public UUID getSellerId() {
    return sellerId;
  }

  public BigDecimal getStartingPrice() {
    return startingPrice;
  }

  public Instant getStartTime() {
    return startTime;
  }

  public Instant getEndTime() {
    return endTime;
  }

  public BigDecimal getCurrentPrice() {
    return readLocked(() -> currentPrice);
  }

  public AuctionStatus getStatus() {
    return readLocked(() -> status);
  }

  public Optional<UUID> getLeadingBidderId() {
    return readLocked(() -> Optional.ofNullable(leadingBidderId));
  }

  /** Khi CANCELED, giữ kết quả đấu giá để tra cứu; không nghĩa là đã mua hàng. */
  public Optional<UUID> getWinnerId() {
    return readLocked(() -> Optional.ofNullable(winnerId));
  }

  public Optional<String> getCancellationReason() {
    return readLocked(() -> Optional.ofNullable(cancellationReason));
  }

  public long getVersion() {
    return readLocked(() -> version);
  }

  /** Trả bản sao chỉ đọc, không trả danh sách nội bộ. */
  public List<BidTransaction> getBidHistory() {
    return readLocked(() -> List.copyOf(bidHistory));
  }

  /** Đọc nhiều giá trị cùng lúc nên dùng phương thức này. */
  public AuctionSnapshot snapshot() {
    return readLocked(() -> new AuctionSnapshot(
        getId(), currentPrice, status, Optional.ofNullable(leadingBidderId),
        Optional.ofNullable(winnerId), bidHistory, version,
        Optional.ofNullable(cancellationReason)));
  }

  /**
   * Đồng bộ trạng thái theo Clock. Trả true nếu có thay đổi.
   * Không mở lại phiên đã đóng, kể cả khi đồng hồ lùi.
   */
  public boolean refreshStatus() {
    lock.lock();
    try {
      long previousVersion = version;
      advanceState(clock.instant());
      return version != previousVersion;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Nhận giá trong [startTime, endTime), phải cao hơn currentPrice.
   *
   * <p>Bidder phải do lớp xử lý đăng nhập xác định, không lấy ID
   * tùy ý từ giao diện. Kiểm tra/cập nhật dùng chung một khóa.
   */
  public BidTransaction placeBid(Bidder bidder, BigDecimal amount) {
    Objects.requireNonNull(bidder, "bidder không được null.");
    BigDecimal normalizedAmount = BidTransaction.requirePositiveVnd(amount, "amount");
    lock.lock();
    try {
      // Lấy giờ SAU khi lấy khóa, không tin giờ do client gửi.
      Instant now = clock.instant();
      advanceState(now);
      if (status != AuctionStatus.RUNNING
          || now.isBefore(startTime) || !now.isBefore(endTime)) {
        throw new IllegalStateException("Phiên không trong thời gian nhận giá.");
      }
      if (sellerId.equals(bidder.getId())) {
        throw new IllegalArgumentException("Người bán không được tự đặt giá.");
      }
      if (normalizedAmount.compareTo(currentPrice) <= 0) {
        throw new IllegalArgumentException("Giá mới phải cao hơn giá hiện tại.");
      }
      BidTransaction bid = new BidTransaction(getId(), bidder.getId(), normalizedAmount, now);
      bidHistory.add(bid);
      currentPrice = normalizedAmount;
      leadingBidderId = bidder.getId();
      version++;
      return bid;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Ghi nhận đã thanh toán; KHÔNG thực hiện chuyển tiền.
   * Quy ước: chủ phiên hoặc Admin xác nhận, phiên FINISHED và có người thắng.
   */
  public void markPaid(User actor) {
    Objects.requireNonNull(actor, "actor không được null.");
    lock.lock();
    try {
      advanceState(clock.instant());
      requireSellerOrAdmin(actor);
      requireFinished();
      if (winnerId == null) {
        throw new IllegalStateException("Phiên không có người thắng để thanh toán.");
      }
      status = AuctionStatus.PAID;
      version++;
    } finally {
      lock.unlock();
    }
  }

  /** Quy ước: chỉ hủy từ FINISHED, có lý do và đúng quyền. */
  public void cancel(User actor, String reason) {
    Objects.requireNonNull(actor, "actor không được null.");
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("Phải ghi lý do hủy.");
    }
    String normalizedReason = reason.strip();
    lock.lock();
    try {
      advanceState(clock.instant());
      requireSellerOrAdmin(actor);
      requireFinished();
      cancellationReason = normalizedReason;
      status = AuctionStatus.CANCELED;
      version++;
    } finally {
      lock.unlock();
    }
  }

  // Chỉ gọi các hàm private thay đổi trạng thái khi đang giữ lock.
  private void advanceState(Instant now) {
    if (status == AuctionStatus.OPEN && !now.isBefore(startTime)) {
      status = AuctionStatus.RUNNING;
      version++;
    }
    if (status == AuctionStatus.RUNNING && !now.isBefore(endTime)) {
      status = AuctionStatus.FINISHED;
      winnerId = leadingBidderId;
      version++;
    }
  }

  private void requireFinished() {
    if (status != AuctionStatus.FINISHED) {
      throw new IllegalStateException("Chỉ thao tác được với phiên FINISHED.");
    }
  }

  private void requireSellerOrAdmin(User actor) {
    boolean admin = actor.getRole() == UserRole.ADMIN;
    boolean owner = actor.getRole() == UserRole.SELLER && sellerId.equals(actor.getId());
    if (!admin && !owner) {
      throw new SecurityException("Chỉ chủ phiên hoặc Admin được thực hiện.");
    }
  }

  /** Dùng nội bộ cho các getter; không nhận callback từ bên ngoài. */
  private <T> T readLocked(Supplier<T> reader) {
    lock.lock();
    try {
      return reader.get();
    } finally {
      lock.unlock();
    }
  }
}
