package com.shimanamisan.baseballmarket.shared.infrastructure.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 静的リソース配信設定。
 *
 * <p>{@code /uploads/**} を {@code file:${app.uploads.path}/} から配信する。これはランタイムに
 * ユーザーが出品・プロフィール編集でアップロードした画像（{@link
 * com.shimanamisan.baseballmarket.shared.infrastructure.storage.ImageStorage} の保存先）であり、
 * Git 追跡せず環境ごとに増えていく。全プロファイルで有効。
 *
 * <p>開発用シード（{@code db/seed/*.sql}）が参照する fixture 画像は名前空間を分離し、dev 限定の
 * {@link SeedImageWebConfig} が {@code /seed-images/**} として別途配信する。本番には混入しない。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final String uploadsPath;

  public WebConfig(@Value("${app.uploads.path}") String uploadsPath) {
    // 空文字だと "file:/" となりサーバのルート全体が /uploads/** で露出するため拒否。
    // あわせて末尾スラッシュを正規化し、設定値の有無で // にならないようにする。
    if (uploadsPath == null || uploadsPath.isBlank()) {
      throw new IllegalArgumentException("app.uploads.path must not be blank");
    }
    this.uploadsPath = uploadsPath.endsWith("/") ? uploadsPath : uploadsPath + "/";
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/uploads/**").addResourceLocations("file:" + uploadsPath);
  }
}
