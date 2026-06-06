package com.shimanamisan.baseballmarket.shared.infrastructure.web;

import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 横断的な例外ハンドラ。
 *
 * 旧 PHP の各 Controller が個別に try/catch で行っていた「errors マップを画面に渡してリダイレクト」を統一する。
 * 配置: architecture.md §2 で shared は presentation を持たないため、Spring の関心事として infrastructure 配下に置く。
 */
@ControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ValidationException.class)
  public ModelAndView handleValidation(ValidationException e, HttpServletRequest request) {
    log.debug("ValidationException at {}: {}", request.getRequestURI(), e.getMessage());
    ModelAndView mav = new ModelAndView("shared/error");
    mav.setStatus(HttpStatus.BAD_REQUEST);
    mav.addObject("errors", e.getErrors());
    mav.addObject("message", e.getFirstError());
    return mav;
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ModelAndView handleNotFound(NoResourceFoundException e, HttpServletRequest request) {
    log.debug("404 at {}: {}", request.getRequestURI(), e.getMessage());
    ModelAndView mav = new ModelAndView("shared/error");
    mav.setStatus(HttpStatus.NOT_FOUND);
    mav.addObject("message", "ページが見つかりません");
    return mav;
  }

  @ExceptionHandler(Exception.class)
  public ModelAndView handleUnexpected(Exception e, HttpServletRequest request) {
    log.error("Unhandled exception at {}: {}", request.getRequestURI(), e.getMessage(), e);
    ModelAndView mav = new ModelAndView("shared/error");
    mav.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    mav.addObject("message", "エラーが発生しました。しばらく経ってからやり直してください。");
    return mav;
  }
}
