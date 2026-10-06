# RCA — b-deploy-5xx (2026-09-17)

> **작성 범위**: 1. 수집 범위 · 2. 타임라인 · 3. 가설 표까지입니다. 가설의 로그 반증 실행, 코드 · 설정 대조, 판정(4절)과 요약 · 원인 · 수정안 등 5절 항목은 **이번에 작성하지 않았습니다**.
> 대상: `incident-logs/b-deploy-5xx/` 원본 3개 파일 (수정하지 않음).

## 1. 수집 범위

> 방법: `wc`, `head`, `tail`, `grep -c`, `grep -n`, `sed -n`, `awk` 집계만 사용했습니다. 로그 파일을 통째로 읽지 않았습니다(39줄짜리 `deploy-history.md` 는 전체를 봤습니다).
> 이 절은 범위만 정합니다. 원인은 다루지 않습니다.

### 1.1 파일별 기록 기간 · 줄 수 · 시각 형식 · 타임존

| 파일 | 줄 수 | 기록 기간 (KST) | 시각 형식 (예) | 타임존 | KST 변환 |
|---|---:|---|---|---|---|
| `app.log` (Spring Boot) | 2,938 (타임스탬프 엔트리 1,275 + 스택트레이스 연속행 1,663) | 2026-09-17 09:00:04 ~ 14:39:57 | `2026-09-17T13:46:38.113+09:00` (ISO-8601, ms, 오프셋 포함) | +09:00 (엔트리 1,275건 전부) | 그대로 사용 |
| `nginx-access.log` (combined) | 20,365 | 2026-09-17 09:00:00 ~ 14:39:58 | `[17/Sep/2026:13:46:38 +0900]` | +0900 (20,365줄 전부) | 그대로 사용 |
| `deploy-history.md` | 39 | 배포 4건: 2026-09-01 ~ 2026-09-17 (이벤트 기록) | `2026-09-17 13:40:05` (오프셋 없음) | **KST** (파일 3행 명시 + 아래 근거) | 그대로 사용 |

**타임존 판정 근거**

- `deploy-history.md` 는 오프셋이 없고 3행에 "시각은 모두 KST" 라고만 적혀 있습니다. v1.4.2 배포 구간 13:40:05 ~ 13:42:31 (deploy-history.md:7) 안에 `app.log` 의 종료 · 기동 로그가 들어 있습니다: graceful shutdown 시작 13:41:48.214 (app.log:1240), `Starting ItemBankApplication v1.4.2` 13:41:55.902 (app.log:1245), `Started ItemBankApplication` 13:42:00.304 (app.log:1264). 버전 문자열까지 같으므로 KST 로 판정했습니다. UTC 였다면 22:40 이 되어 두 로그의 기록 범위 밖입니다.
- 사실로만 기록: 앱 기동 완료(13:42:00.304)와 배포 이력의 "배포 완료"(13:42:31) 사이에 약 31초 차이가 있습니다.

**KST 변환 규칙 (요약)**

| 원본 | 규칙 |
|---|---|
| `app.log`, `nginx-access.log` | 오프셋이 +09:00 / +0900 이므로 변환 없음 |
| `deploy-history.md` | 오프셋 없음. KST 로 확인했으므로 변환 없음 |

**집계 시 주의**

- `app.log` 의 비타임스탬프 1,663행은 ERROR 46건의 스택트레이스(엔트리당 약 36행)입니다. 줄 단위로 세면 부풀려집니다. 예: `grep -c 'does not exist'` = 92(줄, 예외 본문 + `Caused by`), 엔트리 기준 = 46.
- `nginx-access.log` 의 `$time_local` 은 요청이 **끝난** 시각입니다. 다만 이번 500 은 모두 응답 크기 176 바이트의 즉시 응답이고, 같은 요청의 app ERROR 와 같은 초에 찍혀 있습니다.
- `deploy-history.md` 의 기간은 수집 범위가 아니라 배포 이벤트 4건의 시각입니다. 9/17 이전 배포(v1.4.1 이하) 시점의 로그는 수집 범위에 없습니다.
- **두 로그 모두 14:39:57 ~ 14:39:58 에서 끝납니다.** 마지막 500 은 14:34:45 (nginx-access.log:20084)로, 끝까지 5분 남짓입니다. 이 5분에 500 이 없는 것은 재배포 요청이 그 사이 다시 오지 않았기 때문인지(14:35 ~ 14:39 redistribute 요청 1건, 대상 distribution 2 → 409) 복구됐기 때문인지 로그로 구분할 수 없습니다. **복귀는 관측되지 않았습니다.**
- 평시에도 나오는 응답은 이상 범주에서 뺍니다: 404 (시간당 128~199건, `not found` · `no handler` INFO), 409 / WARN `state conflict: 마감된 과제는 재배포할 수 없습니다` (distribution 1 · 2 · 3, 시간당 3~9건), 400 (시간당 2~8건).

