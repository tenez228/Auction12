package vn.edu.auction.domain.item.item;

/** Ba loại sản phẩm trong bài tập. */
public enum ItemType {
  ELECTRONICS("Điện tử"),
  ART("Nghệ thuật"),
  VEHICLE("Phương tiện");

  private final String label;

  ItemType(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
