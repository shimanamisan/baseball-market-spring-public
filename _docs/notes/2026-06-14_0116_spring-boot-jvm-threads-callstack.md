# Spring Boot 実行時のスレッド（コールスタック）一覧の読み方

デバッガ（IntelliJ IDEA / VS Code）で Spring Boot アプリを起動・デバッグ実行したとき、「コールスタック」ビューに多数のスレッドが「実行中」で並ぶことがある。これは特定の 1 つのスタックフレームではなく、**現在 JVM 内に存在する全スレッド**を表示したもの。本メモは各スレッドの正体と役割をまとめた技術解説。

> 本プロジェクト（baseball-market-spring）の構成（組み込み Tomcat + Spring Data JPA + HikariCP + MySQL 8 + DevTools）で実際に観測されるスレッドを対象にしている。

## 観測されたスレッド一覧

```
Thread [Reference Handler]                          実行中
Thread [Finalizer]                                  実行中
Thread [Signal Dispatcher]                          実行中
Thread [Notification Thread]                        実行中
Thread [Common-Cleaner]                             実行中
Thread [Catalina-utility-1]                         実行中
Thread [Catalina-utility-2]                         実行中
Thread [container-0]                                実行中
Thread [mysql-cj-abandoned-connection-cleanup]      実行中
Thread [HikariPool-1 housekeeper]                   実行中
Thread [File Watcher]                               実行中
Thread [Live Reload Server]                         実行中
Thread [http-nio-8080-exec-1]                       実行中
Thread [http-nio-8080-exec-2]                       実行中
Thread [http-nio-8080-exec-3]                       実行中
```

## 1. JVM 標準のシステムスレッド

アプリのコードと無関係に、Java ランタイムが必ず持つスレッド。

| スレッド名 | 役割 |
|---|---|
| **Reference Handler** | `WeakReference` / `SoftReference` / `PhantomReference` などの参照オブジェクトを処理する。GC が到達不能と判断したオブジェクトの参照をキューに積み、後処理する。 |
| **Finalizer** | `finalize()` を持つオブジェクトのファイナライズ処理を実行する専用スレッド。GC と連携して動く。 |
| **Signal Dispatcher** | OS からのシグナル（`Ctrl+C` = SIGINT、SIGTERM など）を受け取り、JVM 内の対応ハンドラへ振り分ける。 |
| **Notification Thread** | JVM の内部通知（JMX 通知や VM 状態変化通知など）を配送する。 |
| **Common-Cleaner** | `java.lang.ref.Cleaner` の共有インスタンス。`finalize()` の代替として、ネイティブメモリ等のリソース解放を行う新しい仕組み。 |

> 通常はさらに `main` スレッド・GC スレッド・JIT コンパイラスレッドなども存在するが、デバッガの表示範囲やフィルタの都合で見えていないことがある。

## 2. 組み込み Tomcat（Catalina）由来のスレッド

Spring Boot が内蔵する組み込み Tomcat に由来する。

| スレッド名 | 役割 |
|---|---|
| **Catalina-utility-1 / -2** | Catalina は Tomcat コンテナのコア部分の名称。セッションの期限切れチェックやリソース再読み込み監視など、バックグラウンドの定期メンテナンスを担うユーティリティスレッドプール。 |
| **container-0** | Spring Boot の `TomcatWebServer#startNonDaemonAwaitThread()` が起動する**非デーモンの待機スレッド**。`run()` は `tomcat.getServer().await()` を呼んでブロックし、組み込みサーバ（および JVM）を起動後も終了させずに稼働させ続ける。セッション期限切れチェック等の定期メンテナンスは担当しない（それは上記 `Catalina-utility` の役割）。 |
| **http-nio-8080-exec-1 / -2 / -3** | **最重要。リクエスト処理ワーカースレッド**。`nio` = ノンブロッキング I/O コネクタ、`8080` = リッスンポート、`exec-N` = スレッドプール内の実行スレッド番号。HTTP リクエスト到着時にこのプールから 1 本割り当てられ、`@Controller → @Service → Repository` の処理を実行する。同時に複数本あることで並行リクエストを捌ける。 |

> `@Controller` のブレークポイントで停止したとき、止まっているのはこの `http-nio-8080-exec-N` スレッドのいずれか。

## 3. データベース接続（HikariCP / MySQL）由来のスレッド

Spring Data JPA + MySQL 8 + HikariCP（Spring Boot デフォルト接続プール）構成に対応。

| スレッド名 | 役割 |
|---|---|
| **mysql-cj-abandoned-connection-cleanup** | MySQL Connector/J（`cj` = Connector/J）が持つ、放置（abandoned）された接続を検出・回収するスレッド。閉じ忘れた接続のリークを防ぐ。 |
| **HikariPool-1 housekeeper** | HikariCP コネクションプールのハウスキーパー。アイドル接続のタイムアウト管理・最小接続数の維持・古い接続の入れ替えを定期実行する。`HikariPool-1` はプール名（デフォルト 1 番）。 |

## 4. 開発用ツール（Spring Boot DevTools）由来のスレッド

`spring-boot-devtools` 依存によるホットリロード機能のスレッド。

| スレッド名 | 役割 |
|---|---|
| **File Watcher** | ソース／クラスファイルの変更を監視し、検知するとアプリを自動再起動（restart）する DevTools の中核。 |
| **Live Reload Server** | LiveReload プロトコルのサーバ。ブラウザの LiveReload 拡張と連携し、リソース変更時にブラウザを自動リロードさせる。デフォルトでポート 35729。 |

## まとめ — この画面が示していること

- アプリは**正常に起動して待機状態**にある。すべてのスレッドが「実行中（イベント待ちのループ含む）」で、エラー停止ではない。
- 内訳は **①JVM システム ②組み込み Tomcat ③DB 接続プール ④DevTools** の 4 系統。自分のビジネスロジックはリクエスト到着時に **`http-nio-8080-exec-N`** 上で実行される。
- デバッグ開始時に「Uncaught Exceptions」ブレークポイントを設定していると、この一覧が表示された状態になりやすい。

## デバッグ時の実践ヒント

- 自分の Controller／ロジックを追うときは **`http-nio-8080-exec-*` のスタックを展開**する。`main` やシステムスレッドを見ても自分のコードは出てこない。
- 「ブレークポイントで止めたのに一覧しか出ない」場合、止まったスレッドが未選択の可能性。`exec-*` を選ぶと `Controller#method → Service#method → ...` の見慣れたフレームが見える。
- `exec-*` がどんどん増える（`exec-100` 等まで）場合は、リクエスト滞留や DB 接続待ちでブロックしているサイン。ボトルネック調査にこの一覧が役立つ。
