package com.shimanamisan.baseballmarket.product.domain;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

  Optional<Product> findById(ProductId id);

  /**
   * 購入処理用に、対象商品を行ロック（SELECT ... FOR UPDATE 相当）して取得する。
   * 同一商品への同時購入を直列化し、二重購入（複数 boards 作成）を防ぐ。
   * 必ず application 層の @Transactional 内から呼ぶこと（ロックはトランザクション終了で解放される）。
   */
  Optional<Product> findByIdForUpdate(ProductId id);

  Optional<Product> findByIdAndOwner(ProductId id, int userId);

  List<Product> findByOwner(int userId);

  SearchResult search(SearchCriteria criteria);

  ProductId save(Product product);

  void softDelete(ProductId id);

  List<SaleHistoryItem> findSaleHistory(int userId);
}
