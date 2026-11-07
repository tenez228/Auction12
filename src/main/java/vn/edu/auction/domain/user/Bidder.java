package vn.edu.auction.domain.user;

import java.util.UUID;

/** Người tham gia đấu giá. */
public final class Bidder extends User {
  public Bidder(String username, String displayName) {
    super(username, displayName);
  }

  public Bidder(UUID id, String username, String displayName) {
    super(id, username, displayName);
  }

  @Override
  public UserRole getRole() {
    return UserRole.BIDDER;
  }
}
