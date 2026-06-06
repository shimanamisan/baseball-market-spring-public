package com.shimanamisan.baseballmarket.product.application;

/**
 * 商品登録／更新時の入力（DTO）。
 * Controller が画像保存後にこの record を組み立て、ProductService に渡す。
 */
public record ProductRegistrationCommand(
    String name,
    int categoryId,
    int makerId,
    int price,
    String comment,
    String pic1Path,
    String pic2Path,
    String pic3Path) {}
