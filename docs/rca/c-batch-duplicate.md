# RCA — c-batch-duplicate (2026-09-15 분 load_datamart 중복 적재)

## 요약

- **언제**: 2026-09-16 02:58:42 KST 에 `load_datamart`(대상일 2026-09-15)의 수동 재실행이 lock 을 우회해 시작됐습니다. 1차 run 은 03:12:41, 재실행 run 은 03:15:31 에 둘 다 SUCCESS 로 끝났습니다. 03:30:04 에 `build_report_cache` 가 2배 입력을 읽었고, 이상은 07:00:04 dq-check WARN 으로 처음 알려졌습니다.
- **무엇이**: `dm.daily_submission_fact` 의 2026-09-15 적재 건수가 369,064 건으로, 직전 7일 평균 184,617.43 건 대비 +99.91% 였습니다. 두 run 이 각각 184,532 건을 적재했고, 합계가 정확히 2배(184,532 × 2 = 369,064)입니다.
- **직접 원인**: 1차 run 이 아직 진행 중일 때 `force=true` 재실행이 lock 을 무시하고 시작됐습니다. 두 run 모두 `mode=append` 로 같은 테이블 · 같은 대상일에 적재했습니다.
- **촉발 조건**: 1차 run 이 평시(약 201 rows/s)보다 느리게(약 30~80 rows/s) 진행했습니다. 그래서 02:40 p95 초과 WARN 과 02:45 SLA `NOT_FINISHED` 알림이 났고, 운영자가 "멈춘 것으로 보고" 재실행했습니다. 그러나 1차 run 의 progress 는 재실행 전까지 계속 늘고 있었습니다(64% @02:47:15).
- **확신 수준: 높음(중복 적재의 기전) / 낮음(1차 run 이 느렸던 이유)**. 중복 적재의 기전은 lock 우회 · append 2회 · 건수 정확히 2배가 로그에 모두 직접 찍혀 있어 높음으로 봤습니다. 다만 batch-runner 소스 · 설정은 저장소에 없어 코드와 대조하지 못했고, 테이블의 실제 중복 행도 DB 로 확인하지 않았습니다(9절). 1차 run 이 느렸던 이유는 로그에 DB · 서버 지표가 없어 판단하지 못했습니다.

## 1. 수집 범위

> 대상: `incident-logs/c-batch-duplicate/` 원본 2개 파일 (수정하지 않음)
> 방법: `wc -l`, `head`, `tail`, `grep -c`, `grep -n`, `sed -n`, `awk` 집계만 사용했습니다. 파일을 통째로 읽지 않았습니다.
> 이 절은 범위만 정합니다. 원인은 다루지 않습니다.

### 1.1 파일별 기록 기간 · 줄 수 · 시각 형식 · 타임존

| 파일 | 줄 수 | 기록 기간 (KST) | 시각 형식 (예) | 타임존 | KST 변환 |
|---|---:|---|---|---|---|
| `batch-job.log` | 115 (INFO 99 · WARN 16, 연속행 없음) | 2026-09-09 01:30:02 ~ 2026-09-16 07:00:04 | `2026-09-16 02:58:42.465 +0900` (ms, 오프셋 포함) | +0900 (115줄 모두, `grep -c ' +0900 '` = 115) | 그대로 사용 |
| `datamart-counts.csv` | 25 (헤더 1 + 데이터 24) | 대상일 2026-09-08 ~ 09-15 (`loaded_at` 2026-09-09T01:40:03 ~ 09-16T03:33:42) | `2026-09-16T03:15:31+09:00` (ISO-8601, 초, 오프셋 포함) | +09:00 | 그대로 사용 |

**KST 변환 규칙**

| 원본 | 규칙 |
|---|---|
| `batch-job.log` 줄 앞 시각 | 오프셋 +0900 → 변환 없음 |
| `datamart-counts.csv` `loaded_at` | 오프셋 +09:00 → 변환 없음. `loaded_at` 은 해당 job 의 마지막 SUCCESS 시각과 초 단위로 같습니다(예: `datamart-counts.csv:24` 03:15:31 = `batch-job.log:110` 03:15:31) |
| `date=` 필드 · CSV `date` | **실행일이 아니라 대상일**입니다. 2026-09-15 분은 2026-09-16 새벽에 실행됩니다 |

