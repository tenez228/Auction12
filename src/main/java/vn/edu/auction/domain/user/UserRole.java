package vn.edu.auction.domain.user;

/** Ba vai trò người dùng trong hệ thống. */
public enum UserRole {
  BIDDER("Người đấu giá"),
  SELLER("Người bán"),
  ADMIN("Quản trị viên");

  private final String label;

  UserRole(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}