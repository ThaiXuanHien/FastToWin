import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import vm from "node:vm";

const overridePath = path.resolve(
  "webApp/webpack.config.d/dev-server-host.js",
);

function applyOverride(environment = {}) {
  const source = fs.readFileSync(overridePath, "utf8");
  const context = vm.createContext({
    config: { devServer: { port: 8081 } },
    process: { env: environment },
  });

  vm.runInContext(source, context, { filename: overridePath });
  return context.config.devServer;
}

test("LAN mode exposes the Web development server on every interface", () => {
  const devServer = applyOverride({ FASTTOWIN_WEB_DEV_HOST: "0.0.0.0" });

  assert.equal(devServer.host, "0.0.0.0");
  assert.equal(devServer.port, 8081);
});

test("default mode keeps Kotlin's localhost binding", () => {
  const devServer = applyOverride();

  assert.equal(devServer.host, undefined);
  assert.equal(devServer.port, 8081);
});
