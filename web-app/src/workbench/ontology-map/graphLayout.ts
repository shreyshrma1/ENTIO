import type { WebOntologyGraphEdge, WebOntologyGraphNode } from "../../web/contracts";

export interface GraphPoint { x: number; y: number }
export interface GraphBounds { minX: number; minY: number; width: number; height: number }

const NODE_WIDTH = 184;
const NODE_HEIGHT = 72;
const LAYER_GAP = 116;
const ROW_GAP = 34;
export const WORLD_PADDING = 80;

export function layeredGraphLayout(nodes: WebOntologyGraphNode[], edges: WebOntologyGraphEdge[]): Record<string, GraphPoint> {
  const ordered = [...nodes].sort((a, b) => a.label.localeCompare(b.label) || a.identity.id.localeCompare(b.identity.id));
  const ids = new Set(ordered.map((node) => node.identity.id));
  const graphEdges = edges.filter((edge) => ids.has(edge.sourceNodeId) && ids.has(edge.targetNodeId));
  // Assertions may be cyclic by design. They influence row proximity but not the structural columns.
  const rankEdges = graphEdges.filter((edge) => edge.kind !== "ObjectAssertion");
  const components = stronglyConnectedComponents(ordered.map((node) => node.identity.id), rankEdges);
  const componentByNode = new Map(components.flatMap((component, index) => component.map((id) => [id, index] as const)));
  const outgoing = new Map(components.map((_, index) => [index, new Set<number>()]));
  const indegree = new Map(components.map((_, index) => [index, 0]));
  rankEdges.forEach((edge) => {
    const from = componentByNode.get(edge.sourceNodeId)!;
    const to = componentByNode.get(edge.targetNodeId)!;
    if (from === to || outgoing.get(from)!.has(to)) return;
    outgoing.get(from)!.add(to);
    indegree.set(to, indegree.get(to)! + 1);
  });
  const componentRank = new Map(components.map((_, index) => [index, 0]));
  const queue = [...components.keys()].filter((index) => indegree.get(index) === 0).sort((a, b) => componentName(components[a], ordered).localeCompare(componentName(components[b], ordered)));
  while (queue.length) {
    const current = queue.shift()!;
    [...outgoing.get(current)!].sort((a, b) => a - b).forEach((next) => {
      componentRank.set(next, Math.max(componentRank.get(next)!, componentRank.get(current)! + 1));
      indegree.set(next, indegree.get(next)! - 1);
      if (indegree.get(next) === 0) queue.push(next);
    });
  }
  const connected = new Set(rankEdges.flatMap((edge) => [edge.sourceNodeId, edge.targetNodeId]));
  const maxConnectedRank = Math.max(0, ...ordered.filter((node) => connected.has(node.identity.id)).map((node) => componentRank.get(componentByNode.get(node.identity.id)!)!));
  const layers = new Map<number, typeof ordered>();
  ordered.forEach((node) => {
    const rank = connected.has(node.identity.id) ? componentRank.get(componentByNode.get(node.identity.id)!)! : maxConnectedRank;
    layers.set(rank, [...(layers.get(rank) ?? []), node]);
  });
  const ranks = [...layers.keys()].sort((a, b) => a - b);
  for (let pass = 0; pass < 4; pass += 1) {
    reorderLayers(ranks, layers, graphEdges, "forward");
    reorderLayers([...ranks].reverse(), layers, graphEdges, "backward");
  }
  return Object.fromEntries(ranks.flatMap((rank) => layers.get(rank)!.map((node, row) => [node.identity.id, {
    x: WORLD_PADDING + rank * (NODE_WIDTH + LAYER_GAP),
    y: WORLD_PADDING + row * (NODE_HEIGHT + ROW_GAP),
  }] as const)));
}

