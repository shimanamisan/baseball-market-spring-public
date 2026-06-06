package com.shimanamisan.baseballmarket.product.domain;

public record ProductId(long value) {

  public ProductId {
    if (value < 0) {
      throw new IllegalArgumentException("商品IDは0以上の整数である必要があります");
    }
  }

  public static ProductId fromLong(long value) {
    return new ProductId(value);
  }

  public static ProductId temporary() {
    return new ProductId(0);
  }

  public boolean isTemporary() {
    return value == 0;
  }
}
