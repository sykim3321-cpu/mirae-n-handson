# 배치 로그 · 적재 건수 점검 리포트

- 대상: 판정 대상 일자 **2026-09-15 하루** (`pipeline-samples/batch-logs/2026-09-15.log`, 47줄). 기준값 계산에는 `2026-09-08.log` ~ `2026-09-17.log` (10일)와 CSV `pipeline-samples/datamart-counts.csv` (데이터 49행)를 썼다. 이상 목록에는 2026-09-15 행만 넣었다.
- 대상 job (5개): `build_item_stats` · `export_grades` · `extract_submissions` · `load_datamart` · `build_report_cache` (`2026-09-15.log:1` `day_open ... planned=` 에 다섯 job 이 모두 계획됨)
- 점검 기준
  1. 실행 여부: 다른 날 매일 돈 job 이 없거나, START 만 있고 SUCCESS · FAIL 이 없으면 이상
  2. 중복 실행: 같은 날 같은 job 의 SUCCESS 가 2번 이상이면 이상 (force · lock 줄을 근거로 함께 표기)
  3. 적재 건수: 직전 7일 평균 대비 ±30% 초과, 0건, CSV 행 누락이면 이상
  4. 지연: START → SUCCESS 소요 시간이 job 전체 기간 중앙값의 2배 초과면 이상

## 이상 목록

| 날짜 | job | 점검 항목 | 근거 |
|---|---|---|---|
| 2026-09-15 | load_datamart | ② 중복 실행 | SUCCESS 2회: `2026-09-15.log:40`(run `…0200-edf5`, rows=184532) · `:42`(run `…0258-8963`, rows=184532). `:30` batchctl `rerun requested ... reason="SLA 알림 — 멈춘 것으로 보고 재실행" force=true`, `:31` `lock=held_by run_id=ldm-20260916-0200-edf5 ... action=ignored reason="--force"`, `:32` 수동 START `trigger=manual lock=bypassed`, `:41` 1차 run `lock=released`, `:43` 재실행 run `lock=release_skipped reason="not owner (bypassed)"`. 두 run 모두 `:22` · `:33` 에서 `mode=append target=dm.daily_submission_fact`. **확인 필요**: `dm.daily_submission_fact` 2026-09-15 중복 행 여부, `/var/lock/batch/load_datamart.lock` 현재 상태 |
| 2026-09-15 | load_datamart | ③ 적재 건수 | `datamart-counts.csv:39` `369064`, 직전 7일 평균 184617.43 대비 +99.91%. 로그의 두 SUCCESS rows 가 각각 184532(`2026-09-15.log:40` · `:42`)이고 CSV 값은 그 합(184532×2=369064)과 같다. `2026-09-15.log:47` dq-check WARN `rows=369064 avg_7d=184617 deviation=+99%`. **확인 필요**: `dm.daily_submission_fact` 2026-09-15 행 수와 중복 제거 필요 여부 |
| 2026-09-15 | load_datamart | ④ 지연 | 1차 run `2026-09-15.log:21` START 02:00:03 → `:40` SUCCESS `elapsed=4357s`, 중앙값 923.0s 의 4.72배. `:27` WARN `note="elapsed 40m exceeds p95 17m"`, `:28` sla-monitor `status=NOT_FINISHED sla=02:45`. progress 는 `:24` 25% → `:25` 38% → `:26` 51% → `:29` 64% → `:35` 77% → `:37` 90% 로 계속 늘고 있었다(로그상 멈춤 기록 없음). 앞 단계 extract_submissions 지연과의 인과는 로그만으로 판단할 수 없다. **확인 필요**: `dm.daily_submission_fact` 쓰기 지표, 02:00~03:12 DB 부하 |
| 2026-09-15 | extract_submissions | ④ 지연 | `2026-09-15.log:18` START 01:30:02 → `:20` SUCCESS `elapsed=1668s`, 중앙값 681.2s 의 2.45배. `:19` WARN `note="source read slow: grade-db 10.20.2.41 replica lag 412s"`. 건수는 정상(`datamart-counts.csv:38` 41573, 직전 7일 평균 42315.0 대비 -1.75%). **확인 필요**: grade-db 10.20.2.41 복제 지연 지표(01:30~01:58) |
| 2026-09-15 | build_report_cache | ③ 적재 건수 (입력 이상) | 출력 건수는 정상(`datamart-counts.csv:40` 1271, 직전 7일 평균 1273.57 대비 -0.20%)이나 입력이 2배: `2026-09-15.log:46` `input_rows=369064`. 다른 날은 해당일 load_datamart rows 와 같다(예: `2026-09-14.log:26` load_datamart `rows=186058` = `:29` `input_rows=186058`). `2026-09-15.log:45` WARN `note="input_rows differs from extract_submissions rows=41573 x avg fan-out"`. **확인 필요**: 2026-09-15 리포트 캐시 집계값이 중복 입력의 영향을 받았는지 |

