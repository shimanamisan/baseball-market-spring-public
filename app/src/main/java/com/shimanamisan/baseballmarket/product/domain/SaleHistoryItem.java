package com.shimanamisan.baseballmarket.product.domain;

import java.time.LocalDateTime;

/**
 * 売買履歴の 1 行（JOIN 結果のフラット表現）。
 */
public record SaleHistoryItem(
    int productId,
    String productName,
    int price,
    String pic1,
    int buyUser,
    String buyerName,
    LocalDateTime soldAt,
    int boardId) {}
