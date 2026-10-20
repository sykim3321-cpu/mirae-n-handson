# 전사 규칙 후보 — sykim (주 스택: data-infra)

- 출처: `.claude/settings.json`(permissions · hooks), `hooks/block-dangerous.mjs`, `hooks/secret-scan.mjs`, `scripts/hook-node.sh`
- 기준일: 2026-10-13. 기존 파일은 바꾸지 않았다.
- 스택 코드: `data-infra`(Terraform · AWS · Python 배치), `spring`(Gradle), `react`(npm · Node), `php`(composer). 어느 스택에서든 뜻이 같으면 `공통`.

## 규칙 표

| 규칙 | 종류 | 적용 범위 | 막는 사고 또는 허용 이유 |
|---|---|---|---|
| `Bash(rm -rf *)` | deny | 공통 | 재귀 강제 삭제로 작업 트리 · 홈 디렉터리를 날리는 사고 |
| `Bash(sudo *)` | deny | 공통 | 권한 상승으로 시스템 파일 · 패키지를 바꾸는 사고 |
| `Bash(git push --force *)` · `Bash(git push -f *)` | deny | 공통 | 원격 브랜치 이력을 덮어써 다른 사람 커밋을 잃는 사고 |
| `Bash(git reset --hard *)` | deny | 공통 | 커밋하지 않은 로컬 변경을 되돌릴 수 없게 버리는 사고 |
| `Bash(*prod-db*)` · `Bash(*prod.db*)` | deny | 공통 | 운영 DB 호스트에 붙어 운영 데이터를 읽거나 쓰는 사고 |
| `Bash(curl *)` · `Bash(wget *)` | deny | 공통 | 코드 · 데이터 · 자격 증명을 외부로 전송하거나 검증 안 된 스크립트를 내려받는 사고 |
| `Read(./.env)` · `Read(./.env.*)` · `Read(./secrets/**)` · `Read(**/*.pem)` · `Read(~/.ssh/**)` · `Read(~/.aws/**)` | deny | 공통 | 비밀값 · 키가 모델 컨텍스트로 들어가 답변 · 로그 · 커밋에 새는 사고 |
| `Edit(./.env)` · `Edit(./.env.*)` · `Edit(./secrets/**)` | deny | 공통 | 비밀 파일을 덮어쓰거나 새 비밀값을 만들어 넣는 사고 |
| `mcp__github__delete_file` · `mcp__github__merge_pull_request` | deny | 공통 | 원격 저장소 파일 삭제 · 리뷰 없는 머지 |
| `Bash(terraform apply *)` · `Bash(terraform destroy *)` | deny | data-infra | 승인 안 된 인프라 변경 · 자원 삭제(apply 는 사람이 plan 파일로만 실행) |
| `Bash(git push *)` | ask | 공통 | 원격 반영 전에 사람이 브랜치 · 내용을 확인 |
| `mcp__github__create_pull_request` · `mcp__github__push_files` | ask | 공통 | 외부(원격 저장소)로 나가는 쓰기를 사람이 확인 |
| `mcp__dbhub` | ask | 공통 | DB 질의를 사람이 확인(접속 대상 · 쿼리 내용) |
| `Bash(docker compose down *)` · `Bash(docker compose --profile *)` | ask | data-infra | 로컬 DB 컨테이너를 내려 데이터(tmpfs)를 잃거나 엉뚱한 프로필을 띄우는 일 |
| `Bash(npm install *)` · `Bash(npm i *)` · `Bash(npm ci *)` | ask | react | 의존성 추가 · `package-lock.json` 변경, 공급망 위험 |
| `Bash(pip install *)` | ask | data-infra | 전역 설치 · `requirements.txt` 밖 패키지 유입, 공급망 위험 |
| `Bash(git status *)` · `Bash(git diff *)` · `Bash(git log *)` | allow | 공통 | 읽기 전용, 매 작업마다 쓰는 확인 명령 |
| `Bash(git add *)` · `Bash(git commit *)` | allow | 공통 | 로컬에서만 바뀌고 되돌릴 수 있음. 커밋은 secret-scan Hook 이 검사 |
| `mcp__github__get_file_contents` · `list_pull_requests` · `pull_request_read` · `list_issues` | allow | 공통 | 원격 저장소 읽기 전용 |
| `mcp__item-bank__search_items` | allow | data-infra | 이 저장소의 문항 조회용 읽기 전용 MCP 도구 |
| `Bash(npm test *)` · `npm run test` · `npm run lint` · `npm run typecheck` · `npm run build` | allow | react | 검증 루프 명령. 로컬 산출물만 만듦 |
| `Bash(./gradlew test *)` · `./gradlew check` · `./gradlew build` | allow | spring | 검증 루프 명령. 테스트는 H2 로 돌아 외부 DB 를 건드리지 않음 |
| `WebFetch(domain:docs.spring.io)` · `WebFetch(domain:docs.gradle.org)` | allow | spring | 공식 문서 읽기 |
| `WebFetch(domain:developer.mozilla.org)` | allow | react | 공식 문서 읽기 |
| `scripts/hook-node.sh` (Hook 실행기, node 없으면 exit 2) | Hook | 공통 | node 가 PATH 에 없는 셸에서 Hook 이 조용히 꺼지는(fail-open) 사고 |
| block-dangerous: `rm` + 재귀 + 강제 옵션 조합(`-rf` · `-fr` · `-r -f` · `--recursive --force`) | Hook | 공통 | deny 패턴 `rm -rf *` 를 옵션 순서 · 표기만 바꿔 우회하는 사고 |
| block-dangerous: `DROP TABLE` · `DROP DATABASE` | Hook | 공통 | 셸에서 실행하는 SQL 로 테이블 · DB 를 지우는 사고 |
| block-dangerous: `prod-db` · `prod.db` | Hook | 공통 | 운영 DB 접근(deny 와 이중 방어) |
| secret-scan: `git commit` 직전 스테이징 추가 줄에서 AWS Access Key · 하드코딩 `password=` · PEM 개인키 탐지 | Hook | 공통 | 시크릿이 커밋 · 원격 저장소에 남는 사고. 값은 출력하지 않고 건수만 보고 |

