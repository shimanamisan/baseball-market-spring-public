---
paths:
  - app/src/main/java/com/shimanamisan/baseballmarket/shared/infrastructure/security/**/*
  - app/src/main/java/com/shimanamisan/baseballmarket/user/**/*
  - app/src/main/resources/application*.yml
  - app/src/main/resources/application*.properties
---

# Spring Security Authentication & Security Rules

## 技術スタック
- Web 認証: Spring Security + フォーム認証 + セッション
- パスワードハッシュ: BCrypt（`BCryptPasswordEncoder`）
- メール認証（仮登録 → 本登録）: トークン（`EmailVerificationToken` VO）+ Spring Mail
- 認可: URL ベース（`authorizeHttpRequests`）＋必要に応じてメソッドセキュリティ（`@PreAuthorize`）

旧 `Shared\Infrastructure\Session\SessionManager` は Spring Security の `SecurityContextHolder` で代替する（[replacement-policy.md](../replacement-policy.md) §3.2）。

## セキュリティ原則
- 最小権限の原則
- 入力値は必ず Bean Validation（`@Valid` + DTO）で検証
- 認可は SecurityConfig / メソッドセキュリティで集中管理（Controller に散らさない）
- 認証イベント（成功・失敗）をログ記録（個人情報・パスワードは出さない）

## SecurityConfig
```java
package com.shimanamisan.baseballmarket.shared.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/signup/**", "/login", "/css/**", "/js/**", "/uploads/**").permitAll()
                .requestMatchers("/mypage/**", "/products/new", "/products/*/edit").authenticated()
                .anyRequest().permitAll()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/mypage", true)
                .failureUrl("/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            )
            // CSRF はデフォルト有効。フォームに th:action を使えば自動でトークンが付与される
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(c -> {})       // X-Content-Type-Options: nosniff
            );
        return http.build();
    }
}
```

## UserDetailsService（認証情報のロード）
```java
package com.shimanamisan.baseballmarket.shared.infrastructure.security;

import com.shimanamisan.baseballmarket.user.domain.UserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return userRepository.findByEmail(new Email(email))
            .map(user -> org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail().value())
                .password(user.getPasswordHash())   // BCrypt 済みハッシュ
                .roles("USER")
                .build())
            .orElseThrow(() -> new UsernameNotFoundException("ユーザーが見つかりません"));
    }
}
```
- 本登録（メール認証）が完了していないユーザーはログイン不可とする（`disabled` 扱いにするか、認証時に弾く）。

## パスワードの取り扱い
```java
// 登録時（application 層）
String hash = passwordEncoder.encode(rawPassword); // 生パスワードは保存しない・ログに出さない

// 照合は Spring Security が DaoAuthenticationProvider 経由で自動実行する
```

## 認可（メソッドセキュリティを使う場合）
```java
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {}

// 所有者チェックの例（application 層）
@PreAuthorize("#command.ownerEmail == authentication.name")
public void update(UpdateProductCommand command) { ... }
```
細かな所有権チェック（「自分の商品しか編集できない」等）は application 層でドメインの所有者と認証ユーザーを突き合わせて行うのが基本。

## メール認証フロー（user context）
1. 仮登録: ユーザー作成（未認証状態）+ `EmailVerificationToken` 発行 → 確認メール送信
2. 本登録: トークン検証 → 認証済みフラグを立てる
3. トークンは `java.security.SecureRandom` ベースで生成（旧 `TokenGenerator` 相当）、有効期限を持たせる

## セキュリティログ
```java
private static final Logger log = LoggerFactory.getLogger(...);

log.info("ログイン成功 userId={}", userId);
log.warn("ログイン失敗 ip={}", clientIp);   // メールアドレスやパスワードは記録しない
```
`AuthenticationSuccessEvent` / `AbstractAuthenticationFailureEvent` を `@EventListener` で拾うと集中管理できる。

## 禁止事項
- パスワード・トークンの平文ログ出力／コミット
- CSRF 保護を理由なく無効化（無効化が必要な API は範囲を限定しユーザーに相談）
- 認可チェックを Controller の if 文に散在させる（SecurityConfig / メソッドセキュリティ / application 層に集約）
