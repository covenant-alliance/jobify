# Jobify API Documentation

**Base URL:** `http://localhost:9080`  
**Swagger UI:** `http://localhost:9080/swagger-ui/index.html`  
**OpenAPI spec (JSON):** `http://localhost:9080/v3/api-docs`

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
| `404` | Resource not found |
| `422` | Business rule violation (e.g. duplicate company) |
| `500` | Unexpected server error |

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
| `password` | string | yes | Plain text — hashed server-side (BCrypt) |
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
| `401` | Username not found or wrong password |

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
  "isIndependent": false
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
- **CORS:** The backend allows requests from `http://localhost:3000` by default. Change `cors.allowed-origins` in `application.properties` for other origins.
- **Timestamps:** All `LocalDateTime` fields are serialized as ISO-8601 strings in UTC (e.g. `"2024-01-15T10:30:00"`).
- **Interactive testing:** Open `http://localhost:9080/swagger-ui/index.html`, click **Authorize**, paste your JWT, and try every endpoint from the browser.
