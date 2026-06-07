---
paths:
  - app/src/main/java/com/shimanamisan/baseballmarket/**/*
---

# Spring Boot DDD Architecture Rules

旧 `baseball-market`（PHP / DDD レイヤード）を踏襲した **Bounded Context × 4 レイヤー** 構成。
詳細な原則は [architecture.md](../architecture.md) を、コーディング規約は [coding-style.md](../coding-style.md) を参照。本ファイルは各レイヤーの実装イディオムを示す。

## パッケージ構造（context ごと）

```
com.shimanamisan.baseballmarket.<context>/
├── domain/          // エンティティ, Value Object(record), Repository インターフェース, ドメイン例外
├── application/     // ユースケース／サービス（@Service, @Transactional 境界, DTO）
├── infrastructure/  // Repository 実装（@Repository）, 外部 IO（Mail 等）
└── presentation/    // Controller（@Controller / @RestController）, Request/Response DTO
```

`shared` のみ `presentation` を持たず `domain` / `infrastructure` で構成する。

## 依存方向（厳守）

```
presentation ──▶ application ──▶ domain ◀── infrastructure
```

- `domain` は Spring に依存しない（`@Component` などを持ち込まない、純 POJO/record）
- `presentation` は `application` を呼ぶ。`Repository` を直接呼ばない
- 別 context への直接依存は禁止。跨ぐ場合は ID 値（`UserId` 等）を保持し application 層で連携

## Controller（presentation）

```java
package com.shimanamisan.baseballmarket.product.presentation;

import com.shimanamisan.baseballmarket.product.application.ProductService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/products")
    public String index(Model model) {
        model.addAttribute("products", productService.findAll());
        return "product/index"; // templates/product/index.html
    }

    @PostMapping("/products")
    public String create(@Valid @ModelAttribute ProductCreateRequest form,
                         BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "product/new";
        }
        productService.create(form.toCommand());
        return "redirect:/products";
    }
}
```

Ajax（`like` の非同期化など）は `@RestController` または `@ResponseBody` で JSON を返す。

## Application Service（application）

トランザクション境界はこの層のみ。`@Transactional` は application のサービスメソッドに付与する。

```java
package com.shimanamisan.baseballmarket.product.application;

import com.shimanamisan.baseballmarket.product.domain.Product;
import com.shimanamisan.baseballmarket.product.domain.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    @Transactional
    public ProductId create(CreateProductCommand command) {
        Product product = Product.create(command.name(), command.price());
        return productRepository.save(product);
    }
}
```

## Repository（domain にインターフェース / infrastructure に実装）

```java
// domain
package com.shimanamisan.baseballmarket.product.domain;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Optional<Product> findById(ProductId id);
    List<Product> findAll();
    ProductId save(Product product);
}
```

```java
// infrastructure
package com.shimanamisan.baseballmarket.product.infrastructure;

import com.shimanamisan.baseballmarket.product.domain.Product;
import com.shimanamisan.baseballmarket.product.domain.ProductId;
import com.shimanamisan.baseballmarket.product.domain.ProductRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository jpaRepository; // Spring Data JPA を委譲利用

    public ProductRepositoryImpl(ProductJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Product> findById(ProductId id) {
        return jpaRepository.findById(id.value());
    }

    @Override
    public List<Product> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public ProductId save(Product product) {
        return new ProductId(jpaRepository.save(product).getId());
    }
}
```

- Spring Data の型（`Page<>`, `Pageable` など）を Repository インターフェースから外に漏らさない。必要なら application 用の DTO/値で受け渡す。

## ドメインエンティティ（domain）

集約が小さいうちは JPA Entity 兼務でよい（[architecture.md](../architecture.md) §5）。ドメインロジックは domain に閉じる。

```java
package com.shimanamisan.baseballmarket.product.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private int price;
    private boolean sold;

    protected Product() {} // JPA 用

    private Product(String name, int price) {
        this.name = name;
        this.price = price;
        this.sold = false;
    }

    public static Product create(String name, int price) {
        return new Product(name, price);
    }

    // ドメインロジックはエンティティに置く
    public void markAsSold() {
        if (this.sold) {
            throw new ProductAlreadySoldException(this.id);
        }
        this.sold = true;
    }

    public Long getId() { return id; }
}
```

## Value Object（Java 21 record）

プリミティブの引き回しを避け、生成時にコンパクトコンストラクタで検証する。検証失敗は `shared.domain.exception.ValidationException` を投げる。

```java
package com.shimanamisan.baseballmarket.user.domain;

import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;

public record Email(String value) {

    private static final String PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    public Email {
        if (value == null || !value.matches(PATTERN)) {
            throw new ValidationException("メールアドレスの形式が不正です");
        }
    }
}
```

## DI と Bean 登録

- コンストラクタインジェクションを使用（`new` でのインスタンス化を避ける）
- `@Service` / `@Repository` / `@Controller` のステレオタイプで自動登録される。Laravel のような per-feature ServiceProvider 手動バインドは不要
- インターフェース実装が 1 つなら型解決は自動。複数実装が出た場合のみ `@Qualifier` 等を検討

## ロギング

```java
private static final Logger log = LoggerFactory.getLogger(Xxx.class);

log.info("操作開始 param={}", param);
log.error("操作失敗", e); // 個人情報・パスワード・トークンは出力しない
```

## 禁止事項

- domain 層への Spring アノテーション持ち込み（`@Service` 等）
- Controller にビジネスロジックを書く
- `@Transactional` を presentation / domain / infrastructure に付ける
- `System.out.println` での恒久ログ
- Lombok の無断導入（[coding-style.md](../coding-style.md) 参照）
