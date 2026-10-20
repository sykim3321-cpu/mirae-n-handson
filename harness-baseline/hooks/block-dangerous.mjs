#!/usr/bin/env node
// hooks/block-dangerous.mjs
// PreToolUse(Bash) 훅: 위험한 셸 명령을 실행 전에 차단한다.
// 차단 = exit 2 + stderr 메시지(Claude에게 거부 사유로 전달됨)
import { readFileSync } from "node:fs";

let input;
try {
  input = JSON.parse(readFileSync(0, "utf8")); // fd 0 = stdin
} catch {
  // stdin JSON을 읽지 못하면 판단하지 않고 통과(fail-open)시킨다.
  process.exit(0);
}

if (input.tool_name !== "Bash") process.exit(0);

const command = String(input.tool_input?.command ?? "");

const RULES = [
  { pattern: /rm\s+-rf/, reason: "rm -rf 는 금지되어 있습니다. 삭제 대상을 구체적으로 지정하거나 사람에게 요청하세요." },
  { pattern: /DROP\s+(TABLE|DATABASE)/i, reason: "DROP TABLE / DROP DATABASE 는 금지되어 있습니다." },
  { pattern: /prod[-.]db/, reason: "운영 DB 호스트(prod-db / prod.db)에 대한 접근은 금지되어 있습니다." },
];

for (const { pattern, reason } of RULES) {
  if (pattern.test(command)) {
    console.error(`[block-dangerous] 차단됨: ${reason}\n실행하려던 명령: ${command}`);
    process.exit(2);
  }
}

process.exit(0);
