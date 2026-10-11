import { readFile } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { validateDatabaseSettings } from '../../scripts/lib/load-test-utils.mjs';

// Optional durable E2E fixture: owns one UUID schema in the known local test DB.
// Neither a target URL nor a pre-existing schema can be supplied by callers.
export async function openLocalPostgres(settingsFile, psql = 'psql') {
  const settings = JSON.parse(await readFile(settingsFile, 'utf8'));
  validateDatabaseSettings(settings);
  const password = (await readFile(settings.passwordFile, 'utf8')).trim();
  const schema = `webe2e_${randomUUID().replaceAll('-', '')}`;
  const env = Object.fromEntries(['PATH', 'Path', 'SystemRoot', 'WINDIR', 'TEMP', 'TMP']
    .filter(key => process.env[key]).map(key => [key, process.env[key]]));
  async function sql(query) {
    await new Promise((resolve, reject) => {
      const child = spawn(psql, ['-X', '-q', '-v', 'ON_ERROR_STOP=1', '-h', '127.0.0.1',
        '-p', '15432', '-d', 'admin_test', '-U', settings.user], {
        env: { ...env, PGPASSWORD: password, PGCONNECT_TIMEOUT: '5' },
        stdio: ['pipe', 'ignore', 'ignore'], windowsHide: true,
      });
      const timer = setTimeout(() => {
        child.kill(); reject(new Error('LOCAL_E2E_DATABASE_TIMEOUT'));
      }, 15_000);
      child.on('error', () => { clearTimeout(timer); reject(new Error('LOCAL_E2E_DATABASE_PROCESS_ERROR')); });
      child.on('exit', code => {
        clearTimeout(timer);
        code === 0 ? resolve() : reject(new Error('LOCAL_E2E_DATABASE_QUERY_FAILED'));
      });
      child.stdin.on('error', () => {});
      child.stdin.end(query);
    });
  }
  let closed = false;
  const close = async () => {
    if (closed) return;
    await sql(`DROP SCHEMA IF EXISTS ${schema} CASCADE;`);
    closed = true;
  };
  // CREATE may have reached PostgreSQL even if its acknowledgement was lost.
  try { await sql(`CREATE SCHEMA ${schema};`); }
  catch (failure) {
    try { await close(); } catch { throw new Error('LOCAL_E2E_SCHEMA_CLEANUP_FAILED'); }
    throw failure;
  }
  return {
    serverEnvironment: { DATABASE_URL: `${settings.url}?currentSchema=${schema}`,
      DATABASE_USER: settings.user, DATABASE_PASSWORD: password, DATABASE_POOL_SIZE: '5' },
    close,
  };
}
