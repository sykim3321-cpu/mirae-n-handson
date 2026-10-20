# harness-baseline — 강사 제공 "기준 한 벌"

> **4회차(Day 4-1 · 4-2) 전에는 쓰지 않습니다.** 1~3회차 실습은 이 폴더를 보지 않고 직접 만드는 것이 목적입니다. Claude Code에게 이 폴더를 읽히거나 복사를 시키지 마세요.

## 무엇인가

1~3회차 교안을 처음부터 그대로 따라 했을 때 팀 저장소 `mirae-n-claude-harness` 에 쌓이는 결과물(3회차 종료 시점)을 강사가 한 벌로 만들어 둔 것입니다. 새로 만든 규칙은 없습니다. 교안 본문의 예시 코드와 프롬프트가 의도한 결과 그대로입니다.

| 이 폴더 | 복사한 뒤 이름 | 만든 차시 |
|---|---|---|
| `CLAUDE.baseline.md` | `CLAUDE.md` | Day 1-1 (+ Day 1-3 완료 기준) |
| `dot-claude/skills/convention-check/` | `.claude/skills/convention-check/` | Day 1-1 |
| `dot-claude/skills/document-module/` | `.claude/skills/document-module/` | Day 1-2 |
| `dot-claude/skills/verify/` | `.claude/skills/verify/` | Day 2-1 (+ Day 2-4 Sub-agent 연결) |
| `dot-claude/skills/rca/` (`SKILL.md`, `masking-rules.md`) | `.claude/skills/rca/` | Day 2-2 |
| `dot-claude/agents/reviewer.md`, `tester.md` | `.claude/agents/` | Day 2-4 |
| `dot-claude/skills/batch-check/`, `dot-claude/agents/iac-reviewer.md` | `.claude/…` | Day 3-2 (팀에 따라 둘 중 하나만 만듦) |
| `dot-claude/settings.json` | `.claude/settings.json` | Day 3-3 권한 + Day 3-4 Hook 등록 |
| `hooks/block-dangerous.mjs`, `hooks/secret-scan.mjs` | `hooks/` | Day 3-4 |
| `templates/` | `templates/` | 저장소 `templates/` 사본. `approval-checklist.md` · `verification-loop.md` 는 v1(Day 2-1 실습 4), `secure-coding-checklist.md` 는 v1(Day 3-4 실습 4) |

## 언제 쓰나

- Day 4-2 실습 6에서 팀 저장소에 빠진 파일이 있을 때(결석, 진도 차이). 빠진 것은 실력 문제가 아닙니다.
- Day 4-1에서 `/verify` 가 목록에 없을 때.

## 받는 법

```bash
cd ~/work/mirae-n-handson
git fetch upstream && git merge --no-edit upstream/main
ls harness-baseline
```

## 복사하는 법

`T` 에 복사할 프로젝트 폴더를 넣습니다. 아래는 Day 4-2의 `~/work/spec-project` 입니다. 한 줄씩 붙여 넣으세요.

```bash
T=~/work/spec-project
B=~/work/mirae-n-handson/harness-baseline
mkdir -p "$T/.claude"
cp -rn "$B/dot-claude/." "$T/.claude/"
cp -rn "$B/hooks" "$T/"
cp -rn "$B/templates" "$T/"
cp -n "$B/CLAUDE.baseline.md" "$T/CLAUDE.md"
ls -a "$T/.claude" "$T/.claude/skills" "$T/.claude/agents" "$T/hooks" "$T/templates"
```

- `-n` 은 이미 있는 파일을 덮어쓰지 않습니다. 팀 저장소에서 먼저 복사해 둔 파일이 있으면 그 파일이 남고, **빠진 파일만** 채워집니다. 건너뛴 파일이 있으면 `cp` 가 메시지 없이 종료 코드 1을 낼 수 있습니다(macOS에서 확인, 리눅스는 coreutils 버전에 따라 다름). 정상이므로 `&&` 로 잇지 말고 한 줄씩 실행합니다.
- `dot-claude/.` 처럼 끝에 `/.` 를 붙여야 대상에 `.claude/` 가 이미 있어도 그 안으로 합쳐집니다. `cp -rn dot-claude "$T/.claude"` 로 쓰면 `.claude/` 가 있을 때 `.claude/dot-claude/` 가 생깁니다.
- `hooks/` 를 빠뜨리면 `settings.json` 이 `"$CLAUDE_PROJECT_DIR"/hooks/…` 를 찾지 못해 Hook이 **오류 없이 조용히 통과**합니다. `templates/` 를 빠뜨리면 `verify` 와 `reviewer` 가 판정 기준(`templates/approval-checklist.md`)을 읽지 못합니다.
- 복사 뒤에는 Claude Code를 종료했다가 대상 폴더에서 다시 실행하고, 폴더 신뢰에 **예**를 고릅니다. 프로젝트 `settings.json` 의 allow 는 신뢰한 뒤에만 적용됩니다.

