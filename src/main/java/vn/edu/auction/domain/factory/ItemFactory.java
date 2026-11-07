package vn.edu.auction.domain.factory;

import java.util.UUID;
import vn.edu.auction.domain.item.Item;

/**
 * Creator trong Factory Method. T là loại Item mà factory tạo ra.
 * Lớp con quyết định sản phẩm cụ thể trong newItem().
 */
public abstract class ItemFactory<T extends Item> {
  public final T createItem(UUID sellerId, String name, String description) {
    return newItem(UUID.randomUUID(), sellerId, name, description);
  }

  /** Tạo với ID đã biết; không tự đăng ký vào kho sản phẩm. */
  public final T createItem(UUID id, UUID sellerId, String name, String description) {
    return newItem(id, sellerId, name, description);
  }

  protected abstract T newItem(UUID id, UUID sellerId, String name, String description);

  protected static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " không được để trống.");
    }
    return value.strip();
  }
}
