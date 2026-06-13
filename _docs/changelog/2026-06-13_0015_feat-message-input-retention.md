# 2026-06-13 00:15 メッセージ送信エラー時に入力本文を保持

> フェーズ 7（message context）の UX 改善。Issue #12 / PR #25 由来。changelog 未作成だったため遡って記録。

## 背景

掲示板のメッセージ送信は PRG（Post-Redirect-Get）構成で、バリデーションエラー（空本文・500 文字超過）時はフラッシュにエラーを載せて同 URL へリダイレクトする。

このとき**入力していた本文は失われ**、ユーザーは長文を打ち直す羽目になっていた。旧 PHP でも入力保持は不十分だった箇所で、リプレースを機に改善する。

## 変更内容

### 1. Controller でエラー時に入力本文をフラッシュへ

- `message/presentation/MessageController.send` の `ValidationException` catch 節に 1 行追加:
  ```java
  redirect.addFlashAttribute("submittedMsg", form.getMsg()); // 入力本文を保持し再入力を防ぐ
  ```
- 正常送信時は `submittedMsg` を載せない（PRG リダイレクトのみ）。

### 2. テンプレートで textarea に復元

- `templates/message/msg.html` の本文 `<textarea>` に `th:text="${submittedMsg}"` を付与。エラーで戻った直後のみ前回入力が復元され、通常表示時は空のまま。

### 3. テスト（新規 `MessageControllerTest`）

| テスト | 検証内容 |
| --- | --- |
| エラー時に入力保持 | `ValidationException` 発生時、`submittedMsg`（入力本文）と `errorMessage` をフラッシュに載せ、`redirect:/message?b_id=` を返す |
| 正常時は非保持 | 正常送信時は `submittedMsg` / `errorMessage` を一切載せず PRG のみ |

## 検証

| 項目 | 結果 |
| --- | --- |
| `MessageControllerTest`（Mockito、エラー時保持 / 正常時非保持） | ✅ 全緑 |

## 分析・判断記録

### フラッシュ属性で渡す理由

PRG のリダイレクト先（GET）に一度だけ値を渡す用途はフラッシュ属性が定石。リクエストスコープでは redirect 後に消えるため不適。`submittedMsg` はエラー往復時のみ存在し、リロードで自然に消える（次の GET ではフラッシュが空）ため、表示状態がべたつかない。

### XSS

`th:text` は Thymeleaf が自動エスケープするため、入力本文をそのまま復元しても安全（既存の本文表示と同じ方針）。
