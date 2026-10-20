배치 로그 · 적재 건수 CSV를 대조해 2026-09-15 `load_datamart` 중복 실행 같은 이상을 `docs/batch-check/report.md` 에 남기고, 같은 사건의 1차 원인 리포트를 `docs/rca/c-batch-duplicate.md` 로 만드는 과정을 기존 Skill 두 개로 자동화한다.
실행: Skill `batch-check` 를 먼저 돌리고 이어서 Skill `rca` 를 돌린다(대상 폴더와 지시문은 `DESIGN.md` 5절).
한계는 샘플 로그 하루치만 판정하고, 배치 점검 리포트에는 마스킹이 없어 IP가 그대로 남으며, DB의 실제 중복 행은 조회하지 않았다는 점이고, 현업에 옮길 때는 로그 · CSV 경로를 운영 수집 위치로 바꾸고 `batch-check` 에 마스킹을 추가하고 `readonly` 계정으로 중복 행을 확인하는 단계를 넣어야 한다.
