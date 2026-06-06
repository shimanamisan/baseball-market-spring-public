package com.shimanamisan.baseballmarket.user.domain;

import java.util.Optional;

/**
 * User 集約のリポジトリインターフェース。
 * 旧 PHP の User\Domain\UserRepository に対応する責務を踏襲する。
 * 実装は user.infrastructure.UserRepositoryImpl（Spring Data JPA を委譲利用）。
 */
public interface UserRepository {

  Optional<User> findById(UserId id);

  Optional<User> findByEmail(Email email);

  boolean existsByEmail(Email email);

  UserId save(User user);

  void saveVerificationToken(UserId userId, EmailVerificationToken token);

  /**
   * メール認証トークンから User とトークン情報を取得。
   * 戻り値は VerifiedTokenResult。トークンが存在しない / ユーザーが論理削除済みの場合は empty。
   */
  Optional<VerifiedTokenResult> findByVerificationToken(String token);

  void markEmailAsVerified(UserId userId);

  void deleteVerificationTokens(UserId userId);

  void updatePassword(UserId userId, Password password);

  void updateProfile(UserId userId, ProfileUpdate update);

  /**
   * 退会処理（soft delete）。
   * users / products / likes / boards / messages の delete_flg を 1 に更新する。
   */
  void withdraw(UserId userId);

  record VerifiedTokenResult(User user, EmailVerificationToken token) {}
}
