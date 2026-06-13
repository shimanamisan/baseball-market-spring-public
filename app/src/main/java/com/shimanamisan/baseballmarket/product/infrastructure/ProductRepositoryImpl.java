package com.shimanamisan.baseballmarket.product.infrastructure;

import com.shimanamisan.baseballmarket.product.domain.Product;
import com.shimanamisan.baseballmarket.product.domain.ProductId;
import com.shimanamisan.baseballmarket.product.domain.ProductRepository;
import com.shimanamisan.baseballmarket.product.domain.SaleHistoryItem;
import com.shimanamisan.baseballmarket.product.domain.SearchCriteria;
import com.shimanamisan.baseballmarket.product.domain.SearchResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepositoryImpl implements ProductRepository {

  private final ProductJpaRepository jpa;

  @PersistenceContext private EntityManager em;

  public ProductRepositoryImpl(ProductJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<Product> findById(ProductId id) {
    return jpa.findByIdAlive(toInt(id));
  }

  @Override
  public Optional<Product> findByIdForUpdate(ProductId id) {
    return jpa.findByIdAliveForUpdate(toInt(id));
  }

  @Override
  public Optional<Product> findByIdAndOwner(ProductId id, int userId) {
    return jpa.findByIdAndOwnerAlive(toInt(id), userId);
  }

  @Override
  public List<Product> findByOwner(int userId) {
    return jpa.findByOwnerAlive(userId);
  }

  @Override
  public List<Product> findByIdsAlive(List<Integer> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    return jpa.findByIdsAlive(ids);
  }

  @Override
  public SearchResult search(SearchCriteria c) {
    Sort sort = switch (c.sortOrder() == null ? 0 : c.sortOrder()) {
      case 1 -> Sort.by(Sort.Direction.ASC, "price");
      case 2 -> Sort.by(Sort.Direction.DESC, "price");
      default -> Sort.by(Sort.Direction.DESC, "id");
    };
    Pageable pageable = PageRequest.of(c.page() - 1, c.perPage(), sort);
    Page<Product> page = jpa.findAll(ProductSpecifications.matches(c), pageable);
    int totalPages = page.getTotalPages();
    return new SearchResult(page.getContent(), page.getTotalElements(), totalPages, c.page());
  }

  @Override
  public ProductId save(Product product) {
    Product saved = jpa.save(product);
    return ProductId.fromLong(saved.getId().longValue());
  }

  @Override
  public void softDelete(ProductId id) {
    jpa.findByIdAlive(toInt(id)).ifPresent(Product::markDeleted);
  }

  /**
   * 売買履歴 (products INNER JOIN boards LEFT JOIN user_profiles)。
   * Board / UserProfile は本 context の管轄外のためネイティブクエリで取得し、record にマップする。
   */
  @Override
  public List<SaleHistoryItem> findSaleHistory(int userId) {
    var sql = """
        SELECT p.id AS pid, p.product_name, p.price, p.pic1,
               b.id AS board_id, b.buy_user, b.created_at AS sold_at,
               up.username AS buyer_name
        FROM products AS p
        INNER JOIN boards AS b ON p.id = b.product_id
        LEFT JOIN user_profiles AS up ON b.buy_user = up.user_id
        WHERE p.user_id = :userId
          AND p.sold_out_flg = 1
          AND p.delete_flg = 0
          AND b.delete_flg = 0
        ORDER BY b.created_at DESC
        """;
    @SuppressWarnings("unchecked")
    List<Object[]> rows = em.createNativeQuery(sql).setParameter("userId", userId).getResultList();
    return rows.stream()
        .map(r -> new SaleHistoryItem(
            ((Number) r[0]).intValue(),
            (String) r[1],
            ((Number) r[2]).intValue(),
            (String) r[3],
            ((Number) r[5]).intValue(),
            (String) r[7],
            ((Timestamp) r[6]).toLocalDateTime(),
            ((Number) r[4]).intValue()))
        .toList();
  }

  private static int toInt(ProductId id) {
    long v = id.value();
    if (v > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("ProductId out of INT range: " + v);
    }
    return (int) v;
  }
}
