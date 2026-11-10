FROM golang@sha256:e482b31b5d30c31f9e495f4b8cbdf41429b2d74955dfbcd9c0a904483d738390 AS build
WORKDIR /src
COPY . .
ENV CGO_ENABLED=0 GOTOOLCHAIN=local GOPROXY=off
RUN go build -trimpath -buildvcs=false -ldflags="-s -w" -o /out/supervisor ./cmd/supervisor

FROM scratch
COPY --from=build /out/supervisor /supervisor
