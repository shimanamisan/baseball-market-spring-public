# Git-Flow 運用ルール

本プロジェクトは Git-Flow をベースにした **簡略運用** を採用しています。**コードの編集を開始する前に、必ず適切なブランチを作成してから着手してください。**

小規模・少人数のため、原典 git-flow のように `release/*` ブランチを必ず経由するのではなく、**`develop` → `main` を直接マージし、`main` にバージョンタグ `vX.Y.Z` を付与する** 方式を正式運用とする（詳細は「リリース手順」を参照）。`release/*` はリリース準備に複数人での調整が必要になった場合に任意で使用する。

## ブランチ構成

| ブランチ        | 役割                                 | 派生元      | マージ先         |
|-------------|------------------------------------|----------|--------------|
| `main`      | 本番リリース済みの安定コード（タグ付与対象）             | -        | -            |
| `develop`   | 次回リリースに向けた統合ブランチ                    | `main`   | `main` (リリース時に直接マージ) |
| `feature/*` | 新機能の開発                             | `develop`| `develop`    |
| `bugfix/*`  | `develop` ベースのバグ修正                  | `develop`| `develop`    |
| `release/*` | （任意）リリース準備が必要な場合のみ使用              | `develop`| `main`, `develop` |
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

## リリース手順（develop → main 直接マージ）

通常のリリースは `release/*` を切らず、`develop` から `main` へ直接マージする。

1. `develop` が安定していることを確認する（テストが通る・主要動線の動作確認済み）
2. `develop` → `main` の PR を作成する（`gh pr create --base main --head develop`）
3. **PR のタイトル・説明は必ず記入する**（GitHub 既定の「Develop」「空欄」のまま作らない）
4. マージ後、`main` にバージョンタグ `vX.Y.Z` を付与して push する
   ```bash
   git checkout main && git pull --ff-only
   git tag -a vX.Y.Z -m "リリース vX.Y.Z"
   git push origin vX.Y.Z
   ```
5. ホットフィックス時は `main` と `develop` の両方へマージしてからタグを付与する

### リリース PR のタイトル・説明

- **タイトル**: `release(x.y.z): <リリースの要点>`（例: `release(0.2.0): product コンテキスト追加`）。自動生成の「Develop」は使わない
- **説明**: そのリリースに含まれる主要変更（changelog 抜粋）を箇条書きで記載し、後から「この本番リリースに何が入ったか」を追えるようにする

## 必須ルール

- `main` / `develop` への直接コミット禁止
- ブランチ作成前にコード編集ツール (Edit / Write) を実行しない
- ユーザーから「ブランチを切らずに作業して」「現在のブランチで続行して」と明示された場合のみ例外
- マージは PR 経由 (`gh pr create`) を原則とする
- リリースは `develop` → `main` の直接マージ + `main` へ `vX.Y.Z` タグ付与（PR のタイトル・説明は空欄にしない）
- ホットフィックス時は `main` と `develop` の両方へマージし、Git タグ `vX.Y.Z` を付与する

## コミットメッセージ

`.claude/skills` の `commit` スキル定義に従う。Conventional Commits ライク (`feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:` ...) を採用。
