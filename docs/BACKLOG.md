# Back-end backlog

Ordered by priority. **B** = bug or security, **P** = product feature, **T** = tech debt. Each item names the front-end impact. Confirm any item still applies before starting it, because this was written from a code read on 2026-09-30 and nothing was run.

## Bugs and security (do first)

**B1. ✅ CORS blocks PATCH.** `SecurityConfig.corsConfigurationSource` allows `GET, POST, PUT, DELETE, OPTIONS` only. The front end calls `PATCH /jobs/{id}/available` (open/close a job), so the browser preflight fails. Add `PATCH`. *Front-end impact: fixes the employer open/close toggle.*

**B2. ✅ Employers see everyone's jobs.** `GET /jobs` has no owner filter, and the employer panel calls it unfiltered. Add `GET /jobs/mine` (EMPLOYER only), or an `employer=me` param, and tell the front-end session to switch.

**B3. ✅ No ownership or role checks on job writes.** `PATCH /jobs/{id}/available` states "any authenticated user can update availability". `POST /jobs` only checks "authenticated", so a SEEKER token gets "Employer not found" (404) rather than a proper 403. Enforce `ROLE_EMPLOYER` for create, and ownership for update, in the service layer. Add tests.

**B4. ✅ Stored XSS through `jobDescription`.** The front end sends HTML from a rich-text editor and DOMPurify only protects one client. Sanitize server-side (for example the OWASP Java HTML Sanitizer, with an allow-list of p, br, ul, ol, li, strong, em, h2, h3, a[href]) on write. Decide and document a max length. `api-documentation.md` says 1000, but the column is `@Lob`.

**B5. ✅ Entity used as request body.** `POST /jobs` binds `@RequestBody JobPost` (a JPA entity, with no `@Valid`), so clients can set `postId`, `employer`, `available` and so on. Introduce `CreateJobRequest` (title, description, rating, rate, location, workMode, employmentType, requiredSkills: string[]) with Bean Validation. Keep the JSON field names exactly as today so the front end doesn't break. A `PUT /jobs/{id}` (edit) is the natural companion.