**파일 구성과 다른 로그와의 관계**

- `batch-job.log` 의 job 은 3개뿐입니다(`extract_submissions` 17줄 · `load_datamart` 79줄 · `build_report_cache` 17줄, 그 밖에 job 필드 없는 batchctl 2줄). 날짜별로는 09-09 ~ 09-15 실행일이 각 12줄, 09-16 실행일이 31줄입니다.
- 출처 태그는 `batch-runner` 111줄 · `batchctl` 2줄 · `sla-monitor` 1줄 · `dq-check` 1줄입니다(호스트는 모두 10.20.x.x).
- 09-16 실행일 31줄(`batch-job.log:85`~`:115`)을 `pipeline-samples/batch-logs/2026-09-15.log` 와 줄 단위로 비교했습니다(`grep -F -x -f`). 31줄 중 30줄이 완전히 같고, 다른 1줄은 `batch-job.log:98`(batchctl 의 API 호출 줄)입니다. 이 줄은 배치 로그 쪽에 없습니다. CSV 25줄은 `pipeline-samples/datamart-counts.csv` 에 모두 그대로 있습니다.

**집계 함정**

- 스택트레이스 연속행은 없습니다. 줄 수 = 엔트리 수입니다.
- 이벤트성 로그입니다. 09-09 ~ 09-16 은 job 이 실행된 시각대(01:30 ~ 03:34, 07:00)만 기록돼 있고, 그 사이 공백은 "수집 안 됨"이 아니라 "이벤트 없음"으로 봅니다. 하지만 이 파일에 없는 job(`build_item_stats` · `export_grades`)의 기록은 이 묶음 범위 밖입니다.
- `sla-monitor` 줄(`:95`)은 `last_status=RUNNING` 을 담고 있어 `status=RUNNING` 으로 grep 하면 같이 잡힙니다. progress 집계에서는 제외했습니다.
- `RUNNING ... rows=` 는 누적 건수입니다. 속도는 같은 run_id 의 앞 progress 줄과의 차이로 계산했습니다.

### 1.2 평시 수준과 이상 구간

| 항목 | 평시 (대상일 09-08 ~ 09-14) | 2026-09-15 분 |
|---|---|---|
| `load_datamart` SUCCESS 횟수 / 일 | 1 (7일 모두) | **2** (`:108`, `:110`) |
| `load_datamart` 적재 건수 (CSV) | 평균 184617.43 (n=7) | **369064** (+99.91%, `datamart-counts.csv:24`) |
| `load_datamart` 소요 시간 | 중앙값 923s (9개 run 기준, 평시 최대 977s) | 1차 **4357s** (4.72배) / 재실행 1008s (1.09배) |
| `load_datamart` progress 속도 | 201.5 rows/s (예: 대상일 09-14 `:78`~`:81`) | 1차 80.1 → 34.2 → 31.7 → 30.1 → 34.5 → 51.9 → 50.2 rows/s / 재실행 187.5 → 183.1 → 178.8 → 182.4 rows/s |
| `trigger=manual` · `force` · `lock=bypassed` 줄 | 0 | 5 (`:97`, `:98`, `:99`, `:100`, `:111`) |
| WARN | `step=validate skipped=1` 1건 / 일 (`:5`, `:17`, `:29`, `:41`, `:53`, `:65`, `:77`) | 9건 (`:86`, `:90`, `:94`, `:95`, `:99`, `:102`, `:111`, `:113`, `:115`) |

### 1.3 분석 창

- **2026-09-16 01:30:02 ~ 07:00:04 KST** (`batch-job.log:85`~`:115`). 앞 여유로 같은 날 첫 job(`extract_submissions` 시작)부터 잡았습니다. 뒤는 마지막 기록인 dq-check WARN 까지입니다. 그 뒤로는 로그가 없어 복귀는 확인되지 않습니다.
- 비교 기준으로 직전 실행일(2026-09-15 실행, `:73`~`:84`)을 함께 봤습니다.

## 2. 타임라인

> 분석 창 안의 사실만 적었습니다. 모든 시각은 KST, 실행일 2026-09-16 입니다.

