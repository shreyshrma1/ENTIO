import type { HTMLAttributes } from "react";

type IconName = "explore" | "documents" | "changes" | "reasoning" | "constraints" | "fibo" | "activity" | "settings" | "account" | "assistant" | "search" | "filter" | "close";

const glyphs: Record<IconName, string> = {
  explore: "◈",
  documents: "▤",
  changes: "◇",
  reasoning: "∴",
  constraints: "⊡",
  fibo: "◎",
  activity: "↗",
  settings: "⚙",
  account: "●",
  assistant: "✦",
  search: "⌕",
  filter: "≡",
  close: "×",
};

export default function Icon({ name, ...props }: { name: IconName } & HTMLAttributes<HTMLSpanElement>) {
  return <span className="ui-icon" aria-hidden="true" {...props}>{glyphs[name]}</span>;
}
