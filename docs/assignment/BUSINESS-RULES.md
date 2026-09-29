# 과제 배포 (legacy/assignment-thymeleaf) 비즈니스 규칙 후보 — "새 배포" 화면

- 범위: `GET /distributions/new` · `POST /distributions` 와 그 호출 코드 (`ARCHITECTURE.md` "범위" 절). 재배포 · 배포 목록은 범위 밖.
- 경로 표기: `DistributionController.java` 등 Java 파일은 `legacy/assignment-thymeleaf/src/main/java/com/example/assign/` 아래, 템플릿은 `.../src/main/resources/templates/` 아래.
- 확신도: 앱을 실행하지 않았으므로 앱 동작은 모두 "높음(코드 읽기)" 이하다. "확실(실행 확인)" 은 MariaDB `readonly` 조회로 확인한 DB 쪽 사실에만 쓴다.

## 먼저 볼 것

| 구분 | 내용 | 규칙 |
|---|---|---|
| 코드 밖에서 정해지는 동작 | 현재 시각이 **`2026-09-15 00:00:00` 고정**이다. `-Dapp.clock=system` 이 없으면 고정이고, Dockerfile · compose 에 그 설정이 없다. 마감 판단 · 배포 시각 저장이 모두 이 값을 쓴다 | BR-04, BR-06, BR-12 |
| 주석-코드 불일치 | `AppClock` 주석은 "2024-03 QA 기간 중 임시로 고정 … 되돌릴 것 (아직 안 되돌림)" 인데 고정값은 `2026-09-15` 다 | BR-04 |
| 스키마 주석-코드 불일치 | 스키마는 상태를 `O=진행 C=마감` 만 정의하는데, 코드는 `X`(연장) · `D`(삭제)로 분기한다. 시드 · 현재 DB 에 `X` · `D` 가 없어 **연장 예외 · 삭제 차단 분기는 시드 데이터로 탈 수 없다** | BR-02, BR-06, BR-08 |
| 기능 없음 | 상태 `C`(마감)는 어디서도 막지 않는다. `C` 라도 `due_at` 이 미래면 배포된다 | BR-10 |
| 같은 규칙, 다른 구현 | "마감 지난 과제 불가" 가 템플릿(option `disabled`)과 컨트롤러에 따로 있다. 서버 쪽은 컨트롤러뿐이고 서비스에는 없다 | BR-02, BR-06 |
| 같은 규칙, 다른 구현 | 과제 존재 확인이 컨트롤러와 서비스에 두 번 있고 문구가 다르다. 서비스 쪽 문구는 사실상 나오지 않는다 | BR-05 |
| 콜레이션 vs Java 비교 | 드롭다운 SQL `status IN ('O','X','C')` 은 대소문자 무시(`utf8mb4_unicode_ci`, 실행 확인)지만, 연장 판단 `"X".equals` · `!= 'X'` 는 대소문자 구분이다. 소문자 `x` 과제는 목록에 뜨지만 연장으로 취급되지 않는다 | BR-01, BR-06 |
| 규칙-데이터 불일치 | "같은 과제+학급은 한 건만" 을 코드로만 막는데(UNIQUE 없음), 시드에 이미 (과제 2, 학급 1) 이 2건 있다 (실행 확인) | BR-09 |
| 검증 순서 | 마감 검사(컨트롤러)가 삭제 검사(서비스)보다 먼저라, 삭제 + 마감 경과 과제는 "마감" 문구를 받는다 | BR-06, BR-08 |
| 오류 처리 두 갈래 | 같은 DB 오류라도 try 안(서비스)이면 폼으로 돌아가 문구 표시, try 밖(첫 과제 조회 · 폼 표시)이면 `db_error` 화면 | BR-15 |

---

## 1. 폼에 보이는 목록