| 시각 | 출처 | 사실 | 근거 |
|---|---|---|---|
| 01:30:02.724 | batch-runner | `extract_submissions` 대상일 2026-09-15 START (cron, `lock=acquired`) | `batch-job.log:85` |
| 01:44:07.724 | batch-runner | WARN `rows=19204 note="source read slow: grade-db 10.20.x.x replica lag 412s"` | `batch-job.log:86` |
| 01:57:50.724 | batch-runner | `extract_submissions` SUCCESS `rows=41573 elapsed=1668s` | `batch-job.log:87` |
| 02:00:03.643 | batch-runner | `load_datamart` 1차 run `ldm-20260916-0200-edf5` START (cron, `lock=acquired`, pid=46735) | `batch-job.log:88` |
| 02:00:04.843 | batch-runner | 1차 run `step=read source=extract_submissions/2026-09-15 mode=append target=dm.daily_submission_fact` | `batch-job.log:89` |
| 02:00:06.043 | batch-runner | 1차 run WARN `step=validate skipped=1 student_id=학생A reason="class_id is NULL"` | `batch-job.log:90` |
| 02:09:39.643 | batch-runner | 1차 run `progress=25% rows=46133` | `batch-job.log:91` |
| 02:21:21.643 | batch-runner | 1차 run `progress=38% rows=70122` | `batch-job.log:92` |
| 02:33:57.643 | batch-runner | 1차 run `progress=51% rows=94111` | `batch-job.log:93` |
| 02:40:03.943 | batch-runner | 1차 run WARN `note="elapsed 40m exceeds p95 17m" rows=94111` | `batch-job.log:94` |
| 02:45:00.512 | sla-monitor | WARN `status=NOT_FINISHED sla=02:45 last_status=RUNNING run_id=ldm-20260916-0200-edf5 notify=[EMAIL]` | `batch-job.log:95` |
| 02:47:15.643 | batch-runner | 1차 run `progress=64% rows=118100` | `batch-job.log:96` |
| 02:58:41.077 | batchctl | `rerun requested job=load_datamart date=2026-09-15 operator=[EMAIL] reason="SLA 알림 — 멈춘 것으로 보고 재실행" force=true` | `batch-job.log:97` |
| 02:58:41.287 | batchctl | `POST http://10.20.x.x:8793/api/v1/jobs/load_datamart/runs body={"date":"2026-09-15","force":true} header="Authorization: [TOKEN]" -> 201 Created` | `batch-job.log:98` |
| 02:58:42.465 | batch-runner | 재실행 run `ldm-20260916-0258-8963` WARN `lock=held_by run_id=ldm-20260916-0200-edf5 pid=46735 since=02:00:03 action=ignored reason="--force"` | `batch-job.log:99` |
| 02:58:42.477 | batch-runner | 재실행 run START `trigger=manual operator=[EMAIL] lock=bypassed` pid=47948 | `batch-job.log:100` |
| 02:58:43.765 | batch-runner | 재실행 run `step=read ... mode=append target=dm.daily_submission_fact` | `batch-job.log:101` |
| 02:58:44.965 | batch-runner | 재실행 run WARN `step=validate skipped=1 student_id=학생A` | `batch-job.log:102` |
| 02:58:51.643 | batch-runner | 1차 run `progress=77% rows=142089` | `batch-job.log:103` |
| 03:02:48.465 | batch-runner | 재실행 run `progress=25% rows=46133` | `batch-job.log:104` |
| 03:06:33.643 | batch-runner | 1차 run `progress=90% rows=166078` | `batch-job.log:105` |
| 03:07:00.465 | batch-runner | 재실행 run `progress=50% rows=92266` | `batch-job.log:106` |
| 03:11:18.465 | batch-runner | 재실행 run `progress=75% rows=138399` | `batch-job.log:107` |
| 03:12:41.043 | batch-runner | 1차 run SUCCESS `rows=184532 elapsed=4357s` | `batch-job.log:108` |
| 03:12:41.081 | batch-runner | 1차 run `lock=released` | `batch-job.log:109` |
| 03:15:31.365 | batch-runner | 재실행 run SUCCESS `rows=184532 elapsed=1008s` | `batch-job.log:110` |
| 03:15:31 | (CSV) | `2026-09-15,load_datamart,369064,2026-09-16T03:15:31+09:00` | `datamart-counts.csv:24` |
| 03:15:31.400 | batch-runner | 재실행 run WARN `lock=release_skipped reason="not owner (bypassed)"` | `batch-job.log:111` |
| 03:30:01.233 | batch-runner | `build_report_cache` START (cron, `lock=acquired`) | `batch-job.log:112` |
| 03:30:04.333 | batch-runner | WARN `input=dm.daily_submission_fact input_rows=369064 note="input_rows differs from extract_submissions rows=41573 x avg fan-out"` | `batch-job.log:113` |
| 03:33:42.433 | batch-runner | `build_report_cache` SUCCESS `rows=1271 input_rows=369064` | `batch-job.log:114` |
| 07:00:04.610 | dq-check | WARN `table=dm.daily_submission_fact date=2026-09-15 rows=369064 avg_7d=184617 deviation=+99% notify=[EMAIL]` | `batch-job.log:115` |

