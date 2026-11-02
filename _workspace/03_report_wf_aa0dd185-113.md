# 구현 보고 wf_aa0dd185-113 (2026-10-29)

| 마일스톤 | 상태 | 수정 라운드 | verify | 완료 조건 | 누락 리뷰어 | 남은 지적 |
|---|---|---|---|---|---|---|
| M1 | done | 0 | True | pass 8/8 | 없음 | residual 0, uncertain 0, low 5, rejected 0 |
| M2 | done | 0 | True | pass 10/10 | 없음 | residual 0, uncertain 0, low 5, rejected 0 |
| M3 | done | 0 | True | pass 12/12 | 없음 | residual 0, uncertain 0, low 4, rejected 0 |

메인 재확인: verify.sh 종료 코드 0, 테스트 112건(건너뜀 0, 실패 0), 로그 _workspace/build/verify_20261029_103437.log

## low 지적(차단 아님, 다음 마일스톤에서 처리)

- **M1** [qa] 2.2절이 M1에 맡긴 navigation-suite 판 확인을 미루고 기록도 남기지 않음 (값 자체는 맞음) — /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/_workspace/03_feature-engineer_M1_impl.md:5 — 고칠 방법: impl.md에 'BOM 2026.09.00이 material3-adaptive-navigation-suite 1.4.0을 관리함(확인함)'을 적는다. 그리고 '명세와 다른 점: 없음' 줄을 실제 내용과 맞춘다. 코드 변경은 없다
- **M1** [kotlin] MainActivity에 enableEdgeToEdge() 없음 — app/src/main/java/com/timemoredot/app/MainActivity.kt:13 — 고칠 방법: super.onCreate 다음, setContent 앞에 enableEdgeToEdge()를 넣는다. 인셋은 Scaffold 한 곳에서만 처리한다.
- **M1** [kotlin] Manifest에 enableOnBackInvokedCallback 없음 — app/src/main/AndroidManifest.xml:3 — 고칠 방법: <application>에 android:enableOnBackInvokedCallback="true"를 추가한다. M1에서 못 하면 내비게이션 마일스톤에서 반드시 넣는다.
- **M1** [kotlin] 플랫폼 Material 테마와 하드코딩 앱 이름 — app/src/main/AndroidManifest.xml:6 — 고칠 방법: Theme.Material.DayNight.NoActionBar 계열 res/values/themes.xml과 res/values/strings.xml(app_name)로 옮긴다. 화면 구현 마일스톤 전까지 처리해도 된다.
- **M1** [kotlin] core:ble에 release 난독화와 lint 설정 없음 (참고) — core/ble/build.gradle.kts:5 — 고칠 방법: M1에서는 조치하지 않는다. release 빌드 설정을 추가하는 마일스톤에서 minify와 keep 규칙을 함께 정한다.
- **M2** [kotlin] Unused dependencies in a pure-Kotlin codec module — core/protocol/build.gradle.kts:15-16 — 고칠 방법: Remove the `api(coroutines)` line and the `java-test-fixtures` plugin unless a later milestone needs them. Otherwise, add them in that milestone.
- **M2** [kotlin] Set allocation on every decode call — core/protocol/src/main/kotlin/com/timemoredot/protocol/FrameCodec.kt:43,45 — 고칠 방법: Use direct comparisons (`cmd == CMD_NAME || cmd == CMD_FIRMWARE`) or hoist the sets into private top-level vals.
- **M2** [kotlin] Buffer re-copied on every feed and frame — core/protocol/src/main/kotlin/com/timemoredot/protocol/FrameReassembler.kt:12,21,25,32 — 고칠 방법: Keep a read index and compact once at the end of feed(), or accept the cost and add a comment.
- **M2** [kotlin] FirmwareVersion decoding maps bytes through a boxed list — core/protocol/src/main/kotlin/com/timemoredot/protocol/FrameCodec.kt:67-69 — 고칠 방법: Use `buildString` or `String(CharArray)`. Make the last `when` branch an explicit `cmd == CMD_FIRMWARE` and add an `else` that fails.
- **M2** [kotlin] WeightReport.weightRaw can overflow to negative — core/protocol/src/main/kotlin/com/timemoredot/protocol/FrameCodec.kt:58 — 고칠 방법: If weight is unsigned, document that it is signed by design, or use Long. Add a vector or test covering the high bit.
- **M3** [qa] [Q18 인접·계약] onUnitReadFailed()가 architecture.md 5.3 시그니처 주석과 다르게 동작하는데 명세에 반영되지 않았고 그 분기를 검증하는 테스트도 없다 — /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/core/protocol/src/main/kotlin/com/timemoredot/protocol/ScaleStateReducer.kt:48 — 고칠 방법: architecture.md 5.3의 onUnitReadFailed 주석을 '단위 미상일 때만 unitReadFailed = true'로 고치도록 설계자에게 요청한다. 그리고 ScaleStateReducerTest에 'UnitReport(00) 뒤 onUnitReadFailed() → unitReadFailed=false 유지' 한 건을 추가한다.
- **M3** [kotlin] ScaleStateReducer 상태 var가 스레드 안전하지 않음 — /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/core/protocol/src/main/kotlin/com/timemoredot/protocol/ScaleStateReducer.kt:23 — 고칠 방법: 클래스 KDoc에 호출은 단일 코루틴(직렬)에서만 한다고 명시하거나 @Volatile과 락을 쓴다. :core:ble에서 단일 디스패처로 직렬화되는지 M4에서 확인한다.
- **M3** [kotlin] Advertisement가 ByteArray를 담은 data class라 equals/hashCode가 참조 기준 — /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/core/protocol/src/main/kotlin/com/timemoredot/protocol/Advertising.kt:11 — 고칠 방법: 일반 class로 바꾸거나, equals/hashCode를 contentEquals 기준으로 재정의하거나, 비교에 쓰지 않는다는 주석을 단다.
- **M3** [kotlin] FakeScaleClient.send는 중단점이 없고 실제 계약(한 번에 하나, 응답까지 대기, 시간 제한)을 흉내 내지 않음 — /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/core/protocol/src/testFixtures/kotlin/com/timemoredot/protocol/FakeScaleClient.kt:64 — 고칠 방법: 필요해지는 시점에 respondWith에 CompletableDeferred나 지연 옵션을 추가한다. M3 완료 조건에는 영향이 없다.