### 1.2 분 단위 이상 건수와 평소 수준

**범주 정의**

| 파일 | 오류 | 경고 | 5xx |
|---|---|---|---|
| `app.log` | 레벨 `ERROR` (46건 전부 `GlobalExceptionHandler : unhandled exception on <경로>`) | 레벨 `WARN` (35건 전부 `state conflict`, 평시 잡음) | 해당 없음 |
| `nginx-access.log` | 해당 없음 | 해당 없음 | status `5xx` (500 / 502 로 나눔. 그 밖의 5xx 는 0건) |

**파일 간 정합성 확인** (시각 정렬과 건수 대조용. 원인 해석은 하지 않음)

- access 500 46건 = app ERROR 46건. 경로 · 초 단위 시각이 1:1 로 맞습니다(app ERROR 메시지의 경로 = access 요청 경로).
- access 502 8건(13:41:51 ~ 13:41:59)에 대응하는 app 엔트리는 없습니다. 같은 시각 app.log 는 13:41:50.224 (app.log:1244, 풀 종료 완료)부터 13:41:55.902 (app.log:1245, 기동 시작)까지 비어 있습니다.
- access 409 35건 = app WARN 35건.

**시간대별 전체 분포와 평소 수준**

| 시 (KST) | access 전체 | 5xx | 500 | 502 | 404 | 409 | app 엔트리 | app ERROR | app WARN |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 09 | 3,625 | 3 | 3 | 0 | 199 | 4 | 233 | 3 | 4 |
| 10 | 3,621 | 1 | 1 | 0 | 173 | 8 | 208 | 1 | 8 |
| 11 | 3,762 | 3 | 3 | 0 | 182 | 8 | 218 | 3 | 8 |
| 12 | 3,215 | 0 | 0 | 0 | 145 | 9 | 192 | 0 | 9 |
| **13** | 3,678 | **22** | 14 | 8 | 142 | 3 | 225 | **14** | 3 |
| **14** (~14:39) | 2,464 | **25** | 25 | 0 | 128 | 3 | 199 | **25** | 3 |

- 평소 요청량: 09:00 ~ 13:39 분당 40 ~ 82건 (중앙값 59, 280분).
- 평소 5xx: 09:00 ~ 13:39 에 7건뿐이며, 전부 `distribution 41` 대상 500 입니다 (09:52 3건, 10:31 1건, 11:17 ~ 11:18 3건). 같은 예외 메시지를 쓰므로 분석 창 밖이지만 **참고 보존**합니다(1.3).

**5분 단위 집계 (13:10 ~ 14:39, 분석 창)**

| 구간 시작 | access 전체 | 5xx | 500 | 502 | redistribute 요청 | 그중 500 | 404 | 409 | app 엔트리 | app ERROR |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 13:10 | 312 | 0 | 0 | 0 | 6 | 0 | 10 | 1 | 18 | 0 |
| 13:15 | 312 | 0 | 0 | 0 | 2 | 0 | 9 | 1 | 11 | 0 |
| 13:20 | 312 | 0 | 0 | 0 | 2 | 0 | 13 | 0 | 17 | 0 |
| 13:25 | 336 | 0 | 0 | 0 | 1 | 0 | 8 | 0 | 10 | 0 |
| 13:30 | 313 | 0 | 0 | 0 | 0 | 0 | 8 | 0 | 8 | 0 |
| 13:35 | 350 | 0 | 0 | 0 | 0 | 0 | 21 | 0 | 21 | 0 |
| **13:40** | 331 | **8** | 0 | **8** | 2 | 0 | 14 | 0 | 41 | 0 |
| **13:45** | 294 | 3 | 3 | 0 | 3 | 3 | 7 | 0 | 13 | 3 |
| 13:50 | 279 | 6 | 6 | 0 | 8 | 6 | 15 | 0 | 30 | 6 |
| 13:55 | 270 | 5 | 5 | 0 | 7 | 5 | 9 | 1 | 21 | 5 |
| 14:00 | 315 | 6 | 6 | 0 | 6 | 6 | 23 | 0 | 38 | 6 |
| 14:05 | 298 | 1 | 1 | 0 | 1 | 1 | 15 | 0 | 18 | 1 |
| 14:10 | 313 | 3 | 3 | 0 | 3 | 3 | 14 | 0 | 26 | 3 |
| 14:15 | 352 | 3 | 3 | 0 | 3 | 3 | 18 | 0 | 24 | 3 |
| 14:20 | 316 | 3 | 3 | 0 | 6 | 3 | 19 | 1 | 30 | 3 |
| 14:25 | 289 | 3 | 3 | 0 | 3 | 3 | 12 | 0 | 19 | 3 |
| 14:30 | 310 | 6 | 6 | 0 | 7 | 6 | 15 | 1 | 30 | 6 |
| 14:35 (~14:39:58) | 271 | 0 | 0 | 0 | 1 | 0 | 12 | 1 | 14 | 0 |

