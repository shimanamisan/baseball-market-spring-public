# Git-Flow 運用ルール

本プロジェクトは Git-Flow を採用しています。**コードの編集を開始する前に、必ず適切なブランチを作成してから着手してください。**

## ブランチ構成

| ブランチ        | 役割                                 | 派生元      | マージ先         |
|-------------|------------------------------------|----------|--------------|
| `main`      | 本番リリース済みの安定コード                      | -        | -            |
| `develop`   | 次回リリースに向けた統合ブランチ                    | `main`   | `main` (リリース時) |
| `feature/*` | 新機能の開発                             | `develop`| `develop`    |
| `bugfix/*`  | `develop` ベースのバグ修正                  | `develop`| `develop`    |
| `release/*` | リリース準備 (バージョン番号確定・最終調整)              | `develop`| `main`, `develop` |
| `hotfix/*`  | 本番障害の緊急修正                          | `main`   | `main`, `develop` |

## ブランチ命名規則

- `feature/<topic>` 例: `feature/user-signup-email-verification`
- `bugfix/<topic>` 例: `bugfix/login-redirect-loop`
- `release/<x.y.z>` 例: `release/1.2.0`
- `hotfix/<topic>` 例: `hotfix/password-reset-token-leak`

`<topic>` はケバブケース、英数字とハイフンのみ。短く具体的に。

## Claude が新規実装/修正タスクを開始する際の手順

1. **作業前にブランチを確認する**
   ```bash
   git status
   git branch --show-current
   ```
2. **現在ブランチが `main` / `develop` の場合は新規ブランチを作成する** (これらに直接コミットしない)
   - 新機能 → `git checkout develop; git pull --ff-only; git checkout -b feature/<topic>`
   - バグ修正 → `git checkout develop; git pull --ff-only; git checkout -b bugfix/<topic>`
   - 本番障害 → `git checkout main; git pull --ff-only; git checkout -b hotfix/<topic>`
3. **既に `feature/*` 等の作業ブランチにいる場合は、タスクの内容がブランチの目的と一致しているか確認する**
   - 別タスクの場合は新しいブランチを切る
4. **ブランチ作成後にコード編集に着手する**

## 必須ルール

- `main` / `develop` への直接コミット禁止
- ブランチ作成前にコード編集ツール (Edit / Write) を実行しない
- ユーザーから「ブランチを切らずに作業して」「現在のブランチで続行して」と明示された場合のみ例外
- マージは PR 経由 (`gh pr create`) を原則とする
- リリース・ホットフィックス時は `main` と `develop` の両方へマージし、Git タグ `vX.Y.Z` を付与する

## コミットメッセージ

`.claude/skills` の `commit` スキル定義に従う。Conventional Commits ライク (`feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:` ...) を採用。
