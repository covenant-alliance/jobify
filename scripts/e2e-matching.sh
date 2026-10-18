#!/usr/bin/env bash
# End-to-end check of the matching microservice with Jobify, over a real network connection (docs/MATCHING_SERVICE.md).
#
#   scripts/e2e-matching.sh                 # builds both jars, starts both services, runs the checks, stops everything
#   E2E_SKIP_BUILD=1 scripts/e2e-matching.sh   # reuse the jars already in target/
#
# It starts the matching service (profile prod, so it needs and checks a token) and Jobify (dev profile, in-memory H2,
# a temporary upload folder: it never touches ./data), then:
#   1. registers a seeker with skills and preferences, and an employer posts a job
#   2. calls GET /jobs/{id}/match, /jobs/recommended and /jobs/{id}/candidates and checks the answers
#   3. checks that the request id of a call is in the matching service's log, and that its metrics exist
#   4. STOPS the matching service and checks the three endpoints answer 503 with the friendly message while
#      GET /jobs and the health check still work
#   5. starts it again and checks the circuit breaker lets calls through once its wait is over
# Needs: java 21, maven, curl, jq. Uses ports 19080 (Jobify), 19090 (gRPC), 19091 (matching health); override with
# E2E_APP_PORT, E2E_GRPC_PORT, E2E_MGMT_PORT. Exits non-zero if any check fails.
set -uo pipefail
cd "$(dirname "$0")/.."

APP_PORT=${E2E_APP_PORT:-19080}
GRPC_PORT=${E2E_GRPC_PORT:-19090}
MGMT_PORT=${E2E_MGMT_PORT:-19091}
TOKEN="e2e-$(head -c 16 /dev/urandom | od -An -tx1 | tr -d ' \n')"
WORK=$(mktemp -d)
RUN=$(date +%s)
BASE="http://localhost:$APP_PORT"
FAILED=0
MATCHING_PID=""
APP_PID=""

for tool in java mvn curl jq; do
  command -v "$tool" >/dev/null || { echo "missing tool: $tool"; exit 2; }
done

cleanup() {
  [ -n "$MATCHING_PID" ] && kill "$MATCHING_PID" 2>/dev/null
  [ -n "$APP_PID" ] && kill "$APP_PID" 2>/dev/null
  wait 2>/dev/null
  echo "logs kept in $WORK"
}
trap cleanup EXIT

pass() { echo "  PASS  $1"; }
fail() { echo "  FAIL  $1 ($2)"; FAILED=$((FAILED + 1)); }
expect() { # name, expected, actual
  if [ "$2" = "$3" ]; then pass "$1"; else fail "$1" "expected '$2', got '$3'"; fi
}
http() { # method url [token] [body] [extra curl args...] -> body on stdout, status in $WORK/status
  local method=$1 url=$2 auth=${3:-} body=${4:-}
  shift 4 2>/dev/null || shift $#
  local args=(-s -o "$WORK/body" -w '%{http_code}' -X "$method" "$url" -H 'Content-Type: application/json')
  [ -n "$auth" ] && args+=(-H "Authorization: Bearer $auth")
  [ -n "$body" ] && args+=(-d "$body")
  curl "${args[@]}" "$@" > "$WORK/status"
  cat "$WORK/body"
}
status() { cat "$WORK/status"; }
wait_for() { # name url
  for _ in $(seq 1 90); do
    curl -fs "$2" >/dev/null 2>&1 && return 0
    sleep 1
  done
  echo "timed out waiting for $1 at $2"; return 1
}

start_matching() {
  MATCHING_TOKEN=$TOKEN MATCHING_GRPC_PORT=$GRPC_PORT MATCHING_MANAGEMENT_PORT=$MGMT_PORT SPRING_PROFILES_ACTIVE=prod \
    java -jar services/matching/matching-service/target/matching-service-*.jar >> "$WORK/matching.log" 2>&1 &
  MATCHING_PID=$!
  wait_for "matching service" "http://localhost:$MGMT_PORT/actuator/health"
}

if [ -z "${E2E_SKIP_BUILD:-}" ]; then
  echo "== building"
  mvn -B -q -DskipTests package || exit 1
  mvn -B -q -f services/matching/pom.xml -DskipTests package || exit 1
fi

echo "== starting the matching service (gRPC $GRPC_PORT, health $MGMT_PORT)"
start_matching || exit 1

echo "== starting Jobify ($APP_PORT)"
SERVER_PORT=$APP_PORT SPRING_DATASOURCE_URL="jdbc:h2:mem:e2e$RUN;DB_CLOSE_DELAY=-1" APP_UPLOAD_DIR="$WORK/uploads" \
  MATCHING_ENABLED=true MATCHING_TARGET="localhost:$GRPC_PORT" MATCHING_TOKEN=$TOKEN \
  java -jar target/jobify-*.jar >> "$WORK/jobify.log" 2>&1 &
APP_PID=$!
wait_for "Jobify" "$BASE/actuator/health" || exit 1