## 복사한 뒤 확인

```bash
cd "$T"
python3 -m json.tool .claude/settings.json > /dev/null && echo "settings OK"
echo '{"tool_name":"Bash","tool_input":{"command":"rm -rf /"}}' | node hooks/block-dangerous.mjs; echo "exit=$?"    # exit=2
echo '{"tool_name":"Bash","tool_input":{"command":"/bin/rm -rf build"}}' | node hooks/block-dangerous.mjs; echo "exit=$?"    # exit=2
echo '{"tool_name":"Bash","tool_input":{"command":"npm test"}}' | node hooks/block-dangerous.mjs; echo "exit=$?"    # exit=0
```

Claude Code 안에서는 `/permissions` 에 deny · ask · allow, `/hooks` 에 PreToolUse Hook 2개, `/` 목록에 `verify` 가 보이면 됩니다.

## 반드시 고칠 것 (Day 4-2 실습 6)

이 한 벌은 실습 저장소(`mirae-n-handson`) 기준입니다. 새 프로젝트에 그대로 두면 안 되는 곳이 있습니다.

- **`CLAUDE.md`** — 빌드 · 테스트 명령과 아키텍처 안내가 `modern/api` · `modern/web` · `characterization` 기준입니다. 5단계 프롬프트로 이 프로젝트에 맞춥니다.
- **`.claude/agents/tester.md` 와 `.claude/skills/verify/SKILL.md`** — 테스트 실행 위치와 명령이 `modern/api` · `modern/web` · `characterization`(`npm test`, `TARGET_BASE_URL=…`)으로 적혀 있습니다. 6단계 프롬프트로 이 프로젝트의 명령(Java `./gradlew test`, Python `pytest`)으로 바꾸고 `grep -n "modern/" .claude/agents/tester.md .claude/skills/verify/SKILL.md` 가 아무것도 출력하지 않는지 확인합니다.
- **`verify` 의 기본 비교 범위** — 인자가 없으면 `upstream/main...HEAD` 를 씁니다. 새 프로젝트에는 `upstream` 이 없으므로 `/verify <첫 커밋 해시>..HEAD` 처럼 범위를 넘깁니다(없으면 Skill이 범위를 묻습니다).
- **Python 프로젝트** — `settings.json` 의 allow 에 `"Bash(pytest *)"` 를 추가합니다(7단계).
- **MCP 규칙** — `mcp__dbhub` · `mcp__dbhub-mssql` · `mcp__item-bank__search_items` · `mcp__github__…` 는 Day 2-3에서 등록한 서버 이름입니다. 서버가 없으면 아무것도 매칭하지 않을 뿐 해는 없습니다.
- **개인 설정** — `.claude/settings.local.json` 은 올리지 않습니다: `echo '.claude/settings.local.json' >> .gitignore`

## 왜 `dot-claude/` · `CLAUDE.baseline.md` 인가 (알려진 주의점)

이 폴더는 실습 저장소 안에 있고, 참가자는 1회차부터 저장소 맨 위에서 `claude` 를 실행합니다. 이름을 `.claude/` · `CLAUDE.md` 로 두면 아래 동작 때문에 **완성본이 1회차 세션에 섞입니다.**

- 하위 폴더의 `CLAUDE.md` 는 Claude가 그 폴더의 파일을 읽을 때 로드됩니다(08 문법 레퍼런스 §12.1). Day 1-1의 `/init` · "최상위 폴더 설명" 요청이 이 폴더를 읽으면 완성된 팀 규칙이 초안에 들어갑니다.
- 하위 폴더의 `.claude/skills/` 는 시작할 때는 로드되지 않지만, Claude가 그 폴더의 파일을 처음 읽거나 고치는 순간 로드되어 세션 끝까지 남습니다(공식 문서 skills.md "nested directories"). 그러면 참가자가 만들기도 전에 `/verify`, `/rca` 등이 목록에 나타납니다.

그래서 Claude Code가 인식하지 않는 이름으로 두고, 복사할 때 이름을 바꿉니다. 이 폴더의 `settings.json` 은 하위 폴더에 있으므로 어느 이름이든 실습 저장소 세션에 적용되지 않습니다. `hooks/` · `templates/` 는 이름만으로는 로드되지 않으므로 그대로 둡니다.

남는 한계: Claude가 이 폴더의 파일을 **일반 파일로** 읽는 것까지 막지는 못합니다. 1~3회차에는 이 폴더를 읽히지 마세요.
