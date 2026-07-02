#!/usr/bin/env bash
#
# Run every generated test suite in this directory.
#
# Usage:
#   ./run-all-tests.sh              # all suites; continue on failure (default)
#   ./run-all-tests.sh --fail-fast  # stop on first suite failure
#   ./run-all-tests.sh --compile-only
#   RUN_SKIP_UNIT=1 ./run-all-tests.sh
#
# Environment:
#   TEST_ENV_FILE          — defaults to ./test-env.properties
#   test-env.local.properties — loaded when present (secrets / riyaz IDs)
#   RUN_SKIP_SMOKE=1       — skip quick createArticle smoke
#   RUN_SKIP_MAVEN=1       — skip JUnit modules (unit, integration, nfr, performance, security)
#   RUN_SKIP_UNIT=1        — skip unit module only
#   RUN_SKIP_INTEGRATION=1
#   RUN_SKIP_NFR=1
#   RUN_SKIP_PERFORMANCE=1
#   RUN_SKIP_SECURITY=1
#   RUN_SKIP_SEQUENCE_JAVA=1
#   RUN_SKIP_SCHEMATHESIS=1
#   RUN_SKIP_POSTMAN=1     — skip Newman collection
#
set -uo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
export TEST_ENV_FILE="${TEST_ENV_FILE:-$ROOT/test-env.properties}"

FAIL_FAST=0
COMPILE_ONLY=0
for arg in "$@"; do
  case "$arg" in
    --fail-fast) FAIL_FAST=1 ;;
    --compile-only) COMPILE_ONLY=1 ;;
    -h|--help)
      sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      echo "Unknown option: $arg (try --help)" >&2
      exit 2
      ;;
  esac
done

# Suite result tracking: name -> PASS|FAIL|SKIP
declare -a SUITE_NAMES=()
declare -a SUITE_STATUS=()

log() { printf '%s\n' "$*"; }
log_section() { log ""; log "========== $* =========="; }

load_properties_file() {
  local file="$1"
  [[ -f "$file" ]] || return 0
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%%#*}"
    line="$(echo "$line" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
    [[ -z "$line" || "$line" != *"="* ]] && continue
    local key="${line%%=*}"
    local value="${line#*=}"
    key="$(echo "$key" | tr -d '[:space:]')"
    value="$(echo "$value" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
    case "$key" in
      base.url|accept.language|tls.verify|auth.login.base|auth.username|auth.password|auth.token)
        export "PROP_${key//./_}=$value"
        ;;
    esac
  done < "$file"
}

load_properties_file "$TEST_ENV_FILE"
load_properties_file "$ROOT/test-env.local.properties"

record_suite() {
  SUITE_NAMES+=("$1")
  SUITE_STATUS+=("$2")
}

run_maven_module() {
  local name="$1"
  local dir="$ROOT/$2"
  if [[ ! -d "$dir" || ! -f "$dir/pom.xml" ]]; then
    log "SKIP $name — missing $dir/pom.xml"
    record_suite "$name" "SKIP"
    return 0
  fi
  log_section "Maven: $name ($2)"
  local start=$SECONDS
  if (cd "$dir" && mvn -q test); then
    log "OK $name (${SECONDS - start}s)"
    record_suite "$name" "PASS"
    return 0
  fi
  log "FAIL $name (${SECONDS - start}s) — see $dir/target/surefire-reports/"
  record_suite "$name" "FAIL"
  return 1
}

compile_all_modules() {
  log_section "Compile check (test-compile)"
  local fail=0
  for dir in integration unit nfr performance security sequence-java; do
    if [[ ! -f "$ROOT/$dir/pom.xml" ]]; then
      log "SKIP compile $dir — no pom.xml"
      continue
    fi
    if (cd "$ROOT/$dir" && mvn -q test-compile); then
      log "  compile OK: $dir"
    else
      log "  compile FAIL: $dir"
      fail=1
    fi
  done
  return "$fail"
}

run_smoke() {
  log_section "Smoke: createArticle (integration)"
  local start=$SECONDS
  if (cd "$ROOT/integration" && mvn -q test \
      -Dtest='ArticlesIntegrationTest#testCreateArticle_Success_ClientApplication'); then
    log "OK smoke createArticle (${SECONDS - start}s)"
    record_suite "smoke-createArticle" "PASS"
    return 0
  fi
  log "FAIL smoke createArticle (${SECONDS - start}s)"
  record_suite "smoke-createArticle" "FAIL"
  return 1
}

