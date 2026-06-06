package com.shimanamisan.baseballmarket.shared.infrastructure.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * メール送信サービス（旧 PHP の Shared\Infrastructure\Mail\MailService 相当）。
 *
 * シグネチャは旧版を踏襲し send(from, to, subject, body) を提供する。
 * 差出人名は app.mail.from-name 既定値、from が null/空なら app.mail.from-address を使う。
 */
@Service
public class MailService {

  private static final Logger log = LoggerFactory.getLogger(MailService.class);

  private final JavaMailSender mailSender;
  private final MailProperties mailProperties;

  public MailService(JavaMailSender mailSender, MailProperties mailProperties) {
    this.mailSender = mailSender;
    this.mailProperties = mailProperties;
  }

  public boolean send(String from, String to, String subject, String body) {
    if (to == null || to.isBlank() || subject == null || subject.isBlank() || body == null) {
      log.warn("メール送信エラー: 必須パラメータが不足しています");
      return false;
    }

    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
      String resolvedFrom = (from == null || from.isBlank()) ? mailProperties.fromAddress() : from;
      helper.setFrom(new InternetAddress(resolvedFrom, mailProperties.fromName(), StandardCharsets.UTF_8.name()));
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(body, false);
      mailSender.send(message);
      log.info("メールを送信しました: to={}", to);
      return true;
    } catch (MailException | MessagingException | UnsupportedEncodingException e) {
      log.error("メール送信に失敗: to={}, reason={}", to, e.getMessage(), e);
      return false;
    }
  }
}
