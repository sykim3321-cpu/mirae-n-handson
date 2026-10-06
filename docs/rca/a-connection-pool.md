# RCA — a-connection-pool (2026-09-16)

## 요약

- **언제**: 2026-09-16 14:37:52 KST 에 내부 첫 신호(커넥션 leak WARN)가 찍혔습니다. 사용자 영향은 **14:40:52 ~ 14:51:47(약 11분)** 이었고, 5xx 는 186건(500 49 / 502 82 / 504 55)입니다. 14:52 부터 평시로 돌아왔고, 재시작 기록은 없습니다.
- **무엇이**: API 커넥션 풀(`itembank-pool`, 5개)이 비었습니다. 그래서 문항 · 단원 · 배포 조회까지 3초 대기 후 500 이 났고, nginx 가 upstream 을 제외하면서 502 가 이어졌습니다.
- **직접 원인**: `ReportService.buildClassReport` 가 조회를 끝낸 뒤에도 트랜잭션(커넥션)을 쥔 채 `sign()`(SHA-256 20만 회)을 돌렸습니다. 그 결과 report 1건이 커넥션 1개를 15.5~21.5초 점유했습니다.
- **촉발 조건**: 14:38 부터 교사용 리포트 페이지에서 report 요청이 분당 0.16건에서 12.2건으로 늘었습니다. 동시 점유가 풀 크기 5에 닿았습니다(14:40:25).
- **확신 수준: 중간**. 로그와 코드는 같은 순서를 가리킵니다. 하지만 `sign()` 소요 시간, 운영 leak threshold, 운영 코드 버전은 확인하지 못했습니다(9절).

## 1. 수집 범위

> 대상: `incident-logs/a-connection-pool/` 원본 4개 파일 (수정하지 않음)
> 방법: `wc`, `head`, `tail`, `grep -c`, `grep -n`, `sed -n`, `awk` 집계만 사용했습니다. 파일을 통째로 읽지 않았습니다.
> 이 절은 범위만 정합니다. 원인은 다루지 않습니다.

### 1.1 파일별 기록 기간 · 줄 수 · 시각 형식 · 타임존

| 파일 | 줄 수 | 기록 기간 (KST) | 시각 형식 (예) | 타임존 | KST 변환 |
|---|---:|---|---|---|---|
| `app.log` (Spring Boot) | 5,371 (타임스탬프 엔트리 1,927 + 스택트레이스 연속행 3,444) | 2026-09-16 06:43:56 ~ 15:29:51 | `2026-09-16T14:40:52.319+09:00` (ISO-8601, ms, 오프셋 포함) | +09:00 (전 엔트리 동일) | 그대로 사용 |
| `nginx-access.log` (combined) | 21,086 | 2026-09-16 06:43:56 ~ 15:29:59 | `[16/Sep/2026:14:40:52 +0900]` | +0900 (전 줄 동일) | 그대로 사용 |
| `nginx-error.log` | 168 | 2026-09-16 09:12:03 ~ 14:51:47 (이벤트가 있을 때만 기록) | `2026/09/16 14:41:06` (오프셋 없음) | **KST** (아래 근거) | 그대로 사용 |
| `mariadb-slow.log` | 21 (쿼리 3건) | 2026-09-16 09:14:07 ~ 14:37:52 KST (이벤트가 있을 때만 기록) | `# Time: 260916  5:37:52` (YYMMDD, 시는 공백으로 채움) 와 `SET timestamp=1789537062` (epoch) | **UTC** | `# Time` 에 **+9시간**. epoch은 `TZ=Asia/Seoul date -d @<epoch>` |

**타임존 판정 근거**

- `nginx-error.log` 에는 오프셋이 없습니다. 14:51:03 `upstream timed out` (client 교사단말A, `GET /api/classes/3/report`) 줄이 `nginx-access.log` 19351행의 `[16/Sep/2026:14:51:03 +0900] ... 504` 와 초 단위까지 일치합니다. 그래서 KST로 판정했습니다.
- `mariadb-slow.log` 의 `SET timestamp=1789537062` 는 2026-09-16 05:37:42 UTC, 즉 14:37:42 KST입니다. `# Time: 5:37:52` 도 같은 기준(UTC)입니다. 나머지 2건도 같은 방식으로 맞습니다.
- slow log 한 건 안에서 `# Time` 과 `SET timestamp` 는 약 10초 차이가 나고, 이는 `Query_time 9.81` 과 거의 같습니다. 따라서 **`SET timestamp` = 쿼리 시작, `# Time` = 쿼리 종료**로 보고 타임라인에 배치합니다.

**KST 변환 규칙 (요약)**

| 원본 | 규칙 |
|---|---|
| `app.log`, `nginx-access.log` | 오프셋이 +09:00 / +0900이므로 변환 없음 |
| `nginx-error.log` | 오프셋 없음. 서버 로컬 = KST로 확인했으므로 변환 없음 |
| `mariadb-slow.log` `# Time: YYMMDD H:MM:SS` | UTC → **+9h** (예: `260916  5:37:52` → 2026-09-16 14:37:52 KST). 자정을 넘기면 날짜도 +1 |
| `mariadb-slow.log` `SET timestamp=<epoch>` | epoch(UTC) → KST. 쿼리 **시작** 시각 |

**집계 시 주의**

- `app.log` 의 비타임스탬프 3,444행(`java.lang.Exception: ...`, `at ...`, `Caused by: ...`)은 **모두 1244행 이후**, 즉 이상 구간 안에 있습니다. 줄 단위로 `grep -c` 하면 건수가 부풀려지므로 엔트리(타임스탬프 행) 기준으로 셉니다. 예: `grep -c 'timed out'` = 196(줄), 엔트리 기준 = 49.
- `nginx-access.log` 의 `$time_local` 은 요청이 **끝난** 시각입니다. 504는 로그 시각보다 최대 수십 초 먼저 시작된 요청입니다.
- `nginx-error.log`, `mariadb-slow.log` 의 "기간"은 수집 범위(coverage)가 아니라 첫 이벤트와 마지막 이벤트의 시각입니다. `nginx-error.log` 가 14:51:47에서 끝나는 것만 보고는 오류가 멈췄는지 기록이 멈췄는지 알 수 없습니다. `nginx-access.log` 에서 14:51:47 이후 5xx가 0건이므로 "멈춤"으로 봅니다.

### 1.2 분 단위 이상 건수와 평소 수준

**범주 정의**

| 파일 | 오류 | 경고 | 5xx | 타임아웃 |
|---|---|---|---|---|
| `app.log` | 레벨 `ERROR` | 레벨 `WARN` | 해당 없음 | `Connection is not available, request timed out` 엔트리 (49건, ERROR의 `SqlExceptionHelper` 엔트리와 같은 집합) |
| `nginx-access.log` | 해당 없음 | 해당 없음 | status `5xx` (500 / 502 / 504로 나눔) | 504 |
| `nginx-error.log` | `[error]` | `[warn]` | 해당 없음 | `upstream timed out` |
| `mariadb-slow.log` | 범주 해당 없음. 3건의 시각만 기록 | | | |

**파일별 전체 분포와 평소 수준**

| 파일 | 전체 건수 | 평소 수준 (06:43 ~ 14:36) | 이상 구간 |
|---|---|---|---|
| `app.log` ERROR | 98 | **0건** | 14:40:52 ~ 14:49:51 |
| `app.log` WARN | 203 = `ProxyLeakTask` 134 + `SqlExceptionHelper` 49 (ERROR와 짝) + `state conflict` 20 | `state conflict` 하루 내내 시간당 1~4건 (분당 0~1건) | `ProxyLeakTask` 14:37:52 ~ 14:51:37 |
| `app.log` 타임아웃 | 49 | **0건** | 14:40:52 ~ 14:49:51 |
| `nginx-access.log` 5xx | 186 = 500: 49 / 502: 82 / 504: 55 | **0건** (요청량은 분당 약 32~56건) | 14:40:52 ~ 14:51:47 |
| `nginx-error.log` [error] | 137 = `no live upstreams` 68 + `upstream timed out` 55 + `connect() failed (Connection refused)` 14 | **0건** | 14:41:06 ~ 14:51:47 |
| `nginx-error.log` [warn] | 28 = `upstream server temporarily disabled` 26 + `buffered to a temporary file` 2 | `buffered ...` 2건 (09:12:03, 13:05:51). [info] 3건 | 14시대 26건 |
| `mariadb-slow.log` | 3 | 09:14:07~09 KST (2.3s), 11:02:40~42 KST (2.0s) | 14:37:42~52 KST (9.8s) |

**파일 간 정합성 확인** (시각 정렬과 건수 대조용. 원인 해석은 하지 않음)

- access 500 49건 = app 풀 타임아웃 ERROR 49건
- access 502 82건 = nginx-error `no live upstreams` 68 + `connect() failed` 14
- access 504 55건 = nginx-error `upstream timed out` 55
- 첫 500(access 18918행)과 첫 app ERROR(app.log 1522행)가 모두 14:40:52.

**분 단위 집계 (14:30 ~ 14:55 KST)**

이 범위 밖의 모든 분에서 app ERROR · 타임아웃, access 5xx, nginx-error [error]는 0건입니다. app WARN은 `state conflict` 0~1건뿐입니다.

| 분 (KST) | app 전체 | app ERROR | app WARN | app 타임아웃 | app leak 관련\* | access 전체 | access 5xx | 500 | 502 | 504 | ngx [error] | ngx [warn] | ngx 타임아웃 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 14:30 | 2 | 0 | 0 | 0 | 0 | 47 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 14:31 | 6 | 0 | 0 | 0 | 0 | 48 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 14:32 | 4 | 0 | 0 | 0 | 0 | 44 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 14:33 | 6 | 0 | 1 | 0 | 0 | 52 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 14:34 | 1 | 0 | 0 | 0 | 0 | 34 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 14:35 | 2 | 0 | 0 | 0 | 0 | 48 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 14:36 | 5 | 0 | 0 | 0 | 0 | 41 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| **14:37** | 5 | 0 | 1 | 0 | 1 | 50 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 14:38 | 11 | 0 | 2 | 0 | 5 | 41 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 14:39 | 12 | 0 | 3 | 0 | 6 | 42 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| **14:40** | 39 | 2 | 15 | 1 | 24 | 42 | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| 14:41 | 61 | 12 | 19 | 6 | 28 | 42 | 9 | 6 | 0 | 3 | 3 | 0 | 3 |
| 14:42 | 39 | 4 | 13 | 2 | 20 | 41 | 20 | 2 | 13 | 5 | 18 | 2 | 5 |
| 14:43 | 80 | 20 | 24 | 10 | 29 | 58 | 17 | 10 | 2 | 5 | 7 | 1 | 5 |
| 14:44 | 43 | 4 | 13 | 2 | 22 | 34 | 13 | 2 | 5 | 6 | 11 | 4 | 6 |
| 14:45 | 49 | 16 | 17 | 8 | 17 | 39 | 27 | 8 | 14 | 5 | 19 | 3 | 5 |
| 14:46 | 51 | 8 | 16 | 4 | 25 | 51 | 26 | 4 | 16 | 6 | 22 | 3 | 6 |
| 14:47 | 45 | 10 | 14 | 5 | 18 | 38 | 14 | 5 | 6 | 3 | 9 | 1 | 3 |
| 14:48 | 43 | 8 | 14 | 4 | 20 | 42 | 15 | 4 | 6 | 5 | 11 | 4 | 5 |
| 14:49 | 59 | 14 | 19 | 7 | 25 | 42 | 23 | 7 | 8 | 8 | 16 | 4 | 8 |
| 14:50 | 33 | 0 | 12 | 0 | 20 | 34 | 18 | 0 | 11 | 7 | 18 | 4 | 7 |
| **14:51** | 16 | 0 | 4 | 0 | 8 | 46 | 3 | 0 | 1 | 2 | 3 | 0 | 2 |
| 14:52 | 3 | 0 | 1 | 0 | 0 | 47 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 14:53 | 3 | 0 | 0 | 0 | 0 | 37 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 14:54 | 3 | 0 | 0 | 0 | 0 | 44 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 14:55 | 1 | 0 | 0 | 0 | 0 | 42 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

