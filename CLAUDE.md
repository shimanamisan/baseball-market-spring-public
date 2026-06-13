# baseball-market-spring プロジェクトメモ

旧 `baseball-market`（PHP / DDD レイヤード）を Spring Boot にフルリプレースするリポジトリ。

## 必読ルール

このプロジェクトで作業する際は、以下のルール文書を必ず参照すること。指示と齟齬がある場合はユーザーに確認する。

- [.claude/architecture.md](.claude/architecture.md) — Bounded Context・レイヤー構成・依存方向・Value Object 方針
- [.claude/coding-style.md](.claude/coding-style.md) — Java / Spring Boot のコーディング規約
- [.claude/replacement-policy.md](.claude/replacement-policy.md) — 旧 PHP 構成からのマッピングと未決事項

## 現状

- Spring Boot 3.4.x + Java 21 + Gradle 構成（`app/`）
- 依存: Web / Thymeleaf / Spring Data JPA / Validation / Spring Security / Spring Mail / Flyway（core + mysql）/ MySQL Connector（開発メールは MailHog）
- パッケージルート: `com.shimanamisan.baseballmarket`
- メインクラス: `com.shimanamisan.baseballmarket.BaseballMarketSpringApplication`
- DB: `bb_market` / user `bbuser` / MySQL 8（devcontainer の docker-compose で起動）
- スキーマ管理: Flyway（`app/src/main/resources/db/migration/V*__*.sql`、`V1__init.sql` 作成済み）

### リプレース進捗（フェーズ制）

旧 PHP の 6 つの Bounded Context のうち **5 つが移植完了**。残るは mypage のみ。

- ✅ `shared` / `user`（signup・メール認証・login・パスワード編集/リマインド・プロフィール編集・退会）/ `product`（一覧・詳細・出品・売買履歴）/ `message`（掲示板 + 購入フロー）/ `like`（Ajax いいね）
- ⬜ `mypage`（出品商品 / お気に入り / 参加中の掲示板の集約表示）— **次フェーズ（フェーズ 8）**
- 主動線「登録 → メール認証 → ログイン → 出品 → 一覧 → 詳細 → 購入 → 掲示板で連絡」は一通り稼働中
- 詳細な経緯は `_docs/changelog/` および `_docs/2026-06-13_0942_replacement-progress-analysis.md` を参照

## 作業着手前のチェック

1. 該当機能がどの context に属するかを特定
2. `architecture.md` のレイヤー責務に沿って必要なクラスを洗い出す
3. `replacement-policy.md` の「要確定」項目に触れる場合はユーザーへ確認
4. 旧 `/mnt/docker_work/baseball-market/public/src/<Context>/` を参照して責務・命名を踏襲

## 旧プロジェクトの場所

`/mnt/docker_work/baseball-market`（同じワークスペース内）。仕様確認の一次資料として参照可能。
