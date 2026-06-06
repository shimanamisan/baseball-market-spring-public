package com.shimanamisan.baseballmarket.product.domain;

public record ProductImage(String path) {

  public ProductImage {
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("画像パスが空です");
    }
  }

  public static ProductImage fromPath(String path) {
    return new ProductImage(path);
  }
}
