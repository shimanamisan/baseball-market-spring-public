# TDD Examples — 具体的な TDD ウォークスルー（Java / JUnit 5）

baseball-market-spring のドメインを題材にした、Red → Green → Refactor の実例。

---

## Example 1: Value Object の TDD（`Email`）

### テストリスト
```markdown
- [ ] 正しい形式のメールで生成できる   ← 最も単純
- [ ] 不正な形式は ValidationException
- [ ] null は ValidationException
```

### Step 1: 最初のテスト（🔴 RED）
```java
class EmailTest {
    @Test
    void 正しい形式のメールで生成できる() {
        Email sut = new Email("user@example.com");
        assertThat(sut.value()).isEqualTo("user@example.com");
    }
}
```
→ コンパイルエラー（`Email` 未定義）= 失敗。

### Step 2: 最小実装（🟢 GREEN）
```java
public record Email(String value) {}   // まだ検証なし
```
→ グリーン。

### Step 3: 次のテストを追加（🔴 RED）
```java
@Test
void 不正な形式は例外() {
    assertThatThrownBy(() -> new Email("invalid"))
        .isInstanceOf(ValidationException.class);
}
```
→ 例外が飛ばず失敗。

### Step 4: 一般化（🟢 GREEN → 🔵 REFACTOR）
```java
public record Email(String value) {
    public Email {
        if (value == null || !value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ValidationException("メールアドレスの形式が不正です");
        }
    }
}
```

### 境界・null を一括検証
```java
@ParameterizedTest
@NullAndEmptySource
@ValueSource(strings = {" ", "invalid", "@example.com", "test@"})
void 不正な値はすべて例外(String value) {
    assertThatThrownBy(() -> new Email(value)).isInstanceOf(ValidationException.class);
}
```

---

## Example 2: エンティティのドメインロジックの TDD（`Product`）

### テストリスト
```markdown
- [ ] 作成直後は未売却
- [ ] markAsSold で売却済みになる
- [ ] 売却済みを再度 markAsSold すると例外
```

### テストコード
```java
class ProductTest {

    @Test
    void 作成直後は未売却() {
        Product sut = Product.create("グローブ", 5000);

        assertAll(
            () -> assertThat(sut.getName()).isEqualTo("グローブ"),
            () -> assertThat(sut.isSold()).isFalse()
        );
    }

    @Test
    void markAsSoldで売却済みになる() {
        Product sut = Product.create("グローブ", 5000);

        sut.markAsSold();

        assertThat(sut.isSold()).isTrue();
    }

    @Test
    void 売却済みを再度売却すると例外() {
        Product sut = Product.create("グローブ", 5000);
        sut.markAsSold();

        assertThatThrownBy(sut::markAsSold)
            .isInstanceOf(ProductAlreadySoldException.class);
    }
}
```

### 実装（テストを通す最小限 → 整理）
```java
@Entity
@Table(name = "products")
public class Product {
    // ... 省略（id / name / price / sold）
    public void markAsSold() {
        if (this.sold) {
            throw new ProductAlreadySoldException(this.id);
        }
        this.sold = true;
    }
}
```
ポイント: ドメインロジック（売却の不変条件）は**エンティティ自身**に置き、純 POJO として Spring 起動なしでテストする。

---

## Example 3: 外部依存のある application のTDD（`SignupUseCase`）

天気 API 例の代わりに、本プロジェクトの「仮登録 → 確認メール送信」を題材にする。
**外部境界（`UserRepository` / `MailSender`）だけをモック**し、ユースケースの振る舞いを検証する。

### テストリスト
```markdown
- [ ] 仮登録するとユーザーが保存される
- [ ] 仮登録すると確認メールが送信される
- [ ] 既に登録済みのメールは例外（メールは送らない）
```

### テストコード（Mockito）
```java
@ExtendWith(MockitoExtension.class)
class SignupUseCaseTest {

    @Mock UserRepository userRepository;
    @Mock MailSender mailSender;
    @InjectMocks SignupUseCase sut;

    @Test
    void 仮登録するとユーザーが保存される() {
        // Arrange
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenReturn(new UserId(1L));

        // Act
        UserId id = sut.register(new SignupCommand("user@example.com", "Password123"));

        // Assert（戻り値で検証）
        assertThat(id.value()).isEqualTo(1L);
    }

    @Test
    void 仮登録すると確認メールが送信される() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenReturn(new UserId(1L));

        sut.register(new SignupCommand("user@example.com", "Password123"));

        // 相互作用が仕様 → verify を使う
        verify(mailSender).send(argThat(m -> m.to().equals("user@example.com")));
    }

    @Test
    void 既に登録済みのメールは例外でメールを送らない() {
        when(userRepository.findByEmail(any()))
            .thenReturn(Optional.of(User.register(new Email("user@example.com"), "x")));

        assertThatThrownBy(() ->
            sut.register(new SignupCommand("user@example.com", "Password123")))
            .isInstanceOf(EmailAlreadyRegisteredException.class);

        verifyNoInteractions(mailSender);   // メール未送信を保証
    }
}
```

### 実装イメージ
```java
@Service
public class SignupUseCase {
    private final UserRepository userRepository;
    private final MailSender mailSender;

    public SignupUseCase(UserRepository userRepository, MailSender mailSender) {
        this.userRepository = userRepository;
        this.mailSender = mailSender;
    }

    @Transactional
    public UserId register(SignupCommand command) {
        Email email = new Email(command.email());
        if (userRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyRegisteredException(email);
        }
        User user = User.register(email, command.password());
        UserId id = userRepository.save(user);
        mailSender.send(VerificationMail.of(email, user.verificationToken()));
        return id;
    }
}
```

---

## ポイントまとめ
1. **最も単純なテストから始める**（VO → エンティティ → ユースケースの順が安定）
2. **仮実装でもOK** — まずグリーンに、その後三角測量で一般化
3. **境界値を忘れない** — ちょうど境界・±1・最小・最大・null/empty
4. **外部依存だけモック** — Repository / MailSender。ドメインロジックは本物で検証
5. **相互作用の verify は仕様のときだけ** — それ以外は戻り値・状態で検証
