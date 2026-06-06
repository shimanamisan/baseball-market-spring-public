package com.shimanamisan.baseballmarket.user.application;

import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import com.shimanamisan.baseballmarket.shared.infrastructure.mail.MailProperties;
import com.shimanamisan.baseballmarket.shared.infrastructure.mail.MailService;
import com.shimanamisan.baseballmarket.user.domain.Email;
import com.shimanamisan.baseballmarket.user.domain.EmailVerificationToken;
import com.shimanamisan.baseballmarket.user.domain.Password;
import com.shimanamisan.baseballmarket.user.domain.PasswordResetToken;
import com.shimanamisan.baseballmarket.user.domain.ProfileUpdate;
import com.shimanamisan.baseballmarket.user.domain.User;
import com.shimanamisan.baseballmarket.user.domain.UserId;
import com.shimanamisan.baseballmarket.user.domain.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * user コンテキストのユースケースを束ねるアプリケーションサービス。
 *
 * 旧 PHP UserService の register/sendVerificationEmail/verifyEmail/resendVerificationEmail を移植。
 * ログイン本体は Spring Security の DaoAuthenticationProvider + SpringUserDetailsService が担当。
 */
@Service
@Transactional
public class UserService {

  private static final Logger log = LoggerFactory.getLogger(UserService.class);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final MailService mailService;
  private final MailProperties mailProperties;
  private final String appUrl;

