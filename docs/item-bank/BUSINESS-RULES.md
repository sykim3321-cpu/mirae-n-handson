# 문항 은행 (legacy/item-bank-php) 비즈니스 규칙 후보

- 선행 문서: [ARCHITECTURE.md](ARCHITECTURE.md), [ERD.md](ERD.md)
- 범위: `legacy/item-bank-php/` 의 `search.php` · `register.php` · `units.php` 와, 이 코드가 참조하는 `db/mariadb/init/01-schema.sql` 의 뷰 정의.
- 원칙: 코드(조건문 · SQL)에 있는 것만 규칙으로 올렸다. 근거가 주석뿐인 내용은 규칙으로 올리지 않고 비고에 "주석만 있음"으로 적었다. 주석과 코드가 다르면 코드를 기준으로 썼다.
- 실행 여부: BR-01 · BR-02 · BR-08 · BR-09 · BR-11 은 CROSS-CHECK.md 와의 불일치를 풀려고 2026-09-29 에 실행해 확인했다(compose `php` 프로필의 레거시 :8081 에 GET `search.php` 요청, MariaDB 는 `readonly` 계정으로 SELECT 만, 시드 상태). 나머지 규칙은 실행해 보지 않았고 코드 읽기로만 확인했다. 자세한 내용은 맨 아래 "검증 이력".

## 먼저 볼 것

| 구분 | 내용 | 규칙 |
|---|---|---|
| 주석-코드 불일치 | 난이도를 비우고 검색하면 주석은 "1~5 모두 포함"이지만 코드는 `level < 5` 로 **난이도 5 문항을 뺀다** | BR-06 |
| 같은 규칙, 다른 결과 | 단원 목록의 "공개 문항 수"는 난이도 5 를 세지만, 조건 없는 검색은 난이도 5 를 빼서 두 화면의 건수가 다르다 | BR-01, BR-06, BR-30 |
| 주석-코드 불일치 | 뷰 주석은 "등록 화면도 이 뷰를 기준으로 삼는다"고 하지만 `register.php` 는 뷰를 참조하지 않는다 | BR-02 비고 |
| 주석-코드 불일치 | 난이도가 `3abc` · `3.5` · `03` 이면 주석은 "대개 0건"이지만 **난이도 3 문항이 경고와 함께 조회된다** (실행 확인) | BR-08 |
| 경고-동작 불일치 | 단원 `m5-1` 은 "입력값 그대로 조회합니다" 경고가 뜨지만 **`M5-1` 과 같은 결과가 나온다** (콜레이션, 실행 확인) | BR-09 |
| 기능 없음 | **수정**(UPDATE) 코드가 모듈에 없다. 수정 시 검증 규칙은 뽑을 것이 없다 | BR-31 |

---

## 1. 목록 노출 · 제외 조건

### BR-01
- 규칙: 문항의 `status` 가 `'A'` 가 아니면 검색 결과와 건수에서 제외한다.
- 근거: `search.php:521-522`, `:526`, `db/mariadb/init/01-schema.sql:70`, `units.php:17`
- 근거 코드:
  ```php
  $select = "SELECT id, title, unit_code, level, tag_names, created_at";   // search.php:521
  $from   = " FROM v_item_public";                                        // search.php:522
  $countSql = "SELECT COUNT(*) AS cnt" . $from . $where;                  // search.php:526
  ```
  ```sql
  WHERE i.status = 'A';                                              -- 01-schema.sql:70
  ```
  ```php
  (SELECT COUNT(*) FROM item i WHERE i.unit_id = u.id AND i.status = 'A') AS item_count  // units.php:17
  ```
- 확신도: 확실
- 비고: 같은 규칙이 두 방식으로 구현돼 있다. 검색은 뷰(`item JOIN unit ... WHERE status='A'`)를, 단원 목록은 `item` 을 직접 `status = 'A'` 로 센다. `A=공개 D=삭제 R=검수중` 이라는 코드 의미는 **주석만 있음**(`01-schema.sql:26`, `units.php:15`). `status` 에 CHECK 제약은 없다.
- 실행 확인(2026-09-29, 시드 상태): `readonly` 로 `SELECT status, COUNT(*) FROM item GROUP BY status` → `A` 26 · `D` 2 · `R` 2. `v_item_public` 은 26행. `status <> 'A'` 인 문항 4건이 검색 결과에 나오지 않았다.
  ```text
  비공개 문항: id 17(R, 난이도 3, M5-1) · 30(R, 난이도 5, M6-2) · 11(D, 난이도 2, M5-3) · 23(D, 난이도 4, M5-2)
  GET search.php?unit=M5-1          → id 18 12 6 1 2   (17 없음, 24 는 난이도 5 라 BR-06 으로 빠짐)
  GET search.php?level=5            → id 24~29, 6건     (30 없음)
  GET search.php?unit=M5-3&level=2  → id 7              (11 없음)
  GET search.php?unit=M5-2&level=4  → id 19             (23 없음)
  ```
  건수(`id="count"`)도 표의 행 수와 같아, 목록과 건수 양쪽에서 빠진다(같은 `$from` · `$where` 를 쓰는 `search.php:522`, `:526` 과 일치).

