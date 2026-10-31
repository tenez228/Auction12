package vn.edu.auction.domain.item.item;

import vn.edu.auction.domain.item.Item;
import vn.edu.auction.domain.item.ItemType;

import java.util.UUID;

/** Tác phẩm nghệ thuật. */
public final class Art extends Item {
  private final String artist;
  private final String material;

  public Art(UUID sellerId, String name, String description, String artist, String material) {
    this(UUID.randomUUID(), sellerId, name, description, artist, material);
  }

  public Art(
      UUID id, UUID sellerId, String name, String description, String artist, String material) {
    super(id, sellerId, name, description);
    this.artist = requireText(artist, "artist");
    this.material = requireText(material, "material");
  }

  public String getArtist() {
    return artist;
  }

  public String getMaterial() {
    return material;
  }

  @Override
  public ItemType getType() {
    return ItemType.ART;
  }

  @Override
  public String describeDetails() {
    return "Tác giả: " + artist + ", chất liệu: " + material;
  }
}
