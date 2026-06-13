package com.shimanamisan.baseballmarket.like.application;

/**
 * お気に入りトグルの結果。
 *
 * 旧 PHP の文字列 'add' / 'remove' を型で表現する。Ajax レスポンスへ変換する際の値は presentation 層が決める。
 */
public enum ToggleResult {
  ADD,
  REMOVE
}
