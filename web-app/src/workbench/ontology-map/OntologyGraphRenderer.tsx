import { useEffect, useMemo, useRef, useState, type KeyboardEvent, type PointerEvent } from "react";
import type { WebOntologyGraphEdge, WebOntologyGraphNode } from "../../web/contracts";
import { anchoredScroll, clampZoom, curvedEdgePath, directionalNeighbor, draggedPoint, edgeLabelPoint, graphNodeSize, graphWorldBounds, layeredGraphLayout, type GraphPoint } from "./graphLayout";

const DRAG_THRESHOLD = 4;
const kindMark = { Class: "C", ObjectProperty: "OP", DatatypeProperty: "DP", Individual: "I" } as const;

export interface RendererState { selectedNodeId: string | null; positions?: Record<string, GraphPoint>; zoom?: number }

export default function OntologyGraphRenderer({ nodes, edges, state, childCounts = {}, dimmedNodeIds = new Set(), dimmedEdgeIds = new Set(), onStateChange, onViewDetails }: {
  nodes: WebOntologyGraphNode[];
  edges: WebOntologyGraphEdge[];
  state: RendererState;
  childCounts?: Record<string, number>;
  dimmedNodeIds?: Set<string>;
  dimmedEdgeIds?: Set<string>;
  onStateChange: (state: RendererState) => void;
  onViewDetails: (node: WebOntologyGraphNode) => void;
}) {
  const viewport = useRef<HTMLDivElement>(null);
  const geometryKey = `${nodes.map((node) => node.identity.id).join("\u0001")}\u0002${edges.map((edge) => edge.id).join("\u0001")}`;
  // Presentation-only selection and emphasis create new arrays without changing graph geometry.
  const defaultPositions = useMemo(() => layeredGraphLayout(nodes, edges), [geometryKey]);
  const positions = Object.fromEntries(nodes.map((node) => [node.identity.id, state.positions?.[node.identity.id] ?? defaultPositions[node.identity.id]]));
  const persistedPositions = { ...state.positions, ...positions };
  const [initialZoom, setInitialZoom] = useState<number | null>(null);
  const zoom = state.zoom ?? initialZoom ?? 1;
  const bounds = graphWorldBounds(positions);
  const pointer = useRef<{ id: string; x: number; y: number; origin: GraphPoint; dragged: boolean } | null>(null);
  const suppressClick = useRef(false);
  const pan = useRef<{ x: number; y: number; left: number; top: number } | null>(null);
  const initiallyFocused = useRef(false);
  const [spaceHeld, setSpaceHeld] = useState(false);
  const [panning, setPanning] = useState(false);
  const nodesById = useMemo(() => new Map(nodes.map((node) => [node.identity.id, node])), [nodes]);

  useEffect(() => {
    const element = viewport.current;
    if (!element || initiallyFocused.current || !nodes.length) return;
    if (state.zoom === undefined && initialZoom === null) {
      const fitZoom = clampZoom(Math.min(1, (element.clientWidth - 40) / bounds.width, (element.clientHeight - 40) / bounds.height));
      if (fitZoom < 1) {
        setInitialZoom(fitZoom);
        return;
      }
    }
    const first = [...nodes].sort((a, b) => positions[a.identity.id].x - positions[b.identity.id].x || positions[a.identity.id].y - positions[b.identity.id].y || a.identity.id.localeCompare(b.identity.id))[0];
    const target = positions[state.selectedNodeId ?? ""] ?? positions[first.identity.id];
    if (!target) return;
    initiallyFocused.current = true;
    element.scrollLeft = Math.max(0, (target.x + graphNodeSize.width / 2 - bounds.minX) * zoom - element.clientWidth / 2);
    element.scrollTop = Math.max(0, (target.y + graphNodeSize.height / 2 - bounds.minY) * zoom - element.clientHeight / 2);
  }, [bounds.height, bounds.minX, bounds.minY, bounds.width, initialZoom, nodes, positions, state.selectedNodeId, state.zoom, zoom]);

  function setZoom(nextValue: number, clientX?: number, clientY?: number) {
    const element = viewport.current;
    const next = clampZoom(nextValue);
    if (!element || next === zoom) return;
    const rect = element.getBoundingClientRect();
    const anchorX = clientX === undefined ? element.clientWidth / 2 : clientX - rect.left;
    const anchorY = clientY === undefined ? element.clientHeight / 2 : clientY - rect.top;
    onStateChange({ ...state, positions: persistedPositions, zoom: next });
    requestAnimationFrame(() => { element.scrollLeft = anchoredScroll(element.scrollLeft, anchorX, zoom, next); element.scrollTop = anchoredScroll(element.scrollTop, anchorY, zoom, next); });
  }

  useEffect(() => {
    const element = viewport.current;
    if (!element) return;
    const capturePinch = (event: globalThis.WheelEvent) => {
      if (!event.ctrlKey) return;
      event.preventDefault();
      setZoom(zoom * Math.exp(-event.deltaY * 0.002), event.clientX, event.clientY);
    };
    element.addEventListener("wheel", capturePinch, { passive: false });
    return () => element.removeEventListener("wheel", capturePinch);
  }, [zoom, state, persistedPositions]);

  function nodePointerDown(event: PointerEvent, id: string) {
    if (event.button !== 0) return;
    event.currentTarget.setPointerCapture(event.pointerId);
    pointer.current = { id, x: event.clientX, y: event.clientY, origin: positions[id], dragged: false };
  }

  function nodePointerMove(event: PointerEvent) {
    const active = pointer.current;
    if (!active) return;
    const dx = (event.clientX - active.x) / zoom;
    const dy = (event.clientY - active.y) / zoom;
    const nextPoint = draggedPoint(active.origin, dx, dy, DRAG_THRESHOLD);
    if (!nextPoint) return;
    active.dragged = true;
    onStateChange({ ...state, zoom, positions: { ...persistedPositions, [active.id]: nextPoint } });
  }

  function nodePointerUp() {
    suppressClick.current = Boolean(pointer.current?.dragged);
    pointer.current = null;
  }

  function selectNode(id: string) {
    if (suppressClick.current) {
      suppressClick.current = false;
      return;
    }
    onStateChange({ ...state, positions: persistedPositions, zoom, selectedNodeId: id });
  }

  function keyNavigate(event: KeyboardEvent<HTMLButtonElement>, id: string) {
    const direction = event.key === "ArrowLeft" ? "left" : event.key === "ArrowRight" ? "right" : event.key === "ArrowUp" ? "up" : event.key === "ArrowDown" ? "down" : null;
    if (!direction) return;
    const next = directionalNeighbor(id, direction, positions);
    if (!next) return;
    event.preventDefault();
    onStateChange({ ...state, positions: persistedPositions, zoom, selectedNodeId: next });
    document.getElementById(`ontology-node-${next}`)?.focus();
  }

  function fit() {
    const element = viewport.current;
    if (!element) return;
    setZoom(Math.min(element.clientWidth / bounds.width, element.clientHeight / bounds.height));
    requestAnimationFrame(() => { element.scrollTo({ left: 0, top: 0, behavior: matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" }); });
  }

  function reset() {
    if (!window.confirm("Reset the temporary map layout and zoom?")) return;
    onStateChange({ ...state, positions: { ...state.positions, ...layeredGraphLayout(nodes, edges) }, zoom: 1 });
    viewport.current?.scrollTo({ left: 0, top: 0 });
  }

  return <div className="ontology-graph" onKeyDown={(event) => { if (event.key === "Escape" && state.selectedNodeId) onStateChange({ ...state, selectedNodeId: null }); }}>
    <div className="ontology-graph-controls"><button type="button" onClick={() => setZoom(zoom - 0.1)} aria-label="Zoom out">−</button><output aria-label="Zoom percentage">{Math.round(zoom * 100)}%</output><button type="button" onClick={() => setZoom(zoom + 0.1)} aria-label="Zoom in">+</button><button type="button" onClick={fit}>Fit</button><button type="button" onClick={reset}>Reset</button></div>
    <div className={`ontology-graph-viewport ${panning ? "panning" : ""}`} ref={viewport} tabIndex={0} onContextMenu={(event) => event.preventDefault()} onKeyDown={(event) => { if (event.code === "Space") setSpaceHeld(true); }} onKeyUp={(event) => { if (event.code === "Space") setSpaceHeld(false); }} onPointerDown={(event) => { const overNode = (event.target as Element).closest(".ontology-node"); if (spaceHeld || event.button === 1 || event.button === 2 || (event.button === 0 && !overNode)) { event.preventDefault(); pan.current = { x: event.clientX, y: event.clientY, left: event.currentTarget.scrollLeft, top: event.currentTarget.scrollTop }; setPanning(true); event.currentTarget.setPointerCapture(event.pointerId); } }} onPointerMove={(event) => { if (pan.current) { event.currentTarget.scrollLeft = pan.current.left - (event.clientX - pan.current.x); event.currentTarget.scrollTop = pan.current.top - (event.clientY - pan.current.y); } }} onPointerUp={() => { pan.current = null; setPanning(false); }} onPointerCancel={() => { pan.current = null; setPanning(false); }}>
      <div className="ontology-graph-world" style={{ width: bounds.width * zoom, height: bounds.height * zoom }}>
        <svg width={bounds.width * zoom} height={bounds.height * zoom} role="img" aria-label="Loaded ontology graph">
          <defs>
            <marker id="ontology-arrow" markerWidth="9" markerHeight="9" refX="8" refY="4" orient="auto"><path d="M0,0 L9,4 L0,8 z" /></marker>
          </defs>
          <g transform={`scale(${zoom}) translate(${-bounds.minX} ${-bounds.minY})`}>
            {edges.map((edge) => { const from = positions[edge.sourceNodeId]; const to = positions[edge.targetNodeId]; if (!from || !to) return null; const label = edgeLabelPoint(from, to); const crossLink = edge.kind !== "SubclassOf" && edge.kind !== "Domain" && edge.kind !== "Range" && edge.kind !== "Type"; return <g key={edge.id} className={`ontology-edge edge-${edge.kind} ${crossLink ? "cross-link" : ""} ${dimmedEdgeIds.has(edge.id) ? "dimmed" : ""}`}><path d={curvedEdgePath(from, to)} markerEnd="url(#ontology-arrow)" /><text x={label.x} y={label.y}>{edge.label}</text></g>; })}
            {nodes.map((node) => { const point = positions[node.identity.id]; const childCount = childCounts[node.identity.id] ?? 0; return <foreignObject key={node.identity.id} x={point.x} y={point.y} width={graphNodeSize.width} height={graphNodeSize.height}><button id={`ontology-node-${node.identity.id}`} className={`ontology-node node-${node.kind} ${state.selectedNodeId === node.identity.id ? "selected" : ""} ${dimmedNodeIds.has(node.identity.id) ? "dimmed" : ""}`} type="button" aria-label={`${node.kind}: ${node.label}`} onClick={() => selectNode(node.identity.id)} onPointerDown={(event) => nodePointerDown(event, node.identity.id)} onPointerMove={nodePointerMove} onPointerUp={nodePointerUp} onPointerCancel={() => { pointer.current = null; suppressClick.current = false; }} onKeyDown={(event) => keyNavigate(event, node.identity.id)} onDoubleClick={() => onViewDetails(node)}><span aria-hidden="true">{kindMark[node.kind]}</span><strong>{node.label}{childCount ? <small>{childCount} children</small> : null}</strong></button></foreignObject>; })}
          </g>
        </svg>
      </div>
    </div>
    <details className="ontology-loaded-list"><summary>Loaded entities ({nodes.length})</summary><ul>{nodes.map((node) => <li key={node.identity.id}><button type="button" onClick={() => { onStateChange({ ...state, positions: persistedPositions, zoom, selectedNodeId: node.identity.id }); document.getElementById(`ontology-node-${node.identity.id}`)?.focus(); }}>{node.label} <small>{node.kind}</small></button></li>)}</ul></details>
  </div>;
}
