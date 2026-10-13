# characterization — 동작 보존 테스트 틀

레거시 모듈의 **지금 동작**을 스냅샷으로 찍어 두고, 이관 · 리팩토링 뒤에 **같은 입력**으로 다시 돌려 결과가 같은지 비교한다.
기대값을 손으로 적지 않는다. 지금 시스템의 실제 응답이 기대값이다.

## 명령

```bash
cd characterization
npm ci                                       # 처음 한 번 (lock 파일을 고치지 않음)
npm run baseline -- <모듈명>                  # 스냅샷 새로 찍기 (= vitest run -u tests/<모듈명>.test.js)
npm test                                     # 스냅샷과 비교 (대상: 레거시 기본 주소)
TARGET_BASE_URL=http://localhost:8080 npm test   # 대상만 새 API 로 바꿔 같은 테스트를 돌린다
```

모듈명은 폴더 이름이 아니라 `item-bank` · `assignment` · `grade` 다.

## 대상 주소 — 환경 변수 `TARGET_BASE_URL`

| 모듈명 | 기본 주소(레거시) | Compose 프로필 |
|---|---|---|
| `item-bank` | `http://localhost:8081` | `php` |
| `assignment` | `http://localhost:8082` | `thymeleaf` |
| `grade` | `http://localhost:8083` | `mssql` |

- `TARGET_BASE_URL` 이 있으면 그 주소를, 없으면 위 기본 주소를 쓴다. 기본 주소는 `lib/target.mjs` **한 곳**에만 있다.
- 테스트 코드에 `http://localhost:808x` 같은 주소를 직접 적지 않는다. `fetchNormalized(모듈명, 경로, 파라미터)` 를 쓰면 주소는 자동으로 붙는다.
- 이관하면서 **경로가 바뀐 화면**은 `lib/target.mjs` 의 `PATH_ALIASES` 에 적는다(예: `/search.php` → `/api/items/search`). 테스트는 레거시 경로로 적고, 대상 서버가 그 경로를 404 로 돌려주면 대응표의 새 경로로 한 번 더 요청한다. 테스트 코드와 스냅샷은 그대로 둔다.

## 폴더 구조 · 이름 규칙

```
characterization/
├── lib/
│   ├── normalize.mjs      # 응답(HTML · JSON) → {status, rows[], count, message}
│   └── target.mjs         # 대상 주소 · fetchNormalized(module, path, params)
├── scripts/baseline.mjs   # npm run baseline -- <모듈명>
├── tests/
│   ├── example-units.test.js      # 예시 (문항 은행 단원 목록 units.php)
│   ├── normalize.unit.test.js     # 정규화 도우미 자체의 단위 테스트 (서비스 없이 돈다)
│   └── <모듈명>.test.js            # 참가자가 만드는 파일 — 예: item-bank.test.js
└── __snapshots__/
    ├── example-units.test.js.snap
    └── <모듈명>.test.js.snap        # 예: __snapshots__/item-bank.test.js.snap (git 으로 추적한다)
```

- 테스트 파일: `tests/<모듈명>.test.js`. 한 모듈에 파일 하나.
- 스냅샷: `__snapshots__/<테스트 파일명>.snap`. **커밋한다.** 이 파일이 "기준"이다.
- 예시 파일 `tests/example-units.test.js` 는 단원 목록(`units.php`)을 대상으로 한다. 참가자가 만들 `tests/item-bank.test.js`(문항 검색 등)와 겹치지 않는다.

## 테스트 한 케이스의 모양

```js
import { describe, expect, it } from 'vitest';
import { fetchNormalized } from '../lib/target.mjs';

const MODULE = 'item-bank';

describe('item-bank · 문항 검색(search.php)', () => {
  it('키워드 검색 — 분수', async () => {
    const result = await fetchNormalized(MODULE, '/search.php', { q: '분수' });
    expect(result).toMatchSnapshot();
  });
});
```

`fetchNormalized` 는 요청 → 정규화까지 한 번에 한다. 파라미터 값이 `undefined` 면 빼고, 빈 문자열 `''` 은 `level=` 처럼 빈값 그대로 보낸다(빈값 케이스에 쓴다).

## 정규화 결과 모양

HTML 응답이든 JSON 응답이든 아래 한 모양으로 바꾼 뒤 비교한다.

```js
{
  status: 200,                 // HTTP 상태 코드
  rows: [                      // 표의 행. 필드 이름은 영문 소문자. 응답 순서 그대로
    { id: 12, title: '분수의 덧셈 문장제', unit: 'M5-1', level: 4, tags: ['계산', '문장제'] },
  ],
  count: 1,                    // 건수 (#count 의 숫자, JSON 의 count)
  message: null                // 안내 문구 (#message, JSON 의 message). 없으면 null
}
```

