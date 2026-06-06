package com.shimanamisan.baseballmarket.user.presentation;

import jakarta.validation.constraints.NotBlank;

public class PasswordEditRequest {

  @NotBlank(message = "現在のパスワードを入力してください")
  private String passOld = "";

  @NotBlank(message = "新しいパスワードを入力してください")
  private String passNew = "";

  @NotBlank(message = "新しいパスワード（確認）を入力してください")
  private String passNewRe = "";

  public String getPassOld() { return passOld; }
  public void setPassOld(String v) { this.passOld = v; }
  public String getPassNew() { return passNew; }
  public void setPassNew(String v) { this.passNew = v; }
  public String getPassNewRe() { return passNewRe; }
  public void setPassNewRe(String v) { this.passNewRe = v; }
}
