FROM eclipse-temurin:17-jre-alpine@sha256:c4a33e6ddb738fe4be0a28a613ea0940cec42d25d7263c3e2238c43354cb19b0
RUN addgroup -S runner && adduser -S -G runner runner
WORKDIR /opt/runner
COPY classes/ classes/
COPY lib/ lib/
RUN mkdir receipts && chown runner:runner receipts
USER runner
ENTRYPOINT ["timeout", "900", "java", "-Xmx384m", "-cp", "classes:lib/*", "com.hienthai.fastowin.server.ownership.RailwayRoomSmokeKt"]
