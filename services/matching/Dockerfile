# Matching service: multi-stage build, small non-root runtime image.
# Build context is services/matching (it holds both modules):
#   docker build -t jobify-matching services/matching
# Run: see docker-compose.yml (profile "matching") and docs/MATCHING_SERVICE.md. NOT yet built or run by the author.

# ---- build stage -----------------------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY matching-api/pom.xml matching-api/
COPY matching-service/pom.xml matching-service/
# Resolve dependencies first so this layer is cached until a pom changes.
RUN mvn -B -q dependency:go-offline || true
COPY matching-api matching-api
COPY matching-service matching-service
# Tests run in CI (matching-service.yml); the image build only packages.
RUN mvn -B -q -DskipTests package && cp matching-service/target/matching-service-*.jar /build/app.jar

# ---- runtime stage -----------------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre
# curl is only for the container health check.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system matching && useradd --system --gid matching --home-dir /app --shell /usr/sbin/nologin matching \
    && mkdir -p /app && chown -R matching:matching /app
WORKDIR /app
COPY --from=build --chown=matching:matching /build/app.jar /app/app.jar

USER matching
# Any profile except dev: the service then refuses to start without MATCHING_TOKEN.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# 9090 gRPC (internal network only, never publish it), 9091 health over HTTP.
EXPOSE 9090 9091

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:9091/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
