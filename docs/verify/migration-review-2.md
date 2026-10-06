# 이관 리뷰 2 — 문항 검색(`search.php` → `GET /api/items/search`)

- 범위: `git diff upstream/main...HEAD` (5 커밋: `0558daf`, `f4cf84e`, `983637b`, `dc5a849`, `4934a0a` · 25 파일, +4304/−1)
- 요청: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관
- 리뷰 1(`migration-review-1.md`) 이후 달라진 것: 커밋 `dc5a849` · `4934a0a` 는 `docs/verify/decisions.md` · `docs/verify/migration-review-1.md` 만 추가한다(+76). `983637b..HEAD` 에서 `modern/` · `characterization/` · `legacy/` 변경은 0건이다. 즉 이관 코드는 리뷰 1 때와 같다.
- 방식: 실행 중인 레거시(:8081, compose `php`)와 신규 API(:8080, `bootRun`, 마지막 코드 커밋 이후에 기동)에 같은 요청을 보내 `characterization/lib/normalize.mjs` 로 정규화한 결과를 비교했다. `modern/api` 의 `./gradlew test` 는 이번에 실행하지 않았다. 코드는 수정하지 않았다.

판정: 승인 (조건부 — 아래 "승인 조건" 참조)

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 | `characterization` `npm test` (레거시 대상) → 30 통과 · 실패 0. `TARGET_BASE_URL=http://localhost:8080 npm test` (신규 API 대상) → 29 통과 · 1 건너뜀 · 실패 0. 건너뛴 1건은 `example-units.test.js` 의 `legacyOnly` 로, 이번 diff 에 없는 기존 파일이다. `modern/api` 단위 테스트는 실행하지 않았다(리뷰 1 에서 53 통과, 이후 코드 변경 없음) |
| 치명 이슈 0건 | 충족 | 치명 이슈 없음. 이번 범위에서 코드가 바뀌지 않았고, 리뷰 1 의 SQL 바인딩 · 비밀값 확인이 그대로 유효하다 |
| 요청 범위 이탈 없음 | 충족 | 이번에 늘어난 파일은 `docs/verify/*` 2개뿐이다. 리뷰 1 이슈 #1(경고 제외)은 요청자(sykim)가 `decisions.md` 에 의도한 설계로 기록했다. 이슈 #3(범위 밖 파일)은 아직 기록된 결정이 없다 → "확인 필요" |
| 컨벤션 준수 | 충족 | 코드 변경이 없어 리뷰 1 의 대조 결과를 그대로 쓴다. 이번에는 다시 대조하지 않았다 |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `modern/api/src/main/java/com/example/item/ItemSearchResponse.java:14` | 리뷰 1 #1 이 그대로다. 이번 65개 입력 중 레거시가 `<ul id="warnings">` 를 낸 경우가 23건이다. 예: `level=6` → 레거시는 "난이도는 1~5 사이여야 합니다." 를 내고 신규는 `{"status":200,"rows":[],"count":0,"message":"검색 결과가 없습니다"}` 만 돌려준다. 행 · 건수 · message 는 65건 모두 일치한다. 결정(`decisions.md`)으로 "현재 설계 유지"가 기록돼 있어 승인 · 반려에는 쓰지 않는다 | 결정대로 유지. 호출 측이 경고를 필요로 하게 되면 `warnings` 필드를 추가한다 |
| 2 | 경고 | `modern/api/src/main/java/com/example/common/GlobalExceptionHandler.java:53` | 리뷰 1 #2 가 그대로고, 범위가 더 넓다. `POST` 뿐 아니라 `PUT` · `DELETE` · `PATCH` 도 500 `서버 내부 오류` 를 돌려준다. 레거시는 네 메서드 모두 200 이다(`OPTIONS` · `HEAD` 는 양쪽 200). `HttpRequestMethodNotSupportedException` 전용 핸들러가 없어 `Exception` 핸들러로 떨어진다. 결정으로 별도 변경에 미뤄져 있다 | 별도 변경에서 405 핸들러를 추가한다. 이 파일이 공통 핸들러라 다른 엔드포인트의 PUT · DELETE 응답도 함께 바뀌는지 확인한다 |
| 3 | 경고 | `CLAUDE.md`, `.claude/skills/*`, `docs/**`, `characterization/**` (커밋 `0558daf`, `f4cf84e`) | 리뷰 1 #3 이 그대로다. 요청 문장은 "엔드포인트 1개 이관"인데 컨벤션 · Skill · 문서가 같은 범위에 들어 있다. `decisions.md` 에 이 건의 결정이 없다 | 사람이 범위를 승인하면 된다. 다음부터 이관 PR 과 문서 · Skill PR 을 나눈다 |
| 4 | 제안 | `modern/api/src/main/java/com/example/item/Item.java:60` | 리뷰 1 #4 가 그대로다. 태그 지연 로딩의 N+1 가능성이다. 이번에도 SQL 로그를 켜지 않아 쿼리 수는 확인하지 못했다. 승인 · 반려에 영향이 없다 | SQL 로그로 쿼리 수를 먼저 확인한다 |

