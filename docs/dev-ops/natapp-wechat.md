# natapp 微信联调说明

这份材料用于把本地后端暴露给微信公众号后台，验证扫码登录、公众号事件回调和模板消息。

## 1. 启动本地服务

```powershell
cd E:\javaproject\agent-group
.\docs\dev-ops\start.ps1
```

后端地址默认为 `http://127.0.0.1:8080`。

## 2. 启动 natapp

把 `natapp.exe` 放到本机任意目录后执行：

```powershell
.\natapp.exe -authtoken=你的隧道token
```

拿到公网地址后，例如 `https://abc123.natappfree.cc`，设置环境变量：

```powershell
$env:AGENT_GROUP_WECHAT_CALLBACK_BASE_URL="https://abc123.natappfree.cc"
$env:AGENT_GROUP_WECHAT_OFFICIAL_APP_ID="你的公众号AppID"
$env:AGENT_GROUP_WECHAT_OFFICIAL_APP_SECRET="你的公众号AppSecret"
$env:AGENT_GROUP_WECHAT_OFFICIAL_TOKEN="agent_group_dev_token"
```

重新启动后端。

## 3. 配置微信公众号后台

服务器地址：

```text
https://abc123.natappfree.cc/api/v1/weixin/portal
```

令牌填写：

```text
agent_group_dev_token
```

消息加解密方式先选明文。

## 4. 验证接口

生成登录二维码：

```powershell
curl -X POST http://127.0.0.1:8080/api/v1/weixin/login/qr `
  -H "Content-Type: application/json" `
  -d "{\"userId\":\"U10001\"}"
```

查询扫码状态：

```powershell
curl "http://127.0.0.1:8080/api/v1/weixin/login/status?sceneId=返回的sceneId"
```

本地模拟扫码：

```powershell
curl -X POST http://127.0.0.1:8080/api/v1/weixin/login/simulate `
  -H "Content-Type: application/json" `
  -d "{\"sceneId\":\"返回的sceneId\",\"userId\":\"U10001\",\"openId\":\"mock_openid_U10001\"}"
```

发送模板消息需要运营账号：

```powershell
curl -u operator:operator_dev -X POST http://127.0.0.1:8080/api/v1/weixin/template/send `
  -H "Content-Type: application/json" `
  -d "{\"openId\":\"mock_openid_U10001\",\"templateId\":\"demo_template_id\",\"title\":\"拼团状态更新\",\"remark\":\"请查看订单列表\"}"
```

没有配置公众号密钥时，系统会走本地模拟模式，仍然能演示扫码状态、用户身份和模板消息载荷。
