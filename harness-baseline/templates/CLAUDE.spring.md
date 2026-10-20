# CLAUDE.md — Spring Boot API 팀 템플릿

이 파일은 `templates/CLAUDE.spring.md` 입니다. 저장소 루트의 `CLAUDE.md` 로 옮겨 쓰기 전에 팀 사정에 맞지 않는 줄을 고치세요.
대상 코드: `modern/api` (Spring Boot 3.3 · Java 21 · Gradle wrapper · MariaDB 10.11)

## 1. 빌드 · 테스트 명령

```bash
cd modern/api && ./gradlew test        # 단위 · 슬라이스 테스트. DB 없이 통과해야 한다 (테스트 프로필은 H2)
cd modern/api && ./gradlew bootRun     # 로컬 실행. 먼저 docker compose --profile modern up -d 로 DB를 띄운다
cd modern/api && ./gradlew build       # 테스트 포함 전체 빌드
```

- 서버 포트는 8080. 기동 확인은 `curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/`.
- 테스트 설정은 `src/test/resources/application-test.yml`. 테스트가 실제 MariaDB에 붙게 만들지 않는다.
- 작업이 끝나면 반드시 `./gradlew test` 를 실행하고, 실행 결과(통과 · 실패 수)를 답변에 적는다. 실행하지 않았으면 "실행하지 않음"이라고 쓴다.

## 2. 코딩 컨벤션

### 계층 규칙
- 패키지는 도메인 단위: `com.example.item`, `com.example.assignment`. 한 도메인 안에 `Controller` → `Service` → `Repository` → 엔티티 순으로 호출한다.
- 컨트롤러는 서비스만 호출한다. **컨트롤러에서 Repository 를 주입받거나 SQL 문자열을 직접 실행하는 것은 금지**한다.
- 서비스가 다른 도메인의 데이터를 쓸 때는 그 도메인의 서비스를 통한다. 다른 도메인의 Repository 를 직접 주입하지 않는다.
- 요청 · 응답은 record 기반 DTO 를 쓴다. 엔티티를 컨트롤러 밖으로 그대로 반환하지 않는다.

### 예외 처리
- **예외를 잡고 아무 처리 없이 넘기는 빈 `catch` 블록은 금지**한다. 잡았으면 로그를 남기고 다시 던지거나, 의미 있는 도메인 예외로 바꿔 던진다.
- 예외 → HTTP 응답 변환은 `@ControllerAdvice` 한 곳에서만 한다. 컨트롤러 메서드 안의 try-catch 로 상태 코드를 만들지 않는다.
- 없는 리소스는 404, 검증 실패는 400, 그 밖의 예상 못 한 예외는 500 으로 매핑한다.

### 로깅
- 로그는 SLF4J(`org.slf4j.Logger`)로만 남긴다. **`System.out.println` · `System.err.println` · `e.printStackTrace()` 는 금지**한다.
- 로그에 학생 식별자(`STU-…`) · 이메일 · 토큰 값을 그대로 찍지 않는다.
- 로그 레벨: 정상 흐름은 `INFO` 이하, 복구 가능한 실패는 `WARN`, 요청을 실패시키는 예외는 `ERROR`.

### 테스트
- **동작을 바꾸는 변경에는 대응하는 테스트가 있어야 한다.** 새 public 서비스 메서드 · 새 엔드포인트마다 테스트 1개 이상.
- 컨트롤러는 `@WebMvcTest` 슬라이스 테스트, 서비스는 Mockito 단위 테스트, 리포지토리 쿼리는 `@DataJpaTest`(H2) 로 검증한다.
- 테스트 이름은 `메서드명_상황_기대결과` 형식(예: `search_levelOmitted_excludesLevel5`).
- 스냅샷 · 동작 보존 테스트(`characterization/`)를 깨뜨리는 변경은 먼저 사람에게 알린다.

### 이름 · 형식
- 클래스 `PascalCase`, 메서드 · 필드 `camelCase`, 상수 `UPPER_SNAKE_CASE`. 약어도 `ItemDto` 처럼 첫 글자만 대문자.
- 매직 넘버는 이름 붙인 상수로 뺀다(예: 난이도 상한 `MAX_LEVEL = 5`).
- 들여쓰기 4칸, 한 줄 120자 이내. 와일드카드 import 금지.

## 3. 금지 사항

- `legacy/` 는 분석 · 이관 대상이다. 허락 없이 수정하지 않는다.
- **DB 스키마 변경 금지**: 테이블 · 컬럼 추가 · 삭제, 인덱스 변경, 마이그레이션 파일 추가는 먼저 사람에게 묻는다. 시드 데이터도 마찬가지다.
- 의존성 추가(`build.gradle` 의 `dependencies` 변경)는 먼저 묻는다. 이유와 대안을 함께 적는다.
- `application.yml` 의 DB 접속 정보 · 커넥션 풀 설정을 바꾸지 않는다. 비밀값을 코드나 설정 파일에 리터럴로 넣지 않는다.
- 운영 DB 호스트(`prod-db` 등)에 접속하는 명령 · 설정을 만들지 않는다.
- `@Transactional` 안에서 외부 HTTP 호출이나 긴 루프를 돌리지 않는다.
- 요청받지 않은 파일을 "정리" 명목으로 고치지 않는다. 포맷팅 · import 정리도 요청 범위 안의 파일에서만 한다.

## 4. 아키텍처 안내

```
modern/api/src/main/java/com/example/
├── item/          문항 · 단원 · 태그 (ItemController, ItemService, ItemRepository, Item, Unit)
├── assignment/    과제 배포 (골격만 있음)
└── common/        @ControllerAdvice, 공통 응답 · 예외
modern/api/src/main/resources/application.yml       기본 프로필 = 로컬 MariaDB, 커넥션 풀 설정 포함
modern/api/src/test/resources/application-test.yml  테스트 프로필 = H2 (MariaDB 모드)
```

- 기존 엔드포인트: `GET /api/units`, `GET /api/units/{code}/items`, `GET /api/items/{id}`. 새 엔드포인트는 `/api/<도메인 복수형>` 아래에 둔다.
- 공개 문항만 노출한다(`status = 'A'`). 삭제 플래그가 아니라 상태 코드로 판단한다.
- 새 조회 API 는 컨트롤러 → 서비스 → 리포지토리 세 파일과 테스트를 같은 도메인 패키지에 만든다.
- 레거시 규칙을 옮길 때는 근거를 `파일:줄번호` 로 답변에 적는다(예: `legacy/item-bank-php/search.php:214`).

## 5. 완료 기준

작업을 "끝났다"고 보고하려면 아래를 모두 만족해야 한다.

- [ ] `cd modern/api && ./gradlew test` 가 통과했고, 통과 · 실패 수를 답변에 적었다
- [ ] 바꾼 동작마다 대응하는 테스트가 추가 · 수정되었다
- [ ] 컨트롤러에 Repository 주입 · SQL 문자열이 없고, 빈 `catch` 와 `System.out.println` 이 없다
- [ ] 변경 파일 목록이 요청 범위 안에 있다(요청에 없는 파일을 고쳤다면 이유를 적었다)
- [ ] 스키마 · 의존성 · 설정 변경이 없거나, 있었다면 사전에 승인받았다
