# 배포 이력 — item-bank-api (`modern/api`)

시각은 모두 KST(한국 표준시)입니다. 배포 대상 호스트: `api-1` (10.20.1.21), 대기 호스트 `api-2` (10.20.1.22, 평소 정지).

| 버전 | 배포 시작 | 배포 완료 | 배포자 | 변경 요약 | 변경 파일 |
|---|---|---|---|---|---|
| v1.4.2 | 2026-09-17 13:40:05 | 2026-09-17 13:42:31 | lee.dev@example.com | 재배포 응답에 학급 배포 이력(history) 포함 — `DistributionController.redistribute` | `modern/api/src/main/java/com/example/assignment/DistributionController.java` |
| v1.4.1 | 2026-09-14 18:05:12 | 2026-09-14 18:07:40 | park.dev@example.com | 단원별 문항 목록 조회 정리 — `ItemService.listActiveItemsByUnit` | `modern/api/src/main/java/com/example/item/ItemService.java` |
| v1.4.0 | 2026-09-08 18:10:03 | 2026-09-08 18:13:22 | park.dev@example.com | 학급 리포트 API 추가 — `GET /api/classes/{id}/report` | `modern/api/src/main/java/com/example/assignment/ReportController.java`, `modern/api/src/main/java/com/example/assignment/ReportService.java` |
| v1.3.9 | 2026-09-01 18:02:47 | 2026-09-01 18:04:55 | lee.dev@example.com | 배포 단건 조회 API — `GET /api/distributions/{id}` | `modern/api/src/main/java/com/example/assignment/DistributionService.java` |

## v1.4.2 (2026-09-17)

- 요청: 교사 화면에서 재배포 후 목록을 다시 불러오지 않도록, 재배포 응답에 같은 학급의 배포 이력을 함께 돌려 달라(교무부 요청, 수업 중 반영 요청).
- 변경: `POST /api/distributions/{id}/redistribute` 응답 형식 변경 `{distribution}` → `{distribution, history[]}`.
- 변경 지점: `DistributionController.redistribute` 에서 재배포 후 `DistributionService.listByClass(classId)` 호출 추가.
- 테스트: `./gradlew test` 통과 (24 tests). 스테이징 DB(시드 데이터)에서 재배포 3건 수동 확인.
- 배포 방식: 단일 호스트 재기동(무중단 아님). 예상 중단 10초 안팎.
- 배포 명령(파이프라인 로그에서 복사):
  `curl -s -X POST https://deploy.example.com/api/v1/apps/item-bank-api/releases -H "Authorization: Bearer dpl_7Fq2kX9wLm4RtY8vZ1cN" -d '{"version":"1.4.2","host":"10.20.1.21"}'`
- 승인: kim.lead@example.com (13:38)
- 롤백 계획: `v1.4.1` 이미지로 재기동(약 3분).

## v1.4.1 (2026-09-14)

- 변경: 단원별 공개 문항 목록의 정렬 기준을 id 순으로 고정.
- 테스트: `./gradlew test` 통과.
- 승인: kim.lead@example.com

## v1.4.0 (2026-09-08)

- 변경: 학급 리포트 API 신설(외부 집계 시스템 서명 포함).
- 테스트: `./gradlew test` 통과. 리포트 응답 시간 스테이징 0.3~0.6초.
- 승인: kim.lead@example.com

## v1.3.9 (2026-09-01)

- 변경: 배포 단건 조회 API 추가.
- 승인: kim.lead@example.com