13:40 구간의 app 엔트리 41건 중 다수는 종료 · 기동 INFO 입니다(app.log:1240 ~ 1264).

**분 단위 집계 (13:38 ~ 13:48, 배포 전후)**

| 분 | access 전체 | 500 | 502 | app 엔트리 | app ERROR |
|---|---:|---:|---:|---:|---:|
| 13:38 | 76 | 0 | 0 | 6 | 0 |
| 13:39 | 67 | 0 | 0 | 2 | 0 |
| 13:40 | 73 | 0 | 0 | 4 | 0 |
| **13:41** | 72 | 0 | **8** | 28 | 0 |
| 13:42 | 69 | 0 | 0 | 5 | 0 |
| 13:43 | 59 | 0 | 0 | 3 | 0 |
| 13:44 | 58 | 0 | 0 | 1 | 0 |
| 13:45 | 59 | 0 | 0 | 0 | 0 |
| **13:46** | 56 | **2** | 0 | 6 | **2** |
| 13:47 | 58 | 1 | 0 | 4 | 1 |
| 13:48 | 68 | 0 | 0 | 3 | 0 |

**이상 구간**

| 구분 | 시각 (KST) | 근거 |
|---|---|---|
| 배포 시작 | 13:40:05 | deploy-history.md:7 |
| 첫 이상 (502) | **13:41:51** ~ 13:41:59 (8건) | nginx-access.log:16865 ~ 16872. 13:42:01 부터 200 (nginx-access.log:16873) |
| 첫 500 (배포 후) | **13:46:38** | app.log:1275, nginx-access.log:17155 |
| 마지막 500 | 14:34:45 | app.log:2887, nginx-access.log:20084 |
| 복귀 | **관측 안 됨** | 로그가 14:39:58 에서 끝남 (1.1 주의 참고) |

### 1.3 분석 창

| 구간 | 범위 (KST) | 여유 | 근거 |
|---|---|---|---|
| **분석 창 (기본)** | **13:10 ~ 14:39:58** | 앞 **30분** (배포 시작 13:40:05 기준) / 뒤: 로그 끝 | 뒤쪽 여유를 둘 로그가 없습니다. 복귀 확인 불가 |
| 참고 보존 | 09:52 ~ 11:18 `distribution 41` 500 7건 | 분석 창 밖 | 분석 창의 ERROR 와 같은 예외 · 같은 엔티티 ID 입니다. 7건뿐이라 전부 2.0 에 보존합니다 |
| 평시 비교 구간 | 12:00 ~ 13:00 | 별도 | 5xx 0건, redistribute 정상 응답 크기(170 ~ 200 바이트) 비교 기준 |

- 앞쪽을 30분으로 두는 이유: 배포 이력상 시작은 13:40:05 지만 앱 로그에 배포 관련 흔적은 13:41:48 부터입니다. 그 전 단계(이미지 준비 등)는 로그에 없을 수 있습니다. 또 이번 500 은 특정 요청(재배포)이 와야만 드러나므로, 배포 직전 같은 요청이 정상이었던 기록(13:41:20)까지 창 안에 넣습니다.

## 2. 타임라인

> 모든 시각은 KST 입니다. 출처 표기는 `파일:줄번호` 입니다. 인용 전에 `sed -n` 으로 다시 확인했습니다.
> 같은 메시지가 반복되면 첫 발생 줄 하나만 쓰고 이후 건수를 묶었습니다.
> **(선후 불확실)**: 파일 간 시각 차이가 1초 이내라 순서를 단정할 수 없는 곳입니다. `nginx-access.log` 는 초 단위, `app.log` 는 ms 단위입니다.
> 원인 해석은 쓰지 않았습니다.
> 클라이언트 별칭: 재배포를 요청한 교사 화면(referrer `/teacher/...`) 단말 두 대를 로그에 처음 나온 순서로 `교사단말A` · `교사단말B` 로 부릅니다. 배포 대상 서버는 `API-1` 입니다(deploy-history.md:3).

### 2.0 참고 보존 (분석 창 밖, 09:52 ~ 11:18)

