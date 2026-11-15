# 털 한 올 — Backend

2026 간지톤 15팀 '털 한 올' 백엔드 서버입니다.

## 기술 스택

- Java 21, Spring Boot 4.1, Gradle
- Spring Web MVC, Spring Data JPA, Spring Security, Bean Validation
- MySQL 8.4 (로컬은 Docker), 테스트는 H2(MySQL 모드)
- springdoc-openapi (Swagger UI)
- LLM: Upstage Solar Pro 4 (`solar-pro4`, HTTP 직접 호출) 또는 Claude API (`anthropic-java` SDK, `claude-opus-5-5`). `app.llm.provider`로 선택

## 로컬 실행

```bash
# 1. MySQL 실행 (기본 포트 3307)
docker compose up -d

# 2. 서버 실행 (기본 프로필: local)
./gradlew bootRun
```

- API 문서: http://localhost:8080/swagger-ui.html
- 상태 확인: http://localhost:8080/health

접속 정보를 바꾸려면 `.env.example`을 `.env`로 복사해서 수정하세요. `.env`는 커밋하지 않습니다.

## 테스트

```bash
./gradlew test
```

테스트는 `test` 프로필로 H2 인메모리 DB에서 실행되므로 Docker가 없어도 됩니다.

## 프로필

| 프로필 | 용도 | DB |
| --- | --- | --- |
| `local` (기본) | 로컬 개발 | Docker MySQL, `ddl-auto: update` |
| `test` | 자동 테스트 | H2 (MySQL 모드), `create-drop` |
| `prod` | 운영 | 환경변수 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `APP_CRYPTO_KEY`, `APP_JWT_SECRET`, `UPSTAGE_API_KEY` 또는 `ANTHROPIC_API_KEY`, `ddl-auto: validate` |

## 패키지 구조

```
com.ganzithon.teolhanol
├── global          # 공통: 응답 형식, 예외 처리, 설정
│   ├── config
│   ├── exception
│   └── response
└── domain          # 기능별 도메인 (user, diary, care, sheep, farm, worry, wool, safety, report, notification)
```

## 민감정보 암호화

케어 대화와 마무리 카드는 AES-256-GCM으로 암호화해서 저장합니다 (`global/crypto`).
엔티티 필드에 `@Convert(converter = EncryptedStringConverter.class)`를 붙이면 적용됩니다.

- 로컬·테스트: 설정 파일의 개발용 키를 사용
- 운영: `APP_CRYPTO_KEY` 환경변수 필수 (`openssl rand -base64 32`로 생성). 키를 잃어버리면 기존 데이터를 복호화할 수 없으니 안전하게 보관하세요.
- 대화 본문은 로그에도 남기지 않습니다.

## 인증 (JWT)

- `POST /auth/login`, `POST /auth/signup` 응답의 `accessToken`을 `Authorization: Bearer {토큰}` 헤더로 보냅니다. 공개 경로(`SecurityConfig.PUBLIC_PATHS`) 외에는 토큰이 없으면 401입니다.
- 현재 사용자 id: 컨트롤러 인자에 `@AuthenticationPrincipal AuthUser user` → `user.id()`.
- 로그아웃은 서버에 상태가 없으므로 클라이언트가 토큰을 지웁니다. 연속 5번 실패하면 5분 잠깁니다(423 `ACCOUNT_LOCKED`).
- 테스트에서 로그인 사용자가 필요하면 `@Import(TestUsers.class)` 후 `testUsers.signIn(email, nickname)` → `login.bearer()` (`src/test/.../support/TestUsers.java`).
- 로컬은 개발용 키를 쓰고, 운영은 `APP_JWT_SECRET` 필수 (`openssl rand -base64 48`).

## 위험도 분류 (SAF-01)

케어 대화 메시지와 고민 본문은 저장·응답 전에 `RiskClassifier`를 거칩니다.

```java
RiskResult r = riskClassifier.classify(text, CrisisSource.MESSAGE /* 또는 WORRY */, user);
r.level();      // SAFE / CAUTION / CRISIS (키워드와 LLM 중 더 높은 등급)
r.isCrisis();   // true면 상담 연결로 전환 (SAF-02)
```