### BR-02
- 규칙: 문항을 등록하면 `status = 'R'` 로 저장하므로, 등록 직후 문항은 검색 결과에 나오지 않는다.
- 근거: `register.php:92-95`, `db/mariadb/init/01-schema.sql:70`, `search.php:522`
- 근거 코드:
  ```php
  $stmt = $conn->prepare(                                                          // register.php:92
      "INSERT INTO item (id, unit_id, title, stem, level, status, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, 'R', NOW(), NOW())"
  );                                                                               // register.php:95
  ```
  ```sql
  WHERE i.status = 'A';                                              -- 01-schema.sql:70 (검색이 읽는 뷰 v_item_public)
  ```
- 확신도: 확실
- 비고: 'R' 이 "검수중"이라는 것과 "검수 완료 후 노출"은 주석(`register.php:84`)과 안내 문구(`register.php:116`)에만 있다 — **주석만 있음**. `R → A` 로 바꾸는 코드는 이 모듈에 없다(ERD.md 미확인 참고). 뷰 주석 `01-schema.sql:51` "(검색 화면 search.php 와 등록 화면이 모두 이 뷰를 기준으로 삼는다)"는 코드와 다르다 — `register.php` 에 `v_item_public` 참조가 없다. 스키마 기본값은 `DEFAULT 'A'`(`01-schema.sql:26`)지만 등록 코드는 항상 'R' 을 명시한다.
- 실행 확인 범위: "`'R'` 문항은 검색에 나오지 않는다"는 시드의 `R` 문항(id 17 · 30)으로 확인했다(BR-01 실행 확인). 등록 POST 는 쓰기라 실행하지 않았다 — "INSERT 가 `'R'` 을 쓴다"는 코드 읽기로만 확인했다.

---

## 2. 문항 검색 — 조건 조합

### BR-03
- 규칙: 키워드 · 단원 · 난이도 · 태그 조건은 모두 `AND` 로 결합한다.
- 근거: `search.php:44`, `:98`, `:118`, `:215`, `:218`, `:244`
- 근거 코드:
  ```php
  $where    = " WHERE 1=1";
  $where .= " AND (title LIKE ? OR stem LIKE ?)";
  $where .= " AND unit_code = ?";
  ```
- 확신도: 확실
- 비고: 같은 파라미터가 배열(`q[]=a&q[]=b`)로 오면 첫 값만 쓴다(`search.php:55`, `:59`, `:63`, `:67`). OR 조합 · 다중 단원 · 다중 태그 검색은 코드에 없다.

### BR-04
- 규칙: 키워드가 있으면 앞뒤 공백을 제거한 뒤 제목 또는 지문에 부분 일치(`LIKE '%키워드%'`)하는 문항을 찾는다.
- 근거: `search.php:85`, `:97-98`
- 근거 코드:
  ```php
  $q = trim($q);
  $like = '%' . $q . '%';
  $where .= " AND (title LIKE ? OR stem LIKE ?)";
  ```
- 확신도: 확실
- 비고: 공백만 있는 키워드는 조건 없음과 같다(`search.php:90`).

### BR-05
- 규칙: 키워드가 100자를 넘으면 앞 100자로 자르고 경고를 보여 준다.
- 근거: `search.php:86-88`, `:494`
- 근거 코드:
  ```php
  if (mb_strlen($q, 'UTF-8') > 100) {
      $q = mb_substr($q, 0, 100, 'UTF-8');
  ```
- 확신도: 확실
- 비고: 폼 입력칸에도 `maxlength="100"` 이 있다(`search.php:494`). 매직 넘버 100 이 두 곳에 따로 있다.

### BR-06
- 규칙: 난이도 파라미터가 비어 있으면 **난이도 5 미만(1~4) 문항만** 조회한다.
- 근거: `search.php:214-216`
- 근거 코드:
  ```php
  if ($level == '') {
      $where .= " AND level < 5";
  }
  ```
- 확신도: 확실
- 비고: **주석과 코드 불일치.** 바로 위 주석 `search.php:213` 은 "난이도 값이 비어 있으면 전체 난이도 검색 (1~5 모두 포함)", 파일 머리 주석 `search.php:8` 은 "빈값 허용"이라고 한다. 폼의 빈 값 라벨도 '전체'(`search.php:192`)다. 이 조건은 요약(`$summary`)에 들어가지 않아 화면에는 "조건 없이 검색했습니다"(`search.php:356`)가 나온다 — 사용자는 난이도 5 가 빠진 것을 알 수 없다. 난이도 5 는 `level=5` 를 명시해야만 검색된다(BR-07). 단원 목록의 공개 문항 수(BR-30)는 난이도 5 를 포함하므로 두 화면 건수가 다르다.

