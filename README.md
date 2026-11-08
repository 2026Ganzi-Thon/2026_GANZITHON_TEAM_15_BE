# 털 한 올 — Backend

2026 간지톤 15팀 '털 한 올' 백엔드 서버입니다.

## 기술 스택

- Java 21, Spring Boot 4.1, Gradle
- Spring Web MVC, Spring Data JPA, Spring Security, Bean Validation
- MySQL 8.4 (로컬은 Docker), 테스트는 H2(MySQL 모드)
- springdoc-openapi (Swagger UI)

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
| `prod` | 운영 | 환경변수 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `ddl-auto: validate` |

## 패키지 구조

```
com.ganzithon.teolhanol
├── global          # 공통: 응답 형식, 예외 처리, 설정
│   ├── config
│   ├── exception
│   └── response
└── domain          # 기능별 도메인 (auth, diary, care, sheep, farm, wool, safety, report)
```

## 공통 응답 형식

```json
{ "success": true, "data": { } }
{ "success": false, "error": { "code": "COMMON_401", "message": "로그인이 필요해요." } }
```

에러 코드는 `global/exception/ErrorCode`에 모아 둡니다. 안내 문구는 비난·명령 없는 존댓말로 씁니다.

## 협업 규칙

- 이슈 하나당 브랜치 하나: `feat/{이슈번호}-{간단한-설명}` (예: `feat/1-project-setup`)
- PR 본문에 `Closes #이슈번호`
- 기능명세서: `docs/털한올_기능명세서.md`