### BR-01
- 규칙: 과제 드롭다운에는 상태가 `O` · `X` · `C` 인 과제만 나오고(그 밖의 상태 — `D` 포함 — 는 빠진다), 마감 시각 오름차순 → id 오름차순으로 정렬된다.
- 근거: `dao/AssignmentDao.java:21-24`, `:34-39`, `web/DistributionController.java:42`, `db/mariadb/init/01-schema.sql:5`, `:90`
- 근거 코드:
  ```java
  + " FROM assignment a LEFT JOIN unit u ON u.id = a.unit_id ";   // AssignmentDao.java:24
  + " WHERE a.status IN ('O', 'X', 'C') "                         // AssignmentDao.java:36
  + " ORDER BY a.due_at ASC, a.id ASC";                           // AssignmentDao.java:37
  model.addAttribute("assignments", assignmentDao.findAllForSelect());   // DistributionController.java:42
  ```
  ```sql
  CREATE DATABASE IF NOT EXISTS itembank CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;   -- 01-schema.sql:5
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;                         -- 01-schema.sql:90 (assignment)
  ```
- 확신도: 높음 (코드 읽기). 콜레이션 때문에 소문자 `o` · `x` · `c` 도 포함되는 점은 확실 (실행 확인 — readonly `information_schema.COLUMNS` 에서 `assignment.status` 콜레이션 = `utf8mb4_unicode_ci`, `SELECT COUNT(*) FROM assignment WHERE status IN ('o','x','c')` → `6`).
- 비고: 목록 화면 `findAllForList` 는 `status <> 'D'` 로 거른다(`AssignmentDao.java:29`, 범위 밖) — 같은 "삭제 제외" 를 두 가지 조건으로 쓴다. 이 화면은 알 수 없는 상태값(예: `Z`)을 빼지만 목록 화면은 포함한다. 시드 기준 6건 모두 나온다(`02-seed.sql:120-125`).

### BR-02
- 규칙: 과제 option 은 `마감 < 현재 시각` 이고 상태가 `'X'` 가 아니면 `disabled` 가 되고 문구 끝에 ` [마감]` 이 붙는다. 마감이 지났어도 상태가 `'X'` 면 선택 가능하고 ` [연장]` 이 붙는다.
- 근거: `templates/distribution_form.html:13-18`, `web/DistributionController.java:44`
- 근거 코드:
  ```html
  <!-- 마감 지난 과제는 선택 불가 (연장 상태 제외) -->                                              <!-- distribution_form.html:13 -->
  th:disabled="${a.dueAt != null and a.dueAt.isBefore(now) and a.status != 'X'}"                <!-- :16 -->
  + (${a.dueAt != null and a.dueAt.isBefore(now)} ? (${a.status == 'X'} ? ' [연장]' : ' [마감]') : '')   <!-- :18 -->
  ```
  ```java
  model.addAttribute("now", AppClock.now());   // DistributionController.java:44
  ```
- 확신도: 높음 (코드 읽기)
- 비고: 브라우저 쪽 제한일 뿐이고 서버 검사는 BR-06 이다. `due_at` 은 `NOT NULL` 이라(`01-schema.sql:86`) `a.dueAt != null` 은 항상 참. `now` 는 고정 시각(BR-04)이라 시드 기준 과제 1 · 2 · 3 이 `[마감]`·비활성이고 4 · 5 · 6 은 선택 가능하다(`02-seed.sql:120-125`). 실제 시각(2026-09-29)이면 과제 4(09-25 마감)도 비활성이 된다. `X` 는 스키마에 정의가 없고 DB 에도 없다.

### BR-03
- 규칙: 학급 드롭다운에는 `class` 테이블의 모든 학급이 조건 없이 id 오름차순으로 나오고, 문구는 `학급명 (교사ID)` 다.
- 근거: `dao/ClassDao.java:20-22`, `web/DistributionController.java:43`, `templates/distribution_form.html:25`
- 근거 코드:
  ```java
  return jdbc.query("SELECT id, name, teacher_id FROM `class` ORDER BY id ASC", MAPPER);   // ClassDao.java:21
  ```
  ```html
  <option th:each="c : ${classes}" th:value="${c.id}" th:text="${c.name} + ' (' + ${c.teacherId} + ')'"></option>   <!-- distribution_form.html:25 -->
  ```
- 확신도: 높음 (코드 읽기)
- 비고: 로그인 · 교사별 필터가 없다(기능 없음). 이미 이 과제가 배포된 학급도 목록에서 빠지지 않고, 제출 시 BR-09 로 막힌다.

## 2. 현재 시각

