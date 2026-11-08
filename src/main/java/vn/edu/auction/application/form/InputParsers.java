package vn.edu.auction.application.form;

import java.math.BigDecimal;
import vn.edu.auction.domain.exception.DataValidationException;
import vn.edu.auction.domain.value.VndAmount;

/** Input syntax only; current-price/time/permission rules remain in the business layer. */
public final class InputParsers {
  private InputParsers() {
  }

  public static BigDecimal money(String input) {
    String text = input == null ? "" : input.strip();
    if (!text.matches("[0-9]+")) {
      throw new DataValidationException("Nhập VND nguyên đồng, không dấu chấm/phẩy.");
    }
    return VndAmount.requirePositive(new BigDecimal(text), "amount");
  }

  public static int integer(String input, String field) {
    try {
      return Integer.parseInt(input == null ? "" : input.strip());
    } catch (NumberFormatException ex) {
      throw new DataValidationException(field + " phải là số nguyên hợp lệ.", ex);
    }
  }
}