### BR-07
- 규칙: 난이도가 1~5 중 한 자리 숫자이면 해당 난이도와 정확히 같은 문항만 조회한다.
- 근거: `search.php:217-220`
- 근거 코드:
  ```php
  else if (preg_match('/^[1-5]$/', $level)) {
      $where .= " AND level = ?";
  ```
- 확신도: 확실
- 비고: 앞뒤 공백은 제거한 뒤 판정한다(`search.php:211`). 범위 검색(예: 3 이상)은 없다.

### BR-08
- 규칙: 난이도가 비어 있지 않고 1~5 형식(`/^[1-5]$/`)도 아니면 경고를 보여 주고, 값을 PHP `(int)` 로 바꿔 `level = 정수` 로 그대로 조회한다. 그래서 **앞자리가 1~5 인 값(`3abc` · `3.5` · `03`)은 그 난이도 문항이 경고와 함께 조회되고**, 숫자로 시작하지 않는 값(`abc`)은 0 이 되어 0건이다.
- 근거: `search.php:223-229`
- 근거 코드:
  ```php
  else {
      // 1~5 밖의 값은 그대로 정수로 비교한다 (대개 0건)          // search.php:224 — 주석
      $warnings[] = '난이도는 1~5 사이여야 합니다.';
      $where .= " AND level = ?";
      $types .= 'i';
      $values[] = (int)$level;
      $summary[] = '난이도 ' . (int)$level;                       // search.php:229
  ```
- 확신도: 확실 (실행 확인)
- 실행 확인(2026-09-29, 레거시 :8081):
  ```text
  GET search.php?level=3     → 경고 없음,                      요약 "난이도 3", 검색 결과 5건 (id 12~16)
  GET search.php?level=3abc  → 경고 "난이도는 1~5 사이여야 합니다.", 요약 "난이도 3", 검색 결과 5건 (id 12~16)
  GET search.php?level=3.5   → 같은 경고, 요약 "난이도 3", 검색 결과 5건
  GET search.php?level=03    → 같은 경고, 요약 "난이도 3", 검색 결과 5건
  GET search.php?level=abc   → 같은 경고, 요약 "난이도 0", 검색 결과 0건
  ```
- 비고: **주석과 코드 불일치.** 주석 `search.php:224` "(대개 0건)"은 `abc` · `6` 처럼 앞자리가 1~5 가 아닌 값에만 맞는다. 요약에는 변환된 정수가 나와서(`search.php:229`) 사용자는 "난이도 3" 으로 검색된 것처럼 보지만, 경고는 "1~5 사이여야 합니다" 라 두 문구가 서로 어긋난다.

### BR-09
- 규칙: 단원 파라미터가 있으면 앞뒤 공백을 제거한 뒤 단원 코드(`unit_code`)와 `=` 로 비교해 같은 문항만 조회한다. 코드에서는 대소문자를 바꾸지 않지만, 컬럼 콜레이션이 `utf8mb4_unicode_ci` 라 **비교는 대소문자를 구분하지 않는다** — `m5-1` 과 `M5-1` 의 결과가 같다.
- 근거: `search.php:109`, `:115-120`, `db/mariadb/init/01-schema.sql:18`
- 근거 코드:
  ```php
  $unit = trim($unit);                                                          // search.php:109
  if ($unit !== strtoupper($unit)) {                                            // search.php:115
      $warnings[] = '단원 코드는 대문자로 입력하세요. (입력값 그대로 조회합니다)';   // search.php:116
  }
  $where .= " AND unit_code = ?";                                               // search.php:118
  $values[] = $unit;                                                            // search.php:120
  ```
  ```sql
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;   -- 01-schema.sql:18 (unit 테이블, 뷰의 unit_code = u.code)
  ```
- 확신도: 확실 (실행 확인)
- 실행 확인(2026-09-29):
  ```text
  GET search.php?unit=M5-1  → 경고 없음,                               검색 결과 5건 (id 18 12 6 1 2)
  GET search.php?unit=m5-1  → 경고 "단원 코드는 대문자로 입력하세요. (입력값 그대로 조회합니다)", 검색 결과 5건 (id 18 12 6 1 2)
  readonly: SELECT 'm5-1' = code, COLLATION(code) FROM unit WHERE code='M5-1'  → 1, utf8mb4_unicode_ci
  ```
