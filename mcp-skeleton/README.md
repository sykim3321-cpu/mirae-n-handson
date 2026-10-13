# mcp-skeleton — 문항 은행 MCP 서버 골격

Day 2-3 실습 3 · 4에서 쓰는 TypeScript MCP 서버 골격입니다. stdio로 동작하고, 외부 서비스 없이 메모리 안의 더미 문항으로 응답합니다.

> **SDK v2 기준입니다.** `@modelcontextprotocol/server@2.0.0` · `zod@4.6.5` 가 `package.json` 과 `package-lock.json` 에 고정돼 있습니다. 인터넷 예제 대부분은 v1(`@modelcontextprotocol/sdk`)이라 import 경로와 `inputSchema` 모양이 다릅니다. v1 예제를 그대로 붙이면 빌드가 깨집니다.

## 파일

| 파일 | 내용 |
| --- | --- |
| `src/index.ts` | stdio 서버. 예시 도구 `ping` 하나. "여기에 도구를 등록합니다" 주석 자리에 도구를 추가합니다 |
| `src/itemApi.ts` | 감쌀 API(더미). `searchItems({ keyword, unit?, difficulty?, limit? })` → `{ id, unit, difficulty, tags, stem }[]`, 심화용 `updateItemTags(id, tags)` |

더미 문항의 `id` · 단원 코드(`M5-1` … `M6-2`)는 `itembank` DB와 같습니다. 난이도는 DB의 `level`(정수 1~5)과 달리 문자열 `하` · `중` · `상` 입니다.

## 빌드 · 실행

```bash
npm ci
npx tsc
ls build        # index.js 가 보이면 성공
```

`node build/index.js` 를 그냥 실행하면 멈춘 것처럼 보이는데 정상입니다(stdin 대기). `Ctrl` + `C` 로 끝냅니다.

## 스모크 테스트

```bash
(printf '%s\n' \
  '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"smoke","version":"0.0.1"}}}' \
  '{"jsonrpc":"2.0","method":"notifications/initialized"}' \
  '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'; sleep 2) | node build/index.js
```

JSON 두 줄이 돌아옵니다. 둘째 줄의 `"tools":[...]` 에 지금은 `ping` 만 있습니다.

## 도구 추가

`src/index.ts` 의 `여기에 도구를 등록합니다` 주석 아래에 `server.registerTool('이름', { description, inputSchema }, handler)` 형태로 등록합니다.

- `inputSchema` 는 `z.object({...})` 전체 스키마, import 는 `import * as z from 'zod/v4'`
- API import 는 `./itemApi.js` (ESM이라 확장자를 `.js` 로 적습니다)
- 로그는 `console.error` 만. `console.log` 는 stdout(프로토콜 채널)을 깨뜨립니다
- 고친 뒤에는 `npx tsc` 로 다시 빌드합니다

`build/` 와 `node_modules/` 는 커밋하지 않습니다(루트 `.gitignore`).
