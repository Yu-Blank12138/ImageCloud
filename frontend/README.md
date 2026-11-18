# ImageCloud 前端

使用 `create-vue` 初始化，采用 Vue 3、TypeScript、Vite 和 Vue Router。Node.js 版本要求为 `^22.18.0 || >=24.12.0`。

按仓库根目录的 README 启动后端，再在 `frontend` 目录运行以下命令。

```sh
npm ci
npm run dev
```

打开终端显示的本地地址。未登录时首页进入 `/login`，可前往 `/register` 创建账号。登录成功后进入 `/workspace` 占位页。

Vite 将 `/api` 请求转发到 `http://127.0.0.1:8080`。后端地址不同时，可通过 `IMAGECLOUD_API_TARGET` 指定目标，例如 `IMAGECLOUD_API_TARGET=http://127.0.0.1:8081 npm run dev`。生产部署时由同源服务转发 `/api`，并将前端页面路由回退到 `index.html`。

运行 `npm run build` 检查类型并构建生产产物，构建输出位于 `dist/`。
