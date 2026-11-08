# 배포 (CI/CD)

## 흐름

| 워크플로 | 언제 | 하는 일 |
| --- | --- | --- |
| `CI` (`.github/workflows/ci.yml`) | 모든 PR, `main` 푸시 | `./gradlew build` (테스트 포함). 실패하면 테스트 리포트를 아티팩트로 올림 |
| `CD` (`.github/workflows/cd.yml`) | `main` 푸시, 수동 실행 | Docker 이미지를 빌드해 GHCR(`ghcr.io/2026ganzi-thon/teolhanol-be`)에 올리고, EC2에서 새 이미지로 컨테이너 교체 |

EC2 배포 단계는 저장소 변수 `DEPLOY_ENABLED`가 `true`일 때만 실행됩니다. EC2 준비 전에는 이미지 빌드·푸시까지만 돕니다.

## 1. EC2 준비 (한 번만)

1. Ubuntu 24.04 인스턴스 생성 (t3.small 이상 권장, MySQL을 같이 띄우므로 메모리 2GB 이상)
2. 보안 그룹 인바운드: `22`(SSH, 가능하면 팀 IP만), `80`(HTTP)
3. Docker 설치

   ```bash
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker $USER   # 다시 로그인해야 적용됩니다
   ```

4. 배포 폴더와 운영 환경변수

   ```bash
   mkdir -p ~/teolhanol && cd ~/teolhanol
   # 저장소의 deploy/.env.prod.example 내용을 복사해 값을 채웁니다
   nano .env
   chmod 600 .env
   ```

   `APP_CRYPTO_KEY`는 `openssl rand -base64 32`로 만들고 따로 안전하게 보관하세요. 키가 바뀌면 기존 일기를 읽을 수 없습니다.

## 2. GitHub 설정

Settings → Secrets and variables → Actions

| 종류 | 이름 | 값 |
| --- | --- | --- |
| Secret | `EC2_HOST` | EC2 퍼블릭 IP 또는 도메인 |
| Secret | `EC2_USER` | `ubuntu` |
| Secret | `EC2_SSH_KEY` | EC2 접속용 private key 전체 (`-----BEGIN ...` 포함) |
| Variable | `DEPLOY_ENABLED` | `true` (EC2 준비가 끝난 뒤) |

Settings → Environments에 `production` 환경이 자동으로 생깁니다. 필요하면 배포 승인자를 지정할 수 있습니다.

운영 값(DB 비밀번호, 암호화 키, API 키)은 GitHub에 올리지 않고 EC2의 `.env`에만 둡니다.

## 3. 배포 확인

```bash
curl http://<EC2_HOST>/health
# {"success":true,"data":"ok"}
```

Swagger: `http://<EC2_HOST>/swagger-ui.html` (`.env`의 `SWAGGER_ENABLED=false`로 끌 수 있음)

## 운영 메모

- 스키마: 운영도 `ddl-auto: update`로 엔티티 기준으로 맞춥니다 (`JPA_DDL_AUTO`로 변경 가능).
- 같은 EC2에 MySQL 컨테이너를 함께 띄웁니다. RDS를 쓰려면 `.env`의 `DB_URL`을 RDS 주소로 채우고 compose의 `mysql` 서비스를 지우세요.
- 프론트엔드가 HTTPS로 배포되면 브라우저가 HTTP API 호출을 막습니다(mixed content). 그 경우 도메인을 연결하고 앞단에 HTTPS(예: Caddy, 로드밸런서)를 붙여야 합니다.
- 로그 보기: `cd ~/teolhanol && docker compose -f docker-compose.prod.yml logs -f app`
- 이전 버전으로 되돌리기: `IMAGE_TAG=sha-<커밋 7자리> docker compose -f docker-compose.prod.yml up -d`
- 대회 규정상 11/20 23:59 이후 커밋이 있으면 실격입니다. 자동 커밋·자동 PR을 만드는 도구(Dependabot 등)는 쓰지 않습니다. CI/CD는 커밋을 만들지 않습니다.