### BR-04
- 규칙: 현재 시각은 시스템 속성 `app.clock` 이 정확히 `system` 일 때만 실제 시각(나노초 0)이고, 그 밖에는 항상 `2026-09-15 00:00:00` 이다.
- 근거: `AppClock.java:8-9`, `:14-20`, `Dockerfile:11`
- 근거 코드:
  ```java
  // 2024-03 QA 기간 중 임시로 고정. 운영 반영 전 LocalDateTime.now() 로 되돌릴 것 (아직 안 되돌림)   // AppClock.java:8
  private static final String FIXED = "2026-09-15 00:00:00";                                          // AppClock.java:9
  String sys = System.getProperty("app.clock");                                                       // AppClock.java:15
  if (sys != null && sys.equals("system")) {                                                          // AppClock.java:16
      return LocalDateTime.now().withNano(0);                                                         // AppClock.java:17
  return LocalDateTime.parse(FIXED, FMT);                                                             // AppClock.java:19
  ```
  ```dockerfile
  ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-jar", "/app/assignment.jar"]   # Dockerfile:11
  ```
- 확신도: 높음 (코드 읽기)
- 비고: **주석-코드 불일치** — 주석의 "2024-03" 과 고정값 "2026-09-15" 가 다르다. 비교는 `equals` 라 `System` · `SYSTEM` · 환경변수 `APP_CLOCK` 은 고정 시각이 된다(시스템 속성만 읽음). compose `assignment` 서비스에도 설정이 없다(`docker-compose.yml`). 시드 주석 "마감이 지난 과제: 1, 2, 3"(`02-seed.sql:117`)은 고정 시각 기준과 맞는다.

## 3. 배포 등록 검증 (`POST /distributions`)

검사 순서: BR-14(파라미터 변환) → BR-05 → BR-06 → (서비스) BR-05 재확인 → BR-07 → BR-08 → BR-09 → BR-11 · BR-12 → BR-13.

### BR-05
- 규칙: `assignmentId` 에 해당하는 과제가 없으면 flash 오류 "존재하지 않는 과제입니다." 와 함께 `/distributions/new` 로 돌아간다.
- 근거: `web/DistributionController.java:53-57`, `dao/AssignmentDao.java:41-48`, `service/DistributionService.java:48-51`
- 근거 코드:
  ```java
  AssignmentRow a = assignmentDao.findById(assignmentId);          // DistributionController.java:53
  if (a == null) {                                                 // :54
      ra.addFlashAttribute("error", "존재하지 않는 과제입니다.");      // :55
      return "redirect:/distributions/new";                        // :56
  String sql = BASE_SELECT + " WHERE a.id = ?";                    // AssignmentDao.java:42
  throw new IllegalArgumentException("존재하지 않는 과제입니다. (id=" + assignmentId + ")");   // DistributionService.java:50
  ```
- 확신도: 높음 (코드 읽기)
- 비고: **같은 검사 두 번** — 서비스(`:48-51`)가 같은 조회를 다시 하고 id 가 붙은 문구를 던지지만, 컨트롤러가 먼저 걸러서 두 조회 사이에 행이 사라지지 않는 한 나오지 않는다. 이 조회는 try 밖이라 DB 오류 시 BR-15 의 `db_error` 화면으로 간다.

### BR-06
- 규칙: 과제 마감이 현재 시각보다 **이전**이고 상태가 정확히 `"X"` 가 아니면 flash 오류 "마감(yyyy-MM-dd HH:mm)이 지난 과제는 배포할 수 없습니다. [과제 제목]" 과 함께 `/distributions/new` 로 돌아간다. 마감 == 현재 시각이면 통과한다.
- 근거: `web/DistributionController.java:58-65`, `AppClock.java:12`, `:22-27`
- 근거 코드:
  ```java
  LocalDateTime now = AppClock.now();                                     // DistributionController.java:58
  // 마감 지난 과제는 새 배포 불가. 연장(X) 상태만 예외                        // :59
  if (a.getDueAt() != null && a.getDueAt().isBefore(now)) {               // :60
      if (!"X".equals(a.getStatus())) {                                   // :61
          ra.addFlashAttribute("error", "마감(" + AppClock.fmt(a.getDueAt()) + ")이 지난 과제는 배포할 수 없습니다. [" + a.getTitle() + "]");   // :62
          return "redirect:/distributions/new";                           // :63
  public static final DateTimeFormatter FMT_SHORT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");   // AppClock.java:12
  ```
