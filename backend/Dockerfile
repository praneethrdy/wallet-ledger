# syntax=docker/dockerfile:1
# ---- build stage: full JDK + Maven wrapper ------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Dependencies first: this layer is cached until pom.xml changes, so code edits rebuild fast.
COPY .mvn .mvn
COPY mvnw pom.xml lombok.config ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
# Tests run in CI against Testcontainers; the image build only packages.
RUN ./mvnw -B -q -DskipTests -Djacoco.skip=true package \
    && cp target/wallet-backend-*.jar /app.jar

# ---- runtime stage: JRE only, non-root ----------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app -u 10001 app
WORKDIR /app
COPY --from=build --chown=app:app /app.jar app.jar
USER 10001

EXPOSE 8080
# Size the heap from the container's memory limit; crash fast (and get restarted) on OOM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=prod

HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness | grep -q '"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