echo "== setting up data"
EMP=$(http POST "$BASE/auth/login" "" '{"username":"techcorp","password":"password"}' | jq -r .token)
JOB=$(http POST "$BASE/jobs" "$EMP" "{\"jobTitle\":\"E2E backend $RUN\",\"jobDescription\":\"<p>x</p>\",\"rate\":100000,\"rateType\":\"YEARLY\",\"location\":\"Berlin, Germany\",\"workMode\":\"HYBRID\",\"employmentType\":\"FULL_TIME\",\"requiredSkills\":[\"E2eJava\",\"E2eKafka\"]}" | jq -r .postId)
SEEKER_NAME="e2e_seeker_$RUN"
SEEKER=$(http POST "$BASE/auth/register" "" "{\"username\":\"$SEEKER_NAME\",\"password\":\"s3cur3Pass\",\"role\":\"SEEKER\",\"firstName\":\"E\",\"lastName\":\"Two\"}" | jq -r .token)
http POST "$BASE/users/seekers/me/skills" "$SEEKER" '{"skillName":"E2eJava","proficiencyLevel":"ADVANCED","yearsOfExperience":4}' >/dev/null
expect "job created" "true" "$([ "$JOB" != "null" ] && [ -n "$JOB" ] && echo true || echo false)"
http PUT "$BASE/users/seekers/me/preferences" "$SEEKER" '{"countryCodes":["DE"],"workModes":["HYBRID"],"employmentTypes":["FULL_TIME"],"minRate":90000,"maxRate":120000,"rateType":"YEARLY"}' >/dev/null
expect "preferences saved" "200" "$(status)"

echo "== the three endpoints, matching service up"
REQ_ID="e2e-req-$RUN"
body=$(http GET "$BASE/jobs/$JOB/match" "$SEEKER" "" -H "X-Request-Id: $REQ_ID")
expect "GET /jobs/{id}/match is 200" "200" "$(status)"
SCORE=$(echo "$body" | jq -r .score)
expect "score is a number above 0" "true" "$(echo "$body" | jq '.score > 0')"
expect "matched skill is Java by name" "E2eJava" "$(echo "$body" | jq -r '.matchedSkills[0].name')"
expect "missing skill is Kafka by name" "E2eKafka" "$(echo "$body" | jq -r '.missingSkills[0].name')"
expect "six parts, five counted" "6/5" "$(echo "$body" | jq -r '"\(.parts | length)/\([.parts[] | select(.counted)] | length)"')"
expect "the one not counted is experience (neither side set a level)" "experience" "$(echo "$body" | jq -r '[.parts[] | select(.counted | not) | .key] | join(",")')"

body=$(http GET "$BASE/jobs/recommended?size=50" "$SEEKER")
expect "GET /jobs/recommended is 200" "200" "$(status)"
expect "the job is in the recommended list" "true" "$(echo "$body" | jq --argjson id "$JOB" '[.content[].job.postId] | index($id) != null')"
expect "its score equals the single-job score" "$SCORE" "$(echo "$body" | jq --argjson id "$JOB" '.content[] | select(.job.postId == $id) | .score')"

http POST "$BASE/jobs/$JOB/apply" "$SEEKER" >/dev/null
expect "the seeker applies" "201" "$(status)"
body=$(http GET "$BASE/jobs/$JOB/candidates" "$EMP")
expect "GET /jobs/{id}/candidates is 200" "200" "$(status)"
expect "the applicant is ranked" "$SEEKER_NAME" "$(echo "$body" | jq -r '.content[0].applicant.username')"
expect "her score equals the single-job score" "$SCORE" "$(echo "$body" | jq -r '.content[0].score')"

echo "== operations"
sleep 1
if grep -q "\[$REQ_ID\]" "$WORK/matching.log"; then pass "the request id appears in the matching service's log"; else fail "request id in the matching log" "no line with [$REQ_ID]"; fi
http GET "http://localhost:$MGMT_PORT/actuator/prometheus" >/dev/null
for metric in matching_requests_total matching_request_duration_seconds_count matching_score_count; do
  if grep -q "^$metric" "$WORK/body"; then pass "metric $metric is exposed"; else fail "metric $metric" "not in /actuator/prometheus"; fi
done

echo "== stopping the matching service"
kill "$MATCHING_PID"; wait "$MATCHING_PID" 2>/dev/null; MATCHING_PID=""
MESSAGE="Job matching is temporarily unavailable. Please try again in a moment."
body=$(http GET "$BASE/jobs/$JOB/match" "$SEEKER");              expect "match is 503" "503" "$(status)"
expect "with the friendly message" "$MESSAGE" "$(echo "$body" | jq -r .message)"
http GET "$BASE/jobs/recommended" "$SEEKER" >/dev/null;           expect "recommended is 503" "503" "$(status)"
http GET "$BASE/jobs/$JOB/candidates" "$EMP" >/dev/null;          expect "candidates is 503" "503" "$(status)"
http GET "$BASE/jobs" >/dev/null;                                  expect "GET /jobs still works" "200" "$(status)"
http GET "$BASE/jobs/$JOB" >/dev/null;                             expect "GET /jobs/{id} still works" "200" "$(status)"
http GET "$BASE/actuator/health" >/dev/null;                       expect "Jobify health is still UP" "200" "$(status)"
http GET "$BASE/users/seekers/me/preferences" "$SEEKER" >/dev/null; expect "other seeker routes still work" "200" "$(status)"

echo "== starting it again (the circuit breaker waits 10 s before trying)"
start_matching || exit 1
sleep 11
http GET "$BASE/jobs/$JOB/match" "$SEEKER" >/dev/null
expect "match works again" "200" "$(status)"

echo
if [ "$FAILED" -eq 0 ]; then echo "ALL CHECKS PASSED"; else echo "$FAILED CHECK(S) FAILED"; fi
exit "$FAILED"