\* `ProxyLeakTask` 가 남긴 엔트리입니다. WARN `Connection leak detection triggered` 134건과 INFO `Previously reported leaked connection ...` 134건을 합한 수입니다.
14:33 · 14:37 이전 · 14:52의 WARN 1건은 평소에도 나오는 `state conflict` 입니다 (14:52:09, app.log 5269행 확인).

**이상 구간 제안**

| 구분 | 시각 (KST) | 근거 |
|---|---|---|
| 내부 신호 시작 | **14:37:52** (분 단위 14:37) | 첫 `ProxyLeakTask` WARN (app.log 1243행). 같은 분에 slow query 1건(14:37:42 시작, 14:37:52 종료)이 있음. 타임라인 표시용으로만 적음 |
| 사용자 영향 시작 | **14:40:52** | 첫 app ERROR(풀 타임아웃) = 첫 access 500. nginx-error 첫 [error]는 14:41:06 |
| 끝 | **14:51:47** | 마지막 access 5xx(504)이자 nginx-error 마지막 줄. app 마지막 leak WARN은 14:51:37, 마지막 ERROR는 14:49:51 |
| 평시 복귀 | 14:52 ~ | 모든 파일의 이상 범주가 평시 수준(0, 또는 `state conflict` 0~1)으로 돌아감 |

→ **핵심 이상 구간: 2026-09-16 14:37 ~ 14:52 KST** (사용자 영향 구간은 14:40:52 ~ 14:51:47, 약 11분)

### 1.3 앞뒤 여유 제안

| 구간 | 범위 (KST) | 여유 | 근거 |
|---|---|---|---|
| **분석 창 (기본)** | **14:07 ~ 15:02** | 앞 **30분** / 뒤 **10분** | 아래 설명 참고 |
| 평시 비교 구간 | 13:00 ~ 14:00 | 별도 | 요청량(분당 약 35~55건)과 WARN 수준을 이상 구간과 비교하기 위한 기준 |
| 참고 보존 | 09:14, 11:02 slow query | 분석 창 밖 | slow log는 3건뿐이라 전부 보존. 분석 창 밖이라는 점만 표시 |

**앞쪽을 넉넉히 두는 이유**

- leak 감지 WARN은 커넥션을 빌린 뒤 threshold만큼 시간이 지나야 찍힙니다. 그런데 `app.log` 에는 threshold 설정값이 남아 있지 않습니다(`leakDetectionThreshold` 0건). 그래서 실제로 커넥션을 빌린 시각은 14:37:52보다 이르지만 얼마나 이른지 모릅니다.
- `nginx-access.log` 의 시각은 요청이 끝난 시각입니다. 504 요청은 그보다 수십 초 먼저 시작했습니다.
- slow log는 쿼리가 끝난 뒤에야 기록되고, threshold를 넘은 쿼리만 남습니다. 그 전 단계 활동은 로그에 남지 않았을 수 있습니다.
- 그래서 앞쪽 30분(14:07~)을 기본으로 둡니다. 14:07~14:37 사이에서 앞선 신호가 보이면 앞쪽을 더 넓힙니다.

**뒤쪽을 10분만 두는 이유**

- 14:52부터 모든 파일이 평시 수준으로 바로 돌아왔습니다. 10분이면 재발 여부와 복구 직후 상태를 확인하기에 충분합니다.
- 다만 `nginx-error.log` 는 14:51:47 이후 기록이 없습니다. 이 파일만으로는 복구를 판단하지 않고, `nginx-access.log` 와 `app.log` 로 복구를 확인합니다.

## 2. 타임라인

> 대상 구간: **2026-09-16 14:07 ~ 15:02 KST** (1.3의 분석 창). 모든 시각은 KST입니다.
> 출처 표기는 `파일:줄번호` 입니다. `mariadb-slow.log` 의 시작 시각은 `SET timestamp`(epoch) 줄에서, 종료 시각은 `# Time`(UTC+9h) 줄에서 가져왔습니다.
> 같은 메시지가 반복되면 첫 발생 줄 하나만 쓰고, 이후 건수를 분 단위로 묶었습니다.
> **(선후 불확실)**: 파일 사이 시각 차이가 1초 이내라서 순서를 단정할 수 없는 곳입니다. `nginx-access.log`, `nginx-error.log`, `mariadb-slow.log` 의 `# Time` 은 초 단위까지만 남고, `app.log` 만 ms 단위입니다. 또 access 로그 시각은 요청이 끝난 시각입니다.
> 원인 해석은 쓰지 않았습니다. 로그에 있는 사실만 적었습니다.

### 2.1 앞선 구간 (14:07 ~ 14:37, 첫 오류 전)

| 시각 | 출처 | 사건 요약 | 원문 발췌 |
|---|---|---|---|
| 14:07 ~ 14:37 | nginx-access.log:17405~18800 (전체 집계) | 평소 트래픽입니다. 분당 32~56건(평균 45.0건)이고 5xx는 0건입니다. `/api/classes/N/report` 는 5건뿐이며(14:10, 14:13, 14:15, 14:17, 14:18) 모두 200입니다 | (집계 결과) |
| 14:15:16 | nginx-access.log:17783 | 위 report 5건 중 하나의 예시입니다 (17539 ~ 17914행) | `[16/Sep/2026:14:15:16 +0900] "GET /api/classes/1/report HTTP/1.1" 200 267` |
| 14:11:38 | app.log:1154 | 평소에도 나오는 업무성 WARN입니다. 같은 메시지가 14:14:03(1165), 14:33:45(1229)에도 있습니다. 이 구간에 ERROR는 0건입니다 | `WARN ... state conflict: 마감된 과제는 재배포할 수 없습니다` |
| 14:07 ~ 14:40 | nginx-error.log:5~6 (구간 앞뒤 줄) | 기록이 없습니다. 바로 앞 줄(5행)은 13:18:05 [info], 다음 줄(6행)은 14:41:06입니다 | — |
| 14:07 ~ 15:02 | app.log:1~5371, nginx-error.log:1~168, mariadb-slow.log:1~21 (파일 전체를 grep -ci로 검색) | 설정 재적재, 재시작, 시그널 기록이 없습니다 (`reload`, `refresh`, `restart`, `Started`, `config`, `HikariPool-` 등으로 grep한 결과 0건) | — |
| **14:37:42** | mariadb-slow.log:20 (`SET timestamp=1789537062`) | **느린 쿼리가 시작됐습니다.** Query_time 9.81s, Rows_examined 1,284,310건, Lock_time 0.00007s입니다. 같은 날 다른 slow 2건은 Rows_examined 41,822건(09:14)과 5건(11:02)이었습니다 | `from submission s1_0 where s1_0.distribution_id=5 order by s1_0.student_id;` |
| 14:37:52 | mariadb-slow.log:15 | 위 느린 쿼리가 끝났습니다 (`# Time` 5:37:52 UTC). **(선후 불확실)** 다음 줄의 14:37:52.199와 같은 초입니다 | `# Time: 260916  5:37:52` |
| **14:37:52.199** | app.log:1243 | **첫 커넥션 leak 감지 WARN**입니다 (Connection@6e8f14d3, thread exec-6). 함께 찍힌 스택(1244~1257행)에 `ReportService.buildClassReport`(1253)와 `ReportController.classReport`(1254)가 있습니다 | `Connection leak detection triggered for org.mariadb.jdbc.Connection@6e8f14d3` |
| **14:38:01** | nginx-access.log:18801 | **report 요청이 늘기 시작했습니다.** 교사단말A가 `/api/classes/3/report` 를 요청해 200을 받았고, 이후 약 26~30초 간격으로 반복합니다 | `[16/Sep/2026:14:38:01 +0900] "GET /api/classes/3/report HTTP/1.1" 200 266` |
| 14:38:01.900 / .902 | app.log:1258, 1259 | class 3 리포트 생성이 끝났고, 14:37:52에 보고된 leak 커넥션이 반환됐습니다 (exec-6). **(선후 불확실)** access 18801행과 같은 초입니다 | `Previously reported leaked connection org.mariadb.jdbc.Connection@6e8f14d3` |
| 14:38:29 | nginx-access.log:18817, app.log:1278 | 교사단말A가 distribution 5를 재배포했습니다 (200). **(선후 불확실)** 두 파일 시각이 같은 초입니다. 재배포는 이날 29건 있는 평소 이벤트이고, 이 구간 안에서는 14:19:38(1182)과 14:44:19(2929)에도 있습니다 | `redistributed distribution 5 (assignment 3, class 3) reason=null` |
| 14:38:51 ~ 14:40:12 | app.log:1280, 1331, 1351, 1370 | leak 감지 대상 커넥션이 늘어났습니다. 6e8f14d3(14:37:52) → 2ac19f07(14:38:51) → 71d4b8e2(14:39:50) → 3f6c2a91(14:40:04) → 5b0e9e0c(14:40:12)로, **서로 다른 커넥션 5개**입니다. 이후 ERROR 줄에 찍힌 풀 크기는 total=5입니다 | `Connection leak detection triggered for org.mariadb.jdbc.Connection@5b0e9e0c` |
| 14:39:54 ~ 14:40:43 | nginx-access.log:18881, 18904, 18912 | report를 요청하는 클라이언트가 늘었습니다. 교사단말B(class 1·2)은 14:39:54, 교사단말C(class 3)은 14:40:27, 교사단말D(class 1)은 14:40:43부터 요청했습니다. 14:38~14:51 report 171건의 IP별 내역은 교사단말B 67건 / 교사단말A 40건 / 교사단말C 35건 / 교사단말D 29건입니다 | `[16/Sep/2026:14:39:54 +0900] "GET /api/classes/1/report HTTP/1.1" 200 271` |

### 2.2 이상 구간 (14:38 ~ 14:51)

