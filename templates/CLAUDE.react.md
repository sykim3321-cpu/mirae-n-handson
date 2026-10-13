# CLAUDE.md — React · TypeScript 화면 팀 템플릿

이 파일은 `templates/CLAUDE.react.md` 입니다. 저장소 루트의 `CLAUDE.md` 로 옮겨 쓰기 전에 팀 사정에 맞지 않는 줄을 고치세요.
대상 코드: `modern/web` (React 18 · TypeScript · Vite · Vitest)

## 1. 빌드 · 테스트 명령

```bash
cd modern/web && npm install                                   # 처음 한 번. package-lock.json 을 바꾸지 않는다
cd modern/web && npm run dev                                   # 개발 서버 http://localhost:5173
cd modern/web && npm run lint && npm run typecheck && npm test # 커밋 전 필수
cd modern/web && npm run build                                 # 배포용 빌드
```

- API 서버는 `http://localhost:8080` (`modern/api`). 개발 서버의 프록시 설정은 `vite.config.ts` 에 있다.
- 작업이 끝나면 `npm run lint && npm run typecheck && npm test` 를 모두 실행하고 결과를 답변에 적는다. 하나라도 실패하면 "완료"라고 쓰지 않는다.
- 테스트 출력은 통과 · 실패 수와 실패한 테스트 이름만 답변에 남긴다. 전체 로그를 대화에 붙이지 않는다.

## 2. 코딩 컨벤션

### 컴포넌트
- 컴포넌트는 `src/components/<이름>/` 폴더 하나에 `<이름>.tsx`, `<이름>.test.tsx`, `index.ts` 세 파일로 만든다. 스타일이 필요하면 `<이름>.module.css`.
- 함수 컴포넌트만 쓴다. 클래스 컴포넌트 · `React.FC` 타입 · `defaultProps` 는 쓰지 않는다.
- props 타입은 컴포넌트 파일 안에 `type <이름>Props = { … }` 로 선언하고 export 한다. `any` 는 금지한다.
- 화면(라우트 단위)은 `src/pages/`, 재사용 조각은 `src/components/`. 페이지 컴포넌트가 다른 페이지를 import 하지 않는다.
- 한 파일에 export 하는 컴포넌트는 하나. 200줄이 넘으면 쪼갠다.

### 상태 관리
- 서버 데이터는 `src/api/` 의 훅(`useItems`, `useUnits` …)으로만 읽는다. 컴포넌트 안에서 `fetch` 를 직접 호출하지 않는다.
- 화면 로컬 상태는 `useState` · `useReducer`. 두 화면 이상이 공유하는 상태만 `src/store/` 의 Context 로 올린다.
- 파생 값은 상태에 저장하지 않고 렌더 시 계산한다(필요하면 `useMemo`).
- `useEffect` 안에서 상태를 동기화하는 패턴(상태 A 가 바뀌면 상태 B 를 set)은 쓰지 않는다.

### API 호출 규약
- 모든 요청은 `src/api/client.ts` 의 `apiClient` 를 거친다. 기본 URL · 공통 헤더 · 오류 변환은 이 파일 한 곳에서만 한다.
- 응답 타입은 `src/api/types.ts` 에 두고 백엔드 JSON 필드명을 그대로 쓴다(예: `{ items, count, message }`). 화면용 이름으로 바꾸려면 훅 안에서 매핑한다.
- 오류는 `apiClient` 가 던지는 `ApiError` 로 받는다. 컴포넌트에서 HTTP 상태 코드를 직접 비교하지 않는다.
- 로딩 · 오류 · 빈 결과 세 상태를 화면에 모두 표시한다. 빈 결과 문구는 백엔드 `message` 를 우선 쓴다.

### 테스트
- 컴포넌트마다 `<이름>.test.tsx` 가 있어야 한다. 렌더 확인 1개 + 사용자 상호작용(클릭 · 입력) 1개 이상.
- Testing Library 를 쓰고 조회는 `getByRole` · `getByLabelText` 우선. `getByTestId` 는 마지막 수단.
- 네트워크는 `src/test/msw/` 의 핸들러로 가로챈다. 테스트에서 실제 `localhost:8080` 을 호출하지 않는다.
- 스냅샷 테스트는 만들지 않는다.

### 이름 · 형식
- 컴포넌트 · 타입 `PascalCase`, 훅 `use` 접두어 + `camelCase`, 파일명은 컴포넌트와 같게.
- 이벤트 핸들러 prop 은 `on<동작>`, 구현 함수는 `handle<동작>`.
- 형식은 ESLint · Prettier 설정을 따른다. 규칙을 끄는 주석(`eslint-disable`)은 사유를 같은 줄에 적는다.

## 3. 금지 사항

- `legacy/` 는 분석 · 이관 대상이다. 허락 없이 수정하지 않는다.
- 의존성 추가 · 업그레이드(`package.json` 변경)는 먼저 사람에게 묻는다. UI 라이브러리 · 상태 관리 라이브러리 도입은 팀 합의 사항이다.
- `package-lock.json` 을 손으로 고치거나 지우지 않는다.
- `any`, `as unknown as`, `@ts-ignore` 금지. 타입을 못 맞추면 그 이유를 답변에 쓰고 사람에게 묻는다.
- `.env*` 파일을 읽거나 만들지 않는다. 비밀값 · 토큰을 프론트 코드에 넣지 않는다.
- `dangerouslySetInnerHTML` 금지. 서버 응답 HTML 을 그대로 렌더링하지 않는다.
- `console.log` 를 커밋에 남기지 않는다.
- 요청받지 않은 컴포넌트를 "정리" 명목으로 고치지 않는다.

## 4. 아키텍처 안내

```
modern/web/src/
├── api/          client.ts(공통 클라이언트) · types.ts(응답 타입) · use*.ts(데이터 훅)
├── components/   재사용 컴포넌트. 폴더 = 컴포넌트 1개 (tsx · test.tsx · index.ts)
├── pages/        라우트 단위 화면 (문항 검색, 단원 목록 …)
├── store/        두 화면 이상이 공유하는 Context
└── test/         테스트 설정, msw 핸들러
```

- 데이터 흐름: 페이지 → 데이터 훅(`src/api`) → `apiClient` → `modern/api`. 컴포넌트는 props 로만 데이터를 받는다.
- 새 화면 하나를 추가하는 표준 작업: `pages/` 에 화면 1개 + 필요한 `components/` + `api/` 훅 1개 + 각 테스트.
- 문항 도메인 용어는 백엔드와 같게 쓴다: 문항 `item`, 단원 `unit`, 난이도 `level`(1~5), 태그 `tag`.

## 5. 완료 기준

- [ ] `cd modern/web && npm run lint && npm run typecheck && npm test` 가 모두 통과했고 결과 수치를 답변에 적었다
- [ ] 새 컴포넌트마다 `<이름>.test.tsx` 가 있고 상호작용 테스트가 1개 이상 있다
- [ ] 컴포넌트 안에 `fetch` 직접 호출 · `any` · `console.log` 가 없다
- [ ] `package.json` · `package-lock.json` 이 바뀌지 않았다(바뀌었다면 사전 승인이 있었다)
- [ ] 변경 파일이 요청 범위 안에 있다
