# 2026-06-24 16:41 本番デプロイ: ghcr.io ログイン `denied: denied` のトラブルシュート

> 手動トリガーの `deploy-production.yml`（self-hosted runner）実行時、`deploy.sh` の ghcr.io ログインで `denied: denied` が発生してデプロイが止まった事象と、その原因・恒久対策の記録。**同じ症状が再発したときに最初に読む手順書**。

## 症状

`Deploy to Production` ワークフローの `Run deploy script` ステップで以下が出て exit 1。

```
[INFO] Logging in to ghcr.io ...
Error response from daemon: Get "https://ghcr.io/v2/": denied: denied
Error: Process completed with exit code 1.
```

該当箇所は `deploy/scripts/deploy.sh` の ghcr ログイン処理。

```bash
if [ -n "${GHCR_TOKEN:-}" ]; then
  echo "$GHCR_TOKEN" | docker login ghcr.io -u "${GHCR_USERNAME:-}" --password-stdin
fi
```

## 原因の切り分け（重要な前提）

- **`Logging in to ghcr.io ...` のログが出ている時点で、`GHCR_TOKEN` Secret は空ではない**（空なら `if` でスキップされ警告ログになる）。つまり「Secret 未設定」ではなく「**渡した認証情報が ghcr.io に拒否されている**」段階。
- `.env` 生成（`PRODUCTION_ENV` の復号・配置）は **このログより手前で成功済み**。`denied` は `.env`／`PRODUCTION_ENV` とは無関係。

`denied: denied` の原因候補（実際の確認順）:

| # | 候補 | 今回の結果 |
| --- | --- | --- |
| 1 | PAT のスコープ不足（`read:packages` 無し） | スコープ画面で `read:packages` / `write:packages` ともにチェック有り → **シロ** |
| 2 | `GHCR_USERNAME` が空 or 誤り | `shimanamisan`（PAT 所有者と一致）で登録済み → **シロ** |
| 3 | **`GHCR_TOKEN` Secret に入っているトークン値が無効／古い／ズレている** | → **これが真因** |

## 真因と「最大の落とし穴」

**classic PAT のトークン文字列（`ghp_...`）は生成した瞬間の一度きりしか表示されない。**

- あとから *Edit personal access token* 画面でスコープを追加・確認しても、**トークンの値そのものは二度と表示されない**。
- そのため「スコープ画面で `read:packages` にチェックが入っているのを確認できた」≠「`GHCR_TOKEN` Secret に正しい値が入っている」。
- スコープが正しくても、`GHCR_TOKEN` Secret に古い値／別トークン／コピー欠け・空白混入のある値が入っていれば `denied: denied` になる。

→ **トークン値を直すには Edit ではなく `Regenerate token`（再生成）して、新しい値を `GHCR_TOKEN` Secret に貼り直すしかない。**

## 解決手順（再発時はこの順で対応）

1. **PAT を再生成する**
   GitHub → Settings → Developer settings → Personal access tokens → **Tokens (classic)** → 対象トークン → **Regenerate token**
   （`read:packages` にチェックが入った状態で。pull のみなら `read:packages` で足りる）
2. 表示された新しい `ghp_...` を **その場でコピー**（画面を離れると再表示不可）。
3. repo → Settings → Secrets and variables → Actions → **`GHCR_TOKEN`** → Update → 貼り付け。
   **前後に空白・改行を入れない。**
4. `GHCR_USERNAME` = `shimanamisan`（PAT 所有者と完全一致）であることを確認。
5. Actions → Deploy to Production → **Run workflow** で再実行。

### 事前に PAT 単体を検証したい場合（Secret 配線と PAT 有効性の切り分け）

self-hosted runner が動く本番サーバー上で直接ログインを試す。**トークンを `echo` でコマンドに直書きするとシェル履歴（`~/.bash_history`）に平文で残る**ため、対話入力でログインする（Password プロンプトに再生成した PAT を貼り付ける）:

```bash
docker login ghcr.io -u shimanamisan
# Username: shimanamisan（入力済み）
# Password: <再生成した PAT を貼り付け> ← 画面に表示されず履歴にも残らない
```

履歴に残さず非対話で行いたい場合は、`read -rs` で変数に読んでから `--password-stdin` に渡す:

```bash
read -rsp 'PAT: ' GHCR_PAT; echo
echo "$GHCR_PAT" | docker login ghcr.io -u shimanamisan --password-stdin; unset GHCR_PAT
```

- `Login Succeeded` → **PAT は有効**。残る原因は Secret 側（値ズレ・`GHCR_USERNAME` 未登録）。
- また `denied` → **PAT 側の問題**（再生成時にスコープ未保存・別トークン等）。

## 解決確認

再実行後、以下が出力されログイン問題は解消した。

```
[INFO] Logging in to ghcr.io ...
Login Succeeded
WARNING! Your credentials are stored unencrypted in '/home/.../.docker/config.json'. ...
```

- `WARNING! Your credentials are stored unencrypted ...` は **無害な通知**（docker が認証情報を `~/.docker/config.json` に平文保存した旨）。エラーではない。気になる場合は credential helper を導入すると消えるが、self-hosted runner では任意。

## 再発防止のチェックリスト

ghcr ログインで `denied: denied` を見たら、上から順に:

- [ ] ログに `Logging in to ghcr.io ...` が出ているか（出ていれば `GHCR_TOKEN` は空ではない＝「未設定」ではなく「無効」）
- [ ] PAT のスコープに **`read:packages`** があるか
- [ ] `GHCR_USERNAME` = `shimanamisan`（PAT 所有者と一致）か
- [ ] **`GHCR_TOKEN` Secret の値を、最後に PAT を *生成/再生成* したときの値から貼り直したか**（Edit でスコープ確認しただけでは値は変わらない＝直っていない）
- [ ] 迷ったら本番サーバー上で `docker login` 単体テストして PAT 有効性と Secret 配線を分離する

## 補足: そもそもログイン不要にする選択肢

ghcr パッケージを **public** にすれば `docker login` 自体が不要になり、`GHCR_TOKEN` / `GHCR_USERNAME` Secret も削除できる（`deploy.sh` は `GHCR_TOKEN` 未設定なら login をスキップし匿名 pull する）。private を維持する必要が無いなら、認証トラブルの根を断つ選択肢として検討の余地がある。今回は private 維持のまま PAT 再生成で解決した。

## 関連

- `deploy/prod/README.md` — 必要な GitHub Secrets（`PRODUCTION_ENV` / `GHCR_TOKEN` / `GHCR_USERNAME`）の一覧
- `.github/workflows/deploy-production.yml` — `deploy` ジョブで `GHCR_TOKEN` / `GHCR_USERNAME` を env 注入
- `deploy/scripts/deploy.sh` — ghcr ログイン処理（`GHCR_TOKEN` 未設定時はスキップ）
