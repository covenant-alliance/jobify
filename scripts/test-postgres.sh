#!/usr/bin/env bash
# Runs the whole test suite against a real PostgreSQL instead of in-memory H2, with Flyway migrations applied and
# Hibernate in "validate" mode. This is the guard against drift between the entities and db/migration/postgresql.
#
#   TEST_DB_URL=jdbc:postgresql://localhost:5432/jobify_test TEST_DB_USER=jobify TEST_DB_PASSWORD=secret scripts/test-postgres.sh
#
# The database must be EMPTY and disposable (create it fresh each run: the tests write demo data into it).
set -euo pipefail
: "${TEST_DB_URL:?Set TEST_DB_URL, e.g. jdbc:postgresql://localhost:5432/jobify_test}"
exec mvn -B test \
    -Dspring.datasource.url="$TEST_DB_URL" \
    -Dspring.datasource.username="${TEST_DB_USER:-jobify}" \
    -Dspring.datasource.password="${TEST_DB_PASSWORD:-}" \
    -Dspring.jpa.hibernate.ddl-auto=validate \
    -Dspring.flyway.enabled=true \
    -Dspring.flyway.locations=classpath:db/migration/postgresql "$@"
