# 2026-06-20 18:38 ランタイムアップロード先（app.uploads.path）の外部化

> ブランチ `chore/externalize-uploads-path`。Issue #57 / #58（実質同一・出自は PR #56 の Gemini レビュー `r3429315326`）に対応。ハードコードされた devcontainer 依存の絶対パスを、環境変数で上書き可能な構成へ変更しデプロイ可搬にする。

## 背景

`application.properties` の `app.uploads.path=/app/app/src/main/resources/static/uploads` は devcontainer のマウント（`../:/app`）前提のハードコード絶対パスで、(1) 別環境（マウント先が `/app` でない・ローカル直接実行）で解決できない、(2) 本番デプロイ（jar 実行）でその絶対パスが存在しない／書込権限が無いと失敗し得る、という可搬性の問題があった。

> 補正: Gemini の「`src/main/resources` は JAR 内部で読み取り専用だから書込失敗」という機序は不正確。`app.uploads.path` は `ImageStorage` が `Path.of(...)` + `Files.createDirectories` + `MultipartFile#transferTo` で**ファイルシステムパス**として消費し、`WebConfig` も `file:` ハンドラで配信する。実際の問題は「既定値が devcontainer マウントに固定され他環境で存在しないパスを指す」点。

## 変更内容

プロジェクト既定パターン（application.properties に prod 安全な既定／application-dev.properties で dev 上書き）に揃え、`flyway.locations` と同型にした。

### 1. `application.properties`（全プロファイル既定）
```properties
app.uploads.path=${APP_UPLOADS_PATH:/var/lib/baseball-market/uploads}
```
- 環境変数 `APP_UPLOADS_PATH` で上書き可能。
- 既定はデプロイ成果物（jar）の外にある永続ディレクトリ。本番は外部ボリュームを `APP_UPLOADS_PATH` でマウントする想定。

### 2. `application-dev.properties`（dev 上書き）
```properties
app.uploads.path=${APP_UPLOADS_PATH:/app/app/src/main/resources/static/uploads}
```
- 本番既定（`/var/lib/...`）は devcontainer に存在しないため、dev は従来どおりリポジトリ内 `static/uploads` を指す（挙動不変）。`APP_UPLOADS_PATH` 指定時はそちらを優先。

### 3. `app/src/test/resources/application.properties`（新規・テスト専用）
```properties
app.uploads.path=${java.io.tmpdir}/baseball-market-test-uploads
```
- `ImageStorage` はコンストラクタで `createDirectories` するため、dev プロファイル無しのテスト（CI・devcontainer 外）で既定 `/var/lib/...` を作れず `@SpringBootTest` の context 生成が失敗する潜在リスクを回避。環境非依存に書き込める tmpdir を使う。

## 検証

| 項目 | 結果 |
| --- | --- |
| 参照箇所の洗い出し | ハードコードパスは `application.properties` の 1 箇所のみ。消費者は `WebConfig` / `ImageStorage`（ともに `@Value("${app.uploads.path}")`）。docker-compose 等にパス依存なし |
| dev 起動（devcontainer・dev プロファイル） | application-dev.properties により従来パスのまま（挙動不変）。要 `./gradlew bootRun` で確認 |
| `./gradlew test`（dev プロファイル無し） | test/resources の tmpdir 既定で context 生成可能。要実機確認 |

> 本ホストに JDK が無いため、devcontainer 内で `./gradlew test` と `bootRun` の最終確認が必要。

## 残課題・未確定

| 項目 | 状況 |
| --- | --- |
| `application-prod.properties`/`yml` の整備 | 本対応は env 上書き口の用意まで。本番プロファイル本体はデプロイ準備フェーズで別途（Issue #57 の本来スコープ） |
| 本番の永続ボリューム（`/var/lib/baseball-market/uploads` 等）のマウント設定 | 本番環境構築時に対応 |
