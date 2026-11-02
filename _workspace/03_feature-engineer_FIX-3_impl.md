# FIX-3 구현 요약
- StatusAnnouncer.kt: announce마다 노드를 "" -> 다음 프레임 새 문구로 바꿔 같은 문구 재낭독. Assertive가 오면 대기 중 Polite 폐기(drop). 노드는 1 dp, invisibleToUser/alpha 없음. 제로폭 문자 방식 제거.
- ConnectionUi.kt: displayNames(candidates) 순수 함수 추가. listName/spokenListName이 이를 사용(S2 행 글자·낭독).
- 테스트: StatusAnnouncerTest, DisplayNamesTest 신규. 스크린샷: connect_scanning_duplicate_name_*, device_sheet_battery_unknown_*.
- 차이: Assertive도 자체 300 ms 창에서 낸다(Polite 폐기는 즉시).
