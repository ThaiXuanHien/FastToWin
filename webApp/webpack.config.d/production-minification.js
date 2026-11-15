if (config.mode === 'production') {
    const TerserPlugin = require('terser-webpack-plugin');
    config.optimization = config.optimization || {};
    config.optimization.minimizer = [new TerserPlugin({
        terserOptions: {
            // Kotlin's production compiler already performs dead code elimination.
            // Terser's compression pass takes excessive time on the Compose bundle.
            compress: false,
        },
    })];
}
