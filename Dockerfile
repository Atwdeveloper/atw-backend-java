# ---- Build stage: compile + package the Spring Boot jar ----
# Java 25 (matches <java.version>25</java.version> in pom.xml).
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# curl is needed by the Maven wrapper (distributionType=only-script) to fetch Maven.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Resolve dependencies first (better layer caching: only re-runs when pom changes).
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Build the application.
COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# ---- Runtime stage: slim JRE image that just runs the jar ----
# If the 25-jre tag is unavailable in your registry, use eclipse-temurin:25-jdk.
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Run as a non-root user.
RUN groupadd --system app && useradd --system --gid app app

# The spring-boot-maven-plugin produces a single executable jar in target/.
COPY --from=build /workspace/target/*.jar app.jar
USER app

# App listens on 8080 (overridable via SERVER_PORT).
EXPOSE 8080

# JAVA_OPTS lets DevOps pass JVM flags (heap %, GC, etc.) without changing the image.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
