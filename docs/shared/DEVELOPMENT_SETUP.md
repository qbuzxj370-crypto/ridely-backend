	# Ridely 개발환경 구성 가이드

> **대상**: git push --force 팀원 3명 (Windows + Linux)
> **목적**: 같은 환경에서 같은 결과물이 나오도록 보장
> **소요시간**: 약 2~3시간 (TAAS API 승인 대기 제외)

---

## 📋 목차

1. [사전 요구사항](#1-사전-요구사항)
2. [저장소 클론·초기 설정](#2-저장소-클론·초기-설정)
3. [PostgreSQL 빠른 설치 (Docker 활용)](#3-postgresql-빠른-설치-docker-활용)
4. [DB 스키마 적용](#4-db-스키마-적용)
5. [API 키 발급·환경변수 설정](#5-api-키-발급·환경변수-설정)
6. [Spring Boot 실행 + 헬스체크](#6-spring-boot-실행--헬스체크)
7. [IDE 설정](#7-ide-설정)
8. [개발 워크플로우](#8-개발-워크플로우)
9. [문제 해결 (Troubleshooting)](#9-문제-해결-troubleshooting)

---

## 1. 사전 요구사항

### 1.1 설치할 도구 (필수)

| 도구 | 버전 | 용도 |
|---|---|---|
| **JDK** | 25 LTS | Spring Boot 백엔드 실행 |
| **Git** | 2.40+ | 형상 관리 |
| **Docker Desktop / Docker Engine** | 최신 | PostgreSQL을 한 번에 설치·실행하는 도구 |
| **IntelliJ IDEA** 또는 **VS Code** | 최신 | IDE (어느 쪽이든) |

### 1.2 설치 가이드

#### Windows

**JDK 25 LTS 설치**
1. https://adoptium.net/temurin/releases/?version=25 접속
2. **JDK 25 LTS** + **Windows x64** + **MSI Installer** 다운로드
3. 설치 시 다음 옵션 모두 체크:
   - [x] Set JAVA_HOME variable
   - [x] Add to PATH
4. 확인:
   ```cmd
   java --version
   ```
   출력 예시: `openjdk 25.x.x 2025-09-16 LTS`

**Git 설치**
1. https://git-scm.com/download/win 에서 다운로드 → 설치
2. 설치 시 **"Git from the command line and also from 3rd-party software"** 선택
3. 줄바꿈 옵션: **"Checkout as-is, commit Unix-style line endings"** (모든 팀원 동일)
4. 확인:
   ```cmd
   git --version
   ```

**Docker Desktop 설치** (PostgreSQL 빠른 실행용)
1. https://www.docker.com/products/docker-desktop/ 다운로드
2. 설치 후 재부팅 (WSL 2 활성화 필요할 수 있음)
3. Docker Desktop 실행 → 우측 하단 고래 아이콘 초록색 확인
4. 확인:
   ```cmd
   docker --version
   docker compose version
   ```

> 본 가이드에서 Docker는 PostgreSQL + PostGIS + pgvector를 한 번에 띄우는 용도로만 사용한다. Docker를 깊이 학습할 필요는 없으며, 명령 4~5개만 익히면 충분하다.

#### Linux (Ubuntu 22.04+ 기준)

**JDK 25 LTS 설치**
```bash
# SDKMAN 사용 권장 (여러 JDK 버전 관리 쉬움)
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

sdk install java 25-tem
sdk default java 25-tem

java --version
```

또는 apt:
```bash
# Adoptium 저장소 추가
sudo apt update
sudo apt install -y wget apt-transport-https
wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg
echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(. /etc/os-release && echo $VERSION_CODENAME) main" | sudo tee /etc/apt/sources.list.d/adoptium.list

sudo apt update
sudo apt install -y temurin-25-jdk
java --version
```

**Git 설치**
```bash
sudo apt install -y git
git --version
```

**Docker 설치** (PostgreSQL 빠른 실행용)
```bash
# Docker Engine + Compose 플러그인
sudo apt update
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# sudo 없이 실행 가능하게
sudo usermod -aG docker $USER
# 로그아웃 후 재로그인

docker --version
docker compose version
```

### 1.3 설치 확인 체크리스트

```bash
java --version              # openjdk 25.x.x
git --version               # git version 2.40+
docker --version            # Docker version 24+
docker compose version      # Docker Compose version v2.x+
```

→ 4개 모두 출력되어야 다음 단계로.

---

## 2. 저장소 클론·초기 설정

### 2.1 Git 글로벌 설정 (최초 1회)

```bash
git config --global user.name "본인 이름"
git config --global user.email "본인 이메일"
git config --global init.defaultBranch main
git config --global pull.rebase false
git config --global core.autocrlf input    # Linux/macOS
# Windows의 경우 위 줄 대신:
# git config --global core.autocrlf true
```

### 2.2 저장소 클론

```bash
cd ~/projects                                    # 본인이 원하는 작업 디렉터리
git clone https://github.com/{팀_GitHub}/ridely.git
cd ridely
```

### 2.3 디렉터리 구조 확인

```
ridely/
├── README.md
├── pom.xml
├── docs/
│   ├── DEVELOPMENT_SETUP.md     ← 이 문서
│   ├── ridely_planning.md
│   ├── ridely_api_spec.md
│   └── ridely_schema.sql
├── src/
│   ├── main/
│   │   ├── java/kr/ridely/
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-local.yml
│   │       ├── application-prod.yml
│   │       └── static/
│   └── test/
└── .env.example
```

→ `pom.xml`, `docs/`, `src/main/java/kr/ridely/`가 보이면 정상.

---

## 3. PostgreSQL 빠른 설치 (Docker 활용)

PostgreSQL 16 + PostGIS + pgvector 3종 세트를 OS별로 직접 설치하면 절차가 복잡하고 1~2시간이 든다. 특히 pgvector는 Windows에서 별도 컴파일이 필요해 트러블슈팅 부담이 크다.

본 가이드는 **Docker로 DB만 실행하는 방식**을 채택했다. Docker는 여기서 단순히 "DB 설치를 한 줄 명령으로 대체"하는 도구일 뿐이며, **앱 배포·컨테이너화와는 무관**하다. 배포 방식은 본선·결선이 가까워졌을 때 별도로 결정한다.

부담스럽다면 PostgreSQL·PostGIS·pgvector를 OS에 직접 설치해도 동작에 차이 없음. 단, 팀 3명의 환경 일관성을 위해 Docker 방식 권장.

### 3.1 `docker-compose.yml` 작성

프로젝트 루트에 `docker-compose.yml` 파일을 생성한다 (이미 있다면 건너뜀):

```yaml
	services:
	  postgres:
	    image: imresamu/postgis:16-3.5-bundle0-bookworm
	    container_name: ridely-postgres
	    restart: unless-stopped
	    environment:
	      POSTGRES_DB: ridely
	      POSTGRES_USER: ridely
	      POSTGRES_PASSWORD: ridely
	    ports:
	      - "5432:5432"
	    volumes:
	      - ridely-postgres-data:/var/lib/postgresql/data
	
	volumes:
	  ridely-postgres-data:
```

### 3.2 컨테이너 실행

```bash
# 프로젝트 루트에서
docker compose up -d
```

### 3.3 컨테이너 상태 확인

```bash
docker compose ps
```

출력에 다음이 보이면 정상:
```
NAME              STATUS                    PORTS
ridely-postgres   Up X seconds (healthy)    0.0.0.0:5432->5432/tcp
```

### 3.4 PostgreSQL 접속 + 확장 활성화

```bash
docker exec -it ridely-postgres psql -U ridely -d ridely
```

`psql` 안에서:
```sql
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS vector;

-- 확인
SELECT extname, extversion FROM pg_extension WHERE extname IN ('postgis','vector');
```

출력:
```
 extname | extversion
---------+------------
 postgis | 3.5.x
 vector  | 0.8.x
```

`\q`로 종료.

### 3.5 정지·재시작 명령 (참고)

```bash
docker compose stop       # 정지 (데이터 유지)
docker compose start      # 재시작
docker compose down       # 컨테이너 삭제 (데이터는 볼륨에 유지)
docker compose down -v    # 컨테이너 + 볼륨 모두 삭제 (데이터 완전 삭제)
```

---

## 4. DB 스키마 적용

`docs/ridely_schema.sql`을 PostgreSQL에 적용.

### 4.1 스키마 파일 실행

```bash
# 프로젝트 루트에서
docker exec -i ridely-postgres psql -U ridely -d ridely < docs/ridely_schema.sql
```

> Windows PowerShell에서는 리디렉션 문법이 다를 수 있어. CMD나 Git Bash 사용 권장.

### 4.2 적용 확인

```bash
docker exec -it ridely-postgres psql -U ridely -d ridely -c "\dt"
```

출력에 다음 17개 테이블이 모두 보이면 정상 (schema v1.1):
```
 region
 accident_zone
 bike_road
 national_bike_route
 route_facility
 bike_parking
 tour_attraction
 bike_station
 repair_shop
 app_user
 refresh_token
 user_settings
 recommended_route
 saved_route
 recommendation_cache
 riding_session
 riding_history_embedding
 
 spatial_ref_sys # PostGIS 기본 제공 테이블
```

### 4.3 시드 데이터 확인

`region` 테이블에 서울·인천·경기 3건이 들어있는지:

```bash
docker exec -it ridely-postgres psql -U ridely -d ridely -c "SELECT * FROM region;"
```

출력:
```
 region_id | region_code |  region_name   | parent_id | created_at
-----------+-------------+----------------+-----------+------------------------
         1 | 11          | 서울특별시     |           | 2026-...
         2 | 28          | 인천광역시     |           | 2026-...
         3 | 41          | 경기도         |           | 2026-...
```

---

## 5. API 키 발급·환경변수 설정

### 5.1 발급 대상 (6개)

> 자세한 발급 절차는 별도 가이드 또는 팀 위키 참조. 여기서는 최종 환경변수만 정리.

| 환경변수                                               | 발급처                  | 비고                        |
| -------------------------------------------------- | -------------------- | ------------------------- |
| `GCP_PROJECT_ID`, `GOOGLE_APPLICATION_CREDENTIALS` | Google Cloud Console | Vertex AI Gemini          |
| `ORS_API_KEY`                                      | openrouteservice.org | 가입 즉시 발급                  |
| `KAKAO_REST_API_KEY`, `KAKAO_JS_API_KEY`           | developers.kakao.com | 앱 생성 → 키 확인               |
| `TAAS_SERVICE_KEY`                                 | data.go.kr           | 1~2일 승인 대기 (가장 먼저 신청)     |
| `SEOUL_OPEN_API_KEY`                               | data.seoul.go.kr     | 즉시 발급                     |
| `JWT_SECRET`                                       | 자체 생성                | `openssl rand -base64 32` |

### 5.2 `.env` 파일 작성

```bash
# 템플릿 복사
cp .env.example .env
```

`.env` 파일을 편집기로 열고 실제 값으로 채운다:

```bash
SPRING_PROFILES_ACTIVE=local

DB_URL=jdbc:postgresql://localhost:5432/ridely
DB_USER=ridely
DB_PASSWORD=ridely

JWT_SECRET=실제로_openssl_rand_base64_32_명령으로_생성한_값

GCP_PROJECT_ID=ridely-prod-12345
GCP_LOCATION=us-central1
GOOGLE_APPLICATION_CREDENTIALS=/abs/path/to/.secrets/gcp-credentials.json

ORS_API_KEY=실제_ORS_토큰
KAKAO_REST_API_KEY=실제_Kakao_REST_키
KAKAO_JS_API_KEY=실제_Kakao_JS_키
TAAS_SERVICE_KEY=실제_공공데이터_인증키
SEOUL_OPEN_API_KEY=실제_서울_인증키
```

### 5.3 JWT 시크릿 생성

#### Linux
```bash
openssl rand -base64 32
```

#### Windows (PowerShell)
```powershell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
```

생성된 값을 `JWT_SECRET`에 붙여넣기.

### 5.4 GCP 서비스 계정 JSON 키 저장

다운받은 `gcp-credentials.json`을 `.secrets/` 디렉터리에 넣음:

```bash
mkdir -p .secrets
# Windows에선: mkdir .secrets

# JSON 파일을 .secrets/gcp-credentials.json 으로 복사
```

`.gitignore`에 이미 `.secrets/`, `.env`, `*.key` 등록되어 있어 커밋되지 않음 (확인 권장).

### 5.5 절대 커밋 금지 항목

| 파일                             | 상태             |
| ------------------------------ | -------------- |
| `.env`                         | `.gitignore` ✅ |
| `.secrets/`                    | `.gitignore` ✅ |
| `application-local-secret.yml` | `.gitignore` ✅ |

→ `git status`로 위 파일들이 안 보이는지 확인.

---

## 6. Spring Boot 실행 + 헬스체크

### 6.1 환경변수 로딩 + 실행

#### Linux

```bash
# .env 파일을 환경에 로드 후 실행
set -a; source .env; set +a
./mvnw spring-boot:run
```

#### Windows (CMD)

```cmd
for /F "tokens=*" %i in (.env) do set %i
mvnw.cmd spring-boot:run
```

#### Windows (PowerShell)

```powershell
Get-Content .env | ForEach-Object {
  if ($_ -match '^([^=]+)=(.*)$') {
    [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2])
  }
}
.\mvnw.cmd spring-boot:run
```

> 처음 실행 시 Maven이 의존성 다운로드 (5~10분 소요).

### 6.2 정상 실행 확인

로그 끝에 다음이 보여야 정상:
```
Tomcat started on port 8080 (http) with context path '/'
Started RidelyApplication in X.XXX seconds
```

### 6.3 헬스체크

다른 터미널에서:

```bash
# Linux / Windows Git Bash
curl http://localhost:8080/api/v1/health
```

또는 브라우저에서 `curl http://localhost:8080/api/v1/health` 접속.

응답:
```json
{
  "app": "ridely",
  "profile": "local",
  "status": "UP",
  "timestamp": "2026-05-17T12:34:56.789Z"
}
```

### 6.4 종료

실행 중인 터미널에서 `Ctrl + C`.

---

## 7. IDE 설정

IntelliJ IDEA 또는 VS Code 중 본인이 편한 쪽 선택.

### 7.1 IntelliJ IDEA

#### 프로젝트 열기
1. **File > Open** → `ridely/` 디렉터리 선택
2. **Open as Project** → Maven 프로젝트 자동 인식 → 의존성 다운로드 대기

#### JDK 25 지정
1. **File > Project Structure** (Ctrl+Alt+Shift+S)
2. **Project Settings > Project**
3. **SDK**: JDK 25 (Temurin) 선택
4. **Language level**: 25
5. **Modules**: 모듈 → Dependencies → Module SDK: Project SDK 사용

#### 환경변수 자동 로딩 (EnvFile 플러그인)
1. **File > Settings > Plugins** → "EnvFile" 검색·설치
2. **Run > Edit Configurations** → Spring Boot 설정 열기
   - 첫 실행 후 자동 생성 (`RidelyApplication` 설정)
3. 상단 탭 **EnvFile** → **Enable EnvFile** 체크
4. `+` 버튼 → `.env` 파일 추가
5. **Apply > OK**
6. ▶ 버튼으로 실행

#### Lombok 활성화
1. **File > Settings > Plugins** → "Lombok" 검색·설치 (보통 기본 탑재)
2. **File > Settings > Build, Execution, Deployment > Compiler > Annotation Processors**
3. **Enable annotation processing** 체크

#### Code Style (팀 통일)
1. **File > Settings > Editor > Code Style > Java**
2. **Scheme**: Project (팀 공유)
3. **Tabs and Indents**: Use tab character 해제 / Indent 4 / Continuation indent 8

### 7.2 VS Code

#### 확장 프로그램 설치 (필수)
| 확장 | 설치 명령 |
|---|---|
| Extension Pack for Java | `ms-vscode.vscode-java-pack` |
| Spring Boot Extension Pack | `vmware.vscode-boot-dev-pack` |
| Lombok Annotations Support | `gabrielbb.vscode-lombok` |
| Docker | `ms-azuretools.vscode-docker` |
| GitLens | `eamodio.gitlens` |

설치:
```bash
code --install-extension vscjava.vscode-java-pack
code --install-extension vmware.vscode-boot-dev-pack
code --install-extension gabrielbb.vscode-lombok
code --install-extension ms-azuretools.vscode-docker
code --install-extension eamodio.gitlens
```

#### `.vscode/settings.json` (프로젝트 루트)

```json
{
  "java.configuration.updateBuildConfiguration": "automatic",
  "java.compile.nullAnalysis.mode": "automatic",
  "java.format.settings.profile": "GoogleStyle",
  "java.jdt.ls.java.home": "<JDK 25 경로>",
  "editor.tabSize": 4,
  "editor.insertSpaces": true,
  "files.encoding": "utf8",
  "files.eol": "\n"
}
```

JDK 25 경로 확인:
- Linux: `/usr/lib/jvm/temurin-25-jdk-amd64`
- Windows: `C:\Program Files\Eclipse Adoptium\jdk-25.x.x-hotspot`

#### `.vscode/launch.json` (Spring Boot + .env)

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Ridely",
      "request": "launch",
      "mainClass": "kr.ridely.RidelyApplication",
      "projectName": "ridely",
      "envFile": "${workspaceFolder}/.env"
    }
  ]
}
```

F5 키로 실행.

---

## 8. 개발 워크플로우

### 8.1 브랜치 전략 (GitHub Flow)

`main`은 항상 배포 가능. 작업은 항상 브랜치에서.

```bash
# 1. main에서 최신 코드 가져오기
git checkout main
git pull origin main

# 2. 작업 브랜치 생성
git checkout -b feature/auth-signup-api

# 3. 작업 + 커밋
# ... 코딩 ...
git add .
git commit -m "feat(auth): 회원가입 API 구현"

# 4. 원격 푸시
git push -u origin feature/auth-signup-api

# 5. GitHub에서 Pull Request 생성
# 6. 팀원 1명 이상 리뷰·승인 → Squash and merge
# 7. 머지 후 로컬 브랜치 정리
git checkout main
git pull origin main
git branch -d feature/auth-signup-api
```

### 8.2 브랜치 네이밍

```
feature/{domain}-{summary}    # 신규 기능
fix/{domain}-{summary}        # 버그 수정
refactor/{domain}-{summary}   # 리팩토링
docs/{summary}                # 문서
chore/{summary}               # 빌드·설정
```

예시:
- `feature/auth-jwt-login`
- `feature/route-langgraph-pipeline`
- `feature/map-kakao-renderer`
- `fix/poi-distance-calculation`
- `docs/api-spec-v2`

### 8.3 커밋 메시지 (Conventional Commits)

```
{type}({scope}): {summary}
```

**type**: `feat` / `fix` / `refactor` / `docs` / `style` / `test` / `chore`
**scope**: `auth` / `route` / `poi` / `map` / `riding` / `settings` / `rag` / `langgraph`

예시:
```
feat(route): InfraCandidateCollector 구현
feat(rag): pgvector 유사도 검색 서비스
fix(map): 사고다발지 폴리곤 좌표 순서 오류
docs(api): /routes/recommend RAG 필드 추가
chore(pom): Spring AI 1.1.6 의존성 추가
```

### 8.4 PR 규칙

- **사이즈**: 300줄 이내 권장. 큰 작업은 여러 PR로 분할
- **승인**: 1명 이상 리뷰·승인 필수
- **CI**: 통과 필수 (Phase 9.6 이후 GitHub Actions 도입)
- **머지 방식**: Squash and merge (커밋 히스토리 깔끔)

### 8.5 코드 스타일

| 항목 | 규칙 |
|---|---|
| 인덴트 | 스페이스 4칸 (탭 X) |
| 줄바꿈 | LF (Unix style) |
| 인코딩 | UTF-8 |
| 파일 끝 | 빈 줄 1개 |
| Java 네이밍 | 클래스 PascalCase, 메서드·필드 camelCase, 상수 SCREAMING_SNAKE_CASE |
| 패키지 | `kr.ridely.{domain}` |

---

## 9. 문제 해결 (Troubleshooting)

### 9.1 Java 관련

**Q. `java --version`이 25가 아니라 다른 버전이 나옴**

여러 JDK가 설치된 상태. `JAVA_HOME` 환경변수 확인:

```bash
# Linux
echo $JAVA_HOME
sudo update-alternatives --config java   # 기본 Java 선택

# Windows
echo %JAVA_HOME%
# 제어판 > 시스템 > 고급 시스템 설정 > 환경변수에서 JAVA_HOME 수정
```

**Q. Maven 빌드 시 `release version 25 not supported`**

`pom.xml`의 `<java.version>25</java.version>` 확인. IntelliJ에서는 Project SDK가 25인지 다시 확인.

### 9.2 Docker 관련

**Q. `docker compose up` 실행했는데 5432 포트가 이미 사용 중**

다른 PostgreSQL 인스턴스가 실행 중. 둘 중 하나로 해결:

```bash
# Linux: 시스템 PostgreSQL 정지
sudo systemctl stop postgresql

# 또는 docker-compose.yml의 포트 변경
ports:
  - "5433:5432"   # 호스트 5433 사용
```

→ 포트 변경 시 `.env`의 `DB_URL`도 `localhost:5433`으로.

**Q. `Permission denied` (Linux)**

`docker` 명령마다 sudo 필요한 경우:
```bash
sudo usermod -aG docker $USER
# 로그아웃 후 재로그인
```

**Q. Windows에서 Docker Desktop이 안 켜짐**

WSL 2 설치 필요:
```powershell
wsl --install
# 재부팅 후 Docker Desktop 재실행
```

### 9.3 DB 관련

**Q. `CREATE EXTENSION postgis` 실패**

`imresamu/postgis-pgvector:16-3.5` 이미지가 아닐 가능성. `docker-compose.yml`의 image 확인.

**Q. 스키마 적용 시 `permission denied for schema public`**

PostgreSQL 15+에서는 `public` 스키마 권한이 기본 제한. 한 번만:

```bash
docker exec -it ridely-postgres psql -U ridely -d ridely -c "GRANT ALL ON SCHEMA public TO ridely;"
```

### 9.4 Spring Boot 관련

**Q. `Failed to configure a DataSource`**

환경변수 로딩 안 됨. `.env` 파일에 `DB_URL`·`DB_USER`·`DB_PASSWORD` 확인.
IntelliJ의 경우 EnvFile 플러그인 설정 다시 확인.

**Q. `Could not resolve dependencies for project ridely:ridely` (Spring AI)**

Spring AI 1.1.6은 Maven Central에 정식 배포되어 있어 별도 저장소 설정이 불필요. 의존성 다운로드 실패 시:

```bash
# Maven 캐시 정리 후 재시도
./mvnw dependency:purge-local-repository
./mvnw clean install -U
```

`pom.xml`의 Spring AI 버전이 `1.1.6`인지 확인.

**Q. Vertex AI 호출 시 `Could not find Application Default Credentials`**

`GOOGLE_APPLICATION_CREDENTIALS` 환경변수가 안 잡혔거나 경로가 잘못됨:

```bash
# Linux/macOS
ls -la $GOOGLE_APPLICATION_CREDENTIALS

# Windows
dir %GOOGLE_APPLICATION_CREDENTIALS%
```

→ 파일이 없으면 GCP 서비스 계정 JSON 키 다시 다운로드.

**Q. MyBatis `Invalid bound statement (not found)`**

Mapper 인터페이스와 XML이 연결 안 됨. 다음 확인:

1. `application.yml`의 `mybatis.mapper-locations: classpath:mapper/**/*.xml` 경로 확인
2. XML의 `<mapper namespace="kr.ridely.dao.AuthDao">`가 인터페이스 풀 패키지명과 일치하는지
3. XML의 `<select id="...">` id가 인터페이스 메서드명과 일치하는지
4. `config/MyBatisConfig.java`에 `@MapperScan("kr.ridely.dao")` 있는지

**Q. MyBatis 결과가 null이거나 camelCase 매핑 안 됨**

`user_id` 같은 스네이크 케이스 컬럼이 `userId` 필드에 안 들어옴:

→ `application.yml`에 `mybatis.configuration.map-underscore-to-camel-case: true` 확인.
→ 그래도 안 되면 `<resultMap>`으로 명시적 매핑.

**Q. JdbcClient에서 PostGIS `geometry` 컬럼 매핑 오류**

`ST_X(geom::geometry)`, `ST_Y(geom::geometry)`로 좌표를 분리 추출하거나, `common/util/GeometryUtils`로 `PGgeometry` → JTS Point 변환. raw `geometry` 타입을 직접 `getObject`로 받지 말 것.

### 9.5 IDE 관련

**Q. IntelliJ에서 Lombok이 인식 안 됨 (`getXxx()` not found)**

1. Lombok 플러그인 설치 확인
2. **File > Settings > Build > Compiler > Annotation Processors** → Enable 체크
3. `Build > Rebuild Project`

**Q. VS Code Java 확장이 무한 로딩**

```bash
# 캐시 삭제
rm -rf ~/.vscode/extensions/redhat.java-*/server/workspace
```

→ VS Code 재시작.

### 9.6 Git 관련

**Q. PR 머지 후 로컬 브랜치 삭제가 안 됨**

```bash
git branch -D feature/xxx   # 강제 삭제
```

**Q. `LF will be replaced by CRLF` 경고 (Windows)**

이미 `core.autocrlf true`로 설정했다면 무시 가능. 문제 되면:

```bash
git config --global core.autocrlf true       # Windows
git config --global core.autocrlf input      # Linux/macOS
```

---

## ✅ 최종 점검 체크리스트

개발환경 구성 끝났는지 한 번에 확인:

- [ ] `java --version` → `25.x.x`
- [ ] `git --version` → `2.40+`
- [ ] `docker compose ps` → `ridely-postgres` 실행 중
- [ ] `docker exec -it ridely-postgres psql -U ridely -d ridely -c "\dt"` → 테이블 17개 보임
- [ ] `.env` 파일 작성 완료 (모든 키 채워짐)
- [ ] `.secrets/gcp-credentials.json` 존재
- [ ] `./mvnw spring-boot:run` → `Started RidelyApplication`
- [ ] `curl http://localhost:8080/api/v1/health` → `{"status":"UP"}`
- [ ] IDE에서 프로젝트 열림 + 의존성 다운로드 완료
- [ ] IDE에서 ▶ 버튼으로 실행 가능 (환경변수 자동 로딩)
- [ ] `git status` → `.env`, `.secrets/`가 안 보임 (gitignore 적용 확인)

→ **11개 모두 체크되면 첫 스프린트 진행 가능.**

---

## 📚 다음 단계

개발환경 구성이 끝나면 [Phase 9.5: 도메인별 첫 스프린트] 진행.

각 팀원은 자기 도메인의 첫 작업부터 시작:
- **A**: 회원가입 API + JWT 발급
- **B**: Kakao Map 띄우기 + 인프라 마커 한 종류
- **C**: 공공데이터 1종 적재 배치 + Health check 확장

---

## 🔗 참고 자료

- Spring Boot 3.5 공식 문서: https://docs.spring.io/spring-boot/3.5/
- Spring AI 1.x 공식 문서 (Stable): https://docs.spring.io/spring-ai/reference/1.1/
- MyBatis-Spring-Boot-Starter: https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/
- MyBatis 매퍼 XML 가이드: https://mybatis.org/mybatis-3/ko/sqlmap-xml.html
- Spring JdbcClient (PostGIS·pgvector용): https://docs.spring.io/spring-framework/reference/data-access/jdbc/client.html
- PostgreSQL + PostGIS: https://postgis.net/documentation/
- pgvector: https://github.com/pgvector/pgvector
- Apache Cordova 공식 문서: https://cordova.apache.org/docs/en/latest/
- DB 접근 사용 규칙 (필독): `docs/adr/002-db-access.md`
- 도전제안서 + 기획서: `docs/ridely_planning.md`
