# 数学公式排版规范

技术报告中公式与文字深度交织，是区别于普通文档的核心特征。本规范确保公式可渲染、易读懂、符号一致。适用于任何技术领域。

## 基本语法

### 行内公式

用单 `$` 包裹，嵌入正文中引用变量或短表达式：

```
模型的隐藏层维度为 $d_h = 256$，训练共进行 $T = 100$ 个 epoch。
```

### 块级公式

用 `$$` 包裹，独立成行，用于核心定义和推导：

```
$$
\hat{y} = \arg\max_{c \in \mathcal{C}} P(y = c \mid \mathbf{x}; \theta)
$$
```

## 公式-文字交织模式

每个块级公式必须遵循 **引导→公式→解释** 三步走。以下四种模式覆盖技术报告中绝大多数公式场景，每种模式用不同领域的范例说明。

### 模式 A：定义式

**引导句**引出定义，公式给出表达式，**解释句**逐个说明变量。适用于：问题定义、输入输出形式化、概率模型。

**范例**（分类问题定义）：
```markdown
设输入样本为 $\mathbf{x} \in \mathbb{R}^d$，
对应标签为 $y \in \{1, 2, \ldots, C\}$。
给定训练集 $\mathcal{D} = \{(\mathbf{x}_i, y_i)\}_{i=1}^{N}$，
目标是学习参数 $\theta$ 使得如下条件概率最大化：

$$
\theta^* = \arg\max_{\theta} \prod_{i=1}^{N} P(y_i \mid \mathbf{x}_i; \theta)
$$

其中 $P(y \mid \mathbf{x}; \theta)$ 为模型输出的类别后验概率，
$N$ 为训练样本数，$C$ 为类别总数。
```

**范例**（序列生成问题定义）：
```markdown
设源序列为 $\mathbf{x} = (x_1, x_2, \ldots, x_S)$，
目标序列为 $\mathbf{y} = (y_1, y_2, \ldots, y_T)$。
模型以自回归方式逐 token 生成：

$$
P(\mathbf{y} \mid \mathbf{x}) = \prod_{t=1}^{T} P(y_t \mid y_{<t}, \mathbf{x}; \theta)
$$

其中 $y_{<t}$ 表示时刻 $t$ 之前的已生成序列，$S$ 和 $T$ 分别为源序列和目标序列长度。
```

### 模式 B：计算式

**说明计算目的**，给出公式，**解释关键变量与设计动机**。适用于：归一化、特征变换、距离度量、评估指标定义。

**范例**（Batch Normalization）：
```markdown
为加速收敛并稳定训练，对每个隐藏层的输出施加批归一化处理：

$$
\hat{z}_j = \frac{z_j - \mu_{\mathcal{B}}}{\sqrt{\sigma_{\mathcal{B}}^2 + \epsilon}}, \quad
\tilde{z}_j = \gamma \hat{z}_j + \beta
$$

其中 $\mu_{\mathcal{B}}$ 和 $\sigma_{\mathcal{B}}^2$ 分别为当前批次的均值与方差，
$\gamma$ 和 $\beta$ 为可学习的缩放与偏移参数，
$\epsilon$ 为防止除零的常数（通常取 $10^{-5}$）。
```

**范例**（评估指标定义）：
```markdown
模型性能采用精确率（Precision）与召回率（Recall）的调和平均 F1 值衡量：

$$
F_1 = \frac{2 \cdot P \cdot R}{P + R}, \quad
P = \frac{TP}{TP + FP}, \quad
R = \frac{TP}{TP + FN}
$$

其中 $TP$、$FP$、$FN$ 分别为真阳性、假阳性和假阴性样本数。
F1 值同时兼顾了查全率和查准率，适合类别不平衡场景下的评估。
```

### 模式 C：递推式 / 网络前向

**说明模块功能**，给出前向计算公式，**解释输出的物理含义**。适用于：网络层定义、注意力机制、编解码器、迭代算法。

**范例**（Transformer 自注意力）：
```markdown
自注意力模块通过查询-键-值机制捕获序列内部的全局依赖关系。
对输入序列 $\mathbf{H} \in \mathbb{R}^{n \times d}$，
注意力计算如下：

$$
\text{Attention}(\mathbf{Q}, \mathbf{K}, \mathbf{V})
= \text{softmax}\left(\frac{\mathbf{Q}\mathbf{K}^\top}{\sqrt{d_k}}\right) \mathbf{V}
$$

其中 $\mathbf{Q} = \mathbf{H}\mathbf{W}_Q$，
$\mathbf{K} = \mathbf{H}\mathbf{W}_K$，
$\mathbf{V} = \mathbf{H}\mathbf{W}_V$ 分别为查询、键、值的线性投影，
$d_k$ 为键向量维度，除以 $\sqrt{d_k}$ 防止点积过大导致梯度消失。
```

**范例**（GRU 时序编码）：
```markdown
系统采用门控循环单元（GRU）对输入序列进行时序编码。
在每个时间步 $t$，GRU 按如下方式更新隐状态：

$$
\mathbf{h}_t = \text{GRU}(\mathbf{e}_t, \mathbf{h}_{t-1})
$$

最终隐藏状态 $\mathbf{h}_T$ 作为序列的紧凑表征，
编码了输入在时间维度上的演化趋势与依赖模式。
```

