---
name: convention-check
description: 변경된 코드(또는 지정한 경로)가 CLAUDE.md 의 코딩 컨벤션 · 금지 사항을 지키는지 예/아니오로 점검하고, 위반을 파일:줄번호와 함께 표로 보고한다. "컨벤션 점검", "커밋 전 점검", "리뷰 전에 확인", "규칙 위반 있는지 봐 줘" 같은 요청이나 커밋 · PR 직전에 사용한다.
argument-hint: "[경로...]"
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash(git diff *)
  - Bash(git status *)
---

# 컨벤션 점검

- **코드를 직접 고치지 않는다.** 판정과 수정 방향만 보고한다.
- **근거 라인을 댈 수 없는 지적은 하지 않고 "확인 필요"로 남긴다.**

## 1. 점검 대상 정하기

- 인자로 경로를 받았으면 그 경로(디렉터리면 Glob 으로 하위 파일)만 본다.
- 인자가 없으면 `git status --porcelain` 과 `git diff HEAD --name-only` 로 변경 파일을 모은다. `??`(아직 add 하지 않은 새 파일)도 포함하고, 삭제된 파일은 8번에만 쓴다.
- 대상이 없으면 "점검할 변경 없음"이라고만 답하고 끝낸다.

## 2. 점검 항목

각 항목을 대상 파일에 대해 Grep/Read 로 확인하고, 위반마다 근거 줄번호를 적는다.

1. **Controller 경계** — `*Controller.java` 에 `Repository` import 가 없고, 핸들러 반환 타입에 `@Entity` 클래스가 없다.
2. **Controller 예외 처리** — `*Controller.java` 에 `try {` · `catch (` 가 없다. 예외는 `GlobalExceptionHandler` 가 맡는다.
3. **생성자 주입** — `modern/api/src/main` 에 `@Autowired` 가 없다.
4. **로그 · 시각** — `src/main` 에 `System.out` · `System.err` · `printStackTrace` · 인자 없는 `now()` 가 없다.
5. **테스트 형식** — 변경된 `*Test.java` 에 `@ActiveProfiles("test")` 가 있고, 모든 `@Test` 메서드에 `@DisplayName` 이 있다.
6. **대응 테스트** — 변경된 `src/main/.../X.java` 마다 같은 패키지의 `src/test/.../XTest.java` 가 있다. 주석 · import 만 바뀐 경우는 "확인 필요".
7. **web 네트워크 경계** — `modern/web/src/components` · `src/hooks` 에 `fetch(` 가 없고, `src/api/client.ts` 밖의 테스트 아닌 코드(주석 제외)에 `localhost:8080` 이 없다.
8. **금지 경로** — 변경 파일에 `characterization/__snapshots__/` · `characterization/tests/` · `mcp-skeleton/src/itemApi.ts` · `incident-logs/` · `package-lock.json` · 루트 `.env` 가 없다. `legacy/` 변경은 허락 여부를 알 수 없으므로 "확인 필요".

대상에 해당 파일이 하나도 없는 항목은 "해당 없음"으로 적는다.

## 3. 출력 형식 (순서 고정)

### 판정 요약

| # | 항목 | 판정 |
|---|---|---|
| 1 | Controller 경계 | 예 / 아니오 / 해당 없음 / 확인 필요 |

(1~8 모두 한 줄씩. 끝에 "점검 파일 N개, 위반 M건, 확인 필요 K건".)

### 위반 목록

| 파일:줄번호 | 어긴 규칙 | 수정 방향 |
|---|---|---|
| `modern/api/.../FooController.java:12` | 1. Controller 경계 | `FooRepository` 대신 `FooService` 를 주입받는다 |

- 위반이 없으면 표 대신 "위반 없음"이라고 쓴다.
- "확인 필요" 항목은 표 아래에 무엇을 사람이 확인해야 하는지 한 줄씩 적는다.
