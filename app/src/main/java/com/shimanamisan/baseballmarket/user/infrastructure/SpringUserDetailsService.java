package com.shimanamisan.baseballmarket.user.infrastructure;

import com.shimanamisan.baseballmarket.user.domain.Email;
import com.shimanamisan.baseballmarket.user.domain.User;
import com.shimanamisan.baseballmarket.user.domain.UserRepository;
import java.util.Collections;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security 用 UserDetailsService 実装。
 *
 * 旧 PHP の UserService::login が行っていた状態判定（削除 / 未認証）を、
 * UserDetails の有効フラグに翻訳して Spring Security の認証フローに連携する。
 */
@Service
public class SpringUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;

  public SpringUserDetailsService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    User user;
    try {
      user = userRepository
          .findByEmail(Email.fromString(email))
          .orElseThrow(() -> new UsernameNotFoundException("メールアドレスまたはパスワードが違います"));
    } catch (IllegalArgumentException e) {
      throw new UsernameNotFoundException("メールアドレスまたはパスワードが違います");
    }

    boolean enabled = user.isEmailVerified();
    boolean accountNonLocked = !user.isDeleted();

    return new org.springframework.security.core.userdetails.User(
        user.getEmail(),
        user.getPassword(),
        enabled,
        true,
        true,
        accountNonLocked,
        Collections.emptyList());
  }
}
