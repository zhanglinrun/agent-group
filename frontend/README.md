# 前端说明

本目录是用户端 `Agent`（智能体）工作台和运营后台的 `Vite`（前端构建工具）应用。

## 启动

```powershell
npm install
npm run dev
```

默认后端代理地址是 `http://localhost:8080`（后端接口地址）。如需改后端地址：

```powershell
$env:VITE_API_TARGET="http://localhost:8080"
npm run dev
```

## 入口

- `http://localhost:5173/`（用户端）
- `http://localhost:5173/admin`（运营后台）

## 验证

```powershell
npm run lint
npm run build
```