## 3. 가설과 검증

> 가장 눈에 띄는 신호(dq-check `deviation=+99%`, `build_report_cache` 입력 2배)는 **증상**으로 분류합니다. 가설은 "왜 2026-09-15 분만 2배가 됐는가"를 설명해야 합니다.

### 3.1 가설 표

| 가설 | 축 | 내용 | 지지 근거 | 반증 조건 | 확인 방법 |
|---|---|---|---|---|---|
| **A** | 운영 · 설정 (수동 재실행 + lock 우회) | 1차 run 이 끝나기 전에 `force` 재실행이 lock 을 무시하고 같은 대상일을 append 로 한 번 더 적재했다 | `:97`~`:100`, `:101`, `:108`, `:110`, `:111` | 두 run 중 하나가 SUCCESS 가 아니거나, 두 run 의 rows 합이 CSV 값과 다르거나, 재실행이 append 가 아니면 기각 | `grep -n 'run_id=ldm-20260916' batch-job.log \| grep -E 'status=SUCCESS\|mode='` |
| **B** | 데이터 (원천 중복) | 앞 단계 `extract_submissions` 산출물 자체가 2배여서, 한 번 적재해도 2배가 됐다 | `:113` WARN `input_rows differs from extract_submissions rows=41573` | `extract_submissions` rows 가 평시 수준이고 각 load run 의 rows 도 평시 수준이면 기각 | `awk -F, '$2=="extract_submissions"' datamart-counts.csv`, `grep -n 'status=SUCCESS' batch-job.log \| grep load_datamart` |
| **C** | 스케줄러 (cron 이중 트리거) | 스케줄러가 같은 대상일에 cron 을 두 번 발화했다 | `:88`, `:100` (START 2회) | 두 번째 START 가 `trigger=cron` 이 아니면 기각 | `grep -n 'job=load_datamart.*status=START' batch-job.log \| grep -o 'trigger=[a-z]*'` |
| **D** | DB · 부하 (촉발 조건) | 1차 run 이 원천 · 적재 대상 DB 부하로 느려져 SLA 알림이 났고, 이것이 재실행을 불렀다 | `:86` replica lag, `:91`~`:96` 낮은 progress 속도, `:94`, `:95` | 1차 run 이 START 직후부터 평시 속도였으면 기각 | progress 줄 간 속도 `awk` 집계 (3.2) |
| **E** | 판단 (1차 run 정지) | 1차 run 이 실제로 멈춰 있었다(재실행 사유 `:97` 의 전제) | `:95` `NOT_FINISHED`, `:97` `reason="... 멈춘 것으로 보고 ..."` | 재실행 직전까지 1차 run progress 가 계속 늘었으면 기각 | `grep -n 'run_id=ldm-20260916-0200-edf5.*progress' batch-job.log` |

### 3.2 로그 반증 실행 기록

**A · B · C — SUCCESS · mode · trigger 확인**

