package vn.edu.auction.domain.user;

import java.util.UUID;

/** Người quản trị hệ thống. */
public final class Admin extends User {
  public Admin(String username, String displayName) {
    super(username, displayName);
  }

  public Admin(UUID id, String username, String displayName) {
    super(id, username, displayName);
  }

  @Override
  public UserRole getRole() {
    return UserRole.ADMIN;
  }
}
