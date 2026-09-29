# 과제 배포 (legacy/assignment-thymeleaf) 데이터 흐름 · ERD — "새 배포" 화면

범위는 `ARCHITECTURE.md` 의 "범위" 절과 같다. 스키마 근거는 `db/mariadb/init/01-schema.sql`, 시드는 `02-seed.sql`.

## 1. 데이터 흐름

### E1. `GET /distributions/new` — 폼 표시

```
입력: 없음
  ↓
SQL ①  AssignmentDao.findAllForSelect()                       AssignmentDao.java:21-24, :34-39
       SELECT a.id, a.title, a.unit_id, u.code AS unit_code, u.name AS unit_name, a.due_at, a.status,
              (SELECT COUNT(DISTINCT d.class_id) FROM distribution d WHERE d.assignment_id = a.id) AS class_cnt
         FROM assignment a LEFT JOIN unit u ON u.id = a.unit_id
        WHERE a.status IN ('O', 'X', 'C')
        ORDER BY a.due_at ASC, a.id ASC
SQL ②  ClassDao.findAll()                                     ClassDao.java:21
       SELECT id, name, teacher_id FROM `class` ORDER BY id ASC
now = AppClock.now()                                          DistributionController.java:44
  ↓
출력: distribution_form.html
  - 과제 option: value=id, 문구 "id. 제목 (단원코드, 마감 MM-dd HH:mm)" + 마감 지났으면 " [연장]" 또는 " [마감]"   :15, :17-18
  - disabled = dueAt != null and dueAt.isBefore(now) and status != 'X'                                          :16
  - 학급 option: value=id, 문구 "학급명 (교사ID)"                                                                  :25
```

- `class_cnt` 는 조회되지만 이 화면은 쓰지 않는다 (`distribution_form.html` 에 `classCount` 참조 없음).

### E2. `POST /distributions` — 배포 등록

```
입력: assignmentId(long), classId(long)                        DistributionController.java:50-51
  ↓ (Spring 형변환 — 실패 시 컨트롤러 진입 전 오류, BR-14)
검증 ① 과제 존재      AssignmentDao.findById  → null 이면 "존재하지 않는 과제입니다."          Controller :53-57
검증 ② 마감 경과      dueAt < AppClock.now() 이고 status != "X" 이면 "마감(...)이 지난 과제는..."  Controller :58-65
  ↓ try {
검증 ③ 과제 존재(중복) AssignmentDao.findById → null 이면 IllegalArgumentException          Service :48-51
검증 ④ 학급 존재      ClassDao.findById     → null 이면 IllegalArgumentException          Service :52-55
검증 ⑤ 삭제 과제      status == "D" 이면 IllegalStateException                           Service :56-58
검증 ⑥ 중복 배포      COUNT(*) > 0 이면 IllegalStateException                            Service :59-61
       SELECT COUNT(*) FROM distribution WHERE assignment_id = ? AND class_id = ?     DistributionDao.java:49
SQL   id = SELECT COALESCE(MAX(id), 0) FROM distribution  + 1                        DistributionDao.java:55-56
SQL   INSERT INTO distribution (id, assignment_id, class_id, distributed_at, redistributed)
      VALUES (?, ?, ?, ?, 0)       -- distributed_at = AppClock.now()                 DistributionDao.java:61, Service :63
검증 ⑦ 영향 행 수 != 1 이면 IllegalStateException                                      Service :64-66
  ↓ } catch
출력: 성공 → flash message "배포가 등록되었습니다. (배포 #id)" → redirect:/distributions     Controller :68, :76
      IllegalArgument/IllegalState → flash error = 예외 메시지 → redirect:/distributions/new  :69-71
      DataAccessException(try 안) → flash error "DB 오류로 배포에 실패했습니다." → redirect:/distributions/new  :72-74
      DataAccessException(①, try 밖) → DbErrorAdvice → db_error.html                     DbErrorAdvice.java:11-17
```

- 트랜잭션: `@Transactional` 은 `distribute()` 에만 있다 (`DistributionService.java:46`). 컨트롤러의 검증 ①② 는 트랜잭션 밖이다.

## 2. ERD (이 경로가 참조하는 테이블)

```mermaid
erDiagram
    unit ||--o{ assignment : "unit_id (fk_assignment_unit)"
    assignment ||--o{ distribution : "assignment_id (fk_distribution_assignment)"
    class ||--o{ distribution : "class_id (fk_distribution_class)"

    unit {
        INT id PK
        VARCHAR16 code UK "uk_unit_code"
        VARCHAR100 name
        TINYINT grade
    }
    assignment {
        INT id PK
        VARCHAR200 title
        INT unit_id FK "NOT NULL"
        DATETIME due_at "NOT NULL"
        CHAR1 status "NOT NULL DEFAULT 'O' -- O=진행 C=마감"
    }
    class {
        INT id PK
        VARCHAR50 name
        VARCHAR20 teacher_id
    }
    distribution {
        INT id PK "AUTO_INCREMENT 아님"
        INT assignment_id FK
        INT class_id FK
        DATETIME distributed_at "NOT NULL"
        TINYINT redistributed "NOT NULL DEFAULT 0"
    }
```

