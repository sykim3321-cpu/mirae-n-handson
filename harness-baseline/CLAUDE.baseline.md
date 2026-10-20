# CLAUDE.md — 팀 표준 규칙 (기준 한 벌 · 3회차 종료 시점)

대상: `modern/api` (Spring Boot 3.3 · Java 21 · Gradle wrapper · MariaDB 10.11), `modern/web` (React 18 · TypeScript · Vite), `characterization/` (동작 보존 테스트)
`legacy/` 는 분석 · 이관 대상이며 컨벤션 적용 대상이 아니다.

## 1. 빌드 · 테스트 명령

```bash
cd modern/api && ./gradlew test        # 단위 · 슬라이스 테스트. DB 없이 통과해야 한다 (테스트 프로필은 H2)
cd modern/api && ./gradlew bootRun     # 로컬 실행(포트 8080). 먼저 docker compose --profile modern up -d 로 DB를 띄운다
cd modern/api && ./gradlew build       # 테스트 포함 전체 빌드

cd modern/web && npm test              # vitest run
cd modern/web && npm run lint          # eslint
cd modern/web && npm run typecheck     # tsc --noEmit
cd modern/web && npm run build         # vite build

cd characterization && npm test                                 # 스냅샷과 비교 (레거시 대상)
cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test   # 같은 테스트를 새 API 대상으로
```

- `modern/web` · `characterization` 은 처음 한 번 `npm install` 이 필요하다. 설치는 사람이 한다(의존성 설치는 확인 대상).
- 기동 확인은 `curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/`.
- 테스트 설정은 `modern/api/src/test/resources/application-test.yml`. 테스트가 실제 MariaDB에 붙게 만들지 않는다.
- 작업이 끝나면 바꾼 폴더의 테스트 명령을 실행하고, 실행 결과(통과 · 실패 수)를 답변에 적는다. 실행하지 않았으면 "실행하지 않음"이라고 쓴다.

## 2. 코딩 컨벤션

### 계층 규칙
- 패키지는 도메인 단위: `com.example.item`, `com.example.assignment`. 한 도메인 안에 `Controller` → `Service` → `Repository` → 엔티티 순으로 호출한다.
- 컨트롤러는 서비스만 호출한다. **컨트롤러에서 Repository 를 주입받거나 SQL 문자열을 직접 실행하지 않는다.**
- 서비스가 다른 도메인의 데이터를 쓸 때는 그 도메인의 서비스를 통한다. 다른 도메인의 Repository 를 직접 주입하지 않는다.
- 요청 · 응답은 record 기반 DTO 를 쓴다. 엔티티를 컨트롤러 밖으로 그대로 반환하지 않는다.

### 예외 처리
- **예외를 잡고 아무 처리 없이 넘기는 빈 `catch` 블록을 두지 않는다.** 잡았으면 로그를 남기고 다시 던지거나, 도메인 예외로 바꿔 던진다.
- 예외 → HTTP 응답 변환은 `common/GlobalExceptionHandler`(`@RestControllerAdvice`) 한 곳에서만 한다. 컨트롤러 메서드 안의 try-catch 로 상태 코드를 만들지 않는다.
- 없는 리소스는 404, 검증 실패는 400, 그 밖의 예상 못 한 예외는 500 으로 매핑한다.

### 로깅
- 로그는 SLF4J(`org.slf4j.Logger`)로만 남긴다. **`System.out.println` · `System.err.println` · `e.printStackTrace()` 를 쓰지 않는다.** 프런트엔드에 `console.log` 를 남기지 않는다.
- 로그에 학생 식별자(`STU-…`) · 이메일 · 토큰 값을 그대로 찍지 않는다.
- 로그 레벨: 정상 흐름은 `INFO` 이하, 복구 가능한 실패는 `WARN`, 요청을 실패시키는 예외는 `ERROR`.

### 테스트
- **동작을 바꾸는 변경에는 대응하는 테스트가 있어야 한다.** 새 public 서비스 메서드 · 새 엔드포인트마다 테스트 1개 이상.
- 컨트롤러는 `@WebMvcTest` 슬라이스 테스트, 서비스는 Mockito 단위 테스트, 리포지토리 쿼리는 `@DataJpaTest`(H2) 로 검증한다. 프런트엔드 컴포넌트는 같은 폴더의 `*.test.tsx`(vitest + Testing Library).
- 테스트 이름은 `메서드명_상황_기대결과` 형식(예: `search_levelOmitted_excludesLevel5`).

### 이름 · 형식
- 클래스 `PascalCase`, 메서드 · 필드 `camelCase`, 상수 `UPPER_SNAKE_CASE`. 약어도 `ItemDto` 처럼 첫 글자만 대문자.
- 매직 넘버는 이름 붙인 상수로 뺀다(예: 난이도 상한 `MAX_LEVEL = 5`).
- 들여쓰기 4칸(Java), 한 줄 120자 이내. 와일드카드 import 를 쓰지 않는다.

