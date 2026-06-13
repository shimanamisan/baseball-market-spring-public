package com.shimanamisan.baseballmarket.like.presentation;

import com.shimanamisan.baseballmarket.like.application.LikeService;
import com.shimanamisan.baseballmarket.like.application.ToggleResult;
import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import com.shimanamisan.baseballmarket.user.application.UserService;
import com.shimanamisan.baseballmarket.user.domain.User;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * お気に入りトグルの Ajax（JSON）エンドポイント。旧 PHP ajaxLike.php / LikeController::toggle() を踏襲する。
 *
 * 認証ユーザーは Spring Security の Principal から解決する（旧 PHP のセッション直参照は移植しない）。
 * /likes/** は permitAll とし、未ログインは例外ではなく {"response":"no_login"} を返す（旧 UX 踏襲）。
 * JSON を返すため例外は GlobalExceptionHandler（HTML を返す）に委ねず、この Controller 内で JSON 化する。
 */
@RestController
public class LikeController {

  private static final Logger log = LoggerFactory.getLogger(LikeController.class);

  private final LikeService likeService;
  private final UserService userService;

  public LikeController(LikeService likeService, UserService userService) {
    this.likeService = likeService;
    this.userService = userService;
  }

  @PostMapping(value = "/likes/toggle", produces = MediaType.APPLICATION_JSON_VALUE)
  public LikeToggleResponse toggle(
      @RequestParam("productId") int productId, Principal principal) {
    if (principal == null) {
      return LikeToggleResponse.noLogin();
    }

    User currentUser = userService.findByEmail(principal.getName()).orElse(null);
    if (currentUser == null) {
      return LikeToggleResponse.noLogin();
    }

    try {
      ToggleResult result = likeService.toggleLike(currentUser.getId(), productId);
      return result == ToggleResult.ADD ? LikeToggleResponse.add() : LikeToggleResponse.remove();
    } catch (ValidationException e) {
      log.debug("[like:toggle] productId={} {}", productId, e.getFirstError());
      return LikeToggleResponse.error(e.getFirstError());
    } catch (RuntimeException e) {
      log.error("[like:toggle] 予期しないエラー productId={}", productId, e);
      return LikeToggleResponse.error("エラーが発生しました");
    }
  }

  /** productId 欠落（旧 PHP: パラメータが不正です）。 */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  @ResponseBody
  public LikeToggleResponse handleMissingParam(MissingServletRequestParameterException e) {
    return LikeToggleResponse.error("パラメータが不正です");
  }

  /** productId が整数でない（旧 PHP: 商品IDが不正です）。 */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  @ResponseBody
  public LikeToggleResponse handleTypeMismatch(MethodArgumentTypeMismatchException e) {
    return LikeToggleResponse.error("商品IDが不正です");
  }
}
