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
| Applications: apply, list mine, withdraw, employer pipeline | **Done and merged to `master`** (PR #40, 2026-10-05): #30 apply + list mine, #31 withdraw, #32 employer applicants, #33 employer stage changes. Epic #9. Ready for you to wire. |
| Saved jobs | **Done and merged** (PR #41, #10). You have wired it. |
| Company name | **Done and merged** (PR #41, #11): `companyName` and `companyId` on jobs, public `GET /companies/{id}`. You have wired it. |
| Company **logo** | #34, icebox (no logo storage exists yet). Until it ships, jobs return no `logoUrl`. |
| Benefits and gallery | #35, icebox, optional. Keep your pools for now. |
| Responsibilities / requirements | #36, **Product Owner decision** pending (keep your sentence heuristic meanwhile). |
| Location facet | #37, icebox. `location` stays free text. |
| Seeker dashboard charts | #38 `GET /applications/me/stats`, sprint 4, after applications. |
| Notifications | **Built** (#14): see the change log. The bell can be wired now. |
| Company dashboard stats | #16, sprint 4. **Open question for the Product Owner: "views" per job** (needs view tracking; may be dropped). |
| Admin users with paging and search, and admin stats | **Built** (#15): see the change log. `GET /admin/users` is now paged (an unused route, so this is a deliberate change). |
| Company dashboard: status history for a true funnel (optional) | **Issue #53, icebox.** Today the funnel is a snapshot by current status, as you say. A real conversion funnel needs a status-change table; sketched in the issue, not scheduled. Tell the Product Owner if you want it. |
| Server-side search and paging | #13, sprint 3. **Proposal written, waiting for your answers** (section below). |
| Dropping your random fallbacks for `location` / `workMode` / `employmentType` | #39, **Product Owner decision** pending: whether the API starts requiring all three on new jobs. Keep your fallbacks for now. |
| Match %, recommended jobs, candidates | **Parked** as you asked (#12, icebox). Nothing will be built until it is scheduled. |
| Employee role dashboard | #26 (P10), Product Owner decision pending. (Your note says "backlog P3"; the correct reference is P10 / #26.) |

**Correction to issue numbers in your "Requests to the back end" list:** admin users and stats is issue **#15** (it says #16), and employer stats is **#16**. The other numbers in your list are right (#9 applications, #10 saved jobs, #12 match score, #13 search, #14 notifications).

Sprint plan after this update: **sprint 2** = #28, #9 (#30 to #33), #10, #11, #17, #21, with #39 to be decided. **Sprint 3** = #13 search, #23 rate limit and password rules. **Sprint 4** = #14, #15, #16, #38.

## Proposal awaiting your answer: server-side job search (#13)
**Status:** not built. I will not start until you answer (reply under "Requests to the back end" in `docs/FRONTEND_STATE.md`; "accept the defaults" is a valid answer). Drafted 2026-10-06.

**Why:** `useJobs` downloads every open job and filters in the browser. That is fine for 9 jobs and breaks at a few thousand.

**Nothing you use today changes.** `GET /jobs` and `GET /jobs?available=true` stay exactly as they are (same plain array, so `sitemap.ts`, the employer panel and any old client keep working). Search is a **new, public route**, `GET /jobs/search`, which you adopt when ready.

### The request
`GET /jobs/search?q=&location=&workMode=&employmentType=&minRate=&maxRate=&skills=&skillsMatch=&available=&sort=&page=&size=`

| Param | Meaning | My default |
|---|---|---|
| `q` | Case-insensitive "contains" over job title, company name, location, description text and required-skill names | none |
| `location` | Case-insensitive "contains" on the free-text location (a clean facet is #37, later) | none |
| `workMode` | `REMOTE`, `HYBRID`, `ONSITE`; repeat it to allow several: `workMode=REMOTE&workMode=HYBRID` | any |
| `employmentType` | Same, repeatable (`FULL_TIME`, ..., `B2B`) | any |
| `minRate`, `maxRate` | Compared on the **hourly equivalent** (`hourlyRate` in the response: monthly / 173.33, yearly / 2080). `CONTRACT_TOTAL` jobs cannot be compared, so they are **left out whenever a rate filter is set** | none |
| `skills` | Required-skill names, repeatable, case-insensitive exact match | none |
| `skillsMatch` | `ANY` (job needs at least one of them) or `ALL` | `ANY` |
| `available` | `true`, `false`, or `all` | `true` |
| `sort` | `newest`, `oldest`, `rateDesc`, `rateAsc`, `title` (a fixed list; anything else is `400`) | `newest` |
| `page`, `size` | Page number and page size | `page=0`, `size=20`, max `50` |

### The response (reuses the `PagedResponse` shape that already exists in the code)
```json
{
  "content": [ /* JobPostResponse, exactly the same shape as GET /jobs items */ ],
  "page": 0,
  "size": 20,
  "totalElements": 134,
  "totalPages": 7,
  "last": false
}
```
No match is `200` with an empty `content`, never `404`. Bad input is `400` in the usual envelope with a readable message, for example `sort must be one of: newest, oldest, rateDesc, rateAsc, title`, `size must be between 1 and 50`, `minRate must not be greater than maxRate`.

### Questions (my recommended answer in bold)
1. **Envelope:** use the shape above? **Yes.** (Alternative: a `items/total` shape of your choice.)
2. **Page numbering:** 0-based like the code, or 1-based? **0-based.**
3. **Over-large `size`:** reject with `400`, or silently cap at 50? **Reject**, so a bug is visible.
4. **Items:** keep the full `JobPostResponse` (full HTML description in every card), or do the cards only need a short plain-text excerpt? **Full shape for now** (no type changes on your side). If cards only show a snippet I can add an optional `summary` (about 200 characters) and drop the long description from search results; tell me.
5. **Rate filter UI:** label it "per hour" and accept that monthly and yearly jobs are converted? **Yes.**
6. **`q` also matching required-skill names and company name:** wanted? **Yes.**
7. **Sitemap:** keep calling `GET /jobs?available=true` (works today), or move to paged search? **Keep as it is.** Later, if the list grows, I would add a lighter ids-only route for the sitemap.
8. **Facet counts** (how many jobs per work mode / type / location, for the sidebar): needed now? **Not in this story.** Say so if the UI cannot work without them and I will size it separately (location facets are #37).

### What changes for you
Replace the client-side filtering in `useJobs` with a debounced request (about 300 ms after typing stops) to `/jobs/search`, keep the filters in the URL, and add paging or "load more". Existing `Job` types are unchanged.

### Honest limits
- Text search is a database "contains" match. It is correct and fine for thousands of jobs, but it cannot use an index, and it is not relevance-ranked. A proper full-text search comes with the PostgreSQL move (#18).
- Matching is on the stored description HTML, so a search for `strong` could match a tag. I will search the text with tags stripped.

### Plan once you answer
8 points: query builder with every filter and sort, input validation, tests for each filter and combination (including the rate rules and the empty and invalid cases), documentation, and a check of query cost on a few thousand generated jobs.

## Open requests for the front end
- [ ] **Session expiry (#50):** anonymous or expired-token calls to protected routes now answer `401` (they were `403`), so the interceptor's existing 401 handling will log the user out. Please check the three points in the 2026-10-06 entry "401 for no valid session" and tick this when done.
- [ ] **Optional, error screens:** show the `X-Request-Id` response header in error toasts or a "report a problem" view (axios: `error.response?.headers['x-request-id']`), so support can find the request in the logs (issue #20).
- [x] **Company dashboard:** *(DONE by you, per your second 2026-10-06 update)* replace `HIRING_CHART_DATA` and the counts around it with `GET /employers/me/stats` (`applications.weekly` for the chart over time, `applications.byStatus` for the stage breakdown, `perJob` for the per-job table, `jobs` for open and closed counts). Use `GET /jobs/{id}/applications` for the candidate list. Remove "views" or mark it as unavailable (issue #16).- [ ] **Admin dashboard:** replace `MOCK_USERS` with `GET /admin/users` (use `content`, `totalPages` and `last` for paging; send `q` for the search box and `role` for a role filter, debounced) and replace the mock metric cards in `ADMIN_METRICS` with `GET /admin/stats`. The pending deletion requests number is in the stats too (issue #15).- [ ] **Notifications:** replace `MOCK_NOTIFICATIONS` with `GET /notifications`, drive the bell badge from `GET /notifications/unread-count` (poll every 30 to 60 s), call `POST /notifications/{id}/read` when one is opened and `POST /notifications/read-all` for "mark all as read". Use `jobId` / `applicationId` for links. Map `type` (uppercase) to your existing icons (issue #14).- [ ] **Seeker dashboard charts:** replace the static `EMPLOYEE_CHART_DATA` with `GET /applications/me/stats` (a weekly bar or line from `weekly`, and a status breakdown from `byStatus`; `active` for the headline number) (issue #38).- [ ] **Answer the search proposal** (section "Proposal awaiting your answer", issue #13): reply under "Requests to the back end" in your file. "Accept the defaults" is enough. I will not start until you do.
- [ ] **Login and register forms:** show the `message` of a `429` as it is (it already says how long to wait), and keep the form usable afterwards. A `429` is not an auth failure, so it must **not** log the user out or clear the token (only `401` does that).
- [ ] **Register and change-password forms:** show the password rules up front (8 to 72 characters, not your username, not a common password) so the user does not hit the `400` first (issue #23).Updated 2026-10-06 after reading your snapshot of the same day. Everything below is merged on `master` and ready to use.

- [x] **Applications, seeker side.** *(DONE by you, per your second 2026-10-06 update)* Replace the in-memory `applyForJob` with `POST /jobs/{id}/apply`, load `GET /applications/me` for the seeker dashboard, and add a withdraw action with `DELETE /applications/{id}` (show it for every status except `REJECTED` and `WITHDRAWN`). Show the readable `message` from the 422 and 403 responses (issues #30, #31).
- [x] **Applications, employer side.** *(DONE by you, per your second 2026-10-06 update)* Replace `MOCK_PIPELINE_CANDIDATES` with `GET /jobs/{id}/applications`, add stage actions with `PUT /applications/{id}/status` (offer only the allowed next stages), and delete the fake `applicationStage.ts` (issues #32, #33). Note: your snapshot says the applications endpoints are "in an unmerged back-end PR"; they are merged (PR #40, 2026-10-05).
- [ ] **Seeker profile editing.** Extend `SeekerResponse` with `educations`, `certifications`, `experiences`, `skills` and build the CRUD UI against `/users/seekers/me/*`. All routes exist; field limits are in `api-documentation.md`.
- [x] **Admin user table.** *(DONE by you, per your second 2026-10-06 update)* Replace `MOCK_USERS` with `GET /admin/users` (exists today, unpaged; paging and search are #15).
- [ ] **Typed clients (optional).** The OpenAPI JSON at `/v3/api-docs` now covers every route; you can generate types from it instead of hand-writing them.
- [ ] **Show the new field limits** in forms if you want to prevent 400s up front (they are in `api-documentation.md`, "Conventions").

**Done by you, thank you (per your second 2026-10-06 update):** applications on both sides (apply, list, stats, withdraw, applicants, stage moves), employer stats, notifications (bell, list, mark read), admin users with search and admin stats, and deleting the mock pipeline and `applicationStage.ts`. Earlier:  `GET /jobs/mine` with 403 handling, `requiredSkills` with the tag input, saved jobs, `companyName`, posted-ago from `createdAt`, and `rate` / `rateType` display.

**Parked / waiting on a decision:** match score (#12, parked as you asked); dropping your `location` / `workMode` / `employmentType` fallbacks waits on the Product Owner's decision in #39, so keep them for now.

## Change log (newest first)

### Sprint 5 — 401 for "no valid session" instead of 403 (#50, B8) — 2026-10-06

**Response change for authenticated routes: this one affects you.**

| Situation | Before | Now |
|---|---|---|
| No token, a malformed token, or an **expired** token on a protected route | `403`, empty body | **`401`**, standard envelope, `message`: `Your session has expired or is not valid. Please sign in again.`, header `WWW-Authenticate: Bearer` |
| Valid token for an account that no longer exists (for example after an approved deletion request) | `500` | **`401`**, same envelope |
| Signed in but the route is not for your role (a seeker calling `/admin/**`, a seeker calling `GET /jobs/mine`) | `403`, empty body | `403`, standard envelope, `message`: `You do not have permission to do that.` |
| Ownership and role rules inside a service (`Only employers can post jobs.` and similar) | `403` with their own messages | unchanged |
| Wrong password at `/auth/login` | `401` `Invalid username or password` | unchanged |
| Public routes (`GET /jobs`, `GET /jobs/{id}`, `GET /content`, `GET /companies/{id}`, `/auth/**`) with a stale or garbage token | worked | unchanged (the token is ignored) |

- **Why:** your interceptor logs the user out on `401`, but an expired 24-hour token used to get `403`, so the session never ended; users saw errors on every call instead.
- **For you to check:** (1) an expired session should now log the user out as intended; (2) make sure no screen deliberately calls a protected route without a token and expects `403`; (3) the interceptor must keep **not** treating a `401` from `/auth/login` (wrong password) as "session ended", exactly as it does today, because that case is unchanged.
- The `401` and `403` responses carry the CORS headers, so browser scripts can read them (tested).
- Issue: #50. Tests: 10 new ones plus about 22 updated expectations (every "anonymous is refused" test now expects 401).

### Sprint 5 — test foundation (#8, T1) — 2026-10-06

**No API change.** Tests, a coverage floor and one small fix to an operations tool. Nothing for the front end to do.
- Coverage is now **95.5% lines / 84.5% branches (345 tests)**, enforced in CI.
- **Finding for you to be aware of (issue #50, waiting for the Product Owner's decision):** a request with **no token, or with a malformed or expired token, gets `403`, not `401`**. Your interceptor logs the user out on `401`, so an expired 24-hour session currently shows an error instead of logging out. The proposed fix (return `401` with the usual error envelope for unauthenticated requests, keep `403` for "signed in but not allowed") would change those responses from 403 to 401; I will add an entry here before doing it. Until then nothing changes.
- Fixed while testing: the database copy tool exited with an error code on `--dry-run`. Not an API matter.
- Issue: #8.

### Sprint 5 — package clean-up (#19, T4) — 2026-10-06

**No API change at all** and nothing for the front end to do. Internal only: two unused classes were deleted and the legacy `model/` package was split into the feature slices. Routes, request and response bodies, status codes, enum names and values, table and column names are identical (the PostgreSQL suite still validates against the unchanged Flyway migrations).
- Issue: #19.

### Sprint 5 — observability and security audit logging (#20, T5) — 2026-10-06

**No request, response body, status code or enum changed.** Everything old works as before. Additions you can use:

- **`X-Request-Id`**: every response now has this header, and CORS exposes it, so `error.response.headers['x-request-id']` works in axios. You may also *send* your own `X-Request-Id` (up to 64 characters of letters, digits, `.`, `-`, `_`; anything else is replaced by a random id). **Suggestion:** show it in the error toast or "report a problem" screen, so a support person can find the exact request in the logs.
- **`Retry-After`** on `429` responses is now exposed to browser scripts too (it was sent before, but scripts could not read it). You can show "try again in N seconds" from it. The message text already says the time in minutes.
- **`GET /actuator/health`**: still public and still `{"status":"UP"}` for you; only an ADMIN token additionally sees components. Nothing for you to change.
- **Audit trail** (back-end only): login success/failure/lockout, rate limiting, registration, password changes, deletion requests, and every `403` ownership or role refusal are now written to the `AUDIT` log. Users are not told anything new; the same messages are returned.
- Behaviour worth knowing: repeated wrong passwords are now visible to operators as `LOGIN_FAILURE` and the locking one as `LOGIN_FAILURE_LOCKED`, per client address.
- Docs: `docs/DEPLOYMENT.md` (section "Logs, correlation ids and the audit trail", "Health"), `api-documentation.md` (conventions).
- Issue: #20. Verified by automated tests (255 earlier plus the new ones), a live run with the `postgres` profile on PostgreSQL 18 (JSON lines, request id, audit lines) and a live run of the `dev` profile. Not done: log shipping, metrics and tracing, an audit table.

### Sprint 5 — PostgreSQL 18, Flyway, Docker, and how to move databases (#18, T3) — 2026-10-06

**No API change.** No route, request, response, status code or enum changed; the front end needs to do nothing. This entry is about where and how the back end runs.

- **New runtime profile `postgres`** (PostgreSQL 18, Flyway migrations, no demo data). The default `dev` profile (H2) is unchanged, so local work and the front end's `localhost:9080` setup behave as before.
- **Demo accounts** (`alice_s`, `techcorp`, ... all with password `password`) exist **only** in `dev` and in tests. A PostgreSQL deployment has none; its first admin is created from `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD`. If you test against a deployed instance, register real accounts.
- **Swagger UI, `/v3/api-docs` and the H2 console** are off in `postgres` (public in `dev`, as before). If you generate API types from `/v3/api-docs`, do it against a `dev` instance, or ask for `APP_DOCS_ENABLED=true`.
- **CORS** for a deployed instance comes from `CORS_ALLOWED_ORIGINS`. Tell the back end the front end's real origin(s) when you deploy (`https://...`); only `localhost:3000/3001` are allowed by default.
- **Removed column:** `job_rating` is gone from the database (it was already gone from the API in sprint 2). `hourly_rate` / `hourlyRate` **stays** and is still returned: stop reading it when you can, then ask for it to be dropped.
- Old H2 dev files are patched automatically at startup (the unused column is dropped, rows are kept). Nothing for you to do.
- Docs: `docs/DEPLOYMENT.md`, `docs/DATABASE_MIGRATION.md`.
- Issue: #18. Verified against a real PostgreSQL 18.6: schema, whole suite (255 tests), an old H2 dev database moved over with a row-count check, and a PostgreSQL-to-PostgreSQL move. **Not verified:** the Dockerfile and `docker-compose.yml` have not been built or run, and the PostgreSQL-to-MySQL guide is untested.

### Sprint 4 — employer dashboard statistics (#16, P8) — 2026-10-06
Additive. No existing route or shape changes.

- **`GET /employers/me/stats`** (employer, own jobs only): `{ jobs, applications, perJob }`.
  - `jobs`: `total`, `open`, `closed`.
  - `applications`: `total`, `active`, `byStatus` (all six statuses, zero-filled) and `weekly` (the last 12 weeks of applications received, oldest first, zero-filled, Monday-start weeks; the same shape as the seeker stats).
  - `perJob`: every job the employer posted (also those with no applicants), newest first, each with `total`, `active` and `byStatus`.
- **`byStatus` is a snapshot of where applications are now, not a conversion funnel.** An application that reached the interview and was then rejected counts only as rejected, because status history is not stored. If you need "how many reached each stage" tell me and I will add a status history (a separate story).
- **Job views are not included.** There is no view tracking and the Product Owner has not asked for it (question open in issue #16). Please drop "views" from the company dashboard or keep it clearly as sample data.
- Seekers, admins and anonymous users are refused (`403`).

### Sprint 4 — admin users with paging and search, and admin stats (#15, P7) — 2026-10-06
**Contract change on `GET /admin/users`:** it now returns a **paged envelope** instead of a plain array. Nothing you call used it (the admin table still shows `MOCK_USERS`), so I changed it rather than keep two shapes. If you had started wiring it as an array, switch to `content`.

- **`GET /admin/users`** (admin): `?q=` (username, first name or last name, contains, any case), `?role=SEEKER|EMPLOYER|ADMIN`, `?sort=username|role|newest|oldest`, `?page=` (from 0), `?size=` (1 to 100, default 20). Response: `{ content, page, size, totalElements, totalPages, last }`, the same envelope proposed for job search. Each item: `id` (numeric account id), `username`, `role`, and now also `name`, `lastName`, `profileId` and `creationDate` (all `null` for admins, who have no profile). Passwords are never returned.
- **`GET /admin/stats`** (admin): `users` (`total`, `byRole`, `newLast7Days`, `newLast30Days`), `jobs` (`total`, `open`, `closed`, `postedLast7Days`), `applications` (`total`, `byStatus` with all six statuses, `submittedLast7Days`) and `pendingDeletionRequests`. All keys are always present.
- Bad paging or sort is a readable `400`; non-admins get `403`.
- **Audit log:** admin actions (listing users, viewing stats, approving or rejecting a deletion, editing content) are now written to a dedicated `AUDIT` log. No effect on the API.
- **Not built (the optional stretch in the issue):** disabling or deleting a user directly, and job moderation. Account deletion still goes through the user's own request and an admin's approval. Say so if you need either and I will size them.

### Sprint 4 — in-app notifications (#14, P6) — 2026-10-06
Additive. New table `notifications`, created in place. Nothing existing changes.

- **`GET /notifications`** (any signed-in user, own only): newest first, `?unreadOnly=true`, `?limit=` 1 to 100 (default 50). Each item: `id`, `type`, `title`, `body`, `read`, `createdAt`, `jobId`, `applicationId` (the last two nullable, for deep links).
- **`GET /notifications/unread-count`** returns `{ "count": n }` for the bell badge. **There is no push yet: poll it** (30 to 60 s).
- **`POST /notifications/{id}/read`** (idempotent, returns the notification; `404` if not yours) and **`POST /notifications/read-all`** (returns `{ "updated": n }`).
- **`type`** is `INTERVIEW | APPLICATION | SYSTEM | HIRING | ACCOUNT`, the same five values as your `MOCK_NOTIFICATIONS` union, in uppercase. Please mirror it exactly.
- **What triggers them:** a seeker applying or withdrawing (the employer is told); the employer moving an application to `IN_REVIEW` (`APPLICATION`), `INTERVIEW` (`INTERVIEW`), `OFFER` (`HIRING`) or `REJECTED` (`APPLICATION`) (the seeker is told); a password change and a declined deletion request (`ACCOUNT`). The full table is in `api-documentation.md`.
- **`title` and `body` are plain text** (they can contain names and job titles typed by users): render them as text, never as HTML.
- **Not sent yet:** "new match" (the matching engine is parked) and `SYSTEM` announcements.
- Read notifications are kept; there is no cleanup of old ones yet. Deleting an account deletes its notifications.

### Sprint 4 — seeker application statistics (#38, P16) — 2026-10-06
Additive. No existing route or shape changes.

- **`GET /applications/me/stats`** (seeker): `{ total, active, byStatus, weekly }`.
  - `byStatus` always has all six statuses (`APPLIED`, `IN_REVIEW`, `INTERVIEW`, `OFFER`, `REJECTED`, `WITHDRAWN`), zero-filled.
  - `active` counts `APPLIED`, `IN_REVIEW`, `INTERVIEW` and `OFFER`; withdrawn and rejected applications are not active.
  - `weekly` is 12 entries `{ weekStart, count }`, oldest first, zero-filled, the last one being the current week. Weeks start on Monday and `weekStart` is that Monday (`yyyy-MM-dd`). It counts when an application was **submitted**; re-applying after a withdrawal counts as a new submission.
  - Non-seekers get `403` `Only job seekers have applications.`
- This replaces your static `EMPLOYEE_CHART_DATA` for the seeker dashboard charts. The interview tips are still static (no endpoint for them).

### Sprint 3 — brute-force protection and password rules (#23, T8) — 2026-10-06
**Contract change:** `POST /auth/register` now rejects weak passwords, and login can answer a new status, `429`.

- **Password policy** on register and on `PUT /account/password`: 8 to 72 characters (register used to accept any length), not equal to the username (any case), not a very common password. Each failure is a `400` with a readable message, listed in `api-documentation.md`. Changing to the same password is `422` `The new password must be different from the current one.` Existing accounts are unaffected, including the seeded `password` demo accounts.
- **Rate limits, new `429` responses** with the usual envelope and a `Retry-After` header (seconds):
  - more than 30 login or register requests per minute from one address: `Too many requests. Please wait 1 minute and try again.`
  - 5 failed logins for one username within 10 minutes locks that username (even for the correct password) until the oldest failure expires: `Too many failed login attempts. Try again in 10 minutes.` A successful login resets the count.
- Unknown usernames lock the same way as real ones, so the response never reveals whether an account exists. Wrong credentials are still `401 Invalid username or password`.
- Both limits are in memory per server and reset on restart. Security events are now logged (failed and blocked logins, lock, rate-limit hits, registrations, password changes).

### Sprint 2 — validation audit and API documentation (#17 T2, #21 T6) — 2026-10-05
Mostly stricter validation and clearer errors. Nothing the current front end sends is rejected, except where noted.

- **Every request body is now validated**, and a test fails the build if a new endpoint forgets `@Valid`. Newly covered: login, availability toggle, CMS update, deletion request and its resolution note.
- **Wording:** a missing required field now reads `<field> is required` everywhere (it used to read `must not be blank` on the older endpoints). Length problems read `<field> must be at most N characters`.
- **New limits, which used to be 500 errors from the database:** username 50, password 72, first/last name 100, company name 150, profile list text 255, experience description 5,000, skill name and category 100, deletion reason and note 255, CMS value 4,000. `yearsOfExperience` must be 0 to 80. `credentialUrl` must start with `http://` or `https://`.
- **Login** with a missing username or password is now `400` `username is required` (wrong credentials are still `401`).
- **`PATCH /jobs/{id}/available`** without the flag is `400` `available is required` (it used to be a vague `400`).
- **`PUT /admin/content`** without `values` is `400` (it used to crash with a 500).
- **`PUT /account/password`**: the rule is now stated as `newPassword must be 8 to 72 characters`.
- **Errors that used to be a 500** are now proper responses in the usual envelope: unknown route `404`, wrong HTTP method `405`, wrong content type `415`, missing request parameter or upload part `400`, and a database conflict `409`.
- **`docs`:** `api-documentation.md` now covers all 55 routes (resume, education, certifications, experiences, skills, account, deletion requests, admin, CMS), the enums, the limits and the new error codes. The OpenAPI JSON at `/v3/api-docs` is generated from the code and is the machine-readable source; you can generate typed clients from it.

### Sprint 2 — saved jobs (#10, P2) — 2026-10-05
Additive. New table `saved_jobs`, created in place.

- **`PUT /jobs/{id}/save`** (seeker): bookmarks a job, `204`, idempotent. Closed jobs may be saved.
- **`DELETE /jobs/{id}/save`** (seeker): removes it, `204`, idempotent (not saved is fine).
- **`GET /jobs/saved`** (seeker): the saved jobs as `JobPostResponse[]`, the same shape as `GET /jobs`, most recently saved first. A job that has closed since stays in the list with `available: false`, so you can grey it out.
- Non-seekers get `403` `Only job seekers can save jobs.`; unknown job is `404`.
- Account deletion also removes the account's bookmarks (and bookmarks of a deleted employer's jobs).

### Sprint 2 — company name on jobs (#11, P5) — 2026-10-05
Additive. Existing fields and shapes are unchanged.

- Every job response (`GET /jobs`, `GET /jobs/{id}`, `GET /jobs/mine`, create and edit) now includes **`companyName`** and **`companyId`**. Both are `null` for an employer who has not created a company, so keep a fallback for that case.
- New public **`GET /companies/{id}`** returns `{ id, name }` (`404` if unknown).
- `logoUrl` is not returned yet: there is no logo storage (issue #34, icebox). Keep deriving initials and colour from the name for now.

### Sprint 2 — applications, stories P1.3 and P1.4: employer side (#32, #33, epic #9) — 2026-10-05
Additive. **Merged to `master` in PR #40 (2026-10-05).** With these, the applications epic is complete on the back end.

- **`GET /jobs/{id}/applications`** (the employer who owns the job). Newest first, withdrawn ones included. Optional `?status=`. Each item is a `JobApplicationResponse`: `id`, `status`, `coverNote`, `createdAt`, `updatedAt` and `applicant { seekerId, username, name, lastName, hasCv }`. Counting per stage is up to you for now (group the list); a stats endpoint comes with #16.
  - `403` `You can only view applications for your own jobs.` (other employers, seekers), `404` unknown job, `400` for an unknown `status` (the message lists the allowed values).
- **`PUT /applications/{id}/status`** (the job's owner). Body `{ "status": "IN_REVIEW" }`, response `200` with the updated `JobApplicationResponse`.
  - Allowed: `APPLIED` -> `IN_REVIEW` | `REJECTED`; `IN_REVIEW` -> `INTERVIEW` | `REJECTED`; `INTERVIEW` -> `OFFER` | `REJECTED`; `OFFER` -> `REJECTED`. `REJECTED` and `WITHDRAWN` are final. Employers cannot set `APPLIED` or `WITHDRAWN`.
  - `422` with a readable message for a disallowed move, e.g. `An application in INTERVIEW cannot move to IN_REVIEW.`; `403` `You can only manage applications for your own jobs.`; `404`; `400` `status is required`.
  - The seeker sees the new stage in `GET /applications/me` straight away.
- A query parameter or path value that is not a valid enum constant now returns `400` with the allowed values, instead of a generic 500.
- **The pipeline is real now**, so `applicationStage.ts` (fake stage from `postId`) can go, and `MOCK_PIPELINE_CANDIDATES` can be replaced by the list endpoint.

### Sprint 2 — applications, story P1.2: withdraw (#31, epic #9) — 2026-10-04
Additive. **Merged to `master` in PR #40 (2026-10-05).**

- **`DELETE /applications/{id}`** (the applicant only). Returns **`204`**. It does **not** delete the record: the status becomes `WITHDRAWN` and `updatedAt` changes, so `GET /applications/me` still lists it, flagged `WITHDRAWN`.
- Allowed from `APPLIED`, `IN_REVIEW`, `INTERVIEW`, `OFFER`. From `REJECTED` or `WITHDRAWN` it is `422` `This application can no longer be withdrawn.`
- `403` `You can only withdraw your own applications.` (another seeker, or an employer); `404` unknown id.
- After withdrawing, the seeker can apply to the job again (`POST /jobs/{id}/apply` re-opens the same application as `APPLIED`).

### Sprint 2 — applications, story P1.1: apply and list mine (#30, epic #9) — 2026-10-03
Additive only; nothing existing changes. **Merged to `master` in PR #40 (2026-10-05).**

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
