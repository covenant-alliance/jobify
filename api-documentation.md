# Jobify API Documentation

**Base URL:** `http://localhost:9080`  
**Swagger UI:** `http://localhost:9080/swagger-ui/index.html`  
**OpenAPI spec (JSON):** `http://localhost:9080/v3/api-docs`

> Swagger UI, the OpenAPI JSON and the H2 console exist only in the default `dev` profile. The `postgres` profile (deployments) turns them off unless `APP_DOCS_ENABLED=true`, and has no demo accounts (see `docs/DEPLOYMENT.md`). The routes themselves are identical in both.

**Conventions used everywhere**

- **Request id:** every response carries an `X-Request-Id` header (readable from browser scripts). Send your own `X-Request-Id` (up to 64 characters of letters, digits, `.`, `-`, `_`) to choose it; anything else is replaced. Quote it when reporting a problem: it finds every log line of that request. A `429` also exposes `Retry-After`.
- **Health:** `GET /actuator/health` is public and returns only the status; an ADMIN token also sees the components (database, disk). No other actuator endpoint is exposed.

- Success responses are the bare object or array, not wrapped. Errors use the envelope below.
- Enums are uppercase strings. Timestamps are ISO-8601 `LocalDateTime` **without** a zone (e.g. `2026-10-05T14:30:00`). Dates are `yyyy-MM-dd`.
- Profile, company, application and education/skill ids are UUID strings; a job's `postId` is an integer. `/me` routes read the username from the JWT.
- Every request body is validated. Failures are `400` and the `message` lists each problem as `field problem`, for example `jobTitle is required; rate must be greater than 0`.
- Text limits (characters): names 100, username 50, password 72, job title 150, most other profile text 255, experience description 5,000, job description 20,000 (HTML), cover note 2,000, deletion reason and note 255, CMS value 4,000.

---

## Authentication

Jobify uses **JWT Bearer tokens**.

1. Call `POST /auth/register` or `POST /auth/login` — both return a `TokenResponse`.
2. Store `token` (e.g. in `localStorage`).
3. Include the token on every protected request:

```
Authorization: Bearer <token>
```

Tokens expire after `expiresIn` milliseconds (default 24 h = `86400000`).

---

## Error format

All error responses share the same envelope:

```json
{
  "success": false,
  "message": "Human-readable error description",
  "data": null,
  "status": 404
}
```

| HTTP status | Trigger |
|---|---|
| `400` | Validation failure, malformed JSON body or illegal argument |
| `401` | Missing / expired / invalid JWT |
| `403` | Forbidden: wrong role or not the owner of the resource |
| `404` | Resource not found, or no such route |
| `405` | The route exists but not for that HTTP method |
| `429` | Rate limited or temporarily locked out; see the `Retry-After` header |
| `409` | The change conflicts with existing data (rare: a concurrent duplicate) |
| `415` | Wrong content type (send `application/json`) |
| `422` | Business rule violation (e.g. duplicate company, closed job, disallowed status move) |
| `500` | Unexpected server error |

`401` makes the front end log the user out, so it is only used for a missing, expired or invalid token and for wrong login credentials.

---

## Auth endpoints `/auth/**`

> **No Authorization header required** for any endpoint in this group.

---

### `POST /auth/register`

Creates an account and immediately returns a token. A domain profile (Seeker **or** Employer) is created in the same transaction.

**Request body**

```json
{
  "username": "jane_doe",
  "password": "s3cur3P@ss",
  "role": "SEEKER",
  "firstName": "Jane",
  "lastName": "Doe"
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `username` | string | yes | Must be unique across the platform |
| `password` | string | yes | 8 to 72 characters, hashed server-side (BCrypt). Must also pass the **password policy** below |
| `role` | `"SEEKER"` \| `"EMPLOYER"` | yes | Determines which profile type is created |
| `firstName` | string | yes | |
| `lastName` | string | yes | |

**Response `201 Created`**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "type": "Bearer",
  "expiresIn": 86400000,
  "username": "jane_doe",
  "role": "SEEKER"
}
```

**Error responses**

| Status | When |
|---|---|
| `400` | Any field blank, `role` null, or username already taken |

---

### `POST /auth/login`

Authenticates an existing account.

**Request body**

```json
{
  "username": "jane_doe",
  "password": "s3cur3P@ss"
}
```

**Response `200 OK`** — same `TokenResponse` shape as register.

**Error responses**

| Status | When |
|---|---|
| `400` | A field is missing or blank (`username is required`) |
| `401` | Username not found or wrong password: `Invalid username or password` (the same message either way) |
| `429` | Too many failed attempts for this username, or too many requests from this address, see below |

---

### Password policy

Applies to `POST /auth/register` and `PUT /account/password`. Failures are `400` with one of these messages:

| Rule | Message |
|---|---|
| 8 to 72 characters | `password must be 8 to 72 characters` (`newPassword ...` when changing) |
| Not the same as the username (any case) | `Password must not be the same as your username.` |
| Not a very common password (for example `password123`, `12345678`) | `That password is too common. Please choose a less predictable one.` |

When changing a password, the new one must also differ from the current one (`422` `The new password must be different from the current one.`). Existing accounts keep their passwords; the policy only applies when a password is set.

### Brute-force protection (`429`)

Two limits protect `POST /auth/login` and `POST /auth/register`. Both answer `429 Too Many Requests` in the usual error envelope with a `Retry-After` header (seconds).

