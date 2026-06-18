import { safeExternalUrl } from "../appRuntime";

const TABLE_SEPARATOR_RE = /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/;
const UNORDERED_LIST_RE = /^\s*(?:[-*+]|\u2022)\s+(.+)$/;
const ORDERED_LIST_RE = /^\s*\d+\.\s+(.+)$/;

function splitTrailingUrlPunctuation(url) {
  let href = url || "";
  let suffix = "";
  while (/[.,;:!?\u3001\u3002\uff0c\uff1b\uff1a\uff01\uff1f]$/.test(href)) {
    suffix = href.slice(-1) + suffix;
    href = href.slice(0, -1);
  }
  return { href, suffix };
}

function renderInlineMarkdown(text, keyPrefix) {
  const source = String(text || "");
  const nodes = [];
  const inlineTokenRe = /(\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)|(https?:\/\/[^\s<)]+)|<br\s*\/?>|\*\*([^*]+)\*\*)/gi;
  let lastIndex = 0;
  let match;
  let tokenIndex = 0;

  while ((match = inlineTokenRe.exec(source)) !== null) {
    if (match.index > lastIndex) {
      nodes.push(source.slice(lastIndex, match.index));
    }

    const tokenKey = `${keyPrefix}-inline-${tokenIndex++}`;
    const fullToken = match[0];

    if (/^<br\s*\/?>$/i.test(fullToken)) {
      nodes.push(<br key={tokenKey} />);
    } else if (match[2] && match[3]) {
      const href = safeExternalUrl(match[3]);
      nodes.push(
        href ? (
          <a className="markdown-link" key={tokenKey} href={href} target="_blank" rel="noreferrer">
            {renderInlineMarkdown(match[2], tokenKey)}
          </a>
        ) : (
          match[2]
        )
      );
    } else if (match[4]) {
      const { href: rawHref, suffix } = splitTrailingUrlPunctuation(match[4]);
      const href = safeExternalUrl(rawHref);
      nodes.push(
        href ? (
          <a className="markdown-link" key={tokenKey} href={href} target="_blank" rel="noreferrer">
            {rawHref}
          </a>
        ) : (
          match[4]
        )
      );
      if (suffix) nodes.push(suffix);
    } else if (match[5]) {
      nodes.push(
        <strong className="markdown-strong" key={tokenKey}>
          {renderInlineMarkdown(match[5], tokenKey)}
        </strong>
      );
    }

    lastIndex = inlineTokenRe.lastIndex;
  }

  if (lastIndex < source.length) {
    nodes.push(source.slice(lastIndex));
  }

  return nodes.length ? nodes : source;
}

function splitMarkdownTableRow(line) {
  return String(line || "")
    .trim()
    .replace(/^\|/, "")
    .replace(/\|$/, "")
    .split("|")
    .map((cell) => cell.trim());
}

function isTableStart(lines, index) {
  return Boolean(lines[index]?.includes("|") && TABLE_SEPARATOR_RE.test(lines[index + 1] || ""));
}

function isMarkdownBlockStart(lines, index) {
  const line = lines[index] || "";
  return Boolean(
    /^#{1,6}\s+/.test(line) ||
      /^-{3,}$/.test(line.trim()) ||
      /^\s*>\s?/.test(line) ||
      UNORDERED_LIST_RE.test(line) ||
      ORDERED_LIST_RE.test(line) ||
      isTableStart(lines, index)
  );
}

