# Public distroless static Debian 13 nonroot; includes system CA roots.
FROM gcr.io/distroless/static-debian13@sha256:25ac8f0c3b2334a9eb05452266bd4697864a8c8ef0ef71e5e6480d7f92868329
COPY --chmod=0555 redis-monitor /redis-monitor
USER 65532:65532
ENTRYPOINT ["/redis-monitor"]
