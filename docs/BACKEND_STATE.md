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
| Company dashboard: status history for a true funnel (optional) | **Built** (#53, issue title now "P18"): `applications.funnel` and `perJob[].funnel` on `GET /employers/me/stats`. See the change log. |
| `hourlyRate` no longer read by the front end ("drop the column and field whenever it likes") | **Done** (#55, in review): the column and the `hourlyRate` response field are gone. See the change log. Nothing for you to do. |
| Server-side search and paging | #13, sprint 3. **Proposal written, waiting for your answers** (section below). |
| Dropping your random fallbacks for `location` / `workMode` / `employmentType` | #39, **Product Owner decision** pending: whether the API starts requiring all three on new jobs. Keep your fallbacks for now. |
| Match %, recommended jobs, candidates | **Parked** as you asked (#12, icebox). Nothing will be built until it is scheduled. |
| Employee role dashboard | #26 (P10). **Decided 2026-10-07: the role is kept**: a person who connects to a company and manages its jobs and activity (company teams, stories #71 to #74). Not built yet; keep the mock until it ships. |

**Correction to issue numbers in your "Requests to the back end" list:** admin users and stats is issue **#15** (it says #16), and employer stats is **#16**. The other numbers in your list are right (#9 applications, #10 saved jobs, #12 match score, #13 search, #14 notifications).

Sprint plan after this update: **sprint 2** = #28, #9 (#30 to #33), #10, #11, #17, #21, with #39 to be decided. **Sprint 3** = #13 search, #23 rate limit and password rules. **Sprint 4** = #14, #15, #16, #38.

## Open requests for the front end
- [x] *(DONE by you, per your 2026-10-07 update; invitations between two accounts and image/logo uploads not yet clicked through in a browser)* **Company teams (#26, stories #72 to #74):** replace the mock "Employee Preview" with the real thing. (1) Company page for owners: members list (`GET /companies/{id}/members`), invite form (`POST /companies/{id}/invitations` with a username), pending invitations with cancel, remove member, change role. (2) For any employer: `GET /invitations/me` (show a badge or banner when not empty, with accept and decline), "Leave company" (`DELETE /companies/me/membership`), and show the role from `GET /users/employers/me` (`companyRole`); hide owner-only controls from managers. (3) Dashboard: use `GET /companies/{id}/stats` (or `/employers/me/stats`, same numbers) and render the activity feed from `GET /companies/{id}/activity` (summaries are plain text). (4) Notifications: team events arrive with type `SYSTEM`.
- [x] *(DONE by you, per your 2026-10-07 update; invitations between two accounts and image/logo uploads not yet clicked through in a browser)* **Optional, preferences (#25):** load `GET /users/me/preferences` after sign-in and use it instead of `localStorage` for the notification toggles and the accent colour; on a toggle change send `PUT /users/me/preferences` with only that toggle (`{"notifications":{"APPLICATION":false}}`); show `ACCOUNT` as always on. Migrate: on first load, if the server has the defaults and `localStorage` has values, PUT them once. Nothing breaks until you switch.
- [x] *(DONE by you, per your 2026-10-07 update; invitations between two accounts and image/logo uploads not yet clicked through in a browser)* **Optional, location facet (#37):** replace the facet built from the free-text `location` strings with `GET /jobs/locations` (show `name` and `jobs`; for a type-ahead send `q`), and filter jobs with `GET /jobs/search?locationId=<id>` (repeat for several). Keep showing `job.location` as written on a job's own page. Nothing breaks until you switch.
- [x] *(DONE by you, per your 2026-10-07 update; invitations between two accounts and image/logo uploads not yet clicked through in a browser)* **Responsibilities and requirements (#36):** add two list inputs to the Post-a-Job form (and the edit form) and send `responsibilities: string[]` and `requirements: string[]` in `POST /jobs` / `PUT /jobs/{id}` (max 20 items of 300 characters each). On the job page render `job.responsibilities` / `job.requirements` when they are not empty and keep the sentence-splitting heuristic only for jobs where both are empty. Until you send them nothing changes: an edit that omits the fields keeps what is saved.
- [x] *(DONE by you, per your 2026-10-07 update; invitations between two accounts and image/logo uploads not yet clicked through in a browser)* **Optional, logo, benefits and gallery (#34, #35):** replace the initials/gradient with `${API}${job.logoUrl}` when it is not null, and `BENEFIT_POOL` / `GALLERY_POOL` with `job.benefits` / `job.images` (prefix each image with the API base) when they are not empty; keep the pools only as fallback for jobs without any. Add the three inputs to the Post-a-Job form if you want employers to fill them: benefits as `benefits: string[]` in the job body, pictures as separate multipart `POST /jobs/{id}/images` calls after the job exists (up to 6, 2 MB each), and a logo upload on the company page (`POST /users/companies/{id}/logo`, 1 MB).
- [x] **Optional, company dashboard funnel (#53):** *(NOT NEEDED for now, per your 2026-10-06 note: the dashboards show applications by current status only. The funnel fields stay available in the response whenever you want them.)* show `applications.funnel` (and `perJob[].funnel`) as a real conversion funnel and, if you like, "typical time in stage" from `medianDaysInStage`. Details and the `null` rule are in the 2026-10-06 entry "a true hiring funnel".
- [x] **Session expiry (#50):** *(CONFIRMED by you on 2026-10-06: the interceptor logs out on any 401 outside `/auth/*`, no screen expects a token-less 403, and a login/register 401 or a 429 does not log out. Thank you.)* anonymous or expired-token calls to protected routes now answer `401` (they were `403`), so the interceptor's existing 401 handling will log the user out. Please check the three points in the 2026-10-06 entry "401 for no valid session" and tick this when done.
- [ ] **Optional, error screens:** show the `X-Request-Id` response header in error toasts or a "report a problem" view (axios: `error.response?.headers['x-request-id']`), so support can find the request in the logs (issue #20).
- [x] **Company dashboard:** *(DONE by you, per your second 2026-10-06 update)* replace `HIRING_CHART_DATA` and the counts around it with `GET /employers/me/stats` (`applications.weekly` for the chart over time, `applications.byStatus` for the stage breakdown, `perJob` for the per-job table, `jobs` for open and closed counts). Use `GET /jobs/{id}/applications` for the candidate list. Remove "views" or mark it as unavailable (issue #16).- [ ] **Admin dashboard:** replace `MOCK_USERS` with `GET /admin/users` (use `content`, `totalPages` and `last` for paging; send `q` for the search box and `role` for a role filter, debounced) and replace the mock metric cards in `ADMIN_METRICS` with `GET /admin/stats`. The pending deletion requests number is in the stats too (issue #15).- [ ] **Notifications:** replace `MOCK_NOTIFICATIONS` with `GET /notifications`, drive the bell badge from `GET /notifications/unread-count` (poll every 30 to 60 s), call `POST /notifications/{id}/read` when one is opened and `POST /notifications/read-all` for "mark all as read". Use `jobId` / `applicationId` for links. Map `type` (uppercase) to your existing icons (issue #14).- [ ] **Seeker dashboard charts:** replace the static `EMPLOYEE_CHART_DATA` with `GET /applications/me/stats` (a weekly bar or line from `weekly`, and a status breakdown from `byStatus`; `active` for the headline number) (issue #38).- [ ] **Job search:** replace the client-side filtering in `useJobs` with a debounced request (about 300 ms after typing stops) to `GET /jobs/search` (see the change log entry for #13), keep the filters in the URL and add paging or "load more". `GET /jobs` is unchanged, so nothing breaks until you switch (issue #13).
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

### Sprint 6 — company teams, part 2: invitations, members, dashboard, activity feed, team notifications (#72, #73, #74, epic #26) — 2026-10-07

**All additive; nothing existing breaks.** With these, the "employee" the Product Owner described works end to end: an employer account connects to a company and manages its jobs and activity. Full route list, bodies and messages are in `api-documentation.md` ("Company teams").

**Connecting (#72).**
- The owner invites an existing employer account by username (`POST /companies/{id}/invitations`, 14 days). The invited person sees it in `GET /invitations/me` and answers with `POST /invitations/{id}/accept` or `/decline`. Accepting makes them a `MANAGER` and cancels their other pending invitations. The owner can list and cancel pending ones.
- `GET /companies/{id}/members` (any member), `DELETE /companies/{id}/members/{username}` (owner removes), `DELETE /companies/me/membership` (leave), and **`PUT /companies/{id}/members/{username}/role`** (owner promotes or demotes: not in the issue, but without it a sole owner could never leave). A company always keeps an owner; the only owner cannot leave (`422`, with the message telling them what to do).
- A person belongs to one company at a time. Seekers cannot be invited; an unknown name and a seeker's name give the same `422` (`No employer account is named 'x'.`).
- Removed or leaving people: the jobs they posted stay with the company.
- Employer profile: the front end can now also read when someone joined (`joinedAt` on members).

**Dashboard and feed (#73).**
- `GET /companies/{id}/stats`: the same shape as `GET /employers/me/stats`, across all the company's jobs (members only). `GET /employers/me/stats` already covered the whole company since #71 and still works.
- `GET /companies/{id}/activity?limit=`: who did what, newest first (jobs posted, edited, closed, reopened; applications moved with from and to; people invited, joined, left, removed; roles changed). `actor` is a username, `summary` a plain sentence.

**Notifications (#74).**
- A new or withdrawn application notifies **every member of the company**, each respecting their own toggles (#25). Team events (invited, joined, declined, left, removed, role changed) notify the people concerned, with type **`SYSTEM`** (no new notification type, so your icon mapping needs no change). Because users can switch `SYSTEM` off in preferences, invitations are also listed at `GET /invitations/me`: show that list somewhere permanent (for example the company page) and not only through the bell.

**Database:** `employers.company_joined_at`, tables `company_invitations` and `company_activity` (Flyway `V11`). Accounts that are deleted lose their invitations.

**What the front end now needs for the "Employee" experience** (all optional, nothing breaks until then): see "Open requests for the front end". The "Employee Preview" mock can be replaced by the real company dashboard.
- Issues: #72, #73, #74 (epic #26).

### Sprint 6 — company teams, part 1: roles in a company and one access check (#71, epic #26) — 2026-10-07

**Additive; nothing changes for a company of one.** The groundwork for several people per company. You cannot put a second person into a company yet (invitations are #72), so this is not visible in the app until then.

- **New field `companyRole: "OWNER" | "MANAGER" | null` on `EmployerResponse`** (`GET /users/employers/me`, `GET /users/employers/{id}`). `null` when the employer has no company. Everyone who already has a company is `OWNER`; whoever creates one becomes its owner.
- **Permissions:** a job (and its images, applicants, status moves and statistics) can be managed by the person who posted it **and by everyone in the same company**. `GET /jobs/mine` and `GET /employers/me/stats` now cover the whole company. Someone outside the company gets the same `403` messages as before.
- **`MANAGER` cannot change the company**: name (`PUT /users/companies/{id}`), logo upload and removal answer `403` with `Only the company owner can do that.` Show those controls to owners only.
- **Leaving:** when an account is deleted and colleagues remain, the person's jobs, their applications and the logo **stay with the company** (the jobs pass to an owner; if no owner is left, to the longest-serving colleague, who becomes owner). A person alone in their company takes their jobs with them, as before.
- Database: `employers.company_role` (Flyway `V10`; existing employers with a company are set to `OWNER`, also by a harmless startup backfill). Deviation from the issue text: no separate `company_members` table. A user belongs to one company at a time, so the role lives on the existing employer-to-company link; a table can come with #72 if multiple companies per user is ever wanted.
- Not done here: `GET /companies/me` (the role is on `/users/employers/me`), the activity feed (#73), team notifications (#74): new-application notices still go to the poster only.
- Issue: #71.

### Sprint 6 — user preferences: notification toggles and accent colour (#25, P9) — 2026-10-07

**New routes, additive: nothing existing changes.** Any signed-in user (seeker, employer or admin), own settings only.

- `GET /users/me/preferences` -> `{ "notifications": { "INTERVIEW": true, "APPLICATION": true, "SYSTEM": true, "HIRING": true, "ACCOUNT": true }, "accentColor": null }`. A user who never saved anything gets all `true` and `null`.
- `PUT /users/me/preferences` with any of `{ "notifications": { "APPLICATION": false }, "accentColor": "#1a73e8" }`. **Only what is sent changes**, so send just the toggle the user touched. Names are the notification `type` values you already map to icons. `accentColor` is `#rrggbb` (stored lower case); `""` clears it; leaving it out keeps it. Returns the full settings.
- `400` (nothing changed) with a readable `message`: unknown type name (lists the valid ones), a non-boolean value, a bad colour, a missing body. **`ACCOUNT` cannot be switched off** (`ACCOUNT notifications cannot be turned off: they tell you about your password and account.`): show that toggle as on and disabled.
- **The toggles have an effect on the server**: a switched-off type is not stored for that user, so it does not appear in `GET /notifications`, is not counted in `unread-count`, and is not brought back by switching it on again. (In-app messages only; there is no email or push yet.)
- Database: tables `user_preferences` and `user_disabled_notifications` (Flyway `V9`); rows are removed when an account is deleted.
- **Answer to your RabbitMQ question: not necessary, and not better yet.** Notifications are written in the same transaction as the event that caused them, so they can never disagree with the data; a broker would add a second system and the need for an outbox anyway. It starts to pay off when email/push, several instances or other consumers appear, and even then the outbox in the database comes first. Reasoning and the upgrade path: `docs/NOTIFICATIONS.md`.
- Issue: #25.

### Sprint 6 — places: clean location values, facet list and search filter (#37, P15) — 2026-10-06

The Product Owner left the model to me ("do what is best, like a location DB"). I built a **catalogue of places** behind the free text. **Additive: nothing existing breaks**, and `location` stays exactly as the employer typed it.

- **New field `locationId: string | null` on `JobPostResponse`** (every job route, including search). It is the id of the clean place the text resolves to; `null` when the text names no place (for example just `Hybrid`).
- **New public route `GET /jobs/locations`**: `[{ id, name, city, region, countryCode, country, remote, jobs }]`, most jobs first. `?available=true|false|all` (default open jobs), `?q=` (name contains, for a type-ahead), `?limit=` (1 to 500, default 100). Places without a counted job are not listed. `name` is ready to show (`Berlin, Germany`, `Remote, United States`, `Austin, TX, United States`, `Berlin, Germany (remote)`).
- **`GET /jobs/search` has a new repeatable `locationId` filter** (max 20 ids, OR between them, unknown id = no match). The old free-text `location` filter is unchanged.
- **Merging, the point of the story:** `Remote — US`, `Remote, USA` and `remote (us)` are one place; so are `Berlin, DE`, `berlin, Germany` and `BERLIN,Deutschland`. Case, accents, country names, aliases (USA, UK, Deutschland...), ISO codes, US state names or codes, and `remote` written before, after or in brackets are understood. The full rules are in `api-documentation.md`. Limits to know: a bare capital `CA`, `PA`, `GA` and the other codes that are both a country and a US state mean the **state** (write `Canada` for the country); a city typed **without** a country joins the one known country for that city if there is exactly one, otherwise it is its own entry; the catalogue cannot know that a city exists, so a misspelling is a new place.
- Existing jobs are filled at startup (`LocationBackfill`, harmless to repeat). Editing a job re-resolves its place. A job saved without its place can only be a very old row.
- Database: table `locations` and column `job_posts.location_id` (Flyway `V8` on PostgreSQL). No external geo service.
- For you: replace your client-side location facet (the one built from free-text strings) with `GET /jobs/locations` and filter with `locationId` (see "Open requests for the front end"). Show `job.location` as the job's own text.
- Issue: #37.

### Sprint 6 — structured responsibilities and requirements on jobs (#36, P14) — 2026-10-06

The Product Owner chose option 2. **Additive: nothing existing breaks.**

- **New fields on `JobPostResponse`** (every route that returns a job, including `GET /jobs/search`): `responsibilities: string[]` and `requirements: string[]`, in display order, `[]` when none.
- **`POST /jobs` and `PUT /jobs/{id}` accept both** (`CreateJobRequest`): each at most **20** entries, each non-blank and at most **300** characters, trimmed. **Omit a field on `PUT` and the saved list stays** (each list is independent); send `[]` to remove it. Errors (400): `responsibilities must have at most 20 entries`, `responsibilities[0] must be at most 300 characters`, `... must not be blank`.
- **Existing jobs return `[]`.** Keep your sentence-splitting heuristic as the fallback for jobs where both lists are empty, so old jobs look the same as today.
- Database: tables `job_responsibilities` and `job_requirements` (Flyway `V7` on PostgreSQL).
- For you: add a "Responsibilities" and a "Requirements" list input to the Post-a-Job form (one line per item; the limits above) and send them as arrays; render `job.responsibilities` / `job.requirements` when not empty (see "Open requests for the front end").
- Issue: #36.

### Sprint 6 — company logo (#34) and job benefits and images (#35) — 2026-10-06

Product Owner said "start with your defaults" (you had not answered), so these are my defaults. **Everything is additive: no existing request or response breaks.** All three are optional for you; adopt them when you want to drop your hardcoded pools and initials.

**New fields on `JobPostResponse`** (every route that returns a job, including `GET /jobs/search`): `benefits: string[]` (display order, `[]` when none), `images: string[]` (URLs, oldest first, at most 6, `[]` when none) and `logoUrl: string | null`. `CompanyResponse` (company routes and `GET /companies/{id}`) gets `logoUrl` too.

**URLs are relative to the API address.** Put your API base in front: `${API}${job.logoUrl}`. They are public, so `<img src>` works without a token. The logo URL ends in `?v=<number>` that changes with every upload, so a new logo is never stuck behind a cache.

**Benefits (#35).** `POST /jobs` and `PUT /jobs/{id}` accept `benefits: string[]`: at most 15, each non-blank and at most 80 characters, trimmed. **Omit it on `PUT` and the saved benefits stay** (so an old client editing a job loses nothing); send `[]` to remove them all. Errors: `benefits must have at most 15 entries`, `benefits[0] must be at most 80 characters`, `... must not be blank` (400).

**Job images (#35).** `POST /jobs/{id}/images` (multipart field `file`, owner only, `201` with the updated job), `DELETE /jobs/{id}/images/{imageId}` (owner only, `200` with the updated job), `GET /jobs/{id}/images/{imageId}` (public, the picture, cacheable for a year). PNG, JPEG or WebP up to 2 MB, six per job; anything else is `422` with a readable message. The format is read from the file's bytes, so a renamed file or an SVG is refused.

**Company logo (#34).** `POST /users/companies/{id}/logo` (multipart `file`, PNG/JPEG/WebP up to 1 MB, replaces the old one, `200` with the company), `DELETE /users/companies/{id}/logo` (`204`), public `GET /companies/{id}/logo`. Only the employer who owns the company: another employer or a seeker gets `403`, an unknown company `404`.

Behaviour worth knowing:
- Files live in the upload folder (`job-images/`, `company-logos/`), so **back up the uploads volume with the database**, as already documented for resumes. Removing an employer account (admin approval) removes their job images and logo files.
- Database: tables `job_benefits` and `job_images`, three nullable columns on `companies` (Flyway `V6` on PostgreSQL). Existing jobs return empty `benefits` and `images`, so keep your fallback pools for jobs that have none.
- Uploads need `multipart/form-data` (FormData in the browser; do not set the content type yourself).
- Jobs are loaded in batches now (`hibernate.default_batch_fetch_size=50`), so the extra lists do not add a query per job.
- Issues: #34, #35.

### Sprint 6 — Spring Boot 4.0.8 (GA) and springdoc 3 (#22, T7) — 2026-10-06

No API change: routes, shapes, status codes and error messages are the same, and the whole suite (430 tests) passes unchanged on H2 and on PostgreSQL 18. Also checked by starting the packaged jar: health UP, login, `GET /jobs/search` and Swagger respond. Nothing for the front end to do. Dependabot now opens weekly update PRs. Not done: an OWASP dependency-check job. Issue: #22.

### Sprint 6 — `location`, `workMode` and `employmentType` are required on jobs (#39, P17) — 2026-10-06

You did not need to answer: the Product Owner decided **yes**.

**Request change: `POST /jobs` and `PUT /jobs/{id}` now return `400` if any of the three is missing** (`location is required`, `workMode is required`, `employmentType is required`; several are joined with `; `). A blank or whitespace-only `location` counts as missing. Responses are unchanged.

- Your Post-a-Job form already sends all three, so no change is needed there. Any other caller that omitted them breaks.
- **Existing jobs with null values still work:** they are returned as before (fields absent/null), can be opened and closed, and are searchable. They are only refused when their owner saves an edit (`PUT`) without filling the three in. So **keep your fallbacks for now**: they can be removed once old data is gone. Demo and seeded jobs already have all three. I cannot clean real-world nulls for you; an owner has to edit those jobs.
- Issue: #39.

### Sprint 6 — server-side job search, filters and paging (#13, P4) — 2026-10-06

You had not answered the proposal, so I built the **defaults** I proposed. Nothing existing changes: `GET /jobs` and `GET /jobs?available=true` still return the same plain array.

**New public route `GET /jobs/search`** (no token needed; a stale or bad token is ignored). Response is a page, `{content, page, size, totalElements, totalPages, last}`, and each `content` item is exactly the `JobPostResponse` that `GET /jobs/{id}` returns (no `hourlyRate`).

| Parameter | Meaning |
|---|---|
| `q` | words to find, case-insensitive "contains", in the title, company name, location, the description **as plain text** (tags are not searched) and the required-skill names. At most 100 characters. `%` and `_` are ordinary characters |
| `location` | contains match on location only (max 100) |
| `workMode`, `employmentType` | enum values, repeat the parameter for several (`workMode=REMOTE&workMode=HYBRID` = either). Different filters are AND-ed |
| `minRate`, `maxRate` | compared on the **hourly equivalent** (monthly / 173.33, yearly / 2080), inclusive, to the cent. `CONTRACT_TOTAL` jobs have no hourly equivalent, so any rate bound leaves them out |
| `skills`, `skillsMatch` | skill names (repeat or comma-separated, case-insensitive, max 20). `skillsMatch=ANY` (default) or `ALL` |
| `available` | `true` (default, open jobs) , `false` (closed) or `all` |
| `sort` | `newest` (default), `oldest`, `rateDesc`, `rateAsc` (hourly equivalent, contract totals last), `title` (case-insensitive). Ties always break by id, so pages never overlap |
| `page`, `size` | 0-based page (default 0); size default 20, **max 50** |

- An empty result is `200` with `content: []`, never 404.
- Bad input is `400` in the usual envelope with a readable `message`, for example `size must be between 1 and 50.`, `sort must be one of: newest, oldest, rateDesc, rateAsc, title.`, `minRate must not be greater than maxRate.`; an unknown enum value (`workMode=UNDERWATER`) names the parameter.
- No facet counts (how many remote jobs, and so on); say so if you want them.
- Text search is a database "contains" match: correct, not relevance-ranked, not index-assisted. Measured on PostgreSQL 18 with 20,000 jobs of about 1.6 KB each, a worst-case full scan took about 0.45 s; a trigram index is the follow-up if it gets slow.
- Database: new column `job_posts.description_text` (plain text of the description, kept in step on create and edit; Flyway `V5` on PostgreSQL). Jobs that existed before are filled once at startup (`JobSearchTextBackfill`), so until that finishes they are findable by everything except description words.
- Open for you: adopt it in `useJobs` (see "Open requests for the front end"). The sitemap keeps using `GET /jobs?available=true`.
- Issue: #13.

### Sprint 6 — `hourlyRate` removed from the job response, `hourly_rate` column dropped (#55) — 2026-10-06

**Response change: `hourlyRate` is gone from `JobPostResponse`** (everywhere a job is returned: `GET /jobs`, `GET /jobs/{id}`, `GET /jobs/mine`, `GET /jobs/saved`, `POST /jobs`, `PUT /jobs/{id}`, `PATCH /jobs/{id}/available`). You confirmed on 2026-10-06 that nothing reads it any more, so no change is needed on your side. Every other field is unchanged.

- **`rate` and `rateType` are now always present and never null** (they were nullable only for very old rows). That matches your `Job` type, where both are required. The database now enforces it (`NOT NULL`).
- Requests are unchanged: an old client that still sends `hourlyRate` (or `jobRating`) is not rejected; the value is ignored.
- Old data keeps its price: any row from before the compensation model that had no `rate` was given its old hourly rate as `rate` with `rateType` `HOURLY` before the column was dropped (Flyway `V4` on PostgreSQL; the H2 startup patch for old dev files).
- The conversion to an hourly equivalent stays in the code (`RateType.toHourly`, tested) for the coming search (`minRate` / `maxRate`, #13); nothing stores or returns it any more.
- Issue: #55.

### Sprint 6 — a true hiring funnel from application status history (#53, P18) — 2026-10-06

**Additive. The old shape of `GET /employers/me/stats` still works unchanged; two new fields are added.**

New fields (same route, same status codes, no new route):
- `applications.funnel` (all of the employer's jobs) and `perJob[].funnel` (that job only), each:
  - `reached`: `{ "APPLIED": n, "IN_REVIEW": n, "INTERVIEW": n, "OFFER": n }`. How many applications **ever reached** each stage. Always these four keys, zero-filled, in pipeline order. **Cumulative**, so it never increases from one stage to the next: an application now at INTERVIEW counts for APPLIED, IN_REVIEW and INTERVIEW; one rejected after an interview counts up to INTERVIEW; one withdrawn while in review counts up to IN_REVIEW. A withdrawn-then-reapplied application counts once, by its latest attempt.
  - `medianDaysInStage`: same four keys, a number with one decimal or **`null`**. Median days applications stayed in a stage, counting only stays that **ended** (moved on, rejected or withdrawn). It is `null` until the first stay ends; an application still in a stage is not counted yet.
- `applications.byStatus` is unchanged and still means "where applications are now".

How you can use it: a funnel chart from `reached` (drop-off between bars is the number who left at that stage), and percentages computed on your side (`reached.INTERVIEW / reached.APPLIED`). Treat `null` medians as "no data yet", not zero. Render both new objects defensively (an older back end would not send them).

Behaviour worth knowing:
- Every apply, stage move, withdrawal and re-application now writes a history row (who and when). Failed or refused moves write nothing.
- Applications made before this change got a **minimal history at startup**: created as APPLIED and, if they had moved on, one step from APPLIED to their current status. For those, the stages in between are unknown, so a rejected old application counts only for APPLIED and their durations only run from creation to last update. The data is exact for everything from now on.
- Deleting an account (approved deletion request) removes the history with the applications; nothing for you to do.
- Not built (say so if you want it): a per-application history route for a timeline view, and time-to-hire.
- Database: Flyway `V3__application_status_changes`. Issue: #53 (title renamed from "P17" to "P18" to avoid a clash in the backlog numbering).

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
