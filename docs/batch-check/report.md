# 배치 로그 · 적재 건수 점검 리포트

- 대상: `pipeline-samples/batch-logs/2026-09-08.log` ~ `2026-09-17.log` (10일), CSV `pipeline-samples/datamart-counts.csv` (데이터 49행)
- 대상 job (5개): `build_item_stats` · `export_grades` · `extract_submissions` · `load_datamart` · `build_report_cache` (매일 `day_open ... planned=` 줄에 다섯 job 이 모두 계획됨)
- 점검 기준
  1. 실행 여부: 다른 날 매일 돈 job 이 없거나, START 만 있고 SUCCESS · FAIL 이 없으면 이상
  2. 중복 실행: 같은 날 같은 job 의 SUCCESS 가 2번 이상이면 이상 (force · lock 줄을 근거로 함께 표기)
  3. 적재 건수: 직전 7일 평균 대비 ±30% 초과, 0건, CSV 행 누락이면 이상
  4. 지연: START → SUCCESS 소요 시간이 job 전체 기간 중앙값의 2배 초과면 이상

## 이상 목록

| 날짜 | job | 점검 항목 | 근거 |
|---|---|---|---|
| 2026-09-09 | build_item_stats | ① 실행 여부 (START 후 종료 기록 없음) | `2026-09-09.log:2` START(cron, lock=acquired) → `:4` `progress=25% rows=3109` 이후 이 run_id 의 줄 없음. SUCCESS · FAIL · `lock=released` 모두 없음. 다른 9일은 모두 SUCCESS. **확인 필요**: 배치 서버 10.20.7.31 의 해당 pid(40655) 종료 여부 · OS/프로세스 로그, `dm.item_stats_daily` 의 2026-09-09 적재 여부 |
| 2026-09-09 | build_item_stats | ③ 적재 건수 (CSV 행 누락) | `datamart-counts.csv:7`~`:10` 에 2026-09-09 행은 4개뿐이고 `build_item_stats` 행이 없음(앞뒤 날짜는 `:2`, `:11` 에 있음). **확인 필요**: `dm.item_stats_daily` 2026-09-09 행 수 |
| 2026-09-12 | export_grades | ④ 지연 | `2026-09-12.log:10` START 01:00:01 → `:16` SUCCESS `elapsed=963s`, 중앙값 319.5s 의 3.01배. progress 간격이 약 4분(`:12` 01:04:01 → `:13` 01:08:02 → `:14` 01:12:03)으로 다른 날보다 고르게 느림. WARN · ERROR 없음. 건수는 정상(8257, 직전 평균 대비 +1.17%). **확인 필요**: grade-db `grades.dbo.grade_summary` 조회 시간, S3 `itembank-dev-export` 업로드 지표(01:00~01:16) |
| 2026-09-15 | extract_submissions | ④ 지연 | `2026-09-15.log:18` START 01:30:02 → `:20` SUCCESS `elapsed=1668s`, 중앙값 681.2s 의 2.45배. `:19` WARN `note="source read slow: grade-db 10.20.2.41 replica lag 412s"`. 건수는 정상(41573, -1.75%). **확인 필요**: grade-db 10.20.2.41 복제 지연 지표(01:30~01:58) |
| 2026-09-15 | load_datamart | ④ 지연 | 1차 run `2026-09-15.log:21` START 02:00:03 → `:40` SUCCESS `elapsed=4357s`, 중앙값 923.0s 의 4.72배. `:27` WARN `"elapsed 40m exceeds p95 17m"`, `:28` sla-monitor `NOT_FINISHED sla=02:45`. progress 는 `:24` 25% → `:25` 38% → `:26` 51% → `:29` 64% → `:35` 77% → `:37` 90% 로 계속 진행 중이었음(멈춤 아님). 앞 단계 extract_submissions 지연과의 인과는 로그만으로 판단할 수 없다. **확인 필요**: `dm.daily_submission_fact` 쓰기 지표, 02:00~03:12 DB 부하 |
| 2026-09-15 | load_datamart | ② 중복 실행 | SUCCESS 2회: `2026-09-15.log:40`(run `…0200-edf5`, rows=184532) · `:42`(run `…0258-8963`, rows=184532). `:30` batchctl `rerun requested ... reason="SLA 알림 — 멈춘 것으로 보고 재실행" force=true`, `:31` `lock=held_by run_id=ldm-20260916-0200-edf5 ... action=ignored reason="--force"`, `:32` 수동 START `lock=bypassed`, `:43` `lock=release_skipped reason="not owner (bypassed)"`. 두 run 모두 `:22` · `:33` 에서 `mode=append target=dm.daily_submission_fact`. **확인 필요**: `dm.daily_submission_fact` 2026-09-15 중복 행 여부, `/var/lock/batch/load_datamart.lock` 현재 상태 |
| 2026-09-15 | load_datamart | ③ 적재 건수 | `datamart-counts.csv:39` `369064`, 직전 7일 평균 184617.4 대비 +99.91%. 값은 로그 SUCCESS rows 184532 의 정확히 2.0배(184532×2=369064). `2026-09-15.log:47` dq-check WARN `rows=369064 avg_7d=184617 deviation=+99%`. **확인 필요**: `dm.daily_submission_fact` 2026-09-15 행 수와 중복 제거 필요 여부 |
| 2026-09-15 | build_report_cache | ③ 적재 건수 (입력 이상) | 출력 건수는 정상(`datamart-counts.csv:40` 1271, -0.20%)이나 입력이 2배: `2026-09-15.log:46` `input_rows=369064`(다른 날은 해당일 load_datamart rows 와 같음, 예: `2026-09-14.log:29` `input_rows=186058`). `2026-09-15.log:45` WARN `"input_rows differs from extract_submissions rows=41573 x avg fan-out"`. **확인 필요**: 2026-09-15 리포트 캐시 집계값이 중복 입력의 영향을 받았는지 |
| 2026-09-17 | build_item_stats | ③ 적재 건수 | `datamart-counts.csv:46` `7284`, 직전 7일 평균 12453.1 대비 -41.51%. 로그도 같은 값: `2026-09-17.log:7` `step=write rows=7284`, `:8` SUCCESS `rows=7284 elapsed=202s`(중앙값 213.9s 의 0.95배, 지연 없음). progress 25% 시점 rows=1821(`:4`)로 처음부터 적음. WARN · ERROR 없음. **확인 필요**: 원본 `itembank.item_response` 의 2026-09-17 행 수 |