### 模式 D：损失函数

**说明优化目标**，给出分项损失，**给出组合公式**，**解释权重设计直觉**。适用于：训练目标、多任务损失、正则化。

**范例**（多任务学习损失）：
```markdown
训练目标由分类损失与回归损失联合构成。
分类分支采用交叉熵损失：

$$
\mathcal{L}_{cls} = -\frac{1}{N} \sum_{i=1}^{N} \log P(y_i \mid \mathbf{x}_i; \theta)
$$

回归分支采用 Smooth L1 损失：

$$
\mathcal{L}_{reg} = \frac{1}{N} \sum_{i=1}^{N} \text{SmoothL1}(\hat{\mathbf{b}}_i, \mathbf{b}_i)
$$

最终训练损失为：

$$
\mathcal{L} = \mathcal{L}_{cls} + \lambda \cdot \mathcal{L}_{reg}
$$

其中 $\lambda$ 控制两项损失的相对权重。
实验中设 $\lambda = 1.0$，使分类与定位任务获得近似相等的梯度贡献。
```

**范例**（带正则化的生成损失）：
```markdown
生成器的优化目标包含重建损失与对抗损失两项：

$$
\mathcal{L}_G = \underbrace{\| G(\mathbf{z}) - \mathbf{x} \|_1}_{\text{重建损失}}
+ \lambda_{adv} \underbrace{(- \log D(G(\mathbf{z})))}_{\text{对抗损失}}
$$

其中 $\lambda_{adv}$ 平衡生成质量与对抗强度。
较大的 $\lambda_{adv}$ 使输出更逼真但可能引入伪影，
较小则趋于模糊。实验中通过网格搜索确定 $\lambda_{adv} = 0.01$。
```

## 符号一致性规则

### 符号定义表

在开始写作前，从代码中提取变量，建立**项目专属**的符号映射表。以下为格式示范：

| 代码变量 | 数学符号 | 含义 |
|---------|---------|------|
| `hidden_dim` | $d_h$ | 隐藏层维度 |
| `num_classes` | $C$ | 类别数 |
| `learning_rate` | $\eta$ | 学习率 |
| `batch_size` | $B$ | 批大小 |
| `input` / `x` | $\mathbf{x}$ | 输入特征 |
| `output` / `logits` | $\hat{\mathbf{y}}$ | 模型预测输出 |
| `label` / `target` | $y$ | 真实标签 |
| `loss` | $\mathcal{L}$ | 损失函数值 |

每个项目的符号表内容不同，上表仅为格式参考。在写作前根据实际代码填充。

### 一致性检查清单

- [ ] 同一变量全文只用一个符号（不出现 $h$ 和 $\mathbf{h}$ 混用）
- [ ] 向量/矩阵用粗体（$\mathbf{x}$），标量用普通体（$x$）
- [ ] 上标用于时间步或层级（$x^t$, $h^{(l)}$），下标用于索引（$x_i$）
- [ ] 所有符号在首次出现时有定义
- [ ] 希腊字母用于超参数/可学习参数（$\lambda$, $\theta$, $\gamma$, $\sigma$）
- [ ] 预测值用 hat（$\hat{y}$），归一化值用 tilde（$\tilde{x}$）

## 常用 LaTeX 速查

| 需求 | LaTeX | 效果 |
|------|-------|------|
| 粗体向量 | `\mathbf{x}` | $\mathbf{x}$ |
| 上下标 | `x_i^t` | $x_i^t$ |
| 分式 | `\frac{a}{b}` | $\frac{a}{b}$ |
| 求和 | `\sum_{i=1}^{N}` | $\sum_{i=1}^{N}$ |
| 连乘 | `\prod_{i=1}^{N}` | $\prod_{i=1}^{N}$ |
| 条件概率 | `P(Y \mid X)` | $P(Y \mid X)$ |
| 拼接 | `[\mathbf{a}; \mathbf{b}]` | $[\mathbf{a}; \mathbf{b}]$ |
| 损失函数 | `\mathcal{L}` | $\mathcal{L}$ |
| 帽子（预测值） | `\hat{y}` | $\hat{y}$ |
| 波浪（归一化） | `\tilde{x}` | $\tilde{x}$ |
| argmin/argmax | `\arg\max_{k}` | $\arg\max_{k}$ |
| 空格 | `\quad` | 公式内间距 |
| 文本嵌入公式 | `\text{softmax}` | $\text{softmax}$ |
| 范数 | `\| \mathbf{x} \|_2` | $\| \mathbf{x} \|_2$ |
| 实数集 | `\mathbb{R}^d` | $\mathbb{R}^d$ |
| 花括号上下标注 | `\underbrace{...}_{\text{说明}}` | $\underbrace{a+b}_{\text{说明}}$ |

## 公式编号（可选）

如果报告需要交叉引用公式，使用手动编号：

```markdown
$$
\mathcal{L} = \mathcal{L}_{cls} + \lambda \cdot \mathcal{L}_{reg} \tag{3.1}
$$

如式 (3.1) 所示，总损失由分类损失与回归损失加权组成。
```

编号格式：`(章节号.公式序号)`，如 (3.1)、(3.2)。
