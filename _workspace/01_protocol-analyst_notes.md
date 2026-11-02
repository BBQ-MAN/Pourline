# protocol-analyst 메모 (2026-10-28)

- 원문: docs/protocol/source/Timemore Black Mirror SCALE BLE Protocol.md v1.0.3 (파일 1개). 실기기 캡처 없음.
- 기존 docs/spec 없음 → 최초 작성.
- CRC 후보: MODBUS(poly 0x8005, init 0xFFFF, refin/refout true, xorout 0), CRC big-endian, 범위 헤더 포함(--start 0). 원문 이름 "CRC-16/IBM"(ARC, init 0)과 init 0xFFFF가 충돌 → 미확정.
- 벡터 53건, 모두 crc_probe.py --build로 생성(생성기: 세션 scratchpad gen_vectors.py), 독립 MODBUS 구현으로 CRC·length 재검증 통과.
- tx-name-write-empty-001만 confirmed(CRC 무관, 원문 §4 1~16바이트).
- 실기기에서 먼저 닫을 항목: F-8/F-9(CRC), C-01-3/C-01-4(무게 배율), G-5(응답 경로), A-1/A-2(광고).

## 프로브 A 반영 (2026-10-28, TES017 펌웨어 v1.0.4)

- CRC: RX 14,578프레임 CRC 전부 `0000`. `crc_probe.py --file` "완전 일치 조합 없음". CRC `FFFF` 영점 `A55A030D0000FFFF`(log:2723)도 성공 `A55A030D0001010000`(log:2726). → F-8 "CRC 미사용·수신 검사 안 함", F-9 "송신 `0000`" 확정. 원문 §3 L64와 충돌, 로그 우선.
- CRC 없는 7바이트 `A55A0302000101`(log:3673)과 1 ms 뒤 `A55A03020001020000`(log:3675) → 응답 1개(log:3677). F-12로 기록.
- 결합: 알림당 2프레임 61회, 3프레임 2회(log:3677, 3897), 4프레임 2회(log:3715, 3975). 결과 요약의 "2~3개"는 실제 최대 4개.
- 0x04 Notify `A55A0104000501000000000000` 등. 원문 없음, C-04-1.
- 광고 `{'0xFFFF': '0201'}`(log:1, 이름 있음) / `'0101'`(log:6738, 이름 없음).
- 벡터: 53 → 75건. 모든 CRC `0000`으로 재작성(rx-crc-bad-001만 `4106` 유지, 기대값 WeightReport로 변경). device 벡터 22건 추가, tx 9건은 실기기 송신 바이트와 같아 origin device. 생성기: scratchpad upd_vectors.py, 독립 디코더로 75건 대조 통과.

## 교차 검증 반영 (2026-10-29)

- 반박 4건 → 근거로 수정, "미확정(수정됨, 재검증 필요)": G-6(연결 3회, 영점 연결 1·3, 타이머 연결 1), C-01-7 감소 시 동작(log:3291-3293 무게 −31·유량 5처럼 감소 중 유량 > 0 있음), C-01-12 실측 수치(1,312프레임, 131.1 s, 간격 2~181 ms), C-02-1 "R/W/N"(실기기 R/W만).
- 미확정 판정을 명세에 표시: length 부호, CRC 바이트 순서(벡터 note), F-11 분할, C-02-4 전원 직후, C-02-2 전원 꺼짐 시점, C-04-1 필드 경계, C-06-1 W·N, 배터리 100 초과, 0x02 미상 stateRaw, 규칙 7, A-1 Android, A-3 비고유성.
- 결과 목록 밖 확정 문장 3개를 미확정으로: G-2a "앱은 쓰지 않는다", C-W-2 영점 재전송, A-3 표시 이름.
- 벡터: id·키·hex 변경 없음. `crc.note`만 보강.
- 코드 영향(ble-engineer): 2.3 규칙 7은 고정 길이 cmd에만 적용(0x0C·0x10 제외). 0x02 미상 stateRaw는 `TimerState`에 raw 그대로. 배터리 100 초과는 디코더가 아닌 UI에서 자름. 유량 0을 "무게 감소"의 신호로 쓰지 말 것.