- 확신도: 높음 (코드 읽기)
- 비고: 서버 쪽 마감 검사는 여기 한 곳이다 — 서비스 `distribute()` 에는 없다(`DistributionService.java:46-68`). `now` 는 고정 시각(BR-04). `"X".equals` 는 대소문자 구분이라 소문자 `x` 과제는 드롭다운에 나오지만(BR-01, 콜레이션) 마감 후엔 비활성·거부된다. `due_at NOT NULL`(`01-schema.sql:86`)이라 `!= null` 조건은 항상 참. 템플릿(BR-02)과 같은 조건을 따로 구현했다. 이 검사가 BR-08(삭제)보다 앞이다.

### BR-07
- 규칙: `classId` 에 해당하는 학급이 없으면 flash 오류 "존재하지 않는 학급입니다. (id=<classId>)" 와 함께 `/distributions/new` 로 돌아간다.
- 근거: `service/DistributionService.java:52-55`, `dao/ClassDao.java:24-30`, `web/DistributionController.java:66-71`
- 근거 코드:
  ```java
  ClassRow c = classDao.findById(classId);                                            // DistributionService.java:52
  if (c == null) {                                                                    // :53
      throw new IllegalArgumentException("존재하지 않는 학급입니다. (id=" + classId + ")");   // :54
  } catch (IllegalArgumentException | IllegalStateException e) {                      // DistributionController.java:69
      ra.addFlashAttribute("error", e.getMessage());                                  // :70
      return "redirect:/distributions/new";                                           // :71
  ```
- 확신도: 높음 (코드 읽기)
- 비고: 학급 존재 확인은 서비스에만 있다(컨트롤러에는 과제 확인만). 과제 검사(BR-05 · BR-06)를 통과한 뒤에야 학급을 본다.

### BR-08
- 규칙: 과제 상태가 정확히 `"D"` 면 flash 오류 "삭제된 과제는 배포할 수 없습니다." 와 함께 `/distributions/new` 로 돌아간다.
- 근거: `service/DistributionService.java:56-58`, `web/DistributionController.java:69-71`
- 근거 코드:
  ```java
  if ("D".equals(a.getStatus())) {                                  // DistributionService.java:56
      throw new IllegalStateException("삭제된 과제는 배포할 수 없습니다.");   // :57
  ```
- 확신도: 높음 (코드 읽기)
- 비고: `D` 는 스키마 주석에 없고(`01-schema.sql:87`) DB 에도 없다(readonly 조회) → 시드로는 이 분기를 탈 수 없다. `D` 과제는 드롭다운에 없으므로(BR-01) 직접 POST 할 때만 닿는다. 마감이 지난 `D` 과제는 BR-06 에서 먼저 걸려 "마감" 문구를 받는다. `"D".equals` 는 대소문자 구분 — 소문자 `d` 는 통과한다(드롭다운에도 없음).

### BR-09
- 규칙: 같은 (과제, 학급) 조합의 `distribution` 행이 1건이라도 있으면(재배포 행 포함) flash 오류 "이미 이 학급에 배포된 과제입니다. 다시 내려면 배포 목록에서 재배포를 사용하세요." 와 함께 `/distributions/new` 로 돌아간다.
- 근거: `service/DistributionService.java:59-61`, `dao/DistributionDao.java:46-52`, `db/mariadb/init/01-schema.sql:92-103`
- 근거 코드:
  ```java
  if (distributionDao.countByAssignmentAndClass(assignmentId, classId) > 0) {   // DistributionService.java:59
      throw new IllegalStateException("이미 이 학급에 배포된 과제입니다. 다시 내려면 배포 목록에서 재배포를 사용하세요.");   // :60
  // 같은 학급 + 같은 과제 는 한 건만 존재해야 한다 (UNIQUE 제약은 없음, 여기서 막는다)   // DistributionDao.java:46
  "SELECT COUNT(*) FROM distribution WHERE assignment_id = ? AND class_id = ?",   // DistributionDao.java:49
  ```
  ```sql
  KEY idx_distribution_assignment (assignment_id),   -- 01-schema.sql:99
  KEY idx_distribution_class (class_id),             -- 01-schema.sql:100
  ```