## 참고 (이상으로 판정하지 않은 것)

- `load_datamart` 의 `step=validate skipped=1 student_id=STU-1013 reason="class_id is NULL"` WARN 이 10일 모두 반복된다(예: `2026-09-08.log:22`, `2026-09-15.log:23` · `:34`, `2026-09-17.log:22`). 매일 1건이라 건수 기준에는 걸리지 않지만 같은 학생이 계속 빠지고 있다. **확인 필요**: STU-1013 의 학급 배정 데이터.
- `load_datamart` 2026-09-16 · 09-17 의 직전 7일 평균(211253.0 · 211493.4)에는 09-15 의 2배 값 369064 가 포함돼 있다. 편차는 -12.48% · -12.74% 로 기준 안이지만, 09-15 값을 빼면 평균이 정상 수준으로 돌아간다.
- `load_datamart` 지연 중앙값(923.0s)은 09-15 두 run 을 모두 포함한 11개 값으로 냈다. 수동 재실행 run 은 1008s(중앙값의 1.09배)로 기준 안이다.
- 2026-09-15 수동 재실행 기록에 운영자 계정이 남아 있다(`2026-09-15.log:30` `operator=`). 리포트에는 옮기지 않았다.
- `2026-09-09.log` 는 24줄로 다른 날(29줄)보다 짧다. build_item_stats 블록이 3줄로 끝난 것 외에 다른 job 의 START · SUCCESS 는 모두 있다.

## 계산 방법

- 뽑은 줄: `grep -n 'status=START\|status=SUCCESS\|status=FAIL\|day_open'`, 그리고 `grep -n 'WARN\|ERROR\|force\|retry'`(lock acquired/released 제외). 이상 후보만 해당 줄 범위를 `sed -n` 으로 확인했다. 로그 전체는 읽지 않았다.
- 적재 건수 기준값: CSV `row_count` 를 job 별로, 해당 날짜 직전 최대 7개 로그 날짜(파일 기준) 중 CSV 행이 있는 날의 평균. 편차 = (실제 − 평균) / 평균 × 100. 2026-09-08 은 직전 데이터가 없어 다섯 job 모두 **비교 불가**. 직전 데이터가 7일 미만인 09-09 ~ 09-14 는 있는 날만으로 평균을 냈다(build_item_stats 는 09-09 누락으로 09-16 도 6일 평균).
- 지연 기준값: START 줄과 같은 `run_id` 의 SUCCESS 줄 타임스탬프 차이(초)를 job 별 전체 기간에 모아 중앙값을 냈다. `elapsed=` 값과는 소수점 절사 차이만 있다. `build_report_cache` 는 SUCCESS 줄에 `elapsed` 가 없어 타임스탬프 차이로만 계산했다. 배수 = 소요 시간 / 중앙값.
- 계산은 `python3` 스크립트(CSV 파싱 · 평균 · 중앙값 · 편차 · 배수)로 했고, 리포트의 숫자는 그 출력값이다.
- 비교하지 못한 것: 2026-09-08 적재 건수(첫날), 2026-09-09 build_item_stats 의 소요 시간 · 건수(SUCCESS 없음 · CSV 행 없음).
