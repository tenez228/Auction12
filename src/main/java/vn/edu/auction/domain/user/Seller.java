package vn.edu.auction.domain.user;

import java.util.UUID;

/** Người đăng sản phẩm đấu giá. */
public final class Seller extends User {
  public Seller(String username, String displayName) {
    super(username, displayName);
  }

  public Seller(UUID id, String username, String displayName) {
    super(id, username, displayName);
  }

  @Override
  public UserRole getRole() {
    return UserRole.SELLER;
  }
}
