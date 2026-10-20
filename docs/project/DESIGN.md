# 실전 프로젝트 설계 — 데이터 · 인프라 (배치 점검 + RCA) / sykim

## 1. 목표
기존 `batch-check` Skill 로 2026-09-15 하루치 배치 이상을 찾아 `docs/batch-check/report.md` 를 남기고, 이어서 기존 `rca` Skill 로 그날 이상의 1차 원인 리포트를 `docs/rca/` 에 만든다.

## 2. 재료

판정 기준일: 2026-10-20, 브랜치 `day3/sykim` 작업 트리. "미커밋"은 `git status` 에서 `??`(추적 안 됨)로 나온 것이다.

| 재료 | 있음/없음 | 경로 |
|---|---|---|
| 배치 점검 Skill (점검 기준 4개 · 근거 줄번호 · 리포트 형식) | 있음(미커밋) | `.claude/skills/batch-check/SKILL.md` — 정본. `harness-baseline/dot-claude/skills/batch-check/` 는 복사본이라 쓰지 않는다 |
| RCA Skill (수집 범위 → 타임라인 → 가설 → 반증 → 리포트 → 마스킹) | 있음(커밋 `2f9dd6b`) | `.claude/skills/rca/SKILL.md` |
| RCA 마스킹 규칙 · 저장 전 확인 명령 | 있음 | `.claude/skills/rca/masking-rules.md` |
| RCA 리포트 예시 | 있음 | `docs/rca/a-connection-pool.md`, `docs/rca/b-deploy-5xx.md` |
| 배치 로그 10일치(2026-09-08 ~ 09-17, job 5개) | 있음 | `pipeline-samples/batch-logs/*.log` (읽기 전용). 대상일 `2026-09-15.log` 는 47줄 |
| 적재 건수 CSV | 있음 | `pipeline-samples/datamart-counts.csv` (읽기 전용) |
| 같은 날 중복 적재 장애 로그 묶음 | 있음 | `incident-logs/c-batch-duplicate/batch-job.log`(115줄) · `datamart-counts.csv`(25줄) (읽기 전용). `batch-job.log:99` 의 `run_id=ldm-20260916-0258-8963 lock=held_by run_id=ldm-20260916-0200-edf5` 가 배치 로그의 09-15 `load_datamart` 재실행과 같은 run_id |
| 이전 배치 점검 리포트(10일 전체, 이상 9건 중 5건이 09-15) | 있음(미커밋) | `docs/batch-check/report.md` — 1단계에서 덮어써진다(4절 Q1) |
| 이번 RCA 결과 파일 | **없음 → 새로 만들 것**(rca Skill 이 생성) | `docs/rca/c-batch-duplicate.md` |
| 위험 명령 차단 · 시크릿 스캔 Hook | 있음(미커밋) | `hooks/block-dangerous.mjs`, `hooks/secret-scan.mjs`, 실행기 `scripts/hook-node.sh` |
| 배치 계산 스크립트(`batch_check.py` 등) | 없음 · 이번에 만들지 않음 | — |
| IaC 리뷰 Sub-agent · Terraform 샘플 | 있음 · 이번에 쓰지 않음 | `.claude/agents/iac-reviewer.md`, `pipeline-samples/terraform/` |
| characterization 테스트 | 있음 · 이 트랙에서 쓰지 않음 | `characterization/` |
| 현행 API · 화면 | 있음 · 이 트랙에서 쓰지 않음 | `modern/api/`, `modern/web/` |

## 3. 범위

### 한다
1. `batch-check` Skill 을 고치지 않고 실행해, 판정 대상을 2026-09-15 하루로 한 `docs/batch-check/report.md` 를 남긴다.
2. `rca` Skill 을 고치지 않고 `c-batch-duplicate` 로 실행해 `docs/rca/c-batch-duplicate.md` 1건을 만든다.
3. 두 리포트가 같은 사건(09-15 `load_datamart` 중복 실행)을 가리키는지, 원본 로그가 그대로인지 확인한다.

### 하지 않는다
- **`batch_check.py` 같은 계산 · 판정 스크립트 작성** — 기존 Skill 실행이 전제이고, 계산은 Skill 이 정한 대로 세션 안에서 명령으로 한다.
- **2026-09-15 외 다른 날짜 판정 · 리포트** — 범위는 이상 일자 하루치, 리포트 1건이다. 다른 날 로그는 기준값 계산에만 쓴다.
- **`incident-logs/` 의 a · b · d 시나리오 RCA** — 이번 배치 이상과 다른 사건이다.
- **Skill 파일(`.claude/skills/batch-check` · `rca`) 수정** — 그대로 실행하는 것이 전제다. 문제가 보이면 4절에 적고 고치지 않는다.
- **데이터마트 중복 행 정리 · 수정안 적용** — rca Skill 은 수정안을 제안만 하고, 코드 · 데이터는 바꾸지 않는다.
- **DB 를 띄워 `dm.daily_submission_fact` 등을 조회** — 40분 안에 compose 기동까지 넣지 않는다. 필요한 확인은 RCA "확인하지 못한 것"에 남긴다.

