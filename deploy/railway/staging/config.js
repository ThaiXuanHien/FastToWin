// Staging uses the same origin for HTTP sessions and WSS. No production push config.
globalThis.FASTTOWIN_CONFIG = {
    serverUrl: `wss://${globalThis.location.host}/game`
};
