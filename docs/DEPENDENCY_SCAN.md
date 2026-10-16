# OWASP dependency-check: guided local run

A manual, step-by-step check of every library Jobify ships against the public vulnerability databases (the NVD).
Each step says **what to run**, **what you should see**, and **what it means if you do not**. Do the steps in order and
tick them off; write the results into the table at the end.

> **Status of this guide.** Written 2026-10-07 from the plugin's documented behaviour. In the environment it was
> written in, only step 2's first half could be confirmed (plugin `13.0.0` is on Maven Central, resolves and starts);
> the database download and the scan itself were **not** run there. If your output differs from "you should see",
> that is information: note it and tell me, and I will correct this guide.

Nothing here changes the project: no `pom.xml` edit, no commit. The plugin is called by its full name on the command
line. (Wiring it into CI is a separate, later story: see "After the manual run" at the end.)

## Stories this guide belongs to

Epic #77 (T9). The manual steps below are the acceptance tests of #78 to #81:

| Story | Issue | Guide steps | Owner |
|---|---|---|---|
| T9.1 NVD key and database | #78 | 0, 2, 3 | Product Owner |
| T9.2 Canary | #79 | 4 | Product Owner |
| T9.3 Baseline scan | #80 | 1, 5, 6, 10 | Product Owner |
| T9.4 Triage with decisions | #81 | 7 | Product Owner decides, back end implements |
| T9.5 Suppression file | #82 | 8 | back end |
| T9.6 Maven profile `security-scan` | #83 | 9 | back end |
| T9.7 Weekly CI scan | #84 | (after the manual run) | back end |
| T9.8 Response policy | #85 | (decision) | Product Owner |
| T9.9 Alert issue on failure | #86 | (optional) | back end |

## What it does and does not tell you

- It lists the libraries in the build (names and versions, including the ones pulled in indirectly) and matches them
  against known CVEs. Each match has a severity score (CVSS, 0 to 10).
- A match is a **candidate**, not a verdict. Matching is by name and version and produces false positives; a real
  finding may also not be reachable from our code. Step 7 is how you decide.
- It does not look at our own code (that is `security-review`) and not at the front end.

## 0. Prerequisites

| Need | Check | You should see |
|---|---|---|
| Java 21 | `java -version` | `openjdk version "21...` |
| Maven 3.9+ | `mvn -v` | `Apache Maven 3.9...` |
| About 3 GB free disk | `df -h ~` | the vulnerability database and its cache live under `~/.m2` |
| An **NVD API key** (free) | request one at https://nvd.nist.gov/developers/request-an-api-key; the key arrives by email | a UUID-like string |

Why the key: without it the NVD throttles the download to a crawl (hours, and often fails with `403`/`429`). With it the
first download takes roughly 10 to 40 minutes.

Put the key in your shell, **never in a file in the repository**:

```bash
export NVD_API_KEY=<your key>
```

You should be able to run `echo ${#NVD_API_KEY}` and see a number (the length, about 36), not `0`.

## 1. Baseline: the build is healthy

```bash
mvn -q clean verify
```

**You should see:** no output and exit code 0 (`echo $?` prints `0`); with `-q` Maven only speaks on failure.
Without `-q` the last lines are `Tests run: ... Failures: 0, Errors: 0` and `BUILD SUCCESS`.

**If not:** stop. A scan of a broken build proves nothing; fix or report that first.

## 2. The scanner runs at all

```bash
mvn org.owasp:dependency-check-maven:13.0.0:help
```

**You should see:** Maven downloads the plugin (first time only) and prints `dependency-check-maven 13.0.0` with a list
of goals including `check`, `aggregate`, `update-only` and `purge`, then `BUILD SUCCESS`.

**If not:** `Could not find artifact` means no access to Maven Central (proxy/VPN); fix that first. If a newer
version exists, use it consistently in every command below and note it in the results table.

## 3. First download of the vulnerability database

