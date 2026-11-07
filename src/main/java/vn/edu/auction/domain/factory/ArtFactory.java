package vn.edu.auction.domain.factory;

import java.util.UUID;
import vn.edu.auction.domain.item.Art;

/** Concrete creator cho tác phẩm nghệ thuật. */
public final class ArtFactory extends ItemFactory<Art> {
  private final String artist;
  private final String material;

  public ArtFactory(String artist, String material) {
    this.artist = requireText(artist, "artist");
    this.material = requireText(material, "material");
  }

  @Override
  protected Art newItem(UUID id, UUID sellerId, String name, String description) {
    return new Art(id, sellerId, name, description, artist, material);
  }
}