| 테이블 | 정의 (01-schema.sql) | 이 화면에서 |
|---|---|---|
| `unit` | `:11-18`. `code` UNIQUE (`:17`) | 과제 option 의 단원 코드 (`LEFT JOIN`) |
| `class` | `:75-80`. `class` 는 예약어라 SQL 에서 백틱으로 감싼다 (`ClassDao.java:21`) | 학급 드롭다운 · 존재 확인 |
| `assignment` | `:82-90`. `due_at DATETIME NOT NULL` (`:86`), `status CHAR(1) NOT NULL DEFAULT 'O' -- O=진행 C=마감` (`:87`), `unit_id NOT NULL` + FK (`:85`, `:89`) | 과제 드롭다운 · 마감/상태 검증 |
| `distribution` | `:92-103`. `id INT NOT NULL` PK, **AUTO_INCREMENT 없음** (`:93`, `:98`). **(assignment_id, class_id) UNIQUE 없음** — 인덱스만 따로 (`:99-100`) | 중복 확인 · 다음 id 계산 · INSERT |

### 스키마에서 나오는 사실 (코드만 보면 놓치는 것)

- **콜레이션**: DB 기본 `utf8mb4_unicode_ci` (`01-schema.sql:5`), `assignment` 테이블도 같다 (`:90`). SQL `status IN ('O', 'X', 'C')` 는 대소문자를 구분하지 않는다 — readonly 조회로 `assignment.status` 컬럼 콜레이션 `utf8mb4_unicode_ci`, `SELECT COUNT(*) FROM assignment WHERE status IN ('o','x','c')` → `6` (실행 확인). 반면 Java `"X".equals(...)` · Thymeleaf `!= 'X'` 는 구분한다 (BR-06).
- **`due_at NOT NULL`** (`:86`): 컨트롤러 `a.getDueAt() != null` (`DistributionController.java:60`) · 템플릿 `a.dueAt != null` (`distribution_form.html:16`, `:18`) 조건은 항상 참이다. `AssignmentDao.java:60` 의 `null` 분기도 타지 않는다.
- **`unit_id NOT NULL` + FK** (`:85`, `:89`): `LEFT JOIN unit` (`AssignmentDao.java:24`) 이 NULL 단원을 만들 일이 없다.
- **상태값 정의**: 스키마 주석은 `O=진행 C=마감` 두 값뿐이다 (`:87`). 코드가 쓰는 `X`(연장 — 컨트롤러 주석 `DistributionController.java:59`) · `D`(삭제 — `DistributionService.java:57` 문구)는 스키마에 정의가 없다. CHECK 제약도 없다.
- **중복 방지는 코드에만 있다**: `DistributionDao.java:46` 주석 "UNIQUE 제약은 없음, 여기서 막는다".

## 3. 시드 · 현재 DB 상태 (readonly 조회, 2026-09-29)

| 대상 | 내용 | 근거 |
|---|---|---|
| `assignment` 상태 | 1 · 2 = `C`, 3 ~ 6 = `O`. `X` · `D` 없음 | `02-seed.sql:119-125`, readonly `SELECT id, status, due_at FROM assignment` |
| `assignment` 마감 | 1 `08-20` · 2 `08-28` · 3 `09-10` · 4 `09-25` · 5 `10-02` · 6 `09-30` (모두 23:59, 2026년) | `02-seed.sql:120-125` |
| 시드 주석 | "마감이 지난 과제: 1, 2 … 3 — 2026-09-10 마감" — 고정 시각 `2026-09-15` 기준과 맞는다(실제 오늘 2026-09-29 기준이면 4 도 지남) | `02-seed.sql:117`, `AppClock.java:9` |
| `class` | 1 `5학년 1반` · 2 `5학년 2반` · 3 `6학년 1반` | `02-seed.sql:111-114` |
| `distribution` | 8건. **(과제 2, 학급 1) 이 2건** (id 3 · 4, id 4 는 `redistributed=1`) | `02-seed.sql:133-134`, readonly `GROUP BY assignment_id, class_id HAVING COUNT(*) > 1` → `2, 1, 2` |
| 이미 배포된 (과제, 학급) | (1,1) (1,2) (2,1) (3,3) (4,3) (5,3) (6,2) | `02-seed.sql:131-138` |