- **HTML**: `<table id="items">` 는 열 순서 고정(`id, title, unit, level, tags`, `tags` 는 쉼표로 나눔). 그 밖의 `<table id="...">` 는 머리글(`<th>`)을 영문 소문자 필드명으로 옮긴다(`단원 코드` → `code`, `학년` → `grade` …, 대응표는 `lib/normalize.mjs` 의 `HEADER_MAP`). 건수는 `<p id="count">`, 안내 문구는 `<p id="message">`.
- **JSON**: `{ items, count, message }` 를 그대로 옮긴다. 배열이 오면 `rows` 로 본다.
- **실행마다 바뀌는 값 제거**: 시각 · 세션 · 토큰 · 요청 ID. HTML 은 표 · 건수 · 안내 문구만 뽑으므로 `<footer>` 의 시각 · 세션은 애초에 들어오지 않는다. JSON 은 `timestamp`, `generatedAt`, `requestId`, `sessionId`, `token` 등(`VOLATILE_KEYS`)을 지운다.
- **순서 유지**: `rows` 의 순서, `tags` 의 순서를 바꾸지 않는다. 정렬도 동작의 일부다.

## 케이스 작성 규칙

1. 한 대상(엔드포인트 · 화면)에 **케이스 10개 이상**. 정상 입력 3개, 규칙의 **경계값 3개 이상**, 빈 값 · 누락 2개, 이상한 값(음수, 아주 긴 문자열, 없는 코드) 2개.
2. **조회 동작**만. 데이터를 바꾸는 요청(등록 · 삭제)은 스냅샷 방식으로 찍지 않는다.
3. 기대값을 적지 않는다. `toMatchSnapshot()` 하나로 끝낸다. 레거시 동작이 이상해 보여도 그대로 찍는다.
4. 실행마다 바뀌는 값이 스냅샷에 남으면 정규화를 고친다(`lib/normalize.mjs`). 스냅샷 파일을 손으로 고치지 않는다.
5. 케이스 이름(`it('…')`)은 "무엇을 겨냥했는지"가 드러나게 적는다. 이름이 스냅샷의 키가 된다. 이름을 바꾸면 스냅샷이 새로 찍힌다.
6. 주소를 직접 적지 않는다. 확인: `grep -rn "localhost:808" lib tests` 가 `lib/target.mjs` 의 `DEFAULT_BASE_URLS`(8081 · 8082 · 8083 세 줄) 만 찍어야 한다. `tests/` 에서는 한 줄도 나오지 않아야 한다.

## 베이스라인을 찍고 확인하는 순서

```bash
npm run baseline -- item-bank        # __snapshots__/item-bank.test.js.snap 생성
git status --short .                 # 테스트 파일과 스냅샷 파일만 보여야 한다
npm test                             # 초록
npm test                             # 한 번 더 초록 — 두 번 다 초록이어야 흔들리는 값이 없는 것
```

두 번째가 빨갛다면 실행마다 바뀌는 값이 섞인 것이다. 스냅샷을 열어 어떤 값이 달라졌는지 보고 정규화에 추가한다.

## 이관 후 비교

테스트와 스냅샷은 그대로 두고 **대상 주소만** 바꾼다.

```bash
TARGET_BASE_URL=http://localhost:8080 npm test
```

- 새 API 가 JSON 을 돌려줘도 같은 `{status, rows, count, message}` 로 정규화되므로 같은 스냅샷과 비교된다.
- 실패는 (a) 규칙 차이 (b) 형식 차이(필드 이름 · 타입 · 상태 코드) (c) 데이터 · 환경 차이로 나눠 본다. 고치는 곳은 이관 코드다. 테스트 · 스냅샷을 고쳐서 통과시키지 않는다.
- 확인: `git status --short .` 와 `git diff --stat .` 가 `characterization/` 에서 아무것도 찍지 않아야 한다.

## 안 될 때

- **`ECONNREFUSED`** — 대상 서비스가 꺼져 있다. 저장소 맨 위에서 `docker compose ps` 로 확인하고 프로필을 올린다(`docker compose --profile php up -d`). 새 API 대상이면 `./gradlew bootRun` 이 떠 있는지 본다.
- **`테스트 파일이 없습니다`** — `npm run baseline -- <모듈명>` 의 모듈명과 `tests/<모듈명>.test.js` 의 이름이 다르다.
- **스냅샷이 `tests/__snapshots__/` 에 생겼다** — `vitest.config.mjs` 의 `resolveSnapshotPath` 가 빠진 것이다. 이 저장소 설정은 `characterization/__snapshots__/` 로 모은다.
- **`Snapshot … obsolete`** — 케이스 이름을 바꾸거나 지운 뒤 옛 스냅샷이 남은 것. `npm run baseline -- <모듈명>` 으로 다시 찍는다.
