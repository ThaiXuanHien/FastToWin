FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S fasttowin && adduser -S -G fasttowin fasttowin
WORKDIR /opt/fasttowin
COPY server/build/install/server/ ./
RUN chown -R fasttowin:fasttowin /opt/fasttowin

USER fasttowin
EXPOSE 8080
ENTRYPOINT ["bin/server"]