- 확신도: 높음 (코드 읽기). "시드에 이미 (과제 2, 학급 1) 이 2건" 은 확실 (실행 확인 — readonly `GROUP BY assignment_id, class_id HAVING COUNT(*) > 1` → `2, 1, 2`, `02-seed.sql:133-134`).
- 비고: DB 제약이 없어 규칙은 이 COUNT 한 줄뿐이다. 주석 "한 건만 존재해야 한다" 와 시드가 어긋난다(시드 주석은 id 4 를 "재배포한 이력" 이라 함, `02-seed.sql:128`; 재배포 코드는 범위 밖). COUNT 와 INSERT 사이에 잠금이 없어 동시 요청 두 건이 모두 통과할 수 있다 — 추정, 낮음. 시드 기준 이미 배포된 조합: (1,1) (1,2) (2,1) (3,3) (4,3) (5,3) (6,2).

### BR-10
- 규칙: 과제 상태 `C`(스키마 주석상 "마감")는 등록을 막지 않는다. 서버가 상태로 막는 것은 `D`(BR-08)뿐이고, `C` 과제도 `due_at` 이 현재 시각 이후이고 중복이 아니면 배포된다.
- 근거: `web/DistributionController.java:58-65`, `service/DistributionService.java:46-68`, `db/mariadb/init/01-schema.sql:87`, `dao/AssignmentDao.java:36`
- 근거 코드:
  ```sql
  status  CHAR(1)      NOT NULL DEFAULT 'O',        -- O=진행 C=마감   -- 01-schema.sql:87
  ```
  ```java
  + " WHERE a.status IN ('O', 'X', 'C') "   // AssignmentDao.java:36 — C 가 드롭다운에 포함됨
  if ("D".equals(a.getStatus())) {          // DistributionService.java:56 — 상태 검사는 이것뿐
  ```
- 확신도: 높음 (코드 읽기)
- 비고: **기능 없음 성격**. "마감" 여부는 상태값이 아니라 `due_at` 과 고정 시각으로만 판단한다. 시드의 `C` 과제 1 · 2 는 `due_at` 도 지나 있어 BR-06 에 먼저 걸리므로 시드로는 차이가 드러나지 않는다.

## 4. 저장

### BR-11
- 규칙: 새 배포 id 는 `distribution` 의 `MAX(id) + 1`(행이 없으면 1)이다.
- 근거: `dao/DistributionDao.java:54-57`, `service/DistributionService.java:62`, `db/mariadb/init/01-schema.sql:93`, `:98`
- 근거 코드:
  ```java
  Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM distribution", Long.class);   // DistributionDao.java:55
  return (max == null ? 0L : max.longValue()) + 1L;                                              // DistributionDao.java:56
  ```
  ```sql
  id             INT      NOT NULL,   -- 01-schema.sql:93 (AUTO_INCREMENT 없음)
  ```
- 확신도: 높음 (코드 읽기)
- 비고: 시드 기준 다음 id 는 9. 동시 요청이면 같은 id 로 PK 충돌 → `DataAccessException` → "DB 오류로 배포에 실패했습니다."(BR-15) 가 될 것으로 보인다 — 추정, 낮음.

### BR-12
- 규칙: 배포 행은 `distributed_at = AppClock.now()`, `redistributed = 0` 으로 INSERT 된다.
- 근거: `service/DistributionService.java:63`, `dao/DistributionDao.java:59-63`, `AppClock.java:9`, `:19`
- 근거 코드:
  ```java
  int n = distributionDao.insert(id, assignmentId, classId, AppClock.now());   // DistributionService.java:63
  "INSERT INTO distribution (id, assignment_id, class_id, distributed_at, redistributed) VALUES (?, ?, ?, ?, 0)",   // DistributionDao.java:61
  id, assignmentId, classId, Timestamp.valueOf(distributedAt));                // DistributionDao.java:62
  ```
- 확신도: 높음 (코드 읽기)
- 비고: 고정 시각(BR-04)이면 이 화면으로 만든 배포는 전부 `2026-09-15 00:00:00` 이 된다. `Timestamp.valueOf` 는 JVM 기본 시간대를 따른다(미확인 — ARCHITECTURE 미확인 목록).

