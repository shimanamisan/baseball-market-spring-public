---
name: sync
description: PR マージ後にローカルを最新化する。develop / main を ff-only で更新し、マージ済みブランチを掃除し、関連 Issue を手動クローズする。「最新に更新して」「マージしたので同期して」等で使用。
disable-model-invocation: true
---

GitHub 上で PR がマージされた後、ローカルリポジトリを最新状態に同期する手順。本プロジェクトの git-flow（`feature/*` → `develop` → `main`）に従う。

## 前提

- ユーザーから「（develop / main を）マージした」「最新に更新して」等の指示があったときに実行する
- 作業ツリーに未コミットの変更がある場合は、先にユーザーへ確認する（`git stash` 等を勝手に行わない）

## 同期手順（一括実行可）

```bash
cd /mnt/docker_work/baseball-market-spring

# 1. リモートを取得し、削除済みリモートブランチの参照を掃除
git fetch --all --prune

# 2. develop を fast-forward で最新化
git checkout develop && git pull --ff-only origin develop

# 3. main を fast-forward で最新化
git checkout main && git pull --ff-only origin main

# 4. develop にマージ済みのローカル作業ブランチを削除（main/develop は除外）
#    ※ xargs -r は GNU 拡張のため、移植性のある while read ループを使う
git branch --merged develop | grep -vE '^\*|main|develop' | while read -r branch; do git branch -d "$branch"; done

# 5. develop に戻して状態を確認
git checkout develop
git branch -vv
```

## マージ後の Issue クローズ（重要）

本プロジェクトは **`develop` 経由でマージするため、`Closes #N` を書いても Issue は自動クローズされない**。
PR が解決した Issue が残っている場合は、マージ完了を確認したうえで手動クローズする。

```bash
# PR のマージ状態を確認
gh pr view <PR番号> --json state,mergedAt

# 該当 Issue が OPEN なら、対応内容を要約したコメント付きでクローズ
gh issue close <Issue番号> -c "PR #<PR番号> を develop にマージして対応完了（develop マージのため自動クローズされず手動クローズ）。<対応の要約>"
```

## ルール

- ブランチ更新は必ず `--ff-only`（意図しないマージコミットを作らない）
- ローカルブランチ削除は `-d`（マージ済みのみ削除する安全側）を使い、`-D` での強制削除はユーザー確認なしに行わない
- リモートに未 push のローカルブランチが `--merged` で削除候補に出た場合は、削除前にユーザーへ確認する
- 同期後は `develop` を作業の起点ブランチとする
