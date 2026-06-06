package com.shimanamisan.baseballmarket.product.domain;

import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import java.text.NumberFormat;
import java.util.Locale;

public record Price(int value) {

  private static final int MAX_VALUE = 99_999_999;

  public Price {
    if (value < 0) {
      throw new ValidationException("price", "価格は0円以上で入力してください");
    }
    if (value > MAX_VALUE) {
      throw new ValidationException("price", "価格が大きすぎます");
    }
  }

  public static Price fromInt(int value) {
    return new Price(value);
  }

  /** "¥1,000" 形式に整形する。Thymeleaf の表示で利用。 */
  public String format() {
    return "¥" + NumberFormat.getNumberInstance(Locale.JAPAN).format(value);
  }
}
