---
name: markdown-to-word-mathtype
description: Convert Markdown files (.md) into styled Word documents (.docx) and turn TeX formulas into MathType equation objects through Microsoft Word automation. Use when the request is about “Markdown 转 Word”, “md 转 docx”, “Markdown 导出 Word”, “公式转 MathType”, or when a Chinese report or paper needs fixed heading, body, table, figure-caption, and code-block styles.
---

# Markdown 转 Word（MathType）

## 适用场景

- 把 `Markdown`（标记文档）转成 `Word`（文档）
- 把 `TeX`（公式源码）转成 `MathType`（公式对象）
- 需要固定中文排版规范的技术报告、课程报告、论文初稿

## 快速使用

1. 激活环境：`conda activate demo244`
2. 如缺依赖，安装：`pip install python-docx mistune pywin32`
3. 运行脚本：`python C:\Users\86157\.codex\skills\markdown-to-word-mathtype\scripts\convert_markdown_to_word.py --input 输入.md --output 输出.docx --mathtype-mode require`

## 工作流程

1. 读取 `Markdown`（源文件）
2. 解析标题、正文、列表、表格、代码块、图片和公式
3. 先生成 `docx`（Word 文件），并给公式加临时标记
4. 调用 `Word`（文档程序）里的 `MathType`（公式插件）宏，把公式替换成真正的 `MathType`（公式对象）
5. 清理临时标记，保存最终文档

## 公式规则

- 行内公式使用 `$...$`
- 块公式使用 `$$...$$`
- 只有显式写了 `\tag{...}`（编号）或 `\tag*{...}`（编号）的块公式，才会按“公式居中、编号右对齐”处理
- 没有编号的块公式，只输出公式本体
- 写了 `\notag`（取消编号）或 `\nonumber`（取消编号）的块公式，按无编号处理
- 代码块里的 `$...$` 不会被当成公式
- `\tag{...}` 不会再被送进 `MathType`（公式对象）

## 当前排版约定

- 一级、二级、三级标题、正文、表格、代码块使用脚本内置样式
- 图名和表名为宋体五号，英文 `Times New Roman`（西文字体），居中
- 图片本体居中
- 表格整体居中，单元格内容居中
- 如需调整样式，优先改脚本中的 `STYLE_SPECS`（样式常量），不要手动进 `Word`（文档程序）里逐段修改

## 运行模式

- `--mathtype-mode require`（必须转换）：必须转成 `MathType`
- `--mathtype-mode auto`（自动转换）：能转则转，失败时保留 `TeX`
- `--mathtype-mode skip`（跳过转换）：只生成 `Word`，不转 `MathType`

## 相关文件

- `scripts/convert_markdown_to_word.py`（主脚本）
- `references/style-spec.md`（排版规则）
- `references/mathtype-notes.md`（`MathType` 宏加载与排查说明）

## 注意

- 依赖 `Windows`（系统）桌面版 `Word`（文档程序）和本机安装的 `MathType`（公式插件）
- 公式转换阶段会启动 `Word` 自动化，不适合纯无界面环境
- 如果文档规范变了，优先更新脚本和 skill，不要在导出的文件里手工返工
