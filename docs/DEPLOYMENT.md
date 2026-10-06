# Running Jobify with PostgreSQL (Docker and plain Java)

For moving data between databases see [`DATABASE_MIGRATION.md`](DATABASE_MIGRATION.md).

Two ways to run the back end:

| | Profile | Database | Schema | Use for |
|---|---|---|---|---|
| **dev** (default) | `dev` | H2 file `./data/jobifydb` | Hibernate `update` | local work; demo accounts; Swagger and H2 console on |
| **production-style** | `postgres` | PostgreSQL 18 | Flyway + Hibernate `validate` | servers; no demo data; Swagger and H2 console off |

The `postgres` profile is chosen with `SPRING_PROFILES_ACTIVE=postgres`. Anything other than `dev` requires `JWT_SECRET`
and refuses the public placeholder key (`JwtConfig`).

> **Verification status.** The PostgreSQL profile, Flyway and the app were run against a real PostgreSQL 18.6.
> The `Dockerfile` and `docker-compose.yml` were **not built or started** (the environment they were written in
> has no Docker daemon); `docker compose config` accepts the compose file. Run the first-time checklist at the
> end of this page the first time you use them.

## Quick start with Docker Compose

```bash
cp .env.example .env
# edit .env: set JWT_SECRET (openssl rand -base64 32) and POSTGRES_PASSWORD; set the CORS origin of your front end
docker compose up -d --build
docker compose logs -f app          # wait for "Started JobifyApplication"
curl localhost:9080/actuator/health # {"status":"UP",...}
```

`docker compose` refuses to start without `JWT_SECRET` and `POSTGRES_PASSWORD`: there are no default secrets.

### First administrator

A production database has no demo accounts, so the first admin comes from the environment:

```env
BOOTSTRAP_ADMIN_USERNAME=root_admin
BOOTSTRAP_ADMIN_PASSWORD=<a strong password>
```

On startup `AdminBootstrap` creates that admin **only if the database has no admin at all**. After that the two
variables are ignored, so you can leave them set or remove them; they cannot reset or add admins. A password
that fails the password policy (too short, too common, equal to the username) stops the startup with a clear
message rather than creating a weak admin. The password is never logged; the creation is recorded on the `AUDIT`
logger (`action=BOOTSTRAP_ADMIN_CREATED`). Change the password from the app afterwards (`PUT /account/password`) and remove it from `.env`.

## Settings (environment variables)

| Variable | Default | Meaning |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `postgres` in the image | `dev` or `postgres` |
| `JWT_SECRET` | none, **required** | Base64 key of at least 256 bits |
| `DB_URL` | `jdbc:postgresql://localhost:5432/jobify` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `jobify` / empty | |
| `DB_POOL_SIZE` | `10` | Hikari pool size |
| `UPLOAD_DIR` | `/data/uploads` | where resumes are stored (a volume in Docker) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | comma-separated front-end origins |
| `TRUST_FORWARDED_FOR` | `false` | `true` only behind a proxy you control that overwrites `X-Forwarded-For` |
| `APP_DOCS_ENABLED` | `false` | `true` makes Swagger UI and `/v3/api-docs` public |
| `LOG_FORMAT` | `logstash` | JSON log format on stdout: `logstash`, `ecs` or `gelf` (profile `postgres` only; `dev` logs plain text) |
| `BOOTSTRAP_ADMIN_USERNAME` / `_PASSWORD` | empty | first admin, see above |

Compose-only: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `APP_PORT`.

## Without Docker

```bash
mvn -DskipTests package
JWT_SECRET=... SPRING_PROFILES_ACTIVE=postgres \
DB_URL=jdbc:postgresql://localhost:5432/jobify DB_USERNAME=jobify DB_PASSWORD=... \
UPLOAD_DIR=/var/lib/jobify/uploads java -jar target/jobify-0.0.1-SNAPSHOT.jar
```

Create the database first (`create user jobify password '...'; create database jobify owner jobify;`). Flyway
creates the tables on first start.

