# pipeline-samples — 데이터 · 인프라 자동화 실습 자료 (Day 3-2)

전부 **더미 데이터**입니다. 실제 서비스 · 사람 · AWS 계정과 관계가 없습니다. IP · 이메일처럼 보이는 값이 일부 섞여 있습니다.

| 경로 | 쓰는 실습 | 내용 |
|---|---|---|
| `batch-logs/YYYY-MM-DD.log` | (a) 실습 1 · 2 | 일자별 배치 실행 로그. job 5개(`build_item_stats`, `export_grades`, `extract_submissions`, `load_datamart`, `build_report_cache`) |
| `datamart-counts.csv` | (a) 실습 1 · 2 | 날짜 · job별 적재 건수. 머리글 `date,job,row_count,loaded_at` |
| `terraform/main.tf` · `variables.tf` | (b) 실습 3 · 4 | 문항 은행 dev DB(RDS MariaDB)와 보안 그룹 |

## 배치 로그 읽는 법

- **파일 이름은 기준일**(로그의 `date=` 값)입니다. job은 기준일 **다음날 새벽**에 돕니다. 예: `2026-09-15.log` 의 줄은 `2026-09-16 00:00` 이후 시각으로 찍혀 있습니다.
- 한 줄에 `job=<이름> date=<기준일> run_id=<실행 ID> status=<START|RUNNING|SUCCESS> rows=<건수>` 가 들어 있습니다. 소요 시간은 `SUCCESS` 줄의 `elapsed=` 값이나, 없으면 `START` · `SUCCESS` 두 줄의 시각 차이로 봅니다.
- 파일을 통째로 읽지 말고 `grep -n`, `awk`, `wc -l` 로 필요한 줄만 뽑습니다.

```bash
ls pipeline-samples/batch-logs/
head -5 pipeline-samples/datamart-counts.csv
grep -n "load_datamart" pipeline-samples/batch-logs/2026-09-15.log
```

## Terraform

- 실제로 적용하지 않습니다. `terraform` CLI가 없어도 실습할 수 있습니다.
- 실습 중 고친 내용은 끝나기 전에 되돌립니다: `git checkout -- pipeline-samples/terraform/`
- `terraform init` 을 실행했다면 생긴 `.terraform/` 폴더와 `.terraform.lock.hcl` 파일도 지웁니다.
