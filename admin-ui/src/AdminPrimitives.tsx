import { Key, KeyboardEvent, ReactNode } from "react";

export type LoadState = "idle" | "loading" | "ready" | "error";

const STATE_LABELS: Record<LoadState, string> = {
  idle: "Not loaded",
  loading: "Loading",
  ready: "Up to date",
  error: "Needs attention"
};

export function SectionToolbar({
  title,
  description,
  state,
  onReload,
  children
}: {
  title: string;
  description?: string;
  state: LoadState;
  onReload: () => void;
  children?: ReactNode;
}) {
  return (
    <header className="section-toolbar">
      <div className="section-toolbar-copy">
        <div className="section-toolbar-title">
          <h2>{title}</h2>
          <span className={`load-state ${state}`} aria-live="polite">{STATE_LABELS[state]}</span>
        </div>
        {description && <p>{description}</p>}
      </div>
      <div className="toolbar-actions">
        {children}
        <button
          aria-label={`Refresh ${title}`}
          className="ghost-button"
          disabled={state === "loading"}
          onClick={onReload}
          type="button"
        >
          {state === "loading" ? "Refreshing..." : "Refresh"}
        </button>
      </div>
    </header>
  );
}

export function MetricCard({ label, value, hint }: { label: string; value: string; hint: string }) {
  return (
    <article className="metric-card">
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{hint}</small>
    </article>
  );
}

export function Panel({
  title,
  description,
  actions,
  children,
  className
}: {
  title: string;
  description?: string;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  const hasExtendedHeading = Boolean(description || actions);
  return (
    <article className={`panel ${className ?? ""}`.trim()}>
      {hasExtendedHeading ? (
        <div className="panel-heading">
          <div>
            <h3>{title}</h3>
            {description && <p>{description}</p>}
          </div>
          {actions && <div className="panel-actions">{actions}</div>}
        </div>
      ) : (
        <h3>{title}</h3>
      )}
      {children}
    </article>
  );
}

export function CollapsiblePanel({
  title,
  open,
  onToggle,
  children
}: {
  title: string;
  open: boolean;
  onToggle: () => void;
  children: ReactNode;
}) {
  return (
    <article className="panel collapsible-panel">
      <button className="collapsible-panel-header" type="button" onClick={onToggle} aria-expanded={open}>
        <h3>{title}</h3>
        <span>{open ? "Hide" : "Show"}</span>
      </button>
      {open && <div className="collapsible-panel-body">{children}</div>}
    </article>
  );
}

export function DataTable<T = unknown>({
  columns,
  rows,
  empty,
  caption,
  rowData,
  rowKeys,
  onRowClick
}: {
  columns: string[];
  rows: ReactNode[][];
  empty: string;
  caption?: string;
  rowData?: T[];
  rowKeys?: Key[];
  onRowClick?: (row: T) => void;
}) {
  if (!rows.length) {
    return <EmptyState title="No results" message={empty} />;
  }

  function activateRow(index: number) {
    if (onRowClick && rowData?.[index]) {
      onRowClick(rowData[index]);
    }
  }

  function handleRowKeyDown(event: KeyboardEvent<HTMLTableRowElement>, index: number) {
    if (event.key !== "Enter" && event.key !== " ") return;
    event.preventDefault();
    activateRow(index);
  }

  return (
    <div className="table-wrap">
      <table>
        {caption && <caption className="sr-only">{caption}</caption>}
        <thead>
          <tr>
            {columns.map((column) => <th key={column} scope="col">{column}</th>)}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr
              className={onRowClick ? "clickable-row" : undefined}
              key={rowKeys?.[index] ?? index}
              onClick={() => activateRow(index)}
              onKeyDown={(event) => handleRowKeyDown(event, index)}
              tabIndex={onRowClick ? 0 : undefined}
            >
              {row.map((cell, cellIndex) => <td key={cellIndex}>{cell}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function PaginationControls({
  page,
  pageSize,
  totalElements,
  totalPages,
  first,
  last,
  onPageChange,
  onPageSizeChange
}: {
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
}) {
  const safeTotalPages = Math.max(totalPages || 1, 1);
  const from = totalElements === 0 ? 0 : page * pageSize + 1;
  const to = Math.min((page + 1) * pageSize, totalElements);
  return (
    <nav className="pagination-bar" aria-label="Table pagination">
      <div aria-live="polite">
        <strong>{formatNumber(from)}-{formatNumber(to)}</strong>
        <span>of {formatNumber(totalElements)} items</span>
      </div>
      <div className="pagination-actions">
        <label>
          Page size
          <select value={pageSize} onChange={(event) => onPageSizeChange(Number(event.target.value))}>
            <option value={10}>10</option>
            <option value={20}>20</option>
            <option value={25}>25</option>
            <option value={50}>50</option>
            <option value={100}>100</option>
          </select>
        </label>
        <button aria-label="First page" className="ghost-button" disabled={first || page <= 0} onClick={() => onPageChange(0)} type="button">First</button>
        <button aria-label="Previous page" className="ghost-button" disabled={first || page <= 0} onClick={() => onPageChange(Math.max(0, page - 1))} type="button">Previous</button>
        <span className="page-indicator" aria-current="page">Page {formatNumber(page + 1)} / {formatNumber(safeTotalPages)}</span>
        <button aria-label="Next page" className="ghost-button" disabled={last || page >= safeTotalPages - 1} onClick={() => onPageChange(Math.min(safeTotalPages - 1, page + 1))} type="button">Next</button>
        <button aria-label="Last page" className="ghost-button" disabled={last || page >= safeTotalPages - 1} onClick={() => onPageChange(safeTotalPages - 1)} type="button">Last</button>
      </div>
    </nav>
  );
}

export function AsyncState({
  state,
  hasData,
  loadingMessage,
  emptyMessage
}: {
  state: LoadState;
  hasData: boolean;
  loadingMessage: string;
  emptyMessage: string;
}) {
  if (state === "loading" && !hasData) {
    return <div className="async-state loading-state" role="status">{loadingMessage}</div>;
  }
  if (state === "error" && !hasData) {
    return <div className="async-state error-state" role="alert">This data could not be loaded. Review the error above and retry.</div>;
  }
  if (state === "ready" && !hasData) {
    return <EmptyState title="No results" message={emptyMessage} />;
  }
  return null;
}

export function EmptyState({ message, title = "Nothing here yet" }: { message: string; title?: string }) {
  return (
    <div className="empty-state">
      <strong>{title}</strong>
      <span>{message}</span>
    </div>
  );
}

function formatNumber(value: number): string {
  return new Intl.NumberFormat("en-GB").format(value);
}
