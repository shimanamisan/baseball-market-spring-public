package com.shimanamisan.baseballmarket.like.presentation;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * お気に入りトグル Ajax のレスポンス DTO。
 *
 * 旧 PHP の JSON 形 {"response": "...", "message": "..."} を踏襲する。
 * response: "add" | "remove" | "no_login" | "error"
 * message は error 時のみ含める（null は出力しない）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LikeToggleResponse(String response, String message) {

  public static LikeToggleResponse noLogin() {
    return new LikeToggleResponse("no_login", null);
  }

  public static LikeToggleResponse add() {
    return new LikeToggleResponse("add", null);
  }

  public static LikeToggleResponse remove() {
    return new LikeToggleResponse("remove", null);
  }

  public static LikeToggleResponse error(String message) {
    return new LikeToggleResponse("error", message);
  }
}
