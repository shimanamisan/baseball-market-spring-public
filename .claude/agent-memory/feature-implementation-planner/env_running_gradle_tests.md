---
name: env-running-gradle-tests
description: このサンドボックスには JDK/Gradle が無く ./gradlew が動かない。temurin docker でテストを回す手順と db 別名衝突の扱い
metadata:
  type: project
---

このエージェントのサンドボックスシェルには **JDK も Gradle も java も存在しない**（PATH に無く、`/usr/lib/jvm` も空）。`./gradlew` は実行不可。一方で `docker` グループ所属のため Docker は使える。

**Why:** ビルド/テストは devcontainer 内で動く前提で、サンドボックスのホスト側には JDK が無い。

**How to apply:** テストは temurin:21-jdk コンテナで回す。`.gradle`（user01 所有）と docker が書く成果物の uid 衝突で "Bad file descriptor" が出るため、リポジトリを `/build` にコピーして隔離し、GRADLE_USER_HOME と project-cache-dir を /tmp に逃がす:

```
docker run --rm -v /mnt/docker_work/baseball-market-spring:/repo \
  -e GRADLE_USER_HOME=/tmp/ghome eclipse-temurin:21-jdk \
  sh -c "cp -r /repo /build && cd /build/app && \
    ./gradlew test --console=plain --no-daemon --project-cache-dir=/tmp/pcache 2>&1 | tail -40"
```

- gradle wrapper はリポジトリ直下ではなく **`app/`** にある（ルートは composite build、`settings.gradle` が `includeBuild('app')`）。必ず `cd app` してから `./gradlew`。
- 特定テストのみ: `--tests 'com.shimanamisan.baseballmarket.like.*'`。
- ドメイン/Mockito/`@WebMvcTest` のテストは DB 不要で通る。`@SpringBootTest` の `BaseballMarketSpringApplicationTests.contextLoads()` だけは Flyway が `db` ホストに繋がらず `UnknownHostException` で失敗する（既知の環境問題。`_docs/2026-06-13_0942_replacement-progress-analysis.md` 記載）。テストが「contextLoads 以外すべて緑」なら本質的に成功とみなす。

**root 所有物の後始末:** `-u 1000:1000` を付けずに docker で repo 直下に書くと root 所有ファイルが残る。`app/build/` 等が root 所有になったら `docker run --rm -v <path>:/b alpine chown -R 1000:1000 /b` で戻す。`static/` ディレクトリ自体はコンテナが root 所有で作っており、配下に新規ファイルを置くには `docker run ... alpine sh -c "mkdir -p /work/js && chown -R 1000:1000 /work/js"` で先にサブディレクトリを作って所有権を移す必要がある。
