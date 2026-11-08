package vn.edu.auction.application;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import vn.edu.auction.domain.auction.Auction;
import vn.edu.auction.domain.auction.AuctionSnapshot;

/**
 * Quản lý các phiên trong bộ nhớ bằng Singleton.
 *
 * <p>Manager lưu một đối tượng Auction duy nhất cho mỗi ID đã đăng ký.
 * Logic đặt giá và khóa của từng phiên vẫn thuộc Auction, không lặp lại ở đây.
 * Không tự chạy bộ đếm giờ, lưu file hoặc kiểm tra đăng nhập.
 *
 * <p>Singleton có phạm vi một class loader; không chia sẻ bộ nhớ giữa các
 * tiến trình Java khác nhau. Đây không phải một cơ chế phân quyền.
 */
public final class AuctionManager implements AuctionRepository {
  private final ConcurrentMap<UUID, Auction> auctions = new ConcurrentHashMap<>();

  private AuctionManager() {
  }

  /** Chỉ khởi tạo khi getInstance() truy cập Holder lần đầu. */
  private static final class Holder {
    private static final AuctionManager INSTANCE = new AuctionManager();
  }

  public static AuctionManager getInstance() {
    return Holder.INSTANCE;
  }

  /**
   * Đăng ký phiên đã tạo; không thay thế phiên có cùng ID.
   *
   * <p>putIfAbsent kiểm tra và thêm trong một thao tác nguyên tử, kể cả khi
   * nhiều luồng cùng đăng ký. Gọi lại với chính đối tượng cũ cũng bị từ chối.
   * Chỉ kiểm tra trùng ID phiên, không áp dụng chính sách theo sản phẩm.
   *
   * @throws NullPointerException nếu auction là null
   * @throws IllegalArgumentException nếu ID đã được đăng ký
   */
  public void registerAuction(Auction auction) {
    Objects.requireNonNull(auction, "auction không được null.");
    Auction previous = auctions.putIfAbsent(auction.getId(), auction);
    if (previous != null) {
      throw new IllegalArgumentException("ID phiên đã tồn tại: " + auction.getId());
    }
  }

  /** Trả về Optional.empty() nếu không tìm thấy; ID null là lỗi. */
  public Optional<Auction> findAuction(UUID auctionId) {
    Objects.requireNonNull(auctionId, "auctionId không được null.");
    return Optional.ofNullable(auctions.get(auctionId));
  }

  /**
   * Lấy đúng đối tượng đã đăng ký, không tạo bản sao của phiên.
   *
   * @throws NullPointerException nếu auctionId là null
   * @throws NoSuchElementException nếu không có phiên với ID này
   */
  public Auction getAuction(UUID auctionId) {
    return findAuction(auctionId).orElseThrow(
        () -> new NoSuchElementException("Không tìm thấy phiên: " + auctionId));
  }

  public boolean containsAuction(UUID auctionId) {
    Objects.requireNonNull(auctionId, "auctionId không được null.");
    return auctions.containsKey(auctionId);
  }

  /**
   * Danh sách không thể thêm/xóa, sắp xếp theo ID để có thứ tự ổn định.
   *
   * <p>Các phần tử vẫn là Auction gốc và có thể thay đổi qua phương thức
   * nghiệp vụ. Dùng getSnapshots() khi chỉ cần dữ liệu đọc. Phiên đăng ký
   * đồng thời có thể xuất hiện hoặc chưa xuất hiện trong lần lấy này.
   */
  public List<Auction> getAuctions() {
    return auctions.values().stream()
        .sorted(Comparator.comparing(Auction::getId))
        .toList();
  }

  /**
   * Lấy bản chụp bất biến của từng phiên, không tự chuyển trạng thái.
   *
   * <p>Mỗi bản chụp nhất quán nhờ khóa trong Auction.snapshot(). Cả danh sách
   * không phải một bản chụp đồng thời của tất cả các phiên.
   */
  public List<AuctionSnapshot> getSnapshots() {
    return getAuctions().stream().map(Auction::snapshot).toList();
  }

  /** Số phiên hiện đăng ký; có thể thay đổi nếu luồng khác đang thêm. */
  public int getAuctionCount() {
    return auctions.size();
  }
}
