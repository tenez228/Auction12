package vn.edu.auction.domain.item;

import java.util.UUID;

/** Sản phẩm điện tử, ví dụ laptop hoặc điện thoại. */
public final class Electronics extends Item {
  private final String brand;
  private final String model;
  private final int warrantyMonths;

  public Electronics(
      UUID sellerId, String name, String description,
      String brand, String model, int warrantyMonths) {
    this(UUID.randomUUID(), sellerId, name, description, brand, model, warrantyMonths);
  }

  public Electronics(
      UUID id, UUID sellerId, String name, String description,
      String brand, String model, int warrantyMonths) {
    super(id, sellerId, name, description);
    this.brand = requireText(brand, "brand");
    this.model = requireText(model, "model");
    if (warrantyMonths < 0) {
      throw new IllegalArgumentException("Số tháng bảo hành không được âm.");
    }
    this.warrantyMonths = warrantyMonths;
  }

  public String getBrand() {
    return brand;
  }

  public String getModel() {
    return model;
  }

  public int getWarrantyMonths() {
    return warrantyMonths;
  }

  @Override
  public ItemType getType() {
    return ItemType.ELECTRONICS;
  }

  @Override
  public String describeDetails() {
    return "Hãng: " + brand + ", model: " + model
        + ", bảo hành: " + warrantyMonths + " tháng";
  }
}