## What the `postgres` profile changes (and why)

| Setting | dev | postgres | Reason |
|---|---|---|---|
| Schema | Hibernate `update` | Flyway migrations + Hibernate `validate` | reviewable, repeatable schema; startup fails fast if entities and schema drift |
| Demo accounts (`password` for everyone) | created in an empty DB | **never** | a known password is not acceptable on a server |
| H2 console | on | off, and not permitted in security | it is a database admin UI |
| Swagger UI / OpenAPI | public | off unless `APP_DOCS_ENABLED=true` | hides the API map from strangers |
| H2-only startup helpers | on | off | they read H2's catalog |
| Upload folder | `./data/uploads` | `/data/uploads` | a volume you can back up |

## Operating it

### Backups: two things, always together

1. **The database.** From the host, with the Compose service names:

   ```bash
   docker compose exec -T db pg_dump -U jobify -d jobify --format=custom > backup-$(date +%F).dump
   ```
   (`pg_dump` inside the `postgres:18` container matches the server version.) Restore: see
   `DATABASE_MIGRATION.md`, Recipe B2.
2. **The uploads volume** (resumes, job images, company logos):

   ```bash
   docker run --rm -v jobify_jobify-uploads:/data -v "$PWD":/backup alpine \
     tar czf /backup/uploads-$(date +%F).tar.gz -C /data .
   ```
   The volume name is `<compose project>_jobify-uploads`; check with `docker volume ls`.

A database backup without the uploads leaves resume records pointing at files that no longer exist.

### Restoring uploads

```bash
docker run --rm -v jobify_jobify-uploads:/data -v "$PWD":/backup alpine \
  sh -c "cd /data && tar xzf /backup/uploads-YYYY-MM-DD.tar.gz && chown -R 999:999 . 2>/dev/null; true"
```
The app runs as a non-root user inside the image; if downloads fail with a permissions error after a restore, fix
the ownership of the files to that user (`docker compose exec app id` shows its uid).

### Upgrading the app

1. Take the backups above.
2. `git pull && docker compose up -d --build`. On start, Flyway applies any new `V<n>__*.sql` migration, then
   Hibernate validates. If a migration fails, the app does not start; the database is left at the last successful
   version (each migration runs in a transaction on PostgreSQL), and the previous image can be started again.
3. Never edit a migration that has been applied: Flyway checksums it. Add a new one.

### Several instances

One instance is the supported setup. The login rate limit and the account lock count in memory, so two instances
each allow their own budget; and a user locked on one is not locked on the other. Fixing that needs a shared
store (a Redis-backed counter) and is not done.

### Behind a reverse proxy

Terminate TLS at the proxy (nginx, Caddy, a cloud load balancer) and forward to port 9080. Set
`TRUST_FORWARDED_FOR=true` **only** if the proxy overwrites `X-Forwarded-For` (otherwise a client can invent an
address to dodge the rate limit); leave it `false` if the app is reachable directly. Set `CORS_ALLOWED_ORIGINS`
to the real front-end origin(s), with `https://`.

### Logs, correlation ids and the audit trail

**Format.** With the `postgres` profile every log line on stdout is one JSON object (`LOG_FORMAT`, default
`logstash`), ready for Loki, ELK, CloudWatch and similar. In `dev` the lines are plain text with the request id in
brackets. (The JVM may print one non-JSON `Picked up JAVA_TOOL_OPTIONS` line at start if that variable is set.)

**Correlation.** Every request gets an id. A caller can send its own `X-Request-Id` (up to 64 characters of letters,
digits, `.`, `-`, `_`; anything else is replaced by a random id), and the response always carries `X-Request-Id`,
also readable by browser scripts (exposed in CORS). The id, the client address and, once signed in, the user are
log fields on every line of that request: `requestId`, `clientIp`, `user`. To follow one request:

```bash
docker compose logs app | jq -c 'select(.requestId == "demo-trace-42")'
```