| 시각 | 출처 | 사건 요약 | 원문 발췌 |
|---|---|---|---|
| 09:52:17.335 | app.log:194 / nginx-access.log:3150 (선후 불확실) | 교사단말A 의 `POST /api/distributions/41/redistribute` 가 500. 첫 ERROR 엔트리 | `ERROR ... unhandled exception on /api/distributions/41/redistribute` / `JpaObjectRetrievalFailureException: Entity com.example.assignment.Assignment with identifier value 9 does not exist` |
| 09:52:17.335 | app.log:194 이하 스택 | `com.example` 프레임: `DistributionService.loadOrThrow(DistributionService.java:65)` ← `DistributionService.redistribute(DistributionService.java:51)` ← `DistributionController.redistribute(DistributionController.java:38)` | (스택 발췌) |
| 09:52:30, 09:52:41 | app.log:232, 270 / nginx-access.log:3162, 3174 | 같은 요청 2회 더 500 (13초 · 11초 간격) | (동일) |
| 10:31:05.909 | app.log:451 / nginx-access.log:5563 (선후 불확실) | 교사단말A 의 `GET /api/distributions/41` 가 500. 프레임 `loadOrThrow(:65)` ← `getDistribution(:32)` ← `DistributionController.getDistribution(:26)` | `unhandled exception on /api/distributions/41` / `... Assignment ... value 9 does not exist` |
| 11:17:40 ~ 11:18:02 | app.log:655, 695, 733 / nginx-access.log:8442, 8466, 8468 | `distribution 41` 재배포 500 3건 | (동일, assignment 9) |
| 09:00 ~ 13:39 | nginx-access.log:1~16727 · app.log:1~1230 (집계) | `distribution 41` 대상 요청은 위 7건뿐이고 모두 500. `redistributed distribution 41` INFO 는 0건 | (집계 결과) |

### 2.1 앞선 구간 (13:10 ~ 13:41:47, 배포 · 재기동 전)

| 시각 | 출처 | 사건 요약 | 원문 발췌 |
|---|---|---|---|
| 13:10 ~ 13:39 | nginx-access.log:14793~16727 (분 단위 집계) | 분당 47 ~ 82건, 5xx 0건. redistribute 11건 = 200 이 9건(응답 170 ~ 199 바이트), 409 2건 | (집계 결과) |
| 13:24:45.671 | app.log:1191 | 이날 마지막 `built class report for class 1` (이날 17건, 모두 `3 distributions`) | `built class report for class 1: 3 distributions, 10 submissions` |
| 13:40:05 | deploy-history.md:7 | v1.4.2 배포 시작. 변경 요약: 재배포 응답에 학급 배포 이력(history) 포함 — `DistributionController.redistribute` | `\| v1.4.2 \| 2026-09-17 13:40:05 \| 2026-09-17 13:42:31 \| [EMAIL] \| 재배포 응답에 학급 배포 이력(history) 포함 ...` |
| 13:41:20.994 | app.log:1238 / nginx-access.log:16829 (선후 불확실) | 교사단말A 의 `distribution 43` 재배포 성공(200, 183 바이트) | `redistributed distribution 43 (assignment 6, class 1) reason=학생A 요청 — 제출 화면 오류` |

### 2.2 배포 · 재기동 (13:41:48 ~ 13:42:31)

| 시각 | 출처 | 사건 요약 | 원문 발췌 |
|---|---|---|---|
| 13:41:48.214 | app.log:1240 | graceful shutdown 시작 | `Commencing graceful shutdown. Waiting for active requests to complete` |
| 13:41:50.144 ~ .224 | app.log:1241 ~ 1244 | shutdown 완료, JPA · 커넥션 풀 종료 | `Graceful shutdown complete` … `itembank-pool - Shutdown completed.` |
| 13:41:50 | nginx-access.log:16862 ~ 16864 | 마지막 200 3건 (재기동 전) | (200) |
| 13:41:51 | nginx-access.log:16865 | 첫 502. 이후 13:41:59 까지 8건(16865 ~ 16872). 학생 화면 조회 6건, 교사 화면 조회 1건, 헬스체크(`kube-probe/1.29`, `GET /`) 1건 | `"GET /api/units/M5-2/items?student=학생B HTTP/1.1" 502 157` |
| 13:41:52 | nginx-access.log:16866 | 헬스체크 502 (이날 `kube-probe` 867건 중 유일한 비-200) | `"GET / HTTP/1.1" 502 157 "-" "kube-probe/1.29"` |
| 13:41:55.902 | app.log:1245 | v1.4.2 기동 시작 | `Starting ItemBankApplication v1.4.2 using Java 21.0.4 with PID 1` |
| 13:41:58.533 | app.log:1259 | 커넥션 풀 첫 연결 | `itembank-pool - Added connection ...` |
| 13:42:00.304 | app.log:1264 | 기동 완료 | `Started ItemBankApplication in 4.921 seconds (process running for 5.488)` |
| 13:42:01 | nginx-access.log:16873 | 재기동 후 첫 200 | `"GET /api/units HTTP/1.1" 200 604` |
| 13:42:10.447 | app.log:1266 / nginx-access.log:16884 (선후 불확실) | 재기동 후 첫 재배포: 교사단말B, `distribution 7` (class 3) 200. 응답 **802 바이트** | `redistributed distribution 7 (assignment 5, class 3) reason=보강 수업 후 재배포` / `"POST /api/distributions/7/redistribute HTTP/1.1" 200 802` |
| 13:42:31 | deploy-history.md:7 | 배포 이력상 배포 완료 | `2026-09-17 13:42:31` |

