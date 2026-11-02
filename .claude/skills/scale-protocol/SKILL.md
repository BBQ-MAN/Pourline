---
name: scale-protocol
description: "Timemore Dot(Black Mirror 계열) 저울 BLE 프로토콜 원문을 앱 구현용 명세(docs/spec/protocol.md)와 테스트 벡터(protocol-vectors.json)로 옮길 때 따르는 규칙과, 이미 저장된 캡처 파일로 CRC 변형·무게 단위 같은 미확정 항목을 판별하는 방법. 패킷 hex 해석, CRC 불일치 원인 분석, 프레임 구조 질문에 사용한다. 명세 갱신·재검증 작업 자체는 timemore-app-orchestrator 1단계로 시작하고, 사용자와 함께 캡처를 얻는 절차는 device-verification, 앱의 BLE 연결 코드는 android-ble를 쓴다."
---

# Scale Protocol — 원문을 구현 가능한 명세로 옮기기

명세는 코덱 구현과 테스트의 유일한 근거다. 명세가 틀리면 앱은 빌드·테스트를 모두 통과한 채 틀린 무게를 보여 준다. 그래서 "원문이 말한 것", "원문이 말하지 않아 추정한 것", "실기기로 확인한 것"을 명세 안에서 구분한다.

## 원문

- `docs/protocol/source/Timemore Black Mirror SCALE BLE Protocol.md` — TIMEMORE 공식 공개 문서 v1.0.3 (출처: github.com/TIMEMORE-COFFEE/open-scale-protocol). 원문은 고치지 않는다. 새 판이 나오면 같은 폴더에 받고 변경 이력을 비교한다.
- 실기기 캡처: `_workspace/04_device_*.log` (`device-verification` 스킬 형식)

## 원문을 읽을 때 반드시 짚을 모호점

공식 문서 v1.0.3을 읽으면 다음이 비어 있다. 명세에서 `미확정`으로 시작하고 실기기 캡처로 닫는다.

| 항목 | 원문 내용 | 비어 있는 것 | 확인 방법 |
|------|-----------|--------------|-----------|
| CRC | "CRC-16/IBM, poly 0x8005, init 0xFFFF". 범위는 "CRC 필드를 뺀 전체"(헤더 포함), 다중 바이트는 big-endian(§3) | 반사 여부, xorout. 이름("CRC-16/IBM")은 보통 init 0x0000(ARC)을 뜻해 init과 충돌한다. 범위·바이트 순서는 원문에 있지만 MODBUS 관례(CRC little-endian 전송)와 다를 수 있어 실기기로 대조한다 | 캡처 프레임에 `scripts/crc_probe.py` 실행 |
| 무게 Int32 | 부호 있는 4바이트 | 단위(0.1 g? 0.01 g?), oz 모드에서 raw 의미가 바뀌는지 | 알려진 무게를 올리고 캡처, 단위 g↔oz 전환 캡처 |
| 유량 Int16 | 0~999, 100 mg/s | oz 모드 배율("최소 단위 0.01"의 뜻), 음수 의미 | 붓는 중·컵 들어 올릴 때 캡처 |
| 과부하 | 플래그 1 = 양·음 한계 초과 | 과부하 동안 무게 필드 값(고정? 한계값?) | 한계 초과 하중 캡처(저울 손상 주의, 가능할 때만) |
| 타이머 uint16 | 0~3599 s | 해상도 1 s(앱 표시용 소수 초는 앱이 만든다), 3599 초과 동작 | 1시간 방치는 생략 가능, 미확정으로 둔다 |
| 0x01 전송 조건 | "active 동안 100 ms마다" | active의 정의, 무게 변화 없을 때·자동 절전 직전 전송 여부 | 빈 저울 2분 방치 캡처 |
| 배터리 | 칸 수 + 0~100 % | 칸 수 범위 | 읽기 응답 캡처 |
| Write 후 Notify | 0x02·0x06은 R/W/N | 앱이 쓰면 Write 응답 외에 Notify도 오는지 | 쓰기 직후 알림 캡처 |
| 광고 | Manufacturer Data에 방송 유형·기기 유형(`01`=DOT TES017) | 회사 ID, 바이트 위치, 서비스 UUID 광고 여부, 기기 이름 패턴 | nRF Connect의 원시 광고 데이터 |
| 수신 분할 | 언급 없음 | 알림 하나에 프레임이 잘리거나 여러 개 붙는지. 응답 payload 최대 길이(이름 16바이트 외 n은 미정) | MTU 23에서 16바이트 이름 응답 캡처 |
| 송신 분할 | 언급 없음 | 이름 쓰기(최대 24바이트)가 MTU 23의 Write without response 한도 20바이트를 넘을 때 저울이 나눠 받는지 | MTU 23에서 13~16바이트 이름 쓰기 |
| 쓰기 응답 | Write 응답 opcode `0x03`, `0x00` 실패 / `0x01` 성공 | 응답이 오지 않을 때의 시간 제한 | 실기기 측정 |
| 페어링 | "whitelist advertising", "Forget device clears pairing data" | Android 본딩(createBond)이 필요한지, 저울 자체 화이트리스트인지 | 본딩 없이 연결·명령 시도 |

