---
name: pr
description: PR（プルリクエスト）を作成する。ユーザーが PR 作成を依頼したときに使用する。
disable-model-invocation: true
---

以下の手順と規約に従い、GitHub Pull Request を作成してください。

## 事前確認（必ず実行）

```bash
git status
git log main..HEAD --oneline        # main から分岐した全コミット
git diff main...HEAD --stat         # 変更ファイルの概要
```

## PR タイトルの規約

- **日本語**で記述する
- コミットメッセージと同じ `type(scope):` プレフィックスを使う
- 70文字以内
- 「何のための PR か」が一目でわかるように書く

```
# 例
feat(user): users/user_profiles テーブル分離とプロフィール編集対応
fix(user): サインアップ時の SQLSTATE[42S22] エラーを修正
test: UserRepository・UserService の PHPUnit テストを追加
```

## PR 本文のテンプレート

```markdown
## 概要

<!-- この PR が解決する課題・目的を1〜3行で説明 -->

## 変更内容

<!-- 主な変更を箇条書き。差分から明らかな What ではなく Why を意識する -->
-
-

## テスト方法

<!-- レビュアーが動作確認できる手順を箇条書きで記載 -->
- [ ]
- [ ]

## 関連情報

<!-- 関連 Issue・changelog・参考 URL など（なければ削除） -->
```

## 作成コマンド

```bash
gh pr create \
  --title "type(scope): タイトル" \
  --body "$(cat <<'EOF'
## 概要

...

## 変更内容

-

## テスト方法

- [ ]

🤖 Generated with [Claude Code](https://claude.ai/claude-code)
EOF
)"
```

## 作成時のルール

- ベースブランチは基本的に `main`（異なる場合は `--base` で指定）
- 現在のブランチが `main` の場合は、先に作業ブランチを作成するようユーザーに確認する
- リモートへの push が済んでいない場合は `git push -u origin HEAD` を先に実行する
- PR 本文の末尾に必ず以下を付与する:

```
🤖 Generated with [Claude Code](https://claude.ai/claude-code)
```