| Limit | Default | Message |
|---|---|---|
| Requests per client address, per route | 30 per minute | `Too many requests. Please wait 1 minute and try again.` |
| Failed logins per username | 5 within 10 minutes locks that username until the oldest failure expires | `Too many failed login attempts. Try again in 10 minutes.` |

- While a username is locked even the correct password is refused. A successful login resets the count, and other usernames are unaffected.
- Unknown usernames count and lock exactly like real ones, so the response never reveals whether an account exists.
- Settings: `app.security.auth-rate-limit.*`, `app.security.login-lock.*`. The client address is the connection address; set `app.security.trust-forwarded-for=true` only behind a proxy you control that overwrites `X-Forwarded-For`, otherwise clients could spoof it.
- Counts are held in memory per instance and reset on restart.

---

## Jobs endpoints `/jobs/**`

> `GET /jobs` and `GET /jobs/{id}` are **public, no Authorization header required.**
> `GET /jobs/mine`, `POST /jobs`, `PUT /jobs/{id}` and `PATCH /jobs/{id}/available` require `Authorization: Bearer <token>`.
> Writes are role- and ownership-checked in the service layer: only an `EMPLOYER` can create, and only the employer who created a post can edit or open/close it.

---

### `GET /jobs`

Returns job postings. By default returns **all** posts regardless of status, from every employer. Employer panels should use `GET /jobs/mine` instead.

**Query parameters**

| Parameter | Type | Required | Notes |
|---|---|---|---|
| `available` | boolean | no | `true` → open positions only; `false` → closed/filled only; omit → all |

**Recommended usage for seekers:** `GET /jobs?available=true`

**Response `200 OK`** — array of `JobPostResponse` (see *Data models reference*).

| Field | Type | Notes |
|---|---|---|
| `postId` | integer | Auto-generated primary key |
| `jobTitle` | string | Up to 150 characters |
| `jobDescription` | string | Sanitized rich-text HTML, see *Description sanitizing* below |
| `rate` | double | Pay amount, in the unit given by `rateType` |
| `rateType` | string | `HOURLY`, `MONTHLY`, `YEARLY` or `CONTRACT_TOTAL` (a fixed total for the engagement) |
| `hourlyRate` | double | **Deprecated.** `rate` as an hourly amount (monthly / 173.33, yearly / 2080, rounded to cents); `0` for `CONTRACT_TOTAL`. Use `rate` and `rateType`. |
| `employerUsername` | string \| null | Username of the posting employer; `null` if unassigned |
| `companyName` | string \| null | Name of the employer's company; `null` if the employer has none |
| `companyId` | string \| null | Company id (UUID), for `GET /companies/{id}` |
| `available` | boolean | `true` = position open; `false` = closed / position filled |
| `location` | string \| null | Free text |
| `workMode` | string \| null | `ONSITE`, `REMOTE`, `HYBRID` |
| `employmentType` | string \| null | `FULL_TIME`, `PART_TIME`, `CONTRACT`, `TEMPORARY`, `INTERNSHIP`, `FREELANCE`, `B2B` |
| `createdAt` | ISO-8601 datetime \| null | No zone |
| `requiredSkills` | string[] | Skill names |

---

### `GET /jobs/mine`

**EMPLOYER only.** Every post owned by the caller, open and closed. Response `200`: array of `JobPostResponse`.

| Status | When |
|---|---|
| `401` | Missing or invalid JWT token |
| `403` | Caller is not an employer |

---

### `GET /jobs/{id}`

Public. Response `200`: one `JobPostResponse`. `404` if it does not exist.

---

### `GET /companies/{id}`

Public, no token. Response `200`: `{ "id": "...", "name": "TechCorp Ltd" }`. `404` if the company does not exist.

---

### `POST /jobs`

**EMPLOYER only.** Creates a job post owned by the caller. New posts are always open (`available: true`). The body is a dedicated request object, so fields such as `postId`, `employer` and `available` are ignored.

**Request body** (`CreateJobRequest`)

```json
{
  "jobTitle": "Full-Stack Engineer",
  "jobDescription": "<p>Looking for a full-stack engineer with React and Spring Boot experience...</p>",
  "rate": 65.00,
  "rateType": "HOURLY",
  "location": "Berlin, DE",
  "workMode": "HYBRID",
  "employmentType": "FULL_TIME",
  "requiredSkills": ["Java", "Spring Boot"]
}
```

| Field | Rules |
|---|---|
| `jobTitle` | required, not blank, max 150 characters |
| `jobDescription` | required, not blank, max 20,000 characters of submitted HTML (before sanitizing) |
| `rate` | required, greater than 0 (`rate must be greater than 0`), max 1,000,000,000 |
| `rateType` | required: `HOURLY`, `MONTHLY`, `YEARLY` or `CONTRACT_TOTAL` |
| `location` | optional, max 255 characters |
| `workMode`, `employmentType` | optional enum strings (`employmentType` includes `B2B`) |
| `jobRating`, `hourlyRate` | **removed from the request.** Old clients that still send them are not rejected; the values are ignored. |
| `requiredSkills` | optional array of skill **names** (max 30, each not blank, max 100). Unknown names are added to the shared skill catalog. This replaces the old array-of-objects form. |

**Response `201 Created`** — the saved `JobPostResponse`.