```
$ grep -n 'job=load_datamart.*status=START' batch-job.log | ... trigger
88: ... run_id=ldm-20260916-0200-edf5 status=START rows=0 trigger=cron lock=acquired
100: ... run_id=ldm-20260916-0258-8963 status=START rows=0 trigger=manual lock=bypassed
(대상일 09-08 ~ 09-14 의 :3 :15 :27 :39 :51 :63 :75 는 모두 trigger=cron lock=acquired)

$ grep -n 'mode=' batch-job.log | grep -vc 'mode=append'
0

$ (CSV load_datamart ↔ 로그 SUCCESS rows 합, 대상일별 join)
2026-09-08 182615 1 182615 일치
...
2026-09-14 186058 1 186058 일치
2026-09-15 369064 2 369064 일치      # SUCCESS 2회, 184532 + 184532

$ awk -F, '$2=="load_datamart" && $1<"2026-09-15"{s+=$3;n++} END{...}' datamart-counts.csv
avg=184617.43 n=7 dev=99.91%
```

- `extract_submissions` 대상일 2026-09-15 rows=41573(`:87`, `datamart-counts.csv:23`)은 09-08 ~ 09-14 범위(41203 ~ 43665, `datamart-counts.csv:2`~`:20` 중 extract 행) 안입니다. 각 load run 의 rows=184532 도 평시 범위(182615 ~ 186319) 안입니다.

**D · E — progress 속도 (같은 run_id 의 앞 progress 줄과의 차이, sla-monitor 줄 제외)**

```
대상일 09-14  ldm-20260915-0200-ae84  L78~L81  각 230.8s 간격  201.5 rows/s
1차 run      ldm-20260916-0200-edf5  L91 80.1 / L92 34.2 / L93 31.7 / L96 30.1 / L103 34.5 / L105 51.9 / L108 50.2 rows/s
재실행 run    ldm-20260916-0258-8963  L104 187.5 / L106 183.1 / L107 178.8 / L110 182.4 rows/s
```

### 3.3 판정

| 가설 | 판정 | 이유 |
|---|---|---|
| **A** | **채택** | 두 run 모두 SUCCESS(`:108`, `:110`) · 두 run 모두 `mode=append`(`:89`, `:101`) · 합계 = CSV 값(`datamart-counts.csv:24`). 재실행은 lock 보유를 알고도 `action=ignored reason="--force"`(`:99`)로 시작했습니다. 반증 조건에 하나도 걸리지 않았습니다 |
| **B** | 기각 | extract rows · load run 별 rows 가 모두 평시 범위입니다. `:113` WARN 은 원천이 아니라 load 결과가 2배라는 증상입니다 |
| **C** | 기각 | 두 번째 START 는 `trigger=manual`(`:100`)이고, 직전에 batchctl 의 수동 요청(`:97`, `:98`)이 있습니다 |
| **D** | 판단 불가 (로그 부족) | 1차 run 은 재실행 전(02:00 ~ 02:58)부터 평시의 15~40% 속도였습니다. 따라서 재실행과의 경합이 원인은 아닙니다. 하지만 느린 이유는 로그에 없습니다. `:86` replica lag 는 extract 단계 WARN 이고, extract 는 load 시작 2분 전에 끝났습니다(`:87` 01:57:50, `:88` 02:00:03). 그래서 둘의 인과는 로그만으로 판단할 수 없습니다 |
| **E** | 기각 | 1차 run progress 는 51%(`:93` 02:33:57) → 64%(`:96` 02:47:15) → 77%(`:103` 02:58:51)로 재실행 전후에 계속 늘었습니다. SLA 알림(`:95`)은 `last_status=RUNNING` 만 담고 있고 progress 정보는 없습니다 |

**코드 · 설정 대조**: batch-runner · batchctl · sla-monitor 의 소스와 설정 파일은 저장소에 없습니다. `load_datamart` · `daily_submission_fact` · `lock=bypassed` 로 저장소 전체를 검색했지만 로그 · 문서만 나왔습니다. 그래서 다음 세 가지는 **전제**로 둡니다. 로그 문자열과 다르게 동작할 가능성은 9절에 남겼습니다.
- `--force` 가 lock 보유자 생존 여부와 무관하게 lock 을 무시한다
- `mode=append` 가 대상일 기존 행을 지우지 않는다
- SLA 알림이 progress 를 보지 않는다

**의심 동작 (판정과 별개, 고치지 않음)**

