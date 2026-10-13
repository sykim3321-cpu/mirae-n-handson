# mirae-n-handson — Claude Code 심화 과정 실습 저장소

4회차 과정 내내 쓰는 실습 저장소입니다. 코드 · 데이터 · 로그는 전부 **더미 에듀테크 도메인**(문항 은행 · 과제 배포 · 성적 집계)이며, 실제 서비스 · 실명 · 실제 시스템 이름은 들어 있지 않습니다.

- 이 저장소는 **포크해서 씁니다.** `cd ~/work && gh repo fork tristan-kkim/mirae-n-handson --clone` 으로 내 포크를 `~/work/mirae-n-handson` 에 받습니다. `origin` 은 내 포크, `upstream` 은 원본입니다.
- 회차 사이에 보강된 자료는 `upstream` 에서 받습니다: `git fetch upstream && git merge --no-edit upstream/main`
- Claude Code 는 항상 이 저장소 루트(`~/work/mirae-n-handson`)에서 실행합니다. 수업 중 만드는 `CLAUDE.md`, `.claude/`, `hooks/`, `docs/` 가 모두 루트에 생깁니다.

## 모듈 구성

| 모듈 | 위치 | 스택 | 주소 |
|---|---|---|---|
| 문항 은행 (레거시) | `legacy/item-bank-php/` | PHP 7.4 + MariaDB 10.11 | http://localhost:8081 |
| 과제 배포 (레거시) | `legacy/assignment-thymeleaf/` | Spring MVC + Thymeleaf + JDBC | http://localhost:8082 |
| 성적 집계 (레거시) | `legacy/grade-mssql/` | MS-SQL 저장 프로시저 + 얇은 Java 호출부 | http://localhost:8083 |
| 현행 API | `modern/api/` | Spring Boot 3 · Java 21 · Gradle | http://localhost:8080 (로컬 실행) |
| 현행 화면 | `modern/web/` | React 18 · TypeScript · Vite | http://localhost:5173 (로컬 실행) |

도메인 용어: 문항 `item` · 단원 `unit` · 난이도 `level`(1~5) · 태그 `tag` / 학급 `class` · 과제 `assignment` · 배포 `distribution` · 제출 `submission` / 학생 식별자 `STU-<숫자>`.

## Compose 프로필 · 포트

| 프로필 | 뜨는 것 | 포트 |
|---|---|---|
| `php` | 문항 은행 웹 + MariaDB | 8081, 3306 |
| `thymeleaf` | 과제 배포 웹 + MariaDB | 8082, 3306 |
| `mssql` | 성적 집계 호출부 + MS-SQL | 8083, 1433 |
| `modern` | 현행 샘플 DB(MariaDB)만. `modern/api` · `modern/web` 은 로컬에서 실행 | 3306 |

- `php` · `thymeleaf` · `modern` 은 **같은 MariaDB 서비스 하나**를 공유합니다. 프로필을 둘 이상 같이 올려도 3306 충돌이 없습니다.
- `down` · `build` · `logs` 도 반드시 프로필을 붙여 실행합니다(아래 명령 목록 참조).
- **Apple Silicon Mac**: `mssql` 프로필은 x86 에뮬레이션으로 돌아 느리거나 뜨지 않을 수 있습니다. macOS 참가자는 `php` 또는 `thymeleaf` 를 권합니다. 성적 집계 모듈의 프로시저 소스는 `legacy/grade-mssql/` 의 `.sql` 파일로도 읽을 수 있습니다.
- **시드 초기화**: DB 컨테이너는 영구 볼륨을 쓰지 않습니다. `docker compose --profile php down && docker compose --profile php up -d` 로 컨테이너를 다시 만들면 시드 상태(고정 ID · 고정 시각)로 돌아갑니다. 데이터를 바꾸는 실습(재배포 등) 전후에 이 방법으로 상태를 맞추세요.

## DB 계정 (읽기 전용)

| DB | 호스트:포트 | DB명 | 계정 / 비밀번호 | DSN |
|---|---|---|---|---|
| MariaDB (문항 은행 · 과제 배포 · 현행) | localhost:3306 | `itembank` | `readonly` / `readonly-pass` — SELECT 권한만 | `mariadb://readonly:readonly-pass@localhost:3306/itembank?sslmode=disable` |
| MS-SQL (성적 집계) | localhost:1433 | `grades` | `readonly` / `Readonly-pass1` — SELECT 권한만 | `sqlserver://readonly:Readonly-pass1@localhost:1433/grades?sslmode=disable` |

애플리케이션용 쓰기 계정은 `docker-compose.yml` 안에서만 쓰며 실습에서는 사용하지 않습니다.

## 명령