| 시각 | 출처 | 사건 요약 | 원문 발췌 |
|---|---|---|---|
| 14:38 ~ 14:51 | nginx-access.log:18801~19392 (전체 집계) | **트래픽 구성이 바뀌었습니다.** 전체 요청은 분당 42.3건으로 평소(45.0)와 비슷합니다. report는 분당 0.16건 → 12.2건으로 늘었고, 그 밖의 요청은 44.9건 → 30.1건으로 줄었습니다 | (집계 결과) |
| 14:37:52 첫 발생 | app.log:1243 → 5251 | leak 감지 WARN이 반복됩니다. 분당 건수는 14:37 1 / 14:38 2 / 14:39 3 / 14:40 14 / 14:41 13 / 14:42 10 / 14:43 14 / 14:44 11 / 14:45 9 / 14:46 12 / 14:47 9 / 14:48 10 / 14:49 12 / 14:50 11 / 14:51 3, 총 134건입니다. 반환 INFO도 134건(1259 → 5267)입니다 | `Connection leak detection triggered for org.mariadb.jdbc.Connection@…` |
| 14:38:01 첫 발생 | nginx-access.log:18801 → 19381 | report 요청이 반복됩니다. 분당 건수는 14:38 3 / 14:39 3 / 14:40 10 / 14:41 16 / 14:42 15 / 14:43 19 / 14:44 14 / 14:45 15 / 14:46 19 / 14:47 12 / 14:48 14 / 14:49 16 / 14:50 10 / 14:51 5입니다 | `"GET /api/classes/3/report HTTP/1.1"` |
| **14:40:52.319** | app.log:1521 ~ 1523 | **첫 커넥션 풀 타임아웃 ERROR**입니다. 3000ms를 기다렸고 풀 상태는 total=5, active=5, idle=0, waiting=4였습니다. 이어서 `/api/units/M6-2/items` 에 unhandled exception이 찍혔습니다 | `itembank-pool - Connection is not available, request timed out after 3000ms` |
| 14:40:52 | nginx-access.log:18918 | 첫 500입니다 (`GET /api/units/M6-2/items`). **(선후 불확실)** app.log 1522행과 같은 초입니다 | `[16/Sep/2026:14:40:52 +0900] "GET /api/units/M6-2/items HTTP/1.1" 500 162` |
| 14:40:52 첫 발생 | app.log:1522 → 4980, nginx-access.log:18918 → 19305 | 풀 타임아웃 ERROR와 500이 반복됩니다. 두 파일의 분당 건수가 같습니다: 14:40 1 / 14:41 6 / 14:42 2 / 14:43 10 / 14:44 2 / 14:45 8 / 14:46 4 / 14:47 5 / 14:48 4 / 14:49 7, 총 49건. waiting 값은 1~7이고, 최댓값 7은 14:45:06(app.log:3249)입니다. 500이 난 경로는 `/api/units/*/items` 19건, `/api/units` 14건, `/api/items/N` 7건, `/api/classes/N/report` 5건, `/api/distributions/N` 4건입니다 | `(total=5, active=5, idle=0, waiting=…)` |
| **14:41:06** | nginx-access.log:18933, nginx-error.log:6 | **첫 504**이자 첫 `upstream timed out` 입니다 (교사단말A, `/api/classes/3/report`). **(선후 불확실)** 두 파일 시각이 같은 초입니다 | `upstream timed out (110: Connection timed out) while reading response header` |
| 14:41:06 첫 발생 | nginx-access.log:18933 → 19381, nginx-error.log:6 → 168 | 504와 `upstream timed out` 이 반복됩니다. 두 파일의 분당 건수가 같습니다: 14:41 3 / 14:42 5 / 14:43 5 / 14:44 6 / 14:45 5 / 14:46 6 / 14:47 3 / 14:48 5 / 14:49 8 / 14:50 7 / 14:51 2, 총 55건. 504는 모두 `/api/classes/N/report` 입니다 | `"GET /api/classes/3/report HTTP/1.1" 504 167` |
| **14:42:01** | nginx-error.log:9, 10, 11 | 같은 초에 세 줄이 파일 순서대로 찍혔습니다. `upstream timed out`(*225025) → **첫 `upstream server temporarily disabled`**(*225025) → **첫 `connect() failed (111: Connection refused)`**(*225045) | `upstream server temporarily disabled while reading response header from upstre` |
| 14:42:01 | nginx-access.log:18970 | 첫 502입니다 (교사단말B, `/api/classes/2/report`). **(선후 불확실)** nginx-error 9~11행과 같은 초입니다 | `[16/Sep/2026:14:42:01 +0900] "GET /api/classes/2/report HTTP/1.1" 502 157` |
| 14:42:04 | nginx-error.log:12 | 첫 `no live upstreams` 입니다 | `no live upstreams while connecting to upstream` |
| 14:42:01 ~ 14:51:02 | nginx-error.log:10 → 166, nginx-access.log:18970 → 19347 | 반복 건수입니다. `temporarily disabled` 26건: 14:42 2 / 14:43 1 / 14:44 4 / 14:45 3 / 14:46 3 / 14:47 1 / 14:48 4 / 14:49 4 / 14:50 4. `connect() failed` 14건: 2/1/1/2/2/1/1/2/2 (14:42~14:50). `no live upstreams` 68건: 14:42 11 / 14:43 1 / 14:44 4 / 14:45 12 / 14:46 14 / 14:47 5 / 14:48 5 / 14:49 6 / 14:50 9 / 14:51 1. access 502 82건: 14:42 13 / 14:43 2 / 14:44 5 / 14:45 14 / 14:46 16 / 14:47 6 / 14:48 6 / 14:49 8 / 14:50 11 / 14:51 1. 502가 난 경로는 report 32건, items 13건, units 12건, units/items 12건, distributions 9건, 그 외 3건입니다 | `" 502 157` |
| 14:49:51.552 | app.log:4980, 4981, nginx-access.log:19305 | 마지막 풀 타임아웃 ERROR이자 마지막 500입니다. **(선후 불확실)** 두 파일 시각이 같은 초입니다 | `itembank-pool - Connection is not available, request timed out after 3000ms` |
| 14:50:52 | nginx-error.log:158, 159 | 마지막 `temporarily disabled` 와 마지막 `connect() failed` 입니다 | `connect() failed (111: Connection refused) while connecting to upstream` |
| 14:51:02 | nginx-error.log:166, nginx-access.log:19347 | 마지막 `no live upstreams` 와 마지막 502입니다. **(선후 불확실)** 두 파일 시각이 같은 초입니다 | `no live upstreams while connecting to upstream, client: 10.20.x.x` |
| 14:51:37.674 | app.log:5251 | 마지막 leak 감지 WARN입니다 | `Connection leak detection triggered for org.mariadb.jdbc.Connection@…` |
| **14:51:47** | nginx-access.log:19381, nginx-error.log:168 | **마지막 504**이자 마지막 `upstream timed out` 이고, nginx-error.log의 마지막 줄입니다 (교사단말C, `/api/classes/3/report`). **(선후 불확실)** 두 파일 시각이 같은 초입니다 | `[16/Sep/2026:14:51:47 +0900] "GET /api/classes/3/report HTTP/1.1" 504 167` |
| 14:51:49.148 | app.log:5267 | 마지막 leak 커넥션 반환 INFO입니다 | `Previously reported leaked connection org.mariadb.jdbc.Connection@…` |

### 2.3 복귀 후 (14:52 ~ 15:02)

| 시각 | 출처 | 사건 요약 | 원문 발췌 |
|---|---|---|---|
| 14:52 ~ 15:02 | nginx-access.log:19393~19842, app.log:5269~5288 (집계); nginx-error.log:168 (구간 앞 마지막 줄, 이후 파일 끝), mariadb-slow.log:15~21 (구간 앞 마지막 항목, 이후 파일 끝) | 오류, 5xx, 타임아웃, leak 범주가 모두 0건입니다. 트래픽은 분당 40.9건이고 report는 분당 0.27건입니다. nginx-error.log와 mariadb-slow.log에는 기록이 없습니다 | (집계 결과) |
| 14:52:09 | app.log:5269 | 평소에도 나오는 업무성 WARN입니다 | `WARN ... state conflict: 마감된 과제는 재배포할 수 없습니다` |
| 14:59:15 | nginx-access.log:19701 | 복귀 후 첫 report 요청이고 200입니다. 이후 14:59:52(19724)와 15:01:20(19781)에도 report 요청이 있었고 모두 200입니다 | `[16/Sep/2026:14:59:15 +0900] "GET /api/classes/3/report HTTP/1.1" 200 257` |

## 3. 가설과 검증

> 가설과 반증 조건은 판정 없이 먼저 세웠습니다. 그 뒤 로그로 확인할 수 있는 항목만 실행해 각 표의 **로그 확인 결과** 열에 적었습니다(실행 명령과 출력은 3.6).
> 판정은 유지 / 기각 / 판단 불가(로그 부족) 세 가지이고, 로그로 본 범위 안의 결론입니다. 코드 · 설정과 대조한 최종 판정은 3.7 에 있습니다.
> 가장 많이 나온 메시지(`Connection is not available, request timed out` 49건, `Connection leak detection triggered` 134건, `no live upstreams` 68건)는 **증상**으로 봅니다. 각 가설은 "커넥션이 왜 10초 넘게 묶였는가, 그리고 왜 14:37 이후에만 그랬는가"를 설명하려는 문장입니다.
> 지지 근거는 `2절 표의 행(시각) — 원본 파일:줄번호` 형식으로 적었습니다.
> DB 확인 명령은 모두 읽기 계정 `readonly` / `[SECRET]` 로 실행합니다. 쓰기 계정은 쓰지 않습니다.

### 3.0 가설 정리 전에 추가로 확인한 사실

2절을 쓴 뒤 저장소와 로그에서 추가로 확인한 것입니다. 해석은 하지 않았습니다.

| 사실 | 출처 |
|---|---|
| 저장소 설정의 leak 감지 임계값은 10초입니다(`leak-detection-threshold: 10000`). 풀 크기 5, 연결 대기 3초입니다. 주석에 "운영 값과 같게 맞춘다"고 적혀 있습니다. **운영 값이 이와 같다는 것은 아직 확인하지 않은 전제입니다** | `modern/api/src/main/resources/application.yml:11~18` |
| 위 전제를 따르면 6e8f14d3 은 14:37:52.199 − 10초 ≈ **14:37:42.2** 에 빌린 커넥션입니다. slow query 시작(`SET timestamp` 14:37:42)과 같은 초입니다 | app.log:1243, mariadb-slow.log:20 |
| 같은 커넥션은 14:38:01.902 에 반환됐습니다. slow query 종료(14:37:52)와 `built class report` 로그(14:38:01.900) 사이는 약 10초입니다 | mariadb-slow.log:15, app.log:1258~1259 |
| `built class report` 는 하루 190건이고, 14:37:52 이전에는 leak WARN 이 0건입니다 | app.log:103 등 (`grep -n 'built class report'`) |
| leak WARN 134건의 스택에 나오는 `com.example` 메서드는 전부 `ReportService.buildClassReport` 입니다 | app.log (`grep -A10 'leak detection' \| grep 'at com.example'` 집계) |
| report 요청의 referrer 가 이상 구간에 바뀌었습니다. 구간 밖 58건은 모두 `/teacher/classes/N`, 구간 안 171건은 모두 `/teacher/classes/N/report` 입니다. `/report` referrer 는 18801행(14:38:01)에서 처음 나옵니다 | nginx-access.log:18801 (`grep -n 'teacher/classes/[0-9]*/report"'` = 171건) |
| 교사단말A 의 report 요청 시각: 14:38:01 → 14:38:27 → 14:38:57 → 14:39:24 → 14:39:55 → 14:40:19 (간격 24~31초) | nginx-access.log:18801 이후 |
| `ReportService.buildClassReport` 는 `@Transactional` 메서드 안에서 조회를 끝낸 뒤 `sign()`(SHA-256 200,000회 반복)을 호출합니다 | `modern/api/src/main/java/com/example/assignment/ReportService.java:23, 41~71, 85~93` |
| 스키마에는 `submission.distribution_id` 인덱스가 있습니다 | `db/mariadb/init/01-schema.sql:112` |
| `SIGNATURE_ROUNDS` 는 저장소 초기 커밋부터 있었습니다. 저장소 이력은 운영 배포 이력이 아닙니다 | `git log -S SIGNATURE_ROUNDS -- modern/api` → `1260542` 1건 |