### 2.3 이상 구간 (13:46 ~ 14:34)

| 시각 | 출처 | 사건 요약 | 원문 발췌 |
|---|---|---|---|
| 13:46:38.107 | app.log:1274 | 교사단말A 의 `distribution 42` 재배포 INFO | `redistributed distribution 42 (assignment 4, class 1) reason=보강 수업 후 재배포` |
| 13:46:38.113 | app.log:1275 / nginx-access.log:17155 (선후 불확실) | 같은 요청 6ms 뒤 ERROR, 500 (176 바이트). **배포 후 첫 500** | `unhandled exception on /api/distributions/42/redistribute` / `Entity com.example.assignment.Assignment with identifier value 9 does not exist` |
| 13:46:38.113 | app.log:1275 이하 스택 | `com.example` 프레임: `DistributionService.listByClass(DistributionService.java:38)` ← `DistributionController.redistribute(DistributionController.java:40)` | (스택 발췌) |
| 13:46:58, 13:47:08 | app.log:1313, 1351 / nginx-access.log:17170, 17181 | `distribution 42` 재시도 2회, 모두 500 | (동일) |
| 13:50:00.415 | app.log:1393 / nginx-access.log:17353 (선후 불확실) | 교사단말A, `distribution 8` (class 2) 재배포 200, 908 바이트 | `redistributed distribution 8 (assignment 6, class 2) reason=학생C 결석으로 재배포` |
| 13:50:12.717 → .723 | app.log:1395 → 1396 / nginx-access.log:17365 (선후 불확실) | `distribution 43` (class 1) 재배포 INFO 후 6ms 뒤 ERROR, 500. 13:41:20 에 200 이었던 같은 distribution | `redistributed distribution 43 (assignment 6, class 1) ...` → `unhandled exception on /api/distributions/43/redistribute` |
| 13:56:43.437 | app.log:1754 / nginx-access.log:17734 (선후 불확실) | 교사단말A, `distribution 44` (class 2) 재배포 200, 640 바이트 | `redistributed distribution 44 (assignment 5, class 2) reason=학생C 결석으로 재배포` |
| 13:46 ~ 14:34 | app.log:1275~2887 (ERROR 엔트리) / nginx-access.log:17155~20084 (500) 집계 | 500 39건 = `distribution 42` 19건 + `distribution 43` 20건. 요청자는 모두 교사단말A. 대부분 1분 안에 2 ~ 4회씩 묶여 반복(13:46 · 13:50 · 13:52 · 13:55 · 13:58 · 14:02 ~ 14:03 · 14:06 · 14:11 · 14:15 ~ 14:16 · 14:22 · 14:27 ~ 14:28 · 14:31 ~ 14:32 · 14:34) | 분 단위 500 건수: 13:46 2 · 13:47 1 · 13:50 4 · 13:52 1 · 13:53 1 · 13:55 3 · 13:58 2 · 14:02 2 · 14:03 4 · 14:06 1 · 14:11 3 · 14:15 1 · 14:16 2 · 14:22 3 · 14:27 2 · 14:28 1 · 14:31 2 · 14:32 1 · 14:34 3 (합 39) |
| 13:46 ~ 14:34 | app.log:1274~2887 (집계) | 39건 모두 직전(같은 스레드, 수 ms 이내)에 `redistributed distribution 42/43 ... class 1` INFO 가 있음. INFO 수: 42 = 24건(배포 전 5 + 후 19), 43 = 33건(전 13 + 후 20) | (집계 결과) |
| 13:42 ~ 14:39 | nginx-access.log:16873~20365 (집계) | 배포 후 redistribute 200 6건: distribution 5 · 7 (class 3, 교사단말B) 4건, 8 · 44 (class 2, 교사단말A) 2건. 응답 640 ~ 910 바이트 (배포 전 200 66건은 170 ~ 200 바이트). 배포 후 class 1 distribution 재배포 200 은 0건 | (집계 결과) |
| 13:42 ~ 14:39 | app.log:1265~2938 (집계) | 재기동 후 `built class report` 10건, 그중 class 1 은 0건 | (집계 결과) |
| 14:34:45.439 | app.log:2887 / nginx-access.log:20084 (선후 불확실) | 마지막 500 (`distribution 43`) | `unhandled exception on /api/distributions/43/redistribute` |

