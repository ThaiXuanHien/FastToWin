FROM eclipse-temurin:17-jre-alpine@sha256:c4a33e6ddb738fe4be0a28a613ea0940cec42d25d7263c3e2238c43354cb19b0
WORKDIR /app
COPY --chown=10001:10001 . /app/
USER 10001:10001
ENTRYPOINT ["java", "-Xmx512m", "-cp", "/app/lib/*", "com.hienthai.fastowin.server.ratelimit.RedisAcceptanceRunner"]
