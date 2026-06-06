-- =====================================================================
-- V1__init.sql
-- 旧 baseball-market（PHP）の bb_market スキーマを引き継ぐ初期マイグレーション。
--
-- 構成:
--   旧 database/migrations/split_users_table.sql + add_email_verification.sql を
--   適用済みの状態を「最終形」として CREATE TABLE で再現する。
--
-- 動作経路:
--   * 旧 DB を引き継ぐ場合: spring.flyway.baseline-on-migrate=true により、
--     既存テーブルが存在する環境では V1 は実行されず baseline 扱いとなる。
--   * 新規構築の場合: V1 が新規実行され、すべてのテーブルが作成される。
--
-- カラム名は旧 PHP の Repository 実装 SQL から逆引きして一致させている。
-- messages.bord_id は旧コードのタイポを意図的に保存している（旧データ互換のため）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. users（認証情報）
--    split_users_table.sql 適用後の状態 + add_email_verification.sql の
--    email_verified_at カラム追加を反映済み。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `users` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `email` VARCHAR(255) NOT NULL,
  `password` VARCHAR(255) NOT NULL,
  `login_at` DATETIME DEFAULT NULL,
  `delete_flg` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `email_verified_at` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 2. user_profiles（プロフィール情報）
--    split_users_table.sql で users から分離。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_profiles` (
  `user_id` INT NOT NULL,
  `username` VARCHAR(255) DEFAULT NULL,
  `age` INT DEFAULT NULL,
  `tel` VARCHAR(255) DEFAULT NULL,
  `zip` VARCHAR(255) DEFAULT NULL,
  `prefecture` VARCHAR(10) DEFAULT NULL,
  `city` VARCHAR(100) DEFAULT NULL,
  `street` VARCHAR(255) DEFAULT NULL,
  `building` VARCHAR(255) DEFAULT NULL,
  `pic` VARCHAR(255) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_user_profiles_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 3. email_verification_tokens（メール認証トークン）
--    add_email_verification.sql 由来。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `email_verification_tokens` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL,
  `token` VARCHAR(64) NOT NULL,
  `expires_at` DATETIME NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_token` (`token`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_expires_at` (`expires_at`),
  CONSTRAINT `fk_email_verification_tokens_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 4. categories（商品カテゴリ）
--    CategoryRepositoryImpl が参照する列: id, category_name, delete_flg
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `categories` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `category_name` VARCHAR(255) NOT NULL,
  `delete_flg` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 5. makers（商品メーカー）
--    MakerRepositoryImpl が参照する列: id, maker_name, delete_flg
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `makers` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `maker_name` VARCHAR(255) NOT NULL,
  `delete_flg` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 6. products（出品商品）
--    ProductRepositoryImpl の INSERT/UPDATE 文から列を逆引き。
--    pic1/pic2/pic3 は画像パス（旧 PHP は uploads/ に格納）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `products` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `product_name` VARCHAR(255) NOT NULL,
  `maker_id` INT NOT NULL,
  `category_id` INT NOT NULL,
  `price` INT NOT NULL,
  `product_comment` TEXT,
  `pic1` VARCHAR(255) DEFAULT '',
  `pic2` VARCHAR(255) DEFAULT '',
  `pic3` VARCHAR(255) DEFAULT '',
  `user_id` INT NOT NULL,
  `sold_out_flg` TINYINT NOT NULL DEFAULT 0,
  `delete_flg` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_products_user_id` (`user_id`),
  KEY `idx_products_category_id` (`category_id`),
  KEY `idx_products_maker_id` (`maker_id`),
  KEY `idx_products_delete_flg` (`delete_flg`),
  CONSTRAINT `fk_products_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_products_category_id`
    FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`),
  CONSTRAINT `fk_products_maker_id`
    FOREIGN KEY (`maker_id`) REFERENCES `makers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 7. boards（メッセージ掲示板スレッド）
--    MessageRepositoryImpl の INSERT (sale_user, buy_user, product_id, created_at) と
--    UserRepositoryImpl の "UPDATE boards SET delete_flg=1 WHERE sale_user=? OR buy_user=?" から逆引き。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `boards` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `sale_user` INT NOT NULL,
  `buy_user` INT NOT NULL,
  `product_id` INT NOT NULL,
  `delete_flg` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_boards_sale_user` (`sale_user`),
  KEY `idx_boards_buy_user` (`buy_user`),
  KEY `idx_boards_product_id` (`product_id`),
  CONSTRAINT `fk_boards_sale_user`
    FOREIGN KEY (`sale_user`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_boards_buy_user`
    FOREIGN KEY (`buy_user`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_boards_product_id`
    FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 8. messages（掲示板メッセージ）
--    MessageRepositoryImpl の INSERT (bord_id, send_at, to_user, from_user, msg, created_at) より。
--
--    ⚠ 旧コード由来のタイポ "bord_id" をそのまま保持している（"board_id" ではない）。
--      旧データを引き継ぐため、列名の修正は別マイグレーション（V2 以降）で慎重に行う。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `messages` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `bord_id` INT NOT NULL,
  `from_user` INT NOT NULL,
  `to_user` INT NOT NULL,
  `msg` TEXT NOT NULL,
  `send_at` DATETIME NOT NULL,
  `delete_flg` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_messages_bord_id` (`bord_id`),
  KEY `idx_messages_from_user` (`from_user`),
  KEY `idx_messages_to_user` (`to_user`),
  CONSTRAINT `fk_messages_bord_id`
    FOREIGN KEY (`bord_id`) REFERENCES `boards` (`id`),
  CONSTRAINT `fk_messages_from_user`
    FOREIGN KEY (`from_user`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_messages_to_user`
    FOREIGN KEY (`to_user`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- 9. likes（お気に入り）
--    LikeRepositoryImpl の INSERT (user_id, product_id, created_at) と
--    findByUserId で delete_flg を参照していることから逆引き。
--    物理削除 (DELETE) も併用されているため delete_flg はオプション扱い。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `likes` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL,
  `product_id` INT NOT NULL,
  `delete_flg` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_likes_user_id` (`user_id`),
  KEY `idx_likes_product_id` (`product_id`),
  CONSTRAINT `fk_likes_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_likes_product_id`
    FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
