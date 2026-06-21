package com.shimanamisan.baseballmarket.shared.infrastructure.security;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security の主要設定。
 *
 * 旧 PHP 仕様の踏襲:
 * - フォーム認証（email + password）
 * - セッションタイムアウト 1 時間（application.properties で設定）
 * - remember-me 30 日（旧 30 日設定に合わせる）
 * - セッション固定化対策（session_regenerate_id 相当）
 * - 同時ログインは 1 セッションのみ（旧と同等の体験）
 *
 * フェーズ 3 時点では UserDetailsService がまだ無いため、Spring Boot 自動生成の InMemoryUserDetailsManager が動く。
 * フェーズ 4 で User context に SpringUserDetailsService を追加すると、本設定はそれを自動的に拾う。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private static final Duration REMEMBER_ME_VALIDITY = Duration.ofDays(30);
  private static final String REMEMBER_ME_KEY = "baseball-market-remember-me";

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            // 商品詳細(GET)は公開だが、購入 POST は要認証（permitAll より先に評価させる）
            .requestMatchers(org.springframework.http.HttpMethod.POST, "/productDetail/*/purchase")
                .authenticated()
            .requestMatchers(
                "/",
                "/signup",
                "/signup/**",
                "/emailVerify",
                "/emailVerify/**",
                "/emailVerifyResend",
                "/passRemindSend",
                "/passRemindRecieve",
                "/productDetail/**",
                "/likes/**",
                "/login",
                "/css/**",
                "/js/**",
                "/uploads/**",
                "/seed-images/**",
                "/assets/**",
                // 本番ヘルスチェック用（Docker / NPM / deploy.sh が無認証で疎通確認する）。
                // sub-path（/actuator/health/{component} 等）は最小権限のため許可しない。
                "/actuator/health",
                "/error"
            ).permitAll()
            .anyRequest().authenticated()
        )
        .formLogin(form -> form
            .loginPage("/login")
            .loginProcessingUrl("/login")
            .usernameParameter("email")
            .passwordParameter("password")
            .defaultSuccessUrl("/", true)
            .failureUrl("/login?error")
            .permitAll()
        )
        .logout(logout -> logout
            .logoutUrl("/logout")
            .logoutSuccessUrl("/")
            .invalidateHttpSession(true)
            .deleteCookies("JSESSIONID")
            .permitAll()
        )
        .rememberMe(rm -> rm
            .key(REMEMBER_ME_KEY)
            .tokenValiditySeconds((int) REMEMBER_ME_VALIDITY.toSeconds())
            .rememberMeParameter("remember-me")
        )
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            .sessionFixation(sf -> sf.migrateSession())
            .maximumSessions(1)
        );
    return http.build();
  }
}
