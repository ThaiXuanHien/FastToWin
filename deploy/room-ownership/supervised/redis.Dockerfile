FROM redis@sha256:71885d8cc6e42f2bc0aa2eb853bfc4be200b5108a46ac50e117ae1df0531fb80
COPY --chmod=0555 supervisor /usr/local/bin/room-fault-supervisor
COPY --chmod=0555 redis-entrypoint.sh /usr/local/bin/room-fixture-redis
ENV ROOM_FAULT_ROLE=redis
USER redis
EXPOSE 8181
ENTRYPOINT ["/usr/local/bin/room-fault-supervisor"]
