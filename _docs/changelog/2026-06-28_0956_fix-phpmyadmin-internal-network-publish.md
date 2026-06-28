# 2026-06-28 09:56 phpMyAdmin の LAN 公開を成立させる（`internal` 網のポート公開不成立を修正）

> ブランチ `bugfix/phpmyadmin-internal-network-publish`。phpMyAdmin が LAN からまったく開けなかった不具合の **根本原因** を修正した。原因はポート番号ではなく **ネットワーク構成**で、phpMyAdmin が `internal: true` のネットワークにしか参加していなかったためポート公開が黙って破棄されていた。通常ブリッジ網 `pma-public` を追加し、phpMyAdmin を `internal`（db 到達用）と `pma-public`（ホスト公開用）の両方に接続することで公開を成立させた。db は `internal` 専用のまま隔離を維持する。

## 背景・症状

PR #90（`feat-phpmyadmin-lan-access`）で phpMyAdmin を `PMA_BIND_IP` による LAN 公開対応にしたが、本番サーバで実際に起動すると **LAN のどの PC からもブラウザで開けなかった**。さらに調査の過程で、ポート競合回避のため PR #94 でホストポートを `PMA_HTTP_PORT`（8091→8092）に可変化して再デプロイしても、依然として到達できなかった。

`docker ps` で `baseball-market-spring-phpmyadmin` の PORTS 欄が常に空、という点が最初の手がかりだった。

## 切り分け（本番サーバ実測）

順に確認した結果、**設定はすべて正しいのに公開だけが成立していない**ことが判明した。

| 確認項目 | 結果 | 判定 |
| --- | --- | --- |
| サーバの LAN IP | `192.168.10.6`（`enp0s31f6`）を保有 | ✅ `PMA_BIND_IP` の値は正しい |
| `.env` | `PMA_BIND_IP=192.168.10.6` / `PMA_HTTP_PORT=8092` | ✅ |
| 配布された compose | `${PMA_BIND_IP:-127.0.0.1}:${PMA_HTTP_PORT:-8091}:80` | ✅ 新版が反映済み |
| `docker compose config`（変数解決後） | `host_ip: 192.168.10.6` / `published: 8092` | ✅ |
| コンテナの `HostConfig.PortBindings` | `{"80/tcp":[{"HostIp":"192.168.10.6","HostPort":"8092"}]}` | ✅ 「公開したい」設定は入っている |
| コンテナの `NetworkSettings.Ports` | `{}`（空） | ❌ **実際には公開されていない** |
| ホストの `ss -tlnp` の 8092 | LISTEN なし（8091 は別プロジェクトの docker-proxy のみ） | ❌ 待ち受けが立っていない |
| `docker compose up` 出力 / `journalctl -u docker` | エラーなし | ❌ 黙って破棄されている |
| `docker-compose.override.yml` / `COMPOSE_FILE` / `daemon.json` | いずれも無し（デフォルト） | — 別要因を除外 |

クリーンに `rm -sf` → `up` し直しても、`up` は成功するのに 8092 は LISTEN されず、`curl http://127.0.0.1:8092/` も `http://192.168.10.6:8092/` も「接続拒否」だった。

## 根本原因

phpMyAdmin が **`internal: true` のネットワーク（`internal`）にのみ参加**していたこと。

```yaml
networks:
  internal:
    driver: bridge
    internal: true        # db 隔離のため外部到達を遮断

  phpmyadmin:
    networks:
      - internal          # phpMyAdmin もこれだけ
    ports:
      - "192.168.10.6:8092:80"   # 公開しようとするが…
```

`internal: true` は「ホスト／外部への経路を持たない」ネットワークであり、db を隔離する目的で意図的にそう設定されている。**経路がないため、`ports:` を指定しても Docker はポート公開を黙って破棄する**。これは実測値（`PortBindings` は付くが `NetworkSettings.Ports` が空・LISTEN なし・エラーなし）と完全に一致する。

重要なのは、これは **ポート番号（8091/8092）とは無関係**だという点。`internal` 網にしか繋がっていない限り、

- LAN 公開（`192.168.10.6:8092`）
- 全 IF 公開（`0.0.0.0:8092`）
- SSH トンネル前提の `127.0.0.1:8091`

の **いずれも一切ホストに出ない**。すなわち PR #90 以降、phpMyAdmin の公開はどの方式でも一度も成立していなかった（PR #90 の検証は YAML パースのみで、ランタイム到達は「未」のままだった ← `2026-06-27_1434_feat-phpmyadmin-lan-access.md` 検証表参照）。

