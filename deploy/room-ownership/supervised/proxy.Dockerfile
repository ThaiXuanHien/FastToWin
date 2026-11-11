FROM ghcr.io/shopify/toxiproxy@sha256:9378ed52a28bc50edc1350f936f518f31fa95f0d15917d6eb40b8e376d1a214e AS toxiproxy
# Cached Alpine runtime supplies local readiness/teardown inspection tools.
FROM redis@sha256:71885d8cc6e42f2bc0aa2eb853bfc4be200b5108a46ac50e117ae1df0531fb80
RUN addgroup -g 10001 fixture && adduser -D -H -u 10001 -G fixture fixture
COPY --from=toxiproxy /toxiproxy /toxiproxy
COPY --chmod=0555 supervisor /usr/local/bin/room-fault-supervisor
RUN mkdir -p /run/room-fault && chown 10001:10001 /run/room-fault && chmod 0700 /run/room-fault
ENV ROOM_FAULT_ROLE=proxy
USER 10001:10001
EXPOSE 15432 25432 8181
ENTRYPOINT ["/usr/local/bin/room-fault-supervisor"]
