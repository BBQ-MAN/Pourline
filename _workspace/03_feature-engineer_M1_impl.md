# M1 구현 요약
- 모듈 4개(:app, :core:protocol, :core:brew, :core:ble), gradle/libs.versions.toml(2.2절 표 전 항목), 래퍼 9.8.2 그대로.
- :app은 compose·serialization 플러그인만 적용. KSP·Room·Roborazzi·navigation은 카탈로그에만 두고 쓰는 마일스톤에서 적용한다.
- KGP 버전은 buildEnvironment로 확인(2.4.21). 2.2절 수정 없음.
- 2.2절 navigation-suite: 카탈로그에 버전 없이 BOM 관리로 선언. 해석 판 확인은 :app이 의존을 쓰는 마일스톤에서 한다.
- 명세와 다른 점: 없음. core 모듈에는 빈 Package.kt만 있다. 테스트는 아직 없다.
