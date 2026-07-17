# ReplyHub 수정 작업 지시서 (코드 리뷰 결과 반영)

멀티에이전트 코드 리뷰에서 검증된 issue들이다. 우선순위 순서대로 작업할 것.
**P0은 제출 전 필수, P1은 안전하고 작은 수정이라 권장, P2는 제출 후로 미룰 것(마감 직전 리팩터링 금지).**

작업 원칙:
- 각 수정 후 `./gradlew testDebugUnitTest` 통과 확인 (현재 58개 전부 통과 상태를 유지할 것)
- 기존 코드 스타일(한국어 에러 메시지, 주석 밀도)을 그대로 따를 것
- 이 문서에 없는 리팩터링을 임의로 확장하지 말 것

---

## P0-1. testConnection이 유효한 API 키를 "실패"로 판정하는 버그 [최우선]

**파일**: `app/src/main/java/com/replyhub/app/ai/OpenAiResponsesClient.kt` (112~120행)

**문제**:
```kotlin
suspend fun testConnection(apiKey: String): Result<Unit> = runCatching {
    val response = createResponse(
        apiKey = apiKey,
        instructions = "Reply with exactly OK.",
        input = "Connection test",
        maxOutputTokens = 16,        // <- 문제
    )
    check(response.outputText.isNotBlank()) { "OpenAI API 응답이 비어 있습니다." }
}
```
`createResponse`의 기본값이 `model = "gpt-5.6"`(추론 모델), `reasoningEffort = "low"`인데
`max_output_tokens = 16`이면 추론 토큰만으로 예산이 소진되어 응답이 `incomplete` 상태로
빈 output_text가 돌아온다. 그러면 `parseResponse` 169행의
`check(outputText.isNotBlank())`가 예외를 던져 **정상 키인데 Result.failure가 된다.**
설정 화면의 "연결하고 저장" 버튼이 이 함수를 쓰므로, 심사자가 유효한 키를 넣어도
연결 실패로 보이는 치명적 첫인상 버그다.

**수정 방향** (두 가지 모두 적용):
1. `testConnection`의 `maxOutputTokens`를 `256`으로 상향하고, `reasoningEffort = "minimal"`을
   명시적으로 전달한다 (`"minimal"`이 현재 Responses API에서 지원되는 최소 effort 값인지
   OpenAI 문서로 확인하고, 지원 안 되면 `"low"` 유지 + 토큰만 상향).
2. testConnection의 목적은 "키가 유효한가"이지 "모델이 텍스트를 냈는가"가 아니다.
   HTTP 2xx를 받았다면 output_text가 비어 있어도 성공으로 처리하도록 바꿔라.
   구체적으로: `parseResponse`의 빈 텍스트 check 예외가 testConnection 경로에서는
   실패로 이어지지 않게 하거나 (예: createResponse에 `allowEmptyOutput: Boolean = false`
   파라미터 추가), testConnection 전용의 가벼운 검증 경로를 만들어라.
   단, **기존 답장 생성 경로의 빈 응답 검증은 그대로 유지**해야 한다.

**완료 기준**:
- 유효한 키 + 정상 네트워크 → 연결 테스트 성공
- 잘못된 키(401) → 기존처럼 서버 에러 메시지로 실패
- 기존 유닛 테스트(OpenAiResponseParsingTest 포함) 전부 통과, testConnection 동작을 커버하는 유닛 테스트 1개 이상 추가

---

## P0-2. incomplete(잘린) 응답 미검출 → 엉뚱한 에러 메시지

**파일**: `app/src/main/java/com/replyhub/app/ai/OpenAiResponsesClient.kt` (93~106행, parseResponse)

**문제**: 응답 JSON의 `status` 필드(`"incomplete"`)와 `incomplete_details.reason`
(`"max_output_tokens"`)을 전혀 확인하지 않는다. 답장 생성 경로는 `maxOutputTokens = 700`인데
추론 토큰 + 3개 초안 JSON이 700을 넘으면 잘린 JSON이 돌아오고, 파싱 실패가
"GPT 요청 실패"라는 오해의 소지가 있는 메시지로 로컬 fallback에 묻힌다.

**수정 방향**:
- `parseResponse`(또는 createResponse의 2xx 처리부)에서 `json.optString("status")`가
  `"incomplete"`이면 `incomplete_details.reason`을 읽어서:
  - reason이 `max_output_tokens`인 경우: 호출자가 구분할 수 있는 전용 예외
    (예: `OpenAiIncompleteException`)를 던지거나, 1회에 한해 `max_output_tokens`를
    2배로 올려 재시도한다 (재시도 쪽 권장, 단 1회 제한).
  - 사용자에게 보이는 에러 메시지는 "응답이 길이 제한으로 잘렸습니다" 류의
    정확한 한국어 문구로.
