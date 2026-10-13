#!/usr/bin/env bash
# 사전 준비 셀프 점검 (Day 0 준비 10 · Day 1-1 실습 1)
#
#   cd ~/work/mirae-n-handson && bash scripts/check-env.sh
#
# - 항목이 실패해도 끝까지 돈다(set -e 를 쓰지 않는다).
# - 항목마다 ✅ 통과 / ❌ 실패 / ⚠️ 경고 를 찍고, 실패 항목에는 돌아갈 Day 0 준비 번호를 붙인다.
# - 마지막 한 줄: "모든 항목 통과" 또는 "실패 N건 — 위 항목을 확인하세요". 실패가 있으면 종료 코드 1.
# - bash 전용, macOS 기본 도구(BSD)와 GNU 모두에서 돈다. jq 같은 추가 도구를 쓰지 않는다.
#
# 환경 변수
#   CHECK_ENV_ALLOW_ANY_DIR=1   저장소가 ~/work/mirae-n-handson 이 아니어도 실패로 보지 않는다(강사용). /mnt/c 아래는 여전히 실패.
#   CHECK_ENV_SKIP_DOCKER=1     샘플 서비스 기동 항목을 건너뛴다(경고로 표시).

REPO_ROOT="$(cd "$(dirname "$0")/.." 2>/dev/null && pwd -P)"
EXPECTED_DIR="$HOME/work/mirae-n-handson"
SAMPLE_URL="http://localhost:8081"
SAMPLE_PROFILE="php"
SAMPLE_WAIT_SEC=90

FAIL_COUNT=0      # 실패한 세부 항목 수
SECTION_FAILS=0   # 실패한 점검 항목(1~6) 수
WARN_COUNT=0

pass() { printf '  ✅ %s\n' "$1"; }
warn() { printf '  ⚠️  %s\n' "$1"; WARN_COUNT=$((WARN_COUNT + 1)); }
fail() { printf '  ❌ %s\n' "$1"; FAIL_COUNT=$((FAIL_COUNT + 1)); }
hint() { printf '     → 돌아갈 곳: %s\n' "$1"; SECTION_FAILS=$((SECTION_FAILS + 1)); }
section() { printf '\n[%s] %s\n' "$1" "$2"; }

has() { command -v "$1" >/dev/null 2>&1; }

# 첫 번째 "숫자.숫자(.숫자)" 조각만 뽑는다.  ex) "openjdk version \"21.0.4\"" → 21.0.4
first_version() { printf '%s' "$1" | tr -d '\r' | grep -oE '[0-9]+\.[0-9]+(\.[0-9]+)*' | head -1; }
major_of() { printf '%s' "$1" | cut -d. -f1; }
minor_of() { printf '%s' "$1" | cut -d. -f2; }

IS_MACOS=0
[ "$(uname -s 2>/dev/null)" = "Darwin" ] && IS_MACOS=1

printf '=== 사전 준비 셀프 점검 ===\n'
printf '저장소: %s\n' "$REPO_ROOT"
printf '실행 시각: %s\n' "$(date '+%Y-%m-%d %H:%M:%S')"

# ──────────────────────────────────────────────────────────────────────────
section 1 "런타임 버전 — Git · GitHub CLI · JDK 21 · Node.js 22 · Python 3.12 · Docker · Docker Compose"
# ──────────────────────────────────────────────────────────────────────────
SECTION_FAIL=0

if has git; then
  pass "Git $(first_version "$(git --version 2>&1)")"
else
  fail "Git 이 없습니다"; SECTION_FAIL=1
fi

if has gh; then
  pass "GitHub CLI $(first_version "$(gh --version 2>&1 | head -1)")"
else
  fail "GitHub CLI(gh) 가 없습니다"; SECTION_FAIL=1
fi

JAVA_OK=0
if has java; then
  JAVA_OUT="$(java -version 2>&1 | grep -i " version " | head -1)"
  [ -z "$JAVA_OUT" ] && JAVA_OUT="$(java -version 2>&1 | head -1)"
  JAVA_VER="$(first_version "$JAVA_OUT")"
  if [ -z "$JAVA_VER" ]; then
    # macOS 의 /usr/bin/java 는 JDK 가 없어도 존재하는 안내용 실행 파일이다.
    fail "JDK(java) 를 실행할 수 없습니다: $JAVA_OUT"; SECTION_FAIL=1
  elif [ "$(major_of "$JAVA_VER")" = "21" ]; then
    pass "JDK $JAVA_VER"; JAVA_OK=1
  else
    fail "JDK 21 이 필요합니다 (지금: $JAVA_VER)"; SECTION_FAIL=1
  fi
