# 문항 은행(PHP) 비즈니스 규칙 — 코드 기반 교차 검증용

- 작성 방식: `legacy/item-bank-php/` 의 PHP 코드만 읽고 추출했다. `docs/` 아래 문서와 `vendor/` 는 읽지 않았다.
- 읽은 파일: `search.php`, `register.php`, `units.php`, `index.php`, `inc/db.php`
- 기준: 주석과 코드가 다르면 코드를 따른다. 코드에서 확인되지 않는 것은 규칙으로 쓰지 않고 §4 에 따로 적었다.
- 줄번호는 읽은 시점의 파일 기준이다. 경로는 `legacy/item-bank-php/` 기준 상대 경로.

## 1. 검색 (`search.php`)

### CX-01 키워드는 제목 또는 지문에 부분 일치한다
앞뒤 공백을 제거한 키워드를 `%키워드%` 로 만들어 `title` 또는 `stem` 중 하나에 맞으면 통과시킨다. `%` · `_` 는 이스케이프하지 않아 와일드카드로 동작한다(경고만 표시).
- 근거: `search.php:85`, `search.php:97-98`, `search.php:94-96`
```php
$like = '%' . $q . '%';
$where .= " AND (title LIKE ? OR stem LIKE ?)";
```

### CX-02 키워드는 100자까지만 사용한다
100자를 넘으면 잘라서 검색하고 경고를 남긴다. 검색을 막지는 않는다.
- 근거: `search.php:86-89`
```php
if (mb_strlen($q, 'UTF-8') > 100) {
    $q = mb_substr($q, 0, 100, 'UTF-8');
```

### CX-03 단원은 단원 코드와 정확히 일치해야 한다
`unit_code = ?` 로 비교하며 대소문자를 변환하지 않고 입력값 그대로 조회한다. 형식(`M5-1` 꼴)이 다르거나 소문자이거나 등록되지 않은 코드여도 경고만 하고 조회는 실행한다.
- 근거: `search.php:109-120` (형식 검사 `111`, 대문자 검사 `115`, 미등록 경고 `141-143`)
```php
$where .= " AND unit_code = ?";
$types .= 's';
$values[] = $unit;
```

### CX-04 난이도를 비우면 5단계 문항이 제외된다
난이도 파라미터가 빈 문자열이면 `level < 5` 조건이 붙어 난이도 1~4 문항만 나온다. 난이도 5 문항은 난이도를 명시(`level=5`)해야만 검색된다. **주석은 "1~5 모두 포함"이라고 하지만 코드는 5를 제외한다.** 코드를 기준으로 썼다.
- 근거: `search.php:213-216`
```php
// 난이도 값이 비어 있으면 전체 난이도 검색 (1~5 모두 포함)
if ($level == '') {
    $where .= " AND level < 5";
```

### CX-05 난이도가 1~5 이면 그 값과 같은 문항만 나온다
`^[1-5]$` 에 맞는 값은 `level = ?` 정수 비교로 처리한다.
- 근거: `search.php:217-221`
```php
else if (preg_match('/^[1-5]$/', $level)) {
    $where .= " AND level = ?";
    $values[] = (int)$level;
```

### CX-06 1~5 밖의 난이도는 거부하지 않고 정수로 바꿔 비교한다
경고만 표시하고 `(int)$level` 로 `level = ?` 조회를 실행한다. 숫자가 아닌 값은 PHP 정수 변환으로 0 이 되어 대개 0건이다. 이때는 CX-04 의 `level < 5` 조건이 붙지 않는다.
- 근거: `search.php:223-229`
```php
$warnings[] = '난이도는 1~5 사이여야 합니다.';
$where .= " AND level = ?";
$values[] = (int)$level;
```

### CX-07 태그는 이름이 정확히 같은 태그가 달린 문항만 나온다
`item_tag` · `tag` 를 조인한 `EXISTS` 로 `t.name = ?` 일치를 본다. 부분 일치는 지원하지 않고(`%` · `_` 는 경고), 50자를 넘으면 50자로 자른다. 등록되지 않은 태그도 경고만 하고 조회한다.
- 근거: `search.php:235-248`, 미등록 경고 `search.php:258-260`
```php
$where .= " AND EXISTS (SELECT 1 FROM item_tag it JOIN tag t ON t.id = it.tag_id"
        . " WHERE it.item_id = v_item_public.id AND t.name = ?)";
```

### CX-08 태그는 하나만 받는다
`q` · `unit` · `level` · `tag` 등 모든 파라미터가 배열로 들어오면 첫 값만 쓴다. 따라서 태그 여러 개로 AND/OR 검색하는 기능은 없다.
- 근거: `search.php:51-68`
```php
$tag = is_array($params['tag']) ? (string)reset($params['tag']) : (string)$params['tag'];
```

