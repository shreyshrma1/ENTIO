import type { WebOntologyGraphEdge, WebOntologyGraphNode } from "../../web/contracts";

export interface GraphPoint { x: number; y: number }
export interface GraphBounds { minX: number; minY: number; width: number; height: number }

const NODE_WIDTH = 184;
const NODE_HEIGHT = 72;
const LAYER_GAP = 116;
const ROW_GAP = 34;
const TREE_GAP_X = 260;
const TREE_GAP_Y = 180;
const CLUSTER_GAP = 280;
const EDGE_LABEL_CLEARANCE = 24;
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
  const trees = buildClassTrees([...roots, ...classes.filter((node) => !roots.includes(node)).sort(compare)], children);
  const treeByClass = new Map(trees.flatMap((tree, index) => tree.ids.map((id) => [id, index] as const)));
  const treeLinks = propertyTreeLinks(graphEdges, treeByClass);
  const treeComponents = connectedTreeComponents(trees.length, treeLinks, trees);
  const positions: Record<string, GraphPoint> = {};
  let clusterX = WORLD_PADDING;
  let clusterY = WORLD_PADDING;
  let clusterRowHeight = 0;
  treeComponents.forEach((component) => {
    const cellWidth = Math.max(...component.map((index) => trees[index].width)) + TREE_GAP_X;
    const cellHeight = Math.max(...component.map((index) => trees[index].height)) + TREE_GAP_Y;
    const columns = component.length === 1 ? 1 : Math.max(2, Math.round(Math.sqrt(component.length * (cellHeight / cellWidth) * 1.35)));
    const width = columns * cellWidth - TREE_GAP_X;
    const height = Math.ceil(component.length / columns) * cellHeight - TREE_GAP_Y;
    if (clusterX > WORLD_PADDING && clusterX + width > 1_400) {
      clusterX = WORLD_PADDING;
      clusterY += clusterRowHeight + CLUSTER_GAP;
      clusterRowHeight = 0;
    }
    component.forEach((treeIndex, order) => {
      const tree = trees[treeIndex];
      const offsetX = clusterX + (order % columns) * cellWidth;
      const offsetY = clusterY + Math.floor(order / columns) * cellHeight;
      tree.positions.forEach((point, id) => { positions[id] = { x: point.x + offsetX, y: point.y + offsetY }; });
    });
    clusterX += width + CLUSTER_GAP;
    clusterRowHeight = Math.max(clusterRowHeight, height);
  });

  const occupied = Object.values(positions);
  ordered.filter((node) => node.kind !== "Class").forEach((node, index) => {
    const outbound = graphEdges.filter((edge) => edge.sourceNodeId === node.identity.id);
    const domain = outbound.find((edge) => edge.kind === "Domain" && positions[edge.targetNodeId]);
    const range = outbound.find((edge) => edge.kind === "Range" && positions[edge.targetNodeId]);
    const type = outbound.find((edge) => edge.kind === "Type" && positions[edge.targetNodeId]);
    const domainPoint = domain ? positions[domain.targetNodeId] : undefined;
    const rangePoint = range ? positions[range.targetNodeId] : undefined;
    const anchor = domainPoint ?? (type ? positions[type.targetNodeId] : undefined);
    const desired = domainPoint && rangePoint && domain?.targetNodeId !== range?.targetNodeId
      ? propertyMidpoint(domainPoint, rangePoint, node.identity.id)
      : anchor
        ? { x: anchor.x, y: anchor.y + NODE_HEIGHT + 26 }
        : { x: WORLD_PADDING + (index % 4) * (NODE_WIDTH + LAYER_GAP), y: clusterY + clusterRowHeight + TREE_GAP_Y };
    positions[node.identity.id] = nearestFreePoint(desired, occupied);
    occupied.push(positions[node.identity.id]);
  });
  return positions;
}

interface ClassTree { ids: string[]; positions: Map<string, GraphPoint>; width: number; height: number; rootId: string }

function buildClassTrees(candidates: WebOntologyGraphNode[], children: Map<string, WebOntologyGraphNode[]>): ClassTree[] {
  const assigned = new Set<string>();
  const trees: ClassTree[] = [];
  candidates.forEach((root) => {
    if (assigned.has(root.identity.id)) return;
    const positions = new Map<string, GraphPoint>();
    let leafRow = 0;
    let maxDepth = 0;
    function place(node: WebOntologyGraphNode, depth: number): number {
      if (assigned.has(node.identity.id)) return positions.get(node.identity.id)?.y ?? leafRow * (NODE_HEIGHT + ROW_GAP);
      assigned.add(node.identity.id);
      maxDepth = Math.max(maxDepth, depth);
      const branch = (children.get(node.identity.id) ?? []).filter((child) => !assigned.has(child.identity.id));
      const childRows = branch.map((child) => place(child, depth + 1));
      const y = childRows.length ? (childRows[0] + childRows[childRows.length - 1]) / 2 : leafRow++ * (NODE_HEIGHT + ROW_GAP);
      positions.set(node.identity.id, { x: depth * (NODE_WIDTH + LAYER_GAP), y });
      return y;
    }
    place(root, 0);
    const ids = [...positions.keys()];
    trees.push({ ids, positions, width: NODE_WIDTH + maxDepth * (NODE_WIDTH + LAYER_GAP), height: NODE_HEIGHT + Math.max(0, leafRow - 1) * (NODE_HEIGHT + ROW_GAP), rootId: root.identity.id });
  });
  return trees;
}