## 추가 제안 (data-infra 에 필요한데 지금 없는 규칙)

| 규칙 | 종류 | 적용 범위 | 막는 사고 또는 허용 이유 |
|---|---|---|---|
| `Bash(terraform import *)` · `Bash(terraform state *)` · `Bash(terraform taint *)` · `Bash(terraform untaint *)` · `Bash(terraform force-unlock *)` | deny | data-infra | 상태 파일 조작 · 잠금 강제 해제. CLAUDE.md 는 금지하지만 settings 에는 apply · destroy 만 있다 |
| `Bash(terraform fmt *)` · `Bash(terraform validate *)` · `Bash(terraform init -backend=false *)` · `Bash(terraform plan *)` | allow | data-infra | 템플릿(`templates/CLAUDE.iac.md`)이 Claude 실행을 허용한 읽기 · 검증 명령. 매번 묻지 않게 함 |
| `Read(**/*.tfstate*)` · `Read(**/tfplan)` · `Read(**/*.tfvars)` | deny | data-infra | 상태 · plan · 변수 파일에 평문으로 든 비밀값이 컨텍스트로 들어가는 사고 |
| `Edit(**/envs/prod/**)` · `Write(**/envs/prod/**)` | deny | data-infra | 운영 환경 Terraform 코드를 직접 고치는 사고 |
| `Bash(*AWS_PROFILE=prod*)` · `Bash(*--profile prod*)` | deny | data-infra | 운영 AWS 계정 프로필로 명령을 실행하는 사고 |
| `Bash(aws * delete*)` · `Bash(aws s3 rm *)` · `Bash(aws s3 sync * --delete*)` | deny | data-infra | 버킷 · 객체 · 자원 삭제 |
| `Bash(aws sts get-caller-identity)` | allow | data-infra | 현재 자격 증명 계정 확인(읽기 전용) |
| `Bash(pytest *)` · `Bash(python -m pytest *)` | allow | data-infra | 검증 루프 명령. 템플릿상 네트워크 · 실제 DB 없이 돈다 |
| `Edit(**/requirements*.txt)` | ask | data-infra | 패키지 추가 · 업그레이드는 사전 승인 대상(템플릿 4절) |
| `Read(**/.env)` · `Read(**/.env.*)` · `Edit(**/.env)` · `Edit(**/.env.*)` | deny | 공통 | 현재 `./.env` 는 루트만 막는다. 배치 · 서비스 하위 폴더의 `.env` 도 막아야 함 |
| block-dangerous 확장: `TRUNCATE TABLE`, `WHERE` 없는 `DELETE FROM` | Hook | data-infra | 적재 테이블 전체 삭제. 재적재는 기준일 범위 `DELETE` 후 `INSERT` 만 허용 |
| block-dangerous 확장: DB 쓰기 계정 `app` 사용(`-u app`, `user=app`, `app@`) | Hook | 공통 | CLAUDE.md 금지 사항(조회는 `readonly` 계정)을 강제 |
| secret-scan 확장: AWS Secret Access Key(`aws_secret_access_key`), 접속 문자열(`mysql://user:pass@` 등), `*.tfvars` 파일 스테이징 | Hook | data-infra | 현재 3가지 패턴으로는 인프라 · 배치 쪽 대표 시크릿을 놓침 |
| secret-scan 트리거 보강: 명령 시작이 아니라 명령 안 어디서든 `git commit` 이 나오면 검사 | Hook | 공통 | 지금은 `startsWith("git commit")` 이라 `cd x && git commit …` 이 검사를 건너뜀 |