| Status | When |
|---|---|
| `400` | Validation failed. `message` lists the problems, for example `jobTitle is required; rate must be greater than 0` |
| `401` | Missing or invalid JWT token |
| `403` | Caller is not an employer: `Only employers can post jobs.` |

---

### `PUT /jobs/{id}`

Replaces the editable fields (same body and rules as `POST /jobs`). The open/closed flag is not touched. Only the employer who created the post may do this.

**Response `200 OK`** — the updated `JobPostResponse`. Errors: `400` validation, `401`, `403` (`You can only change job postings that you created.`), `404`.

---

### `PATCH /jobs/{id}/available`

Opens or closes an existing job posting without modifying any other field. Only the employer who created the post may do this.

**Request body**

```json
{ "available": false }
```

**Response `200 OK`** — the full updated `JobPostResponse`.

| Status | When |
|---|---|
| `401` | Missing or invalid JWT token |
| `403` | Caller does not own the post |
| `404` | No job post found with the given ID |

The browser preflight for `PATCH` is allowed (CORS methods: GET, POST, PUT, PATCH, DELETE, OPTIONS).

---

### Description sanitizing

`jobDescription` is cleaned on every create and edit, so it is safe to render as HTML. Allowed elements: `p, br, ul, ol, li, strong, em, h2, h3, a`. Only `a[href]` keeps an attribute, restricted to `http`, `https` and `mailto` links, and external links get `rel="nofollow"`. Everything else (scripts, styles, iframes, images, event handlers, `javascript:` URLs) is removed. Non-ASCII characters outside the basic plane, such as emoji, come back as numeric entities (`&#x1f680;`), which browsers render normally.

---

## Saved jobs endpoints

> Seekers only; all require `Authorization: Bearer <token>`. Anyone else gets `403` (`Only job seekers can save jobs.`).

### `PUT /jobs/{id}/save`

Bookmarks a job. **Idempotent**: saving a saved job changes nothing. Response `204 No Content`. Closed jobs can be saved. `404` if the job does not exist.

### `DELETE /jobs/{id}/save`

Removes the bookmark. **Idempotent**: a job that is not saved is fine. Response `204`. `404` if the job does not exist.

### `GET /jobs/saved`

The caller's saved jobs, most recently saved first, as `JobPostResponse[]` (the same shape as `GET /jobs`). A job that has since closed stays in the list with `available: false`, so the UI can grey it out. Response `200`, empty array when nothing is saved.

Deleting an account (admin-approved) also deletes its saved jobs, and bookmarks of an employer's jobs.

---

## Employer statistics

### `GET /employers/me/stats`

Employer only, own jobs only (a seeker or admin gets `403` `Only employers have job statistics.`). For the company dashboard.

```json
{
  "jobs": { "total": 3, "open": 2, "closed": 1 },
  "applications": {
    "total": 4, "active": 2,
    "byStatus": { "APPLIED": 1, "IN_REVIEW": 0, "INTERVIEW": 1, "OFFER": 0, "REJECTED": 1, "WITHDRAWN": 1 },
    "weekly": [ { "weekStart": "2026-07-20", "count": 0 }, "... 12 entries ...", { "weekStart": "2026-10-05", "count": 4 } ]
  },
  "perJob": [
    { "postId": 12, "jobTitle": "Backend Engineer", "available": true, "createdAt": "2026-10-01T09:00:00",
      "total": 3, "active": 2, "byStatus": { "APPLIED": 1, "...": 0 } }
  ]
}
```

| Field | Notes |
|---|---|
| `jobs` | Every job the employer posted: total, open (`available`) and closed |
| `applications.byStatus` | Where applications are **now**, all six statuses, zero-filled. It is a snapshot, not a history: an application that reached `INTERVIEW` and was then `REJECTED` counts only as `REJECTED`. A true "reached this stage" funnel would need a status history, which is not stored yet |
| `applications.active` | `APPLIED` + `IN_REVIEW` + `INTERVIEW` + `OFFER`. Rejected and withdrawn are not active |
| `applications.weekly` | Applications **received** in each of the last 12 weeks, oldest first, zero-filled, the last entry being the current week. Weeks start on Monday |
| `perJob` | One entry per job, **including jobs with no applicants**, newest job first (highest `postId`), with that job's `total`, `active` and `byStatus` |

