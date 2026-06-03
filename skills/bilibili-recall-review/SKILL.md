---
name: bilibili-recall-review
description: Review generated Bilibili lecture-note LaTeX or PDF outputs against the original SRT/VTT subtitle or transcript file to find missed important, detailed, useful, interesting, or teaching-relevant information. Use when the user asks to check recall, omissions, missed details, subtitle coverage, or to spawn an independent reviewer after using bilibili-render-pdf. The skill only reports feedback and does not modify the notes.
---

# Bilibili Recall Review

Use this skill after a first draft from `bilibili-render-pdf` exists. The goal is not to polish prose; the goal is to reduce recall loss by checking whether the `.tex` notes preserve all useful teaching information from the original subtitle or transcript.

## Inputs

Require these artifacts:

- the generated `.tex` file, or a PDF only when the `.tex` is unavailable
- the original timestamped subtitle file, preferably `.srt` or `.vtt`
- optional: cleaned transcript, extracted frames, cover image, or previous reviewer report

If either the notes or subtitles are missing, ask for the missing path before reviewing.

## Review Principle

Act as an independent reviewer. Do not edit files. Do not rewrite the notes. Do not silently fix anything.

Prefer high recall over brevity. A useful omission includes any subtitle-backed information that would improve the teaching notes:

- central concept, workflow step, setup requirement, command, parameter, file path, UI action, or caveat
- concrete detail that makes the procedure reproducible
- warning, failure mode, prerequisite, limitation, tradeoff, or hidden assumption
- speaker emphasis, useful analogy, unusually compact explanation, or interesting aside
- substantive closing discussion, advice, next step, or open question

Ignore greetings, sponsorship, routine channel logistics, repeated filler, and low-information back-and-forth.

## Workflow

1. Read the subtitle file with timestamps intact.
2. Read the generated notes and build a coverage map by section.
3. Split the subtitles into coherent windows, usually 1--3 minutes each, or by visible topic changes.
4. For each window, classify the content as covered, partially covered, missing, or intentionally skippable.
5. Produce feedback only. Each finding must cite the subtitle time interval and explain why the missing content matters.
6. If multi-agent tools are available, spawn one independent reviewer agent with only the subtitle path, notes path, and this instruction: review for recall omissions only, report findings, do not modify files. Compare its findings with your own before answering.
7. On later turns, when the user provides a revised `.tex`, re-review unresolved findings and perform one fresh pass for new omissions.
8. Continue until there are no major or medium recall omissions. Then say the notes are recall-complete relative to the provided subtitles, while naming any remaining low-risk gaps.

## Output Format

Start with one sentence:

- `结论：仍有召回遗漏。`
- or `结论：相对当前字幕，讲义信息已基本完备。`

Then list findings in this compact format:

```text
级别：重要 / 细节 / 有趣 / 可选
时间：00:12:31--00:12:46
遗漏：原字幕中有什么信息没有进入讲义
价值：为什么它值得保留
建议位置：应补到讲义的哪个章节或小节
```

Use `重要` for material that affects understanding or复现；use `细节` for concrete implementation or explanation details；use `有趣` for vivid but non-essential information；use `可选` for useful但不必强制加入的内容。

Do not output a revised `.tex` unless the user explicitly asks for修改。If asked to modify, first state that this skill's default mode is review-only, then proceed only after the user confirms.
