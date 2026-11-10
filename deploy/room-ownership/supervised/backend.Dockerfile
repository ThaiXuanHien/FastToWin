FROM eclipse-temurin@sha256:c4a33e6ddb738fe4be0a28a613ea0940cec42d25d7263c3e2238c43354cb19b0
RUN addgroup -S fasttowin && adduser -S -G fasttowin fasttowin
WORKDIR /opt/fasttowin
COPY --chown=fasttowin:fasttowin distribution/ ./
RUN chmod +x bin/server
COPY --chmod=0555 supervisor /usr/local/bin/room-fault-supervisor
ENV FASTTOWIN_ENV=prod SERVER_HOST=0.0.0.0 PORT=8080 FASTTOWIN_TRUST_PROXY_HEADERS=true \
    JAVA_OPTS="-Xms64m -Xmx512m -XX:MaxMetaspaceSize=192m -XX:+ExitOnOutOfMemoryError"
USER fasttowin
EXPOSE 8080 8181
ENTRYPOINT ["/usr/local/bin/room-fault-supervisor"]
