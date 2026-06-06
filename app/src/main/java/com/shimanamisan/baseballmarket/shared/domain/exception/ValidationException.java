package com.shimanamisan.baseballmarket.shared.domain.exception;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ドメイン制約違反を表す例外。
 *
 * 旧 PHP の Shared\Domain\Exception\ValidationException を踏襲する:
 * - 単一メッセージ → errors["common"] に格納
 * - フィールド別エラー → errors[fieldName] に格納
 */
public class ValidationException extends DomainException {

  private static final String DEFAULT_MESSAGE = "バリデーションエラーが発生しました";

  private final Map<String, String> errors;

  public ValidationException(String message) {
    super(message);
    this.errors = singletonError("common", message);
  }

  public ValidationException(Map<String, String> errors) {
    super(DEFAULT_MESSAGE);
    this.errors = new LinkedHashMap<>(errors);
  }

  public ValidationException(String field, String message) {
    super(message);
    this.errors = singletonError(field, message);
  }

  public Map<String, String> getErrors() {
    return Map.copyOf(errors);
  }

  public String getFirstError() {
    return errors.values().stream().findFirst().orElse(null);
  }

  private static Map<String, String> singletonError(String field, String message) {
    var map = new LinkedHashMap<String, String>();
    map.put(field, message);
    return map;
  }
}