**B6. ✅ `jobRating` is employer-supplied.** *(Decided by the PO on 2026-09-30: removed from the API, see P11 / #28.)* The posting employer sets the "rating". Product question: is it really an employer rating or quality score? The UI exposes it as an input. Clarify before building on it (the fake match score uses it).

**B7. ✅ JWT secret and lifetime.** *(Done in sprint 1: `JWT_SECRET` env var, startup validation. Refresh tokens remain a follow-up spike.)* Secret is committed in `application.properties`. Move to `${JWT_SECRET}`, and fail startup if it's missing outside the `dev` profile. Consider refresh tokens. The front end just logs out on 401 or expiry.

## Product features the front end is waiting on (P1 is the big one)

**P11. ✅ Job compensation model (#28).** `rate` + `rateType` (`HOURLY | MONTHLY | YEARLY | CONTRACT_TOTAL`) replace `hourlyRate` and `jobRating`; `EmploymentType.B2B` added. Requested by the front end. Done in sprint 2. Follow-up (T3): drop the unused `job_rating` and `hourly_rate` columns with a real migration once no client reads `hourlyRate`.

**P1. Applications.** *(Split into stories #30 apply + list mine, #31 withdraw, #32 employer applicants, #33 employer stage changes. all four are built and in review in the applications pull request.)* Entity: seeker, job, status (`APPLIED, IN_REVIEW, INTERVIEW, OFFER, REJECTED, WITHDRAWN`), createdAt, updatedAt, optional cover note. Unique on (seeker, job). Endpoints: `POST /jobs/{id}/apply` (SEEKER), `GET /applications/me`, `DELETE /applications/{id}` (withdraw), `GET /jobs/{id}/applications` (owning EMPLOYER), `PUT /applications/{id}/status` (owning EMPLOYER). This replaces the fake stage in `applicationStage.ts`. Also unblocks the company pipeline and funnel widgets and the notification triggers.

**P2. Saved jobs.** `PUT /jobs/{id}/save`, `DELETE /jobs/{id}/save`, `GET /jobs/saved` (SEEKER). Small, and independent of P1.

**P3. Matching engine.** *(PARKED 2026-10-04: the front end is deliberately not touching it; moved to the icebox, issue #12.)* Design in `../job-matching-engine.md`. The data model is ready (`SeekerSkill`, `JobPost.requiredSkills`, location, workMode, employmentType, rate). Nothing is computed. Suggest a `MatchingService` with a pure scoring function, unit-tested heavily, and weights in config. Then `GET /jobs/recommended` and `GET /jobs/{id}/match` (a 0–100 score plus `matchedSkills[]` and `missingSkills[]`), and later `GET /jobs/{id}/candidates`. The front end has a slot on every job card. **Open decision:** how to use `JobPreferences`, which is currently unreachable. Expose `GET/PUT /users/seekers/me/preferences` (salary range, title, location, employment type) so the score has input beyond skills.

**P4. Server-side search and paging.** `GET /jobs?q=&location=&workMode=&employmentType=&minRate=&maxRate=&skills=&available=&page=&size=&sort=`. Today the front end downloads everything and filters in the browser. To avoid breaking callers (`useJobs`, `sitemap.ts`, the employer panel), add paging only when `page` is supplied, or add a new route `/jobs/search`. Coordinate the response envelope.

**P5. ✅ Company name on job responses (#11, done in sprint 2: `companyName`, `companyId`, public `GET /companies/{id}`).** `JobPostResponse` has `employerUsername` only, and the UI title-cases it as the company. Add `companyName` (nullable) and `companyId`. Additive, so safe. Also consider a public `GET /companies/{id}`.

**P6. Notifications.** `Notification` entity (user, type, title, body, read, createdAt) with `GET /notifications`, `POST /notifications/{id}/read` and `POST /notifications/read-all`. Emit on application status change, new application (employer), and new match (P3). The front-end types are in `app/lib/mockDashboardData.ts` (`type: interview | application | system | hiring | account`).

**P7. Admin stats and user management.** `GET /admin/users` exists and is unpaged. Add paging and search, plus `GET /admin/stats` (users by role, open jobs, applications). Optional: disable or delete user, and job moderation.

**P8. Employer stats.** *(Open question for the Product Owner on job "views", see #17.)* Per-job application counts, funnel counts by status, and applications over time (`GET /employers/me/stats`) for the company dashboard.

**P9. User preferences.** Optional `GET/PUT /users/me/preferences` (notification toggles) to replace the front end's `localStorage` copy. Low priority.

**P12. Company logo (#34, icebox).** Upload plus `logoUrl` on jobs and companies. The front end derives initials and a colour until it exists.

**P13. Benefits and gallery on jobs (#35, icebox, optional).** `benefits[]` and `images[]`; the front end keeps hardcoded pools meanwhile.

**P14. Responsibilities and requirements (#36, decision).** Keep the front end's sentence-splitting heuristic, or add structured fields.

**P15. Location normalization (#37, icebox).** Clean facet values and `GET /jobs/locations`.

**P16. Seeker application statistics (#38, sprint 4).** `GET /applications/me/stats` for the dashboard charts. Depends on P1.

**P17. Require location, workMode, employmentType on new jobs? (#39, decision).** Lets the front end drop its invented fallback values.

**P10. EMPLOYEE role.** The front end has an "Employee Preview" dashboard, all mock. There is no such role in the API. **Needs a product decision** (drop it, or define it) before any work. Don't build it speculatively.

## Tech debt and platform

**T1. Tests.** *(Sprint 1 progress: JaCoCo report added; security, job, auth, account-deletion and password tests done; overall instruction coverage 53% against the 85% target. Still uncovered: user/seeker profile services, admin users, CMS controllers, file storage, `common/validation`. Add tests with each new feature.)* Only `contextLoads` exists, against a 85% coverage target in `../Claude.md`. Start with security tests (public vs authed vs admin routes), `JobService`, `AuthService`, and account deletion, then add each new feature test-first.

**T2. Bean Validation everywhere.** Audit every request DTO for `@Valid` and constraints. Check that `MethodArgumentNotValidException` yields a readable 400 `message` in the envelope, since the front end prints it.

**T3. PostgreSQL + Flyway + Docker.** Currently H2 with `ddl-auto=update`. Add a `postgres` profile, Flyway migrations (baseline the current schema), a `Dockerfile` and a `docker-compose.yml`. Keep H2 as the default dev and test profile.

**T4. Dead code.** `job/model/Job.java` and the top-level `model/Region.java` appear unused. Confirm with a grep, then remove. Move `model/JobPost`, `Skill` and the enums into their feature slices.

**T5. Observability.** JSON structured logging, a correlation-ID filter, and health details. Audit-log security events (login failure, admin actions, deletion approvals).

**T6. API docs.** Bring `api-documentation.md` up to date (account, CMS, seeker profile, job fields, admin) or generate it from `/v3/api-docs`. Point the front-end session at the OpenAPI JSON.

**T7. Dependencies.** Spring Boot `4.0.0-M3` is a milestone. Move to GA when available and run a vulnerability check.

**T8. Cleanup.** Also consider a rate limit on `/auth/**` and password rules (front end currently enforces none beyond non-empty).
