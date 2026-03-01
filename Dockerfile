FROM gradle:8.10.2-jdk21 AS build
WORKDIR /workspace

COPY . .
RUN chmod +x gradlew && ./gradlew clean shadowJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --uid 10001 --create-home --shell /usr/sbin/nologin neogenesis
WORKDIR /app

COPY --from=build /workspace/build/libs/*-all.jar /app/neogenesis-core-server.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"
ENV PORT=8080
EXPOSE 8080 50051
USER neogenesis

HEALTHCHECK --interval=10s --timeout=3s --retries=10 CMD sh -c "curl -fsS http://localhost:${PORT:-8080}/health/ready || exit 1"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/neogenesis-core-server.jar"]