- 이 처리는 답장 생성/번역/웹검색 모든 경로에 공통 적용된다.

**완료 기준**: status="incomplete" JSON 픽스처로 유닛 테스트 추가 (재시도 or 전용 예외 확인).

---

## P1-1. LICENSE 파일 추가

**문제**: 공개 저장소인데 루트에 LICENSE 파일이 없다. 기본값이 all-rights-reserved라
심사자가 빌드/실행하는 것도 엄밀히는 법적으로 애매하다. 해커톤 규칙도 "관련 라이선스 포함"을 요구한다.

**수정**: 저장소 루트에 MIT License 파일 추가 (copyright holder: Seunghyuk Park, year: 2026).
README.md 하단에 라이선스 섹션 한 줄 추가.

---

## P1-2. 다크모드: 정의만 있고 적용 경로가 없음

**파일**: `app/src/main/java/com/replyhub/app/ui/ReplyHubTheme.kt` (32~40행), MainActivity의 ReplyHubTheme 호출부

**문제**: `ReplyHubTheme(darkTheme: Boolean = false)`가 하드코딩 기본값이고
`isSystemInDarkTheme()` 호출처가 코드 전체에 없다. `DarkColors`는 primary/secondary/tertiary만
정의되어 background/surface가 다크 기본값과 어긋난다. 다크모드 폰을 쓰는 심사자는
의도되지 않은 화면을 본다.

**수정 방향** (둘 중 하나 선택, A 권장):
- **A (지원)**: 호출부에서 `ReplyHubTheme(darkTheme = isSystemInDarkTheme())`로 연결하고,
  `DarkColors`에 background/surface/surfaceVariant/각종 container 색을 라이트 팔레트와
  일관된 톤으로 채운다. MainActivity 2562행 부근의 하드코딩 색상 몇 곳도
  `MaterialTheme.colorScheme` 토큰으로 바꿔서 다크에서 깨지지 않게 한다.
  **적용 후 라이트/다크 양쪽에서 인박스·작성기·설정 화면 스크린샷을 찍어 확인할 것.**
- **B (제거)**: 다크모드를 지원하지 않기로 한다면 `darkTheme` 파라미터와 `DarkColors`를
  삭제해서 "지원하는 척"하는 죽은 코드를 없앤다.

---

## P1-3. 답장 전송 경로가 메인 스레드에서 binder/IPC 작업 수행

**파일**: `app/src/main/java/com/replyhub/app/notifications/ReplyDispatcher.kt` (dispatch, 22행 부근)
관련: `ReplyHubViewModel.kt` 248행 (viewModelScope에서 호출), `ReplyHubNotificationListener.kt` 204~254행

**문제**: `dispatch()`는 suspend지만 dispatcher 전환이 없어 Main에서 실행된다.
첫 줄의 `refreshReplyTargets()`가 `getActiveNotifications()` binder IPC + 알림별
reply-action 스캔을 수행하고, 이어서 `PendingIntent.send()`까지 전부 메인 스레드다.
활성 알림이 많을 때 전송 버튼 탭에서 잰크/ANR 위험.

**수정**: `dispatch()` 본문을 `withContext(Dispatchers.Default)`로 감싼다
(PendingIntent.send와 clipboard 접근이 백그라운드 스레드에서 안전한지 확인하고,
UI 콜백/상태 갱신이 있다면 그 부분만 Main으로 되돌린다).
동작 변화가 없어야 하며 기존 ReplyTargetMatcherTest 전부 통과.

---

## P1-4. 컴포지션 중 메인 스레드 무거운 I/O (데모 잰크의 유력 원인)

**파일**: `app/src/main/java/com/replyhub/app/MainActivity.kt`
- 2491행 부근: 앱 아이콘을 컴포지션 중 동기 디코드
- 1399행 부근: `BitmapFactory.decodeFile`을 크기 제한 없이 동기 호출 (첨부 이미지)
- 193/202행 부근: `loadLaunchableApps`가 동기 실행 + ON_RESUME마다 재실행

**수정 방향**:
- 아이콘/비트맵 로드를 `produceState` 또는 `LaunchedEffect + Dispatchers.IO`로 옮기고,
  로드 전에는 placeholder를 그린다.
- 아이콘은 패키지명 키의 인메모리 캐시(예: remember 상위의 LruCache) 사용.
- `BitmapFactory.Options.inSampleSize`로 표시 크기에 맞춰 샘플링 디코드.
- `loadLaunchableApps`는 IO 디스패처로 옮기고 결과를 캐시해서 ON_RESUME마다
  전체 재조회하지 않게 한다.

