---
name: seed-and-uploads-conventions
description: 開発シード(R__ Flyway)と画像配信(WebConfig/SeedImageWebConfig)の設計判断。PR#56→#59でレビュー。fixture画像とランタイムアップロードの名前空間分離・@Profile("dev")による構造的prod除外
metadata:
  type: project
---

PR #56→#59「import-legacy-seed-data」でレビューした、開発用シードと画像配信の構成。

**画像配信の構成（最終形・b51232d 以降）**

URL 名前空間ごとランタイムとシードを分離している。

- `shared/infrastructure/web/WebConfig.java`（`WebMvcConfigurer`・全プロファイル）が `/uploads/**` を `file:${app.uploads.path}/` から配信。ランタイムのユーザーアップロード保存先（Git 非追跡、環境ごとに増える）。
- `shared/infrastructure/web/SeedImageWebConfig.java`（`WebMvcConfigurer`・**`@Profile("dev")` 限定**）が `/seed-images/**` を `file:${app.seed.images.path}/` から配信。シード SQL が参照する fixture 画像専用。
- DDL/レイヤー的に妥当: presentation でなく shared/infrastructure に置く判断は OK（横断的な Web インフラ設定）。
- パストラバーサルは Spring の PathResourceResolver が既定で防御するため追加対策不要。ただし `app.seed.images.path` が空文字だと `file:/` となりルート全体が露出するため、`SeedImageWebConfig` のコンストラクタで blank 検証（fail-fast）＋末尾スラッシュ正規化を実施（#59 レビュー対応）。
- **fixture 画像（22枚, 3.2MB）は classpath 外の `app/seed-data/images/` にコミット**（旧: `src/main/resources/db/seed/uploads/`）。classpath 外なので本番 jar に同梱されない（旧構成では混入していた問題を解消）。リポジトリには残るので fresh clone のデモ性は維持。
- `app.seed.images.path` は `application-dev.properties` でのみ `/app/app/seed-data/images` を設定（devcontainer はリポジトリルートを `/app` にマウント）。`application.properties`（全プロファイル既定）には定義しない。
- シード SQL の pic 値は `seed-images/xxxxx.jpeg`。テンプレートは `@{'/' + ${pic1}}` で `/seed-images/...` を生成する。

**Flyway シード(R__)の方針**
- `app/src/main/resources/db/seed/R__dev_seed_legacy_data.sql`（repeatable・data-only）。
- `application.properties` の `spring.flyway.locations` 既定は `classpath:db/migration` のみ。`classpath:db/seed` の追加は **dev プロファイル限定**（`application-dev.properties`）。prod では db/seed を物理的に読まない。
- 冪等化のため先頭で対象テーブルを全 DELETE → 再 INSERT。`SET FOREIGN_KEY_CHECKS=0/1` で囲む。
- R__ は V__ の後・チェックサム変化時に再実行。スキーマ確定後に走るため順序は問題なし。
- **罠**: dev プロファイルが効いていない状態（compose env 追加後にコンテナ未 Rebuild 等）で起動すると、適用済みの `R__` が locations から解決できず Flyway バリデーション失敗で起動中断する。

**prod 混入対策（構造的に分離）**
- シード画像配信は `SeedImageWebConfig` が **`@Profile("dev")` 限定**のため、本番では Bean 自体がロードされず配信ハンドラが存在しない。物理（fixture は classpath 外で jar に無い）と構造（Bean 非ロード）の両面で本番から分離。旧設計の「prod でプロパティを空のまま据え置く運用依存」という弱点は解消済み。
- シード SQL も `flyway.locations` の prod 既定が `db/migration` のみで実行されない。
- `/seed-images/**` および `/uploads/**` は `SecurityConfig` で `permitAll`（未ログインの一覧/詳細から画像が参照されるため両方必要。`/seed-images/**` の permitAll 漏れで画像が 302 され壊れた不具合を #59 で修正）。

**この PR で挙げた主な指摘**
- SQL の prod 除外: 既定を `db/migration` のみとし dev だけ `db/seed` を追加する方式（コメント運用依存を解消）。
- 画像の prod 混入: `@Profile("dev")` 限定の `SeedImageWebConfig` ＋ classpath 外 fixture で構造的に解消。
- 全 DELETE 方式は AUTO_INCREMENT を戻さない & 他データ巻き込みリスク。dev 限定なら許容。
- 旧データ匿名化（email=user{id}@example.com / password 共通 BCrypt）は妥当。
- `app.seed.images.path` 空文字時のルート露出（Gemini security-high）: blank 検証＋末尾スラッシュ正規化で対応。`@Profile("dev")` + デフォルト値なし注入（未定義は fail-fast）のため実害は dev での明示的空設定に限られ、重大度は dev の堅牢化。

**注意: シードは平文 password='password' を全ユーザー共通の BCrypt で投入（dev のみ）。prod 混入は厳禁。**
