import { useEffect, useState } from "react";

import { formatRequestError, PageResponse, requestBlob } from "../api";

import { AuditEntry } from "../types";

import { DataTable, MetricCard, PaginationControls, Panel, SectionToolbar } from "../AdminPrimitives";

import { Badge, DetailItem, buildAuditPath, countBy, downloadBlob, formatDate, formatValue, humanizeFeature, useEndpoint } from "./../admin/shared";

export const AUDIT_ACTION_TYPES = [
  "SUBSCRIPTION_UPDATE", "AI_QUOTA_RESET", "AI_QUOTA_ADDON_GRANT", "AI_QUOTA_REFUND",
  "AI_REQUEST_INSPECT", "AI_CREDIT_PRICING_UPDATE", "SUBSCRIPTION_FEATURE_UPDATE",
  "SUBSCRIPTION_ENTITLEMENT_MATRIX_APPLY", "RETENTION_POLICY_UPDATE", "RECIPE_CREATE",
  "RECIPE_REVIEW_UPDATE", "USER_STATUS_UPDATE", "USER_SUPPORT_NOTE_CREATE", "USER_SESSION_REVOKE",
  "PRODUCT_QUALITY_AI_SETTINGS_UPDATE", "NOTIFICATION_CAMPAIGN_CREATE", "NOTIFICATION_CAMPAIGN_UPDATE",
  "NOTIFICATION_CAMPAIGN_SCHEDULE", "NOTIFICATION_CAMPAIGN_CANCEL", "PROMO_CREATE", "PROMO_UPDATE",
  "PROMO_ACTIVATE", "PROMO_DEACTIVATE", "PROMO_RECONCILE", "PROMO_REDEMPTION_RECORD",
  "ADMIN_ROLE_UPDATE", "ADMIN_STATUS_UPDATE", "ADMIN_MFA_STATUS_UPDATE"
];

export const AUDIT_TARGET_TYPES = [
  "USER_SUBSCRIPTION", "AI_REQUEST", "AI_CREDIT_PRICING", "SUBSCRIPTION_FEATURE", "RETENTION_POLICY",
  "RECIPE", "USER_ACCOUNT", "PRODUCT_QUALITY_AI_SETTINGS", "NOTIFICATION_CAMPAIGN", "PROMOTION", "ADMIN_ACCOUNT"
];

export function AuditsView({ onError }: { onError: (message: string | null) => void }) {
  const [actionType, setActionType] = useState("");
  const [targetType, setTargetType] = useState("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(25);
  const [selectedAudit, setSelectedAudit] = useState<AuditEntry | null>(null);
  const path = buildAuditPath({ actionType, targetType, page, size: pageSize });
  const { data, state, reload } = useEndpoint<PageResponse<AuditEntry>>(path, onError);
  const rows = data?.content ?? [];
  const actionCounts = countBy(rows, (item) => item.actionType ?? "Unknown");

  useEffect(() => {
    setPage(0);
  }, [actionType, targetType, pageSize]);

  async function exportAudits() {
    try {
      const params = new URLSearchParams();
      if (actionType) params.set("actionType", actionType);
      if (targetType) params.set("targetType", targetType);
      const blob = await requestBlob(`/api/v1/admin/audits/export?${params.toString()}`, { timeoutMs: 60000 });
      downloadBlob(blob, `grun-admin-audits-${new Date().toISOString().slice(0, 10)}.csv`);
    } catch (failure) {
      onError(formatRequestError(failure));
    }
  }

  return (
    <div className="stack">
      <SectionToolbar title="Admin action audits" state={state} onReload={reload}>
        <button className="ghost-button" onClick={() => { setActionType(""); setTargetType(""); }} type="button">Clear filters</button>
        <button className="ghost-button" onClick={exportAudits} type="button">Export CSV</button>
      </SectionToolbar>
      <div className="audit-summary-grid">
        <MetricCard label="Returned entries" value={formatValue(data?.totalElements ?? rows.length)} hint="Matching current audit filters" />
        <MetricCard label="Action types" value={formatValue(Object.keys(actionCounts).length)} hint="Types visible on this page" />
        <MetricCard label="Current page" value={formatValue((data?.page ?? page) + 1)} hint={`${formatValue(data?.totalPages ?? 1)} total page(s)`} />
      </div>
      <Panel title="Audit filters">
        <div className="audit-filter-grid">
          <label>
            Action type
            <select value={actionType} onChange={(event) => setActionType(event.target.value)}>
              <option value="">All actions</option>
              {AUDIT_ACTION_TYPES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
            </select>
          </label>
          <label>
            Target type
            <select value={targetType} onChange={(event) => setTargetType(event.target.value)}>
              <option value="">All targets</option>
              {AUDIT_TARGET_TYPES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
            </select>
          </label>
        </div>
      </Panel>
      <DataTable
        columns={["Action", "Target", "Admin", "Before", "After", "Created"]}
        rows={rows.map((item) => [
          <Badge value={item.actionType} />,
          `${item.targetType ?? "-"} #${item.targetKey ?? item.targetId ?? "-"}`,
          item.adminEmail ?? "-",
          <span className="truncate">{item.oldValue ?? item.details ?? "-"}</span>,
          <span className="truncate">{item.newValue ?? "-"}</span>,
          formatDate(item.createdAt)
        ])}
        rowData={rows}
        onRowClick={setSelectedAudit}
        empty="No audit entries returned."
      />
      <PaginationControls
        page={data?.page ?? page}
        pageSize={data?.size ?? pageSize}
        totalElements={data?.totalElements ?? rows.length}
        totalPages={data?.totalPages ?? 1}
        first={Boolean(data?.first)}
        last={Boolean(data?.last)}
        onPageChange={setPage}
        onPageSizeChange={setPageSize}
      />
      {selectedAudit && <AuditDetailsModal audit={selectedAudit} onClose={() => setSelectedAudit(null)} />}
    </div>
  );
}

export function AuditDetailsModal({ audit, onClose }: { audit: AuditEntry; onClose: () => void }) {
  const targetLabel = `${audit.targetType ?? "-"} #${audit.targetKey ?? audit.targetId ?? "-"}`;
  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <section className="audit-modal" role="dialog" aria-modal="true" aria-label="Audit action details" onClick={(event) => event.stopPropagation()}>
        <header className="modal-header">
          <div>
            <p className="eyebrow">Audit action detail</p>
            <h2>{humanizeFeature(audit.actionType)}</h2>
            <span>{targetLabel}</span>
          </div>
          <button className="icon-button" onClick={onClose} type="button" aria-label="Close">x</button>
        </header>
        <div className="audit-detail-layout">
          <Panel title="Action">
            <div className="readonly-grid">
              <DetailItem label="ID" value={audit.id} />
              <DetailItem label="Action type" value={audit.actionType} />
              <DetailItem label="Target type" value={audit.targetType} />
              <DetailItem label="Target key" value={audit.targetKey ?? audit.targetId} />
              <DetailItem label="Admin" value={audit.adminEmail} />
              <DetailItem label="Created" value={formatDate(audit.createdAt)} />
              <DetailItem label="Correlation ID" value={audit.correlationId} />
            </div>
          </Panel>
          <div className="audit-value-grid">
            <Panel title="Before">
              <pre className="audit-value-block">{formatAuditValue(audit.oldValue ?? audit.details)}</pre>
            </Panel>
            <Panel title="After">
              <pre className="audit-value-block">{formatAuditValue(audit.newValue)}</pre>
            </Panel>
          </div>
        </div>
      </section>
    </div>
  );
}

export function formatAuditValue(value?: string | null): string {
  if (!value) return "-";
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}
