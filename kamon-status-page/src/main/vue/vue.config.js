module.exports = {
  productionSourceMap: false,
  devServer: {
    proxy: 'http://localhost:5266'
  }
}