- `load_datamart` 가 매 run 마다 `step=validate skipped=1 student_id=학생A reason="class_id is NULL"` 을 남깁니다(대상일 09-08 ~ 09-15 매일, `:5` ~ `:77` · `:90` · `:102`). 같은 학생이 계속 적재에서 빠지고 있습니다.
- 재실행 run 은 lock 을 해제하지 않았습니다(`:111` `release_skipped`). 반면 1차 run 은 `:109` 에서 해제했습니다. 이후 lock 파일의 상태는 로그로 확인되지 않습니다.
- `build_report_cache` 는 입력이 2배라는 WARN(`:113`)을 남기고도 SUCCESS(`:114`)로 끝났습니다.
- batchctl 이 API 호출 줄(`:98`)에 Authorization 헤더 값을 평문으로 남깁니다.

## 4. 원인

1. 1차 run(`…0200-edf5`)이 평시보다 느리게 진행했습니다(30~80 rows/s, 평시 201.5 rows/s). 느린 이유는 로그로 확인되지 않습니다(가설 D, 판단 불가).
2. 그래서 02:40 p95 초과 WARN(`:94`), 02:45 SLA `NOT_FINISHED`(`:95`)가 났습니다. SLA 알림에는 progress 가 없어 "멈춤"과 "느림"을 구분할 수 없었습니다.
3. 02:58:41 운영자가 "멈춘 것으로 보고" `force=true` 재실행을 요청했습니다(`:97`, `:98`). 실제로 1차 run 은 64% → 77% 로 진행 중이었습니다(가설 E 기각).
4. batch-runner 는 lock 보유자(pid=46735, `since=02:00:03`)를 확인하고도 `--force` 때문에 lock 을 무시했습니다(`:99`). 재실행 run(`…0258-8963`)은 `lock=bypassed` 로 시작했습니다(`:100`).
5. 두 run 이 같은 대상일을 같은 테이블(`dm.daily_submission_fact`)에 `mode=append` 로 적재했습니다(`:89`, `:101`). 두 run 이 각각 184,532 건으로 SUCCESS 를 내(`:108`, `:110`), 대상일 적재 건수가 369,064 건이 됐습니다(`datamart-counts.csv:24`).
6. 이어 `build_report_cache` 가 2배 입력(369,064)으로 리포트 캐시를 만들었습니다(`:113`, `:114`). 이상은 07:00 dq-check 에서야 알려졌습니다(`:115`).

## 5. 근거 로그 줄

> 모두 `sed -n '85,115p' batch-job.log` 와 `sed -n '75,82p' batch-job.log`, `cat -n datamart-counts.csv` 로 확인한 줄입니다.

| 파일:줄 | 발췌 |
|---|---|
| `batch-job.log:81` | `job=load_datamart date=2026-09-14 ... status=SUCCESS rows=186058 elapsed=923s` (평시 비교) |
| `batch-job.log:88` | `run_id=ldm-20260916-0200-edf5 status=START ... trigger=cron ... lock=acquired ... pid=46735` |
| `batch-job.log:89` | `run_id=ldm-20260916-0200-edf5 step=read source=extract_submissions/2026-09-15 mode=append target=dm.daily_submission_fact` |
| `batch-job.log:94` | `status=RUNNING note="elapsed 40m exceeds p95 17m" rows=94111` |
| `batch-job.log:95` | `[sla-monitor@10.20.x.x] ... status=NOT_FINISHED sla=02:45 last_status=RUNNING run_id=ldm-20260916-0200-edf5` |
| `batch-job.log:96` | `run_id=ldm-20260916-0200-edf5 status=RUNNING progress=64% rows=118100` |
| `batch-job.log:97` | `rerun requested job=load_datamart date=2026-09-15 operator=[EMAIL] reason="SLA 알림 — 멈춘 것으로 보고 재실행" force=true` |
| `batch-job.log:99` | `run_id=ldm-20260916-0258-8963 lock=held_by run_id=ldm-20260916-0200-edf5 pid=46735 since=02:00:03 action=ignored reason="--force"` |
| `batch-job.log:100` | `run_id=ldm-20260916-0258-8963 status=START rows=0 trigger=manual operator=[EMAIL] lock=bypassed` |
| `batch-job.log:101` | `run_id=ldm-20260916-0258-8963 step=read source=extract_submissions/2026-09-15 mode=append target=dm.daily_submission_fact` |
| `batch-job.log:103` | `run_id=ldm-20260916-0200-edf5 status=RUNNING progress=77% rows=142089` |
| `batch-job.log:108` | `run_id=ldm-20260916-0200-edf5 status=SUCCESS rows=184532 elapsed=4357s` |
| `batch-job.log:110` | `run_id=ldm-20260916-0258-8963 status=SUCCESS rows=184532 elapsed=1008s` |
| `batch-job.log:111` | `run_id=ldm-20260916-0258-8963 lock=release_skipped reason="not owner (bypassed)"` |
| `batch-job.log:113` | `build_report_cache ... input=dm.daily_submission_fact input_rows=369064 note="input_rows differs from extract_submissions rows=41573 x avg fan-out"` |
| `batch-job.log:115` | `[dq-check@10.20.x.x] table=dm.daily_submission_fact date=2026-09-15 rows=369064 avg_7d=184617 deviation=+99%` |
| `datamart-counts.csv:24` | `2026-09-15,load_datamart,369064,2026-09-16T03:15:31+09:00` |

