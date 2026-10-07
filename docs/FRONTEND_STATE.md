# Front-end state, as seen by the back end

**Snapshot:** 2026-10-07 (everything in `BACKEND_STATE.md` is wired except the matching engine). Front end: `../frontjobfy/` (Next.js 15, React 19, TypeScript, Tailwind 4, axios). Much of it is **uncommitted** in that repo (about 26 modified and 20 new files), so treat the working tree as the source of truth, not git history.
Base URL is hardcoded to `http://localhost:9080` in `app/lib/api.ts`. Token, username, role and expiry are kept in `localStorage`, and a 401 clears them and logs the user out.

## 1. Endpoints the front end really calls (keep these stable)

| Call | Where used | Notes |
|---|---|---|
| `POST /auth/login`, `POST /auth/register` | `AuthContext` | Register sends `role` SEEKER or EMPLOYER plus first and last name. |
| `GET /jobs?available=true` | `useJobs` (home feed), `app/sitemap.ts` | Public. Plain array, no client-side search any more. |
| `GET /jobs/search` (`q`, `locationId`, `workMode`, `employmentType`, `minRate`, `sort`, `page`, `size=20`), `GET /jobs/locations` | `useJobSearch`, `SearchScreen` | Debounced 300 ms, filters kept in the URL (`replaceState`), "Load more" paging, location facet from `/jobs/locations`. Only the experience-level filter is still a client-side keyword heuristic over the loaded page. |
| `GET /jobs/mine` | `useEmployerJobs` | Employer panel (own jobs, including closed). Switched 2026-10-05. A 403 shows the server message through the axios interceptor. |
| `GET /jobs/saved`, `PUT/DELETE /jobs/{id}/save` | `JobContext` | Seekers only. List loaded when a SEEKER is signed in; save/unsave are optimistic and roll back on error (`saveError`). Closed saved jobs come back with `available: false`. |
| `POST /jobs/{id}/apply`, `GET /applications/me`, `GET /applications/me/stats`, `DELETE /applications/{id}` | `JobContext`, seeker dashboard, tracker | Seekers only. Apply, then reload list and stats. Withdraw shown for every status except REJECTED and WITHDRAWN. Server messages (422/403) are shown in a banner. |
| `GET /jobs/{id}/applications`, `PUT /applications/{id}/status`, `GET /employers/me/stats` | `useEmployerPipeline`, company dashboard | One applicants call per owned job. Stage buttons come from the allowed-moves table, so only valid next stages are offered. |
| `GET /notifications`, `/notifications/unread-count`, `POST /notifications/{id}/read`, `/read-all` | `useNotifications`, TopBar, NotificationPanel | Badge polled every 45 s; list loaded when the panel opens; click marks read. Text rendered as text. |
| `GET /admin/users` (paged, `q`, `role`, `sort=newest`), `GET /admin/stats` | `AdminDashboard` | Search debounced 300 ms. |
| `GET /jobs/{id}` | `app/jobs/[id]/page.tsx` (SSR, SEO) | Public. Also feeds the JobPosting JSON-LD. |
| `POST /jobs`, `POST /jobs/{id}/images` | `PostJobModal` | Sends the fields listed below; pictures (max 6, 2 MB, PNG/JPEG/WebP) go as separate multipart calls after the job exists. A failed picture keeps the job and shows the error (the modal then only offers Close, so a retry cannot duplicate the job). |
| `PATCH /jobs/{id}/available` | `useEmployerJobs` | Fixed in sprint 1 (CORS now allows PATCH). Owner only. |
| `GET/PUT /users/seekers/me`, `GET/PUT /users/employers/me` | `UserProfile` | PUT body is `{name, lastName}`. |
| `POST /users/companies`, `PUT /users/companies/{id}` | `UserProfile` | `{name}`. Owner only (controls hidden for `MANAGER`). |
| `POST/DELETE /users/companies/{id}/logo` | `UserProfile` | Multipart `file`, 1 MB. Owner only. The logo shows on cards, details, carousel and the seeker dashboard via `LogoOrInitials`. |
| `GET /users/me/preferences`, `PUT /users/me/preferences` | `usePreferences`, `PreferencesLoader` (in `ClientProviders`), `SettingsPage` | Loaded after sign-in; accent applied app-wide. Each toggle sends only itself; `ACCOUNT` is shown on and disabled. One-time migration of the old `localStorage` values (key `jobify:prefs-migrated`). |
| `GET /users/employers/me` (`companyRole`), `GET /invitations/me`, `POST /invitations/{id}/accept\|decline`, `GET/POST /companies/{id}/invitations`, `DELETE …/invitations/{id}`, `GET /companies/{id}/members`, `DELETE …/members/{username}`, `PUT …/members/{username}/role`, `DELETE /companies/me/membership`, `GET /companies/{id}/activity` | `useCompanyTeam`, `TeamPage`, `InvitationsBanner`, `CompanyActivityFeed` | Employer dashboard: Team page (members, invite, pending invites with cancel, remove, role change, leave), invitations banner on the dashboard, activity feed on the overview. Owner-only controls are hidden from managers. Stats still use `/employers/me/stats` (same numbers as `/companies/{id}/stats`). |
| `GET/POST/PUT/DELETE /users/seekers/me/{educations,experiences,certifications,skills}` | `useProfileList`, `ProfileListSection`, `SeekerProfileSections` | Profile page for seekers. The completion bar is computed from CV, experience, education, skills and certifications. |
| `POST/GET/DELETE /users/seekers/me/resume` | `useResume` | Multipart upload, blob download. Reads `SeekerResponse.cv`. |
| `PUT /account/password` | `useAccount` | `{currentPassword, newPassword}`. |
| `GET/POST/DELETE /account/deletion-request` | `useAccount` | The GET must return `null` (200) when there is no request. |
| `GET /admin/deletion-requests`, `POST …/{id}/approve`, `POST …/{id}/reject {note}` | `useAdminDeletionRequests` | |
| `GET /content` | `ContentContext`, `app/lib/seo.ts` | Public. Returns a flat `{key: value}` map. |
| `GET/PUT /admin/content` | `useAdminContent` | GET returns `ContentEntry[]`. PUT takes `{values: {key: value}}` and returns `ContentEntry[]`. |

