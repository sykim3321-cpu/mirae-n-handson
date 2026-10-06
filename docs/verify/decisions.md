# 검증 결정 기록

리뷰 이슈를 받아들일지, 기각할지, 미룰지 사람이 정한 내용을 기록한다. 이슈 번호는 해당 리뷰 문서의 번호를 따른다.

## 이관 리뷰 1 — 문항 검색(`search.php` → `GET /api/items/search`)

- 리뷰: `docs/verify/migration-review-1.md`
- 결정자: sykim
- 날짜: 2026-10-06
- 코드 수정: 없음. 이 기록은 결정만 남긴다.

### #1 (경고) 입력값 경고를 응답에서 제외 — 현재 설계 유지

- **결정:** 신규 응답 `{status, rows, count, message}`(`modern/api/src/main/java/com/example/item/ItemSearchResponse.java:14`)에 레거시 `search.php` 의 입력값 경고를 넣지 않는다. `warnings` 필드는 추가하지 않는다.
- **이유:** 경고를 응답에서 빼는 것은 의도한 설계다. 비교한 40건 모두 행 · 건수 · message 가 레거시와 같았고, 달라지는 것은 경고 표시뿐이다.
- **남는 차이:** "동작 불변"과의 차이로 기록해 둔다. 레거시가 경고를 낸 21건(`level=6`, `page=-1`, `level=3abc`, `unit=m9-99`, 키워드 1글자 · 101자 · `%` · `_` 등)은 신규 응답만 봐서는 알 수 없다. 동작 보존 테스트도 정규화할 때 경고를 빼므로(`characterization/tests/item-bank.test.js` 머리 주석) 테스트로는 이 차이가 잡히지 않는다.

### #2 (경고) `POST /api/items/search` 가 500 응답 — 별도 변경으로 미룸

- **결정:** 이번 이관에서는 고치지 않고, 별도 변경에서 다룬다.
- **이유:**
  - 이관 요청 범위 밖이다.
  - `GlobalExceptionHandler` 는 공통 핸들러라 고치면 다른 API 에도 영향이 있다.
- **현재 상태:** `HttpRequestMethodNotSupportedException` 을 받는 전용 핸들러가 없어 예외가 `Exception` 핸들러(`modern/api/src/main/java/com/example/common/GlobalExceptionHandler.java:53`)로 떨어지고, 응답은 500 이 된다. 같은 요청에 레거시는 200 을 돌려준다.
- **별도 변경에서 할 일:** 405 로 매핑하는 핸들러를 추가한다. 다른 엔드포인트의 응답이 어떻게 바뀌는지도 함께 확인한다.

### #4 (제안) 태그 지연 로딩의 N+1 가능성 — 기록만 남김

- **내용:** 태그는 지연 로딩인데(`modern/api/src/main/java/com/example/item/Item.java:60` · `ItemSearchRow.java:17`) `@BatchSize` 가 없다. 그래서 페이지마다 행 수만큼 태그 조회가 나갈 수 있다. 리뷰에서 SQL 로그를 켜지 않아 실제 쿼리 수는 확인하지 못했다.
- **제안:** SQL 로그로 쿼리 수를 먼저 확인한다. 실제로 N+1 이 나오면 `hibernate.default_batch_fetch_size` 나 `@BatchSize` 적용을 검토한다.
- **상태:** 제안이라 승인 · 반려에는 영향이 없다. 적용 여부는 아직 정하지 않았다.

## reviewer Sub-agent 리뷰 — 문항 검색(`search.php` → `GET /api/items/search`)

- 리뷰: reviewer Sub-agent 보고(`git diff upstream/main...HEAD -- modern characterization`). 별도 리뷰 문서는 없다. 이슈 번호는 메인 세션의 분류 표 번호를 따른다.
- 결정자: sykim
- 날짜: 2026-10-06
- 코드 수정: 없음. 이 기록은 결정만 남긴다.

### #1 · #2 (경고) 배열 형태 파라미터 해석 차이 — 의도한 차이로 승인

- **내용:**
  - #1: 같은 이름의 일반 · 배열 파라미터가 섞여 오면(`q=a&q[]=b`) 레거시는 쿼리 문자열에서 뒤에 온 형태를 쓴다. 신규 코드는 일반 형태를 먼저 쓴다(`modern/api/src/main/java/com/example/item/ItemSearchCondition.java:126-138`).
  - #2: 인덱스 배열(`level[0]=3`)을 신규 코드는 파라미터 없음으로 보고 `level < 5` 로 조회한다. 레거시는 첫 원소 3 으로 조회한다. 중첩 배열(`level[a][b]=1`)은 레거시가 `"Array"` → `(int)` 0 이 되어 0건이고, 신규 코드는 전체를 조회한다(`legacy/item-bank-php/search.php:61-64`, `:211-229`).
- **결정:** 코드는 고치지 않는다. 의도한 차이로 승인한다.
- **이유:**
  - 배열 · 혼합 형태 파라미터는 비정상 입력 형태다.
  - 레거시를 재현하려면 원본 쿼리 문자열을 PHP `$_GET` 규칙대로 파싱해야 한다. 그러려면 `ItemSearchController` 까지 수정 범위를 넓혀야 한다.
- **남는 차이:** characterization 스냅샷에 이 입력 형태가 없어 테스트로는 잡히지 않는다.

### #3 (경고) DB 오류 시 응답 — 500 `ErrorResponse` 유지

- **결정:** 신규 API 는 DB 오류를 `GlobalExceptionHandler` 가 매핑하는 500 `ErrorResponse` 로 돌려준다. 이 동작을 유지한다.
- **이유:** Controller 에 try/catch 를 두지 않고 오류 응답 본문을 `ErrorResponse` 하나로 둔다는 컨벤션(CLAUDE.md 2절)을 따른다.
- **레거시 의심 동작:** 레거시는 DB 오류에도 HTTP 200 과 HTML 오류 문구 "검색 중 오류가 발생했습니다."를 돌려준다(`legacy/item-bank-php/search.php:712-729`). 레거시 코드는 고치지 않고 의심 동작으로만 기록한다.
- **남는 차이:** 스냅샷은 정상 경로만 담고 있어 이 차이는 테스트로 잡히지 않는다.