### 2.4 로그 끝 (14:35 ~ 14:39:58)

| 시각 | 출처 | 사건 요약 | 원문 발췌 |
|---|---|---|---|
| 14:35 ~ 14:39 | nginx-access.log:20095~20365 (집계) | 271건, 5xx 0건. redistribute 1건 (`distribution 2`, 409) | (집계 결과) |
| 14:39:57 / 14:39:58 | app.log:2938 / nginx-access.log:20365 | 두 로그의 마지막 줄. 롤백 · 재기동 흔적 없음 | `no handler for /favicon.ico` / `"GET /api/units HTTP/1.1" 200 613` |

## 3. 가설과 검증 (가설 표까지)

> 가설과 반증 조건은 판정 없이 세웠습니다. **로그 반증 실행 · 코드 대조 · 판정(4절)은 이번 범위가 아니어서 하지 않았습니다.** 표의 "확인 방법" 명령은 모두 **미실행**입니다.
> 지지 근거는 `2절 표의 행(시각) — 원본 파일:줄번호` 형식입니다.
> DB 확인 명령은 읽기 계정 `readonly` / `[SECRET]` 로만 실행합니다.

### 3.0 증상으로 분류한 것

| 관찰 | 분류 | 이유 |
|---|---|---|
| `Entity com.example.assignment.Assignment with identifier value 9 does not exist` (ERROR 46건 전부) | **증상** | 가장 많이 나온 오류입니다. 가설은 "왜 13:46:38 부터, 왜 class 1 distribution 재배포에서만 이 예외가 났는가"를 설명해야 합니다 |
| 500 이 교사단말A 한 대에서만 나옴, 2 ~ 4회씩 반복 | 증상(건수 증폭) | 같은 요청의 재시도로 건수가 늘어난 것입니다. 시작 시점은 설명하지 않습니다 |
| 502 8건 (13:41:51 ~ 59) | 별도 증상 | 500 과 시각 · 경로 · app 로그 대응이 다릅니다. 가설 C 로 따로 다룹니다 |

**가설을 세우기 전에 로그에서 확인한 사실 (해석 없음)**

| 사실 | 출처 |
|---|---|
| assignment 9 없음 예외는 배포 **전**(09:52 ~ 11:18)에도 7건 있었고, 그때는 `distribution 41` 요청, 프레임 `loadOrThrow(:65)` 였습니다 | 2.0 — app.log:194, 451 |
| 배포 **후** 39건은 `distribution 42/43` 요청, 프레임 `listByClass(:38)` ← `DistributionController.redistribute(:40)` 이고, 직전에 재배포 성공 INFO 가 있습니다 | 2.3 — app.log:1274 ~ 1275 |
| v1.4.2 의 변경 지점은 "`DistributionController.redistribute` 에서 재배포 후 `DistributionService.listByClass(classId)` 호출 추가" 입니다 | deploy-history.md:16 |
| v1.4.2 테스트는 스테이징 DB(시드 데이터)에서 재배포 3건 수동 확인입니다 | deploy-history.md:17 |
| 배포 방식은 "단일 호스트 재기동(무중단 아님). 예상 중단 10초 안팎", 대기 호스트 API-2 는 "평소 정지" 입니다 | deploy-history.md:18, 3 |
| `distribution 41` 이 어느 class 인지는 로그에 없습니다(`redistributed distribution 41` INFO 0건). class 1 리포트는 하루 내내 `3 distributions` 이고, 로그로 class 1 임이 확인된 distribution 은 42 · 43 두 개뿐입니다 | app.log:1191 외 (`grep 'built class report for class 1'`), `grep 'redistributed distribution'` 집계 |

### 3.1 가설 A — 배포된 코드 변경 (v1.4.2)