```bash
# 기동 · 확인 · 정리 — down · build · logs 는 전부 프로필을 붙인 형태
docker compose --profile php up -d
docker compose --profile thymeleaf up -d
docker compose --profile mssql up -d            # Apple Silicon에서는 느리거나 안 뜰 수 있음 → macOS는 php / thymeleaf 권장
docker compose --profile modern up -d
docker compose ps
docker compose --profile php down
docker compose --profile php --profile modern down
docker compose --profile php --profile thymeleaf --profile mssql --profile modern down   # Day 4-3 마지막 정리
docker compose --profile thymeleaf --profile modern build                                 # Day 0 준비 9 (이미지를 미리 만들어 둠)
docker compose --profile mssql build                                                      # Day 0, Windows만
docker compose --profile php logs

# 현행 샘플
cd modern/api && ./gradlew test                  # DB 없이 통과해야 함 (Day 0 준비 9)
cd modern/api && ./gradlew bootRun               # "Tomcat started on port 8080", modern 프로필 DB 필요
cd modern/web && npm ci && npm run dev
cd modern/web && npm run lint && npm run typecheck && npm test

# 동작 보존 테스트
cd characterization && npm ci
cd characterization && npm run baseline -- <모듈명>          # item-bank | assignment | grade
cd characterization && npm test                               # 대상: 레거시(기본 포트)
cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test   # 대상: 새 API

# 상태 확인에 쓰는 형태
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081   # 200 또는 302 = 떠 있음, 000 = 꺼짐
bash scripts/check-env.sh
```

## 폴더 안내

```
mirae-n-handson/
├── README.md                  이 문서
├── docker-compose.yml         프로필 php / thymeleaf / mssql / modern
├── scripts/check-env.sh       환경 셀프 점검 (Day 0, 1-1)
├── legacy/                    분석 · 이관 대상 레거시 모듈 (1-2, 1-3, 2-2)
│   ├── item-bank-php/         문항 은행 — 교안의 기본 예시 모듈
│   ├── assignment-thymeleaf/  과제 배포
│   └── grade-mssql/           성적 집계 (프로시저 .sql 포함)
├── modern/                    현행 샘플 — 팀 컨벤션의 기준 (1-1, 1-3, 2-1, 2-2, 2-4, 3-5, 4-1)
│   ├── api/                   Spring Boot 3 REST
│   └── web/                   React 18 + TypeScript
├── characterization/          동작 보존 테스트 틀 (1-3, 2-1, 2-4, 4-1). 모듈별 테스트 · 스냅샷은 수업 중 생성
├── vendor-prs/                외주 PR 패치 3건 (2-1, 3-1)
├── incident-logs/             장애 로그 시나리오 a~d (2-2, 4-1)
├── mcp-skeleton/              TypeScript MCP 서버 골격 (2-3)
├── pipeline-samples/          배치 로그 · 적재 건수 CSV · Terraform 예시 (3-2, 3-5, 4-1)
├── ci-ports/                  GitLab CI(.gitlab-ci.yml, 숨김 파일) · Jenkins 이식용 예시 (3-1)
├── specs/                     신규 개발 스펙 + 시작 골격 java / python (4-2)
└── templates/                 프로젝트 유형별 CLAUDE.md 템플릿 · 검증루프 · 승인 체크리스트 · 시큐어코딩 체크리스트 (1-1, 2-1, 3-4)
```

회차별로 쓰는 폴더: **1회차** `modern/`, `templates/CLAUDE.*.md`, `legacy/`, `characterization/` · **2회차** `templates/verification-loop.md` · `approval-checklist.md`, `vendor-prs/`, `incident-logs/`, `mcp-skeleton/` · **3회차** `vendor-prs/`, `ci-ports/`, `pipeline-samples/`, `templates/secure-coding-checklist.md` · **4회차** `specs/`, 1~3회차 결과물.

## 이 저장소에 넣지 않은 것

아래는 참가자가 **수업 중에 만듭니다.** 저장소에 미리 들어 있으면 해당 실습이 성립하지 않으므로 넣지 않았습니다.

| 경로 | 만드는 차시 |
|---|---|
| `CLAUDE.md` (루트, `modern/`, `legacy/` 어디에도 없음) | 1-1 (`/init`) |
| `.claude/skills/*/SKILL.md`, `.claude/agents/*.md`, `.claude/settings.json` | 1-1 · 1-2 · 2-1 · 2-2 · 2-4 · 3-2 · 3-3 · 3-4 |
| `hooks/block-dangerous.mjs`, `hooks/secret-scan.mjs` | 3-4 |
| `.mcp.json`, `dbhub.toml` | 2-3 |
| `docs/` 아래 문서 전부 | 1-2 이후 |
| `.github/workflows/pr-review.yml` | 3-1 (내 포크의 `main` 에 웹으로 만듭니다) |
| `modern/api` 의 `/api/items/search` 이관 엔드포인트 | 1-3 |
| `characterization/tests/<모듈>.test.js` 와 스냅샷 | 1-3 |
| 루트 `.env` | 만들지 않습니다. 권한 실습의 더미는 `.env.perm-test` 입니다 |

## 요구 환경

Git · JDK 21 · Node.js 22.5 이상 · Python 3.12 · Docker · Docker Compose · Claude Code(구독 로그인) · `gh`(GitHub CLI). 기준 환경은 Windows + WSL2(Ubuntu 24.04) bash 이고 macOS(zsh)에서도 같은 명령이 동작합니다. WSL 에서는 저장소를 반드시 WSL 홈(`~/work`) 아래에 두고 `/mnt/c` 를 쓰지 않습니다. 전부 갖춰졌는지는 `bash scripts/check-env.sh` 로 확인합니다.