- 예외를 던지지 않습니다. LLM이 실패하거나 5초를 넘기면 키워드 결과를 쓰되 최소 `CAUTION`입니다 (`r.llmFailed()`로 구분).
- 결과는 `crisis_logs`에 기록하고(본문 제외), 위기면 `users.crisis_banner_until`을 24시간 뒤로 켭니다.
- 프롬프트는 `prompts/risk-classification.txt`. 테스트에서는 `@MockitoBean LlmClient`로 판정을 정해 둘 수 있습니다.

## 케어 대화 SSE (CARE-02)

`POST /diaries/{id}/care/messages` `{ "content": "..." }` → `text/event-stream`. 프론트는 `EventSource`가 아니라 헤더를 보낼 수 있는 `fetch` 스트림으로 읽습니다.

| 이벤트 | 데이터 | 설명 |
| --- | --- | --- |
| `progress` | `{userMessageCount, ready, maxUserMessages, limitReached}` | 항상 처음. 3회부터 `ready=true`, 10회에 닿으면 `limitReached=true`(AI가 마무리를 제안) |
| `delta` | `{text}` | AI 응답 조각. 안전 가드(SAF-03)가 문장 단위로 먼저 검사하므로 문장이 끝날 때마다 한 덩어리씩 도착 |
| `crisis` | `{message, actions:["call","continue"]}` | 위기로 판정되면 AI 응답 대신 전환 메시지. 대화는 계속 보낼 수 있고, 이어서 말하면 AI가 "말해 줘서 고마워요…"로 시작 |
| `notice` | `{message}` | 응답이 늦어 다시 시도하는 중("잠시 숨을 고르고 있어요"). 최대 3번 시도 |
| `reset` | `{}` | 지금까지 받은 `delta`를 지우고 처음부터 다시 받기 |
| `replace` | `{text}` | 앞서 일부 문장이 나간 뒤 다음 문장이 금지 패턴에 걸려 응답 전체를 안전 문장으로 바꿈. 말풍선을 이 문장으로 교체. 첫 문장부터 걸리면 `replace` 없이 안전 문장이 `delta`로 옴 |
| `error` | `{code, message}` | 3번 실패. 사용자가 한 말은 저장되어 있어 다시 보내면 이어서 대화 |
| `done` | `{}` | 끝 |

- 검증 오류(404 남의 기록 / 409 마무리했거나 10회 초과 / 400 빈 내용·1000자 초과)는 SSE 대신 일반 JSON 오류입니다.
- 케어 방식을 고르지 않고 바로 보내면 '이야기 들어주기'로 시작합니다.
- AI 응답 안전 가드(`AiResponseGuard`, SAF-03): 병명 단정, 약 복용·중단 조언, 자해 방법, 의존 유도 같은 `safety_patterns`(category `AI_OUTPUT`) 부분 일치(띄어쓰기 무시)에 걸린 문장은 화면에 내보내지 않고 준비한 안전 문장으로 바꿔 저장합니다. 케어 첫 마디와 마무리 카드에도 적용됩니다(걸리면 방식별 고정 문장·카드). 패턴은 시드 JSON(`seed/safety-patterns.json`)에 추가하면 서버 시작 때 반영되고 1분간 캐시됩니다.

## 케어 마무리 (CARE-03)

`POST /diaries/{id}/care/complete` → `{ summary: {careType, text}, wool: {added, total, stage, stageUp} }`

- 케어 방식별 카드: 이야기 들어주기=털어놓은 마음 한 줄, 위로받기=오늘 받은 위로의 말, 생각 정리하기=정리된 생각 세 줄, 해볼 행동 찾기=해볼 행동 1~3개(줄바꿈으로 구분).
- 털은 사용자 메시지 3회 이상이고 위기 신호가 없었던 대화에만 +1(`added=1`). 위기 신호가 있었으면 카드만 남기고 `added=0`. 3회 미만이면 409.
- 이미 마무리한 기록을 다시 보내도 털은 오르지 않고 저장된 카드를 `added=0`으로 돌려줍니다. 동시에 두 번 보내도 한 번만 반영됩니다.
- LLM이 실패하면 방식별 고정 카드로 마무리하고 털은 그대로 쌓입니다.

## 기록 (DRY-02)

