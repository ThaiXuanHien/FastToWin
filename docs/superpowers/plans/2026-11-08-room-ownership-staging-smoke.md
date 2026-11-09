# Room ownership staging smoke implementation plan

Prepare the first private Railway acceptance step for #12 and #15 using the frozen
backend distribution from the staging checkpoint. Keep the local fault and load
runner intact.

1. Add a staging-only launcher under the server test sources. Validate the exact
   staging project and environment, the run-specific service name and deadline
   before opening any connection. Derive endpoints from the validated run name.
   Refuse non-private or overlapping backend addresses. Test all rejection paths.
2. Use a fresh marked fixture database and exactly two synthetic accounts. Reuse
   the existing WebSocket acceptance client to prove two-way online presence,
   a shared account rate limit, a committed selection seen by both backends and
   two finalized quota receipts. Print only bounded result fields.
3. Package the launcher separately from the backend and run a Docker rehearsal
   with the same private DNS names. Verify fail-closed gates and clean up the
   rehearsal. Record the upload contents and outcome before using Railway.

The Railway fault controller and full load profiles follow after the smoke.
They require a way to stop or isolate a fixture process and prove each injection
without granting the runner a workspace-level token.
