# アーキテクチャルール

本プロジェクトは旧 `baseball-market`（PHP / DDD レイヤード構成）を Spring Boot にフルリプレースするものであり、**ドメイン構造は旧プロジェクトを踏襲する**。

## 1. Bounded Context（機能パッケージ）

旧プロジェクトの `public/src/` 直下のモジュール分割をそのまま継承する。

| Context | 責務 |
| --- | --- |
| `user` | アカウント登録・ログイン・退会・メール認証・パスワードリセット・プロフィール |
| `product` | 商品登録・編集・一覧・詳細・検索・売買履歴 |
| `message` | 商品ボード上のメッセージ送受信 |
| `like` | 商品いいね（Ajax 操作） |
| `mypage` | マイページ集約（`user` と `product` を跨ぐ表示） |
| `shared` | 横断的関心事（DB 接続、Logger、Mail、Security、Session、共通 Validation/Exception） |

**新しい機能を追加する場合**は、まずどの context に属するか判断し、なければ新規 context を作る。安易に既存 context を肥大化させない。

## 2. レイヤー構成（各 context 共通）

各 context 内に必ず以下の 4 レイヤーを置く。旧 PHP 側と同じ命名を維持する。

```
com.shimanamisan.baseballmarket.<context>
├── domain          // エンティティ、Value Object、Repository インターフェース、ドメイン例外
├── application     // ユースケース／サービス（トランザクション境界）
├── infrastructure  // Repository 実装、外部 IO、永続化アダプタ
└── presentation    // Controller、リクエスト DTO、ビュー
```

`shared` のみ `presentation` を持たず、`domain` と `infrastructure` のみで構成する。

## 3. 依存方向（厳守）

```
presentation ──▶ application ──▶ domain ◀── infrastructure
```

- `domain` は **どのレイヤーにも依存しない**（Spring Framework の型を持ち込まない）
- `application` は `domain` のみ依存
- `infrastructure` は `domain` の Repository インターフェースを実装し、Spring Data JPA など外部技術を内側に封じ込める
- `presentation` は `application` を呼ぶ。Repository を直接呼ばない
- 別 context への直接依存は原則禁止。集約をまたぐ参照が必要な場合は ID 値（`UserId` など）を保持し、`application` 層で別 context のサービスを呼ぶ

## 4. Value Object パターン

旧プロジェクトの方針を継承する。プリミティブ型の引き回しを避け、ドメイン制約を型で表現する。

旧プロジェクトに存在する VO（リプレース時に必ず維持）:

- `UserId` / `Email` / `Password` / `EmailVerificationToken` / `PasswordResetToken`
- `ProductId` / `ProductName` / `Price` / `ProductImage`
- `MessageId` / `MessageContent` / `BoardId`
- `LikeId`

Java 実装方針:
- **Java 21 record** を第一選択（不変・equals/hashCode 自動生成）
- 生成時に検証ロジック（コンパクトコンストラクタ）を入れる
- 検証失敗は `shared.domain.exception.ValidationException`（旧 `Shared\Domain\Exception\ValidationException` 相当）を投げる

## 5. Repository パターン

- インターフェース → `<context>.domain.<Entity>Repository`
- 実装 → `<context>.infrastructure.<Entity>RepositoryImpl`
- 実装内部で Spring Data JPA の `JpaRepository` を委譲利用してよい（ただし Spring Data の型は `infrastructure` 層から外に漏らさない）
- ドメインエンティティと JPA Entity を分けるか単一にするかは、**集約のサイズが小さいうちは単一**で進め、複雑化したら分離する

## 6. 画面層

旧プロジェクトでは `Presentation/views/*.php` がコンテキストに同梱されていた。Spring Boot 側でも同じ思想で、サーバーサイドレンダリング（Thymeleaf 想定）のテンプレートは context 配下に置かず、Spring の慣習どおり `src/main/resources/templates/<context>/*.html` に配置する。**ただし context 名で必ずディレクトリを切る**（旧構成との対応関係を保つため）。

Ajax 部分（`like` の非同期化など）は REST エンドポイントとして同 context の Controller に並置する。

## 7. パッケージルートと命名

- ルートパッケージ: `com.shimanamisan.baseballmarket`
- クラス名は context を prefix にしない（パッケージで分離されるため）。例: `com.shimanamisan.baseballmarket.product.domain.Product`
- DTO は context 内 `presentation` に置き、サフィックスは `Request` / `Response` で統一

## 8. 違反時の扱い

これらのルールに反する実装を提案・生成する前に、必ずユーザーに理由を提示して合意を取ること。「Spring Boot ではこちらが一般的」という理由だけで上記構成を崩さない。
