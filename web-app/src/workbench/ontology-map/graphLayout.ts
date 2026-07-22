import type { WebOntologyGraphEdge, WebOntologyGraphNode } from "../../web/contracts";

export interface GraphPoint { x: number; y: number }
export interface GraphBounds { minX: number; minY: number; width: number; height: number }

const NODE_WIDTH = 184;
const NODE_HEIGHT = 72;
const LAYER_GAP = 120;
const ROW_GAP = 36;
export const WORLD_PADDING = 80;

export function layeredGraphLayout(nodes: WebOntologyGraphNode[], edges: WebOntologyGraphEdge[]): Record<string, GraphPoint> {
  const ordered = [...nodes].sort((a, b) => a.label.localeCompare(b.label) || a.identity.id.localeCompare(b.identity.id));
  const incoming = new Map(ordered.map((node) => [node.identity.id, 0]));
  edges.forEach((edge) => incoming.set(edge.targetNodeId, (incoming.get(edge.targetNodeId) ?? 0) + 1));
  const layers = new Map<string, number>();
  ordered.filter((node) => incoming.get(node.identity.id) === 0).forEach((node) => layers.set(node.identity.id, 0));
  for (let pass = 0; pass < ordered.length; pass += 1) {
    edges.forEach((edge) => {
      const sourceLayer = layers.get(edge.sourceNodeId);
      if (sourceLayer !== undefined) layers.set(edge.targetNodeId, Math.max(layers.get(edge.targetNodeId) ?? 0, sourceLayer + 1));
    });
  }
  ordered.forEach((node) => { if (!layers.has(node.identity.id)) layers.set(node.identity.id, 0); });
  const rows = new Map<number, number>();
  return Object.fromEntries(ordered.map((node) => {
    const layer = layers.get(node.identity.id) ?? 0;
    const row = rows.get(layer) ?? 0;
    rows.set(layer, row + 1);
    return [node.identity.id, { x: WORLD_PADDING + layer * (NODE_WIDTH + LAYER_GAP), y: WORLD_PADDING + row * (NODE_HEIGHT + ROW_GAP) }];
  }));
}

export function graphWorldBounds(positions: Record<string, GraphPoint>): GraphBounds {
  const points = Object.values(positions);
  if (!points.length) return { minX: 0, minY: 0, width: WORLD_PADDING * 2, height: WORLD_PADDING * 2 };
  const minX = Math.min(...points.map((point) => point.x)) - WORLD_PADDING;
  const minY = Math.min(...points.map((point) => point.y)) - WORLD_PADDING;
  const maxX = Math.max(...points.map((point) => point.x + NODE_WIDTH)) + WORLD_PADDING;
  const maxY = Math.max(...points.map((point) => point.y + NODE_HEIGHT)) + WORLD_PADDING;
  return { minX, minY, width: maxX - minX, height: maxY - minY };
}

export function clampZoom(value: number): number { return Math.min(2, Math.max(0.25, value)); }

export function anchoredScroll(scroll: number, anchor: number, previousZoom: number, nextZoom: number): number {
  return ((scroll + anchor) / previousZoom) * nextZoom - anchor;
}

export function draggedPoint(origin: GraphPoint, dx: number, dy: number, threshold = 4): GraphPoint | null {
  return Math.hypot(dx, dy) < threshold ? null : { x: origin.x + dx, y: origin.y + dy };
}

export function directionalNeighbor(currentId: string, direction: "left" | "right" | "up" | "down", positions: Record<string, GraphPoint>): string | null {
  const current = positions[currentId];
  if (!current) return null;
  const candidates = Object.entries(positions).filter(([id, point]) => id !== currentId && (
    direction === "left" ? point.x < current.x : direction === "right" ? point.x > current.x : direction === "up" ? point.y < current.y : point.y > current.y
  ));
  candidates.sort(([aId, a], [bId, b]) => {
    const aPrimary = direction === "left" || direction === "right" ? Math.abs(a.x - current.x) : Math.abs(a.y - current.y);
    const bPrimary = direction === "left" || direction === "right" ? Math.abs(b.x - current.x) : Math.abs(b.y - current.y);
    const aSecondary = direction === "left" || direction === "right" ? Math.abs(a.y - current.y) : Math.abs(a.x - current.x);
    const bSecondary = direction === "left" || direction === "right" ? Math.abs(b.y - current.y) : Math.abs(b.x - current.x);
    return aPrimary - bPrimary || aSecondary - bSecondary || aId.localeCompare(bId);
  });
  return candidates[0]?.[0] ?? null;
}

export const graphNodeSize = { width: NODE_WIDTH, height: NODE_HEIGHT } as const;
