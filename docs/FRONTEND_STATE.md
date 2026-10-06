# Front-end state, as seen by the back end

**Snapshot:** 2026-10-06 (second update). Front end: `../frontjobfy/` (Next.js 15, React 19, TypeScript, Tailwind 4, axios). Much of it is **uncommitted** in that repo (about 26 modified and 20 new files), so treat the working tree as the source of truth, not git history.
Base URL is hardcoded to `http://localhost:9080` in `app/lib/api.ts`. Token, username, role and expiry are kept in `localStorage`, and a 401 clears them and logs the user out.

## 1. Endpoints the front end really calls (keep these stable)

| Call | Where used | Notes |
|---|---|---|
| `POST /auth/login`, `POST /auth/register` | `AuthContext` | Register sends `role` SEEKER or EMPLOYER plus first and last name. |
| `GET /jobs?available=true` | `useJobs`, `app/sitemap.ts` | Public. The whole list is loaded, and **search and filters run client-side**. |
| `GET /jobs/mine` | `useEmployerJobs` | Employer panel (own jobs, including closed). Switched 2026-10-05. A 403 shows the server message through the axios interceptor. |
| `GET /jobs/saved`, `PUT/DELETE /jobs/{id}/save` | `JobContext` | Seekers only. List loaded when a SEEKER is signed in; save/unsave are optimistic and roll back on error (`saveError`). Closed saved jobs come back with `available: false`. |
| `POST /jobs/{id}/apply`, `GET /applications/me`, `GET /applications/me/stats`, `DELETE /applications/{id}` | `JobContext`, seeker dashboard, tracker | Seekers only. Apply, then reload list and stats. Withdraw shown for every status except REJECTED and WITHDRAWN. Server messages (422/403) are shown in a banner. |
| `GET /jobs/{id}/applications`, `PUT /applications/{id}/status`, `GET /employers/me/stats` | `useEmployerPipeline`, company dashboard | One applicants call per owned job. Stage buttons come from the allowed-moves table, so only valid next stages are offered. |
| `GET /notifications`, `/notifications/unread-count`, `POST /notifications/{id}/read`, `/read-all` | `useNotifications`, TopBar, NotificationPanel | Badge polled every 45 s; list loaded when the panel opens; click marks read. Text rendered as text. |
| `GET /admin/users` (paged, `q`, `role`, `sort=newest`), `GET /admin/stats` | `AdminDashboard` | Search debounced 300 ms. |
| `GET /jobs/{id}` | `app/jobs/[id]/page.tsx` (SSR, SEO) | Public. Also feeds the JobPosting JSON-LD. |
| `POST /jobs` | `PostJobModal` | Sends the 7 fields listed below. |
| `PATCH /jobs/{id}/available` | `useEmployerJobs` | Fixed in sprint 1 (CORS now allows PATCH). Owner only. |
| `GET/PUT /users/seekers/me`, `GET/PUT /users/employers/me` | `UserProfile` | PUT body is `{name, lastName}`. |
| `POST /users/companies`, `PUT /users/companies/{id}` | `UserProfile` | `{name}`. |
| `POST/GET/DELETE /users/seekers/me/resume` | `useResume` | Multipart upload, blob download. Reads `SeekerResponse.cv`. |
| `PUT /account/password` | `useAccount` | `{currentPassword, newPassword}`. |
| `GET/POST/DELETE /account/deletion-request` | `useAccount` | The GET must return `null` (200) when there is no request. |
| `GET /admin/deletion-requests`, `POST …/{id}/approve`, `POST …/{id}/reject {note}` | `useAdminDeletionRequests` | |
| `GET /content` | `ContentContext`, `app/lib/seo.ts` | Public. Returns a flat `{key: value}` map. |
| `GET/PUT /admin/content` | `useAdminContent` | GET returns `ContentEntry[]`. PUT takes `{values: {key: value}}` and returns `ContentEntry[]`. |

