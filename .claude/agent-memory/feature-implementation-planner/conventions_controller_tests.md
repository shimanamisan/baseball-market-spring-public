---
name: conventions-controller-tests
description: このプロジェクトの Controller テストは @WebMvcTest でなく plain Mockito で Controller メソッドを直接呼ぶ
metadata:
  type: feedback
---

このプロジェクトの Controller テストは `@WebMvcTest`（MockMvc）を使わず、**plain Mockito で Controller を `new` して対象メソッドを直接呼ぶ**スタイルで統一されている。`MessageControllerTest` / `LikeControllerTest` / `MyPageControllerTest` がいずれもこの形。

**Why:** 既存の慣習。MockMvc を持ち込むと様式が割れる。レビュー（ddd-code-reviewer）もこのスタイルを承認している。

**How to apply:** 新規 Controller のテストを書くときは `@ExtendWith(MockitoExtension.class)` で依存（Service / UserService / Principal / RedirectAttributes 等）を `@Mock` し、`new XxxController(...)` でインスタンス化してメソッドを直呼びする。Model は `org.springframework.ui.ConcurrentModel` を使うとモデル属性を検証できる。Principal は `@Mock` して `when(principal.getName()).thenReturn(email)`、`userService.findByEmail(email)` で User を返すのが定番（Principal→User→userId の解決パターン）。
