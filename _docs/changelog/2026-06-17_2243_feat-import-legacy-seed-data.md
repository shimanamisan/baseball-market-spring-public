# 2026-06-17 22:43 開発用シード（旧 baseball-market 本番相当データ）の投入と画像不整合の修正

> ブランチ `feature/import-legacy-seed-data`。旧 PHP 版の本番相当データを開発環境へ流し込み、主動線を実データで確認できるようにする。あわせて旧データ由来の画像不整合（商品237）を修正し、シード fixture 画像とランタイムアップロードの配置を分離した。

## 背景

6 コンテキストの移植は完了済みだが、開発環境のデータが手入力の少数レコードのみで、一覧のページング・売買履歴・掲示板といった「データ量に依存する画面」を実データで確認できなかった。旧 `baseball-market` の本番相当ダンプ（`_docs/database/bb_market.sql`）を開発環境へ投入する。

投入後、商品一覧で **商品 id=237「【軟式用】UNDER ARMOUR 野球用スパイク」の画像が表示されない**（壊れた画像になる）不具合が判明したため、本対応で原因特定と修正もあわせて行う。

## 変更内容

### 1. 開発用シード（Flyway repeatable）

- `app/src/main/resources/db/seed/R__dev_seed_legacy_data.sql` を新設。`_docs/database/bb_market.sql` の INSERT を **data-only** で抽出した開発専用シード。
  - repeatable（`R__`）。先頭で対象テーブルを全削除 → 再投入（FK チェックは一時無効化）し、内容変更時に再実行されても重複キーにならない。
  - email/password は開発用に匿名化・統一（email は `user{id}@example.com`、password は全ユーザー共通の BCrypt ハッシュ）。
  - `email_verification_tokens` は旧ダンプにデータが無いため投入しない。
- **prod 安全性を「設定」で担保（fail-safe）**: デフォルトの `spring.flyway.locations` は `classpath:db/migration` のみ（全プロファイル共通・本番含む）。`db/seed` の追加は **dev プロファイル限定**（`application-dev.properties` で `classpath:db/migration,classpath:db/seed`）とし、`.devcontainer/docker-compose.yml` の `SPRING_PROFILES_ACTIVE=dev` で有効化する。本番デプロイは `--spring.profiles.active=prod` で起動するため db/seed は物理的に渡らない。コメント運用でなく設定で保証する。

### 2. 商品237の画像不整合の修正

- **原因**: 商品 id=237 の `pic1/pic2/pic3` が、存在しない 3 ファイル（`5061db06…` / `654e59c8…` / `35df7466….jpeg`）を指していた → `/uploads/…` が 404 → 画像が壊れて表示。元ダンプ `bb_market.sql` の時点で既に存在する **旧 DB 由来のデータ不整合**（移植ミスではなく、旧プロジェクトの uploads にも当該ファイルは存在しなかった）。
- **修正**: `uploads/` 内で唯一どの商品からも参照されていなかった実在のスパイク画像 `descent_spike.jpg`（700×530 JPEG）を `pic1` に設定し、`pic2`/`pic3` は空に。テンプレートは `pic1` 空文字をガードするため一覧・詳細とも正しく表示される。
- `_docs/database/bb_market.sql` は**旧本番の忠実なスナップショットとして未修正**。乖離（匿名化・本不整合の差し替え）は開発シード層（`R__…sql`）にのみ閉じ込め、シードヘッダにコメントで記録。

### 3. シード fixture 画像とランタイムアップロードの配置分離

旧構成では `static/uploads/` が「シード参照画像」と「アプリ稼働で増える実ユーザーアップロード（`app.uploads.path` の書き込み先）」を兼ねており、`git add` 時にどこまで含めるかが曖昧だった。役割で分離する。

| 配置 | 役割 | Git |
| --- | --- | --- |
| `app/src/main/resources/db/seed/uploads/`（22 画像・3.2MB） | シード SQL が参照する fixture 画像の**正規置き場** | **追跡する**（シード SQL と同梱、fresh clone / CI で解決可能） |
| `app/src/main/resources/static/uploads/` | ランタイムのユーザーアップロード保存先（`app.uploads.path`） | **中身は追跡しない**（`.gitkeep` のみ）。実アップロードはコミットされない |

- `shared/infrastructure/web/WebConfig.java`（新規 `WebMvcConfigurer`）で `/uploads/**` を **`file:${app.uploads.path}/`（ランタイム）→ `classpath:/db/seed/uploads/`（fixture）** の順に配信。配信 URL（`/uploads/xxx`）は単一名前空間のまま、実体だけを 2 系統に分離。
- `.gitignore`:
  - `static/uploads/*` を ignore、`!.gitkeep` で allowlist（ランタイムアップロードの誤コミット防止）。
  - `_docs/database/`（元ダンプ＋元画像）はローカル専用として ignore。コミットするシードは `db/seed/*.sql` ＋ fixture 画像 `db/seed/uploads/`。

