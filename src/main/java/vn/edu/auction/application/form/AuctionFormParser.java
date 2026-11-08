package vn.edu.auction.application.form;

import java.time.Duration;
import vn.edu.auction.domain.exception.DataValidationException;
import vn.edu.auction.domain.factory.ArtFactory;
import vn.edu.auction.domain.factory.ElectronicsFactory;
import vn.edu.auction.domain.factory.ItemFactory;
import vn.edu.auction.domain.factory.VehicleFactory;
import vn.edu.auction.domain.item.Item;

/** Pure form-to-draft conversion, usable and testable without JavaFX. */
public final class AuctionFormParser {
  private AuctionFormParser() {
  }

  public static AuctionDraft parse(CreateAuctionInput input) {
    if (input == null || input.type() == null) {
      throw new DataValidationException("Vui lòng chọn loại sản phẩm.");
    }
    String name = requireText(input.name(), "Tên sản phẩm");
    String description = requireText(input.description(), "Mô tả");
    String first = requireText(input.detailOne(), "Hãng / Tác giả");
    String second = requireText(input.detailTwo(), "Model / Chất liệu");
    try {
      ItemFactory<? extends Item> factory = switch (input.type()) {
        case ELECTRONICS -> new ElectronicsFactory(first, second,
            InputParsers.integer(input.numberOne(), "Bảo hành"));
        case ART -> new ArtFactory(first, second);
        case VEHICLE -> new VehicleFactory(first, second,
            InputParsers.integer(input.numberOne(), "Năm sản xuất"),
            InputParsers.integer(input.numberTwo(), "Kilomet"));
      };
      return new AuctionDraft(factory, name, description, InputParsers.money(input.price()),
          Duration.ofSeconds(InputParsers.integer(input.delay(), "Thời gian chờ")),
          Duration.ofSeconds(InputParsers.integer(input.duration(), "Thời lượng")));
    } catch (DataValidationException ex) {
      throw ex;
    } catch (IllegalArgumentException ex) {
      // Translate documented validation failures of the existing item factories.
      // Unexpected runtime errors are NOT re-labelled as user mistakes.
      throw new DataValidationException(ex.getMessage(), ex);
    }
  }

  static String requireText(String text, String field) {
    if (text == null || text.isBlank()) {
      throw new DataValidationException(field + " không được để trống.");
    }
    return text.strip();
  }
}
