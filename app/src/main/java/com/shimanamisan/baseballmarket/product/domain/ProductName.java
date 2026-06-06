package com.shimanamisan.baseballmarket.product.domain;

import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;

public record ProductName(String value) {

  private static final int MAX_LENGTH = 255;

  public ProductName {
    if (value == null || value.isBlank()) {
      throw new ValidationException("name", "商品名を入力してください");
    }
    if (value.length() > MAX_LENGTH) {
      throw new ValidationException("name", "商品名は255文字以内で入力してください");
    }
  }

  public static ProductName fromString(String value) {
    return new ProductName(value);
  }
}