새 판 원문이나 로그가 이 표의 칸을 채우면 명세에서 해당 항목을 `확정`으로 바꾸고 근거를 적는다.

## 작업 절차

1. 원문 전체를 읽고 명령표(§4)와 상세(§5)를 대조한다. 명령표의 지원 연산(N/R/W)과 상세 절의 프레임이 어긋나면 둘 다 적는다.
2. `docs/spec/protocol.md`를 아래 형식으로 쓴다.
3. `docs/spec/protocol-vectors.json`을 쓴다. 원문에 바이트 예시가 없으므로 처음에는 모든 벡터가 `derived`다. CRC가 미확정이면 CRC를 포함하는 벡터는 `status: "unconfirmed"`로 둔다.
4. 명세의 모든 항목에 표 ID와 근거 위치를 남긴다. 워크플로의 별도 추출자가 이 문서에서 주장을 사실 하나씩 뽑아 교차 검증하므로, 표에 없는 내용은 검증되지 않고 미확정으로 내려간다.
5. 실기기 캡처가 들어오면 `crc_probe.py`로 CRC 변형을 판별하고, 캡처 프레임을 `origin: "device"` 벡터로 추가한다. 실기기 벡터는 명세보다 우선하는 근거다.

## 명세 형식 (`docs/spec/protocol.md`)

```markdown
# Timemore Dot 프로토콜 명세
원문: <파일명> v<버전> · 마지막 갱신: YYYY-MM-DD

## 0. 확정 상태 요약
| ID | 항목 | 상태(확정/미확정) | 근거 | 실기기 확인 방법 |

## 1. GATT
서비스·특성 UUID는 128비트 전체 형식으로 적는다(0xFFF0 → 0000fff0-0000-1000-8000-00805f9b34fb).
각 특성의 속성(Notify / Write without response)과 앱이 쓰는 용도.

## 2. 프레임
오프셋 표: | 오프셋 | 길이 | 필드 | 형식(uint8/int16 BE…) | 비고 |
길이 필드 = payload 바이트 수. 전체 길이 = 6 + len + 2.
CRC: 변형 이름, 파라미터(poly/init/refin/refout/xorout), 계산 범위, CRC 바이트 순서, 상태.
수신 재조립 규칙: 헤더 탐색, 길이 확인, CRC 실패 시 재동기화.

## 3. 명령
명령마다: 방향(App→scale / scale→App), opcode, cmd, payload 오프셋 표, 값의 단위와 범위, 앱 변환식, 응답과 시간 제한.
앱 변환식은 (raw, 현재 단위)를 입력으로 받는다. 0x01의 raw 의미가 g/oz 모드에서 다를 수 있기 때문이다. 단위(0x06)를 아직 모르는 동안 받은 0x01은 버리거나 보류하는 규칙도 적는다.
0x0C 이름 쓰기는 UTF-8 바이트 기준 1~16바이트(원문 §4) 검증과, 넘칠 때 글자 경계에서 자르는 규칙을 적는다.

## 4. 광고와 기기 식별
스캔에서 Dot을 고르는 규칙과 그 상태.

## 5. 변경 이력
| 날짜 | 절 | 변경 | 근거 |
```

## 벡터 형식 (`docs/spec/protocol-vectors.json`)

코덱 테스트가 이 파일을 직접 읽는다. 키 이름을 바꾸면 테스트가 깨지므로 바꿀 때는 보고서에 적어 ble-engineer가 알게 한다.

