# baseball-market-spring プロジェクトメモ

旧 `baseball-market`（PHP / DDD レイヤード）を Spring Boot にフルリプレースするリポジトリ。

## 必読ルール

このプロジェクトで作業する際は、以下のルール文書を必ず参照すること。指示と齟齬がある場合はユーザーに確認する。

- [.claude/architecture.md](.claude/architecture.md) — Bounded Context・レイヤー構成・依存方向・Value Object 方針
- [.claude/coding-style.md](.claude/coding-style.md) — Java / Spring Boot のコーディング規約
- [.claude/replacement-policy.md](.claude/replacement-policy.md) — 旧 PHP 構成からのマッピングと未決事項

## 現状

- Spring Boot 3.4.x + Java 21 + Gradle 構成（`app/`）
- 依存: Web / Thymeleaf / Spring Data JPA / Validation / Flyway / MySQL Connector
- パッケージルート: `com.shimanamisan.baseballmarket`
- メインクラス: `com.shimanamisan.baseballmarket.BaseballMarketSpringApplication`
- DB: `bb_market` / user `bbuser` / MySQL 8（devcontainer の docker-compose で起動）
- スキーマ管理: Flyway（`app/src/main/resources/db/migration/V*__*.sql`、現状マイグレーション未作成）
- 機能リプレースは未着手。最初の対象は `user` context（signup → メール認証 → login）の予定

## 作業着手前のチェック

1. 該当機能がどの context に属するかを特定
2. `architecture.md` のレイヤー責務に沿って必要なクラスを洗い出す
3. `replacement-policy.md` の「要確定」項目に触れる場合はユーザーへ確認
4. 旧 `/mnt/docker_work/baseball-market/public/src/<Context>/` を参照して責務・命名を踏襲

## 旧プロジェクトの場所

`/mnt/docker_work/baseball-market`（同じワークスペース内）。仕様確認の一次資料として参照可能。
