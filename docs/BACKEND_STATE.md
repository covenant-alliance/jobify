# Back-end state, as seen by the front end

**Written by the back-end session. The front-end session reads it and never edits it.** The front-end session's own notes and requests live in `docs/FRONTEND_STATE.md` (see the hand-off protocol in section 5 there).

Base URL `http://localhost:9080`. Full route reference: `api-documentation.md`, or the OpenAPI JSON at `/v3/api-docs`.

## How to use this file (front-end session)
1. Read "Open requests for the front end" and the newest change-log entries before you start any work.
2. Do the ticked-off-by-you items, then record what you did or need in `docs/FRONTEND_STATE.md`.
3. Entries are newest first. Each says what changed, whether the old shape still works, and the GitHub issue.

## Answers to your requests (back end → front end)
- **Job compensation model (your BLOCKING request)** → shipped, see the top change-log entry (issue #28). `POST /jobs` and `PUT /jobs/{id}` now take `rate` + `rateType`. `jobRating` is dropped (issue #24 decided by the Product Owner).
- Applications, saved jobs, match score, admin users/stats, notifications, search → tracked as #9, #10, #12, #16, #14, #13.

## Open requests for the front end
- [ ] Show `rate` with `rateType` on job cards, details and the JSON-LD (per-hour, per-month, per-year or fixed total), and stop reading `hourlyRate`. Remove any `jobRating` usage (issue #28).- [ ] Switch `useEmployerJobs` to `GET /jobs/mine`, and handle 403 on job writes (issue #3, #4).
- [ ] Add `requiredSkills: string[]` to `Job` and `CreateJobRequest`, and add a skills picker to `PostJobModal` (issue #6).
- [ ] Extend `SeekerResponse` with `educations/certifications/experiences/skills`, then build the profile-editing UI.
- [ ] Replace `applyForJob` / `saveJob` in-memory state with API calls once the P1 and P2 endpoints ship (issues #9, #10).
- [ ] Replace the fake `matchScore` in `jobEnrichment.ts` once the matching endpoint ships (issue #12).
- [ ] Replace `MOCK_USERS` with `GET /admin/users` (issue #16).

## Change log (newest first)

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
