package com.shimanamisan.baseballmarket.shared.infrastructure.storage;

import com.shimanamisan.baseballmarket.shared.domain.exception.ValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 画像ファイル保存ユーティリティ。
 *
 * 旧 PHP の handleImageUpload を移植:
 * - 3MB 上限
 * - JPEG/PNG/GIF 許可
 * - ファイル名は UUID + 拡張子
 * - 保存先は app.uploads.path（dev は static/uploads/）
 *
 * @return DB に保存する相対パス "uploads/xxx.jpg"
 */
@Component
public class ImageStorage {

  private static final Logger log = LoggerFactory.getLogger(ImageStorage.class);

  private static final long MAX_SIZE_BYTES = 3 * 1024 * 1024;
  private static final List<String> ALLOWED_MIMES = List.of("image/jpeg", "image/png", "image/gif");

  private final Path uploadsRoot;

  public ImageStorage(@Value("${app.uploads.path}") String uploadsPath) {
    this.uploadsRoot = Path.of(uploadsPath);
    try {
      Files.createDirectories(this.uploadsRoot);
    } catch (IOException e) {
      throw new IllegalStateException("uploads ディレクトリの作成に失敗: " + uploadsRoot, e);
    }
  }

  /**
   * ファイル未指定の場合は null を返す。バリデーション失敗時は ValidationException。
   */
  public String save(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      return null;
    }
    if (file.getSize() > MAX_SIZE_BYTES) {
      throw new ValidationException("pic", "画像サイズは3MB以下にしてください");
    }

    String mime;
    try (InputStream in = file.getInputStream()) {
      mime = probeMime(in, file.getContentType());
    } catch (IOException e) {
      throw new ValidationException("pic", "画像の読み取りに失敗しました");
    }
    if (!ALLOWED_MIMES.contains(mime)) {
      throw new ValidationException("pic", "JPEG、PNG、GIF形式の画像のみアップロード可能です");
    }

    String ext = guessExtension(mime);
    String filename = UUID.randomUUID() + "." + ext;
    Path dest = uploadsRoot.resolve(filename);
    try {
      file.transferTo(dest);
    } catch (IOException e) {
      log.error("画像保存失敗 dest={}", dest, e);
      throw new ValidationException("pic", "画像の保存に失敗しました");
    }
    return "uploads/" + filename;
  }

  private static String probeMime(InputStream in, String fallback) throws IOException {
    String detected = java.net.URLConnection.guessContentTypeFromStream(in);
    if (detected == null && fallback != null) {
      return fallback;
    }
    return detected == null ? "" : detected;
  }

  private static String guessExtension(String mime) {
    return switch (mime) {
      case "image/jpeg" -> "jpg";
      case "image/png" -> "png";
      case "image/gif" -> "gif";
      default -> "bin";
    };
  }
}
