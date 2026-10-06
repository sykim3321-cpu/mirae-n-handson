# 이관 리뷰 1 — 문항 검색(`search.php` → `GET /api/items/search`)

- 범위: `git diff upstream/main...HEAD` (3 커밋: `0558daf`, `f4cf84e`, `983637b`, 23 파일, +4228/−1)
- 요청: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관
- 방식: 실행 중인 레거시(:8081, compose `php`)와 신규 API(:8080, `bootRun`)에 같은 요청 40건을 보내 정규화 결과를 비교. 코드는 수정하지 않았다.

판정: 반려

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 | `characterization` `TARGET_BASE_URL=http://localhost:8080 npm test` → 29 통과 · 1 건너뜀(`example-units.test.js:21`, `legacyOnly` 전용 · 이번 범위 밖) · 실패 0. `modern/api` `./gradlew test` → 53 통과 · 실패 0 (태스크가 `UP-TO-DATE` 라 캐시된 직전 결과를 읽음) |
| 치명 이슈 0건 | 충족 | 치명 이슈 없음. SQL 은 Specification 바인딩, 비밀값 리터럴 없음 |
| 요청 범위 이탈 없음 | 미충족 | 이슈 #1 (동작 불변 위반), #3 (범위 밖 파일) |
| 컨벤션 준수 | 충족 | Controller 는 Service 만 주입, `try`/`catch` 없음(`ItemSearchCondition.java:168` 의 숫자 파싱용은 Controller 아님), DTO 는 `record`, 생성자 주입, 테스트에 `@DisplayName` · `@ActiveProfiles` |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `modern/api/src/main/java/com/example/item/ItemSearchResponse.java:14` | 레거시 `search.php` 는 입력값 경고를 화면에 낸다(`search.php:88,92,95,113,116,142,225,238`). 신규 응답은 `{status, rows, count, message}` 뿐이라 경고가 통째로 사라진다. 비교한 40건 중 레거시가 경고를 낸 경우가 21건(`level=6`, `page=-1`, `level=3abc`, `unit=m9-99`, 키워드 1글자 · 101자 · `%` · `_` 등)이고, 신규 응답에는 이를 알릴 필드가 없다. 행 · 건수 · message 는 40건 모두 일치했다. 동작 보존 테스트도 경고를 정규화에서 뺀다(`item-bank.test.js` 머리 주석). 그래서 테스트가 통과해도 이 차이는 잡히지 않는다 | `warnings: List<String>` 필드를 응답에 추가하고 레거시 문구를 그대로 옮기거나, 경고 제외가 의도라면 요청자 승인을 받아 `docs/item-bank/` 에 결정으로 남긴다. 어느 쪽이든 "동작 불변"과의 차이로 보고한다 |
| 2 | 경고 | `modern/api/src/main/java/com/example/common/GlobalExceptionHandler.java:53` | `POST /api/items/search` 가 500(`서버 내부 오류`)을 돌려준다. `HttpRequestMethodNotSupportedException` 이 전용 핸들러 없이 `Exception` 핸들러(`:53`)로 떨어진다. 레거시는 같은 요청에 200 이다. 신규 엔드포인트가 이 갭을 처음 드러냈다. 서버 로그에는 `log.error` 가 남는다 | 405 로 매핑하는 핸들러를 추가한다(별도 변경 권장). 이관 범위 밖이라 이번 건에서는 보고만 한다 |
| 3 | 경고 | `CLAUDE.md`, `.claude/skills/*`, `docs/**`, `characterization/**` (커밋 `0558daf`, `f4cf84e`) | 범위 문장은 "엔드포인트 1개 이관"인데 범위에는 컨벤션 · Skill · 문서 · 동작 보존 테스트가 함께 들어 있다. 동작 보존 테스트와 문서는 이관의 선행 조건(`CLAUDE.md` §5)이라 이해되지만, 나머지는 이관 커밋과 분리되는 편이 리뷰하기 쉽다. 코드 변경(`983637b`)만 보면 `modern/api` 의 `item/` 패키지와 `ItemRepository` 한 파일 안에 있다 | 다음부터 이관 PR 과 문서 · Skill PR 을 나눈다. 이번 건은 사람이 범위를 승인하면 된다 |
| 4 | 제안 | `modern/api/src/main/java/com/example/item/Item.java:60` · `ItemSearchRow.java:17` | 태그는 지연 로딩이고(`ItemRepository.java` 주석이 의도를 밝힘) `@BatchSize` 가 없다. 페이지마다 행 수만큼 태그 조회가 나갈 가능성이 있다. 이번 검증에서는 SQL 로그를 켜지 않아 실제 쿼리 수는 확인하지 못했다 | `hibernate.default_batch_fetch_size` 또는 `@BatchSize` 검토. SQL 로그로 쿼리 수 확인 후 판단 |

## 실행 관찰 (증거)

방법: 프로젝트에 맞는 `verifier-*` 스킬이 없어(`.claude/skills/` 에는 `convention-check`, `document-module`, `verify` 뿐) 이미 떠 있는 두 서버를 그대로 사용했다. 같은 쿼리를 양쪽에 보내 `characterization/lib/normalize.mjs` 로 정규화한 뒤 비교했다.

1. ✅ 동작 보존 테스트와 같은 16개 입력(단원 · 난이도+태그 · 키워드+정렬 · 난이도 5/6 · 키워드 100/101자 · 1글자 · 페이지 1000 · 전부 누락 · 빈 값 · 페이지 -1 · `level=3abc` · 소문자 단원 · 태그 62자 · `sort=DROP&dir=up`) → 전부 `SAME`
2. 🔍 경계 · 이상값 24개 추가(`level=0/-2/99999999999999999999`, `page=abc/2/2abc/99999999999`, `sort=level/id/TITLE`, `dir=ASC`, 단원 · 키워드 · 태그 앞뒤 공백, `q=_` · `%%`, 태그 부분 일치 · 50자, SQL 따옴표 문자열, `q=` 중복 · `level=` 중복, 모르는 파라미터, 이모지 60자, NBSP) → 전부 `SAME`, 상태 200/200
3. ❌ 위 40건에서 레거시는 21건에 경고를 냈고 신규 JSON 에는 해당 정보가 없음 → 이슈 #1
4. 🔍 `POST /api/items/search?unit=M5-1` → 신규 500 `{"status":500,"error":"Internal Server Error","message":"서버 내부 오류",...}`, 레거시 200 → 이슈 #2
5. 🔍 `HEAD` → 양쪽 200
6. 🔍 `page=%FF`(잘못된 UTF-8 바이트) → 신규 200, 기본 1페이지 결과
7. ✅ 응답 헤더: 신규 `Content-Type: application/json`, 레거시 `text/html; charset=UTF-8`. 의도된 형식 차이이며 정규화로 같은 모양이 된다

## 확인 필요

- 응답 형식이 HTML → JSON 으로 바뀌는 것을 호출 측(화면)이 어떻게 받아들일지는 이번 범위에서 보지 못했다.
- `modern/web` 쪽에 이 엔드포인트를 쓰는 곳이 있는지는 diff 에 없어 확인하지 않았다.
- `./gradlew test` 는 `UP-TO-DATE` 였다. 새로 돌려 확인하려면 `--rerun-tasks` 가 필요하다.

## 의심 동작(레거시 그대로 이관된 것)

- 키워드의 `%` · `_` 가 와일드카드로 쓰인다(`CROSS-CHECK.md:11`). 신규도 같다. 이관 중에는 고치지 않는다.
