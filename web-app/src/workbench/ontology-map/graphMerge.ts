import type { WebOntologyGraphEdge, WebOntologyGraphNode } from "../../web/contracts";

export function mergeGraphPage(currentNodes: WebOntologyGraphNode[], currentEdges: WebOntologyGraphEdge[], nextNodes: WebOntologyGraphNode[], nextEdges: WebOntologyGraphEdge[], nodeLimit: number, edgeLimit: number) {
  const allNodes = [...new Map([...currentNodes, ...nextNodes].map((node) => [node.identity.id, node])).values()];
  const nodes = allNodes.slice(0, nodeLimit);
  const ids = new Set(nodes.map((node) => node.identity.id));
  const allEdges = [...new Map([...currentEdges, ...nextEdges].map((edge) => [edge.id, edge])).values()].filter((edge) => ids.has(edge.sourceNodeId) && ids.has(edge.targetNodeId));
  const edges = allEdges.slice(0, edgeLimit);
  return { nodes, edges, limited: allNodes.length > nodeLimit || allEdges.length > edgeLimit };
}
