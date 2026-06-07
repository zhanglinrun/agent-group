# 支付沙箱检查

项目提供 `/api/v1/payment/gateway/status`（支付网关状态接口），用于确认当前支付能力是本地 `MOCK_PAY`（模拟支付），还是已经具备 `ALIPAY`（支付宝渠道）沙箱联调条件。

## 检查命令

```powershell
cd E:\javaproject\agent-group
.\docs\dev-ops\scripts\payment-sandbox-check.ps1
```

检查结果会保存到：

```text
docs\dev-ops\reports
```

报告会记录推荐渠道、沙箱证据、缺失配置项、每个渠道的配置完整度和回调地址。

## 支付宝沙箱就绪条件

`officialSandboxReady`（官方沙箱就绪）不是只看网关地址是否包含 `sandbox`（沙箱）或 `alipaydev`（支付宝沙箱域名），还要求以下条件同时满足：

- `AGENT_GROUP_ALIPAY_GATEWAY_URL`（支付宝网关地址）指向沙箱网关。
- `AGENT_GROUP_ALIPAY_APP_ID`（支付宝应用编号）已配置。
- `AGENT_GROUP_ALIPAY_PRIVATE_KEY`（应用私钥）已配置。
- `AGENT_GROUP_ALIPAY_PUBLIC_KEY`（支付宝公钥）已配置。
- `AGENT_GROUP_ALIPAY_NOTIFY_URL`（异步回调地址）已配置，且是公网可访问地址。

本地 `localhost`（本机地址）、`127.0.0.1`（本机回环地址）、内网地址不会被判定为公网回调。联调时可以用内网穿透工具生成公网地址，再配置到支付宝沙箱后台和本地环境变量中。

## 环境变量示例

```powershell
$env:AGENT_GROUP_ALIPAY_GATEWAY_URL="https://openapi-sandbox.dl.alipaydev.com/gateway.do"
$env:AGENT_GROUP_ALIPAY_APP_ID="你的沙箱应用编号"
$env:AGENT_GROUP_ALIPAY_PRIVATE_KEY="你的应用私钥"
$env:AGENT_GROUP_ALIPAY_PUBLIC_KEY="支付宝沙箱公钥"
$env:AGENT_GROUP_ALIPAY_NOTIFY_URL="https://你的公网域名/api/v1/payment/alipay/notify"
```

密钥只放本地环境变量或 `.env`（环境变量文件），不要提交到 `Git`（版本控制工具）。

## 严格检查

如果要把“支付宝官方沙箱已就绪”作为通过条件：

```powershell
.\docs\dev-ops\scripts\payment-sandbox-check.ps1 -RequireOfficialSandbox
```

当配置不完整时，报告里的 `officialSandboxMissingItems`（官方沙箱缺失项）会列出需要补齐的环境变量或公网回调要求；此时 `recommendedChannel`（推荐支付渠道）会回退为 `MOCK_PAY`（模拟支付）。
