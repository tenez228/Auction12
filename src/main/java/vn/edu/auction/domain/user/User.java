package vn.edu.auction.domain.user;

import java.util.UUID;
import vn.edu.auction.domain.Entity;

/** Thông tin chung của người dùng; không xử lý đăng nhập tại đây. */
public abstract class User extends Entity {
  private final String username;
  private String displayName;

  /** Tạo người dùng mới với id tự sinh. */
  protected User(String username, String displayName) {
    this(UUID.randomUUID(), username, displayName);
  }

  /** Tạo đối tượng với id đã biết. */
  protected User(UUID id, String username, String displayName) {
    super(id);
    this.username = requireText(username, "username");
    this.displayName = requireText(displayName, "displayName");
  }

  public final String getUsername() {
    return username;
  }

  public final String getDisplayName() {
    return displayName;
  }

  /** Chỉ cập nhật sau khi tên mới vượt qua kiểm tra. */
  public final void changeDisplayName(String newDisplayName) {
    this.displayName = requireText(newDisplayName, "displayName");
  }

  /** Mỗi lớp con phải xác định vai trò của mình. */
  public abstract UserRole getRole();

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(
          fieldName + " không được null, rỗng hoặc chỉ chứa khoảng trắng.");
    }

    return value.strip();
  }

  @Override
  public final String toString() {
    return getClass().getSimpleName()
        + "{id=" + getId()
        + ", username='" + username + "'"
        + ", displayName='" + displayName + "'"
        + ", role=" + getRole()
        + "}";
  }
}
