package com.shimanamisan.baseballmarket.product.infrastructure;

import com.shimanamisan.baseballmarket.product.domain.Product;
import com.shimanamisan.baseballmarket.product.domain.SearchCriteria;
import org.springframework.data.jpa.domain.Specification;

final class ProductSpecifications {

  private ProductSpecifications() {}

  static Specification<Product> matches(SearchCriteria c) {
    return (root, query, cb) -> {
      var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
      predicates.add(cb.equal(root.get("deleteFlg"), (byte) 0));
      if (c.categoryId() != null && c.categoryId() > 0) {
        predicates.add(cb.equal(root.get("categoryId"), c.categoryId()));
      }
      if (c.makerId() != null && c.makerId() > 0) {
        predicates.add(cb.equal(root.get("makerId"), c.makerId()));
      }
      return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
    };
  }
}
