# 이관 검증 3회차 — 문항 검색(search.php → /api/items/search)

- 검증 대상: `git diff upstream/main...HEAD -- modern characterization` (13개 파일, 커밋 0558daf · 983637b)
- 요청 범위: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 절차: `.claude/skills/verify/SKILL.md` — 체크리스트 대조는 reviewer, 테스트 실행은 tester 에 위임했고 최종 판정은 메인 세션이 했다.
- 기준: `templates/approval-checklist.md` v1, `CLAUDE.md`
- 검증일: 2026-10-06

판정: 승인

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 | `modern/api` `./gradlew test`: 커밋 상태 53/53 통과, tester 가 테스트 추가 후 재실행 70/70 통과. `characterization` `npm test`(레거시 :8081): 3개 파일, 30/30 통과. `TARGET_BASE_URL=http://localhost:8080 npm test`: 29 통과, 실패 0, 건너뜀 1(`example-units.test.js` "단원 목록 전체" — `it.skipIf(legacyOnly)`, `characterization/tests/example-units.test.js:21`, 의도된 동작). `item-bank.test.js` 16건은 새 API 에서 스냅샷과 모두 같았다. :8080 은 `modern/api/build/classes` 로 띄운 bootRun(pid 24954, 10-06 12:45 기동)이다. modern/api 의 마지막 커밋은 10-05 이고 modern 쪽 main 소스에는 미커밋 변경이 없어 HEAD 코드로 판단했다 |
| 치명 이슈 0건 | 충족 | 치명 4유형에 해당하는 것 없음. `ItemSearchSpecifications.java:47` 의 `"%" + keyword + "%"` 는 Criteria API 바인딩 값이라 SQL 문자열 조립이 아니다 |
| 요청 범위 이탈 없음 | 충족 | Java 11개는 모두 `/api/items/search` 엔드포인트 1개의 이관에 쓰인다. `ItemRepository` 는 `JpaSpecificationExecutor` 추가와 `findAll` override 만 바꿨다. characterization 2개는 이관 커밋보다 먼저 들어간 베이스라인 커밋(0558daf)이고, 그 뒤로 수정된 적이 없다. 의존성 · 스키마 · 설정 변경 없음 |
| 컨벤션 준수 | 충족 | 판정 대상 항목(계층 규칙 · 빈 catch · System.out) 위반 없음. 그 밖의 위반은 경고 #2 |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `modern/api/src/main/java/com/example/item/ItemSearchCondition.java:174-178` | 레거시와 동작이 다르다(스냅샷 밖). 숫자가 약 309자리를 넘어 double 로 무한대가 되는 `page` 값에서 갈린다. 실측: `page=` 9 를 400개 이어 붙인 값 → 레거시 :8081 은 count 20 · 행 20(1페이지), 새 API :8080 은 count 20 · 행 0(999페이지). 원인: PHP 7.4 `(int)` 는 무한대를 0 으로 바꾸고(→ 1페이지로 고정), 새 코드는 `d >= 0x1p63` 분기에서 `Long.MAX_VALUE` 로 바꾼다(→ 999). 20자리(`99999999999999999999`)나 `9223372036854775808` 은 두 서버가 같았다. `ItemSearchConditionTest` 의 `phpIntval("1e999")` → `Long.MAX_VALUE` 기대값도 같은 가정 위에 있다 | `phpIntval` 에서 무한대 · NaN 이면 0 을 돌려주도록 맞춘다. 이 케이스(아주 긴 숫자열 page, `level=1e999`)를 modern/api 단위 테스트에 추가한다. characterization 에 케이스를 더할지는 사람이 정한다(스냅샷 변경이므로 묻고 진행) |
| 2 | 경고 | `modern/api/src/main/java/com/example/item/ItemSearchSpecifications.java:16` | CLAUDE.md "동작을 바꾼 main 클래스마다 같은 패키지에 `<클래스>Test.java`" 를 어겼다. `ItemSearchSpecificationsTest.java` 가 없고, `ItemRepositoryTest` 와 `ItemSearchServiceTest` 를 거쳐 간접으로만 실행된다 | `ItemSearchSpecificationsTest` 를 추가한다. level(null · int 범위 밖) · tag · keyword 의 와일드카드를 직접 검증한다 |
| 3 | 경고 | `modern/api/src/main/java/com/example/item/ItemSearchCondition.java:126-138` | `q=a&q[]=b` 처럼 일반 값과 배열 형태가 섞여 오면, PHP 는 쿼리 문자열에서 뒤에 온 쪽을 쓰지만 새 코드는 항상 일반 값을 쓴다. 주석 123-125행이 이 한계를 적어 두었다. `q[k]=` 처럼 키가 있는 배열도 처리하지 않는다. 스냅샷 케이스에는 없다 | 원본 쿼리 문자열의 순서로 판단하거나, 이 차이를 `docs/item-bank/BUSINESS-RULES.md` 에 이관 차이로 기록한다 |
| 4 | 제안 | `modern/api/src/main/java/com/example/item/ItemSearchResponse.java:14` | 본문 `status` 는 항상 200 이다. 정규화(`normalize.mjs`)는 HTTP 상태를 쓰므로 이 필드를 읽지 않는다 | 이관을 마친 뒤 응답 계약을 정할 때 제거를 검토한다 |

