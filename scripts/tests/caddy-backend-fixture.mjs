import { createServer } from 'node:http';
const port = Number(process.argv[2]);
if (!Number.isInteger(port)) throw new Error('Expected fixture port');
const csp = "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'";
createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  let body = ''; for await (const chunk of req) body += chunk;
  const admin = url.pathname === '/admin' || url.pathname.startsWith('/admin/');
  const disabled = admin && req.headers['x-routing-test-admin'] === 'disabled';
  res.writeHead(disabled ? 404 : 200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store',
    ...(admin ? { 'Content-Security-Policy': csp } : {}), 'X-Content-Type-Options': 'nosniff' });
  res.end(JSON.stringify({ source: 'backend', path: url.pathname, method: req.method, query: url.search,
    body: body ? JSON.parse(body) : null, authorization: req.headers.authorization || null, code: disabled ? 'NOT_FOUND' : null }));
}).listen(port, '127.0.0.1');