## 실행 관찰 (증거)

방법: `.claude/skills/` 에 `verifier-*` 가 없어(`convention-check`, `document-module`, `verify` 뿐) 이미 떠 있는 두 서버를 그대로 썼다. 레거시 `http://localhost:8081/search.php`, 신규 `http://localhost:8080/api/items/search`.

1. ✅ 같은 쿼리 65개를 양쪽에 전송, 정규화 후 비교 → `same 65 / diff 0`. 입력: 단원 `M5-1..M6-2`, 난이도 `0 · -1 · 1~6 · 3abc · " 3 " · 3.0 · 03 · +3 · 1e1`, 태그(공백 포함 · 없는 태그), 키워드(`%` · `_` · `\` · 따옴표 SQL 문자열 · 100자 · 101자 · 전각 문자), 정렬(`title · level · unit · id · tags`, 대소문자, `dir=bogus`), 페이지(`0 · -1 · 1.5 · 999 · 전각 ２`), 모르는 파라미터(`size · limit · per_page`), 전체 조합
2. ❌→기록됨: 위 65건 중 레거시 경고 23건이 신규 JSON 에 없음 → 이슈 #1(결정된 사항)
3. 🔍 HTTP 메서드: `POST · PUT · DELETE · PATCH` → 신규 500 / 레거시 200. `OPTIONS · HEAD` → 양쪽 200
   ```
   POST 신규=500 레거시=200   PUT 신규=500 레거시=200
   DELETE 신규=500 레거시=200 PATCH 신규=500 레거시=200
   {"status":500,"error":"Internal Server Error","message":"서버 내부 오류","path":"/api/items/search","timestamp":"2026-10-06T04:14:35.676486489Z"}
   ```
4. 🔍 경로 변형: `/api/items/search/` → 404, `/api/items/search.json` → 400, `/api/items/Search` → 400, `/api/item/search` → 404
5. 🔍 `Accept: text/html` → 406 `text/html;charset=UTF-8`. 레거시는 Accept 와 무관하게 HTML 을 돌려준다
6. ✅ 응답 키 `['status','rows','count','message']`, 기본 요청은 count 20 · 행 20, 레거시와 일치
7. ✅ CORS: `Origin: http://localhost:5173` GET → `Access-Control-Allow-Origin: http://localhost:5173`
8. ✅ `characterization` 레거시 대상 30 통과, 신규 API 대상 29 통과 · 1 건너뜀

## 승인 조건

- 이 승인은 `decisions.md` 의 결정(#1 유지, #2 별도 변경으로 미룸)에 기대고 있다. 그 결정이 뒤집히면 이 판정도 다시 본다.
- #2 는 별도 변경으로 추적돼야 한다. 지금 상태는 "이관된 엔드포인트가 PUT · DELETE · PATCH 에도 500 을 돌려준다"이다.

## 확인 필요

- 이슈 #3(범위에 문서 · Skill · 컨벤션 포함)을 사람이 범위로 승인했는지 `decisions.md` 에 남아 있지 않다.
- `/api/items/search.json` · `/api/items/Search` 가 400 인 이유는 확인하지 못했다. 같은 접두어의 다른 경로(`/api/items/{id}` 류)로 매칭되는 것으로 보이지만 라우팅 코드는 읽지 않았다. 레거시에는 대응하는 동작이 없다.
- 응답이 HTML → JSON 으로 바뀌는 것을 호출 측(`modern/web`)이 받아들이는지는 diff 에 없어 보지 않았다(리뷰 1 과 같다).
- `./gradlew test` 를 이번에 새로 돌리지 않았다. 필요하면 `--rerun-tasks` 로 사람이 실행한다.
- `.claude/skills/verify/` 는 커밋되지 않은 파일(`??`)이라 `upstream/main...HEAD` 범위에 들어 있지 않다.

## 의심 동작(레거시 그대로 이관된 것)

- 키워드의 `%` · `_` 가 와일드카드로 쓰인다(`CROSS-CHECK.md:11`). 이번에도 `q=%`, `q=_` 가 양쪽에서 같은 결과를 냈다. 이관 중에는 고치지 않는다.
