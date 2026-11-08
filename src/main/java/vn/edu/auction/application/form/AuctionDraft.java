package vn.edu.auction.application.form;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import vn.edu.auction.domain.exception.DataValidationException;
import vn.edu.auction.domain.factory.ItemFactory;
import vn.edu.auction.domain.item.Item;
import vn.edu.auction.domain.value.VndAmount;

/** A validated draft. The seller ID is assigned only by the authenticated application service. */
public record AuctionDraft(
    ItemFactory<? extends Item> factory, String name, String description,
    BigDecimal price, Duration delay, Duration duration) {

  public AuctionDraft {
    Objects.requireNonNull(factory, "factory");
    name = AuctionFormParser.requireText(name, "Tên sản phẩm");
    description = AuctionFormParser.requireText(description, "Mô tả");
    price = VndAmount.requirePositive(price, "price");
    if (delay == null || duration == null || delay.isNegative()
        || delay.compareTo(Duration.ofDays(1)) > 0 || duration.compareTo(Duration.ZERO) <= 0
        || duration.compareTo(Duration.ofDays(1)) > 0) {
      throw new DataValidationException("Chờ: 0..86400 giây; thời lượng: 1..86400 giây.");
    }
  }
}