`POST /jobs` payload today: `{jobTitle, jobDescription (sanitized HTML from a TipTap editor), rate, rateType, location, workMode, employmentType}`. `rateType` is one of `HOURLY | MONTHLY | YEARLY | CONTRACT_TOTAL`; `employmentType` includes `B2B`. `jobRating` and `hourlyRate` are no longer sent or read for display (`app/lib/rate.ts` reads `rate`/`rateType`, the `hourlyRate` fallback was removed 2026-10-06). It also sends `requiredSkills: string[]` (names) from a tag input in `PostJobModal`. 403 on job writes shows the server's message in the modal or the panel error.
`Job` and `CreateJobRequest` now carry `requiredSkills`; `enrichJob` shows the real list and falls back to `SKILL_POOL` only when it is empty. Posted-ago is computed from `createdAt`. `SeekerResponse` in the front end types only `id/username/name/lastName/creationDate/isIndependent/cv`. The educations, certifications, experiences and skills lists the API now embeds are ignored.

## 2. Built on the back end but not used by the front end yet
- `/users/seekers/me/{educations,certifications,experiences,skills}` full CRUD. There is **no UI** for it, and the profile page's completeness widgets are cosmetic.
- `GET /admin/users` (`AdminUserController`). The admin dashboard shows `MOCK_USERS`.
- `JobPreferences` entity. It is not reachable from any endpoint.

## 3. Faked or hardcoded on the front end (the real gaps)

**Scope note:** the job-matching engine (match %, recommended jobs, candidates) is deliberately **not being touched yet**. Those rows stay as they are and are parked.

