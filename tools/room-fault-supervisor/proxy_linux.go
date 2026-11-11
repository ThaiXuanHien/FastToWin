package supervisor

import "context"

// NewProxyDriver owns the fixed Toxiproxy child and exactly two PostgreSQL
// paths. The raw configuration API is reachable only on container loopback.
func NewProxyDriver(ctx context.Context, fixture string) (Driver, error) {
	upstream, err := proxyUpstream(fixture)
	if err != nil {
		return nil, err
	}
	const config = "/run/room-fault/proxies.json"
	if err = writeProxyConfig(config, upstream); err != nil {
		return nil, err
	}
	child, err := newOwnedProcess(ctx, "proxy", []string{"/toxiproxy", "-host=127.0.0.1", "-port=8474", "-config=" + config})
	if err != nil {
		return nil, err
	}
	d := &proxyDriver{child: child, client: proxyHTTPClient(), api: "http://127.0.0.1:8474", targets: [2]string{"127.0.0.1:15432", "127.0.0.1:25432"}, upstream: upstream, state: "running"}
	// Backends/stores may start concurrently. Readiness is verified by Status,
	// never inferred merely from the child's launch acknowledgement.
	return d, nil
}
