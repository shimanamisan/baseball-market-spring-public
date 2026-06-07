---
paths:
  - app/build.gradle
  - build.gradle
  - settings.gradle
  - app/settings.gradle
  - gradle/**/*
  - .devcontainer/**/*
  - docker-compose*.yml
  - Dockerfile
  - app/src/main/resources/application*.yml
  - app/src/main/resources/application*.properties
  - .github/**/*
---

# Spring Boot Technology Stack & Deployment Rules

## 技術スタック
| カテゴリ | 技術 |
|---------|------|
| 言語 | Java 21（Gradle toolchain で固定） |
| Framework | Spring Boot 3.4.x |
| ビルド | Gradle（Wrapper 同梱、`./gradlew`） |
| ビュー | Thymeleaf（+ thymeleaf-extras-springsecurity6） |
| 永続化 | Spring Data JPA / Hibernate |
| マイグレーション | Flyway（flyway-core / flyway-mysql） |
| バリデーション | Jakarta Bean Validation（Hibernate Validator） |
| 認証 | Spring Security（フォーム認証 + BCrypt） |
| メール | Spring Boot Starter Mail（開発は MailHog） |
| Database | MySQL 8.0+（`bb_market`） |

## Gradle 依存（`app/build.gradle` 現状）
```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-mail'
    implementation 'org.thymeleaf.extras:thymeleaf-extras-springsecurity6'
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-mysql'
    runtimeOnly 'com.mysql:mysql-connector-j'
    developmentOnly 'org.springframework.boot:spring-boot-devtools'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```
依存を追加する場合は本表と build.gradle を更新し、その理由をユーザーに説明する。

## application.yml（環境別）
本番では `application-prod.yml` を別出しし、秘匿値は環境変数で注入する。
```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:db}:3306/${DB_NAME:bb_market}
    username: ${DB_USER:bbuser}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate    # 本番は validate/none。スキーマ変更は Flyway 経由
    open-in-view: false
  flyway:
    enabled: true
    locations: classpath:db/migration
  mail:
    host: ${MAIL_HOST:mailhog}
    port: ${MAIL_PORT:1025}
```
- `ddl-auto` を `update` に頼らない（開発初期の利便目的のみ。スキーマは Flyway が正）。
- パスワード等の秘匿値はコミットしない。

## Docker Compose（開発: devcontainer）
開発環境は `.devcontainer/docker-compose.yml` を正とする。MySQL と MailHog を含める。
```yaml
services:
  app:
    build: .devcontainer
    depends_on: [db, mailhog]
  db:
    image: mysql:8.0
    environment:
      MYSQL_DATABASE: bb_market
      MYSQL_USER: bbuser
      MYSQL_PASSWORD: secret
      MYSQL_ROOT_PASSWORD: secret
    volumes:
      - mysql_data:/var/lib/mysql
  mailhog:
    image: mailhog/mailhog
    ports:
      - "8025:8025"   # Web UI
volumes:
  mysql_data:
```

## ビルド・実行コマンド
```bash
./gradlew build            # ビルド（テスト含む）
./gradlew bootRun          # ローカル起動
./gradlew test             # テスト実行
./gradlew bootJar          # 実行可能 jar 生成
```
（マルチプロジェクト構成のため、必要に応じて `:app:` プレフィックス。例: `./gradlew :app:test`）

## デプロイ（jar 実行の例）
```bash
git pull origin main
./gradlew clean bootJar
java -jar app/build/libs/app-*.jar --spring.profiles.active=prod
```
Flyway はアプリ起動時にマイグレーションを自動適用する。

## GitHub Actions CI
```yaml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew test
```

## ヘルスチェック
Spring Boot Actuator を導入する場合は `/actuator/health` を利用。導入しない場合は軽量な `@GetMapping("/health")` を 1 つ用意する。

## ログ設定
- SLF4J + Logback（Spring Boot 標準）。`application.yml` の `logging.level.*` でレベル制御。
- 個人情報・パスワード・トークンはログに出さない。
- 本番はファイル/集約基盤へ。`logging.file.name` または Logback の `RollingFileAppender` を使用。
