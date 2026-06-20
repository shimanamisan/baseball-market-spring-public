package com.shimanamisan.baseballmarket.shared.infrastructure.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 開発用シード画像（{@code db/seed/*.sql} が参照する fixture）の静的配信設定。
 *
 * <p>{@code /seed-images/**} を {@code file:${app.seed.images.path}/}（リポジトリ内
 * {@code app/seed-data/images/}・<b>classpath 外</b>）から配信する。ランタイムのユーザー
 * アップロード（{@code /uploads/**}・{@link WebConfig}）とは URL 名前空間・保存場所ともに
 * 完全に分離している。
 *
 * <p><b>{@code @Profile("dev")} 限定</b>のため本番では本 Bean 自体がロードされず、シード画像は
 * 物理的にも構造的にも本番から分離される（fixture は classpath 外で jar に同梱されず、配信
 * ハンドラも本番には存在しない）。
 */
@Configuration
@Profile("dev")
public class SeedImageWebConfig implements WebMvcConfigurer {

  private final String seedImagesPath;

  public SeedImageWebConfig(@Value("${app.seed.images.path}") String seedImagesPath) {
    // 空文字だと "file:/" となりサーバのルート全体が /seed-images/** で露出するため拒否（dev 限定 Bean だが堅牢化）。
    // あわせて末尾スラッシュを正規化し、設定値の有無で // にならないようにする。
    if (seedImagesPath == null || seedImagesPath.isBlank()) {
      throw new IllegalArgumentException("app.seed.images.path must not be blank");
    }
    this.seedImagesPath = seedImagesPath.endsWith("/") ? seedImagesPath : seedImagesPath + "/";
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/seed-images/**").addResourceLocations("file:" + seedImagesPath);
  }
}
