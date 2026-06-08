# 支付沙箱检查

项目提供 `/api/v1/payment/gateway/status`（支付网关状态接口），用于确认 `ALIPAY`（支付宝沙箱）是否已经具备验收条件。当前演示和验收只接受真实支付宝沙箱；`MOCK_PAY`（模拟支付）只保留在单元测试桩里。

## 检查命令

```powershell
cd E:\javaproject\agent-group
.\docs\dev-ops\scripts\payment-sandbox-check.ps1 -RequireOfficialSandbox
```

报告会保存到：

```text
docs\dev-ops\reports
```

## 支付宝沙箱就绪条件

`officialSandboxReady`（官方沙箱就绪）必须为 `true`（真），并且 `recommendedChannel`（推荐支付渠道）应为 `ALIPAY`（支付宝渠道）。

需要同时满足：

- `AGENT_GROUP_ALIPAY_GATEWAY_URL`（支付宝网关地址）指向沙箱网关。
- `AGENT_GROUP_ALIPAY_APP_ID`（支付宝应用编号）已配置。
- `AGENT_GROUP_ALIPAY_PRIVATE_KEY`（应用私钥）已配置。
- `AGENT_GROUP_ALIPAY_PUBLIC_KEY`（支付宝公钥）已配置。
- `AGENT_GROUP_ALIPAY_NOTIFY_URL`（异步回调地址）已配置，且是公网可访问地址。

本地 `localhost`（本机地址）、`127.0.0.1`（本机回环地址）和内网地址不会被判定为公网回调。联调时可以使用内网穿透或公网域名承载 `/api/v1/payment/alipay/notify`（支付宝回调）。

## 环境变量示例

```powershell
$env:AGENT_GROUP_ALIPAY_GATEWAY_URL="https://openapi-sandbox.dl.alipaydev.com/gateway.do"
$env:AGENT_GROUP_ALIPAY_APP_ID="你的沙箱应用编号"
$env:AGENT_GROUP_ALIPAY_PRIVATE_KEY="你的应用私钥"
$env:AGENT_GROUP_ALIPAY_PUBLIC_KEY="支付宝沙箱公钥"
$env:AGENT_GROUP_ALIPAY_NOTIFY_URL="https://你的公网域名/api/v1/payment/alipay/notify"
```

密钥只放本地环境变量或 `.env`（环境变量文件），不要提交到 `Git`（版本控制工具）。

## 失败口径

配置不完整时，报告里的 `officialSandboxMissingItems`（官方沙箱缺失项）会列出缺失项；此时系统不会推荐或自动回退到 `MOCK_PAY`（模拟支付），而是继续推荐 `ALIPAY`（支付宝沙箱）并标记为不可验收。
