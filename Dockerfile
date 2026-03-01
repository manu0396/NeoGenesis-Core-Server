FROM gradle:8.11.1-jdk17 AS build
WORKDIR /workspace

COPY . .
RUN chmod +x gradlew && ./gradlew clean shadowJar --no-daemon

FROM eclipse-temurin:17-jre-jammy
RUN useradd --uid 10001 --create-home --shell /usr/sbin/nologin neogenesis
WORKDIR /app

COPY --from=build /workspace/build/libs/*-all.jar /app/neogenesis-core-server.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"
EXPOSE 8080 50051
USER neogenesis

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/neogenesis-core-server.jar"]