run_schemathesis() {
  local script="$ROOT/schemathesis/run-schemathesis.sh"
  if [[ "${RUN_SKIP_SCHEMATHESIS:-0}" == "1" ]]; then
    record_suite "schemathesis" "SKIP"
    return 0
  fi
  if [[ ! -x "$script" ]]; then
    log "SKIP schemathesis — $script not executable"
    record_suite "schemathesis" "SKIP"
    return 0
  fi
  if ! command -v schemathesis >/dev/null 2>&1; then
    log "SKIP schemathesis — schemathesis CLI not installed"
    record_suite "schemathesis" "SKIP"
    return 0
  fi
  log_section "Schemathesis"
  local start=$SECONDS
  if (cd "$ROOT/schemathesis" && ./run-schemathesis.sh); then
    log "OK schemathesis (${SECONDS - start}s)"
    record_suite "schemathesis" "PASS"
    return 0
  fi
  log "FAIL schemathesis (${SECONDS - start}s)"
  record_suite "schemathesis" "FAIL"
  return 1
}

run_postman() {
  if [[ "${RUN_SKIP_POSTMAN:-0}" == "1" ]]; then
    record_suite "postman-newman" "SKIP"
    return 0
  fi
  if [[ ! -x "$ROOT/run-tests.sh" ]]; then
    log "SKIP postman — run-tests.sh not found"
    record_suite "postman-newman" "SKIP"
    return 0
  fi
  log_section "Postman (Newman)"
  local start=$SECONDS
  if (cd "$ROOT" && ./run-tests.sh); then
    log "OK postman (${SECONDS - start}s)"
    record_suite "postman-newman" "PASS"
    return 0
  fi
  log "FAIL postman (${SECONDS - start}s)"
  record_suite "postman-newman" "FAIL"
  return 1
}

on_suite_fail() {
  if [[ "$FAIL_FAST" == "1" ]]; then
    print_summary
    exit 1
  fi
}

print_summary() {
  log ""
  log_section "Summary"
  local pass=0 fail=0 skip=0
  local i
  for i in "${!SUITE_NAMES[@]}"; do
    local st="${SUITE_STATUS[$i]}"
    printf '  %-24s %s\n' "${SUITE_NAMES[$i]}" "$st"
    case "$st" in
      PASS) pass=$((pass + 1)) ;;
      FAIL) fail=$((fail + 1)) ;;
      SKIP) skip=$((skip + 1)) ;;
    esac
  done
  log ""
  log "Total: $pass passed, $fail failed, $skip skipped (suites)"
  if [[ "$fail" -gt 0 ]]; then
    log "Reports: {module}/target/surefire-reports/*.txt"
    log "         schemathesis/runTest-*.txt"
    log "         test-results.json (Postman)"
  fi
}

# --- main ---
log "Generated test runner"
log "ROOT=$ROOT"
log "TEST_ENV_FILE=$TEST_ENV_FILE"
[[ -f "$ROOT/test-env.local.properties" ]] && log "Using test-env.local.properties overlay"

if ! compile_all_modules; then
  log ""
  log "Compilation failed — fix generators or regenerate before running tests."
  exit 1
fi

if [[ "$COMPILE_ONLY" == "1" ]]; then
  log "Compile-only mode — exiting."
  exit 0
fi

OVERALL_FAIL=0

if [[ "${RUN_SKIP_SMOKE:-0}" != "1" ]]; then
  run_smoke || { OVERALL_FAIL=1; on_suite_fail; }
fi

if [[ "${RUN_SKIP_MAVEN:-0}" != "1" ]]; then
  if [[ "${RUN_SKIP_INTEGRATION:-0}" != "1" ]]; then
    run_maven_module "integration" "integration" || { OVERALL_FAIL=1; on_suite_fail; }
  fi
  if [[ "${RUN_SKIP_SEQUENCE_JAVA:-0}" != "1" ]]; then
    run_maven_module "sequence-java" "sequence-java" || { OVERALL_FAIL=1; on_suite_fail; }
  fi
  if [[ "${RUN_SKIP_PERFORMANCE:-0}" != "1" ]]; then
    run_maven_module "performance" "performance" || { OVERALL_FAIL=1; on_suite_fail; }
  fi
  if [[ "${RUN_SKIP_NFR:-0}" != "1" ]]; then
    run_maven_module "nfr" "nfr" || { OVERALL_FAIL=1; on_suite_fail; }
  fi
  if [[ "${RUN_SKIP_SECURITY:-0}" != "1" ]]; then
    run_maven_module "security" "security" || { OVERALL_FAIL=1; on_suite_fail; }
  fi
  if [[ "${RUN_SKIP_UNIT:-0}" != "1" ]]; then
    run_maven_module "unit" "unit" || { OVERALL_FAIL=1; on_suite_fail; }
  fi
fi

run_schemathesis || { OVERALL_FAIL=1; on_suite_fail; }
run_postman || { OVERALL_FAIL=1; on_suite_fail; }

print_summary
exit "$OVERALL_FAIL"
