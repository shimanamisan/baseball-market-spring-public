package com.shimanamisan.baseballmarket.shared.infrastructure.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 静的リソース配信設定。
 *
 * <p>{@code /uploads/**} は 2 つのロケーションから配信する:
 *
 * <ul>
 *   <li>{@code file:${app.uploads.path}/} — ランタイムにユーザーが出品・プロフィール編集で
 *       アップロードした画像（{@link
 *       com.shimanamisan.baseballmarket.shared.infrastructure.storage.ImageStorage} の保存先）。
 *       このディレクトリは Git 追跡せず、環境ごとに増えていく。
 *   <li>{@code classpath:/db/seed/uploads/} — 開発用シード（{@code db/seed/*.sql}）が参照する
 *       fixture 画像の「正規置き場」。シード SQL と一緒にコミットされ、fresh clone / CI でも
 *       画像が解決できる。
 * </ul>
 *
 * <p>ランタイム保存先を先に探索し、無ければシード画像にフォールバックする。これにより
 * 「シード fixture」と「実ユーザーアップロード」を別ディレクトリに分離しつつ、配信 URL
 * （{@code /uploads/xxx}）は単一の名前空間に統一できる。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final String uploadsPath;

  public WebConfig(@Value("${app.uploads.path}") String uploadsPath) {
    this.uploadsPath = uploadsPath;
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/uploads/**")
        .addResourceLocations("file:" + uploadsPath + "/", "classpath:/db/seed/uploads/");
  }
}