- 비고: 단원은 **코드**로 받는다(등록 화면은 단원 **id** 로 받는다, BR-24). **경고 문구와 동작이 어긋난다.** "(입력값 그대로 조회합니다)"는 PHP 가 값을 바꾸지 않고 넘긴다는 뜻으로는 맞지만, 사용자는 소문자 그대로(= 0건) 조회된다고 읽을 수 있다. 실제로는 대문자와 같은 결과가 나온다. 비교가 대소문자를 무시하는 것은 PHP 코드가 아니라 DB 콜레이션에 달려 있으므로, 새 API 로 옮길 때 DB · JPA 쿼리의 콜레이션이 바뀌면 결과가 달라진다.

### BR-10
- 규칙: 단원 코드가 `영문자 1 + 숫자 1~2 + '-' + 숫자 1~2` 형식이 아니거나 소문자를 포함하면 경고만 보여 주고 조회는 그대로 한다.
- 근거: `search.php:111-117`
- 근거 코드:
  ```php
  if (!preg_match('/^[A-Za-z][0-9]{1,2}-[0-9]{1,2}$/', $unit)) {
  if ($unit !== strtoupper($unit)) {
  ```
- 확신도: 확실
- 비고: 형식 검사는 조회를 막지 않는다(주석 `search.php:112` 과 일치).

### BR-11
- 규칙: 단원 코드가 `unit` 테이블에 없으면 "등록되지 않은 단원 코드입니다" 경고를 보여 주고 조회는 그대로 한다.
- 근거: `search.php:125`, `:141-143`
- 근거 코드:
  ```php
  $stmt = $conn->prepare("SELECT name, grade FROM unit WHERE code = ?");
  if ($unitName === '') {
      $warnings[] = '등록되지 않은 단원 코드입니다: ' . $unit;
  ```
- 확신도: 확실
- 비고: 이 조회(`search.php:125`)도 `unit.code` 콜레이션(`01-schema.sql:18`, `utf8mb4_unicode_ci`)을 따라 대소문자를 무시한다. `unit=m5-1` 은 "등록되지 않은 단원 코드" 경고 없이 요약에 "단원 m5-1 분수의 덧셈과 뺄셈 (5학년)"이 나왔다(2026-09-29 실행 확인). 태그 경고(BR-14)는 PHP `===` 로 대조해 대소문자를 구분하므로 두 경고의 기준이 다르다.

### BR-12
- 규칙: 태그 파라미터가 있으면 앞뒤 공백을 제거한 뒤 태그 이름이 정확히 같은(`=`) 태그가 하나라도 붙은 문항만 조회한다.
- 근거: `search.php:235`, `:244-245`
- 근거 코드:
  ```php
  $where .= " AND EXISTS (SELECT 1 FROM item_tag it JOIN tag t ON t.id = it.tag_id"
          . " WHERE it.item_id = v_item_public.id AND t.name = ?)";
  ```
- 확신도: 확실
- 비고: `%` · `_` 가 있으면 "부분 일치를 지원하지 않습니다" 경고만 내고(`search.php:237-238`) `=` 비교라 와일드카드로 쓰이지 않는다. 키워드(BR-04)는 반대로 `%` · `_` 가 와일드카드로 쓰인다(`search.php:94-95`, 이스케이프 없음). 태그는 이름으로 받는다(등록 화면은 태그 id 로 받는다, BR-26).

### BR-13
- 규칙: 태그 이름이 50자를 넘으면 앞 50자로 자르고 경고를 보여 준다.
- 근거: `search.php:240-242`
- 근거 코드:
  ```php
  if (mb_strlen($tag, 'UTF-8') > 50) {
      $tag = mb_substr($tag, 0, 50, 'UTF-8');
  ```
- 확신도: 확실
- 비고: 50 은 스키마 `tag.name VARCHAR(50)`(`01-schema.sql:37`)과 같은 값이다.

### BR-14
- 규칙: 태그 이름이 `tag` 목록에 없으면 "등록되지 않은 태그입니다" 경고를 보여 주고 조회는 그대로 한다.
- 근거: `search.php:251-260`
- 근거 코드:
  ```php
  if ($opt['value'] !== '' && $opt['value'] === $tag) {
  ...
  $warnings[] = '등록되지 않은 태그입니다: ' . $tag;
  ```
- 확신도: 확실
- 비고: 목록 대조는 PHP `===`(대소문자 구분), SQL 조회는 `unicode_ci` 콜레이션이라, 대소문자만 다른 태그는 "등록되지 않은 태그" 경고와 함께 결과가 나올 수 있다 — 미검증.

### BR-15
- 규칙: 키워드가 한 글자이거나 `%` · `_` 를 포함하면 경고만 보여 주고 조회는 그대로 한다.
- 근거: `search.php:91-96`
- 근거 코드:
  ```php
  if (mb_strlen($q, 'UTF-8') === 1) {
      $warnings[] = '키워드가 한 글자라 결과가 많을 수 있습니다.';
  ```
- 확신도: 확실

---

## 3. 문항 검색 — 정렬

