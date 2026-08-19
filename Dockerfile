# Stage 1: Build
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apt-get update && apt-get install -y maven && \
    mvn package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre
# The JRE image ships no HTTP client, so the HEALTHCHECK below would report unhealthy
# forever without this. curl is the smallest thing that satisfies it.
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Run unprivileged. A container process running as root shares uid 0 with the host, so any
# container escape or writable bind mount starts from full privilege.
RUN useradd --system --uid 10001 --no-create-home appuser \
    && mkdir -p /app/uploads \
    && chown -R appuser:appuser /app
USER appuser

# Driver documents and trip photos land here. Declared as a volume so they survive the
# container: without it every redeploy silently discards uploaded licences and Aadhaar scans.
VOLUME ["/app/uploads"]

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD curl -f http://localhost:8080/api/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
