# TDD Patterns — よくあるテストパターン集（Java / JUnit 5）

本プロジェクトのスタック: JUnit 5 + AssertJ + Mockito + Spring Boot Test。

## Fixture Patterns（準備パターン）

### @BeforeEach / @AfterEach
```java
class ProductRepositoryImplTest {

    private ProductRepositoryImpl sut;

    @BeforeEach
    void setUp() {        // 各テスト前に実行（状態を毎回初期化）
        sut = new ProductRepositoryImpl(new InMemoryProductJpaRepository());
    }
}
```

### ファクトリメソッド（テストデータ生成）
共有可変フィクスチャより、テストごとに新しいインスタンスを生成するヘルパーを推奨。
```java
private static User user(String email) {
    return User.register(new Email(email), "Password123");
}

@Test
void プロフィール名を取得できる() {
    User sut = user("test@example.com");
    assertThat(sut.getEmail().value()).isEqualTo("test@example.com");
}
```

### @Nested による文脈グルーピング（任意）
```java
class ProductServiceTest {

    @Nested
    class 正常系 {
        @Test void 商品を作成できる() { ... }
    }

    @Nested
    class 異常系 {
        @Test void 存在しないIDで例外() { ... }
    }
}
```

---

## Assertion Patterns（検証パターン / AssertJ）

### 複数条件は assertAll で（1論理アサーション）
最初の失敗で後続が隠れないよう、関連検証はまとめる。
```java
import static org.junit.jupiter.api.Assertions.assertAll;

@Test
void 商品作成後の初期状態() {
    Product sut = Product.create("バット", 8000);

    assertAll(
        () -> assertThat(sut.getName()).isEqualTo("バット"),
        () -> assertThat(sut.getPrice()).isEqualTo(8000),
        () -> assertThat(sut.isSold()).isFalse()
    );
}
```

### AssertJ のチェーン / メッセージ検証
```java
assertThat(list).hasSize(3).extracting(Product::getName).contains("バット");

assertThatThrownBy(() -> sut.find(unknownId))
    .isInstanceOf(NotFoundException.class)
    .hasMessageContaining("見つかりません");
```

### カスタムアサーション（再利用）
```java
static void assertValidEmail(String email) {
    assertThat(email).matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
}
```

---

## Exception Patterns（例外）

```java
// 型のみ
assertThatThrownBy(() -> divide(10, 0)).isInstanceOf(ArithmeticException.class);

// 型 + メッセージ
assertThatThrownBy(() -> validateAge(-1))
    .isInstanceOf(ValidationException.class)
    .hasMessageContaining("年齢は0以上");

// 例外が発生しないこと
assertThatCode(() -> process(validInput)).doesNotThrowAnyException();
```

---

## Mock Patterns（Mockito）
モックは**外部境界（Repository / JavaMailSender / 外部 HTTP）のみ**に使う。

### 戻り値のスタブ
```java
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock ProductRepository repository;
    @InjectMocks ProductService sut;

    @Test
    void 取得できる() {
        when(repository.findById(new ProductId(1L)))
            .thenReturn(Optional.of(Product.create("グローブ", 5000)));

        assertThat(sut.find(new ProductId(1L)).getName()).isEqualTo("グローブ");
    }
}
```

### 例外のスタブ
```java
when(repository.findById(any())).thenThrow(new DataAccessResourceFailureException("db down"));
```

### 相互作用の検証（仕様のときだけ）
```java
verify(mailSender).send(argThat(m -> m.to().equals("user@example.com")));
verify(repository, times(1)).save(any(Product.class));
verifyNoInteractions(mailSender);   // 呼ばれないことの検証
```

### Controller スライスでの @MockBean
```java
@WebMvcTest(ProductController.class)
class ProductControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean ProductService productService;
}
```

---

## Parameterized Tests（パラメータ化）

### 複数入力
```java
@ParameterizedTest
@CsvSource({ "0, zero", "1, one", "2, two" })
void 数値を語に変換(int input, String expected) {
    assertThat(toWord(input)).isEqualTo(expected);
}
```

### 境界値（ちょうど境界・±1・最小最大）
```java
@ParameterizedTest(name = "{0}歳 → adult={1}")
@CsvSource({ "17, false", "18, true", "19, true", "0, false", "120, true" })
void 成人判定(int age, boolean isAdult) {
    assertThat(Age.of(age).isAdult()).isEqualTo(isAdult);
}
```

### 無効値の一括検証
```java
@ParameterizedTest
@ValueSource(strings = {"", " ", "invalid", "@example.com", "test@"})
void 不正なメールは例外(String value) {
    assertThatThrownBy(() -> new Email(value)).isInstanceOf(ValidationException.class);
}
```

### null・empty
```java
@ParameterizedTest
@NullAndEmptySource
void nullと空は例外(String value) {
    assertThatThrownBy(() -> new Email(value)).isInstanceOf(ValidationException.class);
}
```

---

## Spring 結合テスト

### @SpringBootTest（真の結合のみ）
```java
@SpringBootTest
@AutoConfigureMockMvc
@Transactional   // メソッド終了時にロールバック
class SignupFlowIntegrationTest {
    @Autowired MockMvc mockMvc;
    // 実 DB は Testcontainers/専用テスト DB を使用（bb_market を汚さない）
}
```

### Security 有効下の MockMvc
```java
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcRequestPostProcessors.*;

mockMvc.perform(post("/products").with(csrf()).param("name", "バット"))
    .andExpect(status().is3xxRedirection());

// 認証ユーザーとして
mockMvc.perform(get("/mypage").with(user("test@example.com")))
    .andExpect(status().isOk());
```

---

## チェックリスト（テスト作成時）
- [ ] テスト名が振る舞いを説明している
- [ ] AAA 構造が明確（空行で分離）
- [ ] 1テスト1振る舞い・1論理アサーション（複数検証は `assertAll`）
- [ ] 境界値・異常系をカバー（`@ParameterizedTest` 活用）
- [ ] 外部境界のみモック（過度なモックなし）
- [ ] テスト間の依存がない（`@BeforeEach` で初期化）
- [ ] 実行が高速（単体は Spring 起動なし）