### BR-16
- 규칙: 정렬 기준(`sort`)이 비어 있으면 난이도 내림차순, 같은 난이도면 id 오름차순으로 정렬한다.
- 근거: `search.php:317-319`
- 근거 코드:
  ```php
  case '':
      // 기본 정렬: 어려운 문항부터, 같은 난이도면 번호 순
      $orderBy = " ORDER BY level DESC, id ASC";
  ```
- 확신도: 확실
- 비고: 주석과 코드 일치.

### BR-17
- 규칙: 정렬 기준이 `id` · `title` · `unit` · `level` · `created` 가 아니면 경고를 보여 주고 기본 정렬(BR-16)을 쓴다.
- 근거: `search.php:322-325`
- 근거 코드:
  ```php
  default:
      $warnings[] = '알 수 없는 정렬 기준입니다. 기본 정렬을 사용합니다.';
      $orderBy = " ORDER BY level DESC, id ASC";
  ```
- 확신도: 확실
- 비고: 기본 정렬 SQL 문자열이 `:319` 와 `:325` 두 곳에 중복돼 있다. `sort` · `dir` 는 소문자로 바꿔 비교한다(`search.php:266-267`).

### BR-18
- 규칙: 방향(`dir`)을 주지 않으면 `level` · `created` 는 내림차순, `id` · `title` · `unit` 은 오름차순으로 정렬한다.
- 근거: `search.php:278-314`, `:330`
- 근거 코드:
  ```php
  if ($dir === 'desc') { $orderBy = " ORDER BY id DESC"; } else { ... ASC }       // id · title · unit
  if ($dir === 'asc')  { $orderBy = " ORDER BY level ASC, id ASC"; } else { ... DESC }  // level · created
  $dir = ($sort === 'level' || $sort === 'created') ? 'desc' : 'asc';
  ```
- 확신도: 확실
- 비고: 인용한 첫 두 줄은 `:278-282` · `:302-306` 구조를 한 줄로 줄인 것이다. `:330` 은 화면 표시용 방향값을 맞추는 코드이고, SQL 방향은 각 `case` 의 `if/else` 가 정한다 — 두 곳이 같은 기본값을 따로 구현한다.

### BR-19
- 규칙: `dir` 이 `asc` · `desc` 가 아니면 경고를 보여 주고 방향을 주지 않은 것으로 처리한다.
- 근거: `search.php:268-273`
- 근거 코드:
  ```php
  if ($dir !== 'asc' && $dir !== 'desc') {
      if ($dir !== '') {
          $warnings[] = '정렬 방향은 asc 또는 desc 만 가능합니다.';
  ```
- 확신도: 확실

### BR-20
- 규칙: `id` 외의 정렬은 동률일 때 id 오름차순을 보조 정렬로 쓰고, `unit` 정렬은 방향과 무관하게 난이도 내림차순을 2차 기준으로 쓴다.
- 근거: `search.php:287`, `:289`, `:295`, `:297`, `:303`, `:305`, `:311`, `:313`
- 근거 코드:
  ```php
  $orderBy = " ORDER BY unit_code DESC, level DESC, id ASC";
  $orderBy = " ORDER BY unit_code ASC, level DESC, id ASC";
  ```
- 확신도: 확실
- 비고: `dir=desc` 여도 보조 정렬 `id` 는 항상 ASC 다. 열 머리글 링크는 `id` · `title` · `unit` · `level` 만 만들고(`search.php:392-397`) `created` 링크는 없다 — `created` 정렬은 URL 로만 가능하다. 난이도 머리글은 첫 클릭 시 `desc`(`search.php:410-412`).

---

## 4. 문항 검색 — 페이지

### BR-21
- 규칙: 검색 결과는 한 페이지 20건이고, 페이지 번호가 숫자가 아니거나 1 미만이면 1페이지, 999 를 넘으면 999페이지로 고정한다.
- 근거: `search.php:337-349`, `:523`, `:553`
- 근거 코드:
  ```php
  if ($page > 999) {
      $page = 999;
  $offset = ($page - 1) * 20;
  ```
- 확신도: 확실
- 비고: 20 이 `:349`(offset), `:523`(LIMIT), `:553`(page_size) 세 곳에 따로 박혀 있다. 숫자가 아닌 값은 경고가 나오지만(`:340`) `page=0` 은 경고 없이 1 로 바뀐다(`:342-344`). 999 상한 이유는 코드 · 주석에 없다. 마지막 페이지를 넘는 번호는 막지 않는다 — 건수는 나오고 표 본문이 빈 채로 출력된다(`search.php:657`, `:672`, 코드 읽기로 판단). 결과가 0건이면 "검색 결과가 없습니다"(`search.php:657-658`).

---

## 5. 문항 등록 — 검증

모든 검증은 POST 값을 `trim` 한 뒤 수행한다(`register.php:41-44`). 오류가 하나라도 있으면 저장하지 않는다(`register.php:83`).