```bash
mvn org.owasp:dependency-check-maven:13.0.0:update-only -DnvdApiKey=$NVD_API_KEY
```

**You should see:** lines such as `Checking for updates`, `NVD API has N records in this update`, a progress counter
that climbs for many minutes, then `BUILD SUCCESS`. The data is stored in
`~/.m2/repository/org/owasp/dependency-check-data/` and is several hundred MB.

**Takes:** 10 to 40 minutes with a key, only the first time.

**If not:**
- **`Invalid API Key: XXXXX-*****-XXXXX`** (seen on a first run, 2026-10-15): the NVD answered, so the network and the
  plugin are fine and **only the key is refused**. Check, in this order:
  1. *Is it the right kind of string?* An NVD key is a 36-character UUID such as `8f3c2a1e-0b4d-4c7a-9e55-1d2f6a7b8c90`.
     Run `echo ${#NVD_API_KEY}`: it must print `36`. Anything else means you copied something else (the request id, a
     part of the email, a key with quotes or spaces around it).
  2. *Hidden characters:* `printf '%s' "$NVD_API_KEY" | od -c | tail -3` must show only hex digits and dashes, no `\r`
     (Windows line ending), no spaces, no quotes. Re-set it with the key typed in single quotes:
     `export NVD_API_KEY='the-key-here'`.
  3. *Activated?* The NVD only accepts a key after you click the **activation link** in the email it sends (the link is
     valid for 7 days, and requesting a key twice makes the first one invalid). If you never clicked it, do so now; if
     the email is gone, request the key again and use only the newest one.
  4. *Test the key without Maven:*
     `curl -s -D - -o /dev/null -H "apiKey: $NVD_API_KEY" "https://services.nvd.nist.gov/rest/json/cves/2.0?resultsPerPage=1" | head -5`
     **You should see** `HTTP/2 200`. **`HTTP/2 404` with `message: Invalid apiKey.`** is how the NVD answers for a key it
     does not know: a 36-character value of the right shape that is not (yet) a valid key. Typical reasons: the key was
     never activated (click the link in the NVD email; the page you reach shows the real key), the UUID copied was the
     activation link's own id and not the key, or a newer request replaced it. Fix that before running Maven again.
  Do **not** work around it by running without a key: that works but takes hours and often fails.
- `403` from the NVD on its own: same checks as above; if the key is fine, wait and retry (the NVD sometimes blocks a burst).
- `429`/rate-limit messages that keep repeating: wait and run again; it resumes.
- Certificate or proxy errors: your network blocks `services.nvd.nist.gov`; try another network.
- `NVD API request failures`, then `BUILD FAILURE`: run the same command again before assuming anything is wrong; it
  continues where it stopped.

## 4. Canary test: prove the scanner can find something

A scanner that reports "no vulnerabilities" is only believable if it demonstrably finds one when it exists. We test
it on a throw-away project containing a library with a famously critical flaw (Log4Shell, CVE-2021-44228, score 10.0).
This does **not** touch the Jobify repository.

```bash
mkdir -p /tmp/odc-canary && cd /tmp/odc-canary
cat > pom.xml <<'XML'
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>canary</groupId><artifactId>canary</artifactId><version>1</version>
  <dependencies>
    <dependency>
      <groupId>org.apache.logging.log4j</groupId><artifactId>log4j-core</artifactId><version>2.14.1</version>
    </dependency>
  </dependencies>
</project>
XML
mvn org.owasp:dependency-check-maven:13.0.0:check -DnvdApiKey=$NVD_API_KEY -DfailBuildOnCVSS=7
```

**You should see:**
- a warning block naming `log4j-core-2.14.1.jar` with `CVE-2021-44228` and a severity of `CRITICAL`;
- `BUILD FAILURE` with `One or more dependencies were identified with vulnerabilities that have a CVSS score greater
  than or equal to '7.0'`;
