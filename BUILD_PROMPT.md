# ReplyHub — Codex 빌드 프롬프트

이 문서는 Codex(또는 다른 코딩 에이전트)에게 그대로 붙여넣어 실제 개발을 시작하기 위한 상세 지시문입니다. 배경 맥락(왜 이렇게 설계했는지)은 [SPEC.md](./SPEC.md)에 있고, 이 문서는 "무엇을, 어떤 순서로, 어떻게 구현할지"에 집중합니다.

---

## Codex에게 전달할 프롬프트 (그대로 복사해서 사용)

```
너는 Android 네이티브 앱 "ReplyHub"를 만드는 개발자야. 아래 스펙을 정확히 따라서 구현해줘.
언어/스택: Kotlin, Jetpack Compose, 최소 API 26(Android 8.0) 이상 타겟.

## 제품 개요
ReplyHub는 카카오톡, 위챗 등 여러 메신저의 알림을 하나의 피드로 모아 보여주고,
GPT-5.6을 이용해 번역·우선순위 분류·답장 초안 생성(웹 검색 또는 과거 대화 검색 활용)을
지원하며, 음성 입력으로 답장을 부를 수 있고, 가능하면 알림에서 바로 전송(RemoteInput),
안 되면 클립보드 복사 + 딥링크로 폴백하는 앱이야.

## 지금 당장 할 일: Day 1 스파이크 (가장 먼저, 이것부터)
본 개발에 들어가기 전에 아래를 반드시 검증해야 해. 이게 안 되면 전체 설계가 바뀌어야 하니까
최우선으로 만들어줘.

1. NotificationListenerService를 구현해서 기기에 알림이 올 때마다 다음을 로그로 출력:
   - 패키지명(어느 앱에서 왔는지)
   - 알림 제목(title), 본문(text), 하위 텍스트(subText) 전체
   - 해당 알림에 딸린 Notification.Action 목록과, 각 액션에 RemoteInput이 포함되어
     있는지 여부 (Notification.Action.getRemoteInputs() 로 확인)
2. 아래 패키지명을 대상으로 실제 기기에서 테스트할 수 있게 앱을 준비해줘:
   - 카카오톡: com.kakao.talk
   - 위챗: com.tencent.mm
   - (참고용, P1) 라인: jp.naver.line.android, 텔레그램: org.telegram.messenger
3. 알림 리스너 권한 요청 플로우 (설정 화면으로 이동시키는 Intent:
   Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) 구현
4. 결과를 화면에서도 볼 수 있게 간단한 로그 뷰(RecyclerView나 LazyColumn)를 만들어서,
   캡처된 알림 리스트 + "이 알림은 RemoteInput 지원함/안 함" 라벨을 표시

이 스파이크 결과에 따라 카카오톡/위챗이 RemoteInput을 지원하지 않는 것으로 확인되면,
해당 앱은 "클립보드 복사 + 딥링크" 경로로 설계하면 돼 (아래 P0 요구사항에 이미 반영되어 있음).

## 전체 아키텍처
[메신저 알림] → NotificationListenerService (OS 레벨, 앱별 통합 불필요)
    → 로컬 DB 저장 (Room/SQLite) + 검색 인덱스
    → GPT-5.6 API 호출: 언어 감지 → 번역 → 우선순위 분류
    → 통합 피드 UI (Jetpack Compose)
    → [사용자가 답장 요청, 텍스트 또는 음성 입력]
    → GPT-5.6 API 호출: 답장 초안 생성
         - 사실 확인 필요 판단 시 → 웹 검색 API 호출 → 결과 반영
         - 과거 맥락 필요 판단 시 → 로컬 DB에서 관련 메시지 검색 → 결과 반영
    → 번역 + 톤 조정 (상대방 언어로)
    → 발송:
         - RemoteInput 지원 앱 → Notification.Action.actionIntent 에 RemoteInput 값 채워서 전송
         - 미지원 앱 → ClipboardManager로 복사 + 해당 앱 실행 Intent(딥링크)

## P0 요구사항 (반드시 구현)
1. NotificationListenerService로 카카오톡, 위챗 최소 2개 앱의 알림 텍스트 캡처
2. 캡처된 메시지 언어 감지(GPT-5.6 API 호출 또는 경량 언어감지 라이브러리) 후 번역하여
   통합 피드(Compose LazyColumn)에 표시. 원문/번역문 둘 다 보이게.
3. 피드에서 메시지 선택 → "답장" 버튼 → GPT-5.6에게 아래 정보를 함께 전달해서 초안 요청:
   - 원본 메시지(원문 + 번역문)
   - 최근 대화 기록 일부(같은 발신자와의 최근 N개 메시지)
   프롬프트에서 GPT-5.6이 스스로 판단하게 할 것: 웹 검색이 필요한지, 과거 대화 검색이
   필요한지, 둘 다 필요 없는지. 필요하다고 판단하면 해당 도구(웹 검색 API 호출 함수 /
   로컬 DB 검색 함수)를 호출하도록 function calling으로 구현.
4. 생성된 답장 초안을 상대방 언어로 번역
5. 발송 로직:
   - RemoteInput 지원 앱: 알림의 Action에서 RemoteInput을 받아 텍스트 채워서
     PendingIntent 전송
   - 미지원 앱: ClipboardManager.setPrimaryClip()으로 번역문 복사 후,
     packageManager.getLaunchIntentForPackage(패키지명)으로 해당 앱 실행
6. 음성 입력: SpeechRecognizer(RecognitionListener)로 한국어 음성 인식 →
   인식된 텍스트를 답장 초안 파이프라인에 그대로 태움
7. 위 1~6이 실제 Android 기기(에뮬레이터 말고 가능하면 실기기)에서 전체 플로우로
   동작해야 함 — 이게 데모 시나리오의 핵심

## 데이터 모델 (Room Entity 예시)
CapturedMessage(
  id: Long (PK, autoincrement),
  packageName: String,       // com.kakao.talk 등
  sender: String,
  originalText: String,
  detectedLanguage: String,
  translatedText: String,
  timestamp: Long,
  priority: String,          // "URGENT" | "LATER" (P1)
  hasRemoteInputAction: Boolean,
  rawNotificationKey: String // 답장 전송 시 원본 알림 참조용
)

## GPT-5.6 API 연동 시 주의사항
- 번역/분류/초안생성 3개 호출을 각각의 명확한 system prompt로 분리 (하나의 거대 프롬프트로 뭉치지 말 것)
- 답장 초안 생성 호출에는 반드시 두 개의 tool을 정의해서 function calling으로 노출:
  1. web_search(query: string) — 외부 검색 API 호출 (예: Bing Search API, 결과 상위 3개 요약해서 반환)
  2. search_conversation_history(sender: string, query: string) — 로컬 Room DB에서 해당
     발신자와의 과거 메시지 중 관련된 것 검색
  GPT가 필요 없다고 판단하면 두 tool 다 호출 안 하고 바로 답장 초안만 반환하면 됨.
- API 키는 절대 하드코딩하지 말고 local.properties 또는 환경변수로 관리해줘.

## 발송 폴백 설계 (그래스풀 디그레이데이션)
- RemoteInput 지원 여부는 앱마다 다르고 Day 1 스파이크로 확인된 결과에 따라 결정.
- 지원 안 되는 앱은 "번역 완료, 클립보드에 복사됨 — [앱 이름] 열기" 버튼을 보여주고,
  탭하면 해당 앱이 열리는 것으로 충분함 (완전 자동 전송을 억지로 구현하려 하지 말 것).

## 명시적으로 하지 말아야 할 것 (Non-goals — SPEC.md 참고)
- iOS 지원 시도하지 말 것
- 실제 크롬 등 브라우저 앱을 AccessibilityService로 외부 조작하지 말 것 (웹 검색은 API 호출로 충분)
- 메신저 답장 외에 다른 종류의 폰 액션(캘린더, 주문, 송금 등) 자동화 시도하지 말 것
- 그룹채팅 답장 어시스턴트 기능 구현하지 말 것 (알림 캡처는 되지만 답장 지원은 1:1만)

## 완료 기준 (Acceptance Criteria)
- 시나리오 1: 위챗 중국어 메시지 → 피드에 번역되어 표시 → 답장 요청 시 GPT가 웹 검색 필요
  여부 판단해서 검색 수행 → 중국어 답장 초안 생성됨
- 시나리오 2: 카카오톡에서 과거 언급한 정보를 다시 물어봄 → GPT가 로컬 히스토리 검색해서
  답장에 반영
- 시나리오 3: 마이크 버튼 → 한국어 음성 입력 → 텍스트 변환 → 번역 → 발송(RemoteInput 또는
  클립보드+딥링크)까지 한 번에 동작

이 순서(Day 1 스파이크 → P0 파이프라인 → 음성 통합)로 단계별로 커밋을 나눠서 진행해줘.
각 단계마다 실행 가능한 상태를 유지해줘 (중간에 컴파일 안 되는 상태로 두지 말 것).
```

---

## 참고: 이 프롬프트를 쓸 때 체크리스트

- [ ] Codex 세션 시작 전에 GPT-5.6 / 웹 검색 API 키를 발급받아 둘 것 (프롬프트에서 하드코딩 금지라고 명시했으니 실제 값은 별도로 전달)
- [ ] Day 1 스파이크 결과(카카오톡/위챗 RemoteInput 지원 여부)가 나오면 그 결과를 다음 Codex 세션 프롬프트에 추가로 알려줄 것 — 설계가 바뀔 수 있음
- [ ] 해커톤 제출 요구사항에 따라, 핵심 기능(P0) 대부분이 구현된 시점의 Codex 세션 ID를 기록해서 `/feedback`으로 제출 양식에 넣을 것 (잊지 말 것 — [SPEC.md](./SPEC.md) 11번 항목 참고)