### BR-22
- 규칙: 제목이 5자 미만이거나 200자를 넘으면 등록하지 않는다.
- 근거: `register.php:49-54`
- 근거 코드:
  ```php
  if (mb_strlen($title, 'UTF-8') < 5) {
  if (mb_strlen($title, 'UTF-8') > 200) {
  ```
- 확신도: 확실
- 비고: 글자 수(멀티바이트) 기준이다. 주석 `register.php:48` "공백 제외 아님, 앞뒤 공백만 제거"는 코드와 일치한다(가운데 공백은 센다). 200 은 스키마 `title VARCHAR(200)`(`01-schema.sql:23`)과 같은 값. 폼 라벨은 "제목 (5자 이상)"만 안내하고 200자 상한은 안내하지 않는다(`register.php:143`).

### BR-23
- 규칙: 지문이 비어 있으면(공백만 있으면 포함) 등록하지 않는다.
- 근거: `register.php:42`, `:56-58`
- 근거 코드:
  ```php
  if ($stem === '') {
      $errors[] = '지문을 입력해야 합니다.';
  ```
- 확신도: 확실
- 비고: 지문 길이 상한 검사는 없다(스키마는 `TEXT`).

### BR-24
- 규칙: 단원 id 가 `unit` 테이블 목록의 id 와 문자열로 정확히 같지 않으면 등록하지 않는다.
- 근거: `register.php:27`, `:60-68`
- 근거 코드:
  ```php
  if ((string)$u['id'] === $unitId) {
      $unitOk = true;
  ```
- 확신도: 확실
- 비고: 빈 값과 없는 id 가 같은 메시지 "단원을 선택해야 합니다."를 낸다. 문자열 비교라 `01` 처럼 앞에 0 이 붙은 값은 거부된다.

### BR-25
- 규칙: 난이도가 1~5 중 한 자리 숫자가 아니면 등록하지 않는다.
- 근거: `register.php:70-72`
- 근거 코드:
  ```php
  if (!preg_match('/^[1-5]$/', $level)) {
      $errors[] = '난이도는 1~5 사이여야 합니다.';
  ```
- 확신도: 확실
- 비고: 같은 정규식이 검색(`search.php:217`)에도 있지만 처리가 다르다 — 등록은 거부, 검색은 경고 후 `(int)` 변환해 조회(BR-08). DB 에는 CHECK 제약이 없고 `-- 1~5` 주석만 있다(`01-schema.sql:25`).

### BR-26
- 규칙: 태그 id 중 `tag` 테이블 목록에 없는 값은 오류 없이 버리고, 태그는 하나도 선택하지 않아도 등록한다.
- 근거: `register.php:45`, `:74-81`, `:104`
- 근거 코드:
  ```php
  if ((string)$t['id'] === (string)$tid) {
      $validTagIds[] = (int)$tid;
  if (!empty($validTagIds)) {
  ```
- 확신도: 확실
- 비고: 주석 `register.php:73` "태그는 목록에 있는 것만"과 일치하나, 사용자에게 알리지 않고 버린다. `tags` 가 배열이 아니면 빈 배열로 본다(`:45`). 같은 태그 id 가 두 번 오면(`tags[]=1&tags[]=1`) 중복 제거 없이 두 번 INSERT 해 `item_tag` PK 위반 → 전체 롤백되고 "저장 중 오류가 발생했습니다."가 나온다(코드 읽기로 판단, 미실행).

---

## 6. 문항 등록 — 저장

### BR-27
- 규칙: 새 문항 id 는 `item` 의 최대 id + 1 로 정하고, 문항이 하나도 없으면 1 로 정한다.
- 근거: `register.php:87`, `:90`
- 근거 코드:
  ```php
  $res = $conn->query("SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM item FOR UPDATE");
  $newId = (int)$row['next_id'];
  ```
- 확신도: 확실
- 비고: 상태와 무관하게 전체 `item` 에서 MAX 를 구한다(삭제 'D' 문항 포함). 동시 등록 시 잠금 범위는 미검증(ERD.md 미확인).

### BR-28
- 규칙: 문항과 문항-태그 저장 중 하나라도 실패하면 전체를 롤백하고 "저장 중 오류가 발생했습니다."를 보여 준다.
- 근거: `register.php:85`, `:99-101`, `:108-110`, `:114`, `:119-122`
- 근거 코드:
  ```php
  $conn->begin_transaction();
  } catch (Exception $e) {
      $conn->rollback();
  ```
- 확신도: 확실

### BR-29
- 규칙: 등록 시 `created_at` 과 `updated_at` 은 DB 서버의 현재 시각(`NOW()`)으로 같은 값을 넣는다.
- 근거: `register.php:94`
- 근거 코드:
  ```php
  VALUES (?, ?, ?, ?, ?, 'R', NOW(), NOW())"
  ```
