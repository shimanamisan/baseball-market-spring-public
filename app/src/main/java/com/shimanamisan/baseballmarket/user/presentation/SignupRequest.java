package com.shimanamisan.baseballmarket.user.presentation;

import jakarta.validation.constraints.NotBlank;

public class SignupRequest {

  @NotBlank(message = "入力必須です")
  private String email = "";

  @NotBlank(message = "入力必須です")
  private String password = "";

  @NotBlank(message = "入力必須です")
  private String passwordConfirm = "";

  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getPassword() { return password; }
  public void setPassword(String password) { this.password = password; }
  public String getPasswordConfirm() { return passwordConfirm; }
  public void setPasswordConfirm(String passwordConfirm) { this.passwordConfirm = passwordConfirm; }
}
