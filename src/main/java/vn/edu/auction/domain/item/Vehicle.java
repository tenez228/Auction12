package vn.edu.auction.domain.item;

import java.util.UUID;

/** Phương tiện, ví dụ ô tô hoặc xe máy. */
public final class Vehicle extends Item {
  private final String brand;
  private final String model;
  private final int manufactureYear;
  private final int mileageKm;

  public Vehicle(
      UUID sellerId, String name, String description,
      String brand, String model, int manufactureYear, int mileageKm) {
    this(UUID.randomUUID(), sellerId, name, description,
        brand, model, manufactureYear, mileageKm);
  }

  public Vehicle(
      UUID id, UUID sellerId, String name, String description,
      String brand, String model, int manufactureYear, int mileageKm) {
    super(id, sellerId, name, description);
    this.brand = requireText(brand, "brand");
    this.model = requireText(model, "model");
    if (manufactureYear <= 0) {
      throw new IllegalArgumentException("Năm sản xuất phải lớn hơn 0.");
    }
    if (mileageKm < 0) {
      throw new IllegalArgumentException("Số km đã đi không được âm.");
    }
    this.manufactureYear = manufactureYear;
    this.mileageKm = mileageKm;
  }

  public String getBrand() {
    return brand;
  }

  public String getModel() {
    return model;
  }

  public int getManufactureYear() {
    return manufactureYear;
  }

  public int getMileageKm() {
    return mileageKm;
  }

  @Override
  public ItemType getType() {
    return ItemType.VEHICLE;
  }

  @Override
  public String describeDetails() {
    return "Hãng: " + brand + ", model: " + model
        + ", năm: " + manufactureYear + ", đã đi: " + mileageKm + " km";
  }
}
