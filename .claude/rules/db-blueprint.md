---
paths:
  - app/src/main/resources/db/migration/**/*
  - app/src/main/java/com/shimanamisan/baseballmarket/**/domain/**/*
---

# Database Design Rules (Flyway + JPA)

旧 `baseball-market` の `database/migrations/*.sql`（`users` / `user_profiles` 分離後）を正とし、Flyway 形式へ移植する（[replacement-policy.md](../replacement-policy.md) §4）。

## 設計原則
- 第3正規形まで適用
- 適切なインデックス設計
- 外部キー制約で参照整合性を保証
- スキーマ変更は **必ず Flyway マイグレーション経由**。`spring.jpa.hibernate.ddl-auto=update` に頼らない（本番は `validate`/`none`）。

## 命名規則
| 対象 | 規則 | 例 |
|------|------|-----|
| テーブル | 複数形 snake_case | `products`, `user_profiles` |
| 主キー | `id` | - |
| 外部キー | `{単数形}_id` | `user_id`, `product_id` |
| 日時 | `{動詞}_at` | `created_at`, `approved_at` |
| フラグ | `is_{形容詞}` | `is_sold` |

## Flyway マイグレーション
- 配置: `app/src/main/resources/db/migration/`
- 命名: `V{連番}__{説明}.sql`（例: `V1__init.sql`, `V2__split_users_table.sql`）
- 適用済みマイグレーションは**編集しない**（新しい連番で変更を加える）。

```sql
-- V1__init.sql
CREATE TABLE products (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    name         VARCHAR(255) NOT NULL,
    price        INT          NOT NULL,
    description  TEXT         NULL,
    is_sold      TINYINT(1)   NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_products_user_id (user_id),
    KEY idx_products_user_sold (user_id, is_sold),
    CONSTRAINT fk_products_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 多対多（中間テーブル）
`like` は商品いいねの多対多。
```sql
-- V3__create_likes.sql
CREATE TABLE likes (
    user_id    BIGINT   NOT NULL,
    product_id BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, product_id),
    CONSTRAINT fk_likes_user    FOREIGN KEY (user_id)    REFERENCES users (id)    ON DELETE CASCADE,
    CONSTRAINT fk_likes_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## JPA エンティティ（マッピング）
集約が小さいうちはドメインエンティティ＝JPA Entity 兼務（[architecture.md](../architecture.md) §5）。`@Entity` は domain 配下に置くが、ドメインロジックを必ず持たせる（貧血ドメインを避ける）。

```java
package com.shimanamisan.baseballmarket.product.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;          // 別 context への参照は ID 値で保持

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int price;

    @Column(name = "is_sold", nullable = false)
    private boolean sold;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    protected Product() {}        // JPA 用
}
```

## リレーション指針
- 集約をまたぐ参照は **ID 値（`Long userId` / `UserId`）で保持** し、`@ManyToOne` で他 context のエンティティを直接ぶら下げない（context 境界を守る）。
- 同一集約内の関連のみ `@OneToMany` / `@ManyToOne` を使う。
- N+1 を避ける: 一覧取得は `@EntityGraph` または JPQL の `join fetch` を使い、コレクションの遅延ロードをループ内で踏まない。

## クエリ
- 単純な検索は Spring Data JPA の派生クエリ（`findByUserId` 等）。
- 商品検索（条件動的生成）は `Specification` または JPQL/`@Query`、複雑なら QueryDSL 導入をユーザーに相談。
- 生 SQL を使う場合はマイグレーション内に限定し、アプリコードでは原則 JPA を使う。

## 初期データ
- 開発用シードは Flyway の `R__` (repeatable) や別プロファイル限定マイグレーション、または `CommandLineRunner` で投入。本番マイグレーションに混ぜない。