### 3.1 가설 A — 애플리케이션 코드

| 항목 | 내용 | 로그 확인 결과 |
|---|---|---|
| 가설 | `ReportService.buildClassReport` 가 조회를 끝낸 뒤에도 트랜잭션(커넥션)을 쥔 채 CPU 작업 `sign()` 을 수행해, report 요청 1건이 커넥션 1개를 수 초~수십 초 점유했고, 동시 report 요청이 풀 크기(5)를 넘자 다른 API 가 커넥션을 얻지 못했다. | **유지** — leak 134건 전부 `buildClassReport` 이고(A-1), 커넥션 반환 134건 모두 `sign()` 뒤에 찍히는 완료 로그와 0.1초 이내(A-4), 점유 15.54~21.51초(A-3). 단 평시 50건은 leak 0건이라(A-5) 이 가설만으로는 14:37 시작을 설명하지 못해 가설 C 와 함께 봐야 합니다 |
| 지지 근거 | · 2.1 표 14:37:52.199 행 — app.log:1243~1254 (leak 스택에 `ReportService.buildClassReport`, `ReportController.classReport`)<br>· 2.1 표 14:38:01.900 행 — app.log:1258~1259 (리포트 완료 직후 커넥션 반환. slow query 종료 후 약 10초)<br>· 2.1 표 14:38:51 ~ 14:40:12 행 — app.log:1280, 1331, 1351, 1370 (서로 다른 커넥션 5개 = 풀 크기 5)<br>· 2.2 표 14:40:52.319 행 — app.log:1521~1523 (`total=5, active=5, idle=0`)<br>· 3.0: leak 134건 전부 같은 메서드 | · A-3: 점유 시간 n=134, min 15.54 / 중앙값 18.93 / max 21.51 / 평균 19.01초 (획득 시각 = leak WARN − 10초, 3.0 전제)<br>· A-5: 이상 구간 report 완료 140건 중 134건이 10초를 넘음 |
| 반증 조건 | · `sign()` 단독 소요 시간이 운영과 비슷한 환경에서 1초 미만이면, 커넥션 점유 시간을 이 코드로 설명할 수 없다.<br>· 평시(14:37 이전) report 50건도 같은 `sign()` 을 돌렸는데 leak 0건이다. 평시 report 가 10초 미만에 끝났다면 이 코드 **단독**으로는 14:37 이후만 설명하지 못하고, 동시 실행 수(가설 C) 또는 쿼리 지연(가설 B)과 묶어야 한다.<br>· 이상 구간의 leak 스택에 `buildClassReport` 가 아닌 메서드가 섞여 있거나, 같은 커넥션의 반환 시각이 리포트 완료 로그와 맞지 않으면 틀리다. | · ① `sign()` 단독 시간: **미확인** — 로그로 잴 수 없음(테스트 필요, 이번 범위 밖)<br>· ② 평시 50건 leak 0건: **해당** — 단독 설명 불가, C 와 묶어야 함. 평시 점유 시간과 이상 구간에 점유가 길어진 이유(15~21초)는 **판단 불가**: 요청별 처리 시간(nginx `$request_time` · `$upstream_response_time`, 앱 요청 시간 로그)과 CPU 사용률 · 스레드 덤프가 있었다면 비교할 수 있었음<br>· ③ 다른 메서드 0건, 반환 시각 불일치 0건: **해당 없음** (가설을 깨지 않음) |
| 확인 방법 | · 코드: `ReportService.java:41~71` (트랜잭션 경계와 `sign()` 호출 위치), `application.yml:11~18`<br>· `sign()` 소요 시간: `ReportServiceTest` 에 `ReportService.sign("1\|학생A\|...")` 실행 시간을 재는 테스트를 하나 두고 `./gradlew test --tests "com.example.assignment.ReportServiceTest"`<br>· 커넥션 점유 시간: leak WARN 과 반환 INFO 를 커넥션 ID 로 짝지어 차이 계산 — `grep -nE 'leak detection triggered\|was returned to the pool' incident-logs/a-connection-pool/app.log \| grep -oE 'T[0-9:.]*\|Connection@[0-9a-f]*'`<br>· 스택 집계: `grep -A10 'Connection leak detection triggered' incident-logs/a-connection-pool/app.log \| grep -o 'at com.example[^(]*' \| sort \| uniq -c` | · 실행: 3.6 A-1 ~ A-5<br>· 왼쪽 칸의 `grep -oE` 명령은 커넥션별 짝짓기를 못 해서 A-3 의 awk 로 바꿔 실행<br>· 미실행: `sign()` 측정 테스트 (로그 확인 아님) |

### 3.2 가설 B — DB · 쿼리

| 항목 | 내용 | 로그 확인 결과 |
|---|---|---|
| 가설 | `submission` 조회(`where distribution_id=? order by student_id`)가 운영 DB 에서 인덱스를 타지 못하거나 테이블이 커서 약 10초 걸렸고, 쿼리를 기다리는 동안 커넥션이 묶여 풀이 고갈됐다. | **기각** (풀 고갈의 원인으로서) — slow log 에 2.04초 쿼리가 남았으므로 임계값은 2.04초 이하인데, 이상 구간 slow 는 1건뿐(B-1). report 1건은 쿼리 5회(학급 1 + 배포 1 + 제출 3, 이상 구간 140건 모두 배포 3개)라 기록 안 된 쿼리의 합은 10.2초 미만이고, 최소 점유 15.54초(A-3)보다 짧음. 전제 2개: slow log 설정이 하루 동안 같았음(B-2 에서 설정 기록 0건, 미확인), 쿼리 수는 저장소 코드 기준이며 운영 코드가 같음(미확인). 14:37:42 의 9.8초 · 1,284,310행 쿼리 자체는 원인을 모른 채 남김 |
| 지지 근거 | · 2.1 표 14:37:42 행 — mariadb-slow.log:20 (Query_time 9.81s, Rows_examined 1,284,310, Rows_sent 4, Lock_time 0.00007s)<br>· 2.1 표 14:37:52 행 — mariadb-slow.log:15 (종료 시각이 첫 leak WARN 과 같은 초)<br>· 3.0: 6e8f14d3 획득 시각(≈14:37:42.2)이 slow query 시작과 같은 초<br>· 같은 날 다른 slow 2건은 Rows_examined 41,822 / 5 (mariadb-slow.log:4, 11) | · B-3: 첫 커넥션 6e8f14d3 도 쿼리 종료(14:37:52) 뒤 약 9.9초 더 점유함 |
| 반증 조건 | · slow log 임계값(`long_query_time`)이 2초 이하로 확인되면(2.04초 건이 기록됨, mariadb-slow.log:11), 이상 구간 leak 134건 중 slow query 가 1건뿐이므로 나머지 133건의 점유는 쿼리 지연으로 설명되지 않는다.<br>· `EXPLAIN` 이 `idx_submission_distribution` 을 쓰고 `rows` 가 수십 건 이하로 나오면 1,284,310 은 실행계획 문제가 아니다.<br>· 14:37:42 전후에 해당 쿼리를 막는 락 · 대량 쓰기 · 백업이 없고, 같은 쿼리를 다시 실행해도 빠르면 일회성 지연이다. | · ① 임계값 2초 이하: 로그로 **해당** (Query_time 2.041903 건이 기록됨, mariadb-slow.log:11) — 나머지 133건 설명 불가<br>· ② `EXPLAIN`: **미실행** (DB 접속 필요)<br>· ③ 락 · 대량 쓰기 · 백업: **판단 불가** — slow log 밖의 DB 로그가 없음. general log, `SHOW ENGINE INNODB STATUS` 기록, performance_schema 이력, 백업 작업 로그가 있었다면 확인할 수 있었음 |
| 확인 방법 | · `mysql -h 127.0.x.x -u readonly -p[SECRET] itembank -e "SHOW VARIABLES LIKE 'long_query_time'; SHOW VARIABLES LIKE 'log_slow%';"`<br>· `mysql ... -e "SHOW INDEX FROM submission; EXPLAIN SELECT id,distribution_id,score,student_id,submitted_at FROM submission WHERE distribution_id=5 ORDER BY student_id; SELECT COUNT(*) FROM submission;"`<br>· 스키마 대조: `db/mariadb/init/01-schema.sql:105~115`<br>· 한계: 로컬 compose 는 시드 데이터라 실행계획 모양만 볼 수 있습니다. 행 수 · 통계는 운영 DB(readonly)에서 봐야 합니다. | · 실행: 3.6 B-1 ~ B-3<br>· 미실행: 왼쪽 칸의 `mysql` 명령 (로그가 아니라 readonly DB 접속이 필요) |

### 3.3 가설 C — 트래픽 · 클라이언트