## 주의 (다른 스택에 그대로 걸면 일을 막을 수 있는 규칙)

- **`scripts/hook-node.sh` 의 fail-closed** — node 를 못 찾으면 모든 Bash 명령이 막힌다. Python 만 쓰는 data-infra · 배치 환경이나 PHP 환경에는 node 가 없을 수 있다. 전사 적용 전에 node 설치를 전제 조건으로 공지하거나, Hook 을 셸 · Python 판으로 함께 제공해야 한다.
- **`prod-db` · `prod.db` 문자열 매칭(deny + Hook)** — 접속이 아니라 문자열만 본다. 장애 로그 · 문서에서 `grep "prod-db" …` 하는 읽기 명령, 커밋 메시지 · 파일 이름에 이 문자열이 있는 명령도 막힌다. 이 문서를 만드는 중에도 `grep -rn "prod-db\|prod\.db" …` 가 block-dangerous 에 막혔다. RCA · 로그 분석을 하는 팀에는 접속 명령(`mysql -h`, `sqlcmd -S`, 접속 문자열)으로 범위를 좁혀야 한다.
- **`DROP TABLE` · `DROP DATABASE` Hook** — 마찬가지로 명령 문자열 전체를 본다. `grep -n "DROP TABLE" db/…` 같은 검색이나, 로컬 시드 · 마이그레이션 스크립트를 셸로 돌리는 spring · php 작업도 막힌다.
- **`Bash(curl *)` · `Bash(wget *)` deny** — 외부 전송만이 아니라 로컬 확인도 막는다. 템플릿의 헬스 체크(`curl … http://localhost:8000/health`), spring 의 `:8080` 확인, characterization 대상 확인이 모두 막힌다. 공통 deny 로 걸 거면 `localhost` · `127.0.0.1` 대상은 allow 로 열거나, deny 대신 ask 로 두는 편이 낫다.
- **secret-scan 의 하드코딩 `password=` 패턴** — 테스트 픽스처 · 로컬 더미 설정의 가짜 비밀번호(`password = "test"` 등)에도 걸려 커밋이 막힌다. 테스트 경로 예외 또는 허용 목록이 필요하다.
- **`Bash(npm ci *)` ask** — react 스택은 CLAUDE.md 가 의존성 설치에 `npm ci` 를 쓰라고 정해 두었다. 공통으로 걸면 CI 와 같은 설치를 할 때마다 묻는다. 막을 대상은 `npm install` · `npm i` 쪽이다.
- **`Bash(sudo *)` deny** — data-infra 의 로컬 도구 설치(terraform · aws CLI)나 docker 권한이 sudo 를 요구하는 환경이 있다. deny 는 유지하되, 설치는 사람이 하는 절차로 안내해야 한다.
- **`Bash(docker compose --profile *)` ask · `mcp__item-bank__search_items` allow** — 이 저장소의 compose 프로필 · MCP 서버 이름에 묶인 규칙이다. 다른 저장소에서는 뜻이 없거나 이름이 다르므로 공통 설정에 그대로 넣지 않는다.
