# 2026-06-23 22:32 ミラー対象から本番デプロイワークフローを除外

> ブランチ `bugfix/mirror-exclude-deploy-workflow`。public リポジトリへのミラー push が `workflow` スコープ不足で失敗する事象を修正する。

## 背景・原因

`main` への push をトリガーに private → public（`baseball-market-spring-public`、ポートフォリオ用途）へ全体をミラーする `mirror-to-public.yml` が、ミラー push 時に以下のエラーで失敗するようになった。

```
refusing to allow a Personal Access Token to create or update workflow
`.github/workflows/deploy-production.yml` without `workflow` scope
```

- ミラーは push 前の除去ステップで **`mirror-to-public.yml` 自身だけ**を `rm` していた。それ以外の workflow ファイルは public へそのまま渡る。
- デプロイ基盤フェーズ4（PR #69）で `deploy-production.yml` を追加した結果、これが「mirror 以外で初めて public へ渡る workflow ファイル」になった。
- public へ workflow ファイルを作成/更新するには push に使う `PUBLIC_REPO_TOKEN` に `workflow` スコープが必須だが、当該トークンは `repo` のみ。そのため初めてここで拒否された。
- これまで成功していたのは、ミラーが自分自身を毎回消すため public へ渡る workflow ファイルが 0 件で、`workflow` スコープ要求が発生しなかったため。

## 変更内容

### `.github/workflows/mirror-to-public.yml`

除去ステップに `rm -f .github/workflows/deploy-production.yml` を追加し、本番デプロイワークフローをミラー対象から除外した。

```yaml
rm -f .github/workflows/mirror-to-public.yml
# 本番デプロイ用ワークフローも公開リポジトリ（ポートフォリオ用途）には不要。
# 公開側へ workflow ファイルを作成/更新すると PUBLIC_REPO_TOKEN に workflow
# スコープが要求されるため、最小権限維持のためミラー対象から除外する
rm -f .github/workflows/deploy-production.yml
```

## 方針（なぜトークン側ではなくミラー除外か）

`PUBLIC_REPO_TOKEN` に `workflow` スコープを付与する案（A）もあったが、以下の理由で除外案（B）を採用した。

- 本番デプロイ用ワークフローは public（ポートフォリオ）に置く意味が無く、Secret も無いため public 側で再実行されても失敗するだけ。
- トークンに `workflow` スコープを与えずに済み、**最小権限**を維持できる。本番デプロイの内容を public に晒さずに済む。
- 既存の設計思想（「このワークフロー自身は公開リポジトリには不要」）と一致する。

## 検証

| 項目 | 結果 |
| --- | --- |
| 次回 `main` push 時のミラー成功 | 要確認（マージ後の実 push で確認） |