## 設計判断（採用案と不採用案）

| 案 | 内容 | 評価 |
| --- | --- | --- |
| **A: `pma-public`（通常ブリッジ）を追加**（採用） | phpMyAdmin だけ公開用の網を1枚追加し、`internal`＋`pma-public` の2枚に接続 | ✅ db 隔離を維持したまま最小変更で公開成立 |
| B: `internal: true` を外す | `internal` を普通のブリッジ網にする | ✗ db の隔離が弱まる（隔離設計の後退） |
| C: 既存の `nginx-proxy-manager-network` に相乗り | phpMyAdmin を NPM 共有網へ接続 | △ 動作はするが他プロジェクト共有網に混ぜることになり分離が甘い |

最小かつ安全な **A** を採用。

- `db` は **`internal` のみ**（`pma-public` には参加しない）→ 隔離はそのまま。
- phpMyAdmin だけが `internal`（db へ到達）＋ `pma-public`（ホスト公開）の2枚に接続。
- 公開ポートは従来どおり `PMA_BIND_IP`（=`192.168.10.6`）にバインドし、LAN 限定の公開範囲は不変。

## 変更内容 — `deploy/prod/docker-compose.yml`

1. 通常ブリッジ網 `pma-public`（`internal: false`）を新規追加。なぜ必要かをコメントで明記。

   ```yaml
   networks:
     internal:
       driver: bridge
       internal: true
     pma-public:          # 追加: phpMyAdmin のホスト公開用
       driver: bridge
   ```

2. `phpmyadmin` のネットワークを `internal` 単独から `internal` ＋ `pma-public` に変更。

   ```yaml
   phpmyadmin:
     networks:
       - internal         # db へ到達するため
       - pma-public       # ホストへポート公開するため（internal だけだと ports: が成立しない）
   ```

`db` / `app` のネットワークは変更なし（`db` は `internal` のみ＝隔離維持）。

## 即時復旧 / 確証用の override（任意）

恒久デプロイを待たずにサーバ上で直す場合や、原因を自分の目で確かめる場合は、一時 override で同じ構成を当てられる。`pma-public` を足したときだけ 8092 が LISTEN されれば、原因が `internal` 網だったことの動かぬ証拠になる。

```bash
cd ~/deploy/baseball-market-spring
cat > docker-compose.override.yml <<'YAML'
networks:
  pma-public:
    driver: bridge
services:
  phpmyadmin:
    networks:
      - internal
      - pma-public
YAML
docker compose --profile tools up -d --force-recreate phpmyadmin
sudo ss -tlnp | grep ':8092'
curl -sS -o /dev/null -w '%{http_code}\n' http://192.168.10.6:8092/
```

> deploy.sh は `docker-compose.yml` のみコピーし override は配布しない。恒久対応（本 PR）がデプロイされたら **override は削除**してよい（恒久 compose に `pma-public` が入るため）。

## 反映手順（GitHub Actions 経由）

1. 本 PR（#96）を `develop` → `main` までマージ。
2. Actions「Deploy to Production」を `main` で手動実行（新 compose がサーバへ配布される）。
3. デプロイ後、サーバで一時 override を削除して再作成:
   ```bash
   cd ~/deploy/baseball-market-spring
   rm -f docker-compose.override.yml
   docker compose --profile tools up -d --force-recreate phpmyadmin
   ```

## 検証

| 項目 | 結果 |
| --- | --- |
| 本番 compose の YAML 構文・network/port 配線（`db` は `internal` のみ、`phpmyadmin` は `internal`+`pma-public`） | OK（PyYAML パース確認） |
| サーバでの 8092 LISTEN・`curl` 到達・LAN ブラウザ到達 | override 検証 / 恒久デプロイ後に実施予定 |
| `db` が引き続き `internal` 専用で外部非公開（隔離維持） | OK（compose 上で確認。`pma-public` 不参加） |

## 関連

- 直接の前提: `2026-06-27_1434_feat-phpmyadmin-lan-access.md`（本不具合を埋め込んだ PR #90。検証が YAML パースのみで実到達は未確認だった）
- ポート可変化: PR #94（`PMA_HTTP_PORT` 導入）/ リリース PR #95
- 本対応: PR #96
- 教訓: compose のポート公開はネットワークの `internal` 属性に依存する。`internal: true` の網に繋いだコンテナの `ports:` は黙って無視される。**LAN/ホスト公開を伴う変更は YAML パースで満足せず、必ず実サーバで LISTEN と到達まで確認する**こと。