### CX-09 조건들은 모두 AND 로 결합되고 건수 쿼리도 같은 조건을 쓴다
`WHERE 1=1` 뒤에 키워드 · 단원 · 난이도 · 태그 조건이 `AND` 로 이어진다. 목록 SQL 과 `COUNT(*)` SQL 이 같은 `$where` 를 공유한다.
- 근거: `search.php:44`, `search.php:525-526`
```php
$sql      = $select . $from . $where . $orderBy . $limit;
$countSql = "SELECT COUNT(*) AS cnt" . $from . $where;
```

### CX-10 검색 대상은 `item` 이 아니라 뷰 `v_item_public` 이다
목록 · 건수 모두 `FROM v_item_public` 에서 읽는다. 어떤 문항이 이 뷰에서 빠지는지는 뷰 정의(DB 스크립트)에 있어 이 폴더의 코드로는 확인되지 않는다. §4 참고.
- 근거: `search.php:521-522`
```php
$select = "SELECT id, title, unit_code, level, tag_names, created_at";
$from   = " FROM v_item_public";
```

### CX-11 기본 정렬은 난이도 내림차순, 같으면 ID 오름차순이다
`sort` 를 비우면 어려운 문항부터, 같은 난이도면 번호 순이다.
- 근거: `search.php:317-319`
```php
case '':
    // 기본 정렬: 어려운 문항부터, 같은 난이도면 번호 순
    $orderBy = " ORDER BY level DESC, id ASC";
```

### CX-12 알 수 없는 정렬 기준은 기본 정렬로 대체한다
`id | title | unit | level | created` 외의 `sort` 값은 경고 후 CX-11 과 같은 정렬을 쓴다.
- 근거: `search.php:322-326`
```php
default:
    $warnings[] = '알 수 없는 정렬 기준입니다. 기본 정렬을 사용합니다.';
    $sort = '';
```

### CX-13 정렬 방향은 기준마다 기본값이 다르고, 보조 정렬이 붙는다
`dir` 은 `asc` · `desc` 만 유효하고 그 외는 무시한다(값이 있으면 경고).
- `id`: 기본 오름차순, 보조 정렬 없음 (`search.php:277-283`)
- `title`: 기본 오름차순, 보조 `id ASC` (`285-291`)
- `unit`: 기본 오름차순, 보조 `level DESC, id ASC` — 방향과 무관하게 난이도는 내림차순 (`293-299`)
- `level`: `asc` 일 때만 오름차순, 그 외(미지정 포함)는 내림차순 (`301-307`)
- `created`: `asc` 일 때만 오름차순, 그 외는 내림차순 (`309-315`)
- 근거 인용:
```php
$orderBy = " ORDER BY unit_code DESC, level DESC, id ASC";   // search.php:295
$orderBy = " ORDER BY created_at DESC, id ASC";              // search.php:313
```

### CX-14 한 페이지는 20건이고 페이지 번호는 1~999 로 보정된다
숫자가 아닌 페이지는 1 로 보고 경고한다(단 빈 값과 `1` 은 경고 없음). 1 미만은 1, 999 초과는 999 로 조정한다. `LIMIT 20 OFFSET (page-1)*20`.
- 근거: `search.php:336-349`, `search.php:523`
```php
if ($page > 999) {
    $page = 999;
$limit  = " LIMIT 20 OFFSET " . (int)$offset;
```

### CX-15 입력값 문제는 경고만 남기고 검색은 계속한다
키워드 길이 · 단원 형식 · 미등록 단원/태그 · 난이도 범위 · 정렬 · 페이지 오류는 모두 `$warnings` 에 쌓여 화면에 표시될 뿐 조회를 막지 않는다. 검색을 중단하는 예외는 바인딩 개수 불일치뿐이다.
- 근거: `search.php:536-539` (중단), 나머지는 CX-02~07, CX-12, CX-14 의 각 줄
```php
if (strlen($types) !== count($values)) {
    throw new RuntimeException('검색 조건 조립 오류');
```

## 2. 등록 (`register.php`)

### CX-16 제목은 앞뒤 공백 제거 후 5자 이상 200자 이하다
글자 수는 `mb_strlen`(UTF-8) 기준이고 가운데 공백은 세지 않도록 하는 처리가 없다(앞뒤 공백만 제거).
- 근거: `register.php:41`, `register.php:49-54`
```php
if (mb_strlen($title, 'UTF-8') < 5) {
if (mb_strlen($title, 'UTF-8') > 200) {
```

### CX-17 지문은 필수다
앞뒤 공백 제거 후 빈 문자열이면 오류다. 길이 상한은 없다.
- 근거: `register.php:42`, `register.php:56-58`
```php
if ($stem === '') {
    $errors[] = '지문을 입력해야 합니다.';
```

