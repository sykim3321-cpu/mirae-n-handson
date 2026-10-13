# incident-logs — 장애 로그 RCA 실습용 로그 묶음 (Day 2-2)

로그는 전부 **더미 데이터**입니다. 실제 서비스 · 사람과 관계가 없습니다. 다만 실제 장애 로그처럼 IP · 이메일 · 학생 식별자 · 토큰처럼 보이는 값이 일부러 섞여 있습니다. 리포트로 옮길 때 가리는 것까지가 실습입니다.

| 폴더 | 상황 | 파일 |
|---|---|---|
| `a-connection-pool/` | DB 커넥션 풀 고갈로 API 응답 지연 · 실패 | `app.log`(Spring Boot), `nginx-access.log`(combined), `nginx-error.log`, `mariadb-slow.log` |
| `b-deploy-5xx/` | 배포 후 특정 API에서 5xx 증가 | `deploy-history.md`, `app.log`(Spring Boot), `nginx-access.log`(combined) |
| `c-batch-duplicate/` | 배치 중복 실행에 따른 데이터마트 이중 적재 | `batch-job.log`, `datamart-counts.csv` |
| `d-mssql-deadlock/` | MS-SQL 데드락으로 성적 집계가 지연 | `mssql-wait.log`, `app-timeout.log` |

- 파일마다 시간대 표기가 다를 수 있습니다. 타임라인을 만들기 전에 파일별 시각 형식부터 확인하세요.
- 일부 파일은 수만 줄입니다. 통째로 읽지 말고 `wc -l`, `head`, `tail`, `grep -c`, `grep -n`, `sed -n` 으로 필요한 부분만 봅니다.
- 원본 로그는 고치지 않습니다. 분석 결과는 `docs/rca/<폴더 이름>.md` 에 씁니다.
