type StatusTone = "asserted" | "inferred" | "external" | "staged" | "success" | "warning" | "danger" | "neutral";

export default function StatusBadge({ tone = "neutral", children }: { tone?: StatusTone; children: React.ReactNode }) {
  return <span className={`status-badge status-${tone}`}>{children}</span>;
}