export function MarkdownRenderer({ content = "" }) {
  const lines = String(content || "").replace(/\r\n/g, "\n").split("\n");
  const blocks = [];
  let index = 0;

  while (index < lines.length) {
    const line = lines[index];
    const trimmed = line.trim();
    const blockKey = `markdown-block-${index}`;

    if (!trimmed) {
      index += 1;
      continue;
    }

    if (/^-{3,}$/.test(trimmed)) {
      blocks.push(<hr className="markdown-divider" key={blockKey} />);
      index += 1;
      continue;
    }

    const headingMatch = line.match(/^(#{1,6})\s+(.+)$/);
    if (headingMatch) {
      const HeadingTag = headingMatch[1].length === 1 ? "h3" : "h4";
      blocks.push(
        <HeadingTag className="markdown-heading" key={blockKey}>
          {renderInlineMarkdown(headingMatch[2], blockKey)}
        </HeadingTag>
      );
      index += 1;
      continue;
    }

    if (isTableStart(lines, index)) {
      const headers = splitMarkdownTableRow(lines[index]);
      const rows = [];
      index += 2;
      while (index < lines.length && lines[index].trim() && lines[index].includes("|")) {
        rows.push(splitMarkdownTableRow(lines[index]));
        index += 1;
      }
      blocks.push(
        <div className="markdown-table-wrap" key={blockKey}>
          <table className="markdown-table">
            <thead>
              <tr>
                {headers.map((header, cellIndex) => (
                  <th key={`${blockKey}-head-${cellIndex}`}>{renderInlineMarkdown(header, `${blockKey}-head-${cellIndex}`)}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row, rowIndex) => (
                <tr key={`${blockKey}-row-${rowIndex}`}>
                  {headers.map((_, cellIndex) => (
                    <td key={`${blockKey}-row-${rowIndex}-${cellIndex}`}>
                      {renderInlineMarkdown(row[cellIndex] || "", `${blockKey}-row-${rowIndex}-${cellIndex}`)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      );
      continue;
    }

    if (/^\s*>\s?/.test(line)) {
      const quoteLines = [];
      while (index < lines.length && /^\s*>\s?/.test(lines[index])) {
        quoteLines.push(lines[index].replace(/^\s*>\s?/, ""));
        index += 1;
      }
      blocks.push(
        <blockquote className="markdown-quote" key={blockKey}>
          {quoteLines.map((quoteLine, quoteIndex) => (
            <p key={`${blockKey}-quote-${quoteIndex}`}>
              {renderInlineMarkdown(quoteLine, `${blockKey}-quote-${quoteIndex}`)}
            </p>
          ))}
        </blockquote>
      );
      continue;
    }

    const unorderedMatch = line.match(UNORDERED_LIST_RE);
    if (unorderedMatch) {
      const items = [];
      while (index < lines.length) {
        const itemMatch = lines[index].match(UNORDERED_LIST_RE);
        if (!itemMatch) break;
        items.push(itemMatch[1]);
        index += 1;
      }
      blocks.push(
        <ul className="markdown-list" key={blockKey}>
          {items.map((item, itemIndex) => (
            <li key={`${blockKey}-item-${itemIndex}`}>
              {renderInlineMarkdown(item, `${blockKey}-item-${itemIndex}`)}
            </li>
          ))}
        </ul>
      );
      continue;
    }

    const orderedMatch = line.match(ORDERED_LIST_RE);
    if (orderedMatch) {
      const items = [];
      while (index < lines.length) {
        const itemMatch = lines[index].match(ORDERED_LIST_RE);
        if (!itemMatch) break;
        items.push(itemMatch[1]);
        index += 1;
      }
      blocks.push(
        <ol className="markdown-list" key={blockKey}>
          {items.map((item, itemIndex) => (
            <li key={`${blockKey}-item-${itemIndex}`}>
              {renderInlineMarkdown(item, `${blockKey}-item-${itemIndex}`)}
            </li>
          ))}
        </ol>
      );
      continue;
    }

    const paragraphLines = [];
    while (index < lines.length && lines[index].trim() && !isMarkdownBlockStart(lines, index)) {
      paragraphLines.push(lines[index].trim());
      index += 1;
    }
    blocks.push(
      <p className="markdown-paragraph" key={blockKey}>
        {renderInlineMarkdown(paragraphLines.join(" "), blockKey)}
      </p>
    );
  }

  return <div className="text-content markdown-body">{blocks}</div>;
}