- `GET /diaries?month=2026-11` (비우면 이번 달): `days`는 날짜별 대표 감정(하루 여러 개면 가장 최근), `items`는 최신순 목록. 항목의 `summaryLine`은 오늘 받은 말의 첫 문장(마무리 전이면 null), `canResume`은 마무리 전이라 이어서 이야기할 수 있는지(`careStatus`가 NONE이면 케어 방식 고르기부터, IN_PROGRESS면 대화 이어가기). 달 형식이 틀리면 400.
- `GET /diaries/{id}`: 감정, 케어 방식, 마무리 카드(`summary`), 내가 한 이야기(`firstMessage`), 대화 전체(`messages`), 털을 받았는지(`woolEarned`).
- `DELETE /diaries/{id}`: 대화와 카드를 함께 삭제. 이미 쌓인 나의 양 털은 유지. 수정은 없음.
- 모두 본인 기록만, 남의 기록은 404.
- `GET /diaries/summary?month=2026-11` (비우면 이번 달): `{month, woolCount, sentence}`. `woolCount`는 그 달에 마무리해 털을 받은 마음의 수(위기 신호가 나온 대화 제외), `sentence`는 가장 자주 고른 '감정 × 케어 방식' 한 문장(예: '불안한 날엔 위로받기를 자주 골랐어요', 같은 횟수면 더 최근 조합). 털을 받은 기록이 3개 미만이면 `sentence`는 null. 기록을 삭제하면 이 집계에서도 빠집니다.

## 닉네임 (ACC-01, ACC-05)

- 규칙(와이어프레임 v3): 2~10자, 한글·영문·숫자와 띄어쓰기 한 칸, 중복 불가(대소문자 무시), 사칭 단어·금칙어·개인정보 패턴 불가. 길이는 `app.policy.auth.nickname-min/max-length`로 조정합니다. 가입 화면의 `GET /auth/nickname/availability`와 가입, 닉네임 바꾸기가 같은 검사(`NicknameValidator`)를 씁니다.
- `PUT /users/me/nickname` `{ "nickname": "..." }` (로그인 필요): 나 탭 '닉네임 바꾸기'(ME-02). 응답은 `{userId, nickname}`. 쓸 수 없으면 409 `ACCOUNT_NICKNAME_UNAVAILABLE`과 이유 문구, 지금 닉네임과 같으면 그대로 성공, 내 이름의 대소문자만 바꾸는 것은 허용. 이미 올린 고민·조언에는 사용자 id로 연결되어 새 닉네임이 그대로 보입니다.
- 금칙어(`BannedWordNicknameFilter`)와 개인정보 패턴(`PiiNicknameContentFilter`) 검사가 `NicknameContentFilter` 빈으로 가입과 닉네임 바꾸기 모두에 적용됩니다.

## 나의 양 꾸미기 (SHP-03)

- `GET /my-sheep/items`: `{yarnCount, spentYarn, items:[{code, name, yarnCost, owned, equipped}]}`. 아이템은 목도리(SCARF)·모자(HAT)·방울(BELL)·리본(RIBBON) 고정 순서. `GET /my-sheep`의 `equippedItems`에도 착용 중인 코드가 반영됩니다.
- `PUT /my-sheep/items` `{ "craft": ["SCARF"], "equipped": ["SCARF"] }`: `craft`는 털실로 만들 아이템(이미 가진 건 털실을 쓰지 않고 건너뜀), `equipped`는 저장 후 착용할 전체 목록(없는 건 해제, 생략하면 변경 없음). 응답은 `GET /my-sheep/items`와 같은 모양.
- 털실이 모자라면 409 `SHEEP_YARN_NOT_ENOUGH`(아무것도 바뀌지 않음), 만들지 않은 아이템을 착용하면 409 `SHEEP_ITEM_NOT_OWNED`, 알 수 없는 코드는 400. 꾸미기는 털 단계에 영향이 없습니다.
- 아이템 이름과 필요 털실은 `application.yml`의 `app.sheep-items.items`(고정 데이터)에서 바꿉니다. 지금 값은 와이어프레임 v3(ME-03) 기준 모자 5, 방울 8, 리본 3이고, 목도리(3)는 화면에 값이 없어 임시 값입니다.
- 털실은 고민 목장에서 조언을 보낼 때마다 1개씩 쌓입니다(`POST /worry-sheep/{id}/wools`).

