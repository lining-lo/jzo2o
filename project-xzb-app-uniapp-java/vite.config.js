import { defineConfig } from 'vite'
import uni from "@dcloudio/vite-plugin-uni";

export default defineConfig({
  plugins: [
    uni()
  ],
  server: {
    "port" : 8080,
    proxy : {
      "/api" : {
        target: 'http://localhost:11500',
        changeOrigin : true,
        rewrite : (path) => path.replace(/^\/api/, "")
      }
    }
  }
})