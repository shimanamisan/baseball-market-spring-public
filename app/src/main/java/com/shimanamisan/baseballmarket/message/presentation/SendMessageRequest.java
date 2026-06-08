package com.shimanamisan.baseballmarket.message.presentation;

/**
 * メッセージ送信フォームの入力。本文の検証は domain の MessageContent に委ねる。
 */
public class SendMessageRequest {

  private String msg;

  public String getMsg() {
    return msg;
  }

  public void setMsg(String msg) {
    this.msg = msg;
  }
}
