FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S fasttowin && adduser -S -G fasttowin fasttowin
WORKDIR /opt/fasttowin
COPY --chown=fasttowin:fasttowin distribution/ ./
# Public ephemeral staging CA only. Never COPY the CA private key or SMTP key.
COPY staging-mail-ca.crt /tmp/staging-mail-ca.crt
RUN cp "$JAVA_HOME/lib/security/cacerts" /opt/fasttowin/staging-cacerts \
    && keytool -importcert -noprompt -alias fasttowin-staging-mail \
       -file /tmp/staging-mail-ca.crt -keystore /opt/fasttowin/staging-cacerts \
       -storepass changeit \
    && chmod +x bin/server
ENV FASTTOWIN_ENV=prod \
    SERVER_HOST=0.0.0.0 \
    PORT=8080 \
    FASTTOWIN_TRUST_PROXY_HEADERS=true \
    JAVA_OPTS="-Xms64m -Xmx512m -XX:MaxMetaspaceSize=192m -XX:+ExitOnOutOfMemoryError -Djavax.net.ssl.trustStore=/opt/fasttowin/staging-cacerts -Djavax.net.ssl.trustStorePassword=changeit"
USER fasttowin
EXPOSE 8080
ENTRYPOINT ["bin/server"]
