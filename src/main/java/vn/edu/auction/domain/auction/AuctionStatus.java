package vn.edu.auction.domain.auction;

/** Chuỗi trạng thái: OPEN -> RUNNING -> FINISHED -> PAID/CANCELED. */
public enum AuctionStatus {
  OPEN("Chưa bắt đầu"),
  RUNNING("Đang đấu giá"),
  FINISHED("Đã kết thúc"),
  PAID("Đã thanh toán"),
  CANCELED("Đã hủy");

  private final String label;

  AuctionStatus(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }

  public boolean isBiddingClosed() {
    return this == FINISHED || this == PAID || this == CANCELED;
  }
}
