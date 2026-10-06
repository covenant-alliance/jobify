# Moving Jobify's data between databases

How to move Jobify from one database to another, and what to change when the target is a different database
product. Written so you can repeat it without the person who wrote it.

| You want to move | Section | Tested here? |
|---|---|---|
| H2 (dev) to PostgreSQL 18 | [Recipe A](#recipe-a-h2-dev-database-to-postgresql-18) | **Yes**, end to end, with a real PostgreSQL 18.6 |
| One PostgreSQL server to another | [Recipe B](#recipe-b-one-postgresql-server-to-another) | Copy tool: **yes**. `pg_dump`/`pg_restore`: not run (see note) |
| PostgreSQL to MySQL | [Recipe C](#recipe-c-postgresql-to-mysql) | **No.** A reasoned guide, see "What was and was not tested" |

Related: [`DEPLOYMENT.md`](DEPLOYMENT.md) (running the app), `src/main/resources/db/migration/postgresql/` (the schema).

---

## How the pieces fit

```
                     +--------------------------+
 application --JDBC->|  database (rows)         |   <- moved by this guide
                     +--------------------------+
 application --disk->|  upload folder (resumes) |   <- copied as plain files, separately
                     +--------------------------+
 application --env-->|  JWT_SECRET, DB_*, ...   |   <- NOT stored in either; you re-provide them
                     +--------------------------+
```

Three things make up "the data", and a migration has to move all three:

1. **The database rows.** Users, jobs, applications and their status history, CMS text, and so on (25 tables).
2. **The upload folder** (`UPLOAD_DIR`, `/data/uploads` in Docker, `./data/uploads` in dev). Resumes live there as
   files. The database stores only a relative path such as `alice_s_resume/cv.pdf` (job pictures and company logos live under `job-images/` and `company-logos/` in the same folder), so copying the folder
   as-is keeps the links working.
3. **Secrets and settings** (`JWT_SECRET`, database password, CORS origins...). They are environment
   variables, not data. **Keep the same `JWT_SECRET`** if you want users to stay logged in across the move
   (tokens last 24 hours); a new secret just forces everyone to log in again, nothing breaks.

### Who owns the schema

| Database | Who creates the tables | How |
|---|---|---|
| H2 (dev profile, default) | Hibernate | `ddl-auto=update` at startup, plus `LegacySchemaPatch` for old files |
| PostgreSQL (`postgres` profile) | **Flyway** | SQL files in `db/migration/postgresql/`; Hibernate only checks them (`ddl-auto=validate`) and refuses to start if an entity and the schema disagree |

So moving data is always two separate jobs: **(1) get an empty database with the right tables**, which is
the application's own startup (Flyway), and **(2) copy the rows in**, which is the copy tool. Never copy
tables *and* rows from another database product with a generic converter; let Flyway define the tables.

### The copy tool

`com.mcverse.jobify.tools.DatabaseCopyTool` (wrapper: `scripts/db-copy.sh`) is plain JDBC, so it works for any
pair of databases whose JDBC drivers are available:

- reads the table list and foreign keys from the **target**, and copies parents before children;
- matches tables and columns by name ignoring case (H2 upper-cases names, PostgreSQL and MySQL do not);
- reads each value with the getter that fits the **target** column type (text, boolean, timestamp, ...);
- resets identity counters so the next new row gets an id above the copied ones;
- compares row counts per table and **exits non-zero if any differ**;
- never writes to the source, and skips `flyway_schema_history` (the target keeps its own);
- refuses to write into a target that already has rows, unless you pass `--truncate`.

Options: `--source-url --source-user --source-password --target-url --target-user --target-password`
`[--truncate] [--dry-run]`. Passwords can come from `COPY_SOURCE_PASSWORD` / `COPY_TARGET_PASSWORD` so they
stay out of your shell history. `--dry-run` lists the tables in copy order with row counts and writes nothing.

---

## Before any move: checklist

- [ ] **Back up the source.** For the H2 dev database that means copying the whole `data/` folder while the
      app is stopped. Never delete or move the original; a migration only reads it.
- [ ] **Stop the application** that uses the source database. H2 locks its file while the app runs (the tool
      fails with "Database may be already in use"), and for any database a running app can write rows
      between the copy and the switch-over, which you would lose.
- [ ] Use a **current build** (same version on both sides). The tool needs the source tables to exist in the
      target, so an old source needs the new build to have run on it once (Recipe A, step 1).
- [ ] Know the **target connection** string, user and password, and that the target database exists and is empty.
- [ ] Decide the **switch-over** moment (see "Cut-over and rollback").

---

## Recipe A: H2 dev database to PostgreSQL 18

Everything below was run for real against a copy of an old-format dev database and a PostgreSQL 18.6 server.
Replace the paths and URLs with yours. `$JAR` is the packaged jar (`mvn -DskipTests package`, then
`target/jobify-0.0.1-SNAPSHOT.jar`).

### A1. Bring the H2 file up to date with the current build (once)

Old dev databases were created by older builds. Starting the **new** build on them once runs a small
helper that is restricted to H2, `LegacySchemaPatch`: it turns old enum columns into `VARCHAR`, gives very old jobs
their `rate`/`rateType` from the old `hourly_rate` (and then drops that column and `job_rating`). This is what makes
the old file match the current entities, so the copy has nothing odd to trip over. It also gives applications made before status history existed a minimal history (`ApplicationHistoryBackfill`), so the copy carries history rows for every application and the employer funnel works after the move.

```bash
# stop any app that uses the H2 file first, then:
java -jar $JAR --spring.datasource.url=jdbc:h2:file:/path/to/data/jobifydb --server.port=9099
# wait for "Started JobifyApplication", then stop it with Ctrl+C
# (look for "Backfilled rate and rateType" in the log: that is the helper doing its job)
```

If you run it from the project directory with the default settings, it uses `./data/jobifydb`, the usual dev file.

### A2. Create the empty PostgreSQL database and let Flyway build the tables

```bash
psql -h DBHOST -U postgres -c "create user jobify password 'CHOOSE-A-PASSWORD'" \
                          -c "create database jobify owner jobify"

# Start the app ONCE on the empty database so Flyway creates the schema, then stop it.
JWT_SECRET=$(openssl rand -base64 32) SPRING_PROFILES_ACTIVE=postgres \
DB_URL=jdbc:postgresql://DBHOST:5432/jobify DB_USERNAME=jobify DB_PASSWORD=CHOOSE-A-PASSWORD \
UPLOAD_DIR=/tmp/jobify-throwaway java -jar $JAR
# expect: 'Successfully applied 2 migrations to schema "public"' and later 'Started JobifyApplication'; then Ctrl+C
```

Why start the app instead of running the SQL by hand? Because Flyway also records *which* migrations were
applied in `flyway_schema_history`. If you ran `V1__baseline.sql` with `psql`, the app would try to apply it
again on first start and fail with "relation already exists".

**Do not set `BOOTSTRAP_ADMIN_*` for this run**: the copied `app_users` will bring the real admin.

Side effect to know about: that first start also runs `ContentSeeder`, which inserts the default CMS texts
(15 rows in `content_entries`). That is why the copy in step A3 needs `--truncate`: your real CMS texts replace
the defaults. (The tool says so and stops if you forget.)

### A3. Copy the rows

```bash
# optional first look: what would be copied, and in which order
scripts/db-copy.sh --source-url=jdbc:h2:file:/path/to/data/jobifydb --source-user=sa \
                   --target-url=jdbc:postgresql://DBHOST:5432/jobify --target-user=jobify \
                   --dry-run          # password: COPY_TARGET_PASSWORD=... (or --target-password=...)

# the real copy
COPY_TARGET_PASSWORD=CHOOSE-A-PASSWORD \
scripts/db-copy.sh --source-url=jdbc:h2:file:/path/to/data/jobifydb --source-user=sa \
                   --target-url=jdbc:postgresql://DBHOST:5432/jobify --target-user=jobify --truncate
```

The H2 URL is the file path **without** `.mv.db`. Expected output ends like this (numbers are from the old
dev database used for the test run):

```
Emptied the target tables (--truncate).
Identity app_users.id continues at 8
Identity job_posts.post_id continues at 10

table                          source     target
app_users                           7          7  ok
...
job_posts                           9          9  ok
All tables match.
```

Exit code 0 means every table matched. Anything else: do not switch over; read the message. The tool rolls
back the table that failed; fix the cause and run again with `--truncate` (it starts clean).

### A4. Copy the upload folder

```bash
rsync -a /path/to/data/uploads/ /path/on/new/host/uploads/     # or: cp -a, or a docker volume copy
```

Point `UPLOAD_DIR` at the new location. With Docker: see `DEPLOYMENT.md`, "Restoring uploads".

### A5. Start for real and check

```bash
JWT_SECRET=<the same as before if you want sessions to survive> SPRING_PROFILES_ACTIVE=postgres \
DB_URL=... DB_USERNAME=jobify DB_PASSWORD=... UPLOAD_DIR=/path/on/new/host/uploads java -jar $JAR
```

Then verify (this is what was checked in the test run):

- the log says `Successfully validated 2 migrations` (Flyway) and the app starts (Hibernate `validate` passed);
- `curl localhost:9080/jobs` returns the same number of jobs as before, with rates;
- log in as an existing user; open an existing job's detail page (descriptions come through intact);
- create a job and register a user: new ids must continue above the copied ones, with no "duplicate key" error;
- a seeker's resume downloads (proves the upload folder and the stored paths line up);
- `select count(*) from ...` on a few tables matches the tool's report.

Keep the H2 `data/` folder untouched for a while as your rollback.

---

## Recipe B: one PostgreSQL server to another

Same database product, new server (new host, new cloud, bigger machine, new major version). Two ways. Use the
**copy tool** when the servers or major versions differ, or when you want the same verified procedure as
Recipe A. Use **`pg_dump`/`pg_restore`** for a plain like-for-like clone.

### B1. With the copy tool (tested: PostgreSQL 18 to PostgreSQL 18)

1. Stop the app (or put it in maintenance) so nothing writes.
2. On the **new** server: create the user and an empty database, start the app once with the `postgres` profile
   pointing at it (Flyway builds the schema), stop it. Exactly steps A2 above.
3. Copy:

   ```bash
   COPY_SOURCE_PASSWORD=... COPY_TARGET_PASSWORD=... \
   scripts/db-copy.sh --source-url=jdbc:postgresql://OLDHOST:5432/jobify --source-user=jobify \
                      --target-url=jdbc:postgresql://NEWHOST:5432/jobify --target-user=jobify --truncate
   ```
4. Copy the upload folder (A4), switch `DB_URL` and `UPLOAD_DIR`, start, and verify (A5).

Because the schema comes from Flyway on both sides, the new server can run a *newer* application version than
the old one, as long as the migrations on the new side are a superset: the tool matches by column name and will
stop with "column ... does not exist in the target" if the schemas truly differ.

### B2. With `pg_dump` / `pg_restore` (standard PostgreSQL practice; not run in the test environment)

> The sandbox this guide was produced in had a PostgreSQL 16 client and a PostgreSQL 18 server, and
> `pg_dump` refuses to dump a server newer than itself. So these commands are the standard ones, not
> something that was exercised here. **Use `pg_dump` from the same or a newer major version than the source
> server** (`docker run --rm postgres:18 pg_dump ...` is an easy way to get one).

```bash
# 1. dump (custom format: compressed, selective, restorable in parallel)
pg_dump -h OLDHOST -U jobify -d jobify --format=custom --no-owner --no-privileges -f jobify.dump

# 2. on the new server: create the user and an EMPTY database (do NOT start the app on it first)
psql -h NEWHOST -U postgres -c "create user jobify password '...'" -c "create database jobify owner jobify"

# 3. restore (this brings the tables, the data AND flyway_schema_history)
pg_restore -h NEWHOST -U jobify -d jobify --no-owner --no-privileges --exit-on-error jobify.dump

# 4. check
psql -h NEWHOST -U jobify -d jobify -c "select version, description, success from flyway_schema_history"
```

Differences from the copy tool: the dump carries `flyway_schema_history`, so the app on the new server sees
"schema already at V1" and does not migrate again; sequences are restored with their current values (no
counter reset needed). Do not start the app on the empty target before restoring, or Flyway will create the
tables and `pg_restore` will collide with them.

Then copy uploads, switch the settings, start, verify.

### B3. Upgrading the PostgreSQL major version in place (for example 18 to 19)

Not a data migration: follow PostgreSQL's own upgrade path (`pg_upgrade`, or dump and restore as in B2 into a
container of the new version). In the Docker setup the image `postgres:18` stores its cluster under
`/var/lib/postgresql/18/...`, which is why the compose file mounts `/var/lib/postgresql`.

---

## Recipe C: PostgreSQL to MySQL

> **Status: a guide, not a tested procedure.** No MySQL server was available where this was written, so none
> of this was executed. What *is* in place and tested is everything that does not depend on MySQL: the copy tool
> (generic JDBC, tested between H2 and PostgreSQL), the schema defined in plain SQL types, and the
> per-database migration folder. Treat the MySQL specifics below as a checklist to verify, and expect to fix
> a few things on the first run. The order of work is the order a careful port would follow.

Target: **MySQL 8.4 LTS or newer** (MariaDB 10.11+ is close but not identical; see the end).

### C1. Add MySQL support to the build

In `pom.xml`:

```xml
<dependency>
  <groupId>com.mysql</groupId>
  <artifactId>mysql-connector-j</artifactId>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-mysql</artifactId>
</dependency>
```

(Both versions are managed by Spring Boot's BOM except the pinned `flyway.version`; keep `flyway-mysql` on the same
version as `flyway-core`.) Hibernate picks its MySQL dialect from the connection, so no dialect setting is needed.

### C2. A `mysql` profile and its own migration folder

Create `src/main/resources/application-mysql.properties` by copying `application-postgres.properties` and change only:

```properties
spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/jobify}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.flyway.locations=classpath:db/migration/mysql
```

Keep everything else (`ddl-auto=validate`, H2 console off, demo seed off, docs off, env-only secrets).
Put the MySQL schema in `src/main/resources/db/migration/mysql/V1__baseline.sql`. The two folders are
independent, so each database keeps its own history and there is no "if mysql" logic.

### C3. Rewrite `V1__baseline.sql` for MySQL

Start from the PostgreSQL file (`db/migration/postgresql/V1__baseline.sql`). The schema was written with plain
SQL types on purpose, so the port is mostly mechanical:

| PostgreSQL (V1) | MySQL 8 | Notes |
|---|---|---|
| `bigint generated by default as identity primary key` | `bigint not null auto_increment primary key` | `app_users.id` |
| `integer generated by default as identity primary key` | `int not null auto_increment primary key` | `job_posts.post_id` |
| `varchar(255)` | `varchar(255)` | fine in utf8mb4 (1020 bytes, under the 3072-byte index limit) |
| `text` | `longtext` | MySQL `text` caps at 64 KB; descriptions can be longer. Hibernate maps `LONG32VARCHAR` to `longtext` |
| `double precision` | `double` | |
| `boolean` | `boolean` (alias of `tinyint(1)`) | Hibernate 7 may expect `bit(1)`; if `validate` complains, use the type it names |
| `timestamp(6)` | `datetime(6)` | **not** MySQL `timestamp`: that type is zone-converting and ends in 2038 |
| `date` | `date` | |
| `create index ... on t (a, b)` | same | |
| `-- comments` | same, but `--` needs a space after it | |

Also add to every `create table`: `engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci` (or set the
database default once with `create database jobify character set utf8mb4`).

Things that behave differently and need a decision:

- **Case sensitivity of text comparison.** PostgreSQL compares strings case-sensitively; MySQL's default collation
  does not. With the default, `uk_app_users_username` would treat `Bob` and `bob` as the same user, and unique
  names (`skills.name`) likewise. The app lower-cases for the login lock and search, so case-insensitive
  uniqueness is arguably *better*, but it is a behaviour change: check the register tests, and decide per
  column. If you want PostgreSQL behaviour, declare those columns `collate utf8mb4_bin`.
- **Reserved words.** None of the column names in V1 is reserved in MySQL 8 (`rate`, `status`, `type`, `role`,
  `password`, `name`, `description`, `location` are all non-reserved). If a later migration adds a column
  such as `rank` or `groups`, quote it with backticks.
- **Foreign keys** are checked by InnoDB immediately (no deferred constraints), which the copy tool already
  respects by copying parents first.
- **Indexes on foreign keys**: InnoDB creates them automatically; the explicit `create index` lines for FK
  columns are harmless duplicates there and can be dropped from the MySQL file.
- **Time zone.** `LocalDateTime` values are stored without a zone. Run the app and the MySQL session in the same zone
  (add `connectionTimeZone=SERVER` or `UTC` to the JDBC URL, and keep it the same on both sides), or
  timestamps shift by the offset during the copy.

### C4. Create the schema, then copy the rows

```bash
# MySQL: empty database, utf8mb4
mysql -h NEWHOST -u root -p -e "create database jobify character set utf8mb4; \
   create user 'jobify'@'%' identified by '...'; grant all on jobify.* to 'jobify'@'%'"

# start the app once with SPRING_PROFILES_ACTIVE=mysql so Flyway builds the schema; stop it
# (Hibernate 'validate' will tell you precisely which column type does not match: fix V1 and retry
#  on a fresh database; V1 has not been applied anywhere real yet, so editing it is fine at this point)

# copy, with the MySQL driver available to the tool
mkdir -p /opt/jdbc && cp mysql-connector-j-*.jar /opt/jdbc/
EXTRA_DRIVERS=/opt/jdbc COPY_SOURCE_PASSWORD=... COPY_TARGET_PASSWORD=... \
scripts/db-copy.sh --source-url=jdbc:postgresql://OLDHOST:5432/jobify --source-user=jobify \
                   --target-url="jdbc:mysql://NEWHOST:3306/jobify?connectionTimeZone=UTC" --target-user=jobify --truncate
```

(If you built the `mysql` profile into the jar in C1, the driver is already inside it and `EXTRA_DRIVERS` is not
needed.) The tool already handles the MySQL-specific parts: it reads the table list from MySQL's catalog (not a
schema), and it does not touch `AUTO_INCREMENT` because MySQL advances it by itself when explicit ids are inserted.

Then: copy the upload folder, start with the `mysql` profile, verify as in A5.

### C5. Things that will probably need attention on the first real run

1. A `validate` mismatch on `boolean` or `double` columns (see C3, fix the type in the MySQL V1).
2. Any SQL in the code that is PostgreSQL-flavoured. The application uses JPA/JPQL and derived queries, which
   are portable; there is **no** native SQL in the application code today (the H2-only startup helpers are
   off outside H2). If someone adds `@Query(nativeQuery = true)`, test it on both products.
3. Case-insensitive uniqueness (C3) surfacing as a duplicate-key error in a test that registers `Bob` and `bob`.
4. Running the whole suite against MySQL: copy `scripts/test-postgres.sh` to `scripts/test-mysql.sh` with the MySQL
   URL and `-Dspring.flyway.locations=classpath:db/migration/mysql`. That script is exactly the drift guard
   the PostgreSQL side has, and it is the quickest way to find the remaining differences.

MariaDB: use `jdbc:mariadb://` with the MariaDB driver and `flyway-mysql` (Flyway treats it as MySQL family);
`uuid`, JSON and collation defaults differ, so repeat C3's checks.

---

## Cut-over and rollback

A migration is safe if you can go back. The procedure above never modifies the source, so:

1. Stop the old app. Take the copy. Verify (A5). Only then change the production settings (`DB_URL`, ...) to
   the new database, and restart.
2. **Do not delete the old database or `data/` folder** until the new one has run correctly for a while.
   The old data is your rollback: stop the new app, point the settings back, start.
3. Anything written to the **new** database after cut-over is not in the old one. If you roll back after real
   users have written data, you have to decide what to do with it: roll back within minutes, not days.
4. The tool can be re-run: `--truncate` empties the target tables and copies again, so a failed or partial
   attempt is not a problem for the target (it never alters the source).

---

## Gotchas that actually happened while testing

| What you see | Why | What to do |
|---|---|---|
| `Database may be already in use ... The file is locked` | The H2 file is still open by a running app | Stop the app completely (check with `pgrep -af jobify`) |
| `The target already has data in [content_entries]` | Starting the app once to build the schema runs `ContentSeeder`, which inserts the default CMS texts | Expected. Re-run with `--truncate` |
| `The source has tables the target does not` | The target schema is older than the source, or the source is a different app version | Use the same build on both; for an old H2 file run step A1 first |
| `column ... does not exist in the target table` | The two schemas differ (for example a migration was not applied yet) | Start the current build once on the target so Flyway is up to date |
| `Flyway upgrade recommended: PostgreSQL 18.x is newer than this version of Flyway` | The Flyway managed by the old Spring Boot 4.0.0-M3 (11.13.1) predated PostgreSQL 18 | Already fixed: `flyway.version` is pinned to 11.20.3 in `pom.xml`. If you see it after an upgrade, bump it |
| `pg_dump: error: aborting because of server version mismatch` | `pg_dump` is older than the server | Use a `pg_dump` of the same or newer major version (for example from the `postgres:18` image) |
| App fails at start with `Schema-validation: wrong column type` | An entity changed without a migration (or the reverse) | Add the missing `V<n>__...sql`; never edit one that has been applied |
| `relation "..." already exists` when the app starts | Someone applied the SQL by hand, so Flyway has no history | Restore from backup, or on a throwaway database drop it and let the app create the schema |
| Users have to log in again after the move | `JWT_SECRET` changed | Use the old secret if you want sessions to survive |

---

## Adding a schema change later (so the next migration is routine)

1. Change the entity.
2. Add `src/main/resources/db/migration/postgresql/V2__short_description.sql` (next number, two underscores).
   Never edit a file that has been applied anywhere: Flyway stores a checksum and refuses to start if it changed.
3. Run `scripts/test-postgres.sh` against an empty PostgreSQL. It applies all migrations and starts Hibernate in
   `validate` mode, so a forgotten or wrong migration fails the suite. CI does the same on every pull request.
4. If you also keep a `mysql` folder, add the equivalent `V2` there.

H2 (dev) needs nothing: Hibernate's `ddl-auto=update` adds new columns itself. It does not *remove* or *alter*
columns, so a drop or rename needs a small helper like `LegacySchemaPatch` for existing dev files, or a fresh `data/`.

---

## What was and was not tested

| Item | Result |
|---|---|
| Flyway `V1__baseline.sql` and `V2__drop_job_rating.sql` on a real **PostgreSQL 18.6** | Applied; 20 tables. V2 also applied on top of a database already holding V1 and migrated rows |
| Application start with Hibernate `ddl-auto=validate` against the Flyway schema | Passes |
| Whole test suite (255 tests) against PostgreSQL 18.6 with Flyway + `validate` | 0 failures (5 skipped: the H2-only `LegacySchemaPatchTest`) |
| Whole test suite on H2 (the default) | 255 tests, 0 failures |
| H2 old-format dev DB to PostgreSQL 18 with the copy tool | All 20 tables match; login, job list, job create and register work afterwards; ids continue |
| PostgreSQL 18 to PostgreSQL 18 with the copy tool | All tables match |
| Copy tool unit tests (two in-memory databases) | 8 tests: order, values, identity, refusal, truncate, dry-run, rollback |
| `pg_dump` / `pg_restore` (Recipe B2) | **Not run** (client/server version mismatch in the sandbox); standard commands |
| PostgreSQL to MySQL (Recipe C) | **Not run** (no MySQL available); reasoned checklist only |
| `docker compose up`, `docker build` | **Not run** (no Docker daemon in the sandbox); `docker compose config` validates the file |