- a report at `/tmp/odc-canary/target/dependency-check-report.html`.

**This failure is the expected, correct result.** Open the HTML report once to see what a finding looks like.

**If instead you get `BUILD SUCCESS` and no finding:** the scanner is not working (the database is empty or
incomplete). Do **not** trust any later "clean" result; repeat step 3 and, if it persists, tell me.

Clean up: `cd - && rm -rf /tmp/odc-canary`.

## 5. First real scan of Jobify (report only, never fails)

```bash
cd /path/to/jobify          # the folder with pom.xml
mvn org.owasp:dependency-check-maven:13.0.0:check -DnvdApiKey=$NVD_API_KEY \
    -DfailBuildOnCVSS=11 -Dformats=HTML,JSON
```

(`failBuildOnCVSS=11` can never be reached, so this run reports and does not fail: you want to **look** first.)

**You should see:**
- `Analysis Started` ... `Analysis Complete`, then possibly a list of
  `One or more dependencies were identified with known vulnerabilities in jobify:` with entries like
  `spring-security-web-6.x.jar (pkg:maven/org.springframework.security/spring-security-web@6.x) : CVE-...`;
- `BUILD SUCCESS`;
- `target/dependency-check-report.html` and `target/dependency-check-report.json`.

**Sanity check that it scanned the right thing:** in the HTML report the "Dependencies scanned" count should be in the
order of 100 to 200 and the list should contain `spring-boot`, `hibernate-core`, `tomcat-embed-core`,
`jackson-databind`, `jjwt-*`, `springdoc-*`, `flyway-core`, `postgresql` and `h2`. A count near 0 or a missing family
means the scan did not see the build (run `mvn compile` first and repeat).

Test-scope libraries are skipped by default, which is what we want: they do not ship.

**If you see errors about `OSS Index`** (a second data source that needs a Sonatype login): add
`-DossindexAnalyzerEnabled=false` to the command. The NVD alone is enough for this check. If you see `RetireJS` or
`Node` warnings, add `-DretireJsAnalyzerEnabled=false -DnodeAnalyzerEnabled=false`; we ship no JavaScript.

## 6. List the findings in one screen

```bash
jq -r '.dependencies[] | select(.vulnerabilities != null)
       | .fileName as $f | .vulnerabilities[]
       | [$f, .name, .severity, (.cvssv3.baseScore // .cvssv2.score // "n/a")] | @tsv' \
   target/dependency-check-report.json | sort -k4 -nr
```

(Needs `jq`. If a field name differs in your plugin version, open the JSON once and adjust.)

**You should see:** one line per finding: library file, CVE id, severity, score, highest first. An empty output is a
valid result ("no known vulnerabilities") **only because step 4 passed**.

## 7. Decide what to do with each finding

For every line with a score of **7.0 or more** (and any lower one that looks relevant), work through these questions and
write the answer in the results table.

1. **Is it really our library?** Open the CVE link in the HTML report. Is the product named the same as the jar?
   Frequent false positives: a CVE for a different product with a similar name, or one for a client in another language.
   *Yes, real* -> go on. *No* -> suppress (step 8) with the reason "wrong product".
2. **How did it get in?**
   ```bash
   mvn dependency:tree -Dincludes=<groupId>:<artifactId>
   ```
   *You should see* the path from our `pom.xml` to the library, for example `spring-boot-starter-web -> tomcat-embed-core`.
   The first line under `com.mcverse:jobify` tells you which dependency to bump.
3. **Is a fixed version available and compatible?** The CVE page lists "fixed in". For libraries managed by Spring Boot,
   first check whether a newer 4.0.x patch release brings it (`mvn versions:display-parent-updates`, or look at the
   Boot release notes). If not, override the version in `pom.xml` `<properties>` (Boot documents the property name per
   library, for example `tomcat.version`).
   - After any change: `mvn clean verify` **and** `scripts/test-postgres.sh` (see `CLAUDE.md`, Hazards).
