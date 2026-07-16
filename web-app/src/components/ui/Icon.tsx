import type { HTMLAttributes } from "react";

type IconName = "explore" | "changes" | "reasoning" | "constraints" | "fibo" | "activity" | "assistant" | "settings" | "account" | "search" | "close";

const glyphs: Record<IconName, string> = {
  explore: "◈",
  changes: "◇",
  reasoning: "∴",
  constraints: "⊡",
  fibo: "◎",
  activity: "↗",
  assistant: "✦",
  settings: "⚙",
  account: "●",
  search: "⌕",
  close: "×",
};

export default function Icon({ name, ...props }: { name: IconName } & HTMLAttributes<HTMLSpanElement>) {
  return <span className="ui-icon" aria-hidden="true" {...props}>{glyphs[name]}</span>;
}
