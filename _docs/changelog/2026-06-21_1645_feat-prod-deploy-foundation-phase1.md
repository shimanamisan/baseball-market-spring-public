# 2026-06-21 16:45 本番デプロイ基盤フェーズ1（prod プロファイル + actuator + remember-me 外部化）

> ブランチ `feature/prod-deployment-foundation` / PR #64（develop へマージ済み）。Issue #57（prod プロファイル整備）に着手。旧 Laravel プロジェクトのデプロイ構成（GitHub Actions → ghcr.io → self-hosted runner → deploy.sh、`PRODUCTION_ENV` base64 で `.env` 注入）を参考に、Spring Boot 向けの本番デプロイ基盤を段階的に整備するフェーズ1（コードに閉じた最小単位）。

## 背景

本番デプロイ用の設定（prod プロファイル・ヘルスチェック）が未整備だった。`application.properties` は dev 寄りの値が直書きされており（datasource はコンテナ名直指定・MailHog・`show-sql=true` 等）、本番では上書きが必要。デプロイ自動化（後続フェーズ）の前提として、まず「prod プロファイルで秘匿値を環境変数注入する」「起動時マイグレーション完了を判定できるヘルスチェック」をコードに用意する。

## 変更内容

### 1. `application-prod.properties`（新規）
`SPRING_PROFILES_ACTIVE=prod` で有効化。秘匿値は直書きせず環境変数（`.env` / `PRODUCTION_ENV`）から注入する。
- DB / メール接続を env 注入（`${DB_*}` / `${MAIL_*}`）。DB host は旧 baseball-market の MySQL との alias 衝突を避けコンテナ名固定を前提
- 本番トグル: `show-sql=false` / `open-in-view=false` / `thymeleaf.cache=true`
- Nginx Proxy Manager（NPM、TLS終端）配下のため `forward-headers-strategy=native` + Secure Cookie
- `app.url` を env 注入（メール認証リンクの生成元のため本番 https ドメイン必須）

### 2. Actuator ヘルスチェック
- `spring-boot-starter-actuator` を追加し `/actuator/health` のみ公開（`management.endpoints.web.exposure.include=health`、`show-details=never`）
- DB 健全性（= Flyway 起動時マイグレーション完了）を含めて判定。SMTP 到達性でヘルスが揺れないよう `management.health.mail.enabled=false`
- `SecurityConfig` に `/actuator/health` を `permitAll` 追加（無認証で疎通確認させる）

### 3. JDBC 接続文字列の charset 修正（PR レビュー由来）
- `useUnicode=true&characterEncoding=utf8` を削除（prod・dev 両方）。小文字 `utf8` は Connector/J 公式未定義値で utf8mb3 化（4バイト文字破損）のリスク。9.1.0 は省略時 utf8mb4 を既定とするのが公式ベストプラクティス

### 4. remember-me 署名キーの外部化（セキュリティ）
- 本リポジトリは public ミラーへ公開されるため、`SecurityConfig` のハードコード署名キーが実質公開状態だった
- コンストラクタ DI で `@Value("${app.remember-me.key}")` 注入に変更。base は dev 既定値、prod は `${REMEMBER_ME_KEY}`（既定値なし＝未設定なら起動失敗 fail-fast）

## 検証

| 項目 | 結果 |
| --- | --- |
| prod プロファイル起動（devcontainer・クリーンスキーマ） | `/actuator/health` が 200 `{"status":"UP"}`。本番初回デプロイ相当を再現 |
| Flyway の本番安全性 | `db/migration` のみを 0 から適用（`1 - init`）。dev シード（`db/seed`）が本番に渡らないことを実証 |
| utf8mb4 担保 | Connector/J 9.1.0 実接続で `character_set_connection=utf8mb4`、絵文字 ⚾🔥 の往復が無損失（`char_length=2` / `hex=E29ABE F09F94A5`） |
| health 許可の最小化 | `/actuator/health/**` を削除し base パスのみ許可。`/actuator/health/db` は 302（無認証露出なし） |
| remember-me fail-fast | `REMEMBER_ME_KEY` 未設定で context 初期化失敗、設定で正常起動を確認 |
| 全テスト | `./gradlew test` BUILD SUCCESSFUL（actuator 追加の回帰なし） |

## 残課題・申し送り

| 項目 | 状況 |
| --- | --- |
| dev の `bbuser`/`password` 直書き | dev 限定・ローカル使い捨て（外部到達不可）のため許容。厳格化は別途判断 |
| `REMEMBER_ME_KEY` | フェーズ3の `env.template` / `PRODUCTION_ENV` に十分な長さのランダム値で追加すること |
| 後続 | フェーズ2: Dockerfile / フェーズ3: deploy/prod compose + deploy.sh / フェーズ4: deploy workflow |