### BR-13
- 규칙: INSERT 영향 행 수가 1 이 아니면 "배포 저장에 실패했습니다." 오류로 `/distributions/new` 로 돌아가고, 성공하면 flash "배포가 등록되었습니다. (배포 #<id>)" 와 함께 `/distributions` 로 이동한다.
- 근거: `service/DistributionService.java:46`, `:63-67`, `web/DistributionController.java:66-76`
- 근거 코드:
  ```java
  @Transactional                                                            // DistributionService.java:46
  if (n != 1) {                                                             // :64
      throw new IllegalStateException("배포 저장에 실패했습니다.");            // :65
  long id = distributionService.distribute(assignmentId, classId);          // DistributionController.java:67
  ra.addFlashAttribute("message", "배포가 등록되었습니다. (배포 #" + id + ")");   // :68
  return "redirect:/distributions";                                         // :76
  ```
- 확신도: 높음 (코드 읽기)
- 비고: 단일 행 INSERT 라 `n != 1` 분기는 실제로 타기 어렵다. `@Transactional` 은 서비스에만 있어 컨트롤러의 BR-05 · BR-06 조회는 트랜잭션 밖이다.

## 5. 오류 처리

### BR-14
- 규칙: `assignmentId` · `classId` 는 `@RequestParam long`(필수)이라, 값이 없거나 long 으로 바뀌지 않으면 컨트롤러 본문에 들어가기 전에 Spring 이 요청을 거부한다. 이 오류는 컨트롤러 · `DbErrorAdvice` 어디서도 잡지 않는다.
- 근거: `web/DistributionController.java:50-51`, `web/DbErrorAdvice.java:11`
- 근거 코드:
  ```java
  public String create(@RequestParam("assignmentId") long assignmentId,   // DistributionController.java:50
                       @RequestParam("classId") long classId,             // :51
  @ExceptionHandler(DataAccessException.class)                            // DbErrorAdvice.java:11 — 변환 오류는 대상 아님
  ```
- 확신도: 낮음 (추정 — Spring 기본 변환 규칙에 따른 예상이며 실행하지 않았다)
- 비고: 경계값별 예상 — `03` → 3 으로 정상 처리 / `abc` · `3.5` · `3abc` → 400 (타입 불일치) / 파라미터 없음 → 400 / 빈 값 `assignmentId=` → primitive `long` 에 null 을 넣을 수 없어 400 계열로 예상하나 5xx 가능성도 있어 확인 필요. 폼의 `required`(`distribution_form.html:11`, `:23`)와 빈 option `value=""`(`:12`, `:24`)는 브라우저 제약일 뿐이다. 오류 화면 템플릿(`error.html`)이 없어 Spring 기본 오류 페이지가 나올 것으로 보인다.

### BR-15
- 규칙: DB 오류(`DataAccessException`)가 서비스 호출(try 안)에서 나면 flash 오류 "DB 오류로 배포에 실패했습니다." 와 함께 `/distributions/new` 로 돌아가고, 폼 표시(`GET /distributions/new`)나 등록 첫 과제 조회(try 밖)에서 나면 `DbErrorAdvice` 가 `db_error` 화면을 원인 메시지와 함께 보여 준다.
- 근거: `web/DistributionController.java:40-47`, `:53`, `:66-75`, `web/DbErrorAdvice.java:11-17`, `templates/db_error.html:6-8`
- 근거 코드:
  ```java
  AssignmentRow a = assignmentDao.findById(assignmentId);                 // DistributionController.java:53 (try 밖)
  } catch (DataAccessException e) {                                       // :72
      ra.addFlashAttribute("error", "DB 오류로 배포에 실패했습니다.");        // :73
  System.out.println("[DB-ERROR] " + e.getClass().getSimpleName() + " : " + e.getMostSpecificCause().getMessage());   // DbErrorAdvice.java:13
  model.addAttribute("detail", e.getMostSpecificCause().getMessage());    // DbErrorAdvice.java:15
  return "db_error";                                                      // DbErrorAdvice.java:16
  ```