## 6. 수정안 (지금 적용)

> 제안만 합니다. 코드 · 데이터는 바꾸지 않았습니다. 대상 소스(batch-runner · batchctl · sla-monitor)는 이 저장소에 없어 파일 경로는 컴포넌트 이름으로 적습니다.

| # | 대상 | 변경 | 확인 방법 |
|---|---|---|---|
| 1 | batch-runner `load_datamart` 적재 단계 | `mode=append` 를 **대상일 단위 멱등 적재**로 바꿉니다. 같은 트랜잭션에서 `date=<대상일>` 행을 삭제하고 다시 삽입하거나, 파티션을 교체합니다. 같은 대상일을 두 번 돌려도 건수가 1배여야 합니다 | 스테이징에서 같은 대상일을 연속 2회 실행한 뒤 `readonly` 계정으로 `SELECT COUNT(*) FROM dm.daily_submission_fact WHERE <날짜 컬럼>='2026-09-15'` 를 조회해, 1회 실행 값과 같은지 봅니다 |
| 2 | batch-runner lock 처리 | `--force` 는 lock 보유 pid 가 **살아 있지 않을 때만** stale lock 을 넘어서게 합니다. 보유자가 살아 있으면 `held_by` 로 거부(종료 코드 ≠ 0)합니다 | 1차 run 실행 중 `force=true` 재실행을 요청해 거부되는지, 보유 pid 를 kill 한 뒤에는 허용되는지 봅니다 |
| 3 | batchctl 재실행 요청 | 같은 job · 대상일에 RUNNING run 이 있으면 rerun 요청을 거부하고 해당 run_id · 최근 progress 를 응답합니다 | RUNNING 중 `POST /api/v1/jobs/load_datamart/runs` 가 `409` 와 run_id 를 돌려주는지 봅니다 |
| 4 | 데이터 정정 (별도 승인 필요) | 2026-09-15 분 `dm.daily_submission_fact` 중복 184,532 건을 정리하고 `build_report_cache` 2026-09-15 를 다시 만듭니다. 이번 범위에서는 하지 않습니다 | 정리 후 건수가 184,532 건이고, dq-check 편차가 ±30% 안인지 봅니다 |

## 7. 재발 방지 (구조적 장치)

| 장치 | 막는 것 |
|---|---|
| 적재 멱등화(수정안 1) — 대상일 단위 replace | 재실행 · 중복 트리거가 일어나도 건수가 2배가 되지 않습니다. 이번 사건의 직접 피해를 막습니다 |
| 실행 중 run 이 있을 때 force 재실행 거부(수정안 2 · 3) | 정상 진행 중인 run 위에 두 번째 run 이 겹쳐 도는 것을 막습니다 |
| SLA 알림에 progress · 최근 진행 시각 포함, "정지"(N분간 progress 변화 없음)와 "지연"을 구분 | 느리게 진행 중인 run 을 멈춘 것으로 오판해 재실행하는 것을 막습니다(가설 E) |
| 다운스트림 job(`build_report_cache`) 시작 전 입력 건수 게이트 — `input_rows` 가 평시 대비 ±30% 밖이면 FAIL | 잘못된 적재가 리포트 캐시로 퍼지는 것을 막습니다(`:113` 은 WARN 후 SUCCESS) |
| 재실행 절차에 "현재 run progress 확인 → 정지 확인 시에만 kill 후 재실행" 단계 명시 | 운영자 판단만으로 lock 을 우회하는 것을 막습니다 |

