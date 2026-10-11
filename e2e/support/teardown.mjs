export default async function teardown(config) {
  const url = config.projects[0].use.baseURL;
  const response = await fetch(`${url}/__e2e/shutdown`, {
    method: 'POST', headers: { Authorization: `Bearer ${process.env.E2E_SHUTDOWN_TOKEN}` },
    signal: AbortSignal.timeout(25_000),
  });
  if (!response.ok) throw new Error('Owned E2E backend/database cleanup failed');
  const result = await response.json();
  if (!result.cleaned) throw new Error('Owned E2E fixture did not acknowledge cleanup');
}
