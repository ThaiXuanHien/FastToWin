// Fixed runtime adapter. The exported preparation module remains pure.
import fs from 'node:fs';
import {renderAlloy} from './prepare.mjs';
try {
  const config = renderAlloy({
    url: process.env.GRAFANA_CLOUD_REMOTE_WRITE_URL,
    expectedHost: process.env.GRAFANA_CLOUD_EXPECTED_HOST,
    tenantId: process.env.GRAFANA_CLOUD_TENANT_ID,
    collectorAddress: process.env.FASTTOWIN_REDIS_MONITOR_SCRAPE_ADDRESS,
  });
  fs.writeFileSync('/run/secrets/config.alloy', config, {mode: 0o600, flag: 'wx'});
} catch {
  // Never include environment values or lower-level parser/file errors.
  console.error('Invalid sender launch configuration');
  process.exitCode = 1;
}
