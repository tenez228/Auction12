package vn.edu.auction.domain.factory;

import java.util.UUID;
import vn.edu.auction.domain.item.Vehicle;

/** Concrete creator cho phương tiện. */
public final class VehicleFactory extends ItemFactory<Vehicle> {
  private final String brand;
  private final String model;
  private final int manufactureYear;
  private final int mileageKm;

  public VehicleFactory(String brand, String model, int manufactureYear, int mileageKm) {
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

  @Override
  protected Vehicle newItem(UUID id, UUID sellerId, String name, String description) {
    return new Vehicle(id, sellerId, name, description,
        brand, model, manufactureYear, mileageKm);
  }
}
