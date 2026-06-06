---
name: commit
description: コミットメッセージ規約に従ってコミットを作成する。ユーザーがコミットを依頼したときに使用する。
disable-model-invocation: true
---

以下のコミットメッセージ規約に従い、`git diff --staged` と `git status` を確認した上でコミットを作成してください。

## フォーマット

```
type(scope): 件名（日本語・50文字以内）

本文（任意）
```

## type 一覧

| type | 用途 |
|---|---|
| `feat` | 新機能追加 |
| `fix` | バグ修正 |
| `refactor` | 外部仕様を変えないコード改善 |
| `test` | テストコードの追加・修正 |
| `docs` | ドキュメント・changelog のみの変更 |
| `chore` | 設定・依存パッケージ・ビルド関連 |
| `style` | フォーマット・コードスタイルのみの変更（ロジック変更なし） |
| `perf` | パフォーマンス改善 |

## scope 一覧（このプロジェクト）

ドメイン領域: `user` / `product` / `message` / `like` / `mypage`
レイヤー補足: `domain` / `infra` / `app` / `presentation`
横断的: `auth` / `signup` / `shared` / `logger` / `db`
フロントエンド: `layout` / `js` / `css`
テスト: `test`（複数スコープにまたがる場合）

## 件名のルール

- 日本語・体言止めまたは動詞連用形（「〜を追加」「〜に対応」）
- 末尾にピリオド・句点を付けない
- 「何をしたか」ではなく「**何のために何をしたか**」が伝わるように書く

## 本文のルール

- 差分から読み取れる「何を変えたか（What）」は繰り返さない
- **変更の理由・背景（Why）** を書く
- 箇条書きは `-` を使う

## 良い例

```
fix(user): save() から login_time カラムへの参照を除去

users テーブルに login_time 列が存在しないため SQLSTATE[42S22] が
発生していた。INSERT 対象を (email, password, created_at) のみに限定し
PDOException を Logger に記録するよう修正。

- UserRepositoryImpl::save() の INSERT 列を修正
- PDOException のキャッチと Logger::error() による構造化ログを追加
```

## Co-Authored-By（AI 作成時は必須）

コミットメッセージの末尾に必ず付与する：

```
Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```