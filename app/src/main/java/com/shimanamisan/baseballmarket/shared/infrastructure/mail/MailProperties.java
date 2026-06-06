package com.shimanamisan.baseballmarket.shared.infrastructure.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.properties の app.mail.* を束ねる設定 record。
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(String fromAddress, String fromName) {}
