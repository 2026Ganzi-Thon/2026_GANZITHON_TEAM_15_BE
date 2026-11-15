# 프론트 연동 가이드: 고민 목장·조언·안전·신고·차단·알림 (B 영역)

프론트엔드 1·2 담당자용입니다. 화면 흐름 순서로 **실제 요청·응답**, enum 값, null이 될 수 있는 필드, 오류 코드와 처리 방법을 정리했습니다. 계정(회원가입·로그인)·나의 양·케어 대화(A 영역)는 [README](../README.md)를 보세요.

- 운영 API: `https://teolhanol-api.duckdns.org`, Swagger: `/swagger-ui.html` (로컬은 `http://localhost:8080`)
- 연동·시연용 계정과 데이터는 `scripts/seed_demo.py` 로 만듭니다.

> **예시 JSON에 대해**
> - 서버를 실제로 호출해 받은 직렬화 결과입니다(스프링 부트 통합 테스트 서버, 위험도·어조 판정 결과는 경우별로 지정). 키 이름·타입·null/생략 여부·메시지 원문은 그대로이고, id·시각·토큰 값만 그때그때 달라집니다.
> - 시각의 소수점 이하 자릿수는 0~7자리로 들쭉날쭉할 수 있습니다(끝의 0은 잘림). 파싱 방법은 [공통 규칙](#2-공통-규칙)을 보세요.
> - 길이 때문에 목록 예시 하나만 `// …` 로 줄였고, 그 자리에 표시했습니다.

## 목차

1. [화면 ↔ 엔드포인트](#1-화면--엔드포인트)
2. [공통 규칙](#2-공통-규칙)
3. [화면별 연동](#3-화면별-연동)
   - [3.1 인증(토큰)](#31-인증토큰)
   - [3.2 목장 풀밭 FARM-03](#32-목장-풀밭-farm-03)
   - [3.3 고민 올리기 FARM-01/02, CRISIS-02](#33-고민-올리기-farm-0102-crisis-02)
   - [3.4 조언하기 FARM-04/04b/04c](#34-조언하기-farm-04--04b--04c)
   - [3.5 내 고민 MGT-01/02, FARM-05](#35-내-고민-mgt-0102-farm-05)
   - [3.6 신고·차단 SAFE-00~03](#36-신고차단-safe-003)
   - [3.7 위기 CRISIS-02, ME-01](#37-위기-crisis-02-me-01)
   - [3.8 케어 마무리 → 목장 추천](#38-케어-마무리--목장-추천)
   - [3.9 알림 NOTI-01](#39-알림-noti-01)
   - [3.10 공통 오류](#310-공통-오류)
4. [enum 값 전체 표](#4-enum-값-전체-표)
5. [오류 코드 표](#5-오류-코드-표)
6. [TypeScript 타입](#6-typescript-타입)
7. [SSE 알림 스트림을 fetch 로 읽기](#7-sse-알림-스트림을-fetch-로-읽기)
8. [자주 헷갈리는 점](#8-자주-헷갈리는-점)

---

## 1. 화면 ↔ 엔드포인트

| 화면 | 하는 일 | 엔드포인트 |
| --- | --- | --- |
| FARM-03 | 목장 탭, 풀밭 양 목록 | `GET /farms`, `GET /farms/{farmCode}/sheep?page=` |
| FARM-01 | 고민 올리기 입력 | `POST /farms/{farmCode}/sheep` |
| FARM-02 | 새 양이 태어났어요 | 위 응답의 `status=CREATED` |
| CRISIS-02 | 상담 연결 | 위 응답의 `status=CRISIS` → `crisisHelp`, 또는 `GET /help/crisis` |
| FARM-04 | 조언하기(고민 보기, 입력) | `GET /worry-sheep/{id}`, `POST /worry-sheep/{id}/wools` |
| FARM-04b | 조언 다시 확인 | 위 응답의 `status=SUGGEST` / `BLOCKED` |
| FARM-04c | 한 올 보냈어요 | 위 응답의 `status=SENT` |
| MGT-01 | 내 고민(진행 중) | `GET /worry-sheep/me?status=active` |
| MGT-02 | 보관함 | `GET /worry-sheep/me?status=graduated` |
| FARM-05 | 털이 풍성해졌어요(받은 조언, 하트) | `GET /worry-sheep/{id}`(주인), `POST /wools/{id}/thanks`, `POST /worry-sheep/{id}/graduate` |
| SAFE-00~02 | ⋯ 메뉴, 신고, 신고 완료·차단 | `POST /reports`, `POST /blocks` |
| SAFE-03 | 차단한 목동 관리 | `GET /blocks`, `DELETE /blocks/{id}` |
| ME-01 | 나 탭의 상담 배너·힘들 때 도움받을 곳 | `GET /help/crisis/banner`, `GET /help/crisis` |
| CARE-03 | 케어 마무리 → 목장 올리기 버튼 | `GET /diaries/{id}/farm-recommendation` |
| NOTI-01 | 알림(웹 푸시·앱 안 토스트) | `GET /push/vapid-public-key`, `POST·DELETE /push/subscriptions`, `GET /notifications/stream` |

로그인 없이 부를 수 있는 것은 `/auth/**`, `GET /help/crisis`, `/health`, Swagger뿐입니다. 나머지는 모두 토큰이 필요합니다.

---

## 2. 공통 규칙

**Base URL과 인증**

```
Authorization: Bearer {accessToken}
Content-Type: application/json
```

- 토큰은 `POST /auth/signup`, `POST /auth/login` 응답의 `accessToken`입니다(유효 시간 `expiresInSeconds`, 지금은 86400초). 토큰이 없거나 만료되면 401 `COMMON_401` 입니다. 로그아웃은 클라이언트가 토큰을 지우는 것으로 끝납니다.
- CORS 허용 출처는 서버 환경변수(`CORS_ALLOWED_ORIGINS`)로 정합니다. 개발 서버 주소가 막히면 백엔드에 알려 주세요.

**응답 래퍼**

```json
{ "success": true,  "data": { } }
{ "success": false, "error": { "code": "COMMON_401", "message": "로그인이 필요해요." } }
```

- 값이 없는 키는 **빠지거나 `null`** 입니다(DTO마다 다름). 아래 화면별 절의 "null·생략" 표시를 보세요.
- 성공인데 `data`가 없는 경우가 있습니다: `DELETE /blocks/{id}` → `{ "success": true }`.
- `error.message`는 비난·명령 없는 존댓말로 그대로 화면에 보여 줘도 되는 문구입니다. 다만 **`COMMON_400` 의 message는 상황마다 다르고**, `POST·DELETE /push/subscriptions` 의 입력 검증 오류는 `must not be blank` 처럼 영어 기본 문구가 올 수 있으니 그대로 보여 주지 말고 `code`로 분기하세요.
- **HTTP 상태와 `data.status`를 둘 다 봅니다.** 고민 올리기·조언 보내기는 "정상 처리 못 했지만 오류는 아닌" 경우(`CRISIS`, `PII_REJECTED`, `SUGGEST`, `BLOCKED`)를 **200** + `data.status` 로 내려줍니다. 새로 만들어진 경우(`CREATED`, `SENT`)만 **201** 입니다.

**시각 형식**

- `createdAt`, `graduatedAt`, `blockedAt`, `crisisBannerUntil` 은 `2026-11-15T18:38:02.143905` 처럼 **시간대 표시가 없는 Asia/Seoul 시각**입니다. `new Date(s)` 는 브라우저 시간대로 해석하고 소수점 7자리는 일부 브라우저에서 실패하므로 아래처럼 바꿔 쓰세요.

```ts
/** 서버의 시간대 없는 서울 시각 문자열 → Date */
export const parseServerTime = (s: string): Date =>
  new Date(s.replace(/(\.\d{3})\d+/, '$1') + '+09:00');
```

- 상대 시간 문구("3시간 전")는 `createdAt`으로 프론트에서 만듭니다.

**양의 단계 `stage`(1~5)와 `stageName`**

| stage | stageName |
| --- | --- |
| 1 | 털 깎은 양 |
| 2 | 보송한 양 |
| 3 | 포근한 양 |
| 4 | 몽글몽글 양 |
| 5 | 구름 양 |

- 고민 양의 단계는 받은 조언 수로 정해집니다(1단계 0개, 2단계 1개부터, 3단계 3개부터, 4단계 6개부터, 5단계 10개). 10개가 차면 5단계가 되면서 졸업합니다.
- **털 개수 숫자는 어느 응답에도 내려오지 않습니다.** `stage`, `stageName`만 씁니다.
- 이름 문구는 `stageName`을 그대로 쓰세요(프론트에 따로 두지 않아도 됩니다).

**다른 사용자 정보**

- 다른 사용자의 `userId`·이메일은 어느 응답에도 없습니다. 닉네임(`nickname`, `senderNickname`)만 옵니다.

**글자 수**

- 고민 1~200자, 조언 1~100자이고 **앞뒤 공백을 뺀 글자 수(유니코드 코드포인트)** 입니다. 이모지도 1자입니다. JS의 `str.length`는 이모지를 2로 세므로 `[...str.trim()].length`로 세세요.

---

## 3. 화면별 연동

### 3.1 인증(토큰)

가입·로그인의 상세(닉네임 규칙, 잠금 423 등)는 README의 '인증'과 '닉네임'을 보세요. 토큰만 짧게 정리합니다.

`POST /auth/signup` → 201

```json
{
  "success": true,
  "data": {
    "userId": 8,
    "nickname": "새내기목동",
    "sheepStage": 1,
    "sheepWoolCount": 0,
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI4IiwiaWF0IjoxNzk0NzM1NDgxLCJleHAiOjE3OTQ4MjE4ODF9.vD-Fz3JoubCq7K5wp_2TJ__UZ_AO3PCvYNCdz7Xul00",
    "expiresInSeconds": 86400
  }
}
```

`POST /auth/login` → 200 `{ userId, nickname, accessToken, expiresInSeconds }`(가입 응답에서 `sheepStage`, `sheepWoolCount`가 빠진 모양).

이후 모든 요청에 `Authorization: Bearer {accessToken}` 헤더를 붙입니다. 토큰이 없으면:

```json
{ "success": false, "error": { "code": "COMMON_401", "message": "로그인이 필요해요." } }
```

---

### 3.2 목장 풀밭 FARM-03

**호출 순서**

1. 화면에 들어올 때 `GET /farms` → 주제 탭(전체 + 4곳)과 `waitingCount`.
2. `GET /farms/all/sheep?page=0` (전체 탭) 또는 `GET /farms/{farmCode}/sheep?page=0` (탭 선택).
3. 스크롤·더 보기에서 `hasNext=true` 이면 `page+1` 로 다시 호출.

**`GET /farms`** → 200

```json
{
  "success": true,
  "data": [
    { "code": "CAREER", "name": "진로·취업", "tabName": "진로",
      "description": "면접, 진로 선택, 이직처럼 앞길이 막막할 때", "waitingCount": 6 },
    { "code": "RELATIONSHIP", "name": "관계", "tabName": "관계",
      "description": "가족, 친구, 연애처럼 사람 사이에서 생긴 고민", "waitingCount": 5 },
    { "code": "BURNOUT", "name": "번아웃", "tabName": "번아웃",
      "description": "일과 공부에 지치고 소진된 마음", "waitingCount": 5 },
    { "code": "SELF", "name": "나 자신", "tabName": "나",
      "description": "자존감, 불안처럼 나를 향한 고민", "waitingCount": 5 }
  ]
}
```

- 항상 4곳이 고정 순서(진로 → 관계 → 번아웃 → 나)로 옵니다. **'전체' 탭은 목록에 없으니 프론트가 맨 앞에 붙입니다**(코드는 `all`).
- `tabName`은 탭 글자, `name`은 올리기 화면의 목장 이름에 씁니다.
- `waitingCount`는 그 목장의 ACTIVE 양 수입니다(내 양·차단·보호 양 조건은 반영하지 않은 대략적인 수). 풀밭 목록의 개수와 다를 수 있으니 정확한 개수로 쓰지 마세요.

**`GET /farms/CAREER/sheep`** → 200 (이미 조언을 보낸 양이 뒤로 가는 예)

```json
{
  "success": true,
  "data": {
    "farmCode": "CAREER",
    "page": 0,
    "size": 20,
    "hasNext": false,
    "sheep": [
      {
        "sheepId": 26,
        "farmCode": "CAREER",
        "stage": 1,
        "stageName": "털 깎은 양",
        "preview": "회사 사람들과 거리감이 느껴져요.",
        "nickname": "새벽목동",
        "createdAt": "2026-11-15T18:38:02.279102",
        "alreadySent": false
      },
      {
        "sheepId": 22,
        "farmCode": "CAREER",
        "stage": 2,
        "stageName": "보송한 양",
        "preview": "진로를 정하지 못해서 매일 불안해요.",
        "nickname": "민들레목동",
        "createdAt": "2026-11-15T18:38:02.143905",
        "alreadySent": true
      }
    ]
  }
}
```

**`GET /farms/all/sheep?page=0`** → 다음 페이지가 있을 때

```json
{
  "success": true,
  "data": {
    "farmCode": "ALL",
    "page": 0,
    "size": 20,
    "hasNext": true,
    "sheep": [
      {
        "sheepId": 1,
        "farmCode": "CAREER",
        "stage": 1,
        "stageName": "털 깎은 양",
        "preview": "요즘 아침마다 일어나기가 너무 힘들고 하루 종일 아무것도 손에 잡히지 않…",
        "nickname": "풀밭목동",
        "createdAt": "2026-11-15T18:38:01.739963",
        "alreadySent": false
      },
      {
        "sheepId": 2,
        "farmCode": "RELATIONSHIP",
        "stage": 1,
        "stageName": "털 깎은 양",
        "preview": "필러 고민 1번이에요.",
        "nickname": "풀밭목동",
        "createdAt": "2026-11-15T18:38:01.752175",
        "alreadySent": false
      }
      // … 이하 18마리 (실제 응답은 20마리, 모양은 같음)
    ]
  }
}
```

비어 있으면 (EMPTY 상태) → 200

```json
{ "success": true, "data": { "farmCode": "ALL", "page": 0, "size": 20, "hasNext": false, "sheep": [] } }
```

**프론트가 알아둘 것**

- `farmCode` 경로는 대소문자를 가리지 않습니다(`career` 가능). 응답의 `farmCode`는 항상 대문자이고 전체 탭은 `"ALL"` 입니다.
- 정렬은 서버가 정합니다: **내가 이미 조언을 보낸 양은 뒤로 → 받은 조언이 적은 순 → 오래된 순.** 프론트에서 다시 정렬하지 마세요.
- `preview`는 고민 앞 40자이고 넘으면 끝에 `…`가 붙어 옵니다. 전체 본문은 `GET /worry-sheep/{id}`.
- `alreadySent=true` 인 양은 '조언하기' 대신 "이미 보냈어요" 표시를 해 주세요.
- 내 양, 졸업·숨김 양, 차단 관계 사용자의 양은 목록에 없습니다. 보호(주의) 양은 조언을 5회 이상 보낸 사용자에게만 보입니다([자주 헷갈리는 점](#8-자주-헷갈리는-점) 참고).
- 풀밭에서 양을 눌러 아래 카드에 보이는 정보(앞부분·닉네임·시각)는 이 목록 데이터로 충분합니다. 상세 호출은 '조언하기'를 눌러 FARM-04로 갈 때 합니다.

**오류**: 없는 목장 → 404 `FARM_NOT_FOUND`, `page`가 음수이거나 숫자가 아니면 400 `COMMON_400`.

```json
{ "success": false, "error": { "code": "FARM_NOT_FOUND", "message": "찾으시는 목장이 없어요. 목장 목록에서 다시 골라 주세요." } }
```

---

### 3.3 고민 올리기 FARM-01/02, CRISIS-02

**호출 순서**

1. (케어 마무리에서 들어왔다면) `GET /diaries/{id}/farm-recommendation` 으로 목장만 미리 고름 → [3.8](#38-케어-마무리--목장-추천). 탭에서 들어왔다면 현재 탭이 기본값.
2. 입력(1~200자) → `POST /farms/{farmCode}/sheep` `{ "content": "..." }`. **여기의 `farmCode`는 `CAREER`·`RELATIONSHIP`·`BURNOUT`·`SELF` 중 하나**여야 합니다(`all`은 400).
3. 응답의 `data.status` 로 화면을 나눕니다.

| `data.status` | HTTP | 화면 |
| --- | --- | --- |
| `CREATED` | 201 | FARM-02 '새 양이 태어났어요' + `content` 인용 → '목장 가기' |
| `PII_REJECTED` | 200 | 입력 화면에 머무르며 `message` 안내(저장되지 않음, 입력 내용은 유지) |
| `CRISIS` | 200 | CRISIS-02 상담 연결 화면(`crisisHelp` 사용, 양은 만들어지지 않음) |

**`CREATED`** → 201 (`POST /farms/CAREER/sheep` `{"content":"진로를 정하지 못해서 매일 불안해요."}`)

```json
{
  "success": true,
  "data": {
    "status": "CREATED",
    "sheepId": 22,
    "farm": { "code": "CAREER", "name": "진로·취업", "tabName": "진로" },
    "stage": 1,
    "stageName": "털 깎은 양",
    "content": "진로를 정하지 못해서 매일 불안해요."
  }
}
```

**`PII_REJECTED`** → 200 (본문에 전화번호가 있는 경우)

```json
{
  "success": true,
  "data": {
    "status": "PII_REJECTED",
    "message": "전화번호나 계정 아이디 같은 연락처는 남기지 않도록 해 주세요. 그 부분만 빼고 다시 적어 주시면 좋아요."
  }
}
```

**`CRISIS`** → 200 (`crisisHelp` 모양은 `GET /help/crisis` 와 같습니다)

```json
{
  "success": true,
  "data": {
    "status": "CRISIS",
    "crisisHelp": {
      "title": "전화로 이야기해 볼래요?",
      "message": "지금 마음이 많이 힘드시다면, 이야기를 들어 줄 사람이 있어요. 편한 곳에 연락해 보세요.",
      "contacts": [
        { "name": "자살예방 상담전화", "phone": "109", "description": "힘든 마음을 언제든 이야기할 수 있어요." },
        { "name": "정신건강 위기상담", "phone": "1577-0199", "description": "마음이 많이 힘들 때 전문 상담사와 이야기할 수 있어요." }
      ],
      "emergency": { "phone": "119", "message": "지금 위험하다면 119에 바로 연락해 주세요." },
      "hoursNote": "두 곳 모두 24시간"
    }
  }
}
```

- `CRISIS`가 오면 서버가 24시간 상담 배너를 켭니다(3.7). 이 화면에서는 `tel:` 링크(`tel:109`)와 '대화로 돌아가기'를 보여 주세요. 사용자가 쓴 글을 화면에 다시 비난조로 보여 주지 마세요.
- `PII_REJECTED` 와 `CRISIS` 는 양이 만들어지지 않으므로 하루 한도(3마리)에 포함되지 않습니다.

**오류**

| 상황 | HTTP | code | message(원문) |
| --- | --- | --- | --- |
| 하루 3마리 초과 | 429 | `WORRY_DAILY_LIMIT_EXCEEDED` | 오늘은 고민을 충분히 들려주셨어요. 내일 다시 들려주세요. |
| 빈 내용·201자 이상 | 400 | `COMMON_400` | 고민은 1자에서 200자 사이로 적어 주세요. |
| `all` 로 올림 | 400 | `COMMON_400` | 고민을 올릴 목장을 하나 골라 주세요. |
| 없는 목장 | 404 | `FARM_NOT_FOUND` | 찾으시는 목장이 없어요. 목장 목록에서 다시 골라 주세요. |
| 본문 JSON 오류 | 400 | `COMMON_400` | 입력한 내용을 한 번 더 확인해 주세요. |

```json
{ "success": false, "error": { "code": "WORRY_DAILY_LIMIT_EXCEEDED", "message": "오늘은 고민을 충분히 들려주셨어요. 내일 다시 들려주세요." } }
```

- 하루 한도는 서울 시간 0시에 초기화되고, 숨김·졸업한 양도 오늘 만든 수에 포함됩니다.
- 처리 순서는 목장 → 길이 → 하루 한도 → 개인정보 → 위험도입니다. 그래서 한도에 닿았으면 본문에 전화번호가 있어도 429가 먼저 옵니다.
- 보호 양(주의 등급)으로 등록되었는지는 **응답에 나오지 않습니다**(`CREATED` 모양이 같음).

---

### 3.4 조언하기 FARM-04 / 04b / 04c

**호출 순서**

1. 풀밭에서 '조언하기' → `GET /worry-sheep/{id}` 로 고민 본문을 보여 줌(FARM-04).
2. 입력(1~100자) → `POST /worry-sheep/{id}/wools` `{ "content": "...", "sendAsIs": false }`.
3. `data.status` 로 분기:

| `data.status` | HTTP | 화면 |
| --- | --- | --- |
| `SENT` | 201 | FARM-04c '한 올 보냈어요' + '털실 1개를 받았어요' |
| `SUGGEST` | 200 | FARM-04b 조언 다시 확인: '이 문장으로 바꾸기' / '그대로 보내기' |
| `BLOCKED` | 200 | 보낼 수 없음 안내 + 다시 쓰기 |

**`GET /worry-sheep/{id}` (다른 사람의 양)** → 200

```json
{
  "success": true,
  "data": {
    "sheepId": 22,
    "farm": { "code": "CAREER", "name": "진로·취업", "tabName": "진로" },
    "content": "진로를 정하지 못해서 매일 불안해요.",
    "stage": 1,
    "stageName": "털 깎은 양",
    "nickname": "민들레목동",
    "createdAt": "2026-11-15T18:38:02.143905",
    "status": "ACTIVE",
    "mine": false,
    "canSendWool": true,
    "alreadySent": false
  }
}
```

- 다른 사람의 양이면 **`wools` 키 자체가 없습니다.** 받은 조언 목록은 주인에게만 옵니다.
- `canSendWool=false` 이면 입력창을 막습니다(이미 보냈거나, `status`가 ACTIVE가 아님). 이미 보냈다면 `alreadySent=true` 도 같이 옵니다.
- `status` 값: `ACTIVE`, `GRADUATED`. 타인에게는 ACTIVE 양만 보이고 졸업·숨김은 404입니다. 주인에게는 ACTIVE·GRADUATED만 보입니다(숨김 양은 주인에게도 404라서 `HIDDEN` 값은 내려오지 않습니다).
- 내가 조언을 보낸 뒤 상세를 다시 부르면 `stage`가 올라 있고 `canSendWool=false`, `alreadySent=true` 입니다.

**`SENT`** → 201 (`{"content":"그러게 ... 열심히 하세요.","sendAsIs":true}`, 1단계에서 2단계로 올라간 경우)

```json
{
  "success": true,
  "data": {
    "status": "SENT",
    "woolId": 1,
    "sheepStage": 2,
    "sheepStageName": "보송한 양",
    "stageUp": true,
    "graduated": false,
    "yarnAdded": 1,
    "yarnCount": 1
  }
}
```

- `sheepStage`, `sheepStageName`: 조언을 받은 **양의 새 단계**. `stageUp`: 이번 조언으로 단계가 올랐는지. 올랐으면 FARM-04c에서 단계 변화를 보여 줄 수 있습니다. 올라가지 않은 경우의 응답(`stageUp:false`)은 같은 모양입니다.
- `graduated=true` 이면 이번 조언으로 털이 다 차서 양이 졸업한 것입니다(조언 10개째).
- `yarnAdded`는 이번에 받은 털실(지금은 1), `yarnCount`는 내 털실 총합입니다. '내 양 꾸미러 가기'는 A 영역 `GET /my-sheep/items` 로 연결됩니다.

**`SUGGEST`** → 200, 바꿀 문장이 있는 경우 (`suggestion` 있음)

```json
{
  "success": true,
  "data": {
    "status": "SUGGEST",
    "category": "LECTURE",
    "categoryLabel": "다그침·훈계",
    "message": "다그치는 말처럼 들릴 수 있어요.",
    "suggestion": "지금은 쉬어 가도 괜찮아요. 조금 기운이 나면 한 걸음씩 해 봐요."
  }
}
```

**`SUGGEST`** → 200, 바꿀 문장을 만들지 못한 경우 (**`suggestion` 키가 없음**)

```json
{
  "success": true,
  "data": {
    "status": "SUGGEST",
    "category": "BLAME",
    "categoryLabel": "비난",
    "message": "탓하는 말처럼 들릴 수 있어요."
  }
}
```

- `suggestion`이 없으면 '이 문장으로 바꾸기' 버튼 없이 '그대로 보내기'와 '다시 쓰기'만 보여 주세요.
- **저장되지 않았습니다.** 사용자가 고른 대로 다시 요청합니다.
  - '그대로 보내기' → **같은 `content`에 `sendAsIs: true`** 로 다시 `POST`. 이때 어조 확인을 건너뛰고 저장합니다(`SENT` 201). 단 금칙어·개인정보는 `sendAsIs`와 관계없이 막힙니다.
  - '이 문장으로 바꾸기' → 입력창에 `suggestion`을 채워 사용자가 한 번 더 보게 하고, `sendAsIs: false`로 다시 `POST`(제안 문장도 다시 확인을 받습니다).
- `message`는 그대로 안내 문구로 쓰고, 어떤 단어가 걸렸는지는 서버도 알려 주지 않습니다.

**`BLOCKED`** → 200, 금칙어

```json
{
  "success": true,
  "data": {
    "status": "BLOCKED",
    "category": "BANNED_WORD",
    "message": "마음을 다치게 할 수 있는 표현이 들어 있어요. 조금 다르게 써 볼까요?"
  }
}
```

**`BLOCKED`** → 200, 개인정보 (`category` 가 `PII`)

```json
{
  "success": true,
  "data": {
    "status": "BLOCKED",
    "category": "PII",
    "message": "전화번호나 계정 아이디 같은 연락처는 남기지 않도록 해 주세요. 그 부분만 빼고 다시 적어 주시면 좋아요."
  }
}
```

- `BLOCKED`에는 `categoryLabel`, `suggestion`이 없습니다.
- LLM 어조 확인이 실패하거나 시간이 넘으면 서버는 금칙어 판정만으로 처리하므로 `SUGGEST`가 안 올 수 있습니다. 프론트는 신경 쓸 필요 없습니다.

**오류**

| 상황 | HTTP | code | message(원문) |
| --- | --- | --- | --- |
| 내 양에 조언 | 403 | `WOOL_OWN_SHEEP` | 내 고민 양에는 털을 보낼 수 없어요. 다른 목동의 양을 둘러봐 주세요. |
| 같은 양에 두 번째 | 409 | `WOOL_ALREADY_SENT` | 이 양에게는 이미 털 한 올을 보냈어요. |
| 그 사이 졸업한 양(또는 털이 다 찬 양) | 409 | `WORRY_SHEEP_GRADUATED` | 이 양은 방금 목장을 떠났어요. |
| 빈 내용·101자 이상 | 400 | `COMMON_400` | 조언은 1자에서 100자 사이로 적어 주세요. |
| 없는·숨김·차단 관계·볼 수 없는 양 | 404 | `COMMON_404` | 찾으시는 내용이 없어요. |

```json
{ "success": false, "error": { "code": "WOOL_ALREADY_SENT", "message": "이 양에게는 이미 털 한 올을 보냈어요." } }
{ "success": false, "error": { "code": "WORRY_SHEEP_GRADUATED", "message": "이 양은 방금 목장을 떠났어요." } }
```

- 409 `WORRY_SHEEP_GRADUATED`는 안내 문구를 보여 주고 목장으로 돌려보내세요(스펙: '이 양은 방금 목장을 떠났어요').
- 검사 순서는 숨김/차단/볼 수 없는 보호 양(404) → 내 양(403) → 이미 보냄(409) → 졸업(409)입니다.
- 입력창 아래 기본 문장 3개('오늘 정말 고생 많았어요', '응원할게요', '혼자가 아니에요')는 서버가 내려주지 않으니 프론트에 둡니다.

---

### 3.5 내 고민 MGT-01/02, FARM-05

**호출 순서**

1. 목장 › 내 고민(MGT-01) → `GET /worry-sheep/me?status=active` (기본값). 보관함(MGT-02) 탭 → `status=graduated`.
2. 양을 누르면 FARM-05 → `GET /worry-sheep/{id}` (주인이므로 `wools` 포함). 이 호출이 **'확인한 시각'을 남겨** 다음 목록 조회에서 `newWoolCount`가 0이 됩니다.
3. 조언마다 하트 → `POST /wools/{woolId}/thanks`.
4. 직접 보내기 → `POST /worry-sheep/{id}/graduate`.

**`GET /worry-sheep/me?status=active`** → 200 (최신순)

```json
{
  "success": true,
  "data": {
    "status": "ACTIVE",
    "sheep": [
      {
        "sheepId": 24,
        "farm": { "code": "BURNOUT", "name": "번아웃", "tabName": "번아웃" },
        "preview": "일에 지쳐서 아무것도 하기 싫어요.",
        "stage": 1,
        "stageName": "털 깎은 양",
        "createdAt": "2026-11-15T18:38:02.195879",
        "graduatedAt": null,
        "newWoolCount": 0,
        "hidden": false
      },
      {
        "sheepId": 22,
        "farm": { "code": "CAREER", "name": "진로·취업", "tabName": "진로" },
        "preview": "진로를 정하지 못해서 매일 불안해요.",
        "stage": 2,
        "stageName": "보송한 양",
        "createdAt": "2026-11-15T18:38:02.143905",
        "graduatedAt": null,
        "newWoolCount": 2,
        "hidden": false
      }
    ]
  }
}
```

- `status` 값은 대문자(`ACTIVE`/`GRADUATED`)로 돌아오지만 요청 파라미터는 `active`/`graduated` 대소문자 상관없습니다. 그 밖의 값은 400 `COMMON_400`.
- **`graduatedAt`은 키가 있고 값이 `null`**일 수 있습니다(진행 중). 보관함에서는 졸업 시각이 옵니다.
- `newWoolCount`: 마지막으로 FARM-05(상세)를 연 뒤 새로 도착한 조언 수. '새 털 n올 도착' 표시에 쓰고, 0이면 표시하지 않습니다.
- `hidden=true` 는 신고 누적으로 숨겨진 내 양입니다(진행 중 탭에만 나타남). 이 양은 **상세가 404**이므로 눌러도 이동하지 못하게 하고 '신고로 숨김 처리됐어요' 같은 상태 표시만 해 주세요. 보관함(`graduated`)에는 `hidden`이 항상 false입니다.
- 비어 있으면 `"sheep": []` → EMPTY-01 '아직 올린 고민이 없어요'.

**`GET /worry-sheep/{id}` (주인)** → 200

```json
{
  "success": true,
  "data": {
    "sheepId": 22,
    "farm": { "code": "CAREER", "name": "진로·취업", "tabName": "진로" },
    "content": "진로를 정하지 못해서 매일 불안해요.",
    "stage": 2,
    "stageName": "보송한 양",
    "nickname": "민들레목동",
    "createdAt": "2026-11-15T18:38:02.143905",
    "status": "ACTIVE",
    "mine": true,
    "canSendWool": false,
    "alreadySent": false,
    "wools": [
      {
        "woolId": 2,
        "content": "천천히 해도 괜찮아요. 응원할게요.",
        "senderNickname": "구름목동",
        "createdAt": "2026-11-15T18:38:02.510127",
        "thanked": false
      },
      {
        "woolId": 1,
        "content": "그러게 미리 준비했어야죠. 이제라도 열심히 하세요.",
        "senderNickname": "조언목동",
        "createdAt": "2026-11-15T18:38:02.427797",
        "thanked": true
      }
    ]
  }
}
```

- 주인에게만 `wools`가 있고(최신순), 조언이 없으면 빈 배열 `[]`입니다. **타입은 `wools?: WoolItem[]`** 로 두고 `mine`일 때만 읽으세요.
- `thanked`: 하트를 이미 눌렀는지. 신고로 숨겨진 조언은 목록에서 빠집니다.
- 졸업한 내 양(`status: "GRADUATED"`)도 주인은 상세와 `wools`를 볼 수 있습니다(보관함에서 받은 조언 다시 보기).

**`POST /wools/{id}/thanks`** → 201

```json
{ "success": true, "data": { "woolId": 1, "thanked": true } }
```

- 그 조언이 달린 **양의 주인만** 누를 수 있고 조언마다 1회입니다. 보상은 없고, 조언을 보낸 사람에게 `wool-thanked` 알림이 갑니다.
- 이미 눌렀으면 409 `THANKS_ALREADY_SENT`(하트를 눌린 상태로 맞추세요), 주인이 아니거나 없는·숨긴 양의 조언이면 404. 졸업한 양의 조언에도 누를 수 있습니다.

```json
{ "success": false, "error": { "code": "THANKS_ALREADY_SENT", "message": "이 조언에는 이미 고마운 마음을 전했어요." } }
```

**`POST /worry-sheep/{id}/graduate`** (요청 본문 없음) → 200

```json
{
  "success": true,
  "data": {
    "sheepId": 23,
    "status": "GRADUATED",
    "graduatedAt": "2026-11-15T18:38:02.519807",
    "stage": 1,
    "stageName": "털 깎은 양"
  }
}
```

- 이미 졸업한 양에 다시 불러도 오류 없이 현재 상태를 그대로 돌려줍니다(멱등). 주인이 아니거나 없는·숨김 양은 404입니다.
- 졸업은 세 가지 경로입니다: 주인의 직접 졸업, 조언 10개 도달(조언 보내기에서), 작성 후 7일 경과(서버 스케줄, 매시 정각).

---

### 3.6 신고·차단 SAFE-00~03

**호출 순서**

1. 조언하기 화면 ⋯ 메뉴(SAFE-00) → '신고하기'(SAFE-01) → 사유 선택 → `POST /reports`.
2. 신고 완료(SAFE-02)의 '이 목동 차단하기' 토글: 신고할 때 `blockUser: true` 로 함께 보내거나, 완료 화면에서 따로 `POST /blocks`.
3. 나 탭 '차단한 목동 관리'(SAFE-03) → `GET /blocks`, 해제는 `DELETE /blocks/{blockId}`.

사용자 id는 주고받지 않습니다. **대상을 양 또는 조언의 id로 지정합니다.**

**`POST /reports`** → 201

요청: `{ "targetType": "WORRY_SHEEP", "targetId": 26, "reason": "ABUSIVE", "blockUser": false }` (`blockUser`는 생략하면 false)

```json
{ "success": true, "data": { "reportId": 1, "hidden": false, "blocked": false } }
```

- `blockUser: true` 로 보내면 글쓴이를 함께 차단하고 `blocked: true` 가 옵니다(이미 차단한 상대여도 true).

```json
{ "success": true, "data": { "reportId": 2, "hidden": false, "blocked": true } }
```

- 서로 다른 목동 3명이 신고하면 대상이 자동으로 숨겨지고, **세 번째 신고 응답에 `hidden: true`** 가 옵니다. 사용자에게는 신고 완료 화면(SAFE-02) 그대로 보여 주면 되고 `hidden`으로 화면을 바꿀 필요는 없습니다.

```json
{ "success": true, "data": { "reportId": 3, "hidden": true, "blocked": false } }
```

- `LOOKS_DANGEROUS`(글쓴이가 위험해 보여요)는 1회만으로 글쓴이에게 상담 안내 배너가 켜집니다. 신고자 화면은 다른 사유와 같습니다.
- `targetType`: `WORRY_SHEEP`은 볼 수 있는 다른 목동의 양, `WOOL`은 **내 양에 달린 조언**(받은 조언을 보는 주인만 신고할 수 있음)입니다.

| 상황 | HTTP | code | message(원문) |
| --- | --- | --- | --- |
| 내가 쓴 글 | 400 | `REPORT_OWN_CONTENT` | 내가 쓴 글은 신고할 수 없어요. |
| 이미 신고함 | 409 | `REPORT_DUPLICATED` | 이미 신고해 주신 내용이에요. 살펴볼게요, 고마워요. |
| 없거나 볼 수 없는 대상 | 404 | `COMMON_404` | 찾으시는 내용이 없어요. |
| 모르는 `reason`/`targetType` 값 | 400 | `COMMON_400` | 입력한 내용을 한 번 더 확인해 주세요. |

- 409 `REPORT_DUPLICATED`는 오류처럼 보이지 않게 "이미 신고했어요"로 SAFE-02 화면을 보여 줘도 됩니다(문구가 이미 고마움을 담고 있습니다).

**`POST /blocks`** → 새 차단은 **201**, 이미 차단한 상대면 기존 차단을 **200** (멱등)

요청: `{ "targetType": "WORRY_SHEEP", "targetId": 22 }` 또는 `{ "targetType": "WOOL", "targetId": 2 }`(내 양에 달린 조언의 보낸 사람)

```json
{
  "success": true,
  "data": { "blockId": 2, "nickname": "민들레목동", "blockedAt": "2026-11-15T18:38:02.7760591" }
}
```

- 201과 200 모두 같은 모양입니다. 둘 다 성공으로 처리하면 됩니다.
- 내 양을 대상으로 하면 400 `BLOCK_SELF`, 대상이 없거나 볼 수 없으면 404.
- 차단하면 서로의 양이 목록·상세에서 보이지 않습니다. 차단 후 풀밭 목록을 다시 불러 주세요.

```json
{ "success": false, "error": { "code": "BLOCK_SELF", "message": "나 자신은 차단할 수 없어요." } }
```

**`GET /blocks`** → 200 (최신순, 없으면 `[]`)

```json
{
  "success": true,
  "data": [ { "blockId": 1, "nickname": "새벽목동", "blockedAt": "2026-11-15T18:38:02.709066" } ]
}
```

**`DELETE /blocks/{blockId}`** → 200 (본문에 `data` 없음)

```json
{ "success": true }
```

- 내가 만든 차단만 해제할 수 있고, 이미 해제했거나 남의 차단이면 404 `COMMON_404` 입니다(목록을 새로고침하세요).

---

### 3.7 위기 CRISIS-02, ME-01

**`GET /help/crisis`** → 200 (**토큰 없이 호출 가능**, 비회원 고정 메뉴에도 사용)

```json
{
  "success": true,
  "data": {
    "title": "전화로 이야기해 볼래요?",
    "message": "지금 마음이 많이 힘드시다면, 이야기를 들어 줄 사람이 있어요. 편한 곳에 연락해 보세요.",
    "contacts": [
      { "name": "자살예방 상담전화", "phone": "109", "description": "힘든 마음을 언제든 이야기할 수 있어요." },
      { "name": "정신건강 위기상담", "phone": "1577-0199", "description": "마음이 많이 힘들 때 전문 상담사와 이야기할 수 있어요." }
    ],
    "emergency": { "phone": "119", "message": "지금 위험하다면 119에 바로 연락해 주세요." },
    "hoursNote": "두 곳 모두 24시간"
  }
}
```

- 고민 올리기 `CRISIS` 응답의 `crisisHelp` 와 **같은 값**입니다. 같은 컴포넌트로 그리면 됩니다. 연락처·문구를 프론트에 하드코딩하지 말고 이 값을 쓰세요.

**`GET /help/crisis/banner`** (로그인 필요)

배너 꺼짐 → 200

```json
{ "success": true, "data": { "active": false, "crisisBannerUntil": null } }
```

배너 켜짐 → 200 (위기 판정 뒤 24시간)

```json
{ "success": true, "data": { "active": true, "crisisBannerUntil": "2026-11-16T18:38:02.086239" } }
```

- **`crisisBannerUntil`은 키가 있고 꺼져 있으면 `null`** 입니다. `active`로 분기하세요.
- 위기 판정은 고민 올리기·케어 대화 어디서든 일어납니다. 앱 시작·홈 진입·나 탭 진입 때 한 번 불러 상단에 상담 안내 배너를 보여 주세요(눌렀을 때 `GET /help/crisis` 화면).

---

### 3.8 케어 마무리 → 목장 추천

CARE-03 마무리 화면의 '목장에 올리기' 버튼을 누르면 빈 고민 올리기 화면으로 이동하고, 목장만 미리 고릅니다. 대화 원문은 자동으로 채우지 않습니다.

**`GET /diaries/{diaryId}/farm-recommendation`** → 200

```json
{ "success": true, "data": { "farmCode": "BURNOUT", "name": "번아웃", "tabName": "번아웃" } }
```

- 응답의 `farmCode`를 올리기 화면의 기본 목장으로 씁니다(코드는 `GET /farms`의 `code`와 같은 값). 사용자가 다른 목장으로 바꿀 수 있습니다.
- 본인의 기록만 조회할 수 있고, 남의 기록·없는 기록은 404 `COMMON_404` 입니다. 실패하면 추천 없이 올리기 화면을 열고 현재 탭(없으면 `CAREER`)을 기본값으로 쓰면 됩니다.

---

### 3.9 알림 NOTI-01

내 고민 양에 조언(털)이 도착하거나, 내가 보낸 조언에 '고마워요'가 눌리면 알림이 갑니다. 웹 푸시와 앱 안 알림(SSE) 두 가지입니다.

**호출 순서(웹 푸시)**

1. 로그인 후 `GET /push/vapid-public-key`.
2. `enabled=false` 면 웹 푸시는 건너뛰고 앱 안 알림(SSE)만 씁니다. `enabled=true` 면 브라우저 권한을 받아 `pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: publicKey })`.
3. 얻은 `subscription.toJSON()` 의 `endpoint`, `keys`를 `POST /push/subscriptions`.
4. 로그아웃할 때 `DELETE /push/subscriptions` 로 지우세요(그래야 다른 계정 알림이 이 브라우저로 오지 않습니다).

**`GET /push/vapid-public-key`** → 200

서버에 VAPID 키가 없을 때(캡처한 응답):

```json
{ "success": true, "data": { "publicKey": null, "enabled": false } }
```

키가 있을 때(코드 기준): `{ "success": true, "data": { "publicKey": "BEl62iUY…(base64url)", "enabled": true } }`

**`POST /push/subscriptions`** → 새 구독 **201**, 같은 endpoint가 이미 있으면 소유자를 지금 사용자로 바꾸고 **200**

```json
{
  "endpoint": "https://fcm.googleapis.com/fcm/send/example-endpoint-id",
  "keys": {
    "p256dh": "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM",
    "auth": "tBHItJI5svbpez7KI4CCXg"
  }
}
```

```json
{ "success": true, "data": { "subscribed": true, "pushEnabled": false } }
```

- `pushEnabled`는 서버에서 웹 푸시가 켜져 있는지입니다. 꺼져 있어도 구독은 저장됩니다.
- `endpoint`는 https이고 알려진 푸시 서비스(FCM, Mozilla, Apple, Windows) 주소만 받습니다. 아니면 400 `PUSH_ENDPOINT_NOT_ALLOWED`: "이 브라우저에서는 알림을 받을 수 없어요. 앱 안 알림으로 알려 드릴게요." → 웹 푸시는 포기하고 앱 안 알림만 쓰세요.
- `endpoint`가 비었거나 `keys`가 없으면 400 `COMMON_400`이지만 message가 `must not be blank`·`must not be null` 같은 영어 기본 문구입니다. 브라우저가 만든 값을 그대로 보내면 생기지 않는 오류이므로 사용자에게 보여 주지 않아도 됩니다.
- 드물게 같은 브라우저가 동시에 두 번 구독하면 409 `COMMON_409`가 날 수 있습니다. 한 번 다시 보내면 200으로 맞춰집니다.

**`DELETE /push/subscriptions`** `{ "endpoint": "..." }` → 200 (없는 endpoint여도 200)

```json
{ "success": true, "data": { "subscribed": false, "pushEnabled": false } }
```

**알림 내용**

웹 푸시 payload 와 앱 안 알림 SSE `data` 는 똑같습니다. 조언 내용·닉네임·다른 사용자 id는 들어 있지 않습니다.

```json
{ "type": "WOOL_ARRIVED", "title": "털이 풍성해졌어요", "body": "내 고민 양에 새 털 한 올이 도착했어요.", "sheepId": 22, "url": "/farm/my/22" }
{ "type": "WOOL_THANKED", "title": "고마워요가 도착했어요", "body": "보내 주신 털 한 올에 고마운 마음이 전해졌어요.", "sheepId": 22, "url": null }
```

- `WOOL_ARRIVED`: 양 주인에게. `url`은 FARM-05 경로(`/farm/my/{sheepId}`, 서버 설정으로 바뀔 수 있음)이니 알림을 누르면 그 경로로 이동하세요.
- `WOOL_THANKED`: 조언을 보낸 사람에게. **`url`이 `null`** 입니다(보낸 사람은 그 양을 상세로 볼 수 없을 수 있어서). 토스트만 보여 주고 이동하지 마세요.
- 조언이 저장(커밋)된 뒤에만 알림이 가며, 알림 실패가 조언 저장에는 영향을 주지 않습니다.

**SSE 이벤트 형식**

`GET /notifications/stream` 은 `Content-Type: text/event-stream` 입니다. 아래는 테스트 서버에서 받은 **실제 텍스트 프레임**입니다.

양 주인의 스트림: 연결해 둔 사이에 두 사람이 조언을 보냈을 때

```
:connected

event:wool-arrived
data:{"type":"WOOL_ARRIVED","title":"털이 풍성해졌어요","body":"내 고민 양에 새 털 한 올이 도착했어요.","sheepId":22,"url":"/farm/my/22"}

event:wool-arrived
data:{"type":"WOOL_ARRIVED","title":"털이 풍성해졌어요","body":"내 고민 양에 새 털 한 올이 도착했어요.","sheepId":22,"url":"/farm/my/22"}

```

조언을 보낸 사람의 스트림: 주인이 고마워요를 눌렀을 때

```
:connected

event:wool-thanked
data:{"type":"WOOL_THANKED","title":"고마워요가 도착했어요","body":"보내 주신 털 한 올에 고마운 마음이 전해졌어요.","sheepId":22,"url":null}

```

| 줄 | 설명 |
| --- | --- |
| `:connected` | 연결 직후 한 번. 주석이라 무시 |
| `:ping` | 약 25초마다. 프록시·브라우저가 연결을 끊지 않게 하는 주석이라 무시(실제 캡처에서는 나오지 않았고 코드 기준) |
| `event:wool-arrived` / `event:wool-thanked` | `data:` 한 줄에 위 JSON |

- `event:` 와 `data:` 뒤에 **공백이 없습니다**(`event:wool-arrived`). 파서는 콜론 뒤 공백이 있어도 없어도 되게 만드세요.
- 이벤트 이름과 `data.type` 대응: `wool-arrived` ↔ `WOOL_ARRIVED`, `wool-thanked` ↔ `WOOL_THANKED`.
- 읽는 방법과 재연결 주의는 [7장](#7-sse-알림-스트림을-fetch-로-읽기)에 있습니다.

---

### 3.10 공통 오류

**토큰 없음·만료 → 401**

```json
{ "success": false, "error": { "code": "COMMON_401", "message": "로그인이 필요해요." } }
```

공통 클라이언트(`src/api/client.ts`)에서 401을 받으면 토큰을 지우고 로그인 화면으로 보내세요. 단 `POST /auth/login` 의 401(`ACCOUNT_LOGIN_FAILED`)은 로그인 실패 안내이므로 구분합니다.

**입력 검증 → 400 `COMMON_400`**

- 서비스 로직에서 거른 경우 message가 상황별 문구입니다: `고민은 1자에서 200자 사이로 적어 주세요.`, `조언은 1자에서 100자 사이로 적어 주세요.`, `고민을 올릴 목장을 하나 골라 주세요.`
- JSON 형식 오류, 모르는 enum 값, 숫자 파라미터 오류는 `입력한 내용을 한 번 더 확인해 주세요.`
- Bean Validation(`@Valid`) 오류는 message가 첫 번째 필드의 검증 문구입니다(예: 가입 화면은 한국어 문구, 푸시 구독은 영어 기본 문구).

```json
{ "success": false, "error": { "code": "COMMON_400", "message": "입력한 내용을 한 번 더 확인해 주세요." } }
```

**기타**

```json
{ "success": false, "error": { "code": "COMMON_405", "message": "지원하지 않는 요청이에요." } }
```

예기치 못한 서버 오류는 500 `COMMON_500` "잠시 문제가 생겼어요. 조금 뒤에 다시 시도해 주세요." 입니다.

---

## 4. enum 값 전체 표

모든 enum 값은 **대문자 문자열**입니다. 요청에 모르는 값을 보내면 400 `COMMON_400` 입니다.

**목장 코드 `farmCode`**

| 값 | name | tabName |
| --- | --- | --- |
| `CAREER` | 진로·취업 | 진로 |
| `RELATIONSHIP` | 관계 | 관계 |
| `BURNOUT` | 번아웃 | 번아웃 |
| `SELF` | 나 자신 | 나 |
| `ALL` | (전체 탭, 보기 전용. 경로에서는 소문자 `all`도 됨. **올리기에는 쓸 수 없음**) | 전체 |

**고민 올리기 결과 `WorryPostStatus`** (`POST /farms/{code}/sheep` 의 `data.status`)

| 값 | HTTP | 뜻 |
| --- | --- | --- |
| `CREATED` | 201 | 새 양이 태어남 |
| `CRISIS` | 200 | 양을 만들지 않고 상담 연결 안내 |
| `PII_REJECTED` | 200 | 개인정보가 있어 저장하지 않음 |

**양 상태 `WorrySheepStatus`** (상세의 `status`, 목록의 `status`)

| 값 | 뜻 |
| --- | --- |
| `ACTIVE` | 목장에 있음 |
| `GRADUATED` | 졸업(보관함) |
| `HIDDEN` | 신고로 숨김(`status` 값으로는 내려오지 않고 내 고민 목록에 `hidden=true` 로만 표시됨. 상세는 404) |

**내 고민 목록 `status`**: 요청 `active`/`graduated`, 응답 `ACTIVE`/`GRADUATED`.

**조언 보내기 결과 `WoolSendStatus`** (`POST /worry-sheep/{id}/wools` 의 `data.status`)

| 값 | HTTP | 뜻 |
| --- | --- | --- |
| `SENT` | 201 | 저장하고 털 한 올을 보냄 |
| `SUGGEST` | 200 | 어조 주의. 저장하지 않고 바꿀 문장 제안 |
| `BLOCKED` | 200 | 보낼 수 없음. 저장하지 않음 |

**조언 다시 확인 분류 `category`** (`SUGGEST`·`BLOCKED` 응답)

| category | categoryLabel | message(원문) | status |
| --- | --- | --- | --- |
| `BANNED_WORD` | 금칙어 | 마음을 다치게 할 수 있는 표현이 들어 있어요. 조금 다르게 써 볼까요? | `BLOCKED` (`categoryLabel` 없음) |
| `PII` | (없음) | 전화번호나 계정 아이디 같은 연락처는 남기지 않도록 해 주세요. 그 부분만 빼고 다시 적어 주시면 좋아요. | `BLOCKED` (`categoryLabel` 없음) |
| `BLAME` | 비난 | 탓하는 말처럼 들릴 수 있어요. | `SUGGEST` |
| `LECTURE` | 다그침·훈계 | 다그치는 말처럼 들릴 수 있어요. | `SUGGEST` |
| `COMPARE` | 비교 | 다른 사람과 견주는 말처럼 들릴 수 있어요. | `SUGGEST` |
| `MOCK` | 조롱 | 놀리는 말처럼 들릴 수 있어요. | `SUGGEST` |
| `DIAGNOSIS` | 섣부른 진단 | 마음 상태를 단정하는 말처럼 들릴 수 있어요. | `SUGGEST` |
| `SOLICIT` | 종교·상품 권유 | 무언가를 권하는 말처럼 들릴 수 있어요. | `SUGGEST` |

**신고 사유 `ReportReason`** (`POST /reports` 의 `reason`)

| 값 | 화면 문구(SAFE-01) |
| --- | --- |
| `ABUSIVE` | 악성 표현 |
| `PERSONAL_INFO` | 개인정보 |
| `SPAM` | 홍보·스팸 |
| `LOOKS_DANGEROUS` | 글쓴이가 위험해 보여요 |
| `ETC` | 기타 |

**신고 대상 `targetType`** (`POST /reports`: `WORRY_SHEEP`, `WOOL` / `POST /blocks`: `WORRY_SHEEP`, `WOOL`)

| 값 | targetId | 신고 | 차단 |
| --- | --- | --- | --- |
| `WORRY_SHEEP` | 양 id | 볼 수 있는 다른 목동의 양 | 그 양의 주인 |
| `WOOL` | 조언 id | 내 양에 달린 조언 | 그 조언을 보낸 사람 |

**알림 `NotificationType`**

| `type` | SSE 이벤트 이름 | 받는 사람 | `url` |
| --- | --- | --- | --- |
| `WOOL_ARRIVED` | `wool-arrived` | 양 주인 | `/farm/my/{sheepId}` |
| `WOOL_THANKED` | `wool-thanked` | 조언을 보낸 사람 | `null` |

**양 위험도(보호 양)** `riskLevel`(`SAFE`/`CAUTION`/`CRISIS`)은 **응답에 내려오지 않습니다.** 프론트는 몰라도 됩니다.

---

## 5. 오류 코드 표

HTTP 상태, `error.code`, 문구는 모두 서버 `ErrorCode` 기준입니다. 문구는 그대로 화면에 써도 됩니다(`COMMON_400` 제외).

| HTTP | code | message(원문) | 어디서 | 프론트 처리 제안 |
| --- | --- | --- | --- | --- |
| 400 | `COMMON_400` | 입력한 내용을 한 번 더 확인해 주세요. (또는 상황별 문구) | 모든 입력 | 입력 화면에 `message` 표시. JSON·enum 오류는 개발 버그이니 콘솔에 남김 |
| 401 | `COMMON_401` | 로그인이 필요해요. | 전체 | 토큰 삭제 후 로그인 화면 |
| 403 | `WOOL_OWN_SHEEP` | 내 고민 양에는 털을 보낼 수 없어요. 다른 목동의 양을 둘러봐 주세요. | 조언 보내기 | 내 양에서는 입력창을 처음부터 숨기므로(`mine`) 거의 안 남. 오면 목장으로 |
| 404 | `COMMON_404` | 찾으시는 내용이 없어요. | 양·조언·차단·신고 대상·기록 | 안내 후 목록으로. **존재 여부를 숨기려는 통일 응답**(8장) |
| 404 | `FARM_NOT_FOUND` | 찾으시는 목장이 없어요. 목장 목록에서 다시 골라 주세요. | 목장 코드 | 목장 목록으로 |
| 405 | `COMMON_405` | 지원하지 않는 요청이에요. | 전체 | 개발 버그 |
| 409 | `WOOL_ALREADY_SENT` | 이 양에게는 이미 털 한 올을 보냈어요. | 조언 보내기 | `alreadySent` 상태로 바꾸고 목록으로 |
| 409 | `WORRY_SHEEP_GRADUATED` | 이 양은 방금 목장을 떠났어요. | 조언 보내기 | 안내 후 풀밭 새로고침 |
| 409 | `THANKS_ALREADY_SENT` | 이 조언에는 이미 고마운 마음을 전했어요. | 고마워요 | 하트를 눌린 상태로 맞춤(오류 표시 없이) |
| 409 | `REPORT_DUPLICATED` | 이미 신고해 주신 내용이에요. 살펴볼게요, 고마워요. | 신고 | SAFE-02 완료 화면처럼 처리해도 됨 |
| 409 | `COMMON_409` | 이미 처리된 요청이에요. | 푸시 구독 동시 요청 | 한 번 더 시도 |
| 400 | `SAFETY_PII_DETECTED` | 전화번호나 계정 아이디 같은 연락처는 남기지 않도록 해 주세요. 그 부분만 빼고 다시 적어 주시면 좋아요. | (고민·조언은 이 코드로 오지 않고 200 + `status`) | 지금은 B 영역 API에서 이 코드가 내려오지 않음. 방어적으로 같은 문구 처리 |
| 400 | `BLOCK_SELF` | 나 자신은 차단할 수 없어요. | 차단 | 거의 안 남. 안내만 |
| 400 | `REPORT_OWN_CONTENT` | 내가 쓴 글은 신고할 수 없어요. | 신고 | 내 글에는 ⋯ 신고 메뉴를 숨기므로 거의 안 남 |
| 400 | `PUSH_ENDPOINT_NOT_ALLOWED` | 이 브라우저에서는 알림을 받을 수 없어요. 앱 안 알림으로 알려 드릴게요. | 푸시 구독 | 웹 푸시 포기, 앱 안 알림만 사용 |
| 429 | `WORRY_DAILY_LIMIT_EXCEEDED` | 오늘은 고민을 충분히 들려주셨어요. 내일 다시 들려주세요. | 고민 올리기 | 안내 모달. 입력 내용은 버리지 말고 유지 |
| 429 | `COMMON_429` | 오늘은 충분히 이야기해 주셨어요. 내일 다시 들려주세요. | 마음 기록(A) | 이 B 영역 API에서는 안 옴 |
| 500 | `COMMON_500` | 잠시 문제가 생겼어요. 조금 뒤에 다시 시도해 주세요. | 전체 | 재시도 안내 |

계정 관련(`ACCOUNT_*`, 423 잠금, 닉네임)과 나의 양 꾸미기(`SHEEP_*`) 코드는 A 영역입니다(README 참고).

---

## 6. TypeScript 타입

실제 JSON 필드와 일치합니다. 시각은 서버의 시간대 없는 서울 시각 문자열입니다(`ServerTime`).

```ts
// ---- 공통 ----
export type ServerTime = string; // "2026-11-15T18:38:02.143905" (시간대 없음, Asia/Seoul)

export interface ApiError {
  code: string;
  message: string;
}

/** 성공이면 data, 실패면 error. 값이 없는 키는 아예 빠집니다. */
export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: ApiError;
}

export type FarmCode = 'CAREER' | 'RELATIONSHIP' | 'BURNOUT' | 'SELF';
export type FarmTab = FarmCode | 'ALL'; // 응답의 farmCode (전체 탭은 "ALL")
export type SheepStage = 1 | 2 | 3 | 4 | 5;
export type StageName = '털 깎은 양' | '보송한 양' | '포근한 양' | '몽글몽글 양' | '구름 양';

export interface FarmInfo {
  code: FarmCode;
  name: string;
  tabName: string;
}

// ---- 목장 (GET /farms) ----
export interface Farm extends FarmInfo {
  description: string;
  waitingCount: number;
}

// ---- 풀밭 (GET /farms/{farmCode}/sheep?page=) ----
export interface FarmSheepItem {
  sheepId: number;
  farmCode: FarmCode;
  stage: SheepStage;
  stageName: StageName;
  preview: string; // 앞 40자, 넘으면 끝에 "…"
  nickname: string;
  createdAt: ServerTime;
  alreadySent: boolean;
}

export interface FarmSheepPage {
  farmCode: FarmTab;
  page: number;
  size: number;
  hasNext: boolean;
  sheep: FarmSheepItem[];
}

// ---- 상담 연결 (GET /help/crisis, 고민 올리기 CRISIS 의 crisisHelp) ----
export interface CrisisHelp {
  title: string;
  message: string;
  contacts: { name: string; phone: string; description: string }[];
  emergency: { phone: string; message: string };
  hoursNote: string;
}

export interface CrisisBanner {
  active: boolean;
  crisisBannerUntil: ServerTime | null; // 키는 항상 있고 꺼져 있으면 null
}

// ---- 고민 올리기 (POST /farms/{farmCode}/sheep) ----
export type WorryPostStatus = 'CREATED' | 'CRISIS' | 'PII_REJECTED';

/** status 에 따라 아래 선택 필드가 있거나 없습니다(키 자체가 빠짐). */
export type WorryPostResponse =
  | {
      status: 'CREATED'; // 201
      sheepId: number;
      farm: FarmInfo;
      stage: SheepStage;
      stageName: StageName;
      content: string;
    }
  | { status: 'CRISIS'; crisisHelp: CrisisHelp } // 200
  | { status: 'PII_REJECTED'; message: string }; // 200

// ---- 고민 양 상세 (GET /worry-sheep/{id}) ----
export type WorrySheepStatus = 'ACTIVE' | 'GRADUATED'; // HIDDEN 은 상세가 404 라서 내려오지 않음

export interface WoolItem {
  woolId: number;
  content: string;
  senderNickname: string;
  createdAt: ServerTime;
  thanked: boolean;
}

export interface WorrySheepDetail {
  sheepId: number;
  farm: FarmInfo;
  content: string;
  stage: SheepStage;
  stageName: StageName;
  nickname: string;
  createdAt: ServerTime;
  status: WorrySheepStatus; // 타인에게는 ACTIVE 만, 주인에게는 ACTIVE 또는 GRADUATED
  mine: boolean;
  canSendWool: boolean;
  alreadySent: boolean;
  wools?: WoolItem[]; // 주인(mine=true)에게만. 타인에게는 키가 없음
}

// ---- 조언 보내기 (POST /worry-sheep/{id}/wools) ----
export type WoolSendStatus = 'SENT' | 'SUGGEST' | 'BLOCKED';
export type AdviceCategory =
  | 'BANNED_WORD' | 'PII' // BLOCKED
  | 'BLAME' | 'LECTURE' | 'COMPARE' | 'MOCK' | 'DIAGNOSIS' | 'SOLICIT'; // SUGGEST

export interface WoolSendRequest {
  content: string;
  sendAsIs?: boolean; // '그대로 보내기' 일 때 true (생략하면 false)
}

export type WoolSendResponse =
  | {
      status: 'SENT'; // 201
      woolId: number;
      sheepStage: SheepStage;
      sheepStageName: StageName;
      stageUp: boolean;
      graduated: boolean;
      yarnAdded: number;
      yarnCount: number;
    }
  | {
      status: 'SUGGEST'; // 200
      category: Exclude<AdviceCategory, 'BANNED_WORD' | 'PII'>;
      categoryLabel: string;
      message: string;
      suggestion?: string; // 바꿀 문장을 만들지 못하면 키가 없음
    }
  | {
      status: 'BLOCKED'; // 200 (categoryLabel, suggestion 없음)
      category: 'BANNED_WORD' | 'PII';
      message: string;
    };

// ---- 내 고민 (GET /worry-sheep/me?status=active|graduated) ----
export interface MyWorryItem {
  sheepId: number;
  farm: FarmInfo;
  preview: string;
  stage: SheepStage;
  stageName: StageName;
  createdAt: ServerTime;
  graduatedAt: ServerTime | null; // 키는 항상 있고 진행 중이면 null
  newWoolCount: number;
  hidden: boolean; // true 면 상세가 404 (진행 중 탭에만)
}

export interface MyWorryList {
  status: 'ACTIVE' | 'GRADUATED';
  sheep: MyWorryItem[];
}

// ---- 졸업 (POST /worry-sheep/{id}/graduate) ----
export interface WorryGraduate {
  sheepId: number;
  status: 'GRADUATED';
  graduatedAt: ServerTime;
  stage: SheepStage;
  stageName: StageName;
}

// ---- 고마워요 (POST /wools/{id}/thanks) ----
export interface Thanks {
  woolId: number;
  thanked: boolean; // 항상 true
}

// ---- 신고·차단 ----
export type ReportReason = 'ABUSIVE' | 'PERSONAL_INFO' | 'SPAM' | 'LOOKS_DANGEROUS' | 'ETC';
export type TargetType = 'WORRY_SHEEP' | 'WOOL';

export interface ReportRequest {
  targetType: TargetType;
  targetId: number;
  reason: ReportReason;
  blockUser?: boolean; // 생략하면 false
}

export interface ReportResult {
  reportId: number;
  hidden: boolean;
  blocked: boolean;
}

export interface BlockRequest {
  targetType: TargetType;
  targetId: number;
}

export interface Block {
  blockId: number;
  nickname: string;
  blockedAt: ServerTime;
}
// GET /blocks → ApiResponse<Block[]>, DELETE /blocks/{id} → ApiResponse<void> ({ success: true })

// ---- 케어 마무리 → 목장 추천 (GET /diaries/{id}/farm-recommendation) ----
export interface FarmRecommendation {
  farmCode: FarmCode;
  name: string;
  tabName: string;
}

// ---- 알림 ----
export interface VapidPublicKey {
  publicKey: string | null; // 키가 없으면 null
  enabled: boolean;
}

export interface PushSubscribeRequest {
  endpoint: string;
  keys: { p256dh: string; auth: string };
}

export interface PushSubscriptionState {
  subscribed: boolean;
  pushEnabled: boolean;
}

export type NotificationType = 'WOOL_ARRIVED' | 'WOOL_THANKED';

/** SSE data 와 웹 푸시 payload 공통 */
export interface NotificationPayload {
  type: NotificationType;
  title: string;
  body: string;
  sheepId: number;
  url: string | null; // WOOL_THANKED 는 null
}

/** SSE 이벤트 이름 → type */
export type NotificationEventName = 'wool-arrived' | 'wool-thanked';
```

`ApiResponse<T>` 로 감싸 쓸 때 상태 코드까지 본다면(`201`/`200` 구분이 필요하면) 클라이언트가 `status`를 같이 돌려주게 하고, 분기는 `data.status` 로 하세요. 응답 타입을 좁히는 헬퍼 예:

```ts
const res = await client.post<WoolSendResponse>(`/worry-sheep/${id}/wools`, { content, sendAsIs });
switch (res.data.status) {
  case 'SENT':    /* FARM-04c */ break;
  case 'SUGGEST': /* FARM-04b, res.data.suggestion 가 없을 수 있음 */ break;
  case 'BLOCKED': /* 다시 쓰기 */ break;
}
```

---

## 7. SSE 알림 스트림을 fetch 로 읽기

`EventSource`는 `Authorization` 헤더를 보낼 수 없어서 **`fetch` 스트림**으로 읽습니다(케어 대화 SSE와 같은 방식. 공통 `src/api/sse.ts` 의 파서를 쓸 수 있으면 그대로 쓰세요). 아래는 알림용 최소 예시입니다.

```ts
export async function readNotifications(
  baseUrl: string,
  token: string,
  onEvent: (name: string, data: NotificationPayload) => void,
  signal: AbortSignal,
): Promise<void> {
  const res = await fetch(`${baseUrl}/notifications/stream`, {
    headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
    signal,
  });
  if (res.status === 401) throw new Error('UNAUTHORIZED'); // 재연결하지 말고 로그인으로
  if (!res.ok || !res.body) throw new Error(`HTTP_${res.status}`);

  const reader = res.body.pipeThrough(new TextDecoderStream()).getReader();
  let buffer = '';
  for (;;) {
    const { value, done } = await reader.read();
    if (done) return; // 서버가 연결을 끝냄(30분 뒤 등) → 호출한 쪽에서 다시 연결
    buffer += value.replace(/\r\n/g, '\n');
    let end: number;
    while ((end = buffer.indexOf('\n\n')) >= 0) {
      const frame = buffer.slice(0, end);
      buffer = buffer.slice(end + 2);
      let name = 'message';
      let data = '';
      for (const line of frame.split('\n')) {
        if (line.startsWith(':')) continue; // :connected, :ping 주석
        if (line.startsWith('event:')) name = line.slice(6).trim();
        else if (line.startsWith('data:')) data += line.slice(5).trim();
      }
      if (data) onEvent(name, JSON.parse(data) as NotificationPayload);
    }
  }
}
```

**재연결 주의**

- **연결은 30분 뒤 서버가 끝냅니다.** 오류가 아니라 정상 종료이므로 `done`이 오면 바로(또는 짧은 지연 뒤) 다시 연결하세요. 연결하는 동안 놓친 알림은 다시 보내 주지 않습니다. 재연결 직후 `GET /worry-sheep/me` 로 `newWoolCount`를 갱신하면 놓친 것을 따라잡을 수 있습니다.
- **사용자당 동시 연결은 5개**입니다. 6번째 연결이 열리면 **가장 오래된 연결이 서버에서 닫힙니다**(정상 종료처럼 `done`이 옴). 탭이 여러 개인 상태에서 각 탭이 닫히자마자 곧바로 재연결하면 서로 연결을 빼앗는 일이 반복될 수 있으니, 재연결에는 지연(예: 1초에서 시작해 최대 30초까지 지수 증가 + 무작위 흔들림)을 두고, 보이지 않는 탭(`document.visibilityState !== 'visible'`)은 연결하지 않는 것을 권장합니다.
- 로그아웃하면 `AbortController.abort()` 로 연결을 끊고 **재연결하지 않습니다.** 로그인한 사용자가 바뀌면 새 토큰으로 다시 연결합니다.
- 401이면 재연결하지 말고 로그인 화면으로 보냅니다(이 경우 응답은 스트림이 아니라 JSON입니다).
- 웹 푸시도 켜져 있고 앱이 열려 있으면 같은 알림이 두 번 올 수 있습니다. 서비스워커가 `type`으로 앱 안 토스트와의 중복 표시를 판단합니다.
- 앱 안 알림은 서버 한 대의 메모리 기준입니다(서버를 여러 대로 늘리면 다른 서버에 붙은 탭에는 가지 않음). 앱 안 알림은 보조 수단이고, 목록의 기준은 `newWoolCount`입니다.
- 이벤트 이름은 `wool-arrived`, `wool-thanked` 두 가지뿐입니다. 모르는 이름이 오면 무시하세요(나중에 늘어날 수 있음).

---

## 8. 자주 헷갈리는 점

1. **'볼 수 없는 양'은 모두 404 로 통일됩니다.** 숨김, 졸업한 타인의 양, 차단 관계, 조언 이력이 모자란 사용자에게 보호 양, 없는 id가 전부 `COMMON_404`(찾으시는 내용이 없어요.) 입니다. 존재 여부를 흘리지 않으려는 의도이니 404의 이유를 구분하려 하지 마세요. 상세·조언 보내기·신고·차단이 같은 기준을 씁니다. 예외로 조언 보내기에서는 졸업한 양이 404가 아니라 409 `WORRY_SHEEP_GRADUATED` 로 옵니다.
2. **LLM이 꺼져 있으면 새 고민이 목록에 안 보입니다.** 위험도 분류가 실패하면 서버는 위기가 아닌 고민을 보호 양(주의)으로 등록합니다. 보호 양은 조언을 5회 이상 보낸 사용자에게만 보입니다. 응답은 `CREATED` 로 똑같아 프론트에서는 구분할 수 없습니다. 시연·연동 환경에서 "올렸는데 풀밭에 안 보여요"가 생기면 서버의 LLM 키를 먼저 확인해 달라고 백엔드에 요청하세요.
3. **내 양이 신고로 숨겨지면(`hidden: true`) 상세가 404입니다.** 진행 중 목록에는 계속 나오지만 눌러도 상세로 갈 수 없고, 주인에게도 숨깁니다.
4. **조언 100자, 고민 200자는 앞뒤 공백을 뺀 글자 수**(코드포인트)입니다. 이모지도 1자입니다. 입력 글자 수 표시(`0/100`)는 `[...text.trim()].length` 로 맞추세요. 서버 오류 문구는 "1자에서 100자 사이"입니다.
5. **`SUGGEST` 와 `BLOCKED` 는 HTTP 200 입니다.** `res.ok` 만 보고 "보냈다"고 처리하면 저장되지 않은 조언을 보낸 것으로 착각합니다. 반드시 `data.status === 'SENT'` 를 확인하세요. `PII_REJECTED`, `CRISIS` 도 마찬가지입니다.
6. **`wools`, `suggestion`, `crisisHelp`, `message` 등은 키가 아예 없을 수 있습니다**(`undefined`). 반대로 `graduatedAt`, `crisisBannerUntil`, `publicKey`, `url`(고마워요 알림)은 키가 있고 `null` 입니다. 타입 정의의 `?` 와 `| null` 을 그대로 지켜 주세요.
7. **받은 조언은 주인에게만 보입니다.** 다른 목동의 양에는 `wools` 가 없고 조언 수·털 개수도 내려오지 않습니다. 단계(`stage`)만 압니다.
8. **`newWoolCount` 는 주인이 상세를 연 순간 초기화됩니다.** 목록 → 상세 → 뒤로 가기 후 목록을 새로고침하면 0이 됩니다. 알림을 눌러 FARM-05로 바로 들어가도 마찬가지입니다.
9. **내가 조언을 보낸 양이 졸업하면 상세가 404가 됩니다**(타인에게는 졸업 양이 안 보임). 풀밭 목록에서도 빠집니다. 보낸 조언을 다시 보는 화면은 없습니다.
10. **차단은 양쪽 모두 숨깁니다.** 내가 차단한 사용자뿐 아니라 그 사용자의 풀밭에서도 내 양이 안 보입니다. 차단·해제 뒤에는 풀밭을 새로고침하세요.
11. **하루 고민 한도(3마리)는 `CREATED` 일 때만 셉니다.** `CRISIS`/`PII_REJECTED` 는 소비하지 않습니다. 429 화면에서 사용자가 쓴 글을 지우지 마세요(내일 다시 올릴 수 있게 남겨 두는 편이 좋습니다).
12. **어조 판정(`SUGGEST`)은 AI가 하므로** 바꿀 문장(`suggestion`)이 빠질 수 있고, AI가 응답하지 않으면 `SUGGEST` 없이 금칙어 검사만 거쳐 `SENT` 가 됩니다. 화면은 `suggestion`이 있는 경우와 없는 경우를 모두 그려야 합니다.
13. **시각은 시간대가 없는 서울 시각**입니다. `new Date(s)` 를 그대로 쓰지 말고 `parseServerTime` 을 쓰세요(2장).
14. **응답 예시의 id·시각·토큰은 매번 다릅니다.** 같은 id를 하드코딩하지 말고, 연동용 데이터는 `scripts/seed_demo.py` 로 만드세요.