## 참고 (이상으로 판정하지 않은 것)

- `build_item_stats`(`2026-09-15.log:2` → `:8`, 220s, 중앙값 213.9s 의 1.03배, `datamart-counts.csv:36` 12490, +0.40%) · `export_grades`(`:10` → `:16`, 318s, 중앙값 319.5s 의 1.00배, `datamart-counts.csv:37` 8221, +0.42%)는 네 기준 모두 안이다.
- `load_datamart` 수동 재실행 run 은 `:32` → `:42` 1008s 로 중앙값의 1.09배라 지연 기준 안이다. 지연 중앙값 923.0s 는 09-15 두 run 을 모두 포함한 11개 값으로 냈다.
- `load_datamart` 의 `step=validate skipped=1 ... reason="class_id is NULL"` WARN 이 두 run 모두에 있다(`2026-09-15.log:23` · `:34`). 같은 WARN 이 다른 날 로그에도 매일 1건씩 반복된다. 건수 기준에는 걸리지 않는다. **확인 필요**: 해당 학생의 학급 배정 데이터.
- 수동 재실행 기록(`2026-09-15.log:30` · `:32`)에 운영자 계정이 남아 있다. 리포트에는 옮기지 않았다.

## 계산 방법

- 뽑은 줄: 10일 로그 전체에서 `status=START|SUCCESS|FAIL` 줄만 정규식으로 추출했다. 2026-09-15 는 추가로 `grep -n 'WARN\|ERROR\|lock\|force'`, `grep -n 'progress\|mode=\|day_open'` 로 필요한 줄만 뽑았다. 로그 전체는 읽지 않았다.
- 실행 여부 · 중복 실행: 날짜 × job 별 SUCCESS 개수를 셌다. 2026-09-15 는 load_datamart 만 2회, 나머지 네 job 은 1회다.
- 적재 건수 기준값: CSV `row_count` 를 job 별로 2026-09-15 직전 최대 7개 날짜(09-08 ~ 09-14) 중 CSV 행이 있는 날의 평균. `build_item_stats` 는 09-09 CSV 행이 없어 6일 평균(12440.67)이다. 편차 = (실제 − 평균) / 평균 × 100.
- 지연 기준값: START 줄과 같은 `run_id` 의 SUCCESS 줄 타임스탬프 차이(초)를 job 별 전체 기간(10일)에 모아 중앙값을 냈다. `elapsed=` 값과는 소수점 절사 차이만 있다. `build_report_cache` 는 SUCCESS 줄에 `elapsed` 가 없어 타임스탬프 차이(09-15 221.2s, 중앙값 195.5s 의 1.13배)로 계산했다. 배수 = 소요 시간 / 중앙값.
- 계산은 `python3` 스크립트(CSV 파싱 · 평균 · 중앙값 · 편차 · 배수)로 했고, 리포트의 숫자는 그 출력값이다.
- 판정 범위: 2026-09-15 외 날짜는 기준값 계산에만 썼고 판정하지 않았다.
