# =============================================================================
# Sprintly Backend — Multi-Stage Dockerfile
# =============================================================================
#
# WHY MULTI-STAGE?
#   Stage 1 (builder): uses a full Maven + JDK image to compile the project.
#     Image size: ~500MB. Contains Maven, full JDK, source code.
#   Stage 2 (runtime): uses a minimal JRE-only Alpine image.
#     Image size: ~90MB. Contains ONLY the compiled JAR + JRE.
#
#   Without multi-stage: your final image would be 500MB+ and contain
#   Maven, all source code, build tools — none of which are needed at runtime.
#   With multi-stage: final image is ~90MB and contains nothing sensitive.
#
# WHY eclipse-temurin?
#   Temurin (formerly AdoptOpenJDK) is the most widely used, production-grade
#   OpenJDK distribution. It is free, actively maintained, and passes the
#   Java SE TCK. The Alpine variant keeps the image small.
#
# WHY dependency caching (COPY pom.xml first)?
#   Docker builds layer by layer. Each COPY/RUN creates a new layer.
#   Docker caches layers — if a layer's inputs haven't changed, it reuses
#   the cached result instead of re-running.
#
#   By copying only the pom.xml files first and running mvn dependency:go-offline,
#   we create a layer that contains all downloaded dependencies. This layer
#   is only invalidated when a pom.xml changes. Source code changes (which
#   happen constantly) don't invalidate the dependency cache — so rebuilds
#   after code changes skip the slow dependency download step.
#
#   Without this trick: every rebuild downloads all dependencies (~200MB).
#   With this trick: only the first build downloads them. Rebuilds take seconds.
#
# =============================================================================

# ── Stage 1: Builder ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /app

# ── Dependency cache layer ─────────────────────────────────────────────────
# Copy ONLY pom.xml files first (source code not copied yet).
# "go-offline" downloads all Maven dependencies into the local cache.
# This layer is cached and reused on every rebuild UNLESS a pom.xml changes.
# Result: code changes don't trigger a full dependency re-download.
COPY pom.xml .
COPY sprintly-common/pom.xml      sprintly-common/
COPY sprintly-auth/pom.xml        sprintly-auth/
COPY sprintly-user/pom.xml        sprintly-user/
COPY sprintly-task/pom.xml        sprintly-task/
COPY sprintly-notification/pom.xml sprintly-notification/
COPY sprintly-gateway/pom.xml     sprintly-gateway/
COPY sprintly-cli/pom.xml         sprintly-cli/

RUN mvn dependency:go-offline -B --no-transfer-progress

# ── Source code layer ─────────────────────────────────────────────────────
# Now copy all source code. This layer is invalidated on every code change,
# but the dependency layer above is still cached — so Maven doesn't re-download.
COPY sprintly-common/      sprintly-common/
COPY sprintly-auth/        sprintly-auth/
COPY sprintly-user/        sprintly-user/
COPY sprintly-task/        sprintly-task/
COPY sprintly-notification/ sprintly-notification/
COPY sprintly-gateway/     sprintly-gateway/
COPY sprintly-cli/         sprintly-cli/

# Build all modules. Skip tests here — run tests in CI separately.
# -B = batch mode (no interactive prompts, cleaner logs)
# --no-transfer-progress = suppress download progress bars in logs
RUN mvn clean package -DskipTests -B --no-transfer-progress

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
# Start fresh from a minimal JRE-only Alpine image.
# NOTHING from the builder stage carries over except what we explicitly copy.
# No Maven. No source code. No build tools. Just the JRE + our JAR.
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create a non-root user to run the application.
# WHY: Running as root inside a container is a security risk.
# If the app is compromised, the attacker gets root inside the container.
# Running as a dedicated user limits the blast radius.
RUN addgroup -S sprintly && adduser -S sprintly -G sprintly

# Copy ONLY the compiled fat JAR from the builder stage.
# The fat JAR (produced by spring-boot-maven-plugin repackage) contains
# all dependencies bundled — no classpath management needed at runtime.
COPY --from=builder /app/sprintly-gateway/target/sprintly-gateway-*.jar app.jar

# Give ownership of the app directory to the sprintly user
RUN chown -R sprintly:sprintly /app

# Switch to non-root user
USER sprintly

# Expose the port the Spring Boot app listens on
EXPOSE 8080

# Health check — Docker will periodically call this to know if the container
# is healthy. The /actuator/health endpoint returns 200 when Spring Boot
# is fully started and all health indicators pass (including DB connection).
# --interval=30s  check every 30 seconds
# --timeout=10s   wait up to 10s for a response
# --retries=5     mark unhealthy after 5 consecutive failures
# --start-period=60s  don't count failures during the first 60s (startup time)
HEALTHCHECK --interval=30s --timeout=10s --retries=5 --start-period=60s \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# JVM tuning flags:
# -XX:+UseContainerSupport  — makes JVM respect Docker CPU/memory limits
#                             (without this, JVM reads host machine resources
#                              and may allocate too much heap)
# -XX:MaxRAMPercentage=75.0 — use up to 75% of container memory as heap
#                             (leaves 25% for OS, metaspace, thread stacks)
# -Djava.security.egd=...   — faster random number generation (important for
#                             JWT signing performance in containers)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]