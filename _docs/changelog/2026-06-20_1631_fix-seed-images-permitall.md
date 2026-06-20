# 2026-06-20 16:31 シード画像（/seed-images/**）の認可漏れ修正と起動トラブルの切り分け

> ブランチ `feature/import-legacy-seed-data`。`2026-06-17_2243_feat-import-legacy-seed-data.md` の続き。商品一覧でシード画像が表示されない不具合を調査し、原因の認可漏れを修正した。あわせて `b51232d` で実施済みだが未記載だった「シード画像の名前空間分離」と、調査中に判明した dev プロファイル未活性による起動失敗の罠を記録する。

## 背景

初回シード投入（PR #56・前 changelog）後の追い込みで、`b51232d` にてシード fixture 画像をランタイムアップロードから分離した。しかし devcontainer 内でビルド後に商品画像が表示されない（壊れた画像になる）状態だったため、本セッションで原因特定と修正を行った。

## 変更内容

### 1. シード画像の名前空間分離（`b51232d`・本 changelog で追記記録）

前 changelog 時点の構成（`/uploads/**` を 2 ロケーションから配信・fixture は `app/seed-data/uploads/`）を、URL 名前空間ごと分離する構成へ変更済み。

| 項目 | 変更後 |
| --- | --- |
| シード画像の URL | `/seed-images/**`（ランタイムの `/uploads/**` と完全分離） |
| 配信 Bean | `shared/infrastructure/web/SeedImageWebConfig`（新規・`@Profile("dev")` 限定） |
| 配信元 | `file:${app.seed.images.path}/`（= `app/seed-data/images/`・classpath 外・22 画像） |
| プロパティ | `app.seed.images.path`（`application-dev.properties` でのみ定義。prod は Bean 非ロードで未使用） |
| シード SQL の pic 値 | `seed-images/xxxxx.jpeg`（テンプレートは `@{'/' + ${pic1}}` で `/seed-images/...` を生成） |

本番混入対策が「設定（空プロパティ）」依存から「**構造（`@Profile("dev")` で Bean 自体が本番に存在しない）**」依存へ強化された。`WebConfig`（`/uploads/**`・全プロファイル）はランタイムアップロード専用として残置。

### 2. `/seed-images/**` の認可漏れ修正（本セッションの主修正）

- **原因**: `b51232d` で `/uploads/**` から `/seed-images/**` へ名前空間を分離した際、`SecurityConfig` の permitAll リストに `/seed-images/**` を追加し忘れていた（`/uploads/**` のみ残存）。商品一覧トップ `/` は未ログインで閲覧できる公開ページのため、`<img src="/seed-images/...">` が認証フィルタに弾かれ **ログイン画面へ 302 リダイレクト → 壊れた画像** になっていた。
- **修正**: `SecurityConfig` の permitAll に `/seed-images/**` を追加（`/uploads/**` の直後）。

```diff
 "/uploads/**",
+"/seed-images/**",
 "/assets/**",
```

- permitAll リストは全プロファイル共通だが、`/seed-images/**` の配信ハンドラは dev 限定（`SeedImageWebConfig` が `@Profile("dev")`）。本番ではハンドラが無く `/seed-images/*` は permitAll でも 404 になるだけで害はない（`/uploads/**` と同じ扱い）。

### 3. 起動失敗（Flyway バリデーション）の切り分け — コード変更なし

調査の最初、`./gradlew bootRun`（args なし）が以下で**起動失敗**していた（`BUILD SUCCESSFUL` 表示でも Spring は `Application run failed`）。

```
No active profile set, falling back to 1 default profile: "default"
...
Validate failed: Migrations have failed validation
Detected applied migration not resolved locally: dev seed legacy data.
```

- **原因**: compose に `SPRING_PROFILES_ACTIVE=dev` を追加（`6c5f858`）した後、devcontainer を再ビルドしていない**古いコンテナ**で起動していたため dev が未活性。dev 無効 → `spring.flyway.locations` が `db/migration` のみに戻る → DB に過去（dev 有効時）適用済みの repeatable `dev seed legacy data` が残っているのに locations から解決できず Flyway バリデーション失敗。
- **対処**: **Dev Containers: Rebuild Container** で compose の env を反映（repair 不要）。再ビルド後はコンテナに `SPRING_PROFILES_ACTIVE=dev` が入り、素の `bootRun` で `dev` 起動する。暫定確認は `./gradlew bootRun --args='--spring.profiles.active=dev'`。

## 検証

ホストから稼働中コンテナ（`SPRING_PROFILES_ACTIVE=dev` を `docker exec ... printenv` で確認）に対し、未ログインで実施。

| 項目 | 結果 |
| --- | --- |
| `GET /`（一覧トップ） | ✅ 200 |
| `GET /seed-images/descent_spike.jpg`（商品237） | ✅ 200 `image/jpeg` |
| `GET /seed-images/<hash>.jpeg`（任意のシード画像） | ✅ 200 `image/jpeg` |
| ブラウザでの商品画像表示 | ✅ 表示される（ユーザー確認済み） |

修正前は `/seed-images/*` が 302（→ /login）。

## 分析・判断記録

- 名前空間分離後に permitAll を更新し忘れるのは典型的な見落とし。`/uploads/**` と `/seed-images/**` は配信責務が別（ランタイム / dev fixture）でも、どちらも未ログイン公開ページから参照されるため認可は両方 permitAll が必要。
- dev プロファイルは「flyway.locations への db/seed 追加」と「`SeedImageWebConfig` のロード」の両方を駆動する単一スイッチ。未活性だと画像配信ハンドラ消失と Flyway 起動失敗が同時に起きるため、画像不具合の調査時はまず active profile を確認するのが早い。

## 残課題・未確定

| 項目 | 状況 |
| --- | --- |
| 本修正のコミット／push・develop への PR | 未実施（`SecurityConfig` 変更が未コミット、`b51232d` も origin 未 push） |
| 商品237の本物 UNDER ARMOUR 画像 | 前 changelog から継続。暫定で `descent_spike.jpg` |