else
  fail "JDK(java) 가 없습니다"; SECTION_FAIL=1
fi

if has node; then
  NODE_VER="$(first_version "$(node -v 2>&1)")"
  NODE_MAJOR="$(major_of "$NODE_VER")"; NODE_MINOR="$(minor_of "$NODE_VER")"
  if [ "${NODE_MAJOR:-0}" -eq 22 ] 2>/dev/null && [ "${NODE_MINOR:-0}" -ge 5 ] 2>/dev/null; then
    pass "Node.js $NODE_VER"
  elif [ "${NODE_MAJOR:-0}" -gt 22 ] 2>/dev/null; then
    pass "Node.js $NODE_VER (22 이상)"
  else
    fail "Node.js 22.5 이상이 필요합니다 (지금: ${NODE_VER:-알 수 없음})"; SECTION_FAIL=1
  fi
else
  fail "Node.js(node) 가 없습니다"; SECTION_FAIL=1
fi

PY_BIN=""
if has python3; then PY_BIN=python3; elif has python; then PY_BIN=python; fi
if [ -n "$PY_BIN" ]; then
  PY_VER="$(first_version "$("$PY_BIN" --version 2>&1)")"
  if [ "$(major_of "$PY_VER")" = "3" ] && [ "$(minor_of "$PY_VER")" = "12" ]; then
    pass "Python $PY_VER"
  elif [ "$IS_MACOS" = "1" ]; then
    warn "Python 3.12 가 아닙니다 (지금: ${PY_VER:-알 수 없음}) — macOS 는 경고로만 표시합니다"
  else
    fail "Python 3.12 가 필요합니다 (지금: ${PY_VER:-알 수 없음})"; SECTION_FAIL=1
  fi
else
  if [ "$IS_MACOS" = "1" ]; then
    warn "Python 이 없습니다 — macOS 는 경고로만 표시합니다"
  else
    fail "Python(python3) 이 없습니다"; SECTION_FAIL=1
  fi
fi

if has docker; then
  pass "Docker $(first_version "$(docker --version 2>&1)")"
  COMPOSE_OUT="$(docker compose version 2>&1)"
  if [ $? -eq 0 ]; then
    pass "Docker Compose $(first_version "$COMPOSE_OUT")"
  else
    fail "Docker Compose(docker compose) 를 쓸 수 없습니다: $COMPOSE_OUT"; SECTION_FAIL=1
  fi
else
  fail "Docker 가 없습니다"; SECTION_FAIL=1
fi

[ "$SECTION_FAIL" = "1" ] && hint "Day 0 준비 2 (Git · GitHub CLI · JDK · Node · Python) · 준비 3 (Docker)"

# ──────────────────────────────────────────────────────────────────────────
section 2 "Claude Code 로그인 — claude 설치 + 구독 계정 로그인"
# ──────────────────────────────────────────────────────────────────────────
SECTION_FAIL=0
if has claude; then
  CLAUDE_VER="$(claude --version 2>&1 | head -1)"
  pass "Claude Code 설치됨: $CLAUDE_VER"
  VER_NUM="$(printf '%s' "$CLAUDE_VER" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)"
  if [ -n "$VER_NUM" ] && [ "$(printf '%s\n%s\n' "2.1.280" "$VER_NUM" | sort -V | head -1)" != "2.1.280" ]; then
    warn "Claude Code $VER_NUM 은 교안 기준(2.1.280 이상, Opus 5.5)보다 낮습니다 — claude update 후 터미널을 새로 여세요 (Day 0 준비 6)"
  fi
  AUTH_OUT="$(claude auth status 2>&1)"
  AUTH_RC=$?
  if [ $AUTH_RC -eq 0 ] && ! printf '%s' "$AUTH_OUT" | grep -qi '"loggedIn": *false'; then
    pass "Claude Code 로그인 상태"
  elif printf '%s' "$AUTH_OUT" | grep -qiE 'unknown (command|option)|not (a )?(valid|recognized)|error: unknown'; then
    warn "로그인 상태를 확인할 수 없습니다 (이 버전은 'claude auth status' 를 지원하지 않음). 터미널에서 claude 를 열어 로그인돼 있는지 직접 확인하세요"
  else
    fail "Claude Code 에 로그인돼 있지 않습니다 (claude auth status 종료 코드 $AUTH_RC)"; SECTION_FAIL=1
  fi
