const devServerHost = process.env.FASTTOWIN_WEB_DEV_HOST;

if (devServerHost) {
    config.devServer = config.devServer || {};
    config.devServer.host = devServerHost;
}
