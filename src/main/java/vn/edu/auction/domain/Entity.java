package vn.edu.auction.domain;

import java.util.Objects;
import java.util.UUID;

/** Lớp cơ sở cho các đối tượng có định danh. */
public abstract class Entity {
  private final UUID id;

  protected Entity(UUID id) {
    this.id = Objects.requireNonNull(id, "id không được null.");
  }

  public final UUID getId() {
    return id;
  }

  /** Hai đối tượng bằng nhau khi cùng lớp cụ thể và cùng id. */
  @Override
  public final boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (other == null || getClass() != other.getClass()) {
      return false;
    }

    Entity entity = (Entity) other;
    return id.equals(entity.id);
  }

  @Override
  public final int hashCode() {
    return Objects.hash(getClass(), id);
  }
}