# Front-end state, as seen by the back end

**Snapshot:** 2026-09-30. Front end: `../frontjobfy/` (Next.js 15, React 19, TypeScript, Tailwind 4, axios). Much of it is **uncommitted** in that repo (about 26 modified and 20 new files), so treat the working tree as the source of truth, not git history.
Base URL is hardcoded to `http://localhost:9080` in `app/lib/api.ts`. Token, username, role and expiry are kept in `localStorage`, and a 401 clears them and logs the user out.

## 1. Endpoints the front end really calls (keep these stable)

| Call | Where used | Notes |
|---|---|---|
| `POST /auth/login`, `POST /auth/register` | `AuthContext` | Register sends `role` SEEKER or EMPLOYER plus first and last name. |
| `GET /jobs?available=true` | `useJobs`, `app/sitemap.ts` | Public. The whole list is loaded, and **search and filters run client-side**. |
| `GET /jobs` (no filter) | `useEmployerJobs` | Employer panel. Returns every employer's jobs. **Switch to `GET /jobs/mine`** (B2, done on the back end). |
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

`POST /jobs` payload today: `{jobTitle, jobDescription (sanitized HTML from a TipTap editor), rate, rateType, location, workMode, employmentType}`. `rateType` is one of `HOURLY | MONTHLY | YEARLY | CONTRACT_TOTAL`; `employmentType` gains `B2B`. **`jobRating` and `hourlyRate` are no longer sent** (see the request below). It does **not** send `requiredSkills`.
The front end's `Job` type (`app/types/job.ts`) has no `requiredSkills` field, although the API returns one. `SeekerResponse` in the front end types only `id/username/name/lastName/creationDate/isIndependent/cv`. The educations, certifications, experiences and skills lists the API now embeds are ignored.

## 2. Built on the back end but not used by the front end yet
- `/users/seekers/me/{educations,certifications,experiences,skills}` full CRUD. There is **no UI** for it, and the profile page's completeness widgets are cosmetic.
- `requiredSkills` on job posts (never sent, never displayed).
- `GET /admin/users` (`AdminUserController`). The admin dashboard shows `MOCK_USERS`.
- `JobPreferences` entity. It is not reachable from any endpoint.

## 3. Faked on the front end because the API has nothing (the real gaps)
| Feature | Front-end reality today | Back end needs |
|---|---|---|
| **Applications** | `applyForJob` only appends to in-memory React state. It is lost on refresh and the employer never sees it. | `Application` entity plus `POST /jobs/{id}/apply`, `GET /applications/me`, `GET /jobs/{id}/applications` (employer), a withdraw call, and a status pipeline. |
| **Application stage** (Recruiter Review / Interview / Offer / Rejected) | `app/lib/applicationStage.ts` derives a fake stage from `postId`. | A real stage field and an employer-side transition endpoint. |
| **Saved jobs** | In-memory React state. | `PUT/DELETE /jobs/{id}/save` plus `GET /jobs/saved`. |
| **Match %** | `jobEnrichment.ts` computes a fake 70–99 number from `postId` and `jobRating`. Shown on cards, details, hero, carousel and both dashboards. | The matching engine (see `../job-matching-engine.md`): `GET /jobs/recommended`, a per-job score with a breakdown (matched and missing skills), and employer-side `GET /jobs/{id}/candidates`. |
| **Job skills, benefits, gallery, salary band, posted-ago, company name and logo** | Seeded from `postId`. Skills come from a hardcoded pool. | Real `requiredSkills` on the client type (front-end work), `createdAt` (already returned), and a company name in the job response (today only `employerUsername`, which the UI capitalizes). Benefits and gallery are optional. |
| **Notifications** | `MOCK_NOTIFICATIONS`. The bell badge and the panel are fake. | A `Notification` entity, `GET /notifications`, mark-read, and events on application status change and new match. |
| **Notification preferences and accent colour** | `localStorage` only. | Optional `GET/PUT /users/me/preferences`. Low priority. |
| **Company dashboard** | `MOCK_PIPELINE_CANDIDATES`, `HIRING_CHART_DATA` and activity are fake. The job list and counts are real. | The applications and pipeline endpoints above, plus employer stats (views, applications per job, funnel counts). |
| **Admin dashboard metrics and user table** | Mock. Only the deletion-requests card and the CMS panel are real. | Use `GET /admin/users` (add paging and search), plus `GET /admin/stats` (users by role, jobs, applications). |
| **Employee role dashboard** | Pure mock ("Employee Preview"). The role does not exist in the API. | Product decision needed. See backlog P3. |
| **Seeker dashboard charts and tips** | Static (`EMPLOYEE_CHART_DATA`, interview tips). | Application-activity time series once applications exist. |
| **Job search** | Client-side over the full list. | Server-side `GET /jobs` with `q`, `location`, `workMode`, `employmentType`, `minRate`, `skills`, `page`, `size`, `sort`. The response shape must change for paging, so **coordinate** (see below). |
| **Location facet** | Filter over the free-text `location` string. | Optional normalization. |

## 4. Back-end behaviours the front end assumes
- `available=true` filters server-side. Newly created jobs are `available=true`.
- Description is rich text: the front end sanitizes with DOMPurify, but **the server must too** (B4). The docs claim 1000 chars, but the field is `@Lob`.
- Auth errors: login failure must be 401 with a readable `message`.
- The employer panel assumes it only sees its own jobs. It currently filters nothing.
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

- [ ] **Job compensation model (BLOCKING: `POST /jobs` fails validation until this ships).** The Post-a-Job form no longer sends `jobRating` (removed, meaningless) or `hourlyRate`. Please: (1) drop `jobRating` from `CreateJobRequest`, the entity and responses (or make it optional/ignored); (2) add `rate` (decimal, > 0, required) and `rateType` (enum `HOURLY | MONTHLY | YEARLY | CONTRACT_TOTAL`, required) to `CreateJobRequest`, `PUT /jobs/{id}` and the job response; (3) migrate existing rows: `rate = hourlyRate`, `rateType = HOURLY`; keep returning `hourlyRate` for a while (front end falls back to it when `rate` is absent); (4) add `B2B` to the `employmentType` enum; (5) validation message for a bad rate, e.g. `rate must be greater than 0`. Later, server-side search `minRate` should compare on an hourly equivalent (front end uses monthly/173.33 and yearly/2080; `CONTRACT_TOTAL` is not comparable). Screen waiting: Post a Job modal.
- [ ] Applications: apply, list my applications, withdraw, employer review pipeline (backlog P1, issue #9)
- [ ] Saved jobs (P2, issue #10)
- [ ] Real match score and recommended jobs (P3, issue #12)
- [ ] `GET /admin/users` with paging and search, plus `GET /admin/stats` (P7, issue #16)
- [ ] Notifications (P6, issue #14)
- [ ] Server-side job search and paging (P4, issue #13)

_Front-end tasks that came from the back end are tracked in `docs/BACKEND_STATE.md`, not here._