**완료 기준**: 인박스 스크롤과 이미지 첨부 대화 열기에서 프레임 드랍 체감 감소
(변경 전후로 수동 확인), 기존 테스트 전부 통과.

---

## P1-5. 30일 자동 삭제가 앱을 열 때만 실행됨 (PRIVACY.md 문구와 불일치)

**파일**: `app/src/main/java/com/replyhub/app/ui/ReplyHubViewModel.kt` (379행 부근, pruning 호출처가 init과 setRetentionDays뿐)
관련 문서: `PRIVACY.md` 13행 부근, `README.md` 41행 부근

**문제**: 백그라운드 리스너는 계속 메시지를 저장하는데 사용자가 앱 UI를 안 열면
보존기간이 지난 메시지가 삭제되지 않는다. "30일 후 자동 삭제" 문구와 실동작이 다르다.

**수정 방향** (둘 중 하나, A 권장 — 더 작고 안전):
- **A (동작 보강)**: `ReplyHubNotificationListener`의 `onListenerConnected`(또는 캡처 경로에서
  하루 1회 스로틀)에서 pruning을 실행해 앱을 안 열어도 삭제되게 한다.
- **B (문구 완화)**: PRIVACY.md/README의 해당 문구를 "다음 앱 실행 시 보존기간이 지난
  메시지를 삭제합니다"로 정정한다.

---

## P1-6. 사소한 정합성 수정 (일괄 처리)

1. **`isSafeWebUrl()`이 http://를 허용** — `OpenAiResponsesClient.kt` 182행.
   `usesCleartextTraffic=false`라 http 인용 링크는 어차피 안 열린다. `https://`만 허용으로 변경.
2. **429/5xx 재시도 없음** — `OpenAiResponsesClient.kt` 100행 부근. 429/500/502/503에 한해
   지수 백오프로 1~2회 재시도 추가 (readTimeout 45s를 고려해 총 소요가 과하지 않게).
3. **`reasoning.effort = "none"` 값 검증** — `MessageProcessor.kt` 115행 부근에서 enrichment가
   `"none"`을 쓰는데, 이 값이 Responses API에서 유효한지 실제 키로 확인해라.
   유효하지 않으면(400 등) 번역 enrichment가 조용히 fallback으로 빠진다. 무효라면 `"minimal"`로 교체.
4. **SPEC.md 69행**: "웹 검색 API (예: Bing/Google Search API)" 문구가 실제 구현
   (OpenAI web_search tool)과 다름. OpenAI 내장 web_search로 정정.
5. **README.md 11행**: "logs ... RemoteInput availability"라고 썼는데 실제 로그는 raw action
   count다. 로그 필드에 RemoteInput 보유 여부(boolean)를 추가하거나 문구를 정정.

---

## P2. 제출 후로 미룰 것 (마감 전 착수 금지)

- MainActivity.kt(2633줄) 화면별 파일 분리
- ViewModel의 Android 싱글톤 하드와이어링 → DI/생성자 주입 (테스트 용이성)
- UI가 notifications 레이어 싱글톤(`ReplyHubNotificationListener` companion)에 직접 접근하는
  구조를 ViewModel 경유로 정리 (MainActivity.kt 183행)
- 인박스 필터/정렬의 remember/derivedStateOf 최적화 (MainActivity.kt 365행)
- 동일 알림 키 재게시 시 두 단계 쓰기의 좁은 race 정리 (ReplyHubNotificationListener.kt 153행,
  enrichment 쓰기를 `updateEnrichment`의 originalText 가드 방식으로 통일)
- VoiceInputController에 `SpeechRecognizer.isRecognitionAvailable()` 가드 + cancel() API 추가

---

## 참고: 코드로 해결 안 되는 남은 제출 작업 (사람이 할 일)

이 항목들은 Codex 작업 범위가 아니다. 체크리스트로만 둔다.

- [ ] **3분 데모 영상 촬영·업로드** → `SUBMISSION.md:104`의 `ADD_URL` 치환 (최우선 제출물.
      한국어 UI이므로 영어 캡션 오버레이 권장, 웹검색 답장·맥락 답장·음성 입력 3개 시나리오 포함)
- [ ] `JUDGING_CHECKLIST.md` 16/56/57/59행 미완 항목 체크
- [ ] Devpost 프로젝트 갤러리에 스크린샷 3장 업로드 (`artifacts/screenshots/`)
- [ ] 제출 폼의 Codex ID 항목이 요구하는 것이 `/feedback` 세션 ID인지 확인하고,
      `SUBMISSION.md:107`의 "task reference"가 맞는 값인지 검증
