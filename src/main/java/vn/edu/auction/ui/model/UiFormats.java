package vn.edu.auction.ui.model;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import vn.edu.auction.domain.auction.AuctionStatus;

public final class UiFormats {
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM")
      .withZone(ZoneId.systemDefault());

  private UiFormats() {
  }

  public static String money(BigDecimal amount) {
    return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount) + " đ";
  }

  public static BigDecimal parseMoney(String input) {
    String text = input == null ? "" : input.strip();
    if (!text.matches("[0-9]+")) {
      throw new IllegalArgumentException("Nhập số nguyên VND, không dấu chấm/phẩy.");
    }
    BigDecimal amount = new BigDecimal(text);
    if (amount.signum() <= 0) {
      throw new IllegalArgumentException("Số tiền phải lớn hơn 0.");
    }
    return amount;
  }

  public static String time(Instant instant) { return TIME.format(instant); }

  public static String status(AuctionStatus status) {
    return switch (status) {
      case OPEN -> "Chờ bắt đầu";
      case RUNNING -> "Đang đấu giá";
      case FINISHED -> "Đã kết thúc";
      case PAID -> "Đã thanh toán";
      case CANCELED -> "Đã hủy";
    };
  }
}