  public UserService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      MailService mailService,
      MailProperties mailProperties,
      @Value("${app.url}") String appUrl) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.mailService = mailService;
    this.mailProperties = mailProperties;
    this.appUrl = stripTrailingSlash(appUrl);
  }

  public UserId register(String email, String rawPassword) {
    Email emailVo = Email.fromString(email);

    if (userRepository.existsByEmail(emailVo)) {
      throw new ValidationException("email", "そのEmailは既に登録されています");
    }

    Password.validateRawPassword(rawPassword);
    String hash = passwordEncoder.encode(rawPassword);

    User user = new User(emailVo.value(), hash);
    UserId userId = userRepository.save(user);

    sendVerificationEmail(userId, email);
    log.info("ユーザー登録完了 user_id={} email={}", userId.value(), email);
    return userId;
  }

  public void sendVerificationEmail(UserId userId, String email) {
    EmailVerificationToken token = EmailVerificationToken.generate();
    userRepository.saveVerificationToken(userId, token);

    String verifyUrl = appUrl + "/emailVerify?token=" + token.value();
    String subject = "【メールアドレス確認】｜BASEBALL ITEMカスタマーセンター";
    String body = String.format(
        """
        BASEBALL ITEMにご登録いただきありがとうございます。

        下記のURLをクリックしてメールアドレスの確認を完了してください。

        メールアドレス確認URL：%s

        ※このリンクの有効期限は24時間です。
        ※有効期限が切れた場合は、下記ページより確認メールを再送信してください。
        %s/emailVerifyResend

        *****************************************************
        BASEBALL ITEMカスタマーセンター
        URL：%s/
        Email：%s
        *****************************************************
        """,
        verifyUrl, appUrl, appUrl, mailProperties.fromAddress());

    mailService.send(null, email, subject, body);
  }

  public User verifyEmail(String token) {
    var result = userRepository
        .findByVerificationToken(token)
        .orElseThrow(() -> new ValidationException("無効な確認リンクです。"));

    if (!result.token().isValid()) {
      throw new ValidationException("確認リンクの有効期限が切れています。確認メールを再送信してください。");
    }

    // markEmailAsVerified は永続化コンテキスト内で User を更新する。直接 setter を使う形でも等価
    result.user().setEmailVerifiedAt(LocalDateTime.now());
    userRepository.deleteVerificationTokens(result.user().getUserId());
    log.info("メールアドレス確認完了 user_id={}", result.user().getId());
    return result.user();
  }

  public void resendVerificationEmail(String email) {
    Email emailVo = Email.fromString(email);
    User user = userRepository
        .findByEmail(emailVo)
        .orElseThrow(() -> new ValidationException("このメールアドレスは登録されていません。"));

    if (user.isEmailVerified()) {
      throw new ValidationException("このメールアドレスは既に確認済みです。");
    }

    sendVerificationEmail(user.getUserId(), email);
  }

  /**
   * パスワード再発行リクエスト。トークンを生成しメール送信する。
   * トークン自体は呼び出し側で HttpSession に保持する運用（旧 PHP 踏襲）。
   */
  public PasswordResetToken requestPasswordReset(String email) {
    Email emailVo = Email.fromString(email);
    if (!userRepository.existsByEmail(emailVo)) {
      throw new ValidationException("このメールアドレスは登録されていません");
    }

    PasswordResetToken token = PasswordResetToken.generate();

    String subject = "【パスワード再発行】｜BASEBALL ITEMカスタマーセンター";
    String body = String.format(
        """
        本メールアドレス宛にパスワード再発行のご依頼がありました。
        下記のURLにて認証キーをご入力頂くとパスワードが再発行されます。

        パスワード再発行認証キー入力ページ：%s/passRemindRecieve
        認証キー：%s
        ※認証キーの有効期限は30分となります

        認証キーを再発行されたい場合は下記ページより再度お手続きをお願い致します。
        %s/passRemindSend

        *****************************************************
        BASEBALL ITEMカスタマーセンター
        URL：%s/
        Email：%s
        *****************************************************
        """,
        appUrl, token.value(), appUrl, appUrl, mailProperties.fromAddress());

    mailService.send(null, email, subject, body);
    return token;
  }

  /**
   * パスワードをランダム文字列にリセットしメールで通知する。
   * 旧 PHP は 8 文字のランダム英数を生成し、ユーザーに新パスワードをメール送信。
   */
  public void resetPassword(String email, String newRawPassword) {
    Email emailVo = Email.fromString(email);
    User user = userRepository
        .findByEmail(emailVo)
        .orElseThrow(() -> new ValidationException("ユーザーが見つかりません"));

    Password.validateRawPassword(newRawPassword);
    Password newPassword = Password.fromHash(passwordEncoder.encode(newRawPassword));
    userRepository.updatePassword(user.getUserId(), newPassword);

    String subject = "【パスワード変更通知】 ｜ BASEBALL ITEMカスタマーセンター";
    String body = String.format(
        """
        本メールアドレス宛にパスワード再発行のご依頼がありました。
        下記のURLにて再発行パスワードをご入力頂き、ログインしてください。

        ログインページ：%s/login

        再発行パスワード：%s

        ※ログイン後、パスワードの変更をお願い致します。

        *****************************************************
        BASEBALL ITEMカスタマーセンター
        URL：%s/
        Email：%s
        *****************************************************
        """,
        appUrl, newRawPassword, appUrl, mailProperties.fromAddress());

    mailService.send(null, email, subject, body);
  }

  /**
   * ログイン中ユーザーのパスワード変更。
   */
  public void changePassword(UserId userId, String oldPassword, String newPassword) {
    User user = userRepository
        .findById(userId)
        .orElseThrow(() -> new ValidationException("ユーザーが見つかりません"));

    if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
      throw new ValidationException("現在のパスワードが正しくありません");
    }
    if (oldPassword.equals(newPassword)) {
      throw new ValidationException("新しいパスワードは現在のパスワードと異なるものを設定してください");
    }
    Password.validateRawPassword(newPassword);

    Password updated = Password.fromHash(passwordEncoder.encode(newPassword));
    userRepository.updatePassword(userId, updated);

    String username = user.getProfile() != null && user.getProfile().getUsername() != null
        ? user.getProfile().getUsername() : "名前無し";
    String subject = "【パスワード変更通知】 ｜ BASEBALL ITEMカスタマーセンター";
    String body = String.format(
        """
        【パスワード変更メールです。心当たりにない場合はこのメッセージを削除してください。】

        %s　さん
        パスワードが変更されました。

        *****************************************************
        BASEBALL ITEMカスタマーセンター
        URL：%s/
        Email：%s
        *****************************************************
        """,
        username, appUrl, mailProperties.fromAddress());

    mailService.send(null, user.getEmail(), subject, body);
  }

  /**
   * プロフィール更新。メール変更があれば重複チェックを行う。
   */
  public void updateProfile(UserId userId, ProfileUpdate update) {
    User user = userRepository
        .findById(userId)
        .orElseThrow(() -> new ValidationException("ユーザーが見つかりません"));

    if (!user.getEmail().equals(update.email().value())
        && userRepository.existsByEmail(update.email())) {
      throw new ValidationException("email", "このメールアドレスは既に使用されています");
    }

    userRepository.updateProfile(userId, update);
  }

  /**
   * 退会（soft delete）。
   */
  public void withdraw(UserId userId) {
    User user = userRepository
        .findById(userId)
        .orElseThrow(() -> new ValidationException("ユーザーが見つかりません"));
    if (user.isDeleted()) {
      throw new ValidationException("既に退会済みです");
    }
    userRepository.withdraw(userId);
    log.info("ユーザー退会完了 user_id={}", userId.value());
  }

  public Optional<User> findById(UserId userId) {
    return userRepository.findById(userId);
  }

  public Optional<User> findByEmail(String email) {
    try {
      return userRepository.findByEmail(Email.fromString(email));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static String stripTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
