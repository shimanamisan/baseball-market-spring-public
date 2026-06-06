package com.shimanamisan.baseballmarket.user.domain;

/**
 * プロフィール更新時の入力データ。
 * 各フィールドは null 許容（フォームの未入力に対応）。
 * email のみ Email VO で必須扱い、pic はサーバー側で決まったパス文字列。
 */
public record ProfileUpdate(
    String username,
    Integer age,
    String tel,
    String zip,
    String prefecture,
    String city,
    String street,
    String building,
    Email email,
    String pic) {}
