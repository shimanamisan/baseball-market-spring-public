package com.shimanamisan.baseballmarket.product.domain;

/**
 * 商品検索条件。Application 層で組み立て Repository に渡す。
 *
 * sortOrder:
 *   0 / null → デフォルト（id DESC）
 *   1        → 価格昇順
 *   2        → 価格降順
 */
public record SearchCriteria(
    Integer categoryId,
    Integer makerId,
    Integer sortOrder,
    int page,
    int perPage) {

  public SearchCriteria {
    if (page < 1) page = 1;
    if (perPage < 1) perPage = 20;
  }

  public int offset() {
    return (page - 1) * perPage;
  }
}