## 기록 전체 삭제와 탈퇴 (ACC-03)

- `DELETE /me/records`: 마음 돌보기 기록(오늘의 마음·대화·마무리 카드)만 지움 → `{deletedDiaries}`. 나의 양 털, 털실, 꾸미기 아이템은 유지.
- `DELETE /me`: 탈퇴. 대화·기록, 나의 양과 아이템, 위기 판정 로그, 내 고민 양(거기 달린 다른 사람의 조언과 '고마워요' 포함), 내가 보낸 조언, 내가 한 신고와 내 고민·조언에 대한 신고, 차단 관계(내가 한 것·나를 한 것)를 모두 지우고 마지막에 계정을 지움. 한 트랜잭션이라 실패하면 아무것도 지워지지 않음. 같은 이메일로 다시 가입할 수 있고 새 계정으로 시작.
- 다른 사람 고민 양에 이미 쌓인 털 수는 그대로 둠(조언 글만 사라지고 양의 단계는 유지).
- **새 기능이 사용자와 연결된 데이터를 만들면**(예: 푸시 구독 #28) `UserDataEraser`를 구현한 빈을 추가해 탈퇴 때 함께 지워지게 하세요(`domain/user/service/erase/` 참고, `@Order`로 순서 지정). 사용자를 참조하는 엔티티가 새로 생기면 `UserDataErasureCoverageTest`가 실패해서 빠뜨리지 않게 알려 줍니다.

## 고민 목장 (B 영역)

목장 관련 응답에는 다른 사용자의 id·이메일을 넣지 않고 닉네임만 보냅니다. 털 개수 숫자 대신 단계(`stage`, `stageName`)만 내려갑니다. `createdAt` 같은 시각은 시간대 표시가 없는 Asia/Seoul 시각입니다.

### 목장과 고민 양 (FRM-01, FRM-03~07)

| Method | Endpoint | 설명 |
| --- | --- | --- |
| GET | `/farms` | 목장 4곳과 `waitingCount`(ACTIVE 양 수, 사용자별 조건 미적용) |
| GET | `/farms/{farmCode}/sheep?page=0` | 풀밭 목록. `all` 가능, 20마리씩, `hasNext` |
| POST | `/farms/{farmCode}/sheep` | 고민 올리기 `{ content }` (1~200자, 하루 3마리) |
| GET | `/worry-sheep/{id}` | 양 상세. 주인에게만 `wools`(받은 조언) |
| GET | `/worry-sheep/me?status=active\|graduated` | 내 고민. `newWoolCount`는 마지막으로 상세를 본 뒤 도착한 조언 수 |
| POST | `/worry-sheep/{id}/graduate` | 주인이 직접 졸업(멱등) |
| GET | `/diaries/{id}/farm-recommendation` | 케어 마무리에서 올릴 목장 추천(규칙 기반, `FarmRecommender`) |

- 풀밭 정렬: 내가 이미 조언을 보낸 양은 뒤로 → 털 적은 순 → 오래된 순. 제외: ACTIVE가 아닌 양, 내 양, 차단 관계(양방향).
- 보호 양(위험도 CAUTION)은 보낸 조언이 `app.policy.worry.protected-view-min-sent-wools`(5)회 이상인 사용자에게만 보입니다. 열람 판단은 `WorrySheepAccess` 한 곳에 있고 상세·조언 보내기·신고·차단이 함께 씁니다. 볼 수 없는 양은 존재 여부를 흘리지 않도록 모두 404입니다.
- 고민 올리기 응답은 `status`로 구분합니다: `CREATED`(201) / `CRISIS`(200, 양을 만들지 않고 `crisisHelp`에 상담 연결 정보, 24시간 배너 켜짐) / `PII_REJECTED`(200, 저장하지 않음). 처리 순서는 목장 확인 → 길이 → 하루 한도 → 개인정보 → 위험도 분류입니다.
- **LLM이 꺼져 있거나 분류에 실패하면 위기 키워드 판정만 쓰고, 위기가 아닌 고민은 보호 양으로 등록됩니다.** 이 상태에서는 새 고민이 조언 이력 5회 미만인 사용자에게 보이지 않으니, 시연 환경에서는 LLM 키가 들어 있는지 먼저 확인하세요.
- 졸업 경로는 세 가지입니다: 주인의 직접 졸업, 털 `wool-cap`(10)개 도달(조언 보내기 트랜잭션 안에서), 작성 후 `auto-graduate-days`(7)일 경과(`@Scheduled`, `app.policy.worry.auto-graduate-cron` 기본 매시 정각). 스케줄은 `app.scheduling.enabled=false`로 끌 수 있고 테스트 프로필에서는 꺼져 있습니다.

### 조언(털) (WOL-01, WOL-03)

`POST /worry-sheep/{id}/wools` `{ "content": "...", "sendAsIs": false }` (1~100자)

| status | HTTP | 내용 |
| --- | --- | --- |
| `SENT` | 201 | `woolId, sheepStage, sheepStageName, stageUp, graduated, yarnAdded, yarnCount` |
| `SUGGEST` | 200 | 저장하지 않음. `category, categoryLabel, message, suggestion`(없으면 키가 빠짐). 그대로 보내려면 `sendAsIs=true`로 다시 요청 |
| `BLOCKED` | 200 | 저장하지 않음. `category`(`BANNED_WORD` 또는 `PII`), `message` |

- 순서: 길이 → 권한 → 개인정보(`PiiDetector`) → 다시 확인(`AdviceChecker`) → 저장. `BLOCKED`는 `sendAsIs`와 관계없이 막습니다.
- 저장은 한 트랜잭션에서 양 행과 보낸 사람 행을 잠그고 조언 저장, 양 털 +1과 단계 재계산, 보낸 사람 털실 +1·보낸 수 +1을 처리합니다. 동시에 여러 명이 보내도 털은 상한을 넘지 않습니다.
- 오류: 내 양 403 `WOOL_OWN_SHEEP`, 중복 409 `WOOL_ALREADY_SENT`, 졸업한 양 409 `WORRY_SHEEP_GRADUATED`, 볼 수 없는 양 404.
- `POST /wools/{id}/thanks`: 그 조언이 달린 양의 주인만, 조언당 1회(중복 409 `THANKS_ALREADY_SENT`). 보상은 없습니다.

### 안전 (SAF-02, SAF-04, SAF-05)

- `GET /help/crisis`(비회원 가능): 상담 연결 화면 정보(109, 1577-0199, 119). `GET /help/crisis/banner`(로그인): `{ active, crisisBannerUntil }`. 상담 정보는 `CrisisHelpInfo` 한 곳에 있습니다.
- `PiiDetector.containsPii(text)`: 시드의 PII 정규식(전화번호, 이메일, SNS 아이디, 오픈채팅 링크, 계좌번호). 고민·조언·닉네임에 적용됩니다.
- `AdviceChecker.check(content)` → `PASS | SUGGEST | BLOCKED`. 1차 금칙어(`BannedWordFilter`, 부분 일치·띄어쓰기 무시)는 LLM 없이 `BLOCKED`, 2차 LLM 어조 분류(BLAME, LECTURE, COMPARE, MOCK, DIAGNOSIS, SOLICIT)는 `SUGGEST`와 바꾼 문장. LLM이 실패하면 금칙어 판정만 씁니다. 프롬프트는 `prompts/advice-check.txt`.
- 닉네임에는 금칙어·개인정보 필터가 `NicknameContentFilter` 빈으로 자동 적용됩니다.
- 시드는 없는 패턴만 추가합니다. 시드에서 고친 패턴(PII 정규식 3개)의 옛 값은 이미 시드된 DB에 남으므로 지워 주세요.

### 신고와 차단 (RPT-01, RPT-02)

- `POST /reports` `{ targetType: WORRY_SHEEP|WOOL, targetId, reason: ABUSIVE|PERSONAL_INFO|SPAM|LOOKS_DANGEROUS|ETC, blockUser }` → 201 `{ reportId, hidden, blocked }`. 중복 409 `REPORT_DUPLICATED`, 내 글 400 `REPORT_OWN_CONTENT`.
- 서로 다른 신고자 `auto-hide-report-count`(3)명이 신고하면 자동으로 숨깁니다(양은 HIDDEN, 조언은 `wools.hidden`). `LOOKS_DANGEROUS`는 1회만으로 글쓴이에게 24시간 상담 배너를 켭니다.
- 받은 조언은 고민 주인에게만 보이므로 조언을 신고할 수 있는 사람은 주인 1명뿐입니다. 조언의 3회 누적 숨김은 지금 규칙으로는 일어나지 않습니다(정책 결정 필요).
- `POST /blocks` `{ targetType, targetId }`(양 또는 조언 id로 지정, 멱등), `GET /blocks`, `DELETE /blocks/{id}`. 차단하면 서로의 양이 목록과 상세에서 보이지 않습니다.

### 털 도착 알림 (WOL-02)

| Method | Endpoint | 설명 |
| --- | --- | --- |
| GET | `/push/vapid-public-key` | `{ publicKey, enabled }`. 키가 없으면 `enabled=false` |
| POST | `/push/subscriptions` | `{ endpoint, keys: { p256dh, auth } }`. 새 구독 201, 같은 endpoint면 소유자를 바꾸고 200 |
| DELETE | `/push/subscriptions` | `{ endpoint }` 구독 해지(멱등) |
| GET | `/notifications/stream` | 앱 안 알림 SSE. 케어 대화처럼 헤더를 실을 수 있는 `fetch` 스트림으로 읽습니다 |

- 조언이 저장되면 양 주인에게 `wool-arrived`, 고마워요를 누르면 조언을 보낸 사람에게 `wool-thanked` 이벤트가 갑니다. 데이터는 `{ type, title, body, sheepId, url }`이고 웹 푸시 payload도 같습니다. 조언 내용과 닉네임은 넣지 않습니다. 고마워요 알림은 `url`이 null입니다.
- 알림은 트랜잭션이 커밋된 뒤에만 보내고(`@TransactionalEventListener`), 발송이 실패해도 조언 저장에는 영향이 없습니다. 푸시 서비스가 404/410을 돌려준 구독은 지웁니다.
- 웹 푸시는 `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `VAPID_SUBJECT`가 있어야 켜집니다(`npx web-push generate-vapid-keys`). 키가 없어도 서버는 뜨고 구독 저장과 SSE는 동작합니다.
- 구독 endpoint는 https이고 `app.push.allowed-hosts`(FCM, Mozilla, Apple, Windows 푸시 서비스)에 있는 호스트만 받습니다. 그 밖은 400 `PUSH_ENDPOINT_NOT_ALLOWED`입니다.
- 알림을 눌렀을 때 열 경로는 `app.push.click-url-pattern`(기본 `/farm/my/{sheepId}`)에서 바꿉니다.
- SSE 연결은 서버 메모리에 있으므로 서버를 여러 대로 늘리면 다른 서버에 연결된 탭에는 앱 안 알림이 가지 않습니다. 연결은 30분 뒤 끝나고 사용자당 5개까지입니다.
- 푸시 구독은 탈퇴 때 `PushSubscriptionEraser`가 지웁니다.

### B 영역 오류 코드

| HTTP | 코드 | 상황 |
| --- | --- | --- |
| 400 | `SAFETY_PII_DETECTED` | 개인정보 패턴(고민·조언은 오류 대신 정상 응답의 `status`로 알림) |
| 400 | `BLOCK_SELF` | 나 자신을 차단 |
| 400 | `REPORT_OWN_CONTENT` | 내가 쓴 글을 신고 |
| 400 | `PUSH_ENDPOINT_NOT_ALLOWED` | 허용하지 않는 푸시 endpoint |
| 403 | `WOOL_OWN_SHEEP` | 내 고민 양에 조언 |
| 404 | `FARM_NOT_FOUND` | 없는 목장 코드 |
| 409 | `WOOL_ALREADY_SENT` | 같은 양에 두 번째 조언 |
| 409 | `WORRY_SHEEP_GRADUATED` | 졸업했거나 털이 다 찬 양에 조언 |
| 409 | `THANKS_ALREADY_SENT` | 이미 고마워요를 누른 조언 |
| 409 | `REPORT_DUPLICATED` | 같은 대상을 다시 신고 |
| 429 | `WORRY_DAILY_LIMIT_EXCEEDED` | 고민 올리기 하루 3마리 초과 |

## 시드 데이터와 정책 수치

- 서버가 시작될 때 `src/main/resources/seed/`의 목장 4곳과 안전 패턴(위기 키워드, 금칙어, AI 응답 금지 패턴, 개인정보 정규식)을 넣습니다. 이미 있는 데이터는 건너뜁니다.
- 패턴을 추가하려면 `seed/safety-patterns.json`에 넣고 서버를 다시 시작하면 됩니다.
- 기능명세서 5장 수치는 `application.yml`의 `app.policy`에 있고, 코드에서는 `PolicyProperties`로 읽습니다.
- 양 단계 기준표는 `SheepStage` (`ofMySheep`, `ofWorrySheep`)를 사용합니다.

## LLM (Upstage Solar / Claude)

위험도 분류, 케어 대화, 요약, 조언 필터는 `global/llm`의 `LlmClient`를 통해 호출합니다.

| 메서드 | 용도 |
| --- | --- |
| `complete(request)` | 응답 전체를 한 번에 받기 |
| `completeJson(request, Type.class)` | 응답을 JSON 스키마에 맞춰 객체로 받기 (분류 결과 등) |
| `stream(request, onDelta)` | 조각 단위로 받기 (케어 대화 SSE) |

- 요청은 `LlmRequest.classification(...)`(생각 깊이 낮음, 재시도 없음, 타임아웃 지정) 또는 `LlmRequest.chat(...)`으로 만듭니다.
- 실패하면 항상 `LlmException`이 나고, `type`(`TIMEOUT`, `UNAVAILABLE`, `REFUSAL`, `INVALID_RESPONSE`, `API_ERROR`)을 보고 대체 로직으로 넘어가면 됩니다.
- 프롬프트는 `src/main/resources/prompts/{이름}.txt`에 두고 `PromptLoader`로 읽습니다. `withSafetyGuide(이름)`은 공통 안전 지침(`common-safety.txt`)을 앞에 붙입니다.
- 공급자는 `app.llm.provider`(환경변수 `LLM_PROVIDER`)로 고릅니다. 기본 `auto`는 `UPSTAGE_API_KEY`가 있으면 Upstage Solar Pro 4, 없고 `ANTHROPIC_API_KEY`가 있으면 Claude를 씁니다. `upstage` / `claude`로 고정할 수도 있습니다. 시작할 때 로그에 어떤 공급자를 쓰는지 남습니다.
- 키가 하나도 없으면 LLM 없이 서버가 뜨고 모든 호출이 `UNAVAILABLE`로 실패합니다.
- Upstage는 OpenAI 호환 chat completions(`UPSTAGE_BASE_URL`, `UPSTAGE_MODEL`로 주소·모델 변경 가능)를 호출합니다. 생각 깊이(effort)는 보내지 않고, `completeJson`은 스키마 강제 대신 프롬프트로 JSON을 요청해 직접 파싱합니다. 형식이 어긋나면 `INVALID_RESPONSE`입니다.
- LLM에는 본문만 보내고 이메일·닉네임은 보내지 않습니다. 각 공급자의 데이터 이용 정책은 약관을 확인하세요.
- Claude를 쓸 때만 안전 분류기가 응답을 거절하면 서버가 다른 모델로 다시 시도하는 기능(`app.llm.server-fallback`)이 적용됩니다. Upstage에는 없습니다.

## 공통 응답 형식

```json
{ "success": true, "data": { } }
{ "success": false, "error": { "code": "COMMON_401", "message": "로그인이 필요해요." } }
```

에러 코드는 `global/exception/ErrorCode`에 모아 둡니다. 안내 문구는 비난·명령 없는 존댓말로 씁니다.

## CI/CD

- PR마다 GitHub Actions가 빌드와 테스트를 돌립니다.
- `main`에 머지되면 Docker 이미지를 GHCR에 올리고 AWS EC2에 배포합니다.
- EC2 준비와 필요한 Secrets는 [docs/DEPLOY.md](docs/DEPLOY.md)를 보세요.

## 협업 규칙

- 이슈 하나당 브랜치 하나: `feat/{이슈번호}-{간단한-설명}` (예: `feat/1-project-setup`)
- PR 본문에 `Closes #이슈번호`
- 기능명세서: `docs/털한올_기능명세서.md` (2026-11-08, 와이어프레임 v2 기준)