| 항목 | 내용 |
|---|---|
| 가설 | v1.4.2 가 재배포 직후 같은 학급의 배포 목록(`listByClass`)을 조회하도록 바뀌었고, 이 목록 조회가 학급 안의 모든 distribution 의 assignment 를 함께 읽는다. class 1 목록에 assignment 가 없는 distribution 이 섞여 있어, class 1 의 어떤 distribution 을 재배포해도 목록 조회 단계에서 예외가 난다. 13:46:38 은 재기동 후 class 1 재배포가 처음 들어온 시각이다. |
| 지지 근거 | · 2.3 13:46:38.107 → .113 행 — app.log:1274 ~ 1275 (재배포 INFO 후 6ms 뒤 `listByClass(:38)` ← `redistribute(:40)` 에서 예외)<br>· 2.1 13:41:20 행 — app.log:1238 vs 2.3 13:50:12 행 — app.log:1395 ~ 1396 (같은 `distribution 43` 이 재기동 전 200, 후 500)<br>· 2.3 13:50:00 · 13:56:43 행 — app.log:1393, 1754 (class 2 재배포는 배포 후에도 200) / 2.2 13:42:10 행 — app.log:1266 (class 3 200)<br>· 2.3 집계 행 — 배포 후 200 응답 크기 170 ~ 200 → 640 ~ 910 바이트 (응답 형식 변경, deploy-history.md:15)<br>· deploy-history.md:16 (변경 지점) |
| 반증 조건 | · 배포 후에도 class 1 distribution 재배포가 200 으로 끝난 요청이 있으면 틀리다.<br>· v1.4.2 의 `listByClass` 가 assignment 를 읽지 않거나(스택의 `:38` 이 assignment 조회가 아님), v1.4.1 에서도 재배포 경로가 `listByClass` 를 불렀다면 "배포가 촉발" 부분이 틀리다.<br>· class 2 · 3 재배포가 배포 후 500 을 낸 적이 있으면 "class 1 에만" 부분이 틀리다. |
| 확인 방법 (미실행) | · 로그: `awk '$7~/redistribute/ && substr($4,14,8)>"13:42" {print $7,$9}' incident-logs/b-deploy-5xx/nginx-access.log \| sort \| uniq -c`<br>· 로그: `grep -n 'redistributed distribution' incident-logs/b-deploy-5xx/app.log` 로 distribution ↔ class 대응 집계<br>· 코드: `DistributionController.java:38~40`, `DistributionService.java:38, 51, 65` (스택에 찍힌 줄)<br>· 이력: `git log --oneline -- modern/api/src/main/java/com/example/assignment/DistributionController.java` 와 v1.4.1 ↔ v1.4.2 diff (저장소 이력은 운영 배포 이력이 아님을 감안) |

### 3.2 가설 B — 데이터 정합성 (assignment 9 없는 distribution)

| 항목 | 내용 |
|---|---|
| 가설 | DB 에 이미 삭제된(또는 존재하지 않는) assignment 9 를 가리키는 distribution(최소 `distribution 41`)이 남아 있었다. 이 상태는 배포 전부터 있었고(09:52 첫 관측), 그 distribution 을 직접 건드릴 때만 실패했다. 배포 이후 이 행이 class 단위 조회에 포함되면서 같은 class 의 다른 distribution 요청까지 실패하게 됐다. 이 가설에서 데이터 문제는 **전제 조건**이고 촉발은 아니다. |
| 지지 근거 | · 2.0 09:52:17 행 — app.log:194 (배포 전, `distribution 41` → assignment 9 없음)<br>· 2.0 10:31:05 행 — app.log:451 (단건 조회도 같은 예외)<br>· 2.3 13:46:38.113 행 — app.log:1275 (배포 후 예외의 identifier 도 9)<br>· 3.0 사실 표: 46건 모두 identifier 9 |
| 반증 조건 | · DB 에 assignment 9 가 존재하면(또는 assignment 9 를 가리키는 distribution 이 없으면) 틀리다.<br>· `distribution 41` (또는 assignment 9 를 가리키는 distribution)이 class 1 이 **아니면**, class 1 목록 조회 실패를 이 데이터로 설명할 수 없다. **`distribution 41` 의 class 는 로그로 확인되지 않은 전제입니다.**<br>· `distribution.assignment_id` 에 FK 제약이 있어 고아 행이 생길 수 없는 스키마라면, 고아 행 경로를 다시 세워야 한다. |
| 확인 방법 (미실행) | · `mysql -h 10.20.x.x -u readonly -p[SECRET] itembank -e "SELECT id, class_id, assignment_id FROM distribution WHERE id=41 OR assignment_id=9; SELECT id FROM assignment WHERE id=9; SELECT d.id, d.class_id FROM distribution d LEFT JOIN assignment a ON a.id=d.assignment_id WHERE a.id IS NULL;"`<br>· 스키마 대조: `db/mariadb/init/01-schema.sql` 의 `distribution` FK 정의 (`grep -n 'FOREIGN KEY\|REFERENCES' db/mariadb/init/01-schema.sql`)<br>· assignment 9 가 언제 없어졌는지: 9/17 09:52 이전 로그 · DB 감사 로그 (수집 범위 밖) |

