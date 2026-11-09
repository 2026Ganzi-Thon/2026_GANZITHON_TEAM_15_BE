# 털 한 올 — Backend

2026 간지톤 15팀 '털 한 올' 백엔드 서버입니다.

## 기술 스택

- Java 21, Spring Boot 4.1, Gradle
- Spring Web MVC, Spring Data JPA, Spring Security, Bean Validation
- MySQL 8.4 (로컬은 Docker), 테스트는 H2(MySQL 모드)
- springdoc-openapi (Swagger UI)
- Claude API (`anthropic-java` SDK, 기본 모델 `claude-opus-5-5`)

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
| `prod` | 운영 | 환경변수 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `APP_CRYPTO_KEY`, `APP_JWT_SECRET`, `ANTHROPIC_API_KEY`, `ddl-auto: validate` |

## 패키지 구조

```
com.ganzithon.teolhanol
├── global          # 공통: 응답 형식, 예외 처리, 설정
│   ├── config
│   ├── exception
│   └── response
└── domain          # 기능별 도메인 (auth, diary, care, sheep, farm, wool, safety, report)
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

## 시드 데이터와 정책 수치

- 서버가 시작될 때 `src/main/resources/seed/`의 목장 4곳과 안전 패턴(위기 키워드, 금칙어, AI 응답 금지 패턴, 개인정보 정규식)을 넣습니다. 이미 있는 데이터는 건너뜁니다.
- 패턴을 추가하려면 `seed/safety-patterns.json`에 넣고 서버를 다시 시작하면 됩니다.
- 기능명세서 5장 수치는 `application.yml`의 `app.policy`에 있고, 코드에서는 `PolicyProperties`로 읽습니다.
- 양 단계 기준표는 `SheepStage` (`ofMySheep`, `ofWorrySheep`)를 사용합니다.

## LLM (Claude API)

위험도 분류, 케어 대화, 요약, 조언 필터는 `global/llm`의 `LlmClient`를 통해 호출합니다.

| 메서드 | 용도 |
| --- | --- |
| `complete(request)` | 응답 전체를 한 번에 받기 |
| `completeJson(request, Type.class)` | 응답을 JSON 스키마에 맞춰 객체로 받기 (분류 결과 등) |
| `stream(request, onDelta)` | 조각 단위로 받기 (케어 대화 SSE) |

- 요청은 `LlmRequest.classification(...)`(생각 깊이 낮음, 재시도 없음, 타임아웃 지정) 또는 `LlmRequest.chat(...)`으로 만듭니다.
- 실패하면 항상 `LlmException`이 나고, `type`(`TIMEOUT`, `UNAVAILABLE`, `REFUSAL`, `INVALID_RESPONSE`, `API_ERROR`)을 보고 대체 로직으로 넘어가면 됩니다.
- 프롬프트는 `src/main/resources/prompts/{이름}.txt`에 두고 `PromptLoader`로 읽습니다. `withSafetyGuide(이름)`은 공통 안전 지침(`common-safety.txt`)을 앞에 붙입니다.
- `ANTHROPIC_API_KEY`가 없으면 LLM 없이 서버가 뜨고 모든 호출이 `UNAVAILABLE`로 실패합니다.
- LLM에는 본문만 보내고 이메일·닉네임은 보내지 않습니다. Claude API는 기본적으로 API 데이터를 모델 학습에 쓰지 않습니다.
- 안전 분류기가 응답을 거절하면 서버가 다른 모델로 다시 시도하는 기능(`app.llm.server-fallback`)을 켜 두었습니다.

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
