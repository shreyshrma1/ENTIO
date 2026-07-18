import { type MouseEvent, useState } from "react";
import type { WebEntityReference, WebHierarchyItem } from "../web/projectApi";
import { useHierarchy } from "../web/queries";
import { entityKindPresentation } from "./entityKindPresentation";

interface HierarchyNodeProps {
  projectId: string;
  item: WebHierarchyItem;
  depth: number;
  onOpen: (entity: WebEntityReference) => void;
  onContextMenu?: (event: MouseEvent, entity: WebEntityReference) => void;
  stagedIris?: ReadonlySet<string>;
  stagedChildrenByParent?: ReadonlyMap<string, WebHierarchyItem[]>;
}

export default function HierarchyNode({ projectId, item, depth, onOpen, onContextMenu, stagedIris, stagedChildrenByParent }: HierarchyNodeProps) {
  const [expanded, setExpanded] = useState(false);
  const stagedChildren = stagedChildrenByParent?.get(item.iri) ?? [];
  const hasAppliedChildren = item.childCount > 0;
  const hasChildren = hasAppliedChildren || stagedChildren.length > 0;
  const children = useHierarchy(projectId, item.sourceId, item.iri, expanded && hasAppliedChildren);
  const visibleChildren = [
    ...(children.data?.page.items ?? []).filter((child) => !stagedChildren.some((stagedChild) => stagedChild.iri === child.iri)),
    ...stagedChildren,
  ].sort((left, right) => left.label.localeCompare(right.label) || left.iri.localeCompare(right.iri));
  const reference: WebEntityReference = {
    iri: item.iri,
    label: item.label,
    kind: item.kind,
    sourceId: item.sourceId,
  };
  const presentation = entityKindPresentation(item.kind);

  return (
    <li className="hierarchy-node">
      <div className={`hierarchy-row ${stagedIris?.has(item.iri) ? "entity-staged" : ""}`} style={{ paddingInlineStart: `${depth * 16}px` }} onContextMenu={(event) => onContextMenu?.(event, reference)}>
        {hasChildren ? (
          <button
            className="hierarchy-toggle"
            type="button"
            aria-label={`${expanded ? "Collapse" : "Expand"} ${item.label}`}
            aria-expanded={expanded}
            onClick={() => setExpanded((value) => !value)}
          >
            <span className={`hierarchy-chevron ${expanded ? "hierarchy-chevron-expanded" : ""}`} aria-hidden="true" />
          </button>
        ) : (
          <span className="hierarchy-spacer" aria-hidden="true" />
        )}
        <button className="entity-link" type="button" aria-label={`${item.label}, ${presentation.label}`} onClick={() => onOpen(reference)}>
          <span className={`entity-type-marker ${presentation.className}`} aria-hidden="true">{presentation.marker}</span>
          <span className="entity-link-label">{item.label}</span>
        </button>
      </div>
      {expanded && hasAppliedChildren && children.isPending ? <p className="tree-status" role="status">Loading children...</p> : null}
      {expanded && hasAppliedChildren && children.isError ? <p className="tree-status" role="alert">Children unavailable.</p> : null}
      {expanded && hasAppliedChildren && children.isFetching && !children.isPending ? <p className="tree-status">Refreshing...</p> : null}
      {expanded && visibleChildren.length ? (
        <ul className="hierarchy-list">
          {visibleChildren.map((child) => (
            <HierarchyNode key={`${child.sourceId}:${child.iri}`} projectId={projectId} item={child} depth={depth + 1} onOpen={onOpen} onContextMenu={onContextMenu} stagedIris={stagedIris} stagedChildrenByParent={stagedChildrenByParent} />
          ))}
        </ul>
      ) : null}
    </li>
  );
}
