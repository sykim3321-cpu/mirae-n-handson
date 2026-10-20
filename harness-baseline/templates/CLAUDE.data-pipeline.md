# CLAUDE.md — Python 서비스 · 데이터 파이프라인 팀 템플릿

이 파일은 `templates/CLAUDE.data-pipeline.md` 입니다. 저장소 루트의 `CLAUDE.md` 로 옮겨 쓰기 전에 팀 사정에 맞지 않는 줄을 고치세요.
대상: FastAPI 서비스 + LangChain 기반 문항 처리 + 일 배치 적재. 이 저장소에서는 `pipeline-samples/`(배치 로그 · 적재 건수)와 `specs/starters/python/`(FastAPI 골격)이 재료다.

## 1. 실행 · 테스트 명령

```bash
python3 -m venv .venv && source .venv/bin/activate && pip install -r requirements.txt   # 처음 한 번 (Python 3.12)
pytest                                              # 전체 테스트. 외부 서비스 · 네트워크 없이 통과해야 한다
uvicorn app.main:app --reload --port 8000           # 로컬 실행 → http://localhost:8000
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8000/health
```

- 가상 환경은 `.venv/` 하나만 쓴다. 전역 `pip install` 금지.
- 작업이 끝나면 `pytest` 를 실행하고 통과 · 실패 수를 답변에 적는다. 실행하지 않았으면 "실행하지 않음"이라고 쓴다.

## 2. 배치 · 적재 점검 명령

배치 로그는 `pipeline-samples/batch-logs/YYYY-MM-DD.log`(하루 한 파일), 적재 결과는 `pipeline-samples/datamart-counts.csv`(머리글 있음).

```bash
ls pipeline-samples/batch-logs/                                    # 점검 대상 일자 목록
grep -n "load_datamart" pipeline-samples/batch-logs/2026-09-15.log # 특정 job 의 실행 기록
grep -c "status=SUCCESS" pipeline-samples/batch-logs/2026-09-15.log
head -5 pipeline-samples/datamart-counts.csv                      # 머리글 · 열 순서 확인
awk -F, 'NR>1 && $2=="load_datamart" {print $1, $3}' pipeline-samples/datamart-counts.csv
```

점검 기준(순서 고정):
1. **실행 여부** — 매일 돌아야 하는 job 이 그날 로그에 없거나, 시작만 있고 종료 기록이 없으면 이상.
2. **중복 실행** — 같은 날 같은 job 의 성공 기록이 두 번 이상이면 이상(적재 건수 2배의 원인).
3. **적재 건수** — 0건이거나 직전 7일 평균에서 30% 넘게 벗어나면 이상. 평균은 `awk` 로 계산하고 계산식을 답변에 적는다.
4. **지연** — 소요 시간이 평소(직전 7일 중앙값)의 2배를 넘으면 이상.

- 이상마다 근거를 `파일:줄번호` 로 적는다(예: `pipeline-samples/batch-logs/2026-09-15.log:42`). 로그에 없는 원인은 추측하지 않는다.
- 점검 리포트는 `docs/batch-check/report.md`. 이상이 없으면 파일을 만들지 않는다.

## 3. 코딩 컨벤션

### 서비스 구조
- 계층: `app/api/`(라우터) → `app/services/`(업무 로직) → `app/repositories/`(DB · 파일 접근). 라우터에서 DB 세션이나 SQL 을 직접 다루지 않는다.
- 요청 · 응답 모델은 `app/schemas/` 의 Pydantic 모델. 딕셔너리를 그대로 반환하지 않는다.
- 설정은 `app/config.py` 의 `Settings`(pydantic-settings) 한 곳에서 환경 변수로 읽는다. 코드에 접속 문자열 · 키를 쓰지 않는다.
- 타입 힌트는 모든 public 함수에 붙인다. `Any` 는 사유 주석 없이 쓰지 않는다.

### LLM · 프롬프트 · 평가 자산
- 프롬프트는 코드 문자열이 아니라 `app/prompts/<이름>.md` 파일에 둔다. 파일 첫 줄에 버전(`v1`, `v2` …)을 적고, 바꿀 때 버전을 올린다.
- 체인 · 에이전트 구성은 `app/chains/` 에 두고, 모델 이름 · 온도는 `Settings` 에서 읽는다. 코드에 모델 이름을 하드코딩하지 않는다.
- 평가 데이터셋은 `eval/datasets/<이름>.jsonl`, 평가 스크립트는 `eval/run_eval.py`. 프롬프트를 바꾸면 같은 데이터셋으로 `python eval/run_eval.py --prompt <이름>` 을 돌려 전후 점수를 답변에 적는다.
- 학생 · 교사 식별자(`STU-…`, `teacher-…`), 이메일을 프롬프트나 평가 데이터에 원문으로 넣지 않는다. 별칭으로 바꾼다.
- LLM 호출은 테스트에서 실제로 하지 않는다. `tests/fakes/` 의 가짜 모델을 쓴다.

