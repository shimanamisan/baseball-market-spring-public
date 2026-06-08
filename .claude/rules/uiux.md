---
paths:
  - app/src/main/resources/templates/**/*
  - app/src/main/resources/static/**/*
---

# UI/UX & Frontend Rules (Thymeleaf)

旧プロジェクトの SSR + jQuery/Ajax 構成を尊重し、Spring Boot 側ではサーバーサイドレンダリングに Thymeleaf を使う（[architecture.md](../architecture.md) §6）。

## 技術スタック
- テンプレート: Thymeleaf（+ `thymeleaf-extras-springsecurity6`）
- 静的アセット: `app/src/main/resources/static/`（CSS / JS / 画像）
- 非同期（`like` 等）: 素の `fetch`（軽量。jQuery 脱却が暫定方針 — [replacement-policy.md](../replacement-policy.md) §3.3）

## ディレクトリ構造
テンプレートは **context 名でディレクトリを切る**（旧構成との対応を保つ）。
```
templates/
├── fragments/        # layout, header, footer などの共通フラグメント
│   └── layout.html
├── user/             # signup, login, profile ...
├── product/          # index, show, new, edit ...
├── message/
├── like/
└── shared/           # エラーページ等
```

## レイアウト（フラグメント）
```html
<!-- templates/fragments/layout.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="ja">
<head th:fragment="head(title)">
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title th:text="${title}">baseball-market</title>
    <link rel="stylesheet" th:href="@{/css/app.css}">
</head>
<body>
    <header th:fragment="header">...</header>
</body>
</html>
```

```html
<!-- 利用側 templates/product/index.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="ja">
<head th:replace="~{fragments/layout :: head('商品一覧')}"></head>
<body>
    <header th:replace="~{fragments/layout :: header}"></header>
    <main>
        <ul>
            <li th:each="product : ${products}">
                <a th:href="@{/products/{id}(id=${product.id})}" th:text="${product.name}">商品名</a>
            </li>
        </ul>
    </main>
</body>
</html>
```

## フォーム + バリデーション表示
- Controller で `@Valid @ModelAttribute` を受け、`BindingResult` のエラーを表示する。
- `th:action="@{/...}"` を使うと CSRF トークンが自動付与される（Spring Security 有効時）。

```html
<form th:action="@{/products}" th:object="${form}" method="post">
    <div>
        <label for="name">商品名</label>
        <input type="text" id="name" th:field="*{name}">
        <p class="error" th:if="${#fields.hasErrors('name')}" th:errors="*{name}">エラー</p>
    </div>
    <button type="submit">登録</button>
</form>
```

## 認証状態による出し分け（Spring Security 拡張）
```html
<html xmlns:sec="http://www.thymeleaf.org/extras/spring-security">
<div sec:authorize="isAuthenticated()">
    <span sec:authentication="name">ユーザー名</span>
    <form th:action="@{/logout}" method="post"><button>ログアウト</button></form>
</div>
<div sec:authorize="isAnonymous()">
    <a th:href="@{/login}">ログイン</a>
</div>
```

## 非同期（いいね機能 / fetch）
Controller 側は `@ResponseBody` で JSON を返す。CSRF トークンは meta から読んでヘッダに付与する。
```html
<meta name="_csrf" th:content="${_csrf.token}">
<meta name="_csrf_header" th:content="${_csrf.headerName}">
<button class="like-btn" th:data-product-id="${product.id}">♡</button>
```
```javascript
// static/js/like.js
document.querySelectorAll('.like-btn').forEach(btn => {
  btn.addEventListener('click', async () => {
    const id = btn.dataset.productId;
    const header = document.querySelector('meta[name="_csrf_header"]').content;
    const token = document.querySelector('meta[name="_csrf"]').content;
    const res = await fetch(`/products/${id}/likes`, {
      method: 'POST',
      headers: { [header]: token }
    });
    if (res.ok) {
      const data = await res.json();
      btn.textContent = data.liked ? '♥' : '♡';
    }
  });
});
```

## 原則
- コンポーネント（フラグメント）ベースで共通化し、重複マークアップを避ける
- モバイルファースト / レスポンシブ
- WCAG 2.1 AA を意識（label と input の関連付け、フォーカス可視、`aria-*`、エラーは `role="alert"`）

## アクセシビリティ
```html
<button aria-label="商品を削除">
    <svg aria-hidden="true">...</svg>
</button>

<div id="name-error" role="alert" th:if="${#fields.hasErrors('name')}" th:errors="*{name}"></div>
<input th:field="*{name}" aria-required="true" aria-describedby="name-error">
```

## XSS / エスケープ
- 出力は `th:text`（自動エスケープ）を基本にする。`th:utext`（非エスケープ）は信頼できる値に限定し、ユーザー入力には使わない。

## 画像
```html
<img th:src="@{/uploads/{f}(f=${product.imagePath})}"
     th:alt="${product.name}" loading="lazy">
```
アップロード画像は `shared.infrastructure.storage` 経由で `static/uploads/` 配下に保存する想定。
