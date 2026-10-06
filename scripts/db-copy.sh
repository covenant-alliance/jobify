#!/usr/bin/env bash
# Runs the database copy tool (com.mcverse.jobify.tools.DatabaseCopyTool) from the packaged jar.
#   scripts/db-copy.sh --source-url=... --source-user=... --target-url=... --target-user=... [--truncate] [--dry-run]
# Passwords: --source-password / --target-password, or the COPY_SOURCE_PASSWORD / COPY_TARGET_PASSWORD variables.
# JAR: path to the jar (default target/jobify-0.0.1-SNAPSHOT.jar).
# EXTRA_DRIVERS: folder holding extra JDBC driver jars (for example the MySQL connector), see docs/DATABASE_MIGRATION.md.
set -euo pipefail
JAR="${JAR:-$(dirname "$0")/../target/jobify-0.0.1-SNAPSHOT.jar}"
[ -f "$JAR" ] || { echo "Jar not found: $JAR (run: mvn -DskipTests package, or set JAR=)" >&2; exit 2; }
EXTRA=()
[ -n "${EXTRA_DRIVERS:-}" ] && EXTRA=("-Dloader.path=${EXTRA_DRIVERS}")
exec java -cp "$JAR" -Dloader.main=com.mcverse.jobify.tools.DatabaseCopyTool "${EXTRA[@]}" \
    org.springframework.boot.loader.launch.PropertiesLauncher "$@"
