# P13 impl
- RecipeListScreen.kt, HistoryScreen.kt: Empty/Error 감싸개 contentAlignment Center -> TopCenter (EmptyState 위 32 dp 여백, 위쪽 정렬).
- RecipeEditScreen.kt: 원두/물 칸을 람다(doseField/waterField)로 빼고 fs >= 1.3f 이면 세로 쌓기, 아니면 기존 Row(weight 1f). StepCard와 같은 임계값(별도 판정 함수는 없어 같은 조건식 사용).
- 재기록: `:app:recordRoborazziDebug`가 screenshots/ 289장 전부를 다시 썼다(백업 없어 바이트 단위 변경분 구분 불가). 의도한 변경: recipes_empty/error_{light,dark}, history_empty/error_{light,dark}, recipe_edit_invalid_light_font200(및 원두/물 칸이 보이는 recipe_edit_*font200).
- 확인: recipes_empty_light, recipe_edit_invalid_light_font200 직접 확인(위쪽 정렬, 단위 줄바꿈 해소).
- verify.sh: 종료 0, _workspace/build/verify_20261102_064818.log
- verify.sh :app:verifyRoborazziDebug: 종료 0, _workspace/build/verify_20261102_064903.log
- record 로그: _workspace/build/verify_20261102_064530.log