### 배치 job
- job 하나 = `app/jobs/<job_name>.py` 파일 하나. 진입 함수 이름은 `run(target_date: date) -> JobResult`.
- 모든 job 은 **재실행 가능(idempotent)** 해야 한다. 같은 날짜로 두 번 돌려도 건수가 두 배가 되지 않게 upsert 또는 사전 삭제를 쓴다.
- 로그 한 줄에 `job=<이름> date=<기준일> run_id=<실행 ID> status=<START|SUCCESS|FAIL> rows=<건수>` 를 반드시 포함한다. 점검 명령이 이 형식을 읽는다.
- 실패는 삼키지 않는다. 예외를 잡았으면 `status=FAIL` 로그를 남기고 0 이 아닌 종료 코드로 끝낸다.

### 테스트
- 동작을 바꾸는 변경에는 대응하는 `tests/` 파일이 있어야 한다. 라우터는 `httpx.AsyncClient` 로, 서비스는 단위 테스트로.
- 테스트는 네트워크 · 실제 DB · 실제 LLM 없이 돈다. 외부 의존은 전부 가짜 객체로 바꾼다.

## 4. 금지 사항

- `legacy/` 는 분석 · 이관 대상이다. 허락 없이 수정하지 않는다.
- `requirements.txt` 변경(패키지 추가 · 업그레이드)은 먼저 사람에게 묻는다. 이유와 대안을 같이 적는다.
- `.env`, `.env.*` 를 읽거나 만들지 않는다. 키 · 접속 문자열을 코드 · 노트북 · 테스트 픽스처에 넣지 않는다.
- 운영 데이터베이스 · 운영 버킷에 접속하는 코드 · 명령을 만들지 않는다. 데이터 확인은 `pipeline-samples/` 의 샘플과 로컬 compose DB(`readonly` 계정)로 한다.
- 적재 대상 테이블을 `DROP` · `TRUNCATE` 하는 코드를 만들지 않는다. 재적재는 기준일 범위 `DELETE` 후 `INSERT` 로 한다.
- 로그에 학생 식별자 · 이메일 · 토큰을 원문으로 남기지 않는다.
- Jupyter 노트북(`.ipynb`)을 서비스 코드로 커밋하지 않는다. 검증이 끝난 코드는 `app/` 으로 옮긴다.
- 요청받지 않은 파일을 "정리" 명목으로 고치지 않는다.

## 5. 아키텍처 안내

```
app/
├── main.py         FastAPI 앱, /health
├── api/            라우터 (문항 처리, 리포트)
├── services/       업무 로직
├── repositories/   DB · 파일 접근
├── schemas/        Pydantic 모델
├── chains/         LangChain 체인 구성
├── prompts/        프롬프트 파일 (버전 표시)
└── jobs/           배치 job (run(target_date))
eval/               평가 데이터셋 · 실행 스크립트
tests/              pytest. fakes/ 에 가짜 LLM · 가짜 저장소
```

- 데이터 흐름: 배치 job → 데이터마트 적재 → `datamart-counts.csv` 로 건수 기록 → 아침 점검. 점검이 이상을 찾으면 원인 분석(`docs/rca/`)으로 넘긴다.
- 도메인 용어는 문항 은행과 같게 쓴다: 문항 `item`, 단원 `unit`, 난이도 `level`(1~5), 태그 `tag`, 학생 `STU-<숫자>`.

## 6. 완료 기준

- [ ] `pytest` 가 통과했고 통과 · 실패 수를 답변에 적었다
- [ ] 바꾼 동작마다 대응하는 테스트가 있고, 테스트가 네트워크 · 실제 LLM 없이 돈다
- [ ] 프롬프트를 바꿨다면 버전을 올리고 평가 전후 점수를 적었다
- [ ] 새 · 수정 job 이 같은 날짜로 두 번 돌아도 건수가 같음을 테스트로 보였다
- [ ] `requirements.txt` · 접속 설정이 바뀌지 않았다(바뀌었다면 사전 승인이 있었다)
- [ ] 변경 파일이 요청 범위 안에 있다
