FROM postgres@sha256:123b17998ccaf47e53b502e8c6a1e79afd8f579c6f3eec83c563da026f957395
COPY --chmod=0555 supervisor /usr/local/bin/room-fault-supervisor
RUN mkdir -p /var/lib/postgresql/18/docker && chown -R postgres:postgres /var/lib/postgresql/18
ENV ROOM_FAULT_ROLE=pg PGDATA=/var/lib/postgresql/18/docker
USER postgres
EXPOSE 8181
ENTRYPOINT ["/usr/local/bin/room-fault-supervisor"]
