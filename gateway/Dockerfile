FROM gradle:8.10.2-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :gateway:installDist --no-daemon

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /workspace/gateway/build/install/gateway /app
ENV GATEWAY_ID=gw-1
ENV TENANT_ID=tenant-1
ENV SERVER_HOST=core-server
ENV SERVER_PORT=50051
ENV SERVER_TLS=false
ENTRYPOINT ["/app/bin/gateway"]