When a user reports a problem, ask for the `X-Request-Id` of the failing response (browser dev tools, Network tab).
Behind a proxy, set `TRUST_FORWARDED_FOR=true` (see above) so `clientIp` is the real client and not the proxy.

**Audit trail.** Security-relevant events go to the dedicated `AUDIT` logger (JSON field `logger_name` = `AUDIT`), so
they can be routed or kept separately. One line per event, `actor='name' action=NAME details`, with the same
`requestId`, `clientIp` and `user` fields. Line breaks in names are flattened so an attacker cannot forge a second
entry, and passwords, tokens and request bodies are never written.

| Action | When |
|---|---|
| `LOGIN_SUCCESS` | a login succeeded |
| `LOGIN_FAILURE` | wrong password, or unknown username (the name that was tried is the actor) |
| `LOGIN_FAILURE_LOCKED` | the failure that locked the account (default: 5 failures in 10 minutes) |
| `LOGIN_BLOCKED` | a login attempt on a locked account (even with the right password) |
| `RATE_LIMITED` | login or register throttled for one address (actor `anonymous`) |
| `REGISTER` | a new account was created (with its role) |
| `PASSWORD_CHANGED` / `PASSWORD_CHANGE_REFUSED` | password change done / current password wrong |
| `DELETION_REQUESTED` / `DELETION_CANCELLED` | the user asked to delete their account / withdrew the request |
| `APPROVE_DELETION` / `REJECT_DELETION` | an admin resolved a deletion request |
| `LIST_USERS`, `VIEW_STATS`, `UPDATE_CONTENT` | admin reads and edits |
| `BOOTSTRAP_ADMIN_CREATED` | the first admin was created from the environment (actor `system`) |
| `ACCESS_DENIED` | a signed-in user tried something they do not own or are not allowed to do (job, application, role) |

Examples worth alerting on: many `LOGIN_FAILURE` from one `clientIp`, any `LOGIN_FAILURE_LOCKED`, `RATE_LIMITED`
bursts, any `ACCESS_DENIED` for the same user repeatedly. Nothing is kept in the database: retention is whatever
your log platform keeps, so set it to match your policy.

### Health

`GET /actuator/health` is public (load balancers and the Docker health check use it) and returns only
`{"status":"UP"}` plus the probe group names. An **administrator's** token additionally shows the components:
database, disk space (with the path and sizes), ping, SSL. Ordinary users and anonymous callers never see them. No
other actuator endpoint (env, beans, heap dump, metrics...) is exposed.

```bash
curl -s localhost:9080/actuator/health -H "Authorization: Bearer $ADMIN_TOKEN" | jq .components
```

## CI

`.github/workflows/ci.yml` runs the suite twice on every pull request: on H2 (the default) and on a PostgreSQL 18
service container with Flyway and Hibernate `validate`. The second job is the guard against a forgotten migration.
Locally: `TEST_DB_URL=jdbc:postgresql://localhost:5432/jobify_test TEST_DB_USER=jobify TEST_DB_PASSWORD=... scripts/test-postgres.sh`
(use an empty throwaway database).

## First-time checklist for the Docker files (not yet verified in a real Docker environment)

- [ ] `docker build -t jobify-backend .` succeeds (needs network for Maven and the base images).
- [ ] `docker compose up -d` starts both containers; `docker compose ps` shows `db` and `app` healthy.
- [ ] `curl localhost:9080/actuator/health` returns `UP`; `curl -i localhost:9080/swagger-ui/index.html` is **not** 200.
- [ ] Log in with the bootstrap admin; the demo accounts (`alice_s` ...) do **not** exist.
- [ ] Upload a resume as a seeker, then `docker compose down && docker compose up -d`: the resume and the data survive.
- [ ] `docker compose exec db psql -U jobify -d jobify -c '\dt'` lists 24 tables (23 + `flyway_schema_history`).
- [ ] If the `postgres:18` volume layout differs from what the compose file assumes (the cluster must be under
      `/var/lib/postgresql/18/...` inside the volume), adjust the mount; the PostgreSQL image's own documentation
      for version 18 is the reference.