function stronglyConnectedComponents(nodeIds: string[], edges: WebOntologyGraphEdge[]): string[][] {
  const adjacency = new Map(nodeIds.map((id) => [id, [] as string[]]));
  edges.forEach((edge) => adjacency.get(edge.sourceNodeId)?.push(edge.targetNodeId));
  adjacency.forEach((targets) => targets.sort());
  let nextIndex = 0;
  const stack: string[] = [];
  const onStack = new Set<string>();
  const index = new Map<string, number>();
  const low = new Map<string, number>();
  const components: string[][] = [];
  function visit(id: string) {
    index.set(id, nextIndex); low.set(id, nextIndex); nextIndex += 1; stack.push(id); onStack.add(id);
    adjacency.get(id)!.forEach((target) => {
      if (!index.has(target)) { visit(target); low.set(id, Math.min(low.get(id)!, low.get(target)!)); }
      else if (onStack.has(target)) low.set(id, Math.min(low.get(id)!, index.get(target)!));
    });
    if (low.get(id) !== index.get(id)) return;
    const component: string[] = [];
    let member: string;
    do { member = stack.pop()!; onStack.delete(member); component.push(member); } while (member !== id);
    components.push(component.sort());
  }
  nodeIds.forEach((id) => { if (!index.has(id)) visit(id); });
  return components;
}

function componentName(component: string[], nodes: WebOntologyGraphNode[]): string {
  const labels = new Map(nodes.map((node) => [node.identity.id, node.label]));
  return component.map((id) => labels.get(id) ?? id).sort()[0];
}

function reorderLayers(ranks: number[], layers: Map<number, WebOntologyGraphNode[]>, edges: WebOntologyGraphEdge[], direction: "forward" | "backward") {
  const rowById = () => new Map([...layers.values()].flatMap((layer) => layer.map((node, row) => [node.identity.id, row] as const)));
  ranks.forEach((rank) => {
    const rows = rowById();
    layers.get(rank)!.sort((a, b) => {
      const aMean = neighborMean(a.identity.id, edges, rows, direction);
      const bMean = neighborMean(b.identity.id, edges, rows, direction);
      return aMean - bMean || a.label.localeCompare(b.label) || a.identity.id.localeCompare(b.identity.id);
    });
  });
}

function neighborMean(id: string, edges: WebOntologyGraphEdge[], rows: Map<string, number>, direction: "forward" | "backward"): number {
  const neighbors = edges.flatMap((edge) => direction === "forward" && edge.targetNodeId === id ? [edge.sourceNodeId] : direction === "backward" && edge.sourceNodeId === id ? [edge.targetNodeId] : []);
  return neighbors.length ? neighbors.reduce((sum, neighbor) => sum + (rows.get(neighbor) ?? 0), 0) / neighbors.length : Number.MAX_SAFE_INTEGER;
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

export function curvedEdgePath(from: GraphPoint, to: GraphPoint): string {
  const a = boundaryPoint(from, to);
  const b = boundaryPoint(to, from);
  const dx = b.x - a.x;
  const bend = Math.max(36, Math.abs(dx) * 0.45);
  const direction = dx >= 0 ? 1 : -1;
  return `M ${a.x} ${a.y} C ${a.x + bend * direction} ${a.y}, ${b.x - bend * direction} ${b.y}, ${b.x} ${b.y}`;
}

export function edgeLabelPoint(from: GraphPoint, to: GraphPoint): GraphPoint {
  const a = boundaryPoint(from, to);
  const b = boundaryPoint(to, from);
  return { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 - 7 };
}

function boundaryPoint(from: GraphPoint, to: GraphPoint): GraphPoint {
  const center = { x: from.x + NODE_WIDTH / 2, y: from.y + NODE_HEIGHT / 2 };
  const target = { x: to.x + NODE_WIDTH / 2, y: to.y + NODE_HEIGHT / 2 };
  const dx = target.x - center.x;
  const dy = target.y - center.y;
  if (dx === 0 && dy === 0) return center;
  const scale = Math.min(Math.abs((NODE_WIDTH / 2) / (dx || Number.EPSILON)), Math.abs((NODE_HEIGHT / 2) / (dy || Number.EPSILON)));
  return { x: center.x + dx * scale, y: center.y + dy * scale };
}

export const graphNodeSize = { width: NODE_WIDTH, height: NODE_HEIGHT } as const;