| 항목 | 내용 | 로그 확인 결과 |
|---|---|---|
| 가설 | 14:38 무렵 교사용 리포트 **페이지**(`/teacher/classes/N/report`)가 쓰이기 시작했고, 이 페이지가 report API 를 약 25~30초마다 자동 호출해 report 동시 실행 수가 풀 크기를 넘었다. 504/502 를 받은 뒤의 재요청이 부하를 유지했다. | **유지** (자동 호출 부분은 판단 불가) — 동시 점유가 최대 5(풀 크기)에 14:40:25.80 처음 닿았고(C-1), 풀 타임아웃 ERROR 49건 모두 `active=5, idle=0`(C-2). `/report` referrer 는 구간 안 171건 / 밖 0건(C-3). 하지만 요청 간격이 고르지 않고 access 시각이 응답 종료 시각이라, 타이머가 부른 것인지는 판단할 수 없음 |
| 지지 근거 | · 2.1 표 14:38:01 행 — nginx-access.log:18801 (report 반복 시작, 이후 26~30초 간격)<br>· 2.1 표 14:39:54 ~ 14:40:43 행 — nginx-access.log:18881, 18904, 18912 (클라이언트 4개로 늘어남)<br>· 2.2 표 14:38 ~ 14:51 행 — nginx-access.log:18801~19392 (report 분당 0.16 → 12.2건, 전체 요청량은 비슷)<br>· 3.0: report referrer 가 구간 밖 `/teacher/classes/N` 58건 → 구간 안 `/teacher/classes/N/report` 171건, 첫 등장 18801행<br>· 2.3 표 14:59:15 행 — nginx-access.log:19701 (복귀 후 report 는 다시 드문드문, 모두 200) | · C-6: 14:38 이전 report 요청 간격 43개 중 120초 미만은 2개<br>· C-4: 이상 구간 간격 166개 중 15~24초가 90개 |
| 반증 조건 | · `/teacher/classes/N/report` referrer 가 14:38 이전(다른 날 로그 포함)에도 같은 빈도로 있었으면 "새 페이지" 부분이 틀리다.<br>· IP별 요청 간격이 일정하지 않으면(사람이 누른 새로고침) 자동 호출 부분이 틀리다.<br>· 이상 구간에 report 동시 처리 수가 늘 5 미만이었다면 동시 실행 수로 풀 고갈을 설명할 수 없다.<br>· 같은 페이지 · 같은 빈도의 요청이 다른 날에도 있었는데 장애가 없었다면, 트래픽만으로는 원인이 아니다. | · ① referrer 가 이전에도 있었는지: 당일 14:38 이전 0건이라 **해당 없음**. 다른 날은 **판단 불가** — 다른 날 access 로그가 필요<br>· ② 간격 일정성: **판단 불가** — 가설문의 "약 25~30초"는 3.0 의 교사단말A 첫 6건 기준이고, 전체 166개는 15~24초가 가장 많음(90개). 그래도 0~4초 29개, 35~44초 22개로 흩어짐(C-4). 짧은 간격 29개 중 22개는 직전 응답이 5xx, 7개는 200(C-5). access 로그 포맷에 `$request_time` 이 있었다면 요청 시작 시각을 되살려 간격을 판단할 수 있었음<br>· ③ 동시 처리 수 5 미만: **해당 없음** (최대 5, C-1). 단 C-1 은 10초 넘게 쥔 커넥션만 셈<br>· ④ 다른 날 같은 요청이 있었는데 장애가 없었는지: **판단 불가** — 다른 날 로그 필요 |
| 확인 방법 | · referrer 분포: `awk '/classes\/[0-9]+\/report/ {print $11}' incident-logs/a-connection-pool/nginx-access.log \| sort \| uniq -c`<br>· IP별 간격: `awk '/classes\/[0-9]+\/report/ {print $1, substr($4,14,8)}' incident-logs/a-connection-pool/nginx-access.log \| sort -k1,1 -k2,2` 로 정렬 후 같은 IP 의 이웃 시각 차이 계산<br>· 동시 실행 수: app.log 의 leak WARN 과 반환 INFO 를 짝지어 각 시점에 반환 전인 커넥션 수를 셈<br>· 프런트(lms.example.com) 리포트 페이지의 자동 새로고침 · 재시도 코드와 그 배포 이력 확인 — 이번 수집 범위에 없음. 프런트 담당에게 요청 | · 실행: 3.6 C-1 ~ C-6<br>· 미실행: 프런트 자동 새로고침 코드 · 배포 이력 (수집 범위 밖) |

### 3.4 가설 D — 배포 · 설정 변경

| 항목 | 내용 | 로그 확인 결과 |
|---|---|---|
| 가설 | 9/16 또는 그 직전에 API 운영 설정(풀 크기 · 연결 대기 · 서명 반복 횟수) 또는 프런트가 바뀌었고, 그 변경 이후 처음 report 사용이 몰린 시점이 14:37 이다. | **판단 불가 (로그 부족)** — app.log 는 06:43:56 요청 로그부터 시작해 기동 로그가 없고(D-1), app.log · nginx-error.log 에 기동 · 설정 · 재적재 흔적이 0건(D-2, D-3). 배포 시점이나 운영 설정값을 보여 주는 자료가 수집 범위에 없음. 필요한 로그: API · 프런트 운영 배포 이력, 앱 기동 로그(`Started ... in`, Hikari 설정 출력), 9/15 이전 같은 시간대 로그 |
| 지지 근거 | · **직접 근거 없음.** 2.1 표 14:07 ~ 15:02 행 — app.log · nginx-error.log · mariadb-slow.log 전체에서 `reload` · `restart` · `Started` · `config` · `HikariPool-` 0건. 즉 이상 구간 안에서 API 재시작 · 설정 재적재는 로그에 남지 않았습니다<br>· 간접: 3.0 의 referrer 변화(`/teacher/classes/N/report`)는 프런트에 새 경로가 생겼을 가능성과 맞지만, 배포 기록 자체는 아닙니다 | — |
| 반증 조건 | · 운영 배포 이력에 9/16 이전 일정 기간 API · 프런트 배포가 없으면 틀리다.<br>· 운영 Hikari 설정 · `SIGNATURE_ROUNDS` 가 장애 전후로 같고, 그 값이 이전 정상일에도 쓰였으면 틀리다.<br>· app.log 시작 부분(06:43:56 이전 기동 로그)에 찍힌 설정값이 저장소 값과 같고 기동 시각이 9/16 이전이면 "당일 배포" 부분이 틀리다. | · ① 배포 이력: **판단 불가** (자료 없음)<br>· ② 운영 설정이 같은지: **판단 불가** (운영 설정값이 찍힌 로그 없음)<br>· ③ 기동 로그의 설정 · 시각: **판단 불가** (app.log 에 기동 로그 없음, D-1) |
| 확인 방법 | · `head -20 incident-logs/a-connection-pool/app.log` (06:43:56 이전 기동 · 설정 로그가 남았는지)<br>· 운영 배포 이력 · 설정 저장소 요청 (이번 수집 범위에 없음. 참고로 `incident-logs/b-deploy-5xx/deploy-history.md` 같은 형식의 자료)<br>· 저장소 이력: `git log --oneline -- modern/api/src/main/resources/application.yml modern/api/src/main/java/com/example/assignment/ReportService.java` (저장소 이력은 운영 배포 이력이 아님을 감안) | · 실행: 3.6 D-1 ~ D-3<br>· 미실행: 운영 배포 이력 요청, `git log` (저장소 이력은 운영 배포 이력이 아님. 3.0 에 1건 기록) |

### 3.5 가설 밖에 둔 관찰 (증상으로 분류)

아래는 원인 가설로 세우지 않고 증상으로 둔 것입니다. 가설 A~D 중 무엇이 맞든 설명돼야 하는 현상입니다.

| 관찰 | 출처 | 증상으로 둔 이유 |
|---|---|---|
| 풀 타임아웃 ERROR 49건 = access 500 49건 | 2.2 표 14:40:52 첫 발생 행 — app.log:1522 → 4980 | 풀이 비어 있다는 결과입니다. 왜 비었는지는 말하지 않습니다 |
| `upstream timed out` 55건 = 504 55건, 모두 report | 2.2 표 14:41:06 행 — nginx-error.log:6 → 168 | report 응답이 nginx 대기 시간을 넘었다는 결과입니다 |
| `temporarily disabled` → `connect() failed` → `no live upstreams` → 502 82건, report 외 경로 포함 | 2.2 표 14:42:01 ~ 14:51:02 행 — nginx-error.log:9~166 | nginx 가 upstream 을 일시 제외한 결과로, 고갈이 다른 API 로 번진 것으로 보이는 경로입니다. nginx `max_fails` · `fail_timeout` · `proxy_read_timeout` 설정은 수집 범위에 없어 확대 요인인지는 따로 확인해야 합니다 |

### 3.6 로그 확인 실행 기록

- 실행일: 2026-10-06. 저장소 루트에서 실행했습니다. 원본 로그는 읽기만 했습니다.
- 시각 계산은 KST 기준 초 단위입니다. `app.log` 는 ms 까지, `nginx-access.log` 는 초까지만 있습니다.
- A-3 · C-1 은 "커넥션을 빌린 시각 = leak WARN − 10초"를 전제로 합니다(3.0, `application.yml:18`). 10초 안에 반환된 커넥션은 leak 로그가 없어서 세지 못합니다.
- D-2 · D-3 의 `grep -c` 는 0건이면 종료 코드 1을 돌려줍니다. 출력 숫자에는 영향이 없습니다.

**명령**

```bash
L=incident-logs/a-connection-pool
echo "== A-1"; grep -A10 'Connection leak detection triggered' $L/app.log | grep -o 'at com.example[^(]*' | sort | uniq -c
echo "== A-2"; grep -c 'Connection leak detection triggered' $L/app.log; grep -c 'was returned to the pool' $L/app.log
echo "== A-3"; grep -E 'leak detection triggered|was returned to the pool' $L/app.log | awk '{split($1,a,"T"); split(substr(a[2],1,12),h,":"); t=h[1]*3600+h[2]*60+h[3]; match($0,/Connection@[0-9a-f]+/); c=substr($0,RSTART,RLENGTH); if($0~/triggered/) w[c]=t; else if(c in w){print t-w[c]+10; delete w[c]}}' | sort -n | awk '{a[NR]=$1; s+=$1} END{printf "n=%d 점유시간 min=%.2f median=%.2f max=%.2f mean=%.2f\n",NR,a[1],a[int((NR+1)/2)],a[NR],s/NR}'
echo "== A-4"; grep -nE 'built class report|was returned to the pool' $L/app.log | awk -F: '$1>=1243' | awk '{split($1,a,"T"); split(substr(a[2],1,12),h,":"); t=h[1]*3600+h[2]*60+h[3]; th=$6; if($0~/built class/) b[th]=t; else if(th in b){if(t-b[th]<0.1) ok++; else bad++; delete b[th]}} END{print "반환이 같은 스레드 완료 로그 0.1초 이내:",ok+0," 그 외:",bad+0}'
echo "== A-5"; grep -n 'built class report' $L/app.log | awk -F: '$1<1243' | wc -l; grep -n 'built class report' $L/app.log | awk -F: '$1>=1243' | wc -l
echo "== B-1"; grep '^# Time' $L/mariadb-slow.log; grep -o 'Query_time: [0-9.]*' $L/mariadb-slow.log
echo "== B-2"; grep -ciE 'long_query_time|log_slow|started with' $L/mariadb-slow.log
echo "== C-1"; grep -E 'leak detection triggered|was returned to the pool' $L/app.log | awk '{split($1,a,"T"); split(substr(a[2],1,12),h,":"); t=h[1]*3600+h[2]*60+h[3]; if($0~/triggered/) print t-10, 1; else print t, -1}' | sort -n | awk '{c+=$2; if(c>m){m=c; at=$1}} END{printf "최대 동시 점유=%d (첫 도달 %02d:%02d:%05.2f)\n", m, at/3600, (at%3600)/60, at%60}'
echo "== C-2"; grep -E '^2026-' $L/app.log | grep -o 'active=[0-9]*, idle=[0-9]*' | sort | uniq -c
echo "== C-3"; awk '/classes\/[0-9]+\/report/ {split($4,t,":"); hm=t[2]":"t[3]; w=(hm>="14:38" && hm<="14:51")?"IN":"OUT"; r=($11 ~ /\/report"$/)?"/teacher/classes/N/report":"/teacher/classes/N"; print w, r}' $L/nginx-access.log | sort | uniq -c
echo "== C-4"; awk '/classes\/[0-9]+\/report/ {split($4,t,":"); hm=t[2]":"t[3]; if(hm>="14:38"&&hm<="14:51") print $1"_"$7, t[2]*3600+t[3]*60+t[4]}' $L/nginx-access.log | sort -k1,1 -k2,2n | awk '{if($1==p) print int(($2-q)/5)*5; p=$1; q=$2}' | sort -n | uniq -c | awk '{printf "%s~%s초:%s  ", $2, $2+4, $1} END{print ""}'
echo "== C-5"; awk '/classes\/[0-9]+\/report/ {split($4,t,":"); hm=t[2]":"t[3]; if(hm>="14:38"&&hm<="14:51") print $1"_"$7, t[2]*3600+t[3]*60+t[4], $9}' $L/nginx-access.log | sort -k1,1 -k2,2n | awk '{if($1==p){g=($2-q<5)?"<5s":">=5s"; print g, "직전="ps} p=$1; q=$2; ps=$3}' | sort | uniq -c
echo "== C-6"; awk '/classes\/[0-9]+\/report/ {split($4,t,":"); hm=t[2]":"t[3]; if(hm<"14:38") print $1"_"$7, t[2]*3600+t[3]*60+t[4]}' $L/nginx-access.log | sort -k1,1 -k2,2n | awk '{if($1==p){n++; if($2-q<120) c++} p=$1; q=$2} END{print "간격",n,"개 중 120초 미만",c+0}'
echo "== D-1"; head -1 $L/app.log | cut -c1-110
echo "== D-2"; grep -ciE 'Starting |Started |Tomcat initialized|HikariPool-[0-9]+ - Start|itembank-pool - Start|reload|refresh|restart|profile' $L/app.log
echo "== D-3"; grep -ciE 'signal|reload|start|exit' $L/nginx-error.log
echo "== B-3"; grep -n '6e8f14d3' $L/app.log | head -2 | cut -c1-40; sed -n '15p;20p' $L/mariadb-slow.log
echo "== B-4"; grep -n 'built class report' $L/app.log | awk -F: '$1>=1243' | grep -o '[0-9]* distributions' | sort | uniq -c; grep -c 'FetchType.EAGER' modern/api/src/main/java/com/example/assignment/*.java | awk -F: '{s+=$2} END{print "EAGER", s}'
```

