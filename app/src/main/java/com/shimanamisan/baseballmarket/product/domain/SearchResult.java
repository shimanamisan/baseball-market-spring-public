package com.shimanamisan.baseballmarket.product.domain;

import java.util.List;

/**
 * 商品検索結果。infrastructure 層の Page を外に漏らさないため。
 */
public record SearchResult(
    List<Product> items,
    long totalCount,
    int totalPages,
    int currentPage) {}
