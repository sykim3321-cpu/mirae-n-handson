#!/usr/bin/env node
// hooks/secret-scan.mjs
// PreToolUse(Bash) 훅: `git commit` 직전에 스테이징된 변경(git diff --cached)에서
// 시크릿 패턴을 찾으면 커밋을 차단한다. 차단 = exit 2 + stderr 메시지
import { readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";

let input;
try {
  input = JSON.parse(readFileSync(0, "utf8")); // fd 0 = stdin
} catch {
  process.exit(0); // 입력을 읽지 못하면 통과(fail-open)
}

if (input.tool_name !== "Bash") process.exit(0);

const command = String(input.tool_input?.command ?? "").trim();
if (!command.startsWith("git commit")) process.exit(0);

let diff = "";
try {
  diff = execFileSync("git", ["diff", "--cached", "--no-color", "--no-ext-diff"], {
    cwd: input.cwd || process.cwd(), // stdin JSON의 cwd = Claude의 현재 작업 디렉터리
    encoding: "utf8",
    maxBuffer: 50 * 1024 * 1024,
  });
} catch {
  process.exit(0); // git 저장소가 아니거나 git 실행 실패: 통과
}

// 추가된 줄만 검사한다("+++ b/파일" 헤더 제외). 삭제되는 줄은 차단 사유가 아니다.
const added = diff
  .split("\n")
  .filter((line) => line.startsWith("+") && !line.startsWith("+++"));

const PATTERNS = [
  { name: "AWS Access Key ID", re: /AKIA[0-9A-Z]{16}/ },
  { name: "하드코딩된 password", re: /password\s*=\s*['"][^'"]+/i },
  { name: "PEM 개인키", re: /-----BEGIN (?:[A-Z]+ )?PRIVATE KEY-----/ },
];

// 시크릿 값 자체는 출력하지 않는다(메시지는 Claude 컨텍스트로 들어간다). 패턴 이름과 건수만 보고한다.
const counts = new Map();
for (const line of added) {
  for (const { name, re } of PATTERNS) {
    if (re.test(line)) counts.set(name, (counts.get(name) ?? 0) + 1);
  }
}
const findings = [...counts].map(([name, n]) => `- ${name}: ${n}건`);

if (findings.length > 0) {
  console.error(
    `[secret-scan] 커밋 차단: 스테이징된 변경에서 시크릿으로 보이는 값이 발견되었습니다.\n` +
      `${findings.join("\n")}\n` +
      `해당 값을 제거하고 환경 변수 등으로 옮긴 뒤 다시 git add 하세요.`
  );
  process.exit(2);
}

process.exit(0);
