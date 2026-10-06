# Front-end state, as seen by the back end

**Snapshot:** 2026-10-06. Front end: `../frontjobfy/` (Next.js 15, React 19, TypeScript, Tailwind 4, axios). Much of it is **uncommitted** in that repo (about 26 modified and 20 new files), so treat the working tree as the source of truth, not git history.
Base URL is hardcoded to `http://localhost:9080` in `app/lib/api.ts`. Token, username, role and expiry are kept in `localStorage`, and a 401 clears them and logs the user out.

## 1. Endpoints the front end really calls (keep these stable)

| Call | Where used | Notes |
|---|---|---|
| `POST /auth/login`, `POST /auth/register` | `AuthContext` | Register sends `role` SEEKER or EMPLOYER plus first and last name. |
| `GET /jobs?available=true` | `useJobs`, `app/sitemap.ts` | Public. The whole list is loaded, and **search and filters run client-side**. |
| `GET /jobs/mine` | `useEmployerJobs` | Employer panel (own jobs, including closed). Switched 2026-10-05. A 403 shows the server message through the axios interceptor. |
| `GET /jobs/saved`, `PUT/DELETE /jobs/{id}/save` | `JobContext` | Seekers only. List loaded when a SEEKER is signed in; save/unsave are optimistic and roll back on error (`saveError`). Closed saved jobs come back with `available: false`. |
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

