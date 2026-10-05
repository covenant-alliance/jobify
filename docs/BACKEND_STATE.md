# Back-end state, as seen by the front end

**Written by the back-end session. The front-end session reads it and never edits it.** The front-end session's own notes and requests live in `docs/FRONTEND_STATE.md` (see the hand-off protocol in section 5 there).

Base URL `http://localhost:9080`. Full route reference: `api-documentation.md`, or the OpenAPI JSON at `/v3/api-docs`.

## How to use this file (front-end session)
1. Read "Open requests for the front end" and the newest change-log entries before you start any work.
2. Do the ticked-off-by-you items, then record what you did or need in `docs/FRONTEND_STATE.md`.
3. Entries are newest first. Each says what changed, whether the old shape still works, and the GitHub issue.

## Answers to your requests (back end → front end)
Updated 2026-10-04 after reading your snapshot of the same day. Everything on the back-end side is now a GitHub issue.

| Your request | Back-end answer |
|---|---|
| Job compensation model | **Shipped and merged into `master`** (#28, PR #29). See the change log. |
| Applications: apply, list mine, withdraw, employer pipeline | **In progress**, split into #30 apply + list mine, #31 withdraw, #32 employer applicants, #33 employer stage changes. Epic #9. Sprint 2. |
| Saved jobs | #10, sprint 2. |
| Company name | #11, sprint 2: `companyName` and `companyId` on jobs. |
| Company **logo** | #34, icebox (no logo storage exists yet). Until it ships, jobs return no `logoUrl`. |
| Benefits and gallery | #35, icebox, optional. Keep your pools for now. |
| Responsibilities / requirements | #36, **Product Owner decision** pending (keep your sentence heuristic meanwhile). |
| Location facet | #37, icebox. `location` stays free text. |
| Seeker dashboard charts | #38 `GET /applications/me/stats`, sprint 4, after applications. |
| Notifications | #14, sprint 4. |
| Company dashboard stats | #17, sprint 4. **Open question for the Product Owner: "views" per job** (needs view tracking; may be dropped). |
| Admin users with paging and search, and admin stats | #16, sprint 4. `GET /admin/users` already exists unpaged; you can wire it today. |
| Server-side search and paging | #13, sprint 3. The paging envelope needs agreeing with you before build. |
| Dropping your random fallbacks for `location` / `workMode` / `employmentType` | #39, **Product Owner decision** pending: whether the API starts requiring all three on new jobs. Keep your fallbacks for now. |
| Match %, recommended jobs, candidates | **Parked** as you asked (#12, icebox). Nothing will be built until it is scheduled. |
| Employee role dashboard | #26 (P10), Product Owner decision pending. (Your note says "backlog P3"; the correct reference is P10 / #26.) |

Sprint plan after this update: **sprint 2** = #28, #9 (#30 to #33), #10, #11, #15, #18, with #39 to be decided. **Sprint 3** = #13 search, #20 rate limit and password rules. **Sprint 4** = #14, #16, #17, #38.

## Open requests for the front end
- [ ] Use `companyName` from the job response instead of the capitalised `employerUsername`; keep the "Jobify Partner" fallback only for `null` (issue #11).- [ ] Company dashboard: replace `MOCK_PIPELINE_CANDIDATES` with `GET /jobs/{id}/applications`, add stage actions with `PUT /applications/{id}/status` (show only the allowed next stages), and delete the fake `applicationStage.ts` (issues #32, #33).
- [ ] Replace the in-memory `applyForJob` with `POST /jobs/{id}/apply`, load `GET /applications/me` for the seeker dashboard, and add a withdraw action using `DELETE /applications/{id}` (show it for every status except `REJECTED` and `WITHDRAWN`; issues #30, #31; the endpoints are in the applications pull request and reach `master` when it is merged). Show a readable message from the 422/403 responses.- [ ] Show `rate` with `rateType` on job cards, details and the JSON-LD (per-hour, per-month, per-year or fixed total), and stop reading `hourlyRate`. Remove any `jobRating` usage (issue #28).- [ ] Switch `useEmployerJobs` to `GET /jobs/mine`, and handle 403 on job writes (issue #3, #4).
- [ ] Add `requiredSkills: string[]` to `Job` and `CreateJobRequest`, and add a skills picker to `PostJobModal` (issue #6).
- [ ] Extend `SeekerResponse` with `educations/certifications/experiences/skills`, then build the profile-editing UI.
- [ ] Replace `applyForJob` / `saveJob` in-memory state with API calls once the P1 and P2 endpoints ship (issues #9, #10).
- [ ] Replace the fake `matchScore` in `jobEnrichment.ts` once the matching endpoint ships (issue #12).
- [ ] Replace `MOCK_USERS` with `GET /admin/users` (issue #16).

## Change log (newest first)

### Sprint 2 — company name on jobs (#11, P5) — 2026-10-05
Additive. Existing fields and shapes are unchanged.

- Every job response (`GET /jobs`, `GET /jobs/{id}`, `GET /jobs/mine`, create and edit) now includes **`companyName`** and **`companyId`**. Both are `null` for an employer who has not created a company, so keep a fallback for that case.
- New public **`GET /companies/{id}`** returns `{ id, name }` (`404` if unknown).
- `logoUrl` is not returned yet: there is no logo storage (issue #34, icebox). Keep deriving initials and colour from the name for now.

### Sprint 2 — applications, stories P1.3 and P1.4: employer side (#32, #33, epic #9) — 2026-10-05
Additive. **In review in the applications pull request (branch `feature-applications`); not on `master` until it is merged.** With these, the applications epic is complete on the back end.

- **`GET /jobs/{id}/applications`** (the employer who owns the job). Newest first, withdrawn ones included. Optional `?status=`. Each item is a `JobApplicationResponse`: `id`, `status`, `coverNote`, `createdAt`, `updatedAt` and `applicant { seekerId, username, name, lastName, hasCv }`. Counting per stage is up to you for now (group the list); a stats endpoint comes with #17.
  - `403` `You can only view applications for your own jobs.` (other employers, seekers), `404` unknown job, `400` for an unknown `status` (the message lists the allowed values).
- **`PUT /applications/{id}/status`** (the job's owner). Body `{ "status": "IN_REVIEW" }`, response `200` with the updated `JobApplicationResponse`.
  - Allowed: `APPLIED` -> `IN_REVIEW` | `REJECTED`; `IN_REVIEW` -> `INTERVIEW` | `REJECTED`; `INTERVIEW` -> `OFFER` | `REJECTED`; `OFFER` -> `REJECTED`. `REJECTED` and `WITHDRAWN` are final. Employers cannot set `APPLIED` or `WITHDRAWN`.
  - `422` with a readable message for a disallowed move, e.g. `An application in INTERVIEW cannot move to IN_REVIEW.`; `403` `You can only manage applications for your own jobs.`; `404`; `400` `status is required`.
  - The seeker sees the new stage in `GET /applications/me` straight away.
- A query parameter or path value that is not a valid enum constant now returns `400` with the allowed values, instead of a generic 500.
- **The pipeline is real now**, so `applicationStage.ts` (fake stage from `postId`) can go, and `MOCK_PIPELINE_CANDIDATES` can be replaced by the list endpoint.

### Sprint 2 — applications, story P1.2: withdraw (#31, epic #9) — 2026-10-04
Additive. **In review in the applications pull request**, same as P1.1 below.

- **`DELETE /applications/{id}`** (the applicant only). Returns **`204`**. It does **not** delete the record: the status becomes `WITHDRAWN` and `updatedAt` changes, so `GET /applications/me` still lists it, flagged `WITHDRAWN`.
- Allowed from `APPLIED`, `IN_REVIEW`, `INTERVIEW`, `OFFER`. From `REJECTED` or `WITHDRAWN` it is `422` `This application can no longer be withdrawn.`
- `403` `You can only withdraw your own applications.` (another seeker, or an employer); `404` unknown id.
- After withdrawing, the seeker can apply to the job again (`POST /jobs/{id}/apply` re-opens the same application as `APPLIED`).

### Sprint 2 — applications, story P1.1: apply and list mine (#30, epic #9) — 2026-10-03
Additive only; nothing existing changes. **In review in the applications pull request** (branch `feature-applications`).

- **`POST /jobs/{id}/apply`** (SEEKER). Optional body `{ "coverNote": "..." }`, max 2,000 characters. `201` with an `ApplicationResponse`. Starts as `APPLIED`.
  - `422` `This job is no longer accepting applications.` (closed job), `422` `You have already applied to this job.`, `403` `Only job seekers can apply for jobs.` (employers), `404` unknown job, `400` `coverNote must be at most 2000 characters`.
  - Applying again after a withdrawal re-opens the same application as `APPLIED`.
- **`GET /applications/me`** (SEEKER). Newest first, including `WITHDRAWN` and applications to jobs that have since closed (`job.available: false`). Each item has a job summary (`postId`, `jobTitle`, `employerUsername`, `companyName`, `available`), so the dashboard needs no second call.
- Enum `ApplicationStatus`: `APPLIED | IN_REVIEW | INTERVIEW | OFFER | REJECTED | WITHDRAWN`. Please mirror it exactly in your TypeScript union.
- Behaviour to know: when an admin approves an account deletion, that account's applications are deleted too (a seeker's own, or everything applied to an employer's jobs).
- Still to come in this epic: withdraw (#31), employer applicants list (#32), employer stage changes (#33). Until #33 ships, every application stays `APPLIED`, so keep your fake `applicationStage.ts` for the employer-driven stages.

### Sprint 2 — job compensation model (#28, #24) — 2026-09-30
**Breaking for `POST /jobs` callers that still send `hourlyRate` only: they now get 400 `rate is required`.** The new Post-a-Job payload works as is.

- **Request** (`POST /jobs`, `PUT /jobs/{id}`): `rate` (number, greater than 0, max 1,000,000,000) and `rateType` (`HOURLY | MONTHLY | YEARLY | CONTRACT_TOTAL`) are **required**. Messages: `rate must be greater than 0`, `rate is required`, `rateType is required`. An unknown `rateType` is 400.
- **`jobRating` is gone** from requests and responses (Product Owner decision, #24). If an old client still sends `jobRating` or `hourlyRate`, it is ignored, not rejected.
- **Response** (`JobPostResponse`): new `rate` and `rateType`. `hourlyRate` is still returned but **deprecated**: it is `rate` converted to hourly (monthly / 173.33, yearly / 2080, rounded to cents; `0` for `CONTRACT_TOTAL`, which is not comparable). This is the same conversion your front end uses, so you can drop your own copy later.
- `employmentType` gains **`B2B`**.
- **Existing jobs** were migrated: `rate = old hourlyRate`, `rateType = HOURLY`. Seed data now uses the new model.
- Database note for local dev: the app patches old dev databases at startup (converts enum columns to plain text, backfills the rate). No action needed, and `data/` is never deleted.
- Future search (`minRate` / `maxRate`, issue #13) will compare on the hourly equivalent and exclude `CONTRACT_TOTAL`.

### Sprint 1 — 2026-09-30 (branch `claude/keen-bell-ep73oh`)
Breaking for the front end: none. The 7 fields `PostJobModal` sends still work.

- **B1 (#2)** `PATCH` passes CORS, so the employer open/close toggle now works from the browser.
- **B2 (#3)** New `GET /jobs/mine` (EMPLOYER only, includes closed jobs). **Please switch `useEmployerJobs` to it.** `GET /jobs` still returns every employer's jobs.
- **B3 (#4)** Job writes are checked on the server. A non-employer `POST /jobs` is now **403** `Only employers can post jobs.` (was 404 "Employer not found"). Editing or toggling someone else's job is **403** `You can only change job postings that you created.`
- **B4 (#5)** `jobDescription` is sanitized on write to `p, br, ul, ol, li, strong, em, h2, h3, a[href]`. Max is 20,000 characters of submitted HTML. What comes back may differ slightly from what you sent (emoji as `&#x1f680;`, `rel="nofollow"` on links).
- **B5 (#6)** `POST /jobs` binds `CreateJobRequest`. `requiredSkills` is now an array of **names** (`["Java"]`), not objects. Validation errors are **400** with a message such as `jobTitle is required; jobRating must be between 0 and 5`. New `PUT /jobs/{id}` edits a post (same body, owner only).
- **B7 (#7)** No API change. The JWT secret now comes from `JWT_SECRET`; token format and lifetime are unchanged.
- Any malformed JSON body now returns **400** `The request body is missing or malformed.` instead of 500. Bean-validation failures on other endpoints also return 400 with field names.
- **Behaviour to know:** when an admin approves an employer's account deletion, all of that employer's job posts are deleted with it.
