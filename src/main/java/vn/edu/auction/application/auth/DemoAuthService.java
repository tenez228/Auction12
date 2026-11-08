package vn.edu.auction.application.auth;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import vn.edu.auction.domain.user.Admin;
import vn.edu.auction.domain.user.Bidder;
import vn.edu.auction.domain.user.Seller;
import vn.edu.auction.domain.user.User;

/**
 * OFFLINE teaching accounts only. Password demo123 is intentionally public.
 * No registration, durable accounts, password hashing or network authentication.
 * Never put real passwords in this class or use it for a deployed service.
 */
public final class DemoAuthService implements AuthService {
  private final Map<String, User> accounts = Map.of(
      "seller01", new Seller("seller01", "Người bán 01"),
      "bidder01", new Bidder("bidder01", "Người đấu giá 01"),
      "bidder02", new Bidder("bidder02", "Người đấu giá 02"),
      "admin01", new Admin("admin01", "Quản trị viên"));
  private final ConcurrentMap<UUID, User> sessions = new ConcurrentHashMap<>();
  private volatile boolean closed;

  public synchronized UserSession login(String username, char[] password) {
    if (closed) {
      throw new IllegalStateException("Authentication service is closed.");
    }
    if (username == null || username.isBlank() || password == null || password.length == 0) {
      throw new AuthenticationException("Vui lòng nhập tên đăng nhập và mật khẩu.");
    }
    User user = accounts.get(username.strip());
    if (user == null || !Arrays.equals(password, "demo123".toCharArray())) {
      throw new AuthenticationException("Sai tên đăng nhập hoặc mật khẩu.");
    }
    UUID token = UUID.randomUUID();
    sessions.put(token, user);
    return new UserSession(token);
  }

  public User requireUser(UserSession session) {
    if (session == null) {
      throw new AuthenticationException("Vui lòng đăng nhập để tiếp tục.");
    }
    User user = sessions.get(session.token());
    if (closed || user == null) {
      throw new AuthenticationException("Phiên đăng nhập không còn hiệu lực.");
    }
    return user;
  }

  public UserProfile profile(UserSession session) {
    User user = requireUser(session);
    return new UserProfile(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
  }

  public boolean isActive(UserSession session) {
    return !closed && session != null && sessions.containsKey(session.token());
  }

  public void logout(UserSession session) {
    if (session != null) {
      sessions.remove(session.token());
    }
  }

  public String displayName(UUID userId) {
    return accounts.values().stream().filter(user -> user.getId().equals(userId))
        .map(User::getDisplayName).findFirst().orElse(userId.toString().substring(0, 8));
  }

  public List<String> usernames() {
    return accounts.keySet().stream().sorted().toList();
  }

  @Override
  public synchronized void close() {
    closed = true;
    sessions.clear();
  }
}
