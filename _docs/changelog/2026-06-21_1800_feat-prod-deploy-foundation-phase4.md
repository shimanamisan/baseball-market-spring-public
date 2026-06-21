# 2026-06-21 18:00 本番デプロイ基盤フェーズ4（GitHub Actions デプロイワークフロー）

> ブランチ `feature/prod-deploy-workflow` / PR #69（develop へマージ済み）。フェーズ3（PR #68・[2026-06-21_1730](2026-06-21_1730_feat-prod-deploy-foundation-phase3.md)）に続き、build/push とデプロイを自動化する GitHub Actions ワークフローを追加する。これでデプロイ基盤フェーズ1〜4が揃う。

## 背景

フェーズ1〜3で「prod プロファイル」「本番 Dockerfile」「compose + deploy.sh」が揃った。最後に、イメージのビルド/push と本番サーバーへのデプロイを GitHub Actions でつなぐ。トリガーは決定どおり**手動（`workflow_dispatch`）のみ**とし、安定後に push 自動化へ移行できる形にする。

## 変更内容

### 1. `.github/workflows/deploy-production.yml`（新規）
- **トリガー**: `workflow_dispatch` のみ。入力 `run_deploy`（既定 true）が false なら build/push のみ。
- **build-and-push（ubuntu-latest）**: `./Dockerfile` から app イメージを build → `ghcr.io/<owner>/baseball-market-spring/app` に push（`latest` / `timestamp` / `sha_short`）。push は自動付与の `GITHUB_TOKEN`（`packages: write`）で足り**手動 Secret 不要**。`type=gha` キャッシュ利用。
- **deploy（self-hosted, `run_deploy=true` のとき）**: `secrets.PRODUCTION_ENV`(base64) を復号して `$HOME/deploy/baseball-market-spring/.env` に配置 → `deploy/scripts/deploy.sh` 実行。`PRODUCTION_ENV` 未設定ならサーバー既存 `.env` にフォールバック。private パッケージ時のみ `GHCR_TOKEN` / `GHCR_USERNAME` を使用。

### 2. `deploy/prod/README.md`
ワークフロー手順と必要な GitHub Secrets（`PRODUCTION_ENV` 必須、private 時 `GHCR_TOKEN`/`GHCR_USERNAME`）の節を追記。`PRODUCTION_ENV` 生成コマンドは Linux/macOS/共通（`openssl base64 -A`）を併記。

## 検証

| 項目 | 結果 |
| --- | --- |
| YAML 構文 | `yaml.safe_load` OK |
| actionlint | exit 0（指摘なし） |

## 残課題・申し送り（実環境セットアップ — ユーザー側）

`shimanamisan` は **User アカウント**のため、self-hosted runner も Secrets も**リポジトリ単位**である点に注意（他リポジトリの設定は引き継がれない）。

| 項目 | 対応 |
| --- | --- |
| self-hosted runner | **このリポジトリに登録が必要**（API 上 0 件）。同一サーバーに別 runner を併存登録する |
| GitHub Secrets | `PRODUCTION_ENV`（必須）。private パッケージのため pull 用に `GHCR_TOKEN` / `GHCR_USERNAME` を推奨 |
| ワークフローの可視性 | `workflow_dispatch` の Run ボタンは**デフォルトブランチ（main）にファイルがある時のみ**表示。`develop → main` 到達が前提 |
| NPM / DNS | この app 用の Proxy Host（forward `baseball-market-spring-app:8080`）追加、`APP_URL` と一致、SPF/DKIM 設定 |
| メール | 587/STARTTLS 前提（レンタルサーバ SMTP 等）。認証情報は `.env`(`PRODUCTION_ENV`) で注入 |

## デプロイ基盤の全体像（フェーズ1〜4 完了）

| フェーズ | PR | 内容 |
| --- | --- | --- |
| 1 | #64 | prod プロファイル / actuator / remember-me 外部化 |
| 2 | #65 | 本番用マルチステージ Dockerfile / .dockerignore |
| 3 | #68 | compose（app+db 隔離）/ env.template / deploy.sh / README |
| 4 | #69 | GitHub Actions ワークフロー |

これで Issue #57（prod プロファイル整備〜デプロイ基盤）のコード対応は一通り完了。残りは上記の実環境セットアップのみ。