**출력 (가공 없이 그대로)**

```text
== A-1
    134 at com.example.assignment.ReportService$$SpringCGLIB$$0.buildClassReport
== A-2
134
134
== A-3
n=134 점유시간 min=15.54 median=18.93 max=21.51 mean=19.01
== A-4
반환이 같은 스레드 완료 로그 0.1초 이내: 134  그 외: 0
== A-5
50
140
== B-1
# Time: 260916  0:14:09
# Time: 260916  2:02:42
# Time: 260916  5:37:52
Query_time: 2.318446
Query_time: 2.041903
Query_time: 9.812417
== B-2
0
== C-1
최대 동시 점유=5 (첫 도달 14:40:25.80)
== C-2
     49 active=5, idle=0
== C-3
    171 IN /teacher/classes/N/report
     58 OUT /teacher/classes/N
== C-4
0~4초:29  5~9초:3  10~14초:3  15~19초:50  20~24초:40  25~29초:8  30~34초:11  35~39초:13  40~44초:9  
== C-5
      7 <5s 직전=200
      2 <5s 직전=500
      9 <5s 직전=502
     11 <5s 직전=504
     70 >=5s 직전=200
      3 >=5s 직전=500
     23 >=5s 직전=502
     41 >=5s 직전=504
== C-6
간격 43 개 중 120초 미만 2
== D-1
2026-09-16T06:43:56.753+09:00  INFO 1 --- [item-bank-api] [nio-8080-exec-9] c.example.common.GlobalExceptionHa
== D-2
0
== D-3
0
== B-3
1243:2026-09-16T14:37:52.199+09:00  WARN
1259:2026-09-16T14:38:01.902+09:00  INFO
# Time: 260916  5:37:52
SET timestamp=1789537062;
== B-4
    140 3 distributions
EAGER 0
```

**출력을 읽는 법**

| ID | 무엇을 셌나 | 결과 |
|---|---|---|
| A-1 | leak WARN 스택의 `com.example` 메서드 | `buildClassReport` 134건, 다른 메서드 0건 |
| A-2 | leak WARN · 반환 INFO 건수 | 134 / 134 |
| A-3 | 커넥션별 점유 시간 (10초 + WARN→반환 간격) | 134건, min 15.54 / 중앙값 18.93 / max 21.51 / 평균 19.01초 |
| A-4 | 반환 INFO 가 같은 스레드의 `built class report`(`sign()` 다음 줄 로그) 와 0.1초 이내인가 | 134 / 134 |
| A-5 | report 완료 건수 (1243행 전 / 후) | 14:37:52 전 50건(leak 0건) / 후 140건 |
| B-1 | slow log 건수 · Query_time | 3건. 2.32 / 2.04 / 9.81초. 이상 구간은 9.81초 1건 |
| B-2 | slow log 에 설정값(`long_query_time` 등) 기록 | 0건 |
| B-3 | 첫 커넥션 6e8f14d3 | WARN 14:37:52.199 (획득 ≈ 14:37:42.2) → 쿼리 14:37:42~14:37:52 → 반환 14:38:01.902. 쿼리 뒤 약 9.9초 더 점유 |
| B-4 | report 1건의 쿼리 수 근거 | 이상 구간 140건 모두 `3 distributions`, assignment 엔티티에 EAGER 0건 → 학급 1 + 배포 1 + 제출 3 = 5회 (`ReportService.java:43~54`) |
| C-1 | 동시 점유 커넥션 수의 최댓값 | 5, 처음 닿은 시각 14:40:25.80 |
| C-2 | 풀 타임아웃 ERROR 엔트리의 풀 상태 | 49건 모두 `active=5, idle=0` |
| C-3 | report 요청 referrer (14:38~14:51 안 / 밖) | 안: `/teacher/classes/N/report` 171 / 밖: `/teacher/classes/N` 58 |
| C-4 | (IP, 경로) 별 이웃 요청 간격 분포, 5초 단위 | 0~4초 29, 5~9초 3, 10~14초 3, 15~19초 50, 20~24초 40, 25~29초 8, 30~34초 11, 35~39초 13, 40~44초 9 (합 166) |
| C-5 | 간격 5초 미만 / 이상일 때 직전 응답 status | 5초 미만 29: 200 7, 500 2, 502 9, 504 11 / 5초 이상 137: 200 70, 500 3, 502 23, 504 41 |
| C-6 | 14:38 이전 (IP, 경로) 별 간격 | 43개 중 120초 미만 2개 |
| D-1 | app.log 첫 줄 | 06:43:56 요청 처리 로그 (기동 로그 아님) |
| D-2 | app.log 기동 · 설정 · 재적재 흔적 | 0건 |
| D-3 | nginx-error.log 시그널 · 재적재 · 기동 흔적 | 0건 |

### 3.7 판정

> 3.1 ~ 3.6 에서 유지된 가설 A · C 를 `modern/api` 의 설정 파일, 그리고 로그에 찍힌 클래스 · 메서드 · 설정 키와 대조했습니다.
> 설정값은 파일에서 읽은 값만 씁니다. 벤치마크 · 테스트 같은 별도 실행은 하지 않았습니다. 따라서 `sign()` 의 소요 시간은 적지 않습니다.
> 단서로 쓴 로그 항목: 클래스 `ReportService` · `ReportController` · `ItemService` · `UnitService` · `DistributionService` · `GlobalExceptionHandler` · `SqlExceptionHelper` · `ProxyLeakTask`, 메서드 `buildClassReport` · `JpaTransactionManager.doBegin`, 풀 이름 `itembank-pool`, 문자열 `after 3000ms` · `total=5`, 그리고 leak 감지 자체. 이 장애에 프로시저는 나오지 않습니다.

#### 3.7.1 로그 문자열 ↔ 설정 대조

3.0 에서는 "운영 값이 저장소 설정과 같다"를 확인하지 않은 전제로 두었습니다. 로그에 찍힌 문자열로 4개 키 중 3개는 확인됩니다.

| 설정 키 | 파일 값 | 로그에 찍힌 값 | 일치 |
|---|---|---|---|
| `spring.datasource.hikari.pool-name` | `itembank-pool` (`application.yml:10`) | `itembank-pool - Connection is not available` (app.log:1522) | 일치 |
| `spring.datasource.hikari.maximum-pool-size` | `5` (`application.yml:14`) | `total=5` (app.log:1522) | 일치 |
| `spring.datasource.hikari.connection-timeout` | `3000` (`application.yml:15`) | `request timed out after 3000ms` (app.log:1522) | 일치 |
| `spring.datasource.hikari.leak-detection-threshold` | `10000` (`application.yml:18`) | 값이 찍힌 로그 없음. leak 감지가 켜져 있다는 것(`ProxyLeakTask` 268줄)만 보임 | **로그로 확인 못 함** |
| `spring.jpa.open-in-view` | `false` (`application.yml:22`) | 직접 찍힌 값 없음 | 로그로 확인 못 함 |

#### 3.7.2 가설별 코드 · 설정 대조

| 가설 | 판정 | 근거 (파일:줄번호와 인용) |
|---|---|---|
| A 코드 — `buildClassReport` 가 트랜잭션(커넥션)을 쥔 채 `sign()` 을 수행 | **코드 · 설정이 가설을 지지한다** | · **커넥션을 빌리는 시점:** leak 스택이 `HikariDataSource.getConnection`(app.log:1245) ← `JpaTransactionManager.doBegin`(app.log:1250) ← `ReportService.buildClassReport`(app.log:1253) 순서입니다. 코드는 `ReportService.java:41` `@Transactional` / `:42` `public ClassReport buildClassReport(Integer classId)` 입니다. 커넥션은 첫 쿼리가 아니라 **트랜잭션 시작 시점**에 빌리므로, 메서드 전체가 점유 구간입니다.<br>· **메서드 안의 순서:** 조회(`:43` `classRoomRepository.findById`, `:46` `distributionRepository.findBy...`, `:53~54` `submissionRepository.findBy...`) → `:71` `String signature = sign(payload.toString());` → `:73` `log.info("built class report ...")` 입니다. `sign()` 은 `:88~91` 에서 SHA-256 을 `SIGNATURE_ROUNDS` 회 반복하고, 값은 `:23` `static final int SIGNATURE_ROUNDS = 200_000;` 입니다.<br>· **반환 시점:** `open-in-view: false`(`application.yml:22`)라서 커넥션은 요청이 아니라 트랜잭션이 끝날 때 반환됩니다. A-4(반환 INFO 가 `:73` 완료 로그와 0.1초 이내, 134/134)는 이 코드 순서와 맞습니다.<br>· **코드 주석:** `:69~70` `// TODO: 트랜잭션 밖으로 — 외부 집계 시스템 호출을 흉내내는 긴 루프.` `// DB 작업은 위에서 끝났는데 커넥션(트랜잭션)을 쥔 채로 돈다.` 작성자가 이 구조를 알고 있었다는 기록입니다.<br>· **쥔 쪽과 기다린 쪽:** 타임아웃 ERROR 스택에만 나오는 `ItemService`(`ItemService.java:14` `@Transactional(readOnly = true)`, 스택 26건), `UnitService`(`UnitService.java:10` 같은 설정, 14건), `DistributionService.getDistribution`(`DistributionService.java:30` 같은 설정, 4건)은 leak 스택에 0건입니다. leak 스택 134건은 모두 `buildClassReport` 입니다. 풀 크기 `5`(`application.yml:14`)와 연결 대기 `3000`(`:15`)은 "5개가 묶이면 다른 요청은 3초 뒤 실패"라는 경로와 맞습니다<br>· **오류 응답:** 실패한 요청은 `GlobalExceptionHandler.java:53~56` `handleUnexpected` 가 500 으로 바꿉니다. 로그 `unhandled exception on ...` 49건 = access 500 49건과 맞습니다 |
| C 트래픽 — 리포트 페이지의 반복 호출로 report 동시 실행이 풀 크기를 넘음 | **관련 코드를 찾지 못했다** | · **반복 호출하는 쪽:** 이 가설의 고유 주장은 "리포트 페이지가 report API 를 반복 호출했다"입니다. 그 주체는 `lms.example.com` 프런트(referrer `https://lms.example.com/teacher/classes/N/report`, nginx-access.log:18801~)이고, 이 저장소에는 없습니다. `modern/web/src` 에서 `report` · `setInterval` · `refetchInterval` · `retry` 를 검색한 결과는 0건입니다.<br>· **API 쪽에 요청을 줄이는 장치가 있는지:** `modern/api/src/main` 에서 `Cacheable` · `EnableCaching` · `RateLimit` · `Semaphore` · `Bulkhead` · `@Async` 를 검색한 결과는 0건입니다. `ReportController.java:20~22` 는 요청마다 `reportService.buildClassReport(id)` 를 그대로 부릅니다. 같은 학급의 리포트를 반복 요청해도 매번 커넥션 1개를 빌린다는 점은 코드와 맞습니다.<br>· 설정 `maximum-pool-size: 5`(`application.yml:14`)는 "동시 5건이면 고갈" 부분과 맞습니다. 다만 반복 호출이 있었는지는 코드로 확인할 수 없으므로 판정은 "찾지 못했다"로 둡니다 |