Job **views** are not tracked, so they are not included (open question in issue #16).

---

## Notifications endpoints `/notifications/**`

> Any signed-in user; each user only ever sees their own. All require `Authorization: Bearer <token>`. There is no push channel yet, so poll `GET /notifications/unread-count` (every 30 to 60 seconds is plenty) for the bell badge.

```ts
interface NotificationResponse {
  id: string;                     // UUID
  type: "INTERVIEW" | "APPLICATION" | "SYSTEM" | "HIRING" | "ACCOUNT";
  title: string;                  // plain text
  body: string;                   // plain text, show it as text, never as HTML
  read: boolean;
  createdAt: string;              // ISO-8601, no zone
  jobId: number | null;           // the job it is about, for a link
  applicationId: string | null;   // the application it is about, for a link
}
```

| Route | What it does |
|---|---|
| `GET /notifications` | Newest first. Query: `unreadOnly` (default `false`), `limit` 1 to 100 (default 50, otherwise `400` `limit must be between 1 and 100.`). Read ones are kept. |
| `GET /notifications/unread-count` | `{ "count": 3 }` |
| `POST /notifications/{id}/read` | Marks one as read. Idempotent. Returns the notification. `404` if it does not exist **or is not yours** (so ids cannot be probed). |
| `POST /notifications/read-all` | Marks all as read. Returns `{ "updated": 3 }`, the number that were unread. |

**When they are created**

| Event | Recipient | `type` | Title |
|---|---|---|---|
| A seeker applies (or applies again after withdrawing) | the employer who owns the job | `APPLICATION` | `New application` |
| A seeker withdraws | the employer | `APPLICATION` | `Application withdrawn` |
| Employer moves an application to `IN_REVIEW` | the seeker | `APPLICATION` | `Your application is being reviewed` |
| ... to `INTERVIEW` | the seeker | `INTERVIEW` | `You have reached the interview stage` |
| ... to `OFFER` | the seeker | `HIRING` | `You have an offer` |
| ... to `REJECTED` | the seeker | `APPLICATION` | `Application update` |
| The user changes their password | that user | `ACCOUNT` | `Your password was changed` |
| An admin declines their deletion request | that user | `ACCOUNT` | `Account deletion request declined` (the admin's note is included) |

A refused change (for example a disallowed stage move) creates nothing. `SYSTEM` is reserved for announcements and is not sent yet. "New match" notifications arrive with the matching engine, which is parked. Deleting an account deletes its notifications.

---

## Applications endpoints

> All require `Authorization: Bearer <token>`. Apply, list-mine and withdraw are for seekers; list-applicants and change-status are for the employer who owns the job. Anyone else gets `403`.

### `POST /jobs/{id}/apply`

A seeker applies to an open job. The application starts as `APPLIED`.

**Request body** (optional): `{ "coverNote": "..." }` - max 2,000 characters; a blank note is stored as none.

**Response `201 Created`** - `ApplicationResponse` (see below).

| Status | When |
|---|---|
| `400` | Cover note longer than 2,000 characters: `coverNote must be at most 2000 characters` |
| `401` | Missing or invalid JWT token |
| `403` | Caller is not a seeker: `Only job seekers can apply for jobs.` |
| `404` | No job with that id |
| `422` | `This job is no longer accepting applications.` or `You have already applied to this job.` |

Applying again to a job whose application was `WITHDRAWN` re-opens that same application as `APPLIED`.

### `GET /applications/me`

The caller's applications, newest first, including withdrawn ones and ones for jobs that have since closed (`job.available = false`). Response `200`: `ApplicationResponse[]`. Errors: `401`, `403` (not a seeker).

### `GET /applications/me/stats`

Seeker only. Numbers for the dashboard charts, computed from the caller's own applications.

```json
{
  "total": 7,
  "active": 4,
  "byStatus": { "APPLIED": 1, "IN_REVIEW": 1, "INTERVIEW": 1, "OFFER": 1, "REJECTED": 1, "WITHDRAWN": 2 },
  "weekly": [ { "weekStart": "2026-07-20", "count": 0 }, "... 12 entries ...", { "weekStart": "2026-10-05", "count": 3 } ]
}
```

| Field | Notes |
|---|---|
| `total` | Every application ever made, including rejected and withdrawn |
| `active` | `APPLIED` + `IN_REVIEW` + `INTERVIEW` + `OFFER`. `REJECTED` and `WITHDRAWN` are not active |
| `byStatus` | All six statuses are always present, zero-filled |
| `weekly` | Applications **submitted** in each of the last 12 weeks, oldest first, zero-filled; the last entry is the current week. A week runs Monday to Sunday and `weekStart` is its Monday. Older applications count in the totals but not here |

Errors: `401`, `403` `Only job seekers have applications.`

### `DELETE /applications/{id}`

The applicant withdraws their application. The row is **kept** with status `WITHDRAWN` (history is not lost) and `updatedAt` changes. Response `204 No Content`.

Possible from `APPLIED`, `IN_REVIEW`, `INTERVIEW` and `OFFER`.

| Status | When |
|---|---|
| `401` | Missing or invalid JWT token |
| `403` | Caller is not the applicant: `You can only withdraw your own applications.` (employers included) |
| `404` | No application with that id |
| `422` | Already `REJECTED` or `WITHDRAWN`: `This application can no longer be withdrawn.` |

The seeker can apply to the same job again afterwards (see `POST /jobs/{id}/apply`).

### `GET /jobs/{id}/applications`

**EMPLOYER, owner of the job only.** The applications to that job, newest first, withdrawn ones included (flagged `WITHDRAWN`). Optional query `status=` (one of the `ApplicationStatus` values) narrows the list. Response `200`: `JobApplicationResponse[]`.

| Status | When |
|---|---|
| `400` | Unknown `status` value: `status has an invalid value. Allowed values: APPLIED, IN_REVIEW, ...` |
| `401` | Missing or invalid JWT token |
| `403` | Not the job's owner (other employers, seekers): `You can only view applications for your own jobs.` |
| `404` | No job with that id |

### `PUT /applications/{id}/status`

**EMPLOYER, owner of the job only.** Moves an application to another stage. Body: `{ "status": "IN_REVIEW" }`. Response `200`: the updated `JobApplicationResponse`. `updatedAt` changes, and the seeker sees the new status in `GET /applications/me`.

Allowed moves:

| From | To |
|---|---|
| `APPLIED` | `IN_REVIEW`, `REJECTED` |
| `IN_REVIEW` | `INTERVIEW`, `REJECTED` |
| `INTERVIEW` | `OFFER`, `REJECTED` |
| `OFFER` | `REJECTED` |
| `REJECTED`, `WITHDRAWN` | final, no moves |

Employers cannot set `APPLIED` or `WITHDRAWN` (withdrawing is the applicant's action).

| Status | When |
|---|---|
| `400` | `status` missing (`status is required`) or not a known value |
| `401` | Missing or invalid JWT token |
| `403` | Not the job's owner (the applicant included): `You can only manage applications for your own jobs.` |
| `404` | No application with that id |
| `422` | `An application in INTERVIEW cannot move to IN_REVIEW.`, `This application is already REJECTED and can no longer change.`, or `Only the applicant can withdraw an application.` |

### `JobApplicationResponse` (employer view)

```ts
interface JobApplicationResponse {
  id: string;                     // UUID
  status: "APPLIED" | "IN_REVIEW" | "INTERVIEW" | "OFFER" | "REJECTED" | "WITHDRAWN";
  coverNote: string | null;
  createdAt: string;              // ISO-8601, no zone
  updatedAt: string;
  applicant: {
    seekerId: string;             // seeker profile id (UUID)
    username: string;
    name: string;
    lastName: string;
    hasCv: boolean;
  };
}
```

### `ApplicationResponse`

```ts
interface ApplicationResponse {
  id: string;                     // UUID
  status: "APPLIED" | "IN_REVIEW" | "INTERVIEW" | "OFFER" | "REJECTED" | "WITHDRAWN";
  coverNote: string | null;
  createdAt: string;              // ISO-8601, no zone
  updatedAt: string;              // changes whenever the status changes
  job: {
    postId: number;
    jobTitle: string;
    employerUsername: string | null;
    companyName: string | null;   // the employer's company, if they have one
    available: boolean;           // false once the employer closed the position
  };
}
```

Deleting an account (admin-approved) also deletes that account's applications: a seeker's own, or all applications to an employer's jobs.

---

## Users endpoints `/users/**`

> **All endpoints require** `Authorization: Bearer <token>`.  
> `/me` endpoints use the **username embedded in the JWT** — no ID needed in the path.

---

### Seeker endpoints

#### `GET /users/seekers/me`

Returns the seeker profile of the authenticated user.

**Response `200 OK`**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "username": "jane_doe",
  "name": "Jane",
  "lastName": "Doe",
  "creationDate": "2024-01-15T10:30:00",
  "isIndependent": false,
  "cv": null,
  "educations": [],
  "certifications": [],
  "experiences": [],
  "skills": []
}
```

| Field | Type | Notes |
|---|---|---|
| `id` | UUID string | Profile UUID (different from `AppUser` row ID) |
| `username` | string | Matches the login username |
| `name` | string | First name |
| `lastName` | string | Last name |
| `creationDate` | ISO-8601 datetime | When the profile was created |
| `isIndependent` | boolean | Whether the seeker is an independent contractor |
| `cv` | object \| null | `{ originalFileName, fileType, uploadedAt }` of the uploaded resume, or `null` |
| `educations`, `certifications`, `experiences`, `skills` | arrays | The profile lists, see the CRUD sections below. Always present, possibly empty |

**Error responses**

| Status | When |
|---|---|
| `401` | Invalid or missing token |
| `404` | Seeker profile not found (token belongs to an EMPLOYER) |

---

#### `GET /users/seekers/{id}`

Returns any seeker profile by UUID. Accessible to all authenticated users.

**Path parameter:** `id` — seeker UUID.

**Response `200 OK`** — same shape as `/seekers/me`.

---

#### `PUT /users/seekers/me`

Updates the authenticated seeker's first and last name.

**Request body**

```json
{
  "name": "Jane",
  "lastName": "Smith"
}
```

**Response `200 OK`** — updated `SeekerResponse`.

---

### Employer endpoints

#### `GET /users/employers/me`

Returns the employer profile of the authenticated user, including the linked company if one exists.

**Response `200 OK`**

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "username": "acme_corp",
  "name": "Alice",
  "lastName": "Smith",
  "creationDate": "2024-01-15T10:30:00",
  "company": {
    "id": "7f3b2c4a-1234-5678-abcd-ef0123456789",
    "name": "Acme Corporation"
  }
}
```

`company` is `null` until `POST /users/companies` is called.

---

#### `GET /users/employers/{id}`

Returns any employer profile by UUID.

**Path parameter:** `id` — employer UUID.

**Response `200 OK`** — same shape as `/employers/me`.

---

#### `PUT /users/employers/me`

Updates the authenticated employer's first and last name.

**Request body**

```json
{
  "name": "Alice",
  "lastName": "Johnson"
}
```

**Response `200 OK`** — updated `EmployerResponse`.

---

### Company endpoints

An employer can only have **one** company. The company is a child of the employer profile.

#### `POST /users/companies`

Creates a company and links it to the authenticated employer. Returns `422` if a company already exists — use the update endpoint instead.

**Request body**

```json
{
  "name": "Acme Corporation"
}
```

**Response `201 Created`**

```json
{
  "id": "7f3b2c4a-1234-5678-abcd-ef0123456789",
  "name": "Acme Corporation"
}
```

**Error responses**

| Status | When |
|---|---|
| `400` | `name` is blank |
| `401` | Invalid token |
| `404` | Employer profile not found |
| `422` | Employer already has a company |

---

#### `PUT /users/companies/{id}`

Updates the name of the company identified by `{id}`. The caller must own the company.

**Path parameter:** `id` — company UUID (from `CompanyResponse.id`).

**Request body**

```json
{
  "name": "Acme Corp (rebranded)"
}
```

**Response `200 OK`** — updated `CompanyResponse`.

**Error responses**

| Status | When |
|---|---|
| `401` | Invalid token |
| `404` | Employer not found |
| `422` | The `{id}` does not belong to the authenticated employer |

---

### Resume (seeker)

All three need a token. A `404` carries the standard wording `<Thing> not found with id: <value>` (for example `Resume not found with id: alice_s`); the seeker routes return it too when the token belongs to an employer.

| Route | What it does |
|---|---|
| `POST /users/seekers/me/resume` | `multipart/form-data` with one part named **`file`**. PDF, DOC or DOCX, max 5 MB; a new upload replaces the old one. Returns the updated `SeekerResponse` (`200`). Errors: `422` `Please choose a file to upload.`, `Resume must be 5MB or smaller.`, `Only PDF, DOC, and DOCX resumes are supported.`; `400` `The file part 'file' is required.` |
| `GET /users/seekers/me/resume` | Downloads the file (binary, with the original file name). `404` if none. |
| `DELETE /users/seekers/me/resume` | Removes it. `204`. `404` if none. |

### Seeker profile lists: education, certifications, experiences, skills

Four identical-shaped sets of routes under `/users/seekers/me/`, for the authenticated seeker only. `GET` returns the list (`200`), `POST` creates (`201`, returns the new item), `PUT /{id}` replaces (`200`), `DELETE /{id}` removes (`204`). An id that is not the caller's is `404`.

| Path | Request body fields (`*` required) | Response item fields |
|---|---|---|
| `/educations` | `institution*`, `degree*`, `fieldOfStudy*` (max 255 each), `startDate*`, `endDate`, `grade` (max 255) | `id`, the same fields |
| `/certifications` | `name*`, `issuingOrganization*` (max 255), `issueDate*`, `expirationDate`, `credentialId` (max 255), `credentialUrl` (max 255, must start with `http://` or `https://`) | `id`, the same fields |
| `/experiences` | `jobTitle*`, `companyName*` (max 255), `location` (max 255), `employmentType` (see enums), `startDate*`, `endDate`, `description` (max 5,000) | `id`, the same fields |
| `/skills` | `skillName*` (max 100; created in the shared catalog if new), `category` (max 100), `proficiencyLevel*` (`BEGINNER`, `INTERMEDIATE`, `ADVANCED`, `EXPERT`), `yearsOfExperience` (0 to 80) | `id`, `skillName`, `category`, `proficiencyLevel`, `yearsOfExperience` |

Rules: an `endDate`/`expirationDate` earlier than the start is `422` `End date cannot be before start date.`; adding a skill the seeker already has is `422` `You already have this skill on your profile. Update it instead.`

---

## Account endpoints `/account/**`

> All require a token and act on the authenticated account.

### `PUT /account/password`

Body `{ "currentPassword": "...", "newPassword": "..." }` (new password 8 to 72 characters, plus the password policy). `200` with an empty body.
Errors: `400` validation (`newPassword must be 8 to 72 characters`) or a policy message, `422` `Current password is incorrect.` or `The new password must be different from the current one.`

### Deletion requests

A user asks an admin to delete the account; nothing is deleted until an admin approves.

| Route | What it does |
|---|---|
| `POST /account/deletion-request` | Body optional: `{ "reason": "..." }` (max 255). `201` with a `DeletionRequestResponse`. `422` `You already have a pending deletion request.` |
| `GET /account/deletion-request` | The pending request, or **`200` with an empty body (null)** when there is none. The front end relies on this. |
| `DELETE /account/deletion-request` | Cancels the pending request. `204`. `404` if none. |

```ts
interface DeletionRequestResponse {
  id: string;                     // UUID
  username: string;
  requesterRole: "SEEKER" | "EMPLOYER" | "ADMIN";
  reason: string | null;
  status: "PENDING" | "APPROVED" | "REJECTED";
  requestedAt: string;
  resolvedAt: string | null;
  resolutionNote: string | null;
}
```

---

## Admin endpoints `/admin/**`

> Require `ROLE_ADMIN`; other users get `403`.

| Route | What it does |
|---|---|
| `GET /admin/users` | Accounts, **always paged** (see below). |
| `GET /admin/stats` | Headline numbers (see below). |
| `GET /admin/deletion-requests` | Pending deletion requests, oldest first (`DeletionRequestResponse[]`). |
| `POST /admin/deletion-requests/{id}/approve` | Permanently deletes the requester's account and profile, **including their applications and saved jobs, and for an employer all their job posts**. `200` with the resolved request. `404` unknown id, `422` `This request has already been resolved.` |
| `POST /admin/deletion-requests/{id}/reject` | Body optional: `{ "note": "..." }` (max 255). `200` with the resolved request (`resolutionNote` set). Same errors. |

### `GET /admin/users`

Query: `q` (text to find in the username or the profile's first or last name, contains, any case; at most 100 characters; `%` and `_` are literal), `role` (`SEEKER`, `EMPLOYER` or `ADMIN`), `sort` (`username` default, `role`, `newest`, `oldest`), `page` (from 0), `size` (1 to 100, default 20). Response `200` is the same paged envelope the search proposal uses:

```json
{
  "content": [ { "id": 2, "username": "alice_s", "role": "SEEKER", "name": "Alice", "lastName": "Johnson",
                 "profileId": "550e8400-...", "creationDate": "2026-09-30T08:12:00" } ],
  "page": 0, "size": 20, "totalElements": 7, "totalPages": 1, "last": true
}
```

`id` is the numeric account id; `profileId` is the profile UUID (for `GET /users/seekers/{id}` or `/users/employers/{id}`). `name`, `lastName`, `profileId` and `creationDate` are `null` for accounts without a profile (admins). Passwords are never returned. No match is `200` with an empty `content`. Errors: `400` (`size must be between 1 and 100.`, `page must be 0 or more.`, `sort must be one of: username, role, newest, oldest.`, `q must be at most 100 characters.`, unknown `role`), `401`, `403`.

**Changed in sprint 4:** this route used to return a plain array of `{ id, username, role }`. Nothing was calling it, so it was changed rather than kept alongside a second shape. The three old fields are still there.

### `GET /admin/stats`

```json
{
  "users":        { "total": 7, "byRole": { "SEEKER": 3, "EMPLOYER": 3, "ADMIN": 1 }, "newLast7Days": 6, "newLast30Days": 6 },
  "jobs":         { "total": 9, "open": 7, "closed": 2, "postedLast7Days": 9 },
  "applications": { "total": 4, "byStatus": { "APPLIED": 1, "IN_REVIEW": 1, "INTERVIEW": 0, "OFFER": 0, "REJECTED": 1, "WITHDRAWN": 1 }, "submittedLast7Days": 4 },
  "pendingDeletionRequests": 2
}
```

`byRole` and `byStatus` always contain every key (zero-filled). "Last 7 / 30 days" counts profiles created, jobs posted and applications submitted in that window. Errors: `401`, `403`.

### Audit log

Admin actions are written to a separate `AUDIT` logger, one line each: who, what, and the details. Covered: listing users (with the search used), viewing the stats, approving or rejecting a deletion request, and editing site content. Text from requests is flattened to one line so it cannot forge an entry. Route the `AUDIT` logger to its own file or system in production.

---

## Content (CMS) endpoints

Editable site text (SEO titles, landing copy, error messages).

### `GET /content`

**Public.** A flat map `{ "<key>": "<value>" }` of all entries.

### `GET /admin/content` (admin)

`ContentEntryResponse[]`: `{ key, category, value, updatedAt }`, where `category` is `SEO`, `LANDING` or `ERROR_MESSAGE`.

### `PUT /admin/content` (admin)

Body `{ "values": { "<key>": "<new value>" } }` (required; each value max 4,000 characters, at most 500 entries). Unknown keys are ignored. Returns the full `ContentEntryResponse[]`. `400` `values is required`.

---

## Enum reference

| Enum | Values |
|---|---|
| `Role` (login, register) | `SEEKER`, `EMPLOYER`, `ADMIN` (admin cannot be self-registered) |
| `WorkMode` | `REMOTE`, `HYBRID`, `ONSITE` |
| `EmploymentType` | `FULL_TIME`, `PART_TIME`, `CONTRACT`, `TEMPORARY`, `INTERNSHIP`, `FREELANCE`, `B2B` |
| `RateType` | `HOURLY`, `MONTHLY`, `YEARLY`, `CONTRACT_TOTAL` |
| `ApplicationStatus` | `APPLIED`, `IN_REVIEW`, `INTERVIEW`, `OFFER`, `REJECTED`, `WITHDRAWN` |
| `ProficiencyLevel` | `BEGINNER`, `INTERMEDIATE`, `ADVANCED`, `EXPERT` |
| `DeletionRequestStatus` | `PENDING`, `APPROVED`, `REJECTED` |
| `ContentCategory` | `SEO`, `LANDING`, `ERROR_MESSAGE` |

Adding a value to any of these is a contract change: tell the front-end session.

---

## Route summary

| Area | Routes | Access |
|---|---|---|
| Auth | `POST /auth/register`, `POST /auth/login` | public |
| Jobs | `GET /jobs`, `GET /jobs/{id}`, `GET /companies/{id}` | public |
| Jobs | `POST /jobs`, `PUT /jobs/{id}`, `PATCH /jobs/{id}/available`, `GET /jobs/mine` | employer (owner) |
| Saved jobs | `PUT`/`DELETE /jobs/{id}/save`, `GET /jobs/saved` | seeker |
| Applications | `POST /jobs/{id}/apply`, `GET /applications/me`, `GET /applications/me/stats`, `DELETE /applications/{id}` | seeker |
| Applications | `GET /jobs/{id}/applications`, `PUT /applications/{id}/status` | employer (owner of the job) |
| Employer stats | `GET /employers/me/stats` | employer |
| Profiles | `/users/seekers/**`, `/users/employers/**`, `/users/companies/**` | authenticated (`/me` = own) |
| Notifications | `GET /notifications`, `GET /notifications/unread-count`, `POST /notifications/{id}/read`, `POST /notifications/read-all` | authenticated (own only) |
| Account | `/account/**` | authenticated |
| Admin | `/admin/users`, `/admin/stats`, `/admin/deletion-requests/**`, `/admin/content` | admin |
| Content | `GET /content` | public |

---

## Data models reference

### `TokenResponse`

```ts
interface TokenResponse {
  token: string;        // JWT — pass as Bearer header
  type: string;         // always "Bearer"
  expiresIn: number;    // milliseconds (default 86400000 = 24 h)
  username: string;
  role: "SEEKER" | "EMPLOYER";
}
```

### `JobPostResponse`

```ts
interface JobPostResponse {
  postId: number;
  jobTitle: string;
  jobDescription: string;
  rate: number;                   // amount, in the unit of rateType
  rateType: "HOURLY" | "MONTHLY" | "YEARLY" | "CONTRACT_TOTAL";
  hourlyRate: number;             // deprecated: hourly equivalent of rate, 0 for CONTRACT_TOTAL
  employerUsername: string | null;
  companyName: string | null;     // the employer's company, if any
  companyId: string | null;
  available: boolean;             // true = open; false = closed/filled
  location: string | null;
  workMode: "ONSITE" | "REMOTE" | "HYBRID" | null;
  employmentType: string | null;
  createdAt: string | null;       // ISO-8601, no zone
  requiredSkills: string[];
}
```

### `CreateJobRequest`

```ts
interface CreateJobRequest {
  jobTitle: string;               // required, max 150
  jobDescription: string;         // required, HTML, max 20,000 (sanitized server-side)
  rate: number;                   // > 0
  rateType: "HOURLY" | "MONTHLY" | "YEARLY" | "CONTRACT_TOTAL";
  location?: string;              // max 255
  workMode?: "ONSITE" | "REMOTE" | "HYBRID";
  employmentType?: string;
  requiredSkills?: string[];      // skill names
}
```

### `UpdateJobAvailabilityRequest`

```ts
interface UpdateJobAvailabilityRequest {
  available: boolean;   // true = reopen; false = close/fill
}
```

### `SeekerResponse`

```ts
interface SeekerResponse {
  id: string;               // UUID
  username: string;
  name: string;
  lastName: string;
  creationDate: string;     // ISO-8601
  isIndependent: boolean;
  cv: { originalFileName: string; fileType: string; uploadedAt: string } | null;
  educations: EducationResponse[];
  certifications: CertificationResponse[];
  experiences: ProfessionalExperienceResponse[];
  skills: SeekerSkillResponse[];
}
```

### `EmployerResponse`

```ts
interface EmployerResponse {
  id: string;               // UUID
  username: string;
  name: string;
  lastName: string;
  creationDate: string;     // ISO-8601
  company: CompanyResponse | null;
}
```

### `CompanyResponse`

```ts
interface CompanyResponse {
  id: string;               // UUID
  name: string;
}
```

---

## Typical client flows

### Flow 1 — Seeker signs up and browses jobs

```
POST /auth/register   { username, password, role: "SEEKER", firstName, lastName }
  → 201 { token, username, role: "SEEKER" }

Store token in localStorage.

GET /jobs?available=true   Authorization: Bearer <token>
  → 200 [ { postId, jobTitle, ..., available: true }, ... ]   // only open positions

GET /users/seekers/me Authorization: Bearer <token>
  → 200 { id, username, name, lastName, ... }
```

### Flow 2 — Employer signs up and creates a company + job post

```
POST /auth/register   { username, password, role: "EMPLOYER", firstName, lastName }
  → 201 { token, username, role: "EMPLOYER" }

POST /users/companies Authorization: Bearer <token>
  { "name": "Acme Corporation" }
  → 201 { id, name }

POST /jobs            Authorization: Bearer <token>
  { jobTitle, jobDescription, rate, rateType }
  → 201 { postId, jobTitle, ..., available: true }     // new posts are open by default

// Close the position once it's filled:
PATCH /jobs/{postId}/available   Authorization: Bearer <token>
  { "available": false }
  → 200 { postId, ..., available: false }
```

### Flow 3 — Returning user logs in

```
POST /auth/login      { username, password }
  → 200 { token, username, role }

// Use token from here on for all other requests.
```

---

## Notes for frontend developers

- **Token storage:** `localStorage` is the simplest option; consider `httpOnly` cookies for production.
- **Token expiry:** `expiresIn` is in **milliseconds**. Compute expiry as `Date.now() + expiresIn`.
- **Role-based rendering:** Use the `role` field from `TokenResponse` to decide which profile endpoint to call (`/seekers/me` vs `/employers/me`) and which UI to display.
- **`/me` endpoints vs `/{id}` endpoints:** Use `/me` for the authenticated user's own data (token carries the identity). Use `/{id}` to look up other users' public profiles.
- **Job availability:** Always call `GET /jobs?available=true` in the seeker browse view — this filters out closed/filled positions server-side. The `available` flag is also included in every `JobPostResponse` so you can do client-side checks or render a "Closed" badge when displaying all posts (e.g. in an employer dashboard). To close a position, call `PATCH /jobs/{id}/available` with `{ "available": false }`.
- **CORS:** The backend allows requests from `http://localhost:3000` and `http://localhost:3001` by default (methods GET, POST, PUT, PATCH, DELETE, OPTIONS). Change `cors.allowed-origins` in `application.properties` for other origins.
- **Timestamps:** All `LocalDateTime` fields are serialized as ISO-8601 strings **without a time zone** (e.g. `"2024-01-15T10:30:00"`); treat them as server-local time.
- **Machine-readable spec:** the OpenAPI JSON at `/v3/api-docs` is generated from the controllers and is always current. Generate typed clients from it, and treat this file as the human guide.
- **Interactive testing:** Open `http://localhost:9080/swagger-ui/index.html`, click **Authorize**, paste your JWT, and try every endpoint from the browser.
