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

## 用户端能力

- 顶部“模型”用于配置自定义模型；顶部“记忆”用于查看、刷新、启用、停用和删除长期记忆。
- deep（深度任务）会展示能力计划、能力调用、执行时间线、产物列表、失败原因和重新执行入口。
- 产物区集中展示报告、PPT（演示文稿）、图片和文件分析结果，支持预览和下载。

## 验证

```powershell
npm run lint
npm run build
```
