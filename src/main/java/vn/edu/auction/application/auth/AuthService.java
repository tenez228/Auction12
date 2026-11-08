package vn.edu.auction.application.auth;

import java.util.UUID;
import vn.edu.auction.domain.user.User;

/** Authentication contract. Implementations own session validity and identity. */
public interface AuthService extends AutoCloseable {
  UserSession login(String username, char[] password);

  User requireUser(UserSession session);

  UserProfile profile(UserSession session);

  boolean isActive(UserSession session);

  void logout(UserSession session);

  String displayName(UUID userId);

  @Override
  void close();
}
