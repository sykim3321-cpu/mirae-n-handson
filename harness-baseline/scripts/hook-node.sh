#!/usr/bin/env bash
# Hook 실행기 — node 를 찾아 Hook 스크립트를 실행한다. node 를 끝내 못 찾으면 명령을 막는다(fail-closed).
# node 가 PATH 에 없는 셸(VS Code 통합 터미널, wsl -e claude 등)에서 claude 를 띄워도 Hook 이 조용히 꺼지지 않게 한다.
# 사용(.claude/settings.json 의 hooks command): bash "$CLAUDE_PROJECT_DIR"/scripts/hook-node.sh hooks/block-dangerous.mjs
cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/..}" || exit 2
NODE="$(command -v node 2>/dev/null)"
if [ -z "$NODE" ] && [ -s "$HOME/.nvm/nvm.sh" ]; then
  . "$HOME/.nvm/nvm.sh" >/dev/null 2>&1
  NODE="$(command -v node 2>/dev/null)"
fi
for c in ${HOOK_NODE_CANDIDATES-/opt/homebrew/bin/node /usr/local/bin/node /usr/bin/node}; do
  if [ -z "$NODE" ] && [ -x "$c" ]; then NODE="$c"; fi
done
if [ -z "$NODE" ]; then
  echo "[hook] node 를 찾지 못해 이 명령을 막습니다. Ubuntu(macOS는 터미널) 창에서 claude 를 다시 실행하세요." >&2
  exit 2
fi
exec "$NODE" "$@"
