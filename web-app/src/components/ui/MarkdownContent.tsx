import type { ReactNode } from "react";

export default function MarkdownContent({ children }: { children: string }) {
  const blocks = children.trim().split(/\n\s*\n/);
  return <div className="markdown-content">{blocks.map((block, index) => renderBlock(block, index))}</div>;
}

function renderBlock(block: string, key: number): ReactNode {
  const lines = block.split("\n");
  const heading = lines[0].match(/^(#{1,4})\s+(.+)$/);
  if (heading && lines.length === 1) {
    const level = heading[1].length;
    const content = inline(heading[2]);
    if (level === 1) return <h1 key={key}>{content}</h1>;
    if (level === 2) return <h2 key={key}>{content}</h2>;
    if (level === 3) return <h3 key={key}>{content}</h3>;
    return <h4 key={key}>{content}</h4>;
  }
  if (lines.every((line) => /^\s*[-*]\s+/.test(line))) {
    return <ul key={key}>{lines.map((line, index) => <li key={index}>{inline(line.replace(/^\s*[-*]\s+/, ""))}</li>)}</ul>;
  }
  if (lines.every((line) => /^\s*\d+\.\s+/.test(line))) {
    return <ol key={key}>{lines.map((line, index) => <li key={index}>{inline(line.replace(/^\s*\d+\.\s+/, ""))}</li>)}</ol>;
  }
  return <p key={key}>{lines.map((line, index) => <span key={index}>{inline(line)}{index < lines.length - 1 ? <br /> : null}</span>)}</p>;
}

function inline(value: string): ReactNode[] {
  const tokens = value.split(/(`[^`]+`|\*\*[^*]+\*\*|\[[^\]]+\]\(https?:\/\/[^)]+\))/g);
  return tokens.filter(Boolean).map((token, index) => {
    if (token.startsWith("`") && token.endsWith("`")) return <code key={index}>{token.slice(1, -1)}</code>;
    if (token.startsWith("**") && token.endsWith("**")) return <strong key={index}>{token.slice(2, -2)}</strong>;
    const link = token.match(/^\[([^\]]+)\]\((https?:\/\/[^)]+)\)$/);
    if (link) return <a key={index} href={link[2]} target="_blank" rel="noreferrer">{link[1]}</a>;
    return token;
  });
}