#### 3.7.3 최종 정리

| 구분 | 가설 | 결론 | 사유 |
|---|---|---|---|
| **채택** | **A 코드** | **풀 고갈의 원인으로 채택** | 로그(leak 134/134가 `buildClassReport`, 반환이 `sign()` 다음 완료 로그와 0.1초 이내 134/134)와 코드(`ReportService.java:41` 트랜잭션 시작에 커넥션을 빌림 → `:71` `sign()` → `:73` 완료 로그 → 트랜잭션 끝에 반환, `application.yml:22` `open-in-view: false`)가 같은 순서를 가리킵니다 |
| 원인으로 채택하지 않음 (기각 아님) | C 트래픽 | **촉발 조건으로 남김** | 로그로 반박되지 않았습니다(동시 점유 최대 5, `/report` referrer 구간 안 171 / 밖 0). 평시 report 50건은 leak 0건이었고, 14:37 이후 report 동시 요청이 늘면서 A 의 구조가 풀 크기 5를 채웠다는 **발생 시점**을 설명합니다. 반복 호출 주체 코드가 저장소에 없어 확정하지 못했습니다 |
| **기각** | B DB · 쿼리 | 풀 고갈의 원인으로 기각 | slow log 임계값이 2.04초 이하인데 이상 구간 slow 는 1건(B-1)입니다. report 1건의 쿼리는 5회입니다. 학급 `findById` 1회, 배포 1회(`DistributionRepository.java:46~47` `@EntityGraph(attributePaths = {"assignment", "classRoom"})` 로 연관을 한 쿼리로 가져옴), 제출 3회이고, `Distribution` · `Submission` 연관은 `FetchType.LAZY` 입니다. 그래서 기록되지 않은 쿼리 합은 10.2초 미만이고, 최소 점유 15.54초보다 짧습니다. 남은 전제는 "slow log 설정이 하루 동안 같았다" 하나입니다 |
| 판단 불가로 보류 (기각 아님) | D 배포 · 설정 | 근거 없음 | 반박한 근거도 지지한 근거도 없습니다. 배포 이력과 기동 로그가 수집 범위에 없습니다(D-1 ~ D-3). 저장소에서 `ReportService.java` · `application.yml` 의 이력은 초기 커밋 `1260542` 1건뿐이고, 이것은 운영 배포 이력이 아닙니다 |

**확신 수준: 중간**

높음으로 두지 않은 이유:
1. **`sign()` 1회 소요 시간을 재지 않았습니다.** 별도 실행 금지 조건 때문입니다. 점유 15~21초 중 `sign()` 이 차지하는 몫은 "쿼리 종료 뒤에도 약 9.9초 더 점유(B-3)", "쿼리 합 10.2초 미만"이라는 간접 근거로만 추정합니다.
2. **설정 4개 키 중 `leak-detection-threshold` 만 로그로 확인하지 못했습니다(3.7.1).** A-3 의 점유 시간 계산은 이 값이 10초라는 전제 위에 있습니다.
3. **운영 코드가 저장소 코드와 같은지 확인하지 못했습니다.** 저장소 이력은 초기 커밋 1건이고 운영 배포 이력이 없습니다. 다만 로그의 클래스 · 메서드 이름, `ReportController.java:22` 줄번호, 풀 설정 3개는 저장소와 일치합니다.
4. **이상 구간의 점유 시간이 길어진 이유를 모릅니다.** 평시 report 는 10초 미만(leak 0건)이었는데 이상 구간에서는 15~21초로 늘었습니다. 동시 실행 때문에 CPU 경합이 생겼을 수 있지만, CPU 지표가 없어 확인하지 못했습니다.

높음으로 올리려면 다음 자료가 필요합니다. 운영 기동 로그의 Hikari 설정 출력(threshold 확인), 운영 배포 버전과 저장소 커밋 대조, 통제된 환경에서 `sign()` 1회 측정(이번 범위 밖), 장애 시간대 API 서버 CPU 지표.

#### 3.7.4 의심 동작 (판정과 별개, 고치지 않음)

| 관찰 | 근거 |
|---|---|
| 14:37:42 slow query 가 `distribution_id=5` 조회에 `Rows_examined 1,284,310`, `Rows_sent 4` 였습니다. 스키마에는 `idx_submission_distribution` 이 있습니다 | mariadb-slow.log:18, 20 / `db/mariadb/init/01-schema.sql:112` |
| `buildClassReport` 는 조회 전용인데 `@Transactional(readOnly = true)` 가 아니라 `@Transactional` 입니다(CLAUDE.md 2절 컨벤션과 다름) | `ReportService.java:41` |

## 4. 원인

> 3.7.3 에서 채택한 가설 A(직접 원인)와 촉발 조건 C 를 인과 순서로 적었습니다. B 를 기각하고 D 를 보류한 사유는 3.7.3 에 있습니다. 문장 번호는 5절의 번호와 같습니다.

**배경 원인 (고갈이 일어날 수 있던 조건)**

- ① 풀이 작고 대기 시간이 짧습니다. 최대 5개, 연결 대기 3초이며 운영 로그 값과 같습니다(3.7.1).
- ② report API 에는 동시 실행 제한도 캐시도 없습니다. 같은 학급을 반복 요청해도 매번 커넥션을 새로 빌립니다.
- ③ 트랜잭션 안에서 긴 루프를 돈다는 사실은 코드 주석(TODO)에 이미 적혀 있었지만 고쳐지지 않았습니다.

**촉발 조건 (왜 14:37 이후에만)**

- ④ 14:38:01 부터 referrer 가 `/teacher/classes/N/report` 인 report 요청이 시작됐습니다. 클라이언트 4개가 반복해서 요청했고, report 는 분당 0.16건에서 12.2건으로 늘었습니다.

**직접 원인 (커넥션이 왜 오래 묶였나)**

- ⑤ `buildClassReport` 는 트랜잭션을 시작할 때 커넥션을 빌립니다. 조회를 끝낸 뒤에도 같은 트랜잭션 안에서 `sign()`(SHA-256 200,000회)을 수행합니다.
- ⑥ `open-in-view: false` 이므로 커넥션은 `sign()` 과 완료 로그가 끝난 뒤, 트랜잭션이 끝날 때 반환됩니다. 그래서 1건당 점유 시간이 15.54~21.51초(중앙값 18.93초)였습니다. 이 값은 leak threshold 가 10초라는 전제에서 계산했습니다.
- ⑦ 10초 넘게 쥔 커넥션이 서로 다른 5개로 늘었고, 14:40:25 에 동시 점유가 풀 크기 5에 닿았습니다.

**결과 (증상의 전파)**

- ⑧ 다른 API(`ItemService` · `UnitService` · `DistributionService` 의 조회)가 3초를 기다린 뒤 풀 타임아웃으로 실패했고, 500 으로 응답했습니다(49건).
- ⑨ report 응답이 nginx 대기 시간을 넘어 504 가 났습니다(55건).
- ⑩ nginx 가 upstream 을 일시 제외하면서 `no live upstreams` 가 났고, report 가 아닌 경로까지 502 가 났습니다(82건).
- ⑪ 14:51 에 report 요청이 줄자 오류가 멎었습니다. 재시작이나 설정 재적재 기록은 없습니다. 요청이 왜 줄었는지는 9절에 적었습니다.

## 5. 근거 로그 줄

| # | 파일:줄 | 발췌 |
|---|---|---|
| ① | `application.yml:14~15` · app.log:1522 | `maximum-pool-size: 5` `connection-timeout: 3000` / `request timed out after 3000ms (total=5, ...)` |
| ② | `ReportController.java:22` · 3.7.2 C 행 | `return reportService.buildClassReport(id);` (`Cacheable` · `Semaphore` 등 검색 0건) |
| ③ | `ReportService.java:69~70` | `// DB 작업은 위에서 끝났는데 커넥션(트랜잭션)을 쥔 채로 돈다.` |
| ④ | nginx-access.log:18801 · 3.6 C-3 | `"GET /api/classes/3/report HTTP/1.1" 200 266` (referrer `/teacher/classes/3/report`, 구간 안 171 / 밖 0) |
| ④ | nginx-access.log:18881, 18904, 18912 | 교사단말B · 교사단말C · 교사단말D 이 차례로 report 요청을 시작 |
| ⑤ | app.log:1245 → 1250 → 1253 | `HikariDataSource.getConnection` ← `JpaTransactionManager.doBegin` ← `ReportService$$SpringCGLIB$$0.buildClassReport` |
| ⑤ | `ReportService.java:41, 71, 23` | `@Transactional` / `String signature = sign(payload.toString());` / `SIGNATURE_ROUNDS = 200_000` |
| ⑤ | app.log:1243 · 3.6 A-1 | `Connection leak detection triggered for ...Connection@6e8f14d3` (leak 134건 전부 `buildClassReport`) |
| ⑥ | `application.yml:22` · `ReportService.java:73` | `open-in-view: false` / `log.info("built class report ...")` |
| ⑥ | app.log:1258~1259 · 3.6 A-4 | `14:38:01.900 built class report for class 3` → `14:38:01.902 ... 6e8f14d3 ... was returned to the pool` (134/134 가 0.1초 이내) |
| ⑥ | mariadb-slow.log:15 · 3.6 B-3 | `# Time: 260916  5:37:52` — 쿼리 종료 뒤에도 약 9.9초 더 점유 |
| ⑥ | 3.6 A-3 (app.log leak WARN · 반환 INFO 짝) | `n=134 점유시간 min=15.54 median=18.93 max=21.51` |
| ⑦ | app.log:1280, 1331, 1351, 1370 · 3.6 C-1 | `...Connection@5b0e9e0c` (5번째 커넥션) / `최대 동시 점유=5 (첫 도달 14:40:25.80)` |
| ⑧ | app.log:1522~1523 | `(total=5, active=5, idle=0, waiting=4)` / `unhandled exception on /api/units/M6-2/items` |
| ⑧ | nginx-access.log:18918 · 3.6 C-2 | `"GET /api/units/M6-2/items HTTP/1.1" 500 162` (ERROR 49건 모두 `active=5, idle=0`) |
| ⑨ | nginx-error.log:6 · nginx-access.log:18933 | `upstream timed out (110: Connection timed out) ... /api/classes/3/report` / `504` |
| ⑩ | nginx-error.log:10, 12 · nginx-access.log:18970 | `upstream server temporarily disabled` / `no live upstreams ... /api/units/M6-2/items` / 첫 `502` |
| ⑪ | nginx-access.log:19381 · nginx-error.log:168 · 3.6 D-2 · D-3 | 마지막 504 (14:51:47) / 기동 · 재적재 흔적 0건 |

