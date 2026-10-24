# ---- Build stage: full JDK + Maven wrapper -------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Dependencies first: this layer is cached until pom.xml changes, so code-only changes rebuild fast.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline

COPY src src
# Tests run in CI against real PostgreSQL; the image build only packages the already-tested code.
RUN ./mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/stockflow-*.jar extract --layers --launcher --destination target/extracted

# ---- Runtime stage: JRE only, no build tools, no source -----------------------------------------
FROM eclipse-temurin:21-jre-alpine
# Links the published image (GHCR) to this repository.
LABEL org.opencontainers.image.source="https://github.com/bossshhh/stockflow" \
      org.opencontainers.image.description="StockFlow API (Spring Boot)"
WORKDIR /app

# Never run as root: if the app is compromised, the attacker does not own the container.
RUN addgroup -S stockflow && adduser -S stockflow -G stockflow
USER stockflow

# Spring Boot layers, least to most frequently changing, so image updates only ship what changed.
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

# 8080: API. 8081: actuator (health), kept on its own port so it can be firewalled separately.
EXPOSE 8080 8081

# Size the heap from the container's memory limit instead of the host's.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
