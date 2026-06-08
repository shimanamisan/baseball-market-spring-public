---
name: tdd
description: t-wada流TDD（テスト駆動開発）。新機能実装・バグ修正・リファクタリング時に自動適用。Red-Green-Refactor、AAA構造、振る舞いテスト、アンチパターン回避の原則に従う。Java 21 / JUnit 5 / AssertJ / Mockito でテストを書く・テストファーストで実装する・テストコードをレビューする際に使用。
---

# t-wada流 TDD（テスト駆動開発）

和田卓人氏の TDD 哲学に基づき、本プロジェクト（Spring Boot + DDD）で高品質なテスト駆動開発を実践する。

## 中核思想

> 「テストとは、動くことを証明するものではない。間違いを見つけるためのものだ。」— t.wada

1. **テストは設計行為** — テストを書くことで、使いやすい API（クラス・メソッドの公開境界）を設計する
2. **テストは仕様書** — テストコードが最も正確で最新のドキュメントである
3. **小さく回す** — Red → Green → Refactor を短いサイクルで繰り返す

## TDD サイクル
```
🔴 RED      → 失敗するテストを先に書く
     ↓
🟢 GREEN    → 最小限のコードでテストを通す（仮実装OK）
     ↓
🔵 REFACTOR → テストが通ったまま設計を改善（振る舞いは変えない）
     ↓
   （繰り返し）
```

## Instructions

### Step 1: 要件を「振る舞い」で分解
実装前に「〜したとき、〜となる」形式でテストケースをリストアップし、**最も単純なケースから**着手する。

```markdown
## [Feature] テストリスト
- [ ] 正しい形式のメールアドレスで生成できる        ← 最も単純
- [ ] 不正な形式は ValidationException
- [ ] null は ValidationException
- [ ] 一覧を取得できる
- [ ] 存在しないIDで NotFoundException
- [x] 名前が255文字超でバリデーションエラー（完了）
```

### Step 2: テスト構造（AAA パターン）
すべてのテストを **Arrange-Act-Assert** の 3 フェーズで、空行で明確に区切って書く。

```java
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ProductTest {

    @Test
    void 売却済みの商品を再度売却すると例外() {
        // Arrange
        Product sut = Product.create("グローブ", 5000);
        sut.markAsSold();

        // Act + Assert（例外検証は Act と Assert が一体）
        assertThatThrownBy(sut::markAsSold)
            .isInstanceOf(ProductAlreadySoldException.class);
    }
}
```
- テスト対象は `sut`（System Under Test）と命名すると意図が明確
- テスト名は**日本語で振る舞いを記述**（メソッド名 or `@DisplayName`）
- テストクラス名: `[対象クラス名]Test`

### Step 3: 境界値と異常系を必ずカバー
境界値（ちょうど境界・境界±1・最小・最大）と異常系（不正値・null・例外）を網羅する。複数入力は `@ParameterizedTest` を使う（→ [PATTERNS.md](PATTERNS.md)）。

### Step 4: Refactor
テストがグリーンのまま、重複の除去・命名改善・責務分離を行う。**振る舞いを変えずに構造を改善する。**

## レイヤー別の方針（このプロジェクト）
| 対象 | テスト種別 | DB/Spring | モック | 速度 |
|---|---|---|---|---|
| domain（Entity / VO） | 単体（純 POJO） | 不要 | 不要 | 最速 |
| application（Service / UseCase） | 単体 | 不要 | Repository 等の境界を Mockito | 速い |
| presentation（Controller） | `@WebMvcTest` + MockMvc | Service をモック（`@MockBean`） | | 中 |
| infrastructure / 結合 | `@SpringBootTest` | 実 DB or Testcontainers | 最小限 | 遅い |

**原則:** Spring コンテキストを起動しない単体テスト（domain / application）を主力にし、`@SpringBootTest` は真に結合が必要なときだけ使う（テストは速いほどよい）。

## 重要原則
- **1テスト1振る舞い・1論理アサーション**（関連フィールドは `assertAll` でまとめる）
- **振る舞いをテストする** — 公開された結果・状態を検証し、private/内部実装に依存しない
- モックの呼び出し検証（`verify`）は、**その相互作用自体が仕様のとき**だけ（例: 確認メール送信）
- 仮実装→三角測量で一般化する

## Anti-Patterns（避けるべきパターン）

### ❌ 実装詳細のテスト
```java
// Bad: 内部状態・private に依存（リフレクション等）
assertThat(getPrivateField(sut, "cache")).isEqualTo(...);
// Good: 公開された振る舞いを検証
assertThat(sut.getResult()).isEqualTo(expected);
```

### ❌ テスト間の依存（共有可変状態）
```java
// Bad: static / インスタンス共有状態に前テストの結果が残る
// Good: 各テストで初期化（@BeforeEach かテスト内生成）
@BeforeEach void setUp() { counter = new Counter(); }
```

### ❌ 過度なモック
```java
// Bad: ドメインの純ロジックや VO までモック化 → 何もテストしていない
// Good: モックは外部境界（Repository / JavaMailSender / 外部 HTTP）だけに留め、
//       ドメインロジックは本物を使って振る舞いを検証する
```

### ❌ 巨大なテスト（1テストで複数の振る舞い）
```java
// Bad: 登録・メール送信・件数を1テストで全部検証
// Good: 振る舞いごとに分割
void ユーザー登録でメールアドレスが保存される() { ... }
void ユーザー登録で確認メールが送信される() { ... }
```

## 実装完了チェックリスト
- [ ] すべてのテストがパス（`./gradlew test`、必要に応じ `:app:test`）
- [ ] 各テストが独立して実行可能（テスト間で状態を共有しない）
- [ ] テスト名から振る舞いが理解できる
- [ ] AAA 構造が明確（空行で分離）
- [ ] 1テスト1振る舞い・1論理アサーション
- [ ] 境界値・異常系がカバーされている
- [ ] 実装詳細ではなく**振る舞い**をテストしている
- [ ] 過度なモックを使用していない
- [ ] 実 DB（`bb_market`）への破壊的操作・Flyway `clean` をテストで行っていない

## References
- [PATTERNS.md](PATTERNS.md) — fixtures / アサーション / 例外 / モック / パラメータ化 / スライステストのパターン集
- [EXAMPLES.md](EXAMPLES.md) — ドメイン題材での具体的な TDD ウォークスルー
- 関連ルール: `.claude/rules/spring_ddd.md`（レイヤー責務）, `.claude/coding-style.md`（テスト方針）
