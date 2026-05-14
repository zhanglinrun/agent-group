# 样本知识资料

这里放的是网页端验收可直接使用的样本文档。内容是按常见电商商品详情、活动规则和售后政策结构生成的测试资料，不对应真实品牌商品。

建议上传顺序：

1. `product-detail.md`（商品详情）
2. `marketing-rule.md`（营销活动规则）
3. `after-sale-policy.md`（售后政策）

网页端上传后，后端会保存到 `MinIO`（对象存储），解析文本，切分知识片段，并写入 `pgvector`（向量库）。如果没有配置大模型密钥，会自动使用本地兜底向量和回答。

评测用例样本在 `evaluation-cases.json`（评测用例配置），可以通过环境变量指定：

```powershell
$env:AGENT_GROUP_EVALUATE_CASE_FILE="N:\java_project\agent-group\docs\sample-knowledge\evaluation-cases.json"
```
