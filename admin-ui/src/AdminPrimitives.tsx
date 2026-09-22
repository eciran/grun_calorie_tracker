import { Key, KeyboardEvent, ReactNode, useEffect, useRef } from "react";
import { useAdminLocale } from "./admin/locale";
import { commonMessages, formatAdminNumber } from "./admin/commonMessages";

export type LoadState = "idle" | "loading" | "ready" | "error";

const DIALOG_FOCUSABLE = [
  "a[href]", "button:not([disabled])", "input:not([disabled])", "select:not([disabled])",
  "textarea:not([disabled])", "[tabindex]:not([tabindex='-1'])"
].join(",");

export function useDialogAccessibility<T extends HTMLElement = HTMLElement>(onClose: () => void, active = true) {
  const dialogRef = useRef<T>(null);
  const openerRef = useRef<HTMLElement | null>(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;

  useEffect(() => {
    if (!active) return;
    openerRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const dialog = dialogRef.current;
    const initial = dialog?.querySelector<HTMLElement>("[autofocus]")
      ?? dialog?.querySelector<HTMLElement>(DIALOG_FOCUSABLE)
      ?? dialog;
    window.requestAnimationFrame(() => initial?.focus());

    function handleKeyDown(event: globalThis.KeyboardEvent) {
      if (event.key === "Escape") {
        event.preventDefault();
        closeRef.current();
        return;
      }
      if (event.key !== "Tab" || !dialog) return;
      const focusable = Array.from(dialog.querySelectorAll<HTMLElement>(DIALOG_FOCUSABLE))
        .filter((element) => !element.hidden && element.getAttribute("aria-hidden") !== "true");
      if (!focusable.length) {
        event.preventDefault();
        dialog.focus();
        return;
      }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      const opener = openerRef.current;
      window.requestAnimationFrame(() => opener?.isConnected && opener.focus());
    };
  }, [active]);

  return dialogRef;
}

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
  const { locale } = useAdminLocale();
  const text = commonMessages[locale];
  return (
    <header className="section-toolbar">
      <div className="section-toolbar-copy">
        <div className="section-toolbar-title">
          <h2>{title}</h2>
          <span className={`load-state ${state}`} aria-live="polite">{text[state]}</span>
        </div>
        {description && <p>{description}</p>}
      </div>
      <div className="toolbar-actions">
        {children}
        <button
          aria-label={text.refreshLabel(title)}
          className="ghost-button"
          disabled={state === "loading"}
          onClick={onReload}
          type="button"
        >
          {state === "loading" ? text.refreshing : text.refresh}
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
  description,
  open,
  onToggle,
  children,
  className
}: {
  title: string;
  description?: string;
  open: boolean;
  onToggle: () => void;
  children: ReactNode;
  className?: string;
}) {
  return (
    <article className={`user-filter-panel collapsible-panel ${className ?? ""}`}>
      <button className="user-filter-toggle collapsible-panel-header" type="button" onClick={onToggle} aria-expanded={open}>
        <span><strong>{title}</strong>{description && <small>{description}</small>}</span>
        <b aria-hidden="true">{open ? "−" : "+"}</b>
      </button>
      {open && <div className="user-filter-content collapsible-panel-body">{children}</div>}
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
  const { locale } = useAdminLocale();
  const text = commonMessages[locale];
  if (!rows.length) {
    return <EmptyState title={text.noResults} message={empty} />;
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
    <div className="table-wrap responsive-data-table">
      <table>
        <caption className="sr-only">{caption ?? columns.join(", ")}</caption>
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
              {row.map((cell, cellIndex) => <td data-label={columns[cellIndex] ?? text.value} key={cellIndex}>{cell}</td>)}
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
  const { locale } = useAdminLocale();
  const text = commonMessages[locale];
  const formatNumber = (value: number) => formatAdminNumber(value, locale);
  const safeTotalPages = Math.max(totalPages || 1, 1);
  const from = totalElements === 0 ? 0 : page * pageSize + 1;
  const to = Math.min((page + 1) * pageSize, totalElements);
  return (
    <nav className="pagination-bar" aria-label={text.pagination}>
      <div aria-live="polite">
        <strong>{formatNumber(from)}-{formatNumber(to)}</strong>
        <span>{text.items(totalElements, formatNumber(totalElements))}</span>
      </div>
      <div className="pagination-actions">
        <label>
          {text.pageSize}
          <select value={pageSize} onChange={(event) => onPageSizeChange(Number(event.target.value))}>
            <option value={10}>10</option>
            <option value={20}>20</option>
            <option value={25}>25</option>
            <option value={50}>50</option>
            <option value={100}>100</option>
          </select>
        </label>
        <button aria-label={text.firstPage} className="ghost-button pagination-edge" disabled={first || page <= 0} onClick={() => onPageChange(0)} type="button">{text.first}</button>
        <button aria-label={text.previousPage} className="ghost-button" disabled={first || page <= 0} onClick={() => onPageChange(Math.max(0, page - 1))} type="button">{text.previous}</button>
        <span className="page-indicator" aria-current="page">{text.page(formatNumber(page + 1), formatNumber(safeTotalPages))}</span>
        <button aria-label={text.nextPage} className="ghost-button" disabled={last || page >= safeTotalPages - 1} onClick={() => onPageChange(Math.min(safeTotalPages - 1, page + 1))} type="button">{text.next}</button>
        <button aria-label={text.lastPage} className="ghost-button pagination-edge" disabled={last || page >= safeTotalPages - 1} onClick={() => onPageChange(safeTotalPages - 1)} type="button">{text.last}</button>
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
  loadingMessage?: string;
  emptyMessage?: string;
}) {
  const { locale } = useAdminLocale();
  const text = commonMessages[locale];
  if (state === "loading" && !hasData) {
    return <div className="async-state loading-state" role="status">{loadingMessage ?? text.loading}</div>;
  }
  if (state === "error" && !hasData) {
    return <div className="async-state error-state" role="alert">{text.loadFailed}</div>;
  }
  if (state === "ready" && !hasData) {
    return <EmptyState title={text.noResults} message={emptyMessage ?? text.empty} />;
  }
  return null;
}

export function EmptyState({ message, title }: { message?: string; title?: string }) {
  const { locale } = useAdminLocale();
  const text = commonMessages[locale];
  return (
    <div className="empty-state">
      <strong>{title ?? text.nothingHere}</strong>
      <span>{message ?? text.empty}</span>
    </div>
  );
}