- 확신도: 확실
- 비고: 등록에 성공하면 입력 폼을 비운다(`register.php:117-118`).

---

## 7. 단원 목록

### BR-30
- 규칙: 단원 목록은 모든 단원을 학년 오름차순, 같은 학년이면 코드 오름차순으로 보여 주고, 단원마다 `status = 'A'` 문항 수를 난이도와 무관하게 센다.
- 근거: `units.php:16-19`
- 근거 코드:
  ```php
  (SELECT COUNT(*) FROM item i WHERE i.unit_id = u.id AND i.status = 'A') AS item_count
    FROM unit u
   ORDER BY u.grade ASC, u.code ASC";
  ```
- 확신도: 확실
- 비고: 문항이 0건인 단원도 나온다(서브쿼리 방식). 난이도 조건이 없어 난이도 5 를 포함한다 — 조건 없는 검색(BR-06)의 건수와 다르다. `grade ASC, code ASC` 정렬이 `register.php:27`, `search.php:156` 에도 똑같이 있다(3곳 중복). 태그 선택 목록은 `id ASC`(`register.php:34`, `search.php:178`).

---

## 8. 수정

### BR-31
- 규칙: (규칙 없음) 이 모듈에는 문항 수정 · 삭제 · 상태 변경 코드가 없다.
- 근거: `legacy/item-bank-php/` 전체에서 `UPDATE` 는 `register.php:87` 의 `FOR UPDATE`(잠금 절) 하나뿐이고 `DELETE` 는 없다.
- 근거 코드:
  ```php
  $res = $conn->query("SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM item FOR UPDATE");
  ```
- 확신도: 확실
- 비고: 요청에 있던 "수정 시의 검증"은 뽑을 것이 없다. `updated_at` 은 등록 때만 쓰이고 이 모듈에서 읽지도 않는다.

---

## 매직 넘버

| 값 | 위치 | 추정 의미 |
|---|---|---|
| `5` (`level < 5`) | `search.php:215` | 난이도 미지정 시 제외할 최상 난이도. 주석은 "1~5 모두 포함"이라 의도는 불명 (BR-06) |
| `1`~`5` (`/^[1-5]$/`) | `search.php:217`, `register.php:70`, `register.php:163` (`$i <= 5`), `search.php:193-199` | 난이도 범위 |
| `'1 (매우 쉬움)'` ~ `'5 (최상)'` | `search.php:193-199` | 난이도 숫자 → 표시 라벨 매핑. 검색 화면에만 있고 등록 화면은 숫자만 표시 |
| `5` | `register.php:49`, `:143` | 제목 최소 글자 수 |
| `200` | `register.php:52` | 제목 최대 글자 수 = `title VARCHAR(200)` |
| `100` | `search.php:86-87`, `:494` (`maxlength`) | 키워드 최대 글자 수 |
| `1` | `search.php:91` | "결과가 많을 수 있다" 경고를 낼 키워드 길이 |
| `50` | `search.php:240-241` | 태그 이름 최대 글자 수 = `tag.name VARCHAR(50)` |
| `20` | `search.php:349`, `:523`, `:553` | 페이지당 건수 (세 곳 중복) |
| `999` | `search.php:345-346` | 페이지 번호 상한. 근거 없음 |
| `/^[A-Za-z][0-9]{1,2}-[0-9]{1,2}$/` | `search.php:111` | 단원 코드 형식 (예: `M5-1`, 학교급 + 학년 - 단원 번호로 추정) |
| `'A'` | `01-schema.sql:26`, `:70`, `units.php:17` | 공개 상태 (의미는 주석만 있음) |
| `'R'` | `register.php:94` | 검수중 상태 (의미는 주석만 있음) |
| `'D'` | `01-schema.sql:26` (주석) | 삭제 상태. 코드에서는 쓰지 않음 — 주석만 있음 |
| `0` (`COALESCE(MAX(id), 0) + 1`) | `register.php:87` | 첫 문항 id 를 1 로 만들기 위한 초기값 |

## 주석만 있는 내용 (규칙으로 올리지 않음)

| 주석 | 위치 | 코드 상태 |
|---|---|---|
| `A=공개 D=삭제 R=검수중` | `01-schema.sql:26` | 'A' · 'R' 은 쓰지만 의미 · 'D' 는 주석뿐 |
| "검수 완료 전까지 검색 화면에 나오지 않는다" | `register.php:84` | 'R' 저장 + 뷰 'A' 필터로 결과는 맞지만 "검수 완료" 절차 코드는 없음 |
| "등록 화면도 이 뷰를 기준으로 삼는다" | `01-schema.sql:51` | 코드와 다름 — `register.php` 는 뷰를 쓰지 않음 |
| "노출 여부는 삭제 플래그가 아니라 status 코드로 판정한다" | `01-schema.sql:50` | `status = 'A'` 필터와 일치. 삭제 플래그 컬럼은 스키마에 없음 |
| `level -- 1~5` | `01-schema.sql:25` | DB 제약 없음. 앱 코드(BR-25)에서만 검증 |

