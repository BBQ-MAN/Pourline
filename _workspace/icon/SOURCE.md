# 앱 아이콘 출처

- 원본: `app_icon_v1.png` (1254×1254). Codex CLI(OpenAI 이미지 생성)로 생성, 2026-11-02.
- 원본 위치: `~/.codex/generated_images/01a197f2-85d8-77e1-9f40-d404e18efcb5/exec-be117a25-cee0-4e81-b75f-7cb16d31ff71.png`
- 프롬프트: "Generate an app icon image: a minimal, flat, organic illustration for a pour-over coffee brewing scale companion app. A single smooth rounded water drop falling into a soft curved coffee server silhouette, with one gentle flowing ribbon curve suggesting the brew-flow graph. Warm coffee brown (#6F4A2E) and cream (#FFDCC4) shapes with a small muted teal (#2F6A5A) accent, on a solid warm cream background (#FFF8F5). Centered composition, the motif fits inside the central 60% of the canvas with generous empty margin, no text, no letters, no logos, no brand marks, no gradients heavier than subtle, no drop shadow, square 1:1."
- 변환: 그림을 각 밀도 캔버스의 80%로 줄여 배경색 #FEF8F6(원본 배경 표본)으로 채움 → `cwebp -lossless` → `app/src/main/res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_foreground.webp` (108/162/216/324/432 px). 적응형 아이콘 `mipmap-anydpi-v26/ic_launcher(_round).xml`, 배경 `@color/ic_launcher_background`(#FEF8F6). minSdk 26이라 레거시 PNG 아이콘은 없음.
- 미포함: 테마 아이콘용 monochrome 레이어.
