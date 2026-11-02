# DEMO-2 구현 요약

변경: app/src/{debug,release}/java/com/timemoredot/app/VariantHooks.kt, app/src/debug/AndroidManifest.xml(activity-alias .DemoLauncher),
TimemoreApp.container by lazy, MainActivity.onCreate 첫 문장 VariantHooks.onActivityCreate(this).
테스트(app/src/testDebug, release 단위 테스트에 안 섞이게 분리): VariantHooksTest(3), DemoBrewE2ETest(2).
debug VariantHooks에 resetForTest()가 더 있다(테스트 전용, release 시그니처와 다름. 프로세스 정적 상태 초기화용).

## release 제외 확인
명령: unzip -p <apk> 'classes*.dex' | grep -a -c -e 'com/timemoredot/ble/demo' -e 'com/timemoredot/protocol/demo' -e 'FakeScaleClient' -e 'DemoLauncher'
- release (app-release-unsigned.apk): 0
- debug (app-debug.apk): 32
- processReleaseMainManifest 병합 매니페스트 DemoLauncher: 0줄 / processDebugMainManifest: 1줄
- releaseRuntimeClasspath 'test-fixtures': 0
- release VariantHooks.kt grep -ci demo: 0, app/src/main의 DemoScale/DemoScenario/ble.demo 참조: 0

## 결과
verify.sh 종료 0, verify.sh :app:assembleRelease 종료 0.
디버그 프레임 로그: DemoScale.client가 debugFrameLog=true + androidLog(enabled=true)를 BleScaleClient에 넘김(ble-engineer 코드, 확인만).
DemoBrewE2ETest 하리오: ValveCue(120,true) 한 번, 앱 타이머 120~121 s.
