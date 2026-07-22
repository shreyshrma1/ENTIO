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
  const classes = ordered.filter((node) => node.kind === "Class");
  const classIds = new Set(classes.map((node) => node.identity.id));
  const facts = classOrderingFacts(classes, graphEdges);
  const compare = (a: WebOntologyGraphNode, b: WebOntologyGraphNode) => compareClasses(a, b, facts);
  const parents = new Map(classes.map((node) => [node.identity.id, [] as string[]]));
  graphEdges.filter((edge) => edge.kind === "SubclassOf" && classIds.has(edge.sourceNodeId) && classIds.has(edge.targetNodeId)).forEach((edge) => parents.get(edge.sourceNodeId)!.push(edge.targetNodeId));
  const byId = new Map(ordered.map((node) => [node.identity.id, node]));
  const primaryParent = new Map<string, string>();
  parents.forEach((items, child) => {
    const selected = items.map((id) => byId.get(id)!).sort(compare)[0];
    if (selected) primaryParent.set(child, selected.identity.id);
  });
  const children = new Map(classes.map((node) => [node.identity.id, [] as WebOntologyGraphNode[]]));
  primaryParent.forEach((parent, child) => children.get(parent)?.push(byId.get(child)!));
  children.forEach((items) => items.sort(compare));
  const roots = classes.filter((node) => !primaryParent.has(node.identity.id)).sort(compare);
  const positions: Record<string, GraphPoint> = {};
  const placed = new Set<string>();
  let nextRow = 0;
  function placeClass(node: WebOntologyGraphNode, depth: number): number {
    if (placed.has(node.identity.id)) return positions[node.identity.id]?.y ?? WORLD_PADDING + nextRow * (NODE_HEIGHT + ROW_GAP);
    placed.add(node.identity.id);
    const branch = children.get(node.identity.id)!.filter((child) => !placed.has(child.identity.id));
    const childRows = branch.map((child) => placeClass(child, depth + 1));
    const y = childRows.length ? (childRows[0] + childRows[childRows.length - 1]) / 2 : WORLD_PADDING + nextRow++ * (NODE_HEIGHT + ROW_GAP);
    positions[node.identity.id] = { x: WORLD_PADDING + depth * (NODE_WIDTH + LAYER_GAP), y };
    return y;
  }
  roots.forEach((root) => { placeClass(root, 0); nextRow += 1; });
  classes.filter((node) => !placed.has(node.identity.id)).sort(compare).forEach((node) => { placeClass(node, 0); nextRow += 1; });

  const occupiedByLane = new Map<number, number[]>();
  classes.forEach((node) => {
    const point = positions[node.identity.id];
    occupiedByLane.set(point.x, [...(occupiedByLane.get(point.x) ?? []), point.y]);
  });
  ordered.filter((node) => node.kind !== "Class").forEach((node) => {
    const anchorEdge = graphEdges.find((edge) => edge.sourceNodeId === node.identity.id && ((node.kind === "Individual" && edge.kind === "Type") || (node.kind !== "Individual" && edge.kind === "Domain")));
    const anchor = anchorEdge ? positions[anchorEdge.targetNodeId] : undefined;
    const x = anchor?.x ?? WORLD_PADDING;
    const desiredY = anchor ? anchor.y + NODE_HEIGHT + 16 : WORLD_PADDING + nextRow++ * (NODE_HEIGHT + ROW_GAP);
    const lane = occupiedByLane.get(x) ?? [];
    let y = desiredY;
    while (lane.some((taken) => Math.abs(taken - y) < NODE_HEIGHT + 16)) y += NODE_HEIGHT + 16;
    lane.push(y); occupiedByLane.set(x, lane); positions[node.identity.id] = { x, y };
  });
  return positions;
}

interface ClassFacts { subclasses: number; properties: number; individuals: number; relationships: number }

function classOrderingFacts(classes: WebOntologyGraphNode[], edges: WebOntologyGraphEdge[]): Map<string, ClassFacts> {
  const facts = new Map(classes.map((node) => [node.identity.id, { subclasses: 0, properties: 0, individuals: 0, relationships: 0 }]));
  edges.forEach((edge) => {
    if (edge.kind === "SubclassOf" && facts.has(edge.targetNodeId)) facts.get(edge.targetNodeId)!.subclasses += 1;
    if ((edge.kind === "Domain" || edge.kind === "Range") && facts.has(edge.targetNodeId)) facts.get(edge.targetNodeId)!.properties += 1;
    if (edge.kind === "Type" && facts.has(edge.targetNodeId)) facts.get(edge.targetNodeId)!.individuals += 1;
    if (facts.has(edge.sourceNodeId)) facts.get(edge.sourceNodeId)!.relationships += 1;
    if (facts.has(edge.targetNodeId)) facts.get(edge.targetNodeId)!.relationships += 1;
  });
  return facts;
}

function compareClasses(a: WebOntologyGraphNode, b: WebOntologyGraphNode, facts: Map<string, ClassFacts>): number {
  const left = facts.get(a.identity.id)!;
  const right = facts.get(b.identity.id)!;
  return right.subclasses - left.subclasses || right.properties - left.properties || right.individuals - left.individuals || right.relationships - left.relationships || a.label.localeCompare(b.label) || a.identity.entityIri.localeCompare(b.identity.entityIri);
}

export function classChildCounts(nodes: WebOntologyGraphNode[], edges: WebOntologyGraphEdge[]): Record<string, number> {
  const classIds = new Set(nodes.filter((node) => node.kind === "Class").map((node) => node.identity.id));
  const counts: Record<string, number> = {};
  classIds.forEach((id) => { counts[id] = 0; });
  edges.filter((edge) => edge.kind === "SubclassOf" && classIds.has(edge.sourceNodeId) && classIds.has(edge.targetNodeId)).forEach((edge) => { counts[edge.targetNodeId] += 1; });
  return counts;
}

export function positionsForExpansion(existing: Record<string, GraphPoint>, oldIds: Set<string>, nodes: WebOntologyGraphNode[], edges: WebOntologyGraphEdge[]): Record<string, GraphPoint> {
  const next = { ...existing };
  let fallback: Record<string, GraphPoint> | undefined;
  nodes.filter((node) => !oldIds.has(node.identity.id)).forEach((node, index) => {
    const outbound = edges.filter((candidate) => candidate.sourceNodeId === node.identity.id && next[candidate.targetNodeId]);
    const edge = outbound.find((candidate) => candidate.kind === "SubclassOf")
      ?? outbound.find((candidate) => candidate.kind === "Domain")
      ?? outbound.find((candidate) => candidate.kind === "Type")
      ?? outbound[0];
    const anchor = edge ? next[edge.targetNodeId] : undefined;
    if (anchor) {
      next[node.identity.id] = edge?.kind === "SubclassOf"
        ? { x: anchor.x + NODE_WIDTH + LAYER_GAP, y: anchor.y + index * (NODE_HEIGHT + 18) }
        : { x: anchor.x, y: anchor.y + NODE_HEIGHT + 16 + index * (NODE_HEIGHT + 18) };
    } else {
      fallback ??= layeredGraphLayout(nodes, edges);
      next[node.identity.id] = fallback[node.identity.id];
    }
  });
  return next;
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
