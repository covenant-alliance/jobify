# Matching service: design and implementation plan

> **Learning guide with diagrams:** `docs/MATCHING_ARCHITECTURE.md` explains why the service is shaped this way, what makes it a microservice, and how to change it. This page is the plan and the story list.

**Status: plan; the M1 spike (#100) is done, the rest is not built yet.** Epic: #12 (matching engine). Decided by the Product Owner on 2026-10-15: the matching engine is built as a **separate service that Jobify calls over gRPC**, mainly **for learning** microservices. This page says what the service is, how it works with Jobify, and in what order to build it. Items marked **Decision** need the Product Owner's answer before the story that depends on them starts.

> Honest trade-off, so the choice is made with open eyes: for Jobify's size a package inside the monolith would be simpler, faster and cheaper to run. A separate service adds a network, a second deployment, a contract to version and new ways to fail. That cost is the point of the exercise. The design below keeps the service small and its contract narrow so the lessons (contracts, timeouts, failure handling, observability) are not buried under accidental complexity.

## 1. What the service does

Given a seeker's profile and one or more jobs, it answers **how well they fit**, with an explanation. It does not store users or jobs, does not know about login, and has no database in the first version.

| Capability | Who asks | Result |
|---|---|---|
| `Score` | Jobify, for `GET /jobs/{id}/match` (seeker) | one score 0 to 100, matched and missing skills, a breakdown |
| `RankJobs` | Jobify, for `GET /jobs/recommended` (seeker) | the given jobs, scored and sorted, best first |
| `RankCandidates` | Jobify, for `GET /jobs/{id}/candidates` (owning employer) | the given seekers, scored and sorted, best first |

All three use one pure scoring function (section 5). The rest is plumbing around it.

## 2. How it fits with Jobify

```
 Browser (Next.js)
      │  HTTPS + JWT                      JSON, bare DTOs, error envelope (unchanged)
      ▼
 ┌─────────────────────────────┐   gRPC (HTTP/2, internal network only)   ┌───────────────────────────┐
 │ Jobify (Spring Boot, :9080) │ ───────────────────────────────────────► │ Matching service (:9090)  │
 │  - authenticates the user   │   Score / RankJobs / RankCandidates       │  - pure scoring function  │
 │  - checks role + ownership  │ ◄─────────────────────────────────────── │  - no database            │
 │  - loads seeker / job data  │   scores + explanations                   │  - gRPC health + metrics  │
 │  - narrows candidates (SQL) │                                           └───────────────────────────┘
 │  - turns result into JSON   │
 └──────────────┬──────────────┘
                ▼
          PostgreSQL (owned by Jobify only)
```

**Rule 1: the browser never talks to the matching service.** Only Jobify does. The service has no public port and no user concept, so there is nothing to authenticate users against and nothing to leak.

**Rule 2: Jobify owns the data and the authorization.** Jobify decides *who may ask* (a seeker for their own matches; the owning employer for candidates) and *what may be shown*, loads the rows, and sends the service only the fields scoring needs. The service never calls back into Jobify.

**Rule 3: Jobify keeps working without the service.** `GET /jobs`, search, applications and everything else never depend on it. Only the three match endpoints do (section 7).

### Request flow: `GET /jobs/recommended`

1. Jobify checks the JWT and the SEEKER role (as for any other route).
2. Jobify loads the seeker's skills and preferences.
3. Jobify narrows the field with plain SQL: available jobs, not already applied to, same country or remote, newest first, **at most 200**. (Ranking 20 000 jobs over the wire per request is exactly the mistake this design avoids. Narrowing is cheap in SQL; scoring is the service's job.)
4. Jobify sends `RankJobsRequest(seeker, jobs[])` with a **deadline of 800 ms**.
5. The service scores each job and returns them sorted.
6. Jobify keeps the top `size` (default 20), joins the job details it already has, and answers with the same JSON conventions as everything else.

## 3. Where the code lives

**Decision (recommended, default if you say nothing):** start **inside the Jobify repository** as an independent Maven project in `services/matching/`, with its own `pom.xml` and its own CI workflow (path-filtered). Reason: this session can only push to this repository, and one repo keeps the proto contract and both sides in one pull request while we learn. Extract it to its own repository later; that is a `git subtree split` away.

```
services/matching/
  pom.xml                      parent of the two modules below
  matching-api/                the contract: proto/*.proto + generated Java stubs, built to a jar
  matching-service/            the server: scoring, gRPC adapter, health, config, Dockerfile
src/main/java/com/mcverse/jobify/match/   the client side inside Jobify (section 6)
```

The root `pom.xml` of Jobify stays a single-module build. **Decision made in M6 (#105): the contract is shared as the `.proto` file, not as a jar.** Jobify's `pom.xml` points the protobuf plugin at `services/matching/matching-api/src/main/proto` and generates its own client stubs, so neither build needs the other installed first, and when the service moves to its own repository the proto moves with it. (The earlier idea of a `matching-api` jar that Jobify depends on would have forced CI, Docker and every developer to build the service first.)

**Decision made (2026-10-15, answered "go with your recommendations", then checked by the M1 spike #100): Java 21, Spring Boot 4.1.1 with its built-in gRPC starters.** The spike found that Spring Boot 4.1 manages gRPC itself, so the plan's earlier "plain grpc-java" alternative is not needed:
- `spring-boot-starter-grpc-server` (and `spring-boot-starter-grpc-client`) are in Boot 4.1's own BOM, along with `grpc-java` 1.83.1, `protobuf-java` 4.35.1 and `protobuf-maven-plugin` 5.1.8 (`io.github.ascopes`). Do not set versions for them in the `pom.xml`; Boot picks a matching set. (Setting a different protobuf version breaks compilation of the generated code, which the spike hit.)
- Add `grpc-stub`, `grpc-protobuf` and `grpc-services` without versions. Compile `.proto` files with the `protobuf-maven-plugin`, `protocVersion` = `${protobuf-java.version}`, and the `protoc-gen-grpc-java` binary plugin at `${grpc-java.version}`.
- A class annotated `@Service` that extends the generated `...ImplBase` is registered automatically. **gRPC health (`grpc.health.v1.Health`) and server reflection are registered by default**, so `grpcurl` works without code. Port: `spring.grpc.server.port`.
- Older `org.springframework.grpc:spring-grpc-spring-boot-starter` (1.0.x) targets Boot 4.0; Boot 4.1 ships its own starters instead, so do not use it.
- Verified in the spike on Spring Boot 4.1.1, Java 21: a `Ping` call from a client to the server, `grpc.health.v1.Health/Check` returning `SERVING`, and a plain grpc-java variant also working (the fallback if the starter ever gets in the way).
- `buf` 1.57.0 works: `buf lint` (STANDARD) and `buf breaking` (FILE) both ran, and renaming a field is reported as a breaking change. **Lint rules that shape the contract:** a package `jobify.matching.v1` must live in the directory `jobify/matching/v1/`, and request/response messages must be named `<Rpc>Request` / `<Rpc>Response` (so the sketch below uses `ScoreResponse`, never `PingReply`).
- **Not verified in the spike:** running the two services in `docker compose` and calling them with `grpcurl` (there is no Docker daemon and no `grpcurl` in this environment). Both are re-checked on your machine as part of M4 and M9; treat them as unverified until then.

## 4. The contract (`matching.v1`)

Protobuf is the source of truth; REST/JSON stays on the browser side only. **The real contract is `services/matching/matching-api/src/main/proto/jobify/matching/v1/matching.proto`** (fixed in M2, #101). The sketch below is the idea; where they differ, the file wins. Differences settled in M2: enums are prefixed (`WORK_MODE_REMOTE`, as `buf lint` requires) and mirror Jobify's enums exactly (`Proficiency` has `EXPERT`, `ExperienceLevel` is `INTERN..LEAD`, `EmploymentType` includes `TEMPORARY` and `B2B`); `JobProfile` gained `posted_at_epoch_seconds` for the tie-break; `RankedJob` / `RankedCandidate` wrap a `Match` with its id; `RankJobs` and `RankCandidates` take a `limit`. `services/matching/golden/` holds request/response JSON shared with Jobify's tests (M2 has the request; M3 adds the response once scoring exists). Sketch:

```proto
syntax = "proto3";
package jobify.matching.v1;

service MatchService {
  rpc Score(ScoreRequest) returns (ScoreResponse);
  rpc RankJobs(RankJobsRequest) returns (RankJobsResponse);
  rpc RankCandidates(RankCandidatesRequest) returns (RankCandidatesResponse);
}

message SeekerProfile {
  string seeker_id = 1;                       // Jobify's UUID string, opaque to the service
  repeated SeekerSkill skills = 2;
  Preferences preferences = 3;                // absent = seeker has none
}
message SeekerSkill { string skill_id = 1; Proficiency proficiency = 2; double years = 3; }
message Preferences {
  string preferred_title = 1;
  repeated string country_codes = 2;          // ISO 3166-1 alpha-2, from the locations catalogue
  repeated WorkMode work_modes = 3;
  repeated EmploymentType employment_types = 4;
  optional double min_annual_rate = 5;        // already converted to a yearly amount by Jobify
  optional double max_annual_rate = 6;
  optional ExperienceLevel experience_level = 7;
}
message JobProfile {
  int32 job_id = 1;                           // Jobify's integer postId
  string title = 2;
  repeated RequiredSkill required_skills = 3;
  string country_code = 4;
  WorkMode work_mode = 5;
  EmploymentType employment_type = 6;
  optional double annual_rate = 7;
  optional ExperienceLevel experience_level = 8;
}
message RequiredSkill { string skill_id = 1; string name = 2; }   // name only for readable output

message Match {
  int32 score = 1;                            // 0..100, integer
  repeated string matched_skill_ids = 2;
  repeated string missing_skill_ids = 3;
  repeated ScorePart parts = 4;               // the explanation, see section 5
}
message ScorePart { string key = 1; double weight = 2; double value = 3; bool counted = 4; }
```

Rules for the contract, so it can evolve without breaking either side:
- Enums start with `*_UNSPECIFIED = 0`. Values mirror Jobify's enums exactly (`SEEKER`, `FULL_TIME`, `ONSITE` and so on); an unknown value from a newer Jobify is scored as "no data", never an error.
- Only **additive** changes inside `v1`: new fields with new numbers, never reuse or renumber. A breaking change is `matching.v2` served next to `v1` until Jobify has moved.
- `buf lint` and `buf breaking` run in CI against the previous commit's proto, so a breaking change fails the build.
- Money: amounts are **annual equivalents in one currency**; Jobify converts (hourly x 2080, monthly x 12) before sending. **Decision:** Jobify has no currency field today, so the first version assumes one currency; a currency is a separate story if multi-currency matters.
- Limits: a request holds at most 500 jobs or 500 candidates (`RESOURCE_EXHAUSTED` beyond that), and unknown skill ids simply never match.

## 5. How the score is computed

A pure function: same input, same output, no clock, no randomness, no I/O. That makes it the most heavily unit-tested class in the system.

| Part | Weight | Value 0 to 1 |
|---|---|---|
| `skills` | 60 | weighted share of the job's required skills the seeker has |
| `location` | 10 | job country in the seeker's countries, or job is remote and the seeker accepts remote |
| `work_mode` | 8 | job's work mode in the seeker's accepted modes |
| `employment_type` | 7 | job's type in the seeker's accepted types |
| `rate` | 10 | 1 if the job rate is inside the seeker's range; falls off linearly to 0 at 30% outside it |
| `experience` | 5 | 1 for the same level, 0.5 one level apart, 0 for two or more |

**Skills in detail.** Each required skill counts 1. A seeker skill earns credit `proficiency factor` (BEGINNER 0.5, INTERMEDIATE 0.8, ADVANCED 1.0), raised to at most 1.0 by years of experience (+0.1 per year over 2). The part's value is the average credit over the required skills. A job with **no required skills** has no skills part (below).

**Details settled while building M3 (#102)** (code: `services/matching/matching-service/.../score/Scorer.java`, `Ranker.java`): `EXPERT` counts as 1.0 like `ADVANCED`; an unspecified or unknown proficiency counts as the lowest step (0.5); a seeker who lists the same skill twice gets the better entry. `location` is 1 when the job is remote and the seeker accepts remote, otherwise it compares the job's country with the seeker's countries (not counted if either side is empty). A rate bound of 0 never divides by zero. An enum value the service does not know (from a newer Jobify) is "no data", not an error. If every weight of the counted parts is 0, the score is 0.

**Missing data never punishes.** If a part cannot be judged (the seeker set no preference, or the job leaves a field empty), that part is marked `counted = false` and its weight is dropped; the remaining weights are rescaled to 100. So a seeker with no preferences is ranked on skills alone, and a job with no rate is not penalised for it. The response says which parts were counted, so the front end can show "based on skills only".

**Explainable.** Every `Match` carries `parts` (key, weight, value, counted) plus `matched_skill_ids` and `missing_skill_ids`. The score is `round(sum(weight x value over counted parts) / sum(weight over counted parts) x 100)`. Ties are broken by newer job, then by id, so ordering is deterministic.

**Decision (weights).** The table above is my proposal; they live in the service's configuration (`matching.weights.*`) and need no code change to tune. Please confirm or change them. Also open: should a required skill be split into *must have* and *nice to have*? Today `JobPost.requiredSkills` has no such flag; adding it is a separate Jobify story and a new optional field in the contract.

## 6. Changes inside Jobify

New slice `com.mcverse.jobify.match/` (dependency direction `match -> application -> job -> user -> common`; nothing depends on `match`):

| Piece | Purpose |
|---|---|
| `MatchEngine` (interface) | what the rest of Jobify sees: `score`, `rankJobs`, `rankCandidates`, in Jobify's own types |
| `GrpcMatchEngine` | the adapter: maps entities to proto messages, calls the stub with a deadline, maps results back |
| `DisabledMatchEngine` | used when `matching.enabled=false` (default in `dev`): the match endpoints answer 503 with a clear message |
| `MatchService`, `MatchController` | authorization, candidate narrowing, paging, DTO mapping |
| `MatchProperties` | `matching.target` (`dns:///matching:9090`), `matching.deadline-ms`, `matching.enabled` |

New or changed API (all additive; final shapes go into `api-documentation.md` and `docs/BACKEND_STATE.md` with each story):
- `GET /jobs/{id}/match` (SEEKER): `{ jobId, score, matchedSkills[], missingSkills[], parts[] }`. 404 for an unknown or closed job.
- `GET /jobs/recommended?page=&size=` (SEEKER): the usual page envelope with a `score` per job.
- `GET /jobs/{id}/candidates?page=&size=` (the owning employer, team access via `CompanyAccess`): seekers who applied or whose profile is public enough, with `score`. **Decision:** who is a candidate? Recommended: only seekers who **applied** to this job (no talent search over all seekers; that raises privacy questions that need their own decision).
- `GET/PUT /users/seekers/me/preferences`: the endpoint that was never built. `JobPreferences` exists but nothing reads it. Needed changes: `experienceLevel` becomes the `ExperienceLevel` enum (it is a free string today), locations become country codes, salary gets a `rateType` (today `minSalaryExpectation` has no unit), and `workModes[]` is added. Needs a Flyway migration (next `V<n>`), and data migration of existing free-text values to null.
- Optional later: a "new match" notification (the type waits in `docs/NOTIFICATIONS.md`) fired from a job-created event.

**Failure behaviour (Rule 3).** The call has a deadline (800 ms for one job, 2 s for ranking). On `UNAVAILABLE`, `DEADLINE_EXCEEDED` or a tripped circuit breaker, the three endpoints answer **503** in the standard envelope: `"Job matching is temporarily unavailable. Please try again in a moment."` The front end shows the message and the rest of the app keeps working. Read-only calls are safe to retry once on `UNAVAILABLE`; nothing else is retried. A circuit breaker (Resilience4j) opens after repeated failures so a dead service does not make every request wait out its deadline. **Decision:** instead of 503, should `/jobs/recommended` fall back to the newest jobs without scores? Recommended: no, a silent fallback hides the outage and shows the wrong thing as "recommended".

**Security between the two services.** The service is reachable only on the internal Docker/Kubernetes network. Jobify authenticates to it with a shared secret sent as gRPC metadata (`authorization: Bearer <MATCHING_TOKEN>`, from an environment variable, refused at startup if missing outside `dev`), checked by a server interceptor. Stretch goal for learning: replace it with mutual TLS. The service logs ids only, never names, emails or CV text, and the request holds none of those anyway.

## 7. Running it

- **Ports:** Jobify `9080` (unchanged), matching `9090` (gRPC, not published to the host), management `9091` (health, metrics).
- **docker-compose:** a new `matching` service next to `db` and `app`; `app` gets `MATCHING_ENABLED=true`, `MATCHING_TARGET=matching:9090`, `MATCHING_TOKEN`. `app` has **no** `depends_on` on it, because Rule 3 says Jobify must start and work without it.
- **Health:** the standard gRPC health service (`grpc.health.v1`), also used by the compose healthcheck. In `dev` the service enables gRPC reflection so `grpcurl` works.
- **Observability:** every Jobify request id goes into gRPC metadata (`x-request-id`) and into both services' logs, so one user action can be followed across both. Metrics: request count and latency per method, score distribution. Logs in the same JSON format as Jobify.
- **Local without Docker:** `mvn -f services/matching/pom.xml -pl matching-service spring-boot:run`, then Jobify with `MATCHING_ENABLED=true MATCHING_TARGET=localhost:9090`.
- **CI:** a workflow `matching-service.yml` (runs on changes under `services/matching/`): proto lint and breaking check, unit tests, in-process gRPC tests. The Jobify workflows build `matching-api` first. As with the Docker files so far, anything not built or run in this environment is marked unverified in the docs until you have run it.

### Running the service on its own (built in M4, #103)

```
mvn -f services/matching/pom.xml install                         # builds the contract and runs all tests
java -jar services/matching/matching-service/target/matching-service-0.1.0-SNAPSHOT.jar
curl localhost:9091/actuator/health                              # {"status":"UP"}
grpcurl -plaintext localhost:9090 list                           # dev only: reflection is on
grpcurl -plaintext -H 'authorization: Bearer dev-matching-token' \
        -d '{"seeker":{"seekerId":"s"},"job":{"jobId":1}}' localhost:9090 jobify.matching.v1.MatchService/Score
```

- **Profiles:** `dev` is the default and uses the public token `dev-matching-token`. Any other profile (the Docker image uses `prod`) **refuses to start without `MATCHING_TOKEN`**, and refuses the placeholder. Checked by a test and by running the packaged jar.
- **Calls without a valid `authorization: Bearer ...`** get `UNAUTHENTICATED`; `grpc.health.v1.Health` is exempt so probes need no secret.
- **Limits and codes:** more than 500 jobs or candidates is `RESOURCE_EXHAUSTED`; a missing seeker or job, blank seeker id, non-positive job id or negative limit is `INVALID_ARGUMENT`; unknown skill ids simply never match; anything unexpected is `INTERNAL` with no detail (the cause is only in the log).
- **Ports:** gRPC `9090` (`MATCHING_GRPC_PORT`), HTTP `9091` (`MATCHING_MANAGEMENT_PORT`) serving only `/actuator/health`.
- **End to end (M9):** `scripts/e2e-matching.sh` runs both services as jars and checks the endpoints, the request id, the metrics, the 503s and the recovery. It passed here. Operations notes are in `docs/DEPLOYMENT.md`, "The matching service".
- **Docker:** `docker compose --profile matching up -d --build` (opt-in; Jobify does not call the service until M6). The Dockerfile is in `services/matching/`. **Not verified by the author: no Docker daemon was available.** The `image` job in `.github/workflows/matching-service.yml` builds the image, checks it refuses to start without a token, and checks it reports healthy; its first run on GitHub is the first real test. `grpcurl` was not available either, so the example above is untested; the same calls are tested in `MatchingServerTest` with a real gRPC client.

## 8. Testing strategy

1. **Scoring function** (service): table-driven unit tests per part and per edge case (no skills, no preferences, unknown enum, empty job, ties, rate boundaries). This is where most tests live. Target: every branch covered.
2. **gRPC adapter** (service): in-process server and client (`grpc-inprocess`), checks status codes (`INVALID_ARGUMENT`, `RESOURCE_EXHAUSTED`, `UNAUTHENTICATED`) and the size limits.
3. **Contract** (both): `buf breaking` in CI; a shared set of golden request/response pairs (JSON files) that both the service tests and Jobify's client tests run against, so a mapping mistake on either side is caught without starting both.
4. **Jobify** (client side): `MatchService` tests with a fake `MatchEngine`; `GrpcMatchEngine` tests against an in-process fake server that can be told to be slow or to fail (deadline, 503 path, circuit breaker); the existing `RouteSecurityMatrixTest` gets the three new routes (all authenticated, none public). Jobify's JaCoCo floor (90% lines, 80% branches) still applies to the `match` package.
5. **End to end** (manual, scripted): compose up both, register a seeker with skills, create a job, call the three endpoints, then stop the service and confirm the 503 and that `GET /jobs` still works.

## 9. Build order (stories)

Sizes are estimates. Each story ends with docs updated and `mvn verify` green on the side it touches.

| # | Story | Size | Depends on | Done when |
|---|---|---|---|---|
| M1 (#100) | **Spike:** gRPC on Spring Boot 4.1 (which starter or plain grpc-java), `buf`, one `Ping` call between two tiny projects, in compose | S | | the choice is written in this file, with what failed. **Done 2026-10-15** (compose and `grpcurl` left to your machine) |
| M2 (#101) | **Contract and project skeleton:** `services/matching/` multi-module, `matching-api` with the proto of section 4, `buf lint/breaking` in CI | M | M1 | stubs build; CI green; golden-pair format agreed |
| M3 (#102) | **Scoring function** with all tests, weights from configuration | M | M2 | table of section 5 implemented, every branch covered, `ScorePart` explanation correct |
| M4 (#103) | **gRPC server:** `Score`, `RankJobs`, `RankCandidates`, limits, status codes, health, token interceptor, Dockerfile, compose entry | M | M3 | `grpcurl` calls work locally; in-process tests green |
| M5 (#104) | **Seeker preferences endpoint** in Jobify (`GET/PUT /users/seekers/me/preferences`), enum and country normalisation, Flyway migration, tests | M | | front end can read and save preferences; PostgreSQL suite green |
| M6 (#105) | **Jobify client and `GET /jobs/{id}/match`:** `match` slice, adapter, deadlines, 503 behaviour, circuit breaker, `DisabledMatchEngine`, security matrix | L | M4, M5 | endpoint works against the real service and returns 503 when it is stopped; coverage floor met |
| M7 (#106) | **`GET /jobs/recommended`:** SQL narrowing, paging, page envelope | M | M6 | returns the top jobs by score; narrowing limit enforced |
| M8 (#107) | **`GET /jobs/{id}/candidates`:** employer/team access, applicants only (pending the decision above) | M | M6 | owner and team members only; other employers get 403 |
| M9 (#108) | **Operations:** request-id propagation, metrics, README and `DEPLOYMENT.md` section, end-to-end script, mTLS stretch | M | M7 | the manual end-to-end of section 8 passes |
| M10 (#109) | *(optional)* "new match" notification on job creation | S | M7 | seekers whose score is at least a threshold get one notification |
| M11 (#110) | *(optional)* own read model in the service fed by events (outbox) instead of sending profiles per request | L | M9 | only if latency or payload size becomes a real problem; changes the ownership rule of section 2, so it needs its own design |

**Front end (their session, via `docs/BACKEND_STATE.md`):** the fake `matchScore` on job cards is replaced by `score` from `/jobs/recommended` and `/jobs/{id}/match`; a preferences form; the "based on skills only" hint when `parts[].counted` is false; the 503 message shown as it comes.

## 10. Decisions (answered 2026-10-15: "go with your recommendations" for all seven)

1. **Repository:** keep it in `services/matching/` inside this repository for now (recommended), or create a separate repository (then attach it to this session)?
2. **Stack:** Java 21 with grpc-java (recommended), or another language?
3. **Weights** of section 5 (skills 60, location 10, work mode 8, employment type 7, rate 10, experience 5), and whether to add *must have / nice to have* skills.
4. **Who is a candidate** for an employer: only applicants (recommended), or any seeker with a public profile?
5. **Outage behaviour:** 503 with a message (recommended), or a silent fallback list?
6. **Currency:** one currency for now (recommended)?
7. **Scope:** build M1 to M9 as one sprint (recommended order above), with M10 and M11 left as optional?

## 11. What you will learn from this exercise

Contract-first design and schema evolution (protobuf, `buf breaking`), the cost of a network boundary (deadlines, retries, circuit breakers, partial failure), service-to-service authentication, health checks and graceful degradation, correlation ids across services, contract testing with golden files, and, by comparison with the monolith, when *not* to split a service.
