package com.shimanamisan.baseballmarket.user.presentation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ProfileEditRequest {

  @NotBlank(message = "メールアドレスを入力してください")
  private String email = "";

  @Size(max = 255, message = "ユーザー名は255文字以内で入力してください")
  private String username = "";

  @Min(value = 0, message = "年齢は0〜150の数字で入力してください")
  @Max(value = 150, message = "年齢は0〜150の数字で入力してください")
  private Integer age;

  @Pattern(regexp = "^$|^[0-9]{10,11}$", message = "電話番号はハイフンなし10〜11桁の数字で入力してください")
  private String tel = "";

  @Pattern(regexp = "^$|^[0-9]{7}$", message = "郵便番号はハイフンなし7桁の数字で入力してください")
  private String zip = "";

  @Size(max = 10, message = "都道府県は10文字以内で入力してください")
  private String prefecture = "";

  @Size(max = 100, message = "市区町村は100文字以内で入力してください")
  private String city = "";

  @Size(max = 255, message = "番地は255文字以内で入力してください")
  private String street = "";

  @Size(max = 255, message = "建物名は255文字以内で入力してください")
  private String building = "";

  public String getEmail() { return email; }
  public void setEmail(String v) { this.email = v; }
  public String getUsername() { return username; }
  public void setUsername(String v) { this.username = v; }
  public Integer getAge() { return age; }
  public void setAge(Integer v) { this.age = v; }
  public String getTel() { return tel; }
  public void setTel(String v) { this.tel = v; }
  public String getZip() { return zip; }
  public void setZip(String v) { this.zip = v; }
  public String getPrefecture() { return prefecture; }
  public void setPrefecture(String v) { this.prefecture = v; }
  public String getCity() { return city; }
  public void setCity(String v) { this.city = v; }
  public String getStreet() { return street; }
  public void setStreet(String v) { this.street = v; }
  public String getBuilding() { return building; }
  public void setBuilding(String v) { this.building = v; }
}
