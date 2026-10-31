package vn.edu.auction.domain.item;

import java.util.Objects;
import java.util.UUID;
import vn.edu.auction.domain.Entity;

/**
 * Thông tin sản phẩm. Giá và thời gian thuộc Auction.
 *
 * <p>Các trường trong bản này chỉ đọc sau khi tạo;
 * không cung cấp setter để thay đổi tùy ý khi đang đấu giá.
 */
public abstract class Item extends Entity {
  private final UUID sellerId;
  private final String name;
  private final String description;

  protected Item(UUID sellerId, String name, String description) {
    this(UUID.randomUUID(), sellerId, name, description);
  }

  protected Item(UUID id, UUID sellerId, String name, String description) {
    super(id);
    this.sellerId = Objects.requireNonNull(sellerId, "sellerId không được null.");
    this.name = requireText(name, "name");
    this.description = requireText(description, "description");
  }

  public final UUID getSellerId() {
    return sellerId;
  }

  public final String getName() {
    return name;
  }

  public final String getDescription() {
    return description;
  }

  public abstract ItemType getType();

  /** Gọi qua kiểu Item để thể hiện đa hình. */
  public abstract String describeDetails();

  protected static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " không được để trống.");
    }
    return value.strip();
  }

  @Override
  public final String toString() {
    return getClass().getSimpleName()
        + "{id=" + getId()
        + ", sellerId=" + sellerId
        + ", name='" + name + "'"
        + ", type=" + getType()
        + "}";
  }
}