## 8. 모니터링 항목

> "이번에 울렸을 시각"은 이 로그에 같은 조건을 적용했을 때 처음 조건이 성립한 줄의 시각입니다. 실제 영향(2배 적재가 리포트 캐시에 반영)은 03:30:04(`:113`)부터이고, 실제 첫 통지는 07:00:04(`:115`)였습니다.

| 지표 | 임계값 | 이번에 울렸을 시각 | 실제 통지(07:00:04) 대비 / 리포트 캐시 입력(03:30:04) 대비 |
|---|---|---|---|
| 같은 job · 대상일에 RUNNING run 2개 이상 (또는 `lock=bypassed` 발생) | 1건 | 02:58:42 (`:99`, `:100`) | 4시간 1분 앞 / 31분 앞 |
| 같은 job · 대상일 SUCCESS 횟수 | 2회 이상 | 03:15:31 (`:110`) | 3시간 44분 앞 / 14분 앞 |
| 적재 직후 건수 편차 (load_datamart SUCCESS 시점에 직전 7일 평균과 비교) | ±30% 초과 | 03:15:31 (`datamart-counts.csv:24` `loaded_at`) | 3시간 44분 앞 / 14분 앞 |
| `build_report_cache` `input_rows` ÷ 당일 load_datamart 평시 rows | 1.3배 초과 | 03:30:04 (`:113`) | 3시간 30분 앞 / 동시 (캐시 SUCCESS `:114` 03:33:42 보다 3분 38초 앞) |
| load_datamart progress 속도 | 평시(201.5 rows/s)의 50% 미만 15분 지속 | 첫 progress 줄 02:09:39 (`:91`, 80.1 rows/s)에서 이미 50% 미만이었으므로, 지속 조건 충족은 다음 줄 02:21:21 (`:92`) | SLA 알림(02:45:00)보다 23분 앞 — 재실행 판단 전에 "느림"을 알렸을 신호 |

## 9. 확인하지 못한 것

| 항목 | 막힌 이유 | 다음을 위해 남겨야 할 로그 · 확인 |
|---|---|---|
| `dm.daily_submission_fact` 의 2026-09-15 실제 중복 행 수 · 중복 키 | DB 를 띄워 조회하지 않았습니다(이번 범위 밖) | `readonly` 계정으로 대상일 `COUNT(*)` 와 자연키 기준 `GROUP BY ... HAVING COUNT(*)>1` 결과 |
| 1차 run 이 느렸던 이유(가설 D) | 배치 서버 · DB 의 CPU · I/O · lock wait 지표가 로그에 없습니다. replica lag 는 extract 단계 WARN 하나뿐입니다 | 02:00 ~ 03:15 적재 대상 DB 의 쓰기 지연 · 잠금 대기, 원천 DB 복제 지연 지표, batch-runner 의 step 별 소요 시간 |
| batch-runner `--force` · `mode=append` 의 실제 구현 | 소스 · 설정이 저장소에 없습니다 | batch-runner 저장소의 lock 처리 · 적재 SQL, 운영 설정값 |
| `/var/lock/batch/load_datamart.lock` 현재 상태 | `:111` `release_skipped` 이후 기록이 없습니다 | 배치 서버의 lock 파일 존재 · 내용, 다음 실행일(대상일 09-16)의 lock 획득 기록 |
| 리포트 캐시 집계값 오염 여부 | `build_report_cache` 출력 건수(1271)는 평시 범위이고, 값 자체는 로그에 없습니다 | 2026-09-15 리포트 캐시 값과 1배 입력으로 재계산한 값의 비교 |
| 07:00 이후 조치 · 복귀 | 로그가 07:00:04(`:115`)에서 끝납니다 | dq-check 통지 이후 on-call 조치 기록, 정정 작업 로그 |
| 재실행 요청의 Authorization 값 평문 기록(`:98`) | 사건 원인과는 별개입니다 | 해당 토큰 폐기 · 교체 여부, batchctl 로그 마스킹 설정 |

---

마스킹 적용: IP 5건 · 이메일 6건 · 학생 식별자 3건 · 토큰 1건 · 비밀번호/접속 문자열 0건
