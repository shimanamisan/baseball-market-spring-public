package com.shimanamisan.baseballmarket.product.application;

import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import com.shimanamisan.baseballmarket.product.domain.Category;
import com.shimanamisan.baseballmarket.product.domain.CategoryRepository;
import com.shimanamisan.baseballmarket.product.domain.Maker;
import com.shimanamisan.baseballmarket.product.domain.MakerRepository;
import com.shimanamisan.baseballmarket.product.domain.Price;
import com.shimanamisan.baseballmarket.product.domain.Product;
import com.shimanamisan.baseballmarket.product.domain.ProductId;
import com.shimanamisan.baseballmarket.product.domain.ProductName;
import com.shimanamisan.baseballmarket.product.domain.ProductRepository;
import com.shimanamisan.baseballmarket.product.domain.SaleHistoryItem;
import com.shimanamisan.baseballmarket.product.domain.SearchCriteria;
import com.shimanamisan.baseballmarket.product.domain.SearchResult;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductService {

  private static final int COMMENT_MAX = 500;

  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final MakerRepository makerRepository;

  public ProductService(
      ProductRepository productRepository,
      CategoryRepository categoryRepository,
      MakerRepository makerRepository) {
    this.productRepository = productRepository;
    this.categoryRepository = categoryRepository;
    this.makerRepository = makerRepository;
  }

  public ProductId register(int userId, ProductRegistrationCommand cmd) {
    validateCommon(cmd);

    Product product = new Product(
        ProductName.fromString(cmd.name()).value(),
        cmd.categoryId(),
        cmd.makerId(),
        Price.fromInt(cmd.price()).value(),
        cmd.comment(),
        userId,
        cmd.pic1Path(),
        cmd.pic2Path(),
        cmd.pic3Path());

    return productRepository.save(product);
  }

  public void update(ProductId productId, int userId, ProductRegistrationCommand cmd) {
    Product existing = productRepository
        .findByIdAndOwner(productId, userId)
        .orElseThrow(() -> new ValidationException("商品が見つかりません"));
    if (!existing.canEdit(userId)) {
      throw new ValidationException("この商品を編集する権限がありません");
    }
    validateCommon(cmd);

    existing.setProductName(ProductName.fromString(cmd.name()).value());
    existing.setCategoryId(cmd.categoryId());
    existing.setMakerId(cmd.makerId());
    existing.setPrice(Price.fromInt(cmd.price()).value());
    existing.setProductComment(cmd.comment());
    if (cmd.pic1Path() != null) existing.setPic1(cmd.pic1Path());
    if (cmd.pic2Path() != null) existing.setPic2(cmd.pic2Path());
    if (cmd.pic3Path() != null) existing.setPic3(cmd.pic3Path());
  }

  @Transactional(readOnly = true)
  public Optional<Product> findById(ProductId id) {
    return productRepository.findById(id);
  }

  @Transactional(readOnly = true)
  public List<Product> findByOwner(int userId) {
    return productRepository.findByOwner(userId);
  }

  @Transactional(readOnly = true)
  public SearchResult search(SearchCriteria criteria) {
    return productRepository.search(criteria);
  }

  @Transactional(readOnly = true)
  public List<Category> listCategories() {
    return categoryRepository.findAll();
  }

  @Transactional(readOnly = true)
  public List<Maker> listMakers() {
    return makerRepository.findAll();
  }

  public void deleteProduct(ProductId productId, int userId) {
    Product product = productRepository
        .findByIdAndOwner(productId, userId)
        .orElseThrow(() -> new ValidationException("商品が見つかりません"));
    if (!product.canEdit(userId)) {
      throw new ValidationException("この商品を削除する権限がありません");
    }
    productRepository.softDelete(productId);
  }

  @Transactional(readOnly = true)
  public List<SaleHistoryItem> getSaleHistory(int userId) {
    return productRepository.findSaleHistory(userId);
  }

  private void validateCommon(ProductRegistrationCommand cmd) {
    if (cmd.categoryId() <= 0) {
      throw new ValidationException("category", "カテゴリを選択してください");
    }
    if (cmd.makerId() <= 0) {
      throw new ValidationException("maker", "メーカーを選択してください");
    }
    if (cmd.comment() != null && cmd.comment().length() > COMMENT_MAX) {
      throw new ValidationException("comment", "詳細は500文字以内で入力してください");
    }
    // category / maker 存在チェック（FK 制約だが事前に弾く方が UX 良）
    if (categoryRepository.findById(cmd.categoryId()).isEmpty()) {
      throw new ValidationException("category", "選択されたカテゴリが存在しません");
    }
    if (makerRepository.findById(cmd.makerId()).isEmpty()) {
      throw new ValidationException("maker", "選択されたメーカーが存在しません");
    }
  }
}