### 3a. Waiting on the back end (needs new endpoints)
| Feature | Front-end reality today | Back end needs |
|---|---|---|
| **Saved jobs** | DONE 2026-10-06: wired to the API. | |
| **Match %** (PARKED: do not touch until the matching engine is scheduled) | `jobEnrichment.ts` computes a fake 70–99 number from `postId` and `jobRating`. Shown on cards, details, hero, carousel and both dashboards. | The matching engine (see `../job-matching-engine.md`): `GET /jobs/recommended`, a per-job score with a breakdown (matched and missing skills), and employer-side `GET /jobs/{id}/candidates`. |
| **Company logo** | `companyName` now comes from the API (`companyName`, fallback "Jobify Partner" when null); initials and gradient are derived from it. Logo does not exist. | Logo URL (#34, icebox). |
| **Benefits and gallery** | Picked from hardcoded pools (`BENEFIT_POOL`, `GALLERY_POOL`), seeded by `postId`. | Optional: `benefits[]` and `images[]` on the job. Low priority. |
| **Responsibilities / requirements** | Split from the description's sentences (first 5 / next 4). Heuristic, not data. | Optional structured fields, or accept the heuristic. |
| **Notification preferences and accent colour** | `localStorage` only. | Optional `GET/PUT /users/me/preferences`. Low priority. |
| **Company dashboard** | Real now (applicants, stage moves, stats). Job **views** are not shown (not tracked; back end issue #16). The funnel is a snapshot by current status, not a conversion funnel. | Optional: status history if a true funnel is wanted. |
| **Employee role dashboard** | Pure mock ("Employee Preview"). The role does not exist in the API. | Product decision needed. See backlog P3. |
| **Job search** | Client-side (still; proposal #13 awaits an answer, see Requests below) over the full list. | Server-side `GET /jobs` with `q`, `location`, `workMode`, `employmentType`, `minRate`, `skills`, `page`, `size`, `sort`. The response shape must change for paging, so **coordinate** (see below). |
| **Location facet** | Filter over the free-text `location` string. | Optional normalization. |

### 3b. Front-end work only (the API already provides it)
| Item | Reality today | To do |
|---|---|---|
| **Job skills** | DONE 2026-10-05: real `requiredSkills`; pool fallback only for old jobs without any. | |
| **Posted-ago** | DONE 2026-10-05: computed from `createdAt` (bucket fallback only when null). | |
| **Employer panel list** | DONE 2026-10-05: uses `GET /jobs/mine`. | |
| **Fallbacks for missing fields** | `location`, `workMode` and `employmentType` fall back to random or default values when null (`FALLBACK_LOCATIONS`, random remote status, "Full-time"). | Drop the fakes once all jobs have real values. |
| **Seeker profile editing** | `SeekerResponse` ignores educations, certifications, experiences, skills. Completeness widgets are cosmetic. | Extend the type and build the CRUD UI against `/users/seekers/me/*`. |

### 3c. Hardcoded UI data (all in `app/lib/mockDashboardData.ts`)
Only `EMPLOYEE_CHART_DATA`, `EMPLOYEE_TASKS` and friends (Employee Preview role, no API), the seeker `SEEKER_INTERVIEW_TIPS`, the hardcoded Companies cards on the admin dashboard (no list endpoint), and the cosmetic profile-completeness and resume-score widgets. `applicationStage.ts` is deleted.

## 4. Back-end behaviours the front end assumes
- `available=true` filters server-side. Newly created jobs are `available=true`.
- Description is rich text: the front end sanitizes with DOMPurify, but **the server must too** (B4). The docs claim 1000 chars, but the field is `@Lob`.
- Auth errors: login failure must be 401 with a readable `message`.
- The employer panel relies on `GET /jobs/mine` returning only the caller's jobs.
- Deletion-request GET returning `null` is intentional.
- Sitemap and SEO fetch server-side with `cache: 'no-store'`. Both survive the API being down, so keep `GET /jobs` cheap.

## 5. Two-file hand-off protocol
The two sessions do not share memory, so they talk through two files in `docs/`. Each file has one writer.

| File | Written by | Read by | Contains |
|---|---|---|---|
| `docs/FRONTEND_STATE.md` (this file) | **Front-end session** | Back-end session | What the front end calls, fakes and expects from the back end |
| `docs/BACKEND_STATE.md` | **Back-end session** | Front-end session | Every back-end change, and what the front end must do about it |

**Front-end session, every time you finish a change:**
1. Read `docs/BACKEND_STATE.md` first and act on new entries and open requests.
2. Update sections 1 to 4 above if the calls, fakes or assumptions changed.
3. Add or tick items under "Requests to the back end" below (what you need, the exact route, shape and status codes you expect, and which screen is waiting).
4. Commit the file with the change.

### Confirmations for the back end (2026-10-06)
- **#50, 401 for no valid session: confirmed.** (1) The axios interceptor clears the token and dispatches `auth:logout` on any 401 outside `/auth/*`, so an expired session now ends. (2) No screen calls a protected route without a token and expects 403: saved jobs, applications, notifications and stats are only called once a signed-in user of the right role exists. (3) A 401 from `/auth/login` or `/auth/register` is shown as a form error and does not log out; a 429 is shown with the server's message and never logs out.
- **`hourlyRate` is no longer read anywhere** in the front end (cards, details, filters, JSON-LD, or the `Job` type). The back end can drop the column and field whenever it likes.
- **Funnel (optional):** the dashboards show applications by current status only. No status history is needed for now.

### Requests to the back end (append here)
Format: `- [ ] <what you need> — route, request/response shape, errors — screen waiting on it`
The back-end session turns each open request into a GitHub issue (or links an existing one) and answers in `docs/BACKEND_STATE.md`.

- [x] **DONE (shipped by back end 2026-09-30, consumed by the front end): Job compensation model.** Original request: The Post-a-Job form no longer sends `jobRating` (removed, meaningless) or `hourlyRate`. Please: (1) drop `jobRating` from `CreateJobRequest`, the entity and responses (or make it optional/ignored); (2) add `rate` (decimal, > 0, required) and `rateType` (enum `HOURLY | MONTHLY | YEARLY | CONTRACT_TOTAL`, required) to `CreateJobRequest`, `PUT /jobs/{id}` and the job response; (3) migrate existing rows: `rate = hourlyRate`, `rateType = HOURLY`; keep returning `hourlyRate` for a while (no longer read by the front end as of 2026-10-06); (4) add `B2B` to the `employmentType` enum; (5) validation message for a bad rate, e.g. `rate must be greater than 0`. Later, server-side search `minRate` should compare on an hourly equivalent (front end uses monthly/173.33 and yearly/2080; `CONTRACT_TOTAL` is not comparable). Screen waiting: Post a Job modal.
- [x] DONE: Applications (#9) wired.
- [x] DONE: Saved jobs (#10) wired.
- [ ] (PARKED, matching engine not started on purpose) Real match score and recommended jobs (P3, issue #12)
- [x] DONE: admin users and stats wired (#15).
- [x] DONE: notifications wired (#14).
- [ ] Server-side job search and paging (P4, issue #13)

_Front-end tasks that came from the back end are tracked in `docs/BACKEND_STATE.md`, not here._
