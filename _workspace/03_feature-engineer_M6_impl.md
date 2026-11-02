# M6 구현 요약 — Room 저장소·CSV

- 소스: app/src/main/java/com/timemoredot/app/data/{Entities,Daos,AppDatabase,DefaultRecipes,Repositories,CsvExport}.kt
- 가짜: app/src/test/java/com/timemoredot/app/fake/FakeRepositories.kt (FakeRecipeRepository, FakeBrewRepository, failNext(메서드명, 예외), 호출 기록)
- 테스트: data/{DefaultRecipesTest,DefaultRecipeSeedTest,RoomBrewRepositoryTest,RoomRecipeRepositoryTest,CsvExportTest}
- 스키마: app/schemas/com.timemoredot.app.data.AppDatabase/1.json
- 빌드: app에 ksp·room 플러그인, room/serialization/robolectric/turbine 추가, robolectric.properties sdk=35
- verify.sh 전체(test lintDebug assembleDebug) PASS, 종료 0, 로그 _workspace/build/verify_20261029_113218.log

## 결정·명세와 다른 점
- writeSamples도 BOM으로 시작한다(8.4 공통 형식).
- saveDraft는 draft.id가 null이면 항상 새 행, id가 있으면 같은 행 갱신.
- 소스에 BOM 리터럴 대신 0xFEFF.toChar() 사용(lint ByteOrderMark).
- SessionStatus에 db 문자열 필드를 둠. 알 수 없는 status 문자열은 Interrupted로 읽는다.