function propertyTreeLinks(edges: WebOntologyGraphEdge[], treeByClass: Map<string, number>): Map<number, Map<number, number>> {
  const links = new Map<number, Map<number, number>>();
  const byProperty = new Map<string, WebOntologyGraphEdge[]>();
  edges.filter((edge) => edge.kind === "Domain" || edge.kind === "Range").forEach((edge) => byProperty.set(edge.sourceNodeId, [...(byProperty.get(edge.sourceNodeId) ?? []), edge]));
  byProperty.forEach((propertyEdges) => {
    const domains = propertyEdges.filter((edge) => edge.kind === "Domain").map((edge) => treeByClass.get(edge.targetNodeId)).filter((id): id is number => id !== undefined);
    const ranges = propertyEdges.filter((edge) => edge.kind === "Range").map((edge) => treeByClass.get(edge.targetNodeId)).filter((id): id is number => id !== undefined);
    domains.forEach((domain) => ranges.forEach((range) => {
      if (domain === range) return;
      links.set(domain, links.get(domain) ?? new Map()); links.set(range, links.get(range) ?? new Map());
      links.get(domain)!.set(range, (links.get(domain)!.get(range) ?? 0) + 1);
      links.get(range)!.set(domain, (links.get(range)!.get(domain) ?? 0) + 1);
    }));
  });
  return links;
}

function connectedTreeComponents(count: number, links: Map<number, Map<number, number>>, trees: ClassTree[]): number[][] {
  const remaining = new Set(Array.from({ length: count }, (_, index) => index));
  const components: number[][] = [];
  while (remaining.size) {
    const seed = [...remaining].sort((a, b) => (links.get(b)?.size ?? 0) - (links.get(a)?.size ?? 0) || trees[a].rootId.localeCompare(trees[b].rootId))[0];
    const queue = [seed];
    const component: number[] = [];
    remaining.delete(seed);
    while (queue.length) {
      const current = queue.shift()!;
      component.push(current);
      [...(links.get(current)?.entries() ?? [])].sort(([a, aw], [b, bw]) => bw - aw || trees[a].rootId.localeCompare(trees[b].rootId)).forEach(([neighbor]) => {
        if (!remaining.delete(neighbor)) return;
        queue.push(neighbor);
      });
    }
    components.push(component);
  }
  return components;
}

function propertyMidpoint(domain: GraphPoint, range: GraphPoint, id: string): GraphPoint {
  const direction = stableParity(id) ? 1 : -1;
  const dx = range.x - domain.x;
  const dy = range.y - domain.y;
  const length = Math.max(1, Math.hypot(dx, dy));
  return {
    x: (domain.x + range.x) / 2 - (dy / length) * 58 * direction,
    y: (domain.y + range.y) / 2 + (dx / length) * 58 * direction,
  };
}

function nearestFreePoint(desired: GraphPoint, occupied: GraphPoint[]): GraphPoint {
  for (let ring = 0; ring <= 10; ring += 1) {
    const candidates = ring === 0 ? [desired] : [
      { x: desired.x + ring * (NODE_WIDTH + EDGE_LABEL_CLEARANCE), y: desired.y },
      { x: desired.x - ring * (NODE_WIDTH + EDGE_LABEL_CLEARANCE), y: desired.y },
      { x: desired.x, y: desired.y + ring * (NODE_HEIGHT + 22) },
      { x: desired.x, y: desired.y - ring * (NODE_HEIGHT + 22) },
    ];
    const free = candidates.find((candidate) => occupied.every((point) => Math.abs(point.x - candidate.x) >= NODE_WIDTH + EDGE_LABEL_CLEARANCE || Math.abs(point.y - candidate.y) >= NODE_HEIGHT + EDGE_LABEL_CLEARANCE));
    if (free) return free;
  }
  return { x: desired.x, y: desired.y + occupied.length * (NODE_HEIGHT + 22) };
}

function stableParity(value: string): boolean {
  return [...value].reduce((sum, character) => sum + character.charCodeAt(0), 0) % 2 === 0;
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

export function anchoredScroll(
  scroll: number,
  anchor: number,
  previousZoom: number,
  nextZoom: number,
  previousCanvasOffset: number = 0,
  nextCanvasOffset: number = 0,
): number {
  const graphCoordinate = (scroll + anchor - previousCanvasOffset) / previousZoom;
  return nextCanvasOffset + graphCoordinate * nextZoom - anchor;
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
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const length = Math.max(1, Math.hypot(dx, dy));
  return { x: (a.x + b.x) / 2 - (dy / length) * 14, y: (a.y + b.y) / 2 + (dx / length) * 14 };
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
