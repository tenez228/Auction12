package vn.edu.auction.domain.factory;

import java.util.UUID;
import vn.edu.auction.domain.item.Electronics;

/** Factory được cấu hình bằng đặc điểm điện tử cần tạo. */
public final class ElectronicsFactory extends ItemFactory<Electronics> {
  private final String brand;
  private final String model;
  private final int warrantyMonths;

  public ElectronicsFactory(String brand, String model, int warrantyMonths) {
    this.brand = requireText(brand, "brand");
    this.model = requireText(model, "model");
    if (warrantyMonths < 0) {
      throw new IllegalArgumentException("Số tháng bảo hành không được âm.");
    }
    this.warrantyMonths = warrantyMonths;
  }

  @Override
  protected Electronics newItem(UUID id, UUID sellerId, String name, String description) {
    return new Electronics(id, sellerId, name, description, brand, model, warrantyMonths);
  }
}
