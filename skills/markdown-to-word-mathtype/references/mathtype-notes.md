# MathType 说明

## 当前实现

- 脚本不会把整篇文档一次性丢给 MathType。
- 脚本会先给每个公式加临时标记，只选中公式本身去执行 Word 宏。
- 如果块公式里出现 `\tag{...}`，脚本会先把编号拆出来，在同一段里用制表位把编号排到右侧，再只把公式主体送给 MathType。
- 当前使用的宏名是：
  `MathTypeCommands.UILib.MTCommand_TeXToggle`

## 为什么这样做

- 直接整篇转换会误伤代码块里的 `$...$`。
- `Convert Equations` 对话框型宏更依赖桌面界面，自动化稳定性差。
- `TeXToggle` 已经在本机验证过，可以把选中的 `$...$` 或 `\[...\]` 变成 `Equation.DSMT4`（MathType 公式对象）。

## 依赖条件

- Windows
- 已安装桌面版 `Microsoft Word`
- 已安装 `MathType`
- Word 里能加载 `MathType Commands *.dotm`

## 常见失败点

- 没装 MathType，或者 Word 没加载 MathType 模板。
- 公式写法不是 MathType 支持的 TeX 子集。
- 在纯无界面或远程受限桌面里跑，Word 自动化初始化失败。

## 排查顺序

1. 先确认本机能正常打开 Word。
2. 再确认 `C:\Program Files (x86)\MathType\Office Support` 下面存在 `MathType Commands *.dotm`。
3. 用 `--mathtype-mode auto` 跑一次，看脚本有没有输出宏加载失败或转换失败提示。
4. 只拿一个最小公式样例测试，比如 `$x^2+1$`。