## 4. 미해결 질문
- **Q1.** 기존 `docs/batch-check/report.md`(10일 전체, 미커밋)는 1단계에서 덮어써진다. 덮어쓰기 전에 커밋해 둘지? 이 설계는 1단계 첫 할 일로 커밋을 넣었다.
- **Q2.** `batch-check` Skill 의 기준 ①(다른 날 대비 실행 여부) · ③(직전 7일 평균) · ④(전체 기간 중앙값)는 다른 날 로그가 있어야 계산된다. 그래서 "하루치 점검"을 **로그 폴더는 그대로 넘기고 판정 · 리포트 대상만 2026-09-15 로 제한**으로 해석했다. 이 해석이 맞는지?
- **Q3.** `rca` Skill 은 `incident-logs/<폴더>/` 만 대상으로 받는다. 09-15 는 같은 사건의 로그 묶음 `incident-logs/c-batch-duplicate/` 가 있어 Skill 을 고치지 않고 연결된다. 그러나 `batch-job.log` 가 `pipeline-samples/batch-logs/2026-09-15.log` 와 같은 원본인지(줄 수 115 vs 47로 다름)는 확인하지 않았다.
- **Q4.** "1차 원인 리포트"의 깊이: `rca` Skill 5절 7항목(요약 ~ 확인하지 못한 것)까지 전부 쓰는지, `b-deploy-5xx.md` 처럼 3절(가설)까지만 쓰는지? 이 설계는 로그가 140줄로 작아 7항목 전부로 잡았다.
- **Q5.** `batch-check` Skill 에는 마스킹 규칙이 없어, 기존 `report.md` 에도 IP(`10.20.7.31` 등)가 그대로 있다. 이번 리포트도 같을 것이다. 이번 범위에서 손대지 않는다는 판단이 맞는지?

## 5. 단계

### 1단계 — 2026-09-15 배치 점검
- **할 일**: 기존 `report.md` 를 커밋해 둔다(Q1). 새 세션에서 아래처럼 Skill 을 실행한다.
  ```
  /batch-check pipeline-samples/batch-logs
  점검 대상 일자는 2026-09-15 하루다. 다른 날 로그와 CSV 는 기준값(직전 7일 평균 · 중앙값) 계산에만 쓰고, 이상 목록에는 2026-09-15 행만 넣는다.
  ```
- **만들거나 고칠 파일**: `docs/batch-check/report.md`(Skill 이 덮어씀)
- **확인 방법**:
  ```bash
  grep -c '^| 2026-09-15 |' docs/batch-check/report.md                          # 1 이상
  grep -cE '^\| 2026-09-(0[89]|1[0-46-9]) \|' docs/batch-check/report.md         # 0
  grep -nE '^\| 2026-09-15 \| load_datamart \|.*중복' docs/batch-check/report.md  # 1줄 이상
  ```

### 2단계 — c-batch-duplicate 1차 RCA
- **할 일**: 1단계 리포트의 09-15 `load_datamart` 중복 실행 · 적재 건수 이상을 출발점으로, 새 세션에서 `/rca c-batch-duplicate` 를 실행한다.
- **만들거나 고칠 파일**: `docs/rca/c-batch-duplicate.md`(Skill 이 새로 만듦)
- **확인 방법**:
  ```bash
  ls docs/rca/c-batch-duplicate.md
  grep -nE '요약|원인|근거 로그 줄|수정안|재발 방지|모니터링 항목|확인하지 못한 것' docs/rca/c-batch-duplicate.md
  tail -1 docs/rca/c-batch-duplicate.md        # "마스킹 적용:" 으로 시작
  ```

### 3단계 — 연결 · 마스킹 · 원본 확인
- **할 일**: 두 리포트가 같은 run 을 가리키는지, RCA 에 원본 값이 남지 않았는지, 원본 로그가 바뀌지 않았는지 확인한다.
- **만들거나 고칠 파일**: 없음
- **확인 방법**:
  ```bash
  grep -c '0258-8963' docs/batch-check/report.md docs/rca/c-batch-duplicate.md   # 두 파일 모두 1 이상 (재실행 run_id 끝자리. 기존 리포트는 `…0258-8963` 처럼 줄여 씀)
  grep -nE '([0-9]{1,3}\.){3}[0-9]{1,3}|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}|STU-[0-9]+|Bearer [A-Za-z0-9._-]+|eyJ[A-Za-z0-9_-]{10,}|(password|passwd|pwd)=[^ ]+| -p[^ [`]+' docs/rca/c-batch-duplicate.md   # 출력 없음
  git status --short pipeline-samples incident-logs   # 출력 없음
  ```

## 6. 완성 기준 (Day 4-1에서 점검)
- [ ] `docs/batch-check/report.md` 이상 목록 표에 `| 2026-09-15 |` 로 시작하는 행이 1개 이상 있고, 다른 날짜로 시작하는 행은 0개다.
- [ ] 그 표에 `2026-09-15 · load_datamart · 중복 실행` 행이 있고, 근거 칸에 `2026-09-15.log:<줄번호>` 가 1개 이상 있다.
- [ ] `docs/rca/c-batch-duplicate.md` 가 있고, rca Skill 5절 7항목(요약 · 원인 · 근거 로그 줄 · 수정안 · 재발 방지 · 모니터링 항목 · 확인하지 못한 것)이 모두 나온다.
- [ ] RCA 리포트에 `masking-rules.md` 3절 확인 명령을 돌린 출력이 0줄이고, 마지막 줄이 `마스킹 적용:` 으로 시작한다.
- [ ] 재실행 run_id 끝자리 `0258-8963` 이 두 리포트 모두에 나오고, `git status --short pipeline-samples incident-logs` 출력이 0줄이다.