## 3. 금지 사항

- `legacy/` 는 분석 · 이관 대상이다. 허락 없이 수정하지 않는다.
- **DB 스키마 변경은 먼저 묻는다**: 테이블 · 컬럼 추가 · 삭제, 인덱스 변경, 마이그레이션 파일 추가, 시드 데이터 변경.
- 의존성 추가(`build.gradle` 의 `dependencies`, `package.json` 의 `dependencies` · `devDependencies`)는 먼저 묻는다. 이유와 대안을 함께 적는다.
- `application.yml` 의 DB 접속 정보 · 커넥션 풀 설정을 바꾸지 않는다. 비밀값을 코드 · 설정 · 테스트 픽스처에 리터럴로 넣지 않는다.
- 운영 DB 호스트(`prod-db` 등)에 접속하는 명령 · 설정을 만들지 않는다.
- `@Transactional` 안에서 외부 HTTP 호출이나 긴 루프를 돌리지 않는다.
- 요청받지 않은 파일을 "정리" 명목으로 고치지 않는다. 포맷팅 · import 정리도 요청 범위 안의 파일에서만 한다.

## 4. 아키텍처 안내

```
modern/api/src/main/java/com/example/
├── item/          문항 · 단원 · 태그 (ItemController, UnitController, ItemService, UnitService, *Repository)
├── assignment/    과제 배포 · 학급 리포트 (DistributionController, ReportController, *Service, *Repository)
├── common/        GlobalExceptionHandler, ErrorResponse, NotFoundException, RootController
└── config/        WebConfig (/api/** CORS)
modern/api/src/main/resources/application.yml       기본 프로필 = 로컬 MariaDB, 커넥션 풀 설정 포함
modern/api/src/test/resources/application-test.yml  테스트 프로필 = H2 (MariaDB 모드)
modern/web/src/    api/ (fetch 클라이언트) · components/ (화면 + 테스트) · test/ (fixtures, mockFetch)
characterization/  lib/ (정규화 · 대상 주소) · tests/<모듈명>.test.js · __snapshots__/
```

- 기존 엔드포인트: `GET /api/units`, `GET /api/units/{code}/items`, `GET /api/items/{id}`, `GET /api/distributions/{id}`, `POST /api/distributions/{id}/redistribute`, `GET /api/classes/{id}/report`. 새 엔드포인트는 `/api/<도메인 복수형>` 아래에 둔다.
- 공개 문항만 노출한다(`status = 'A'`). 삭제 플래그가 아니라 상태 코드로 판단한다.
- 새 조회 API 는 컨트롤러 → 서비스 → 리포지토리 세 파일과 테스트를 같은 도메인 패키지에 만든다.
- 레거시 규칙을 옮길 때는 근거를 `파일:줄번호` 로 답변에 적는다(예: `legacy/item-bank-php/search.php:214`).
- 모듈 문서는 `docs/<모듈명>/`(item-bank · assignment · grade)에 있다. 반복 절차는 Skill 을 쓴다: `/convention-check`, `/document-module`, `/verify`, `/rca`, `/batch-check`. 리뷰 · 테스트는 `reviewer` · `tester` Sub-agent, IaC 리뷰는 `iac-reviewer`.
- 머지 전 판정 기준은 `templates/approval-checklist.md`, 절차는 `templates/verification-loop.md` 이다.

## 5. 완료 기준

작업을 "끝났다"고 보고하려면 아래를 모두 만족해야 한다.

- [ ] 바꾼 폴더의 테스트 명령(1절)을 실행했고, 통과 · 실패 수를 답변에 적었다
- [ ] 바꾼 동작마다 대응하는 테스트가 추가 · 수정되었다
- [ ] 컨트롤러에 Repository 주입 · SQL 문자열이 없고, 빈 `catch` 와 `System.out.println` 이 없다
- [ ] 변경 파일 목록이 요청 범위 안에 있다(요청에 없는 파일을 고쳤다면 이유를 적었다)
- [ ] 스키마 · 의존성 · 설정 변경이 없거나, 있었다면 사전에 승인받았다

이관 · 리팩토링 작업에는 아래를 더한다.

1. 이관 · 리팩토링 작업은 `characterization` 의 `npm test` 가 전부 통과하기 전에는 완료라고 보고하지 않는다.
2. 테스트가 실패하면 실패한 케이스와 차이를 그대로 보고한다. 요약해서 "거의 됐다"고 말하지 않는다.
3. 테스트를 통과시키려고 `characterization/` 의 테스트 코드나 스냅샷 파일을 고치지 않는다. 스냅샷을 바꿔야 한다고 판단되면 멈추고 묻는다.
4. 레거시 동작이 버그로 보여도 이관 중에는 고치지 않는다. "의심 동작" 목록으로 따로 보고한다.
