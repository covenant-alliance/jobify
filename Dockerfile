# Jobify back end: multi-stage build, small non-root runtime image.
# Build:  docker build -t jobify-backend .
# Run:    see docker-compose.yml and docs/DEPLOYMENT.md (needs PostgreSQL and a JWT_SECRET).

# ---- build stage -----------------------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Resolve dependencies first so this layer is cached until the pom changes.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Tests run in CI (see .github/workflows/ci.yml); the image build only packages.
RUN mvn -B -q -DskipTests package && cp target/jobify-*.jar /build/app.jar

# ---- runtime stage -----------------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre
# curl is only for the container health check.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system jobify && useradd --system --gid jobify --home-dir /app --shell /usr/sbin/nologin jobify \
    && mkdir -p /app /data/uploads && chown -R jobify:jobify /app /data
WORKDIR /app
COPY --from=build --chown=jobify:jobify /build/app.jar /app/app.jar

USER jobify
ENV SPRING_PROFILES_ACTIVE=postgres \
    UPLOAD_DIR=/data/uploads \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# Resumes are stored here: mount a volume and back it up together with the database.
VOLUME /data/uploads
EXPOSE 9080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:9080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
