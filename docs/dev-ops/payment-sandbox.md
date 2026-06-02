# 支付沙箱检查

项目新增了 `/api/v1/payment/gateway/status`（支付网关状态接口），用于确认当前支付能力是否只是 `MOCK_PAY`（模拟支付），还是已经配置了支付宝/微信支付网关。

## 检查命令

```powershell
cd E:\javaproject\agent-group
.\docs\dev-ops\scripts\payment-sandbox-check.ps1
```

结果会保存到：

```text
docs\dev-ops\reports
```

## 支付宝沙箱

支付宝沙箱需要配置这些环境变量：

```powershell
$env:AGENT_GROUP_ALIPAY_GATEWAY_URL="https://openapi-sandbox.dl.alipaydev.com/gateway.do"
$env:AGENT_GROUP_ALIPAY_APP_ID="你的沙箱 AppId"
$env:AGENT_GROUP_ALIPAY_PRIVATE_KEY="你的应用私钥"
$env:AGENT_GROUP_ALIPAY_PUBLIC_KEY="支付宝沙箱公钥"
```

脚本会检查 `gateway-url`（网关地址）是否包含 `sandbox`（沙箱）或 `alipaydev`（支付宝沙箱域名）。

## 严格检查

如果要把“官方沙箱已就绪”作为通过条件：

```powershell
.\docs\dev-ops\scripts\payment-sandbox-check.ps1 -RequireOfficialSandbox
```