### 3.3 가설 C — 배포 절차 (단일 호스트 재기동)

| 항목 | 내용 |
|---|---|
| 가설 | v1.4.2 를 대기 호스트 없이 API-1 한 대에서 재기동했고, nginx 가 넘길 upstream 이 없는 약 8초 동안 들어온 요청이 502 를 받았다. 500 과는 별개의 실패다. |
| 지지 근거 | · 2.2 13:41:50 ~ 13:41:55 행 — app.log:1244 ~ 1245 (풀 종료 완료 후 기동 시작까지 5.7초 공백)<br>· 2.2 13:41:51 ~ 13:41:59 행 — nginx-access.log:16865 ~ 16872 (502 8건, 경로 무관)<br>· 2.2 13:42:00 ~ 13:42:01 행 — app.log:1264, nginx-access.log:16873 (기동 완료 직후 200 복귀)<br>· deploy-history.md:18 (무중단 아님, 예상 중단 10초), :3 (API-2 평소 정지) |
| 반증 조건 | · 13:41:51 ~ 59 사이 다른 upstream 이 요청을 처리한 기록(같은 시각 200)이 있으면 "넘길 곳이 없었다" 부분이 틀리다.<br>· 502 가 재기동 구간 밖에서도 나왔으면 재기동 외 원인이 있다. |
| 확인 방법 (미실행) | · `awk 'substr($4,14,8)>="13:41:48" && substr($4,14,8)<="13:42:01" {print NR, substr($4,14,8), $9}' incident-logs/b-deploy-5xx/nginx-access.log`<br>· `awk '$9==502 {print substr($4,14,5)}' incident-logs/b-deploy-5xx/nginx-access.log \| uniq -c`<br>· nginx `error.log`(upstream 연결 실패 메시지)와 upstream 설정 — 수집 범위 밖 |

### 3.4 가설 D — 트래픽 · 클라이언트 (재시도)

| 항목 | 내용 |
|---|---|
| 가설 | 13:46 이후 500 건수는 교사단말A 한 사용자가 class 1 재배포를 반복 시도한 결과다. 같은 시점에 다른 사용자는 class 1 재배포를 하지 않았으므로, 영향 범위는 "class 1 재배포를 시도한 사용자"로 좁고, 건수(39건)는 사용자 수가 아니라 재시도 횟수를 반영한다. 이 가설은 시작 시점이 아니라 **건수와 영향 범위**를 설명한다. |
| 지지 근거 | · 2.3 13:46 ~ 14:34 집계 행 — 500 39건 전부 교사단말A, 2 ~ 4회 묶음<br>· 2.3 13:46:58, 13:47:08 행 — nginx-access.log:17170, 17181 (첫 500 후 20초 · 10초 간격 재시도)<br>· 2.3 집계 행 — 교사단말B 의 배포 후 재배포는 class 3 뿐이고 모두 200 |
| 반증 조건 | · 다른 클라이언트에서도 배포 후 class 1 재배포 요청이 있었는데 200 이었다면, "class 1 이면 실패" 전제(가설 A)와 함께 이 가설도 틀리다.<br>· 500 묶음의 간격이 일정(예: 고정 주기)하면 사람이 아닌 클라이언트 자동 재시도이며, 영향 범위 해석이 달라진다. |
| 확인 방법 (미실행) | · `awk '$7~/redistribute/ && $9==500 {print $1}' incident-logs/b-deploy-5xx/nginx-access.log \| sort \| uniq -c`<br>· 교사단말A 의 500 간 간격: `awk '$9==500 && substr($4,14,8)>"13:42" {print substr($4,14,8)}' incident-logs/b-deploy-5xx/nginx-access.log` 후 이웃 차이 계산<br>· 교사 화면(lms) 재배포 버튼의 재시도 로직 — 수집 범위 밖, 프런트 담당에게 요청 |

### 3.5 가설 밖에 둔 관찰 (해석 없음)

- `deploy-history.md` 의 v1.4.2 배포 명령 줄에 `Authorization: Bearer [TOKEN]` 형태의 배포 토큰이 평문으로 남아 있습니다(원본 파일 20행). 원본은 수정하지 않았고, 이 리포트에는 토큰을 옮기지 않았습니다. 이번 장애와는 별개로 보고합니다.

---

마스킹 적용: IP 22건 · 이메일 1건 · 학생 식별자 4건 · 토큰 1건 · 비밀번호/접속 문자열 2건