4. **Does it apply to how we use it?** Many CVEs need a feature we never enable (for example a parser we do not
   use, or an admin console that is off in the `postgres` profile). If you can say precisely why it is unreachable,
   suppress it with that reason and an expiry date (step 8). If you cannot say why, treat it as real.
5. **Decision, one of:** *Upgrade* (preferred), *Suppress* (false positive or unreachable, with reason and expiry),
   *Accept for now* (real, no fix yet; write a date to look again).

**Expected result of this step:** every finding at 7.0 or above has a written decision; none is left "to look at later".

## 8. Test the suppression mechanism (only if step 7 produced a suppress decision, or to learn it)

Create `dependency-check-suppressions.xml` next to `pom.xml` (not committed yet):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">
  <suppress until="2027-01-01Z">
    <notes>REASON, who decided, date, link to the discussion.</notes>
    <packageUrl regex="true">^pkg:maven/GROUP/ARTIFACT@.*$</packageUrl>
    <cve>CVE-XXXX-NNNN</cve>
  </suppress>
</suppressions>
```

Re-run step 5 with `-DsuppressionFiles=dependency-check-suppressions.xml`.

**You should see:** that single CVE no longer appears in the console list or the report (the HTML report has a
"Suppressed vulnerabilities" section showing it with your note), and every other finding is unchanged.

**Negative test** (proves the expiry works): change `until="2027-01-01Z"` to a past date, e.g. `until="2020-01-01Z"`,
and run again. **You should see** the CVE **reappear**. Put the real date back.

**Rule:** a suppression always has a reason, an author/date in `notes`, and an `until` date at most 6 to 12 months out,
so it is looked at again.

## 9. The gate: would a build fail at 7.0 or above?

```bash
mvn org.owasp:dependency-check-maven:13.0.0:check -DnvdApiKey=$NVD_API_KEY \
    -DfailBuildOnCVSS=7 -DsuppressionFiles=dependency-check-suppressions.xml