## 주석과 코드가 다른 곳

| 위치 | 주석 | 코드 | 규칙 |
|---|---|---|---|
| `search.php:213` · `:8` | 난이도 비면 "1~5 모두 포함" · "빈값 허용" | `level < 5` — 난이도 5 제외 | BR-06 |
| `search.php:224` | 1~5 밖의 값은 "대개 0건" | `(int)` 변환으로 `3abc` · `3.5` · `03` → 3 이 조회됨 (실행 확인) | BR-08 |
| `search.php:116` | "입력값 그대로 조회합니다" | `unicode_ci` 콜레이션으로 대소문자 무시 비교 — `m5-1` 이 `M5-1` 과 같은 5건 (실행 확인) | BR-09 |
| `01-schema.sql:51` | 등록 화면도 뷰 기준 | 등록 화면은 뷰 미참조 | BR-02 |

## 같은 규칙이 여러 곳에 다르게 구현된 경우

| 규칙 | 구현 위치 | 차이 |
|---|---|---|
| 공개 문항 판정 | 뷰 `01-schema.sql:70` / `units.php:17` | 뷰는 `unit` JOIN + status, 단원 목록은 `item` 직접 + status. 검색은 추가로 난이도 5 제외(BR-06) → 건수 불일치 |
| 난이도 1~5 검증 | `register.php:70` / `search.php:217` | 등록은 거부, 검색은 경고 후 정수 변환 조회 |
| 단원 식별 | `register.php:62` (id) / `search.php:118` (code) | 등록은 id, 검색은 코드 |
| 태그 식별 | `register.php:77` (id) / `search.php:245` (name) | 등록은 id, 검색은 이름 |
| 기본 정렬 | `search.php:319` / `:325` | 같은 SQL 두 벌 |
| 기본 정렬 방향 | 각 `case` 의 `if/else` (`search.php:278-314`) / `search.php:330` | SQL 방향과 화면 표시 방향을 따로 계산 |
| 페이지 크기 20 | `search.php:349` / `:523` / `:553` | 세 곳 하드코딩 |
| 단원 정렬 `grade, code` | `units.php:19` / `register.php:27` / `search.php:156` | 세 곳 중복 |

## 미확인

| 항목 | 확인할 곳 |
|---|---|
| 난이도 5 제외가 의도(최상 난이도 숨김)인지 버그인지 | 업무 담당자 · 과거 커밋 이력 |
| `tag.name` 비교가 실제로 대소문자를 무시하는지(단원 코드는 BR-09 에서 확인됨) | 레거시 :8081 에 `search.php?tag=<대소문자만 다른 이름>` 요청 |
| 중복 태그 id 등록 시 롤백되는지 | 레거시 :8081 에 POST (쓰기이므로 시드 복원 가능한 환경에서만) |

## 검증 이력

| 날짜 | 규칙 | 관련 교차 검증 | 바뀐 내용 | 방법 |
|---|---|---|---|---|
| 2026-09-29 | BR-01 | CX-10 (§4 "어떤 status 가 노출되는지 미확인") | 규칙 유지. 근거에 `search.php:521-522` · `:526` 추가, 시드의 `R` · `D` 문항 4건이 목록 · 건수에서 빠지는 것을 실행 결과로 추가 | GET :8081 `search.php` + `readonly` SELECT |
| 2026-09-29 | BR-02 | CX-22 (노출 여부 "§4 참고") | 규칙 유지. 근거를 `register.php:92-95` 로 넓히고 뷰 조건 `01-schema.sql:70` 인용 추가. 확인 범위(POST 미실행)를 명시 | 코드 읽기 + BR-01 실행 결과 |
| 2026-09-29 | BR-08 | CX-06 (주석과 같은 "대개 0건" 결론) | 규칙 문장에 "앞자리가 1~5 인 값은 그 난이도로 조회" 추가, 근거를 `:223-229` 로, "미실행" → 실행 확인 | GET :8081 `level=3` · `3abc` · `3.5` · `03` · `abc` |
| 2026-09-29 | BR-09 | CX-03 (§4 collation 미확인) | 규칙 문장을 "대소문자를 구분하지 않고 비교"로 고침, 근거에 `search.php:115-116` · `01-schema.sql:18` 추가, "미검증" → 실행 확인 | GET :8081 `unit=M5-1` · `m5-1` + `readonly` `COLLATION(code)` |
| 2026-09-29 | BR-11 | CX-03 파생 | 비고 추가: 단원 존재 확인 조회도 대소문자 무시 | GET :8081 `unit=m5-1` |
| 2026-09-29 | — | — | 실행하지 않은 것: 등록 POST(쓰기), 태그 이름 대소문자 비교(BR-14) | — |