### 4. DB 接続ホストの一意化（環境の罠の恒久対応）

- `spring.datasource.url` の host を `db`（サービス名）から **`baseball-market-spring-db`（コンテナ名）** へ変更。サービス名 `db` は旧 `baseball-market` の MySQL も同一外部ネットワーク上で同名エイリアスを持ち、DNS が両者へ曖昧解決して接続先が非決定になるため。コンテナ名なら spring 側 MySQL のみに解決される。

## 検証

| 項目 | 結果 |
| --- | --- |
| シードが参照する全 uploads パスの実在チェック | ✅ 欠損 0 件（商品237修正後） |
| `.gitignore` の追跡判定（`git check-ignore`） | ✅ ランタイム想定ファイルは ignored / `.gitkeep`・`db/seed/uploads/` の fixture は tracked |
| `compileJava` / `bootRun` での画像表示 | ⚠️ **未検証**。本ホストに JDK が無く（ビルドは devcontainer 内で実行）、コンテナ内での確認が必要 |

> コンテナ内検証手順: `cd app && ./gradlew compileJava` で `WebConfig` のコンパイル、`./gradlew bootRun` 起動後に `/uploads/descent_spike.jpg` が 200・商品237の画像表示を確認。シードは repeatable のため起動時にチェックサム差分で再投入され商品237が更新される。

## 分析・判断記録

### 1. 代替画像に `descent_spike.jpg` を採用

`uploads/` 内で唯一未参照（orphan）かつスパイク商品向けの実在画像であり、商品237（スパイク）の代替として最も妥当と判断。ユーザー確認のうえ採用。本物の UNDER ARMOUR 画像が用意できれば差し替え可能（pic2/pic3 追加も含め将来対応の余地）。

### 2. 元ダンプは未修正・乖離はシード層に閉じる

`bb_market.sql` は旧本番の忠実なスナップショットとして保全価値があるため修正しない。匿名化と本画像差し替えは「開発専用シード（`R__…`）」側でのみ行い、両者の差分はシードヘッダコメントで追跡可能にした。

### 3. コピーでなくリソースハンドラで配信

dev 起動時に fixture を `static/uploads/` へコピーする案もあったが、(a) 実行レイアウト（exploded source / packaged jar）に依存して壊れやすい、(b) ディスク上に重複が出る、ため不採用。`/uploads/**` を 2 ロケーションから配信するリソースハンドラなら、環境差に強く重複も無い。本番 jar には `db/seed/uploads/` が同梱されるが（数 MB）、シード SQL 同様 prod では未使用で害は無いと判断。

## コードレビュー対応（ddd-code-reviewer / PR #56）

| 指摘 | 重大度 | 対応 |
| --- | --- | --- |
| prod での db/seed 除外がコメント運用依存（実体の prod 設定が無く、デフォルトに db/seed が含まれていた） | Warning(W-1) | **修正**: デフォルトを `db/migration` のみに変更し、`db/seed` は dev プロファイル限定（`application-dev.properties` + devcontainer の `SPRING_PROFILES_ACTIVE=dev`）へ。本番デフォルトが安全側に倒れる fail-safe 設計に |
| 全 DELETE 方式が prod で走ると破壊的 | Warning(W-2) | W-1 解消により prod は db/seed を読まないためカバー |
| id 欠番/飛びが欠落バグと誤認され得る | Suggestion(S-3) | シードヘッダに「旧本番データの忠実再現」と注記 |
| 本番 jar への fixture 画像同梱 | Suggestion(S-2) | 現状許容。prod は seed SQL を実行せず DB に該当パス参照が無いため classpath フォールバックは無害なデッドウェイト。肥大化時に再評価 |
| パストラバーサル懸念 | — | 指摘なし（Spring `PathResourceResolver` が location 外解決を既定で遮断） |

## 残課題・未確定

| 項目 | 状況 |
| --- | --- |
| `compileJava` / `bootRun` での実画像表示確認 | devcontainer 内で要実施（本ホストに JDK 無し） |
| 商品237の本物 UNDER ARMOUR 画像 | 暫定で `descent_spike.jpg`。実画像が用意でき次第 `db/seed/uploads/` 追加＋シード差し替えで対応可 |
| 本番 jar への fixture 同梱 | 現状は許容。肥大が問題化したらビルド構成で dev 限定 sourceSet 等を検討 |
