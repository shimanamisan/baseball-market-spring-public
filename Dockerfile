# 本番用イメージ（マルチステージ・非 root 実行）。
# ビルドコンテキストはリポジトリルート。Gradle はコンポジットビルドだが、実体は app/ に
# あり app/settings.gradle で自己完結するため app/ 内で bootJar すればよい（devcontainer で
# `cd /app/app && ./gradlew` しているのと同じ）。
# syntax=docker/dockerfile:1

# ===== Stage 1: build =====
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace/app

# 依存解決を Docker レイヤキャッシュに乗せるため、まず wrapper とビルド定義のみコピーする。
# src を後からコピーすることで、ソース変更時に依存ダウンロード層を再利用できる。
COPY app/gradlew app/settings.gradle app/build.gradle ./
COPY app/gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --quiet || true

# ソースをコピーして実行可能 jar を生成（テストは CI で別途実行するためスキップ）。
COPY app/src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ===== Stage 2: runtime =====
FROM eclipse-temurin:21-jre AS runtime

# compose / NPM のヘルスチェックが /actuator/health を叩くため curl を導入。
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 非 root 実行ユーザー。--create-home で /app をこのユーザー所有で作成する
# （--create-home が無いと WORKDIR が /app を root 所有で自動作成してしまう）。
RUN groupadd --system spring \
    && useradd --system --gid spring --create-home --home-dir /app spring
WORKDIR /app

# ユーザーアップロードの保存先（APP_UPLOADS_PATH 既定値）。本番は外部ボリュームをここに
# マウントする。ディレクトリと所有権を用意しておく（ImageStorage が起動時に書込む）。
RUN mkdir -p /var/lib/baseball-market/uploads && chown -R spring:spring /var/lib/baseball-market

# bootJar のみを実行するため build/libs の jar は 1 つ（plain jar は jar/assemble/build
# タスクが生成するもので、ここでは実行しないためワイルドカードは単一ファイルに解決する）。
# 実行ユーザー所有でコピーする。
COPY --chown=spring:spring --from=build /workspace/app/build/libs/baseball-market-spring-*.jar app.jar

USER spring
EXPOSE 8080

# JVM はコンテナのメモリ制限を認識する（21 は UseContainerSupport が既定で有効）。
# 追加の JVM オプションは JDK_JAVA_OPTIONS 環境変数で注入できる（exec 形式を維持し
# シグナル伝播を保つ）。プロファイルは compose の SPRING_PROFILES_ACTIVE で指定する。
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