```
(leave the `-DsuppressionFiles` out if you made no suppression file)

**You should see, depending on the state after step 7:**
- all decisions made and fixes applied: `BUILD SUCCESS`, no vulnerability block. **This is the target state.**
- something still open: `BUILD FAILURE`, `... CVSS score greater than or equal to '7.0'`, and the open items listed.
  That is not a tool problem: go back to step 7 for those.

### The same gate as one short command (since story T9.6, #83)

The `pom.xml` now has a profile `security-scan` with the plugin version pinned in one place, the 7.0 gate, the suppression
file and the unused analyzers (OSS Index, RetireJS, Node) already switched off:

```bash
export NVD_API_KEY=...            # the same key
mvn -B -Psecurity-scan verify -DskipTests
```

**You should see** the same results as the long command in step 9. **Without the key** it stops immediately with
`NVD_API_KEY is not set. Request a free key at ...` instead of starting a throttled download. **Without
`-Psecurity-scan`** nothing changes: `mvn verify` does not scan.

## 10. A fast second run

```bash
time mvn org.owasp:dependency-check-maven:13.0.0:check -DnvdApiKey=$NVD_API_KEY -DfailBuildOnCVSS=7
```

**You should see:** much quicker than the first scan (usually 1 to 3 minutes): the database is cached and only
refreshed if older than 4 hours. If it re-downloads everything each time, the data directory is not persisting; check
that `~/.m2/repository/org/owasp/dependency-check-data/` still exists.

## 11. Record the result

Fill this in and send it (or paste it into the issue):

| Item | Value |
|---|---|
| Date | |
| Plugin version | 13.0.0 (or what you used) |
| Database last updated (top of the HTML report) | |
| Jobify commit (`git rev-parse --short HEAD`) | |
| Canary test (step 4) found CVE-2021-44228 | yes / no |
| Dependencies scanned | |
| Findings at 7.0 or above (count) | |
| Findings below 7.0 (count) | |
| Each decision (library, CVE, score, upgrade / suppress / accept, reason) | |
| Final gate (step 9) | success / failure |
| Time of the first scan and of the repeat scan | |

## Response policy (story T9.8, #85)

> **Status: confirmed by the Product Owner on 2026-10-15** (issue #85). These are the working rule.

| CVSS score | Meaning | Decide within | Fix or suppress within | Who |
|---|---|---|---|---|
| 9.0 to 10 | critical | 2 working days | **7 days** | Product Owner decides, back end implements |
| 7.0 to 8.9 | high | 5 working days | **30 days** | same |
| 4.0 to 6.9 | medium | at the next planned upgrade | next planned upgrade | back end |
| below 4.0 | low | not tracked individually | with the next upgrade | back end |

"Decide" means one of the three outcomes in step 7: upgrade, suppress (with reason and expiry), accept (with a date).
The clock starts when the weekly scan (or a manual one) first reports the finding and someone confirms it is real.

1. **Who triages:** the Product Owner decides; the back-end session does the upgrade or writes the suppression.
2. **Suppressing:** only with a reason and an `until` date at most 12 months ahead in `dependency-check-suppressions.xml`;
   every change to that file is reviewed by the Product Owner. Nothing is suppressed "because it is noisy".
3. **What blocks a release:** an unsuppressed finding of 7.0 or above in a library we ship. Test-scope libraries are
   ignored (they are not scanned).
4. **Emergency path:** a flaw that is being exploited in the wild (for example in Spring, Tomcat or the JWT library),
   whatever its score: patch the same day, release out of cycle, and tell the front-end session if the API is affected.
5. **The weekly alert:** a failing weekly scan opens (or comments on) an issue labelled `dependency-scan`; the policy
   above applies from the day it is opened. A later clean scan comments "clean as of ..." and a human closes it.

## Done when

- [ ] Step 4 (canary) failed the build and named CVE-2021-44228.
- [ ] Step 5 scanned the right libraries (count and names as described).
- [ ] Every finding at 7.0 or above has a decision, and the gate in step 9 is `BUILD SUCCESS` (or the open items are
      written down with an owner and a date).
- [ ] Suppressions, if any, have a reason and an expiry date, and the negative test in step 8 reintroduced one.
- [ ] The results table is filled in.

## After the manual run (not part of this guide)

**Built (stories T9.6, T9.7, T9.9):** the Maven profile above, and `.github/workflows/dependency-scan.yml`: weekly
(Mondays 04:17 UTC) and on manual trigger (`Actions`, `Dependency scan`, `Run workflow`), never on pull requests. It
needs the repository secret **`NVD_API_KEY`** (Settings, Secrets and variables, Actions): without it the job stops at
once with a clear message. It caches the vulnerability database between runs, uploads the HTML and JSON report as an
artifact for 90 days, writes the findings table into the run summary, fails (red) on any unsuppressed finding of 7.0 or
above, and opens or comments on a `dependency-scan` issue. Dependabot (already enabled) opens upgrade pull requests.

**To prove the workflow works** (not yet done: it has never run on GitHub), after adding the secret:
1. Run it once from `master` via `Run workflow`. **Expected:** green, summary "0 finding(s) of 7.0 or above" (or your
   known findings), artifact `dependency-check-report` attached. The first run takes long (database download).
2. On a scratch branch add `org.apache.logging.log4j:log4j-core:2.14.1` to `pom.xml`, run the workflow on that branch.
   **Expected:** red, summary table lists `CVE-2021-44228 | CRITICAL | 10.0`, and an issue
   `Dependency scan: N findings of 7.0 or above (week YYYY-WW)` labelled `dependency-scan` appears. Run it again:
   **Expected:** a comment on that issue, no second issue. Delete the branch and close the issue.
3. Run it on `master` again. **Expected:** green, and a "Clean as of ..." comment on any still-open scan issue.
