FROM mcr.microsoft.com/devcontainers/java:1-21-bullseye

# Gradleをインストール
RUN apt-get update && apt-get install -y gradle

# ワークディレクトリを設定
WORKDIR /app

# ポート8080を公開
EXPOSE 8080

# コンテナ起動時のコマンド
CMD ["bash"] 