- 확신도: 높음 (코드 읽기)
- 비고: `db_error.html:8` 이 DB 원인 메시지(`detail`)를 화면에 그대로 출력한다. 로그는 `System.out` 으로만 남는다. try 안에서 잡힌 DB 오류는 로그를 남기지 않는다(`DistributionController.java:72-74`).

---

## 주석만 있는 내용 (코드로 강제되지 않거나 코드와 다른 것)

| 위치 | 주석 | 코드 · 데이터 실제 |
|---|---|---|
| `AppClock.java:8` | "2024-03 QA 기간 중 임시로 고정 … 되돌릴 것 (아직 안 되돌림)" | 고정값은 `2026-09-15`, 여전히 고정 (BR-04) |
| `01-schema.sql:87` | `O=진행 C=마감` | 코드는 `X` · `D` 도 쓰고, `C` 는 막지 않는다 (BR-06, BR-08, BR-10) |
| `DistributionController.java:59` | "연장(X) 상태만 예외" | 코드와 일치하나 `X` 의 정의 · 설정 경로가 저장소 범위 안에 없다 |
| `distribution_form.html:13` | "마감 지난 과제는 선택 불가 (연장 상태 제외)" | 코드(`:16`)와 일치. 브라우저 제약일 뿐 |
| `DistributionDao.java:46` | "한 건만 존재해야 한다" | 시드에 2건인 조합이 있다 (BR-09) |
| `02-seed.sql:128` | "4번은 2번 과제를 1반에 재배포한 이력" | 재배포가 새 행을 만드는지는 범위 밖(재배포 코드) |

## 기능 없음 (코드에 없는 것)

| 항목 | 확인한 곳 |
|---|---|
| 로그인 · 교사 권한 확인 (자기 학급에만 배포 등) | `DistributionController.java:49-77`, `DistributionService.java:46-68` 에 사용자 정보 없음 |
| 상태 `C` 차단 | BR-10 |
| (과제, 학급) DB 유일성 | `01-schema.sql:92-103` 에 UNIQUE 없음 (BR-09) |
| 서버 쪽 검사 실패 시 선택값 유지 | redirect 로 새 폼을 그려 선택이 초기화된다 (`DistributionController.java:56`, `:63`, `:71`, `:74`) |

## 표본 점검 추천

| 순번 | 규칙 ID | 고른 이유 | 확인 방법 |
|---|---|---|---|
| 1 | BR-06 | **가장 그럴듯한 규칙.** "마감 지난 과제는 배포 불가" 는 너무 당연해 다시 안 보지만, 판단 기준이 실제 시각이 아니라 고정 시각(`2026-09-15`)이다 | `AppClock.java:8-19` 확인 후, compose `thymeleaf` 프로필로 띄워 `GET http://localhost:8082/distributions/new` 에서 과제 4(09-25 마감)가 선택 가능한지 본다. 이어서 `-Dapp.clock=system` 으로 띄우면 과제 4 가 `[마감]`·비활성으로 바뀌는지 비교 (GET 만) |
| 2 | BR-01 · BR-06 (콜레이션) | SQL(대소문자 무시)과 Java `"X".equals`(구분)가 같은 상태값을 다르게 본다. "하지 말 것" 의 콜레이션 유형 | readonly 로 `SELECT COUNT(*) FROM assignment WHERE status IN ('o','x','c')` (→ 6, 확인함). 코드는 `AssignmentDao.java:36` · `DistributionController.java:61` · `distribution_form.html:16` 를 나란히 열어 비교. 소문자 상태 행은 쓰기가 필요하므로 시드를 복사한 별도 DB 에서만 시험 |
| 3 | BR-14 | 확신도 낮음. 형변환 경계값(`abc` · `3.5` · `03` · 빈 값)의 결과를 실행 없이 추정했다 | 쓰기가 일어나므로 운영 · 공용 DB 가 아닌 곳에서만. `03` 은 실제 INSERT 가 되므로, 마감 지난 과제(`assignmentId=01&classId=3` → BR-06 에 걸려 저장 안 됨)로 변환 여부만 본다. `assignmentId=abc` · `assignmentId=` 는 컨트롤러 진입 전 거부되는지 상태 코드 확인 |

## 검증 이력

| 날짜 | 규칙 | 바뀐 내용 | 방법 |
|---|---|---|---|
| | | | |