else
  fail "claude 명령이 없습니다 (Claude Code 미설치 또는 PATH 문제)"; SECTION_FAIL=1
fi
[ "$SECTION_FAIL" = "1" ] && hint "Day 0 준비 5"

# ──────────────────────────────────────────────────────────────────────────
section 3 "샘플 저장소 위치 — ~/work/mirae-n-handson 에 있고 /mnt/c 아래가 아님"
# ──────────────────────────────────────────────────────────────────────────
SECTION_FAIL=0
case "$REPO_ROOT" in
  /mnt/c/*|/mnt/d/*|/mnt/e/*)
    fail "저장소가 Windows 드라이브($REPO_ROOT) 아래에 있습니다. WSL 홈 아래(~/work)에 다시 clone 하세요"; SECTION_FAIL=1 ;;
  *)
    pass "Windows 드라이브(/mnt/c) 아래가 아님" ;;
esac

EXPECTED_REAL="$(cd "$EXPECTED_DIR" 2>/dev/null && pwd -P)"
if [ -n "$EXPECTED_REAL" ] && [ "$REPO_ROOT" = "$EXPECTED_REAL" ]; then
  pass "위치: $REPO_ROOT"
elif [ "${CHECK_ENV_ALLOW_ANY_DIR:-0}" = "1" ]; then
  warn "위치가 $EXPECTED_DIR 이 아닙니다 ($REPO_ROOT) — CHECK_ENV_ALLOW_ANY_DIR=1 이므로 실패로 보지 않음"
else
  fail "저장소가 $EXPECTED_DIR 에 있어야 합니다 (지금: $REPO_ROOT)"; SECTION_FAIL=1
fi

if [ -d "$REPO_ROOT/.git" ] || git -C "$REPO_ROOT" rev-parse --git-dir >/dev/null 2>&1; then
  pass "git 저장소 확인"
else
  fail "git 저장소가 아닙니다 (.git 없음)"; SECTION_FAIL=1
fi
[ "$SECTION_FAIL" = "1" ] && hint "Day 0 준비 8"

# ──────────────────────────────────────────────────────────────────────────
section 4 "GitHub 로그인 · 원격 — gh auth status, git remote 의 origin · upstream"
# ──────────────────────────────────────────────────────────────────────────
SECTION_FAIL=0
if has gh; then
  if gh auth status >/dev/null 2>&1; then
    pass "GitHub CLI 로그인 상태"
  else
    fail "GitHub CLI 에 로그인돼 있지 않습니다 (gh auth login)"; SECTION_FAIL=1
  fi
else
  fail "GitHub CLI(gh) 가 없어 로그인 상태를 확인할 수 없습니다"; SECTION_FAIL=1
fi

REMOTES="$(git -C "$REPO_ROOT" remote 2>/dev/null)"
HAS_ORIGIN=0; HAS_UPSTREAM=0
printf '%s\n' "$REMOTES" | grep -qx origin && HAS_ORIGIN=1
printf '%s\n' "$REMOTES" | grep -qx upstream && HAS_UPSTREAM=1
if [ "$HAS_ORIGIN" = "1" ] && [ "$HAS_UPSTREAM" = "1" ]; then
  pass "원격 origin · upstream 있음"
elif [ "${CHECK_ENV_ALLOW_ANY_DIR:-0}" = "1" ]; then
  warn "원격 origin/upstream 이 갖춰지지 않음 (origin=$HAS_ORIGIN upstream=$HAS_UPSTREAM) — CHECK_ENV_ALLOW_ANY_DIR=1 이므로 실패로 보지 않음"
else
  fail "git remote 에 origin 과 upstream 이 모두 있어야 합니다 (origin=$HAS_ORIGIN upstream=$HAS_UPSTREAM)"; SECTION_FAIL=1
fi
[ "$SECTION_FAIL" = "1" ] && hint "Day 0 준비 7 · 준비 8"

# ──────────────────────────────────────────────────────────────────────────
section 5 "샘플 서비스 기동 — docker compose --profile $SAMPLE_PROFILE up -d → $SAMPLE_URL 응답 → 내리기"
# ──────────────────────────────────────────────────────────────────────────
SECTION_FAIL=0
STARTED_BY_US=0
PRE_RUNNING=""
if [ "${CHECK_ENV_SKIP_DOCKER:-0}" = "1" ]; then
  warn "CHECK_ENV_SKIP_DOCKER=1 — 샘플 서비스 기동을 건너뜁니다"
elif ! has docker; then
  fail "Docker 가 없어 샘플 서비스를 띄울 수 없습니다"; SECTION_FAIL=1
elif [ ! -f "$REPO_ROOT/docker-compose.yml" ] && [ ! -f "$REPO_ROOT/compose.yml" ] && [ ! -f "$REPO_ROOT/compose.yaml" ] && [ ! -f "$REPO_ROOT/docker-compose.yaml" ]; then
  fail "저장소에 docker-compose.yml 이 없습니다"; SECTION_FAIL=1
else
  DOCKER_INFO="$(docker info 2>&1 >/dev/null)"
  if [ $? -ne 0 ]; then
    printf '%s\n' "$DOCKER_INFO" | head -3 | sed 's/^/     /'
    fail "Docker 데몬에 연결할 수 없습니다. Docker Desktop 이 켜져 있는지 확인하세요"; SECTION_FAIL=1
  else
    pass "Docker 데몬 연결"
    cd "$REPO_ROOT" || true

    PRE_RUNNING="$(docker compose --profile "$SAMPLE_PROFILE" ps --services --status running 2>/dev/null)"
    if printf '%s\n' "$PRE_RUNNING" | grep -qx item-bank; then
      pass "샘플 서비스(item-bank)가 이미 떠 있음 — 점검 뒤에도 그대로 둡니다"
    else
      printf '     기동 중: docker compose --profile %s up -d (처음에는 이미지 빌드 · 내려받기로 몇 분 걸릴 수 있습니다)\n' "$SAMPLE_PROFILE"
      UP_OUT="$(docker compose --profile "$SAMPLE_PROFILE" up -d 2>&1)"
      UP_RC=$?
      if [ $UP_RC -ne 0 ]; then
        printf '%s\n' "$UP_OUT" | tail -5 | sed 's/^/     /'
        fail "docker compose --profile $SAMPLE_PROFILE up -d 실패 (종료 코드 $UP_RC)"; SECTION_FAIL=1
      else
        STARTED_BY_US=1
        pass "docker compose --profile $SAMPLE_PROFILE up -d"
      fi
    fi

    if [ "$SECTION_FAIL" = "0" ]; then
      HTTP_CODE=000
      ELAPSED=0
      while [ "$ELAPSED" -lt "$SAMPLE_WAIT_SEC" ]; do
        HTTP_CODE="$(curl -s -o /dev/null --max-time 3 -w '%{http_code}' "$SAMPLE_URL" 2>/dev/null)"
        case "$HTTP_CODE" in 200|302) break ;; esac
        sleep 3
        ELAPSED=$((ELAPSED + 3))
      done
      case "$HTTP_CODE" in
        200|302) pass "$SAMPLE_URL 응답 HTTP $HTTP_CODE (${ELAPSED}초)" ;;
        *)
          fail "$SAMPLE_URL 이 ${SAMPLE_WAIT_SEC}초 안에 응답하지 않았습니다 (마지막 코드: ${HTTP_CODE:-000}). 'docker compose --profile $SAMPLE_PROFILE logs' 로 확인하세요"; SECTION_FAIL=1 ;;
      esac
    fi

    if [ "$STARTED_BY_US" = "1" ]; then
      if [ -z "$PRE_RUNNING" ]; then
        DOWN_OUT="$(docker compose --profile "$SAMPLE_PROFILE" down 2>&1)"
        if [ $? -eq 0 ]; then
          pass "docker compose --profile $SAMPLE_PROFILE down (점검 전 떠 있던 컨테이너 없음 → 전부 내림)"
        else
          printf '%s\n' "$DOWN_OUT" | tail -3 | sed 's/^/     /'
          warn "샘플 서비스를 내리지 못했습니다. 'docker compose --profile $SAMPLE_PROFILE down' 을 직접 실행하세요"
        fi
      else
        # 점검 전에 이미 떠 있던 서비스(예: modern 프로필의 mariadb)는 건드리지 않고, 우리가 새로 올린 것만 내린다.
        NOW_RUNNING="$(docker compose --profile "$SAMPLE_PROFILE" ps --services --status running 2>/dev/null)"
        TO_STOP=""
        for svc in $NOW_RUNNING; do
          printf '%s\n' "$PRE_RUNNING" | grep -qx "$svc" || TO_STOP="$TO_STOP $svc"
        done
        if [ -n "$TO_STOP" ]; then
          # shellcheck disable=SC2086
          docker compose --profile "$SAMPLE_PROFILE" rm -s -f $TO_STOP >/dev/null 2>&1
          pass "새로 올린 서비스만 내림:$TO_STOP (이미 떠 있던 것은 그대로)"
        fi
      fi
    fi
  fi
fi
[ "$SECTION_FAIL" = "1" ] && hint "Day 0 준비 3 · 준비 9"

# ──────────────────────────────────────────────────────────────────────────
section 6 "테스트 1회 실행 — modern/api ./gradlew test, characterization npm ci"
# ──────────────────────────────────────────────────────────────────────────
SECTION_FAIL=0
if [ ! -d "$REPO_ROOT/modern/api" ] || [ ! -f "$REPO_ROOT/modern/api/gradlew" ]; then
  fail "modern/api/gradlew 가 없습니다 (저장소가 불완전합니다 — 다시 clone/pull)"; SECTION_FAIL=1
elif [ "$JAVA_OK" != "1" ]; then
  fail "JDK 21 이 없어 ./gradlew test 를 돌릴 수 없습니다 (1번 항목을 먼저 고치세요)"; SECTION_FAIL=1
else
  printf '     실행 중: (cd modern/api && ./gradlew test -q) — 처음에는 라이브러리 내려받기로 몇 분 걸릴 수 있습니다\n'
  GRADLE_OUT="$(cd "$REPO_ROOT/modern/api" && ./gradlew test -q --console=plain 2>&1)"
  GRADLE_RC=$?
  if [ $GRADLE_RC -eq 0 ]; then
    pass "modern/api ./gradlew test 통과"
  else
    printf '%s\n' "$GRADLE_OUT" | grep -v '^$' | tail -8 | sed 's/^/     /'
    fail "modern/api ./gradlew test 실패 (종료 코드 $GRADLE_RC)"; SECTION_FAIL=1
  fi
fi

NPM_OK=0
if [ ! -f "$REPO_ROOT/characterization/package.json" ]; then
  fail "characterization/package.json 이 없습니다 (저장소가 불완전합니다)"; SECTION_FAIL=1
elif ! has npm; then
  fail "npm 이 없어 characterization 의존성을 설치할 수 없습니다"; SECTION_FAIL=1
else
  NPM_OK=1
  if [ ! -d "$REPO_ROOT/characterization/node_modules" ]; then
    printf '     실행 중: (cd characterization && npm ci)\n'
    NPM_OUT="$(cd "$REPO_ROOT/characterization" && npm ci --silent --no-audit --no-fund 2>&1)"
    NPM_RC=$?
    if [ $NPM_RC -eq 0 ]; then
      pass "characterization npm ci"
    else
      printf '%s\n' "$NPM_OUT" | tail -5 | sed 's/^/     /'
      fail "characterization npm ci 실패 (종료 코드 $NPM_RC)"; SECTION_FAIL=1; NPM_OK=0
    fi
  else
    pass "characterization node_modules 있음 (의존성 설치 완료)"
  fi
  if [ "$NPM_OK" = "1" ] && [ -f "$REPO_ROOT/characterization/tests/normalize.unit.test.js" ]; then
    UNIT_OUT="$(cd "$REPO_ROOT/characterization" && npx vitest run tests/normalize.unit.test.js 2>&1)"
    if [ $? -eq 0 ]; then
      pass "characterization 정규화 단위 테스트 통과 (서비스 없이 도는 테스트)"
    else
      printf '%s\n' "$UNIT_OUT" | tail -8 | sed 's/^/     /'
      fail "characterization 단위 테스트 실패"; SECTION_FAIL=1
    fi
  fi
fi
[ "$SECTION_FAIL" = "1" ] && hint "Day 0 준비 2 · 준비 9의 6단계"

# ──────────────────────────────────────────────────────────────────────────
printf '\n=== 결과 ===\n'
if [ "$WARN_COUNT" -gt 0 ]; then
  printf '경고 %d건 (실패 아님)\n' "$WARN_COUNT"
fi
if [ "$SECTION_FAILS" -eq 0 ]; then
  printf '모든 항목 통과\n'
  exit 0
else
  printf '실패 %d건 — 위 항목을 확인하세요\n' "$SECTION_FAILS"
  exit 1
fi
