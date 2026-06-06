package com.shimanamisan.baseballmarket.user.infrastructure;

import com.shimanamisan.baseballmarket.user.domain.Email;
import com.shimanamisan.baseballmarket.user.domain.EmailVerificationToken;
import com.shimanamisan.baseballmarket.user.domain.EmailVerificationTokenEntity;
import com.shimanamisan.baseballmarket.user.domain.Password;
import com.shimanamisan.baseballmarket.user.domain.ProfileUpdate;
import com.shimanamisan.baseballmarket.user.domain.User;
import com.shimanamisan.baseballmarket.user.domain.UserId;
import com.shimanamisan.baseballmarket.user.domain.UserProfile;
import com.shimanamisan.baseballmarket.user.domain.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * UserRepository の Spring Data JPA 実装。
 *
 * JpaRepository をフィールド保持し、ドメイン IF が露出しない範囲で委譲する。
 * Page<>/Pageable などの Spring Data 型は本クラス内で完結させ、外部へ漏らさない。
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

  private final UserJpaRepository userJpa;
  private final EmailVerificationTokenJpaRepository tokenJpa;

  public UserRepositoryImpl(
      UserJpaRepository userJpa, EmailVerificationTokenJpaRepository tokenJpa) {
    this.userJpa = userJpa;
    this.tokenJpa = tokenJpa;
  }

  @Override
  public Optional<User> findById(UserId id) {
    return userJpa.findByIdAlive(toInt(id));
  }

  @Override
  public Optional<User> findByEmail(Email email) {
    return userJpa.findByEmailAlive(email.value());
  }

  @Override
  public boolean existsByEmail(Email email) {
    return userJpa.existsByEmailAlive(email.value());
  }

  @Override
  public UserId save(User user) {
    User saved = userJpa.save(user);
    return UserId.fromLong(saved.getId().longValue());
  }

  @Override
  public void saveVerificationToken(UserId userId, EmailVerificationToken token) {
    int uid = toInt(userId);
    tokenJpa.deleteByUserId(uid);
    tokenJpa.save(new EmailVerificationTokenEntity(uid, token.value(), token.expiresAt()));
  }

  @Override
  public Optional<VerifiedTokenResult> findByVerificationToken(String token) {
    return tokenJpa
        .findByToken(token)
        .flatMap(entity ->
            userJpa
                .findByIdAlive(entity.getUserId())
                .map(user ->
                    new VerifiedTokenResult(
                        user,
                        new EmailVerificationToken(entity.getToken(), entity.getExpiresAt()))));
  }

  @Override
  public void markEmailAsVerified(UserId userId) {
    int uid = toInt(userId);
    userJpa.findByIdAlive(uid).ifPresent(u -> u.setEmailVerifiedAt(LocalDateTime.now()));
    tokenJpa.deleteByUserId(uid);
  }

  @Override
  public void deleteVerificationTokens(UserId userId) {
    tokenJpa.deleteByUserId(toInt(userId));
  }

  @Override
  public void updatePassword(UserId userId, Password password) {
    userJpa
        .findByIdAlive(toInt(userId))
        .ifPresent(u -> u.setPassword(password.getHash()));
  }

  @Override
  public void updateProfile(UserId userId, ProfileUpdate update) {
    int uid = toInt(userId);
    User user = userJpa
        .findByIdAlive(uid)
        .orElseThrow(() -> new IllegalStateException("user not found: " + uid));

    user.setEmail(update.email().value());

    UserProfile profile = user.getProfile();
    if (profile == null) {
      profile = new UserProfile(user);
      user.setProfile(profile);
    }
    profile.setUsername(update.username());
    profile.setAge(update.age());
    profile.setTel(update.tel());
    profile.setZip(update.zip());
    profile.setPrefecture(update.prefecture());
    profile.setCity(update.city());
    profile.setStreet(update.street());
    profile.setBuilding(update.building());
    profile.setPic(update.pic());
  }

  @Override
  public void withdraw(UserId userId) {
    int uid = toInt(userId);
    userJpa
        .findByIdAlive(uid)
        .ifPresent(User::markDeleted);
    userJpa.softDeleteProductsByUserId(uid);
    userJpa.softDeleteLikesByUserId(uid);
    userJpa.softDeleteBoardsByUserId(uid);
    userJpa.softDeleteMessagesByUserId(uid);
  }

  private static int toInt(UserId id) {
    long v = id.value();
    if (v > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("UserId out of INT range: " + v);
    }
    return (int) v;
  }
}
