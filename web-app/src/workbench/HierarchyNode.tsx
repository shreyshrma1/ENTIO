import { useState } from "react";
import type { WebEntityReference, WebHierarchyItem } from "../web/projectApi";
import { useHierarchy } from "../web/queries";

interface HierarchyNodeProps {
  projectId: string;
  item: WebHierarchyItem;
  depth: number;
  onOpen: (entity: WebEntityReference) => void;
}

export default function HierarchyNode({ projectId, item, depth, onOpen }: HierarchyNodeProps) {
  const [expanded, setExpanded] = useState(false);
  const children = useHierarchy(projectId, item.sourceId, item.iri, expanded);
  const reference: WebEntityReference = {
    iri: item.iri,
    label: item.label,
    kind: item.kind,
    sourceId: item.sourceId,
  };

  return (
    <li className="hierarchy-node">
      <div className="hierarchy-row" style={{ paddingInlineStart: `${depth * 16}px` }}>
        {item.childCount > 0 ? (
          <button
            className="icon-button"
            type="button"
            aria-label={`${expanded ? "Collapse" : "Expand"} ${item.label}`}
            aria-expanded={expanded}
            onClick={() => setExpanded((value) => !value)}
          >
            {expanded ? "−" : "+"}
          </button>
        ) : (
          <span className="hierarchy-spacer" aria-hidden="true" />
        )}
        <button className="entity-link" type="button" onClick={() => onOpen(reference)}>
          <span>{item.label}</span>
          <small>{item.kind}</small>
        </button>
      </div>
      {expanded && children.isPending ? <p className="tree-status" role="status">Loading children...</p> : null}
      {expanded && children.isError ? <p className="tree-status" role="alert">Children unavailable.</p> : null}
      {expanded && children.isFetching && !children.isPending ? <p className="tree-status">Refreshing...</p> : null}
      {expanded && children.data?.page.items.length ? (
        <ul className="hierarchy-list">
          {children.data.page.items.map((child) => (
            <HierarchyNode key={`${child.sourceId}:${child.iri}`} projectId={projectId} item={child} depth={depth + 1} onOpen={onOpen} />
          ))}
        </ul>
      ) : null}
    </li>
  );
}
