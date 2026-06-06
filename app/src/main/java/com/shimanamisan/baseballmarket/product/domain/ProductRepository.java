package com.shimanamisan.baseballmarket.product.domain;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

  Optional<Product> findById(ProductId id);

  Optional<Product> findByIdAndOwner(ProductId id, int userId);

  List<Product> findByOwner(int userId);

  SearchResult search(SearchCriteria criteria);

  ProductId save(Product product);

  void softDelete(ProductId id);

  List<SaleHistoryItem> findSaleHistory(int userId);
}
