import { FormEvent, useState } from "react";

import { formatRequestError, request } from "../api";

import { RetentionPolicy, AdminGdprRequest, AdminGdprRequestPage } from "../types";

import { DataTable, LoadState, PaginationControls, Panel, SectionToolbar } from "../AdminPrimitives";

import { Badge, combineStates, formatDate, formatValue, shortFeature, useEndpoint } from "./../admin/shared";

export function RetentionPoliciesView({ onError }: { onError: (message: string | null) => void }) {
  const { data, state, reload } = useEndpoint<RetentionPolicy[]>("/api/v1/admin/legal/retention-policies", onError);
  const [selected, setSelected] = useState<RetentionPolicy | null>(null);
  const [draft, setDraft] = useState({ retentionDays: "", legalBasis: "", description: "", active: true });
  const [saveState, setSaveState] = useState<LoadState>("idle");
  const rows = data ?? [];
  const [gdprStatus, setGdprStatus] = useState("");
  const [gdprPage, setGdprPage] = useState(0);
  const [gdprPageSize, setGdprPageSize] = useState(10);
  const gdprQuery = new URLSearchParams({ page: String(gdprPage), size: String(gdprPageSize) });
  if (gdprStatus) gdprQuery.set("status", gdprStatus);
  const { data: gdprData, state: gdprState, reload: reloadGdpr } = useEndpoint<AdminGdprRequestPage>(
    `/api/v1/admin/legal/gdpr-requests?${gdprQuery.toString()}`,
    onError
  );

  function editPolicy(policy: RetentionPolicy) {
    setSelected(policy);
    setDraft({
      retentionDays: policy.retentionDays == null ? "" : String(policy.retentionDays),
      legalBasis: policy.legalBasis ?? "",
      description: policy.description ?? "",
      active: Boolean(policy.active)
    });
  }

  async function savePolicy(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected?.policyKey) return;
    setSaveState("loading");
    try {
      await request<RetentionPolicy>(`/api/v1/admin/legal/retention-policies/${selected.policyKey}`, {
        method: "PUT",
        body: {
          retentionDays: Number(draft.retentionDays),
          legalBasis: draft.legalBasis.trim(),
          description: draft.description.trim(),
          active: draft.active
        }
      });
      setSaveState("ready");
      setSelected(null);
      await reload();
    } catch (error) {
      setSaveState("error");
      onError(formatRequestError(error));
    }
  }


  return (
    <div className="stack">
      <SectionToolbar title="Retention policies" state={combineStates([state, saveState])} onReload={reload} />
      <DataTable
        columns={["Policy", "Days", "Legal basis", "Status", "Updated by", "Updated"]}
        rows={rows.map((item) => [
          shortFeature(item.policyKey),
          formatValue(item.retentionDays),
          item.legalBasis ?? "-",
          <Badge value={item.active ? "Active" : "Inactive"} tone={item.active ? "good" : "neutral"} />,
          item.updatedBy ?? "-",
          formatDate(item.updatedAt)
        ])}
        rowData={rows}
        onRowClick={editPolicy}
        empty="No retention policies returned."
      />
      <Panel title="GDPR request queue" description="Metadata-only export and deletion tracking. Export bodies and deleted account data are never exposed here.">
        <div className="section-toolbar-actions">
          <select value={gdprStatus} onChange={(event) => { setGdprStatus(event.target.value); setGdprPage(0); }}>
            <option value="">All statuses</option>
            <option value="IN_PROGRESS">In progress</option>
            <option value="ESCALATED">Escalated</option>
            <option value="FAILED">Failed</option>
            <option value="COMPLETED">Completed</option>
          </select>
          <button className="ghost-button" type="button" onClick={reloadGdpr}>Refresh queue</button>
          <span className="toolbar-state">{gdprState}</span>
        </div>
        <DataTable
          columns={["Type", "Status", "Anonymous subject", "Requested", "SLA due", "Result", "Evidence"]}
          rows={(gdprData?.content ?? []).map((item: AdminGdprRequest) => [
            item.requestType,
            <Badge value={item.status} tone={item.status === "COMPLETED" ? "good" : item.status === "FAILED" || item.status === "ESCALATED" ? "danger" : "warn"} />,
            `${item.subjectReference.slice(0, 12)}...`,
            formatDate(item.requestedAt),
            formatDate(item.dueAt),
            item.resultCode ?? item.failureSummary ?? "-",
            item.evidenceReference ?? "-"
          ])}
          empty="No GDPR operation metadata returned."
        />
        <PaginationControls
          page={gdprData?.page ?? gdprPage}
          pageSize={gdprData?.size ?? gdprPageSize}
          totalElements={gdprData?.totalElements ?? 0}
          totalPages={gdprData?.totalPages ?? 1}
          first={Boolean(gdprData?.first)}
          last={Boolean(gdprData?.last)}
          onPageChange={setGdprPage}
          onPageSizeChange={(size: number) => { setGdprPageSize(size); setGdprPage(0); }}
        />
      </Panel>
      {selected && <Panel title={`Edit ${shortFeature(selected.policyKey)} retention`}>
        <form className="review-filter-grid" onSubmit={savePolicy}>
          <label>
            Retention days
            <input value={draft.retentionDays} onChange={(event) => setDraft((current) => ({ ...current, retentionDays: event.target.value.replace(/[^0-9]/g, "") }))} required />
          </label>
          <label>
            Legal basis
            <input value={draft.legalBasis} onChange={(event) => setDraft((current) => ({ ...current, legalBasis: event.target.value }))} required />
          </label>
          <label>
            Description
            <textarea value={draft.description} onChange={(event) => setDraft((current) => ({ ...current, description: event.target.value }))} required />
          </label>
          <label className="inline-check">
            <input checked={draft.active} onChange={(event) => setDraft((current) => ({ ...current, active: event.target.checked }))} type="checkbox" />
            Active
          </label>
          <div className="form-actions">
            <button className="ghost-button" type="button" onClick={() => setSelected(null)}>Cancel</button>
            <button className="primary-button" type="submit" disabled={saveState === "loading"}>Save policy</button>
          </div>
        </form>
      </Panel>}
    </div>
  );
}
