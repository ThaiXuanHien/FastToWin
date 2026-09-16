FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S fasttowin && adduser -S -G fasttowin fasttowin
WORKDIR /opt/fasttowin
COPY --chown=fasttowin:fasttowin distribution/ ./
RUN chmod +x bin/server

ENV FASTTOWIN_ENV=prod \
    SERVER_HOST=0.0.0.0 \
    PORT=8080 \
    FASTTOWIN_TRUST_PROXY_HEADERS=true \
    JAVA_OPTS="-Xms64m -Xmx512m -XX:MaxMetaspaceSize=192m -XX:+ExitOnOutOfMemoryError"
USER fasttowin
EXPOSE 8080
ENTRYPOINT ["bin/server"]
