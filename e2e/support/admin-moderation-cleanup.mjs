// Owns final evidence and teardown for the opt-in local moderation harness.
export async function finishModerationAcceptance({ evidence, failure, closeSockets, closeContext, closeBrowser, removePassword, stopBackend, dropSchema, writeEvidence }) {
  const errors = failure ? [failure] : [];
  const failedSteps = failure ? ['acceptance'] : [];
  const cleanup = evidence.cleanup = {};
  async function attempt(name, operation) {
    try { await operation(); cleanup[name] = 'done'; return true; }
    catch (error) {
      if (name === 'password' && error?.code === 'ENOENT') { cleanup[name] = 'already-absent'; return true; }
      cleanup[name] = 'failed'; failedSteps.push(name); errors.push(error); return false;
    }
  }
  await attempt('sockets', closeSockets);
  await attempt('context', closeContext);
  await attempt('browser', closeBrowser);
  await attempt('password', removePassword);
  if (await attempt('backend', stopBackend)) await attempt('schema', dropSchema);
  else cleanup.schema = 'retained-backend-not-stopped';
  if (errors.length) evidence.status = 'FAIL';
  evidence.failedSteps = failedSteps;
  try { await writeEvidence(evidence); }
  catch (error) { evidence.status = 'FAIL'; failedSteps.push('evidence'); errors.push(error); }
  if (errors.length) throw new AggregateError(errors, 'Owned moderation acceptance/cleanup failed: ' + failedSteps.join(', '));
}