### CX-18 단원은 필수이며 `unit` 테이블에 있는 ID 여야 한다
폼 값(`unit_id`)을 단원 목록의 `id` 와 문자열 비교해 하나도 맞지 않으면 오류다. 등록은 단원 **ID** 를 쓰고 검색은 단원 **코드**를 쓴다.
- 근거: `register.php:27`, `register.php:60-68`
```php
if ((string)$u['id'] === $unitId) {
    $unitOk = true;
```

### CX-19 난이도는 1~5 정수만 허용한다
정확히 한 자리 `1`~`5` 가 아니면 오류다(검색과 달리 거부한다).
- 근거: `register.php:70-72`
```php
if (!preg_match('/^[1-5]$/', $level)) {
    $errors[] = '난이도는 1~5 사이여야 합니다.';
```

### CX-20 태그는 선택이며, 목록에 없는 태그 ID 는 오류 없이 버려진다
`tags[]` 중 `tag` 테이블에 있는 ID 만 저장 대상이 된다. 없는 ID 는 오류 없이 무시한다. 태그를 하나도 안 골라도 등록된다.
- 근거: `register.php:74-81`, `register.php:104`
```php
if ((string)$t['id'] === (string)$tid) {
    $validTagIds[] = (int)$tid;
```

### CX-21 검증 오류가 하나라도 있으면 저장하지 않고, 오류는 모두 모아서 보여 준다
검증은 중간에 멈추지 않고 전부 수행해 `$errors` 에 쌓고, 비어 있을 때만 저장 블록으로 들어간다.
- 근거: `register.php:49-71`, `register.php:83`
```php
if (empty($errors)) {
```

### CX-22 새 문항은 검수중(`R`) 상태로 저장된다
`INSERT` 가 `status` 에 `'R'` 을 고정으로 넣는다. 화면 안내문은 "검수 완료 후 검색에 노출"이라고 한다(노출 여부의 실제 필터는 §4 참고).
- 근거: `register.php:92-95`, 안내문 `register.php:116`
```php
"INSERT INTO item (id, unit_id, title, stem, level, status, created_at, updated_at)
 VALUES (?, ?, ?, ?, ?, 'R', NOW(), NOW())"
```

### CX-23 문항 ID 는 현재 최대 ID + 1 로 부여하고, 문항과 태그는 한 트랜잭션으로 저장한다
`item` 에 `FOR UPDATE` 로 `MAX(id)+1` 을 구하고, 문항 · 태그 저장 중 하나라도 실패하면 롤백한다.
- 근거: `register.php:85-90`, `register.php:114`, `register.php:119-123`
```php
$res = $conn->query("SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM item FOR UPDATE");
```

## 3. 목록 제외 조건 (`units.php`)

### CX-24 단원별 "공개 문항 수"는 상태 `A` 인 문항만 센다
`status = 'A'` 만 세며 삭제 · 검수중 문항은 세지 않는다고 주석에 있고, 코드는 `A` 만 세는 것까지 확인된다(삭제 상태 코드는 이 폴더에서 확인되지 않음).
- 근거: `units.php:15-17`
```php
// 단원별 공개(status='A') 문항 수. 삭제 · 검수중 문항은 세지 않는다.
(SELECT COUNT(*) FROM item i WHERE i.unit_id = u.id AND i.status = 'A') AS item_count
```

## 4. 코드만으로 확인되지 않은 것 / 주석과 코드의 불일치

| 구분 | 내용 | 위치 |
|---|---|---|
| **불일치** | 난이도 빈값 주석은 "1~5 모두 포함"인데 코드는 `level < 5` 로 5단계를 제외한다 → 코드 기준으로 CX-04 에 기록 | `search.php:213-216` |
| **미확인** | 검색이 읽는 `v_item_public` 의 정의(어떤 `status` 가 노출되는지)는 이 폴더에 없다. "검수중(R)은 검색에 안 나온다"는 `register.php:84` 주석과 `units.php:15` 주석의 서술일 뿐, 검색 코드 자체에는 `status` 조건이 없다 | `search.php:521-522` |
| **미확인** | 단원 코드 대소문자 비교 결과는 DB 컬럼 collation 에 좌우된다. PHP 는 변환 없이 `=` 로 넘긴다 | `search.php:118` |
| **범위 밖** | 문항 **수정** 기능은 이 폴더에 없다. 파일은 `index.php` · `search.php` · `register.php` · `units.php` 뿐이고 `UPDATE` / 삭제 SQL 이 없다. 요청의 "등록 · 수정 시 검증" 중 수정 규칙은 추출할 코드가 없어 쓰지 않았다 | 폴더 전체 |
| **참고** | 폼 안내 문구 "제목 (5자 이상)"은 있으나 200자 상한은 화면에 안내되지 않는다 | `register.php:143` |