## 6. 수정안 (지금 적용)

> 제안만 적습니다. 코드는 바꾸지 않았습니다.

**6.1 `sign()` 을 트랜잭션 밖으로 — `modern/api/src/main/java/com/example/assignment/`**

같은 클래스 안의 다른 메서드로 옮기기만 하면 Spring 프록시를 거치지 않습니다(self-invocation). 그러면 트랜잭션 경계가 바뀌지 않으므로 빈을 나눠야 합니다.

| 파일 | 변경 |
|---|---|
| `ReportDataReader.java` (신규 `@Service`) | `ReportService.java:43~67` 의 조회 · 집계를 옮깁니다. `@Transactional(readOnly = true)` 를 붙이고, 엔티티가 아닌 `record ReportSnapshot(classId, className, distributionCount, submissionCount, averageScore, payload)` 를 반환합니다 |
| `ReportService.java:41` | `buildClassReport` 의 `@Transactional` 을 지웁니다. 순서는 `reader.read(classId)` → `sign(snapshot.payload())` → `ClassReport` 생성입니다. `:69~70` TODO 도 함께 지웁니다. 분리 뒤 `ReportService` 는 Repository 를 쓰지 않고, DB 접근은 모두 `readOnly = true` 인 `ReportDataReader` 에 있으므로 "Service 에 `@Transactional`" 컨벤션의 취지는 지켜집니다. 컨벤션 문구 자체는 7절에서 고칩니다 |
| `ReportServiceTest.java` · `ReportDataReaderTest.java` | CLAUDE.md 2절에 따라 둘 다 필요합니다. `buildClassReport` 에 `@Transactional` 이 없고 `ReportDataReader.read` 에 `readOnly = true` 가 있는지(리플렉션) 확인하는 케이스를 넣습니다 |

- 이렇게 바꾸면 커넥션 점유는 조회 5회에 걸리는 시간으로 줄어듭니다. 3.7.4 의 readOnly 컨벤션 위반도 함께 해소됩니다.
- **`maximum-pool-size` 를 늘리는 것은 수정안이 아닙니다.** 이상 구간의 평균 동시 점유를 어림하면 12.2건/분 × 19초 ÷ 60 ≈ 3.9개입니다. 풀을 늘려도 클라이언트가 조금만 늘면 다시 차고, 고갈 시점이 늦춰질 뿐입니다.
- 확인 명령: `./gradlew test --tests "com.example.assignment.*"`

## 7. 재발 방지 (구조적 장치)

| 장치 | 내용 | 막는 것 |
|---|---|---|
| 트랜잭션 경계 규칙 | CLAUDE.md 2절에 "`@Transactional` 메서드 안에서 DB 외 작업(서명 · 외부 호출 · 대기) 금지"를 추가합니다. ArchUnit 테스트로 `@Transactional` 클래스가 `MessageDigest` · HTTP 클라이언트에 의존하지 않는지 검사합니다 | 같은 구조가 다른 Service 에 생기는 것 |
| report 벌크헤드 | report 생성의 동시 실행을 풀 크기보다 작게 제한합니다(예: `Semaphore(2)`, 초과분은 503 + `Retry-After`). 한 엔드포인트가 풀 전체를 가져가지 못하게 합니다 | 무거운 API 하나로 전체 API 가 장애 나는 것 |
| report 짧은 캐시 | 학급별 결과를 짧은 TTL(예: 30초)로 캐시합니다. 같은 IP 가 같은 학급을 20~30초 간격으로 반복 요청했습니다(3.0) | 폴링이 그대로 DB · CPU 부하가 되는 것 |
| 프런트 폴링 규칙 | 리포트 페이지(lms.example.com, 이 저장소 밖)에 폴링 간격 하한과 5xx 뒤 지수 백오프를 둡니다. 5초 미만 재요청 29건 중 22건이 5xx 직후였습니다(C-5). 프런트 담당과 합의가 필요합니다 | 장애 중 재요청이 부하를 키우는 것 |
| nginx 경로 분리 | `/api/classes/*/report` 를 별도 `location` 으로 빼고 timeout 을 따로 둡니다. report 타임아웃이 `max_fails` 를 채워 모든 경로의 upstream 을 내리지 않게 합니다. nginx 설정은 수집 범위 밖이라 현재 값부터 확인해야 합니다 | 한 경로의 지연이 502 로 전체에 번지는 것 |
| 동시성 회귀 테스트 | report 를 풀 크기보다 많이 동시 호출하는 동안 다른 조회 API 가 성공하는지 확인하는 통합 테스트를 둡니다 | 수정 뒤 같은 구조가 되돌아오는 것 |

## 8. 모니터링 항목

> 경보 시각은 1~3절의 결과로만 추정했습니다. 사용자 영향 시작(14:40:52)보다 얼마나 앞섰는지를 함께 적었습니다.

| 지표 | 임계값 | 이번에 울렸을 시각 | 선행 | 근거 |
|---|---|---|---|---|
| Hikari 커넥션 점유 시간 max (`hikaricp_connections_usage_seconds`) 또는 leak WARN 건수 | ≥ 10초 1건 | **14:37:52** | **약 3분** | app.log:1243 |
| Hikari active / max | = 100% (5/5) | 14:40:25 | 약 27초 | C-1. 10초 넘게 쥔 커넥션만 센 값이라 실제 도달은 같거나 더 이릅니다 |
| Hikari pending (`hikaricp_connections_pending`) | > 0 (즉시) | 늦어도 14:40:49 | 3초 이상 | app.log:1522 의 스레드가 14:40:52.319 에 3000ms 대기 끝에 실패했으므로, 대기는 14:40:49 이전에 시작됐습니다 |
| Hikari 연결 타임아웃 (`hikaricp_connections_timeout_total`) | 1분에 ≥ 1 | 14:40:52 | 0 | app.log:1522 |
| report 요청률 | ≥ 5건/분 (평시 0.16건/분의 30배) | 14:40 분 구간 (10건) | 약 1분 | 2.2 표. ≥ 3건/분으로 잡으면 14:38 에 울리지만, 평시 30분에 5건이 뭉쳐 오는 경우와 구분하려고 5건으로 잡았습니다 |
| nginx 5xx 비율 | 1분 ≥ 5% | 14:41 분 구간 (9/42 ≈ 21%) | — (영향 뒤) | 1.2 표. 14:40 분 구간은 1/42 ≈ 2.4% 라서 울리지 않습니다 |
| nginx upstream 제외 · `no live upstreams` | ≥ 1건 | 14:42:01 / 14:42:04 | — (영향 뒤) | nginx-error.log:10, 12 |
| 엔드포인트별 응답 시간 p95 | report > 5초 | 판단 불가 | — | access 로그에 `$request_time` 이 없습니다(9절) |
| (보조) slow query | > 5초 | 14:37:52 | 약 3분 | mariadb-slow.log:15. 원인으로는 기각했고(B) 이번에는 시각만 겹쳤습니다 |

- 첫 두 지표를 묶어 경보를 걸면 사용자 영향보다 약 3분 먼저 알 수 있습니다.

## 9. 확인하지 못한 것

**로그가 없어 판단하지 못한 것**

| 항목 | 막힌 이유 |
|---|---|
| `sign()` 1회 소요 시간, 평시 10초 미만이던 점유가 15~21초로 늘어난 이유(CPU 경합?) | 측정하지 않았습니다(별도 실행 제외). CPU · 스레드 덤프 기록이 없습니다 |
| 운영 `leak-detection-threshold` 가 10초인지 | 로그에 값이 없습니다(3.7.1). 5절 ⑥의 점유 시간은 이 전제 위에 있습니다 |
| 운영 코드 · 설정이 저장소와 같은지, 9/16 무렵 배포가 있었는지(가설 D) | 배포 이력과 기동 로그가 없습니다(D-1 ~ D-3) |
| report 반복 호출의 주체(페이지 자동 새로고침인지 사람이 누른 것인지), 다른 날과의 비교 | 프런트 코드 · 배포 이력과 다른 날 access 로그가 수집 범위 밖입니다(C ②④) |
| 14:51 에 report 요청이 줄어든 이유(페이지를 닫았는지, 누가 조치했는지) | 조치 기록이 없습니다. 복구가 자연 해소인지 개입인지 모릅니다 |
| nginx-error.log:11 의 `connect() failed` 대상은 **API-2** 입니다. timed out 대상은 API-1 이라 서로 다릅니다. 두 번째 upstream 이 장애 전부터 내려가 있었는지, backup 서버인지 | nginx upstream 설정과 API-2 서버 로그가 없습니다. 집계는 하지 않았고 이 한 줄만 봤습니다 |
| nginx `proxy_read_timeout` · `max_fails` · `fail_timeout` 값, 504 요청이 시작된 시각 | nginx 설정이 수집 범위 밖입니다. access 로그 시각은 요청이 끝난 시각입니다 |
| 14:37:42 slow query 의 `Rows_examined 1,284,310`(인덱스가 있는데도) | `EXPLAIN` 과 DB 로그(general · InnoDB status · 백업)가 없습니다(3.7.4) |
| slow log 설정이 하루 동안 같았는지(B 기각의 전제) | slow log 에 설정 기록이 없습니다(B-2) |

**다음을 위해 남겨야 할 로그**

- nginx `log_format` 에 `$request_time $upstream_response_time $upstream_addr $upstream_status` 를 추가합니다. 요청 시작 시각, 지연, 어느 upstream 이 응답했는지를 남깁니다.
- API 기동 시 Hikari 설정을 출력합니다(`logging.level.com.zaxxer.hikari.HikariConfig: DEBUG`). Actuator/Micrometer 로 `hikaricp_*` 지표를 수집합니다.
- Tomcat access log(`server.tomcat.accesslog.enabled`, 패턴에 `%D`)로 API 쪽 요청별 처리 시간을 남깁니다.
- leak WARN 이 처음 찍힐 때 스레드 덤프와 CPU 사용률을 함께 수집합니다.
- 운영 배포 이력(API · 프런트), nginx 설정 변경 이력, 장애 중 조치 기록을 남깁니다. DB `long_query_time` 등 설정 스냅샷도 함께 보존합니다.

---

마스킹 적용: IP 23건 · 이메일 0건 · 학생 식별자 1건 · 토큰 0건 · 비밀번호/접속 문자열 2건
