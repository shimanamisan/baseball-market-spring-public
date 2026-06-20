---
name: seed-and-uploads-conventions
description: 開発シード(R__ Flyway)と画像配信(WebConfig)の設計判断。PR#56でレビュー。fixture画像とランタイムアップロードの分離・prod除外方針・既知の課題
metadata:
  type: project
---

PR #56「import-legacy-seed-data」でレビューした、開発用シードと画像配信の構成。

**画像配信(WebConfig)の構成（方式B採用後・PR#56で改修）**
- `shared/infrastructure/web/WebConfig.java`（`WebMvcConfigurer`）が `/uploads/**` を最大 2 ロケーションから配信。
  探索順は `file:${app.uploads.path}/`（ランタイムのユーザーアップロード保存先、Git非追跡、全プロファイル）→ `file:${app.seed.uploads.path}/`（シード fixture、`app.seed.uploads.path` が非空のときのみ追加）。
- DDL/レイヤー的に妥当: presentation でなく shared/infrastructure に置く判断は OK（横断的な Web インフラ設定）。
- パストラバーサルは Spring の PathResourceResolver が既定で防御するため追加対策不要。
- **fixture 画像（22枚, 3.2MB）は classpath 外の `app/seed-data/uploads/` にコミット**（旧: `src/main/resources/db/seed/uploads/`）。classpath 外なので本番 jar に同梱されない（旧構成では混入していた問題を解消）。リポジトリには残るので fresh clone のデモ性は維持。
- `app.seed.uploads.path` は `application.properties` で空既定、`application-dev.properties` で `/app/app/seed-data/uploads` を設定（devcontainer はリポジトリルートを `/app` にマウント）。空のとき WebConfig はロケーションを追加しないため prod 配信からも物理的に分離。

**Flyway シード(R__)の方針**
- `app/src/main/resources/db/seed/R__dev_seed_legacy_data.sql`（repeatable・data-only）。
- `application.properties` の `spring.flyway.locations` に `classpath:db/seed` を dev で追加。prod では db/migration のみとする方針（コメントで明記）。
- 冪等化のため先頭で対象テーブルを全 DELETE → 再 INSERT。`SET FOREIGN_KEY_CHECKS=0/1` で囲む。
- R__ は V__ の後・チェックサム変化時に再実行。スキーマ確定後に走るため順序は問題なし。

**この PR で挙げた主な指摘**
- SQL の prod 除外: `application.properties` 既定を `db/migration` のみとし、dev だけ `db/seed` を追加する方式に修正済み（コメント運用依存を解消）。SQL 自体は jar に同梱されるが prod の flyway.locations に無いため実行されない。
- 画像の prod 混入（旧構成の Warning）: 方式B（classpath 外 `app/seed-data/uploads/` へ移動 + `app.seed.uploads.path` プロパティ駆動）で解消。jar 内 `db/seed/uploads/` は 0 件であることを `unzip -l` で検証済み。
- 全 DELETE 方式は AUTO_INCREMENT を戻さない & 他データ巻き込みリスク。dev 限定なら許容。
- 旧データ匿名化（email=user{id}@example.com / password 共通 BCrypt）は妥当。

**注意: シードは平文 password='password' を全ユーザー共通の BCrypt で投入（dev のみ）。prod 混入は厳禁。**

**方式B 改修後レビュー（PR#56・2026-06-18 時点）で残った設計上の注意点**
- WebConfig は `@Value("${app.uploads.path}")` を**デフォルト値なしで注入**。`app.seed.uploads.path:` は空デフォルトあり。
  `app.uploads.path` が未定義のプロファイルで起動すると Bean 生成失敗（fail-fast）。application.properties に既定があるので現状は問題ないが、prod の application.properties/yml にも必ず定義が要る。
- `file:` ロケーションのパス組み立ては `"file:" + path + "/"` の文字列連結。`app.seed.uploads.path` の末尾にスラッシュを付けない前提（dev は `/app/app/seed-data/uploads`）。設定値に末尾 `/` を付けると `//` になる（PathResourceResolver は許容するが規約として末尾なしを統一）。
- カスタムハンドラ `/uploads/**` は Spring Boot デフォルト static ハンドラより優先されるため、`app.uploads.path=.../static/uploads` を指していても二重配信・競合にはならない（hasMappingForPattern で上書き）。
- `/uploads/**` は SecurityConfig で permitAll 対象（auth.md のサンプル準拠）。シード画像配信に追加認可は不要。
- 既知の弱点: prod での画像分離は「prod プロファイルで app.seed.uploads.path を空のまま据え置く」ことに依存。prod 設定ファイルでうっかり値を入れても**そのパスにファイルが無ければ配信されない**ため実害は限定的（fixture は classpath 外なので jar に無い）。SQL 側も flyway.locations の prod 既定が db/migration のみで担保。
