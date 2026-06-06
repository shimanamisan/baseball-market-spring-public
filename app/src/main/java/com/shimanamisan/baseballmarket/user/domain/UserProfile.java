package com.shimanamisan.baseballmarket.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

  @Id
  @Column(name = "user_id")
  private Integer userId;

  @OneToOne(fetch = FetchType.LAZY)
  @MapsId
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "username", length = 255)
  private String username;

  @Column(name = "age")
  private Integer age;

  @Column(name = "tel", length = 255)
  private String tel;

  @Column(name = "zip", length = 255)
  private String zip;

  @Column(name = "prefecture", length = 10)
  private String prefecture;

  @Column(name = "city", length = 100)
  private String city;

  @Column(name = "street", length = 255)
  private String street;

  @Column(name = "building", length = 255)
  private String building;

  @Column(name = "pic", length = 255)
  private String pic;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected UserProfile() {}

  public UserProfile(User user) {
    this.user = user;
  }

  @PrePersist
  void onPersist() {
    var now = LocalDateTime.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  public Integer getUserId() { return userId; }
  public User getUser() { return user; }
  public void setUser(User user) { this.user = user; }
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
  public String getPic() { return pic; }
  public void setPic(String v) { this.pic = v; }
}