## 확인 필요

- `level=1e999` 일 때 레거시는 `level = 0`, 새 코드는 거짓 조건(`cb.disjunction()`, `ItemSearchSpecifications.java:65-67`)으로 처리한다. 지금은 시드에 난이도 0 문항이 없어 두 서버 모두 0건이다(실측). 다만 스키마에 난이도 CHECK 가 없어 데이터가 바뀌면 결과가 갈릴 수 있다. 이슈 #1 을 고치면 함께 맞춰진다.
- 정규화가 HTML 과 JSON 을 다르게 다룬다. `normalize.mjs` 는 HTML 셀의 공백을 접고 trim 하지만 JSON 값은 그대로 둔다. 제목이나 태그에 앞뒤 · 연속 공백이 있는 데이터가 생기면 스냅샷이 어긋날 수 있다. 지금 시드에는 해당 데이터가 없다.
- 뷰의 `GROUP_CONCAT` 은 `group_concat_max_len`(기본 1024바이트)에서 잘리지만 새 코드는 자르지 않는다. 태그가 아주 많은 문항에서만 갈린다.
- DB 오류가 나면 레거시는 HTTP 200 에 "검색 중 오류가 발생했습니다" HTML 을 내고(search.php:726-729), 새 API 는 `GlobalExceptionHandler` 를 거쳐 500 을 낸다. 의도한 차이인지 정해야 한다.
- tester 가 추가한 테스트가 커밋되지 않은 채 작업 트리에 있다. 새 파일은 `ItemSearchRowTest.java` · `ItemSearchResponseTest.java`, 케이스를 추가한 파일은 `ItemSearchConditionTest.java`(6건) · `ItemSearchServiceTest.java`(5건)이다. 70/70 통과는 이 파일들을 포함한 결과다. 커밋 여부는 사람이 정한다. (reviewer 는 Row/Response 테스트가 upstream/main 에 원래 있었다고 보고했지만, `git ls-tree upstream/main` 과 `git status` 로 확인해 보니 tester 가 새로 만든 파일이었다.)

## 의심 동작 (레거시 그대로 옮김 · 이관 중 고치지 않음)

- BR-06: 난이도를 비우면 난이도 5 가 빠진다. 레거시 주석은 "1~5 모두"라고 적혀 있다.
- BR-08: `3abc` · `3.5` · `03` 이 난이도 3 으로 조회된다.
- BR-09: "입력값 그대로 조회" 경고를 내지만, 콜레이션 때문에 소문자 단원 코드도 맞는다.
- 태그 이름에 쉼표가 있으면 둘로 나뉜다(뷰 `tag_names` 를 쉼표로 이어 붙였다가 나누는 동작).
- BR-05 · BR-13: 키워드 · 태그를 잘라서 조회한다. 새 API 는 경고 문구를 응답에 넣지 않는다.
- BR-15: 키워드의 `%` · `_` 를 이스케이프하지 않아 와일드카드로 동작한다.
- BR-21: 999 를 넘는 페이지는 999 로 고정되어, 건수는 있는데 행은 빈 응답이 나온다.