```json
{
  "source": "Timemore Black Mirror SCALE BLE Protocol.md v1.0.3",
  "crc": { "variant": "MODBUS", "poly": "0x8005", "init": "0xFFFF", "refin": true, "refout": true,
           "xorout": "0x0000", "byteOrder": "big", "range": "header..payload", "status": "unconfirmed" },
  "vectors": [
    {
      "id": "rx-weight-positive-001",
      "direction": "rx",
      "hex": "A55A01010009...",
      "origin": "derived",
      "status": "unconfirmed",
      "expect": { "type": "WeightReport", "weightRaw": 1234, "flowRaw": 12, "timerSec": 5, "overload": false }
    },
    { "id": "rx-crc-bad-001", "direction": "rx", "hex": "...", "origin": "derived", "status": "confirmed",
      "expect": { "error": "CrcMismatch" } },
    { "id": "tx-tare-001", "direction": "tx", "hex": "A55A030D0000....", "origin": "derived", "status": "unconfirmed",
      "command": { "type": "Tare" } }
  ]
}
```

- `id`는 `{rx|tx}-{명령}-{사례}-{번호}`. 한 번 정한 `id`는 바꾸지 않는다.
- `expect`에는 raw 값을 적는다. g 변환은 명세의 변환식으로 별도 테스트한다. 변환 배율이 미확정이어도 raw 디코딩은 확정할 수 있기 때문이다.
- 벡터가 덮어야 할 사례: 명령마다 정상 1건 이상, 음수 무게, 과부하 플래그, 최대값(타이머 3599, 유량 999), oz 모드 0x01 프레임, CRC 불일치, 길이 필드와 실제 길이 불일치, 헤더 앞 쓰레기 바이트, 한 알림에 프레임 두 개, 프레임이 알림 두 개로 쪼개진 경우(`hex`를 배열로).
- CRC가 미확정인 동안에는 CRC를 포함하는 모든 벡터(CRC 불일치 벡터 포함)를 `unconfirmed`로 둔다. 어떤 바이트가 "틀린 CRC"인지도 변형이 정해져야 알 수 있기 때문이다.
- `derived` 벡터의 CRC는 `scripts/crc_probe.py --build`로 만든다. 손으로 계산하지 않는다. CRC 미확정이면 후보 변형 하나를 고르고 그 이름을 `crc.variant`에 적는다.
- **테스트 처리 규칙**: `confirmed` 벡터가 실패하면 빌드 실패다. `unconfirmed` 벡터는 건너뛰지 않고 별도 표시(예: 테스트 이름 접두어 `[unconfirmed]`)로 실행하며, 실패해도 빌드는 막지 않되 결과를 보고서에 남긴다. 확정되면 같은 `id`로 `status`만 바꾼다.

## 스크립트

`scripts/crc_probe.py` — poly 0x8005에서 init {0x0000, 0xFFFF} × 반사 여부 × xorout {0x0000, 0xFFFF} 8조합(카탈로그 이름이 있으면 ARC, MODBUS, USB, MAXIM, CMS, UMTS로 표시) × CRC 바이트 순서 × 계산 범위(헤더 포함/제외)를 캡처에 대입한다.

- `--file`은 `RX` 줄만 쓴다. `TX` 줄은 가정한 변형으로 만든 프레임이라 판별 근거가 되면 순환 논리가 된다.
- 알림 조각은 이어 붙인 뒤 헤더·길이 필드로 프레임을 잘라 낸다(분할·결합 알림 대응). nRF Connect의 `A5-5A-…` 표기도 받는다.
- 종료 코드: 0 = 모든 프레임에 맞는 조합이 정확히 하나(그 조합으로 확정), 1 = 없음, 3 = 둘 이상(다른 명령·다른 값의 캡처를 더 모은다).
- 출력된 `--variant … --order … --start …`를 `--build`에 그대로 붙인다. `--build`는 변형과 순서를 지정하지 않으면 거부한다.

```bash
python3 -I .claude/skills/scale-protocol/scripts/crc_probe.py --file _workspace/04_device_probeA.log
python3 -I .claude/skills/scale-protocol/scripts/crc_probe.py --build 03 0D "" --variant <판별 결과> --order <판별 결과> --start <판별 결과>   # 영점 명령
python3 -I .claude/skills/scale-protocol/scripts/crc_probe.py --selftest
```

## 실기기 로그와 명세가 다를 때

1. 차이를 `_workspace/01_protocol-analyst_notes.md`에 프레임 hex와 함께 적는다.
2. 원문이 침묵하는 부분이면 로그를 근거로 명세를 고치고 `확정`으로 바꾼다.
3. 원문과 로그가 정면으로 충돌하면 로그를 따르되 원문 내용과 충돌 사실을 변경 이력에 남긴다. 펌웨어 판(Device Information의 Firmware Revision)도 함께 적는다. 펌웨어마다 동작이 다를 수 있다.
