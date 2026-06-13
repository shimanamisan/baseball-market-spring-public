# feature-implementation-planner — Memory Index

- [Gradle テストの回し方（JDK 無し環境）](env_running_gradle_tests.md) — サンドボックスに JDK 無し。temurin docker で /build 隔離コピー実行、gradlew は app/ 配下、contextLoads は db 別名衝突で別件失敗
- [Controller テストの慣習](conventions_controller_tests.md) — @WebMvcTest でなく plain Mockito で Controller を new して直呼び。Model は ConcurrentModel