`POST /jobs` payload today: `{jobTitle, jobDescription (sanitized HTML from a TipTap editor), rate, rateType, location, workMode, employmentType}`. `rateType` is one of `HOURLY | MONTHLY | YEARLY | CONTRACT_TOTAL`; `employmentType` includes `B2B`. `jobRating` and `hourlyRate` are no longer sent or read for display (`app/lib/rate.ts` reads `rate`/`rateType`, falling back to `hourlyRate` only if `rate` is absent). It also sends `requiredSkills: string[]` (names) from a tag input in `PostJobModal`. 403 on job writes shows the server's message in the modal or the panel error.
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
| **Applications** | `applyForJob` only appends to in-memory React state. It is lost on refresh and the employer never sees it. | `Application` entity plus `POST /jobs/{id}/apply`, `GET /applications/me`, `GET /jobs/{id}/applications` (employer), a withdraw call, and a status pipeline. |
| **Application stage** (Recruiter Review / Interview / Offer / Rejected) | `app/lib/applicationStage.ts` derives a fake stage from `postId`. | A real stage field and an employer-side transition endpoint. |
| **Saved jobs** | DONE 2026-10-06: wired to the API. | |
| **Match %** (PARKED: do not touch until the matching engine is scheduled) | `jobEnrichment.ts` computes a fake 70–99 number from `postId` and `jobRating`. Shown on cards, details, hero, carousel and both dashboards. | The matching engine (see `../job-matching-engine.md`): `GET /jobs/recommended`, a per-job score with a breakdown (matched and missing skills), and employer-side `GET /jobs/{id}/candidates`. |
| **Company logo** | `companyName` now comes from the API (`companyName`, fallback "Jobify Partner" when null); initials and gradient are derived from it. Logo does not exist. | Logo URL (#34, icebox). |
| **Benefits and gallery** | Picked from hardcoded pools (`BENEFIT_POOL`, `GALLERY_POOL`), seeded by `postId`. | Optional: `benefits[]` and `images[]` on the job. Low priority. |
| **Responsibilities / requirements** | Split from the description's sentences (first 5 / next 4). Heuristic, not data. | Optional structured fields, or accept the heuristic. |
| **Notifications** | `MOCK_NOTIFICATIONS`. The bell badge and the panel are fake. | A `Notification` entity, `GET /notifications`, mark-read, and events on application status change and new match. |
| **Notification preferences and accent colour** | `localStorage` only. | Optional `GET/PUT /users/me/preferences`. Low priority. |
| **Company dashboard** | `MOCK_PIPELINE_CANDIDATES`, `HIRING_CHART_DATA` and activity are fake (applications endpoints are in an unmerged back-end PR, not wired yet). The job list and counts are real. | The applications and pipeline endpoints above, plus employer stats (views, applications per job, funnel counts). |
| **Admin dashboard metrics and user table** | Mock. Only the deletion-requests card and the CMS panel are real. | Use `GET /admin/users` (add paging and search), plus `GET /admin/stats` (users by role, jobs, applications). |
| **Employee role dashboard** | Pure mock ("Employee Preview"). The role does not exist in the API. | Product decision needed. See backlog P3. |
| **Seeker dashboard charts and tips** | Static (`EMPLOYEE_CHART_DATA`, interview tips). | Application-activity time series once applications exist. |
| **Job search** | Client-side over the full list. | Server-side `GET /jobs` with `q`, `location`, `workMode`, `employmentType`, `minRate`, `skills`, `page`, `size`, `sort`. The response shape must change for paging, so **coordinate** (see below). |
| **Location facet** | Filter over the free-text `location` string. | Optional normalization. |

### 3b. Front-end work only (the API already provides it)
| Item | Reality today | To do |
|---|---|---|
| **Job skills** | DONE 2026-10-05: real `requiredSkills`; pool fallback only for old jobs without any. | |
| **Posted-ago** | DONE 2026-10-05: computed from `createdAt` (bucket fallback only when null). | |
| **Employer panel list** | DONE 2026-10-05: uses `GET /jobs/mine`. | |
| **Fallbacks for missing fields** | `location`, `workMode` and `employmentType` fall back to random or default values when null (`FALLBACK_LOCATIONS`, random remote status, "Full-time"). | Drop the fakes once all jobs have real values. |
| **Seeker profile editing** | `SeekerResponse` ignores educations, certifications, experiences, skills. Completeness widgets are cosmetic. | Extend the type and build the CRUD UI against `/users/seekers/me/*`. |
| **Admin user table** | `MOCK_USERS`. | Wire to `GET /admin/users` (it exists). Paging and search still need the back end. |

### 3c. Hardcoded UI data (all in `app/lib/mockDashboardData.ts`)
`MOCK_NOTIFICATIONS`, `MOCK_PIPELINE_CANDIDATES`, `COMPANY_ACTIVITY`, `HIRING_CHART_DATA`, `ADMIN_METRICS`, `ADMIN_ACTIVITY`, `ADMIN_CHART_DATA`, `MOCK_USERS`, `EMPLOYEE_CHART_DATA`, `EMPLOYEE_ACTIVITY`, plus the trend badges (`trend={12}` etc.) on the metric cards and the interview tips. Also `applicationStage.ts` (fake stage from `postId`) and the in-memory `applyForJob` in `JobContext`.

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

### Requests to the back end (append here)
Format: `- [ ] <what you need> — route, request/response shape, errors — screen waiting on it`
The back-end session turns each open request into a GitHub issue (or links an existing one) and answers in `docs/BACKEND_STATE.md`.

- [x] **DONE (shipped by back end 2026-09-30, consumed by the front end): Job compensation model.** Original request: The Post-a-Job form no longer sends `jobRating` (removed, meaningless) or `hourlyRate`. Please: (1) drop `jobRating` from `CreateJobRequest`, the entity and responses (or make it optional/ignored); (2) add `rate` (decimal, > 0, required) and `rateType` (enum `HOURLY | MONTHLY | YEARLY | CONTRACT_TOTAL`, required) to `CreateJobRequest`, `PUT /jobs/{id}` and the job response; (3) migrate existing rows: `rate = hourlyRate`, `rateType = HOURLY`; keep returning `hourlyRate` for a while (front end falls back to it when `rate` is absent); (4) add `B2B` to the `employmentType` enum; (5) validation message for a bad rate, e.g. `rate must be greater than 0`. Later, server-side search `minRate` should compare on an hourly equivalent (front end uses monthly/173.33 and yearly/2080; `CONTRACT_TOTAL` is not comparable). Screen waiting: Post a Job modal.
- [ ] Applications: apply, list my applications, withdraw, employer review pipeline (backlog P1, issue #9)
- [ ] Saved jobs (P2, issue #10)
- [ ] (PARKED, matching engine not started on purpose) Real match score and recommended jobs (P3, issue #12)
- [ ] `GET /admin/users` with paging and search, plus `GET /admin/stats` (P7, issue #16)
- [ ] Notifications (P6, issue #14)
- [ ] Server-side job search and paging (P4, issue #13)

_Front-end tasks that came from the back end are tracked in `docs/BACKEND_STATE.md`, not here._
