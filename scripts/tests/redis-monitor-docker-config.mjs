// Acceptance-only Docker configuration; never reads user registry configuration.
import fs from 'node:fs';
import path from 'node:path';
export function dockerConfig(dockerExecutable) {
  const plugins=path.resolve(path.dirname(dockerExecutable),'../cli-plugins');
  return {auths:{},...(fs.existsSync(path.join(plugins,'docker-compose.exe'))?{cliPluginsExtraDirs:[plugins]}:{})};
}