## 요약

- **M1**: M1 done. Four modules, version catalog with every 2.2 table entry, empty Compose MainActivity and TimemoreApp. verify.sh exit 0 (test, lintDebug, assembleDebug). Acceptance greps all return 0 lines. buildEnvironment resolves KGP 2.4.21, so the 2.2 table is unchanged. KSP, Room, Roborazzi and navigation are in the catalog only and get applied in later milestones. The navigation-suite artifact is declared BOM-managed, but its resolved version is not checked yet. The core modules hold only empty Package.kt files and there are no tests yet.
- **M2**: M2 protocol codec is in :core:protocol. verify.sh :core:protocol:test finished with exit code 0. It ran 90 tests with 0 skipped and 0 failed: ProtocolVectorsTest 75 (one test case per vector, all 75 vectors), CrcRuleMatchesVectorsTest 3, FrameReassemblerTest 7, FrameCodecTest 5. The 8 unconfirmed tx vectors run under a "[unconfirmed]" name prefix. All 8 pass. If one fails, the failure goes to stderr only and the build does not stop.

Main code is in /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/core/protocol/src/main/kotlin/com/timemoredot/protocol/:
- ProtocolConfig.kt: TX_CRC, RawScale, ProtocolConfig, and internal opcode/cmd constants. Its only TODO(device) is on the ounceMode line and cites C-01-4 and C-01-6.
- WeightUnit.kt
- ScaleFrame.kt: commands, frames, results and responseKey.
- FrameCodec.kt: encode adds TX_CRC 00 00 to every frame. SetName is cut to 16 bytes at a code point boundary, and an empty name returns InvalidName. The internal decode follows protocol.md section 3.
- FrameReassembler.kt: follows protocol.md 2.3 rules 1-9. It reads the 2 CRC bytes and discards them.
- Deleted the empty Package.kt from M1.

Tests read docs/spec/protocol-vectors.json directly. build.gradle.kts passes the file path as a system property and declares the file as a test input. Tests use testImplementation kotlinx-serialization-json, JsonElement only, so no serialization plugin is needed.

Grep checks: the CRC grep (0x8005|crc16|crc-16|refin|xorout|CrcMismatch) finds 0 lines. The only TODO(device) is in ProtocolConfig.kt on the ounceMode line.

Public signatures match architecture.md 5.1 and 5.2. The only additions are internal: FrameCodec.decode, FrameReassembler.pendingByteCount (used by tests to check for leftover bytes) and the constants. The debug frame log belongs to :core:ble and is deferred to M4.

Summary file: /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/_workspace/03_ble-engineer_M2_impl.md

Not checked: the full verify.sh default run (test lintDebug assembleDebug) was not run. Only :core:protocol:test ran.
- **M3**: M3 is done and `verify.sh :core:protocol:test` exits 0. 112 tests ran with 0 failures and 0 skipped. The 22 new ones are ScaleStateReducerTest 13, AdvertisingTest 4 and FakeScaleClientTest 5; none of the unconfirmed vectors failed.

New main sources in /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/core/protocol/src/main/kotlin/com/timemoredot/protocol/:
- ScaleStateReducer.kt: MonotonicClock, ScaleReading, ScaleStatus and ScaleStateReducer (architecture.md 5.3).
- Advertising.kt: the advertising constants, Advertisement, isScaleCandidate and EXPECTED_MODEL.
- ScaleClient.kt: ScanCandidate, ScaleDevice, FailureReason, ConnectionState, CommandFailure, CommandResult and the ScaleClient interface with timerEvents (5.4).

FakeScaleClient and the readings() helper are in core/protocol/src/testFixtures/kotlin/com/timemoredot/protocol/FakeScaleClient.kt. java-test-fixtures was already applied in core/protocol/build.gradle.kts, so that file is unchanged.

All three grep conditions hold:
- `TODO(device)` appears only on the ounceMode line in ProtocolConfig.kt and the isScaleCandidate line in Advertising.kt.
- `"TES017"` appears only on the EXPECTED_MODEL line in Advertising.kt.
- `fff0` appears only on the SCALE_SERVICE_UUID line in Advertising.kt. A comment in ScaleClient.kt also matched at first, so I reworded it.

Three differences from the spec, also listed in the impl report:
1. I added one public function not in the spec, `ScaleStatus.afterSuccess(command)`. The reducer and the fake both use it, so a successful write updates status by one shared rule. It is public so testFixtures does not depend on internal visibility.
2. `onUnitReadFailed()` does nothing if the unit is already known. The 5.3 comment just says it sets the flag, but the field is defined as "unit still unknown". Without this, a unit learned from a Notify before ReadUnit times out would still show the timed-out state. From the unknown state the flag is set as the spec says.
3. A successful SetUnit also sets unitReadFailed back to false.

Not checked: the full `verify.sh` (test, lintDebug, assembleDebug) was not run; only `:core:protocol:test` was. The fake does not change connection state on connect or disconnect calls; tests have to drive it with setConnection.