`POST /jobs` payload today: `{jobTitle, jobDescription (sanitized HTML from a TipTap editor), rate, rateType, location, workMode, employmentType, requiredSkills, responsibilities, requirements, benefits}` (the three new ones are one-item-per-line textareas, limits 20x300, 20x300, 15x80 checked before sending). `rateType` is one of `HOURLY | MONTHLY | YEARLY | CONTRACT_TOTAL`; `employmentType` includes `B2B`. `jobRating` and `hourlyRate` are no longer sent or read for display (`app/lib/rate.ts` reads `rate`/`rateType`, the `hourlyRate` fallback was removed 2026-10-06). `requiredSkills: string[]` (names) comes from a tag input in `PostJobModal`. 403 on job writes shows the server's message in the modal or the panel error.
`Job` and `CreateJobRequest` now carry `requiredSkills`; `enrichJob` shows the real list and falls back to `SKILL_POOL` only when it is empty. Posted-ago is computed from `createdAt`. `enrichJob` uses the API's `responsibilities` / `requirements` / `benefits` / `images` / `logoUrl` when present and falls back to the sentence heuristic and the pools only for jobs where they are empty. `SeekerResponse` types the embedded lists, but the profile page loads each list from its own route.

## 2. Built on the back end but not used by the front end yet
- `JobPreferences` entity. It is not reachable from any endpoint.
- `GET /companies/{id}/stats` (the front end uses `/employers/me/stats`, same numbers), `GET /companies/{id}` (company name comes with each job).
- `applications.funnel`, `perJob[].funnel` (not wanted for now), per-application history.

## 3. Faked or hardcoded on the front end (the real gaps)

**Scope note:** the job-matching engine (match %, recommended jobs, candidates) is deliberately **not being touched yet**. Those rows stay as they are and are parked.

### 3a. Waiting on the back end
| Feature | Front-end reality today | Back end needs |
|---|---|---|
| **Match %** (PARKED: do not touch until the matching engine is scheduled) | `jobEnrichment.ts` computes a fake 70–99 number from `postId`. Shown on cards, details, hero, carousel and both dashboards. | The matching engine (`GET /jobs/recommended`, per-job score, `GET /jobs/{id}/candidates`). |
| **Job views** on the company dashboard | Not shown (not tracked; issue #16). | Possibly dropped. |
| **Interviews** pages (seeker and company), companies list on the admin dashboard | Static / "coming soon". | No endpoints. |

Done since the last snapshot (no longer faked): company logo, benefits and gallery (pools stay only as fallback for jobs with none), responsibilities and requirements, notification preferences and accent colour, server-side job search, location facet, company teams (the old "Employee Preview" mock, `EmployeeDashboard` and its mock data are deleted; the sidebar "Preview Role" switcher is removed too, so the dashboard always matches the authenticated role and a seeker can no longer open the Company or Admin screens).

### 3b. Front-end work only (the API already provides it)
| Item | Reality today | To do |
|---|---|---|
| **Fallbacks for missing fields** | `location`, `workMode` and `employmentType` fall back to random or default values when null (`FALLBACK_LOCATIONS`, random remote status, "Full-time"). The back end now requires all three on new jobs but old rows may still be null. | Drop the fakes once old data is gone. |
| **Edit job form** | There is none: only Post a Job. `PUT /jobs/{id}` is not called. | Optional. |
| **Password rules, 429** | Done: rules shown on register and change-password, 429 message shown and never logs out. Server errors (5xx) show the `X-Request-Id` as a reference in the message. | |

### 3c. Hardcoded UI data (`app/lib/mockDashboardData.ts`)
Only the seeker `SEEKER_INTERVIEW_TIPS` (no endpoint), the hardcoded Companies cards on the admin dashboard (no list endpoint), the cosmetic resume-score widget, and two shared types (`ActivityItem`, `ChartPoint`). `applicationStage.ts` and the Employee mock data are deleted.

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

### Notes for the back end (2026-10-07)
- All open items in `BACKEND_STATE.md` are done on the front end except the matching engine. Verified against the dev back end: response shapes for `/jobs/search`, `/jobs/locations`, `/users/employers/me`, `/invitations/me`, `/users/me/preferences`, `/companies/{id}/members` and `/activity` match the types. Not exercised end to end in a browser: invitations between two accounts, image and logo uploads.
- Experience level in search is still a client-side keyword heuristic (no API field).

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
- [x] DONE: server-side job search and paging (#13) wired.
- [x] DONE: location facet (#37), responsibilities/requirements (#36), logo, benefits and gallery (#34, #35), preferences (#25), company teams (#26, #71 to #74) wired.

_Front-end tasks that came from the back end are tracked in `docs/BACKEND_STATE.md`, not here._
