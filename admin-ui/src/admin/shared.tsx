import { ReactNode, useEffect, useMemo, useRef, useState } from "react";

import { formatRequestError, PageResponse, request, requestBlob } from "../api";

import { AdminApprovalRequest, FoodProduct, FoodProductContribution, Notification, SystemHealth } from "../types";

import { DataTable, EmptyState, LoadState, MetricCard, PaginationControls, Panel, SectionToolbar, useDialogAccessibility } from "../AdminPrimitives";

import { SectionKey } from "./navigation";
import { useAdminLocale } from "./locale";
import { commonMessages } from "./commonMessages";

export type AdminTargetContext = {
  section: SectionKey;
    source: "notification" | "dashboard" | "inbox";
  notificationId?: number;
  severity?: string;
  type?: string;
  message?: string;
  targetType?: string;
  targetId?: string;
  targetRoute?: string;
};

export const PLAN_ORDER = ["FREE", "PLUS", "PRO"];

export const MARKET_REGIONS = ["GLOBAL", "TR", "UK_IE", "EU"];

export const VERIFICATION_STATUSES = ["RAW_IMPORTED", "NEEDS_REVIEW", "VERIFIED", "REJECTED"];

export const IMAGE_STATUSES = ["RAW", "NEEDS_REVIEW", "APPROVED", "REJECTED"];

export const IMAGE_SOURCES = ["OPEN_FOOD_FACTS", "ADMIN_UPLOAD", "USER_UPLOAD", "BRAND_OFFICIAL", "AI_GENERATED"];

export const PREFERRED_LANGUAGES = ["EN", "TR"];

export async function submitAdminApproval(actionType: string, targetKey: string, payload: unknown, reason: string) {
  return request<AdminApprovalRequest>("/api/v1/admin/approvals", {
    method: "POST",
    body: { actionType, targetKey, payload, reason }
  });
}

export function ApprovalSubmissionNotice({ request: approval, message, isOwner }: { request: AdminApprovalRequest; message: string; isOwner: boolean }) {
  const { locale } = useAdminLocale();
  return <div className="form-notice approval-submission-notice" role="status">
    <div><strong>{locale === "tr" ? `Onay talebi #${approval.id ?? "-"}` : `Approval request #${approval.id ?? "-"}`}</strong><span>{message}</span></div>
    {isOwner && approval.id && <a className="ghost-button compact" href={`/admin/approvals?status=PENDING&approvalId=${approval.id}`}>{locale === "tr" ? "Owner onay kaydını aç" : "Open owner approval record"}</a>}
  </div>;
}

export function FoodContributionReviewView({ onError }: { onError: (message: string | null) => void }) {
  const [status, setStatus] = useState("PENDING_REVIEW");
  const [marketRegion, setMarketRegion] = useState("TR");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [selected, setSelected] = useState<FoodProductContribution | null>(null);
  const [evidenceObjectUrl, setEvidenceObjectUrl] = useState<string | null>(null);
  const [evidenceState, setEvidenceState] = useState<LoadState>("idle");
  const [reviewNote, setReviewNote] = useState("");
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const contributionDialogRef = useDialogAccessibility<HTMLDivElement>(closeContribution);
  const query = new URLSearchParams({ page: String(page), size: String(pageSize) });
  if (status) query.set("status", status);
  if (marketRegion) query.set("marketRegion", marketRegion);
  const { data, state, reload } = useEndpoint<PageResponse<FoodProductContribution>>(
    `/api/v1/admin/products/contributions?${query.toString()}`,
    onError
  );
  const rows = data?.content ?? [];

  useEffect(() => {
    setPage(0);
  }, [status, marketRegion, pageSize]);

  useEffect(() => () => {
    if (evidenceObjectUrl) URL.revokeObjectURL(evidenceObjectUrl);
  }, [evidenceObjectUrl]);

  async function openContribution(item: FoodProductContribution) {
    setSelected(item);
    setReviewNote(item.reviewNote ?? "");
    setEvidenceState("loading");
    onError(null);
    try {
      const blob = await requestBlob(`/api/v1/admin/products/contributions/${item.id}/evidence`);
      setEvidenceObjectUrl(URL.createObjectURL(blob));
      setEvidenceState("ready");
    } catch (error) {
      setEvidenceState("error");
      onError(formatRequestError(error));
    }
  }

  function closeContribution() {
    setSelected(null);
    setReviewNote("");
    setEvidenceObjectUrl(null);
    setEvidenceState("idle");
  }

  async function review(decision: "APPROVED" | "REJECTED") {
    if (!selected?.id) return;
    if (decision === "REJECTED" && !reviewNote.trim()) {
      onError("A rejection note is required.");
      return;
    }
    setSaving(true);
    onError(null);
    try {
      await request<FoodProductContribution>(`/api/v1/admin/products/contributions/${selected.id}/review`, {
        method: "PATCH",
        body: {
          decision,
          reviewNote: reviewNote.trim() || "Label and nutrition evidence reviewed from admin panel."
        }
      });
      closeContribution();
      await reload();
      setNotice(decision === "APPROVED" ? "Contribution approved." : "Contribution rejected.");
      window.setTimeout(() => setNotice(null), 2500);
    } catch (error) {
      onError(formatRequestError(error));
    } finally {
      setSaving(false);
    }
  }

  async function exportLedger() {
    onError(null);
    try {
      const blob = await requestBlob("/api/v1/admin/products/contributions/evidence-ledger.tsv", { timeoutMs: 60000 });
      downloadBlob(blob, `tr-user-label-evidence-${new Date().toISOString().slice(0, 10)}.tsv`);
    } catch (error) {
      onError(formatRequestError(error));
    }
  }

  return (
    <div className="stack contribution-review-workspace">
      <SectionToolbar title="User label contribution review" state={state} onReload={reload}>
        <button className="ghost-button" type="button" onClick={exportLedger}>Export approved evidence</button>
      </SectionToolbar>
      {notice && <div className="success-banner compact-success">{notice}</div>}
      <div className="review-workspace-summary">
        <MetricCard label="Matching contributions" value={formatValue(data?.totalElements ?? rows.length)} hint="Current review filter" />
        <MetricCard label="Pending on page" value={formatValue(rows.filter((item) => item.status === "PENDING_REVIEW").length)} hint="Awaiting decision" />
        <MetricCard label="Approved on page" value={formatValue(rows.filter((item) => item.status === "APPROVED").length)} hint="Eligible for S9 export" />
        <MetricCard label="Private evidence" value={formatValue(rows.filter((item) => item.evidenceContentType).length)} hint="Stored label objects" />
      </div>
      <Panel title="Review filters">
        <div className="review-filter-grid contribution-filter-grid">
          <label>
            Decision status
            <select value={status} onChange={(event) => setStatus(event.target.value)}>
              <option value="">All</option>
              <option value="PENDING_REVIEW">Pending review</option>
              <option value="APPROVED">Approved</option>
              <option value="REJECTED">Rejected</option>
            </select>
          </label>
          <label>
            Market region
            <select value={marketRegion} onChange={(event) => setMarketRegion(event.target.value)}>
              <option value="">All</option>
              {MARKET_REGIONS.map((value) => <option key={value} value={value}>{value}</option>)}
            </select>
          </label>
        </div>
      </Panel>
      <DataTable
        columns={["Product", "Evidence", "Nutrition / 100 g", "Consent", "Status"]}
        rows={rows.map((item) => [
          <div className="table-stack"><strong>{item.productName ?? "Unnamed product"}</strong><span>{item.brand ?? "-"}</span><small>{item.barcode ?? "-"} · {item.marketRegion ?? "-"}</small></div>,
          <div className="table-stack"><span>{item.evidenceContentType ?? "Private object"}</span><small>{formatContributionBytes(item.evidenceSizeBytes)} · {formatDate(item.evidenceRetrievedAt)}</small></div>,
          <div className="table-stack"><span>{formatValue(item.calories)} kcal</span><small>P {formatValue(item.protein)} · C {formatValue(item.carbs)} · F {formatValue(item.fat)}</small></div>,
          <div className="table-stack"><span>{item.commercialUseAllowed ? "Commercial use" : "Missing commercial consent"}</span><small>{item.persistentStorageAllowed ? "Persistent storage" : "Storage not allowed"}</small></div>,
          <div className="badge-stack"><Badge value={item.status} tone={contributionStatusTone(item.status)} /><small>{formatDate(item.createdAt)}</small></div>
        ])}
        rowData={rows}
        onRowClick={openContribution}
        empty="No label contributions match this filter."
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
      {selected && (
        <div className="modal-backdrop" role="presentation" onClick={closeContribution}>
          <div ref={contributionDialogRef} tabIndex={-1} className="modal-card contribution-review-modal" role="dialog" aria-modal="true" aria-label="Review food label contribution" onClick={(event) => event.stopPropagation()}>
            <header className="modal-header">
              <div><span>PRIVATE LABEL EVIDENCE</span><h2>{selected.productName ?? "Product contribution"}</h2><p>{selected.brand ?? "-"} · {selected.barcode ?? "-"}</p></div>
              <button className="modal-icon-close" type="button" onClick={closeContribution} aria-label="Close contribution review">×</button>
            </header>
            <div className="contribution-review-body">
              <div className="contribution-evidence-panel">
                {evidenceState === "loading" && <div className="evidence-placeholder">Loading private evidence…</div>}
                {evidenceState === "error" && <div className="evidence-placeholder error">Evidence could not be loaded.</div>}
                {evidenceObjectUrl && <img src={evidenceObjectUrl} alt={`Submitted label for ${selected.productName ?? "product"}`} />}
                <small>Private object · no public URL · {formatContributionBytes(selected.evidenceSizeBytes)}</small>
              </div>
              <div className="contribution-review-details">
                <div className="contribution-detail-grid">
                  <DetailItem label="Market" value={selected.marketRegion} />
                  <DetailItem label="Submitted by" value={`User #${formatValue(selected.submittedByUserId)}`} />
                  <DetailItem label="Calories" value={`${formatValue(selected.calories)} kcal`} />
                  <DetailItem label="Protein" value={`${formatValue(selected.protein)} g`} />
                  <DetailItem label="Carbohydrate" value={`${formatValue(selected.carbs)} g`} />
                  <DetailItem label="Fat" value={`${formatValue(selected.fat)} g`} />
                  <DetailItem label="Serving" value={selected.servingSizeGrams ? `${formatValue(selected.servingSizeGrams)} ${selected.servingUnit ?? "g"}` : "Not supplied"} />
                  <DetailItem label="Submitted" value={formatDate(selected.createdAt)} />
                </div>
                <div className="contribution-consent-row">
                  <Badge value={selected.commercialUseAllowed ? "Commercial use allowed" : "Commercial consent missing"} tone={selected.commercialUseAllowed ? "good" : "danger"} />
                  <Badge value={selected.persistentStorageAllowed ? "Storage allowed" : "Storage consent missing"} tone={selected.persistentStorageAllowed ? "good" : "danger"} />
                </div>
                <div className="contribution-checksum"><span>SHA-256</span><code>{selected.evidenceChecksum ?? "-"}</code></div>
                <label>
                  Review note
                  <textarea maxLength={1000} value={reviewNote} onChange={(event) => setReviewNote(event.target.value)} placeholder="Record the evidence decision and any correction needed" />
                </label>
              </div>
            </div>
            <div className="modal-actions padded-actions">
              <button className="ghost-button" type="button" onClick={closeContribution}>Cancel</button>
              {selected.status === "PENDING_REVIEW" && <>
                <button className="ghost-button danger-button" type="button" disabled={saving || !reviewNote.trim()} onClick={() => review("REJECTED")}>Reject</button>
                <button className="primary-button" type="button" disabled={saving || evidenceState !== "ready"} onClick={() => review("APPROVED")}>Approve evidence</button>
              </>}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export function contributionStatusTone(status?: string): "good" | "warn" | "danger" | "neutral" {
  if (status === "APPROVED") return "good";
  if (status === "REJECTED") return "danger";
  if (status === "PENDING_REVIEW") return "warn";
  return "neutral";
}

export function formatContributionBytes(value?: number): string {
  if (value === undefined || value === null) return "Size unavailable";
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / (1024 * 1024)).toFixed(1)} MB`;
}

export type AdminInvitation = {
  id?: number; email?: string; role?: string; status?: string; invitedBy?: string;
  expiresAt?: string; acceptedAt?: string; revokedAt?: string; createdAt?: string;
};

export function GlobalSettingsView() {

  return (
    <div className="stack">
      <SectionToolbar title="Global settings" state="ready" onReload={() => undefined} />
      <Panel title="Configuration workspace">
        <div className="roadmap-strip">
          <span>Runtime configuration remains backend controlled</span>
          <span>Secrets are never exposed in admin UI</span>
          <span>Editable app settings should be added here only through audited APIs</span>
        </div>
      </Panel>
    </div>
  );
}

export function DatePickerButton({
  label,
  max,
  min,
  onChange,
  today,
  value
}: {
  label: string;
  max?: string;
  min?: string;
  onChange: (value: string) => void;
  today?: string;
  value: string;
}) {
  const { locale } = useAdminLocale();
  const [open, setOpen] = useState(false);
  const pickerRef = useRef<HTMLDivElement>(null);
  const [viewDate, setViewDate] = useState(() => parseDateInput(value) ?? new Date());
  useEffect(() => {
    const parsed = parseDateInput(value);
    if (parsed) setViewDate(parsed);
  }, [value]);
  useEffect(() => {
    if (!open) return;
    const closeOnOutsideInteraction = (event: PointerEvent) => {
      if (!pickerRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("pointerdown", closeOnOutsideInteraction);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("pointerdown", closeOnOutsideInteraction);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, [open]);
  const days = calendarDays(viewDate);
  const selected = parseDateInput(value);
  const todayValue = today ?? toDateInputValue(new Date());
  const todayDisabled = Boolean((min && todayValue < min) || (max && todayValue > max));
  const monthLabel = new Intl.DateTimeFormat(locale === "tr" ? "tr-TR" : "en-GB", { month: "long", year: "numeric" }).format(viewDate);
  const weekdays = locale === "tr" ? ["Pt", "Sa", "Ça", "Pe", "Cu", "Ct", "Pa"] : ["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"];
  return (
    <div className="admin-date-picker" ref={pickerRef}>
      <button aria-expanded={open} className="admin-date-trigger" onClick={() => setOpen((current) => !current)} type="button">
        <span>{value ? formatDateInputDisplay(value) : (locale === "tr" ? "Tarih seç" : "Select date")}</span>
        <small>{label}</small>
      </button>
      {open && (
        <div className="admin-calendar-popover">
          <div className="admin-calendar-head">
            <strong>{monthLabel}</strong>
            <div>
              <button onClick={() => setViewDate(addMonths(viewDate, -1))} type="button">&lt;</button>
              <button onClick={() => setViewDate(addMonths(viewDate, 1))} type="button">&gt;</button>
            </div>
          </div>
          <div className="admin-calendar-weekdays">
            {weekdays.map((item) => <span key={item}>{item}</span>)}
          </div>
          <div className="admin-calendar-grid">
            {days.map((day) => {
              const dayValue = toDateInputValue(day);
              const disabled = Boolean((min && dayValue < min) || (max && dayValue > max));
              const outside = day.getMonth() !== viewDate.getMonth();
              const active = selected ? dayValue === toDateInputValue(selected) : false;
              return (
                <button
                  className={[active ? "active" : "", outside ? "outside" : ""].filter(Boolean).join(" ")}
                  disabled={disabled}
                  key={dayValue}
                  onClick={() => {
                    onChange(dayValue);
                    setOpen(false);
                  }}
                  type="button"
                >
                  {day.getDate()}
                </button>
              );
            })}
          </div>
          <div className="admin-calendar-actions">
            <button disabled={todayDisabled} onClick={() => { onChange(todayValue); setOpen(false); }} type="button">{locale === "tr" ? "Bugün" : "Today"}</button>
            <button onClick={() => setOpen(false)} type="button">{locale === "tr" ? "Kapat" : "Close"}</button>
          </div>
        </div>
      )}
    </div>
  );
}

export function MiniNotificationList({ notifications }: { notifications: Notification[] }) {
  if (!notifications.length) {
    return <EmptyState message="No recent notification returned." />;
  }
  return (
    <div className="mini-notification-list">
      {notifications.map((item) => (
        <div className={item.read ? "mini-notification-row" : "mini-notification-row unread"} key={item.id ?? `${item.type}-${item.createdAt}-${item.message}`}>
          <div className="mini-notification-topline">
            <Badge value={item.severity ?? "INFO"} tone={notificationSeverityTone(item.severity)} />
            <Badge value={item.type} />
            {!item.read && <Badge value="Unread" tone="warn" />}
          </div>
          <p>{item.message ?? "-"}</p>
          <div className="mini-notification-meta">
            <small>{notificationTargetLabel(item)}</small>
            <small>{formatDate(item.createdAt)}</small>
          </div>
        </div>
      ))}
    </div>
  );
}

export function useEndpoint<T>(path: string, onError: (message: string | null) => void, enabled = true) {
  const [data, setData] = useState<T | null>(null);
  const [state, setState] = useState<LoadState>("idle");
  const [reloadToken, setReloadToken] = useState(0);
  const stablePath = useMemo(() => path, [path]);

  async function load() {
    setReloadToken((current) => current + 1);
  }

  useEffect(() => {
    let active = true;
    if (!enabled) {
      setData(null);
      setState("idle");
      return () => { active = false; };
    }
    setState("loading");
    onError(null);

    async function fetchData() {
      try {
        const nextData = await request<T>(stablePath);
        if (!active) return;
        setData(nextData);
        setState("ready");
      } catch (err) {
        if (!active) return;
        setState("error");
        onError(formatRequestError(err));
      }
    }

    void fetchData();
    return () => {
      active = false;
    };
  }, [stablePath, reloadToken, enabled]);

  return { data, state, reload: load };
}

export function TargetContextBanner({ context, onClear }: { context: AdminTargetContext; onClear?: () => void }) {
  return (
    <div className={`target-context-banner ${notificationSeverityTone(context.severity)}`}>
      <div>
        <span>Opened from {context.source === "notification" ? "admin notification" : "dashboard"}</span>
        <strong>{notificationTargetLabel(context)}</strong>
        <small>{context.message ?? "Target context is active for this workspace."}</small>
      </div>
      <div className="target-context-meta">
        {context.type && <Badge value={context.type} tone="neutral" />}
        {context.severity && <Badge value={context.severity} tone={notificationSeverityTone(context.severity)} />}
        {context.targetId && <span>Target #{context.targetId}</span>}
        {onClear && <button className="ghost-button" onClick={onClear} type="button">Clear focus</button>}
      </div>
    </div>
  );
}

export function TargetAwareValue({ value, focused }: { value: ReactNode; focused: boolean }) {
  return (
    <span className={focused ? "target-aware-value focused" : "target-aware-value"}>
      {focused && <i>Target</i>}
      <strong>{value}</strong>
    </span>
  );
}

export function DetailItem({ label, value }: { label: string; value?: string | number | null }) {
  return (
    <div className="detail-item">
      <span>{label}</span>
      <strong>{formatValue(value)}</strong>
    </div>
  );
}

export function EditableDetail({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="detail-item editable-detail">
      <span>{label}</span>
      {children}
    </div>
  );
}

export function MiniBarChart({ label, items }: { label: string; items: Array<[string, number]> }) {
  const max = Math.max(1, ...items.map(([, value]) => value));
  return (
    <article className="chart-card bars">
      <div>
        <h3>{label}</h3>
        <p>Last operational counters</p>
      </div>
      <div className="mini-bars">
        {items.length ? items.map(([name, value]) => (
          <div key={name}>
            <span>{name}</span>
            <div><i style={{ width: `${Math.round((value / max) * 100)}%` }} /></div>
            <strong>{formatValue(value)}</strong>
          </div>
        )) : <span className="muted-text">No quota mapping configured.</span>}
      </div>
    </article>
  );
}

export function ConfirmDialog({
  title,
  message,
  confirmLabel,
  danger = false,
  cancelLabel,
  busyLabel,
  dismissOnBackdrop = true,
  busy,
  onCancel,
  onConfirm
}: {
  title: string;
  message: string;
  confirmLabel: string;
  danger?: boolean;
  cancelLabel?: string;
  busyLabel?: string;
  dismissOnBackdrop?: boolean;
  busy: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const dialogRef = useDialogAccessibility(onCancel);
  const { locale } = useAdminLocale();
  const text = commonMessages[locale];
  return (
    <div className="modal-backdrop confirm-backdrop" role="presentation" onClick={() => { if (dismissOnBackdrop && !busy) onCancel(); }}>
      <section ref={dialogRef} tabIndex={-1} className="confirm-dialog" role="alertdialog" aria-modal="true" aria-label={title} onClick={(event) => event.stopPropagation()}>
        <div className="confirm-dialog-content">
          <div className={`confirm-dialog-icon ${danger ? "danger" : "neutral"}`} aria-hidden="true">{danger ? "!" : "i"}</div>
          <div className="confirm-dialog-copy">
            <p className="eyebrow">{danger ? text.confirmationRequired : text.confirmAction}</p>
            <h2>{title}</h2>
            <p>{message}</p>
          </div>
        </div>
        <div className="modal-actions">
          <button autoFocus className="ghost-button" disabled={busy} onClick={onCancel} type="button">{cancelLabel ?? text.cancel}</button>
          <button className={danger ? "primary-button danger-button" : "primary-button"} disabled={busy} onClick={onConfirm} type="button">
            {busy ? (busyLabel ?? text.saving) : confirmLabel}
          </button>
        </div>
      </section>
    </div>
  );
}

export function Badge({ value, tone = "default" }: { value?: string | null; tone?: "default" | "good" | "warn" | "danger" | "neutral" }) {
  const normalized = value || "-";
  const inferred = normalized.toLowerCase().includes("fail") || normalized.toLowerCase().includes("reject")
    ? "danger"
    : normalized.toLowerCase().includes("verified") || normalized.toLowerCase().includes("enabled")
      ? "good"
      : tone;
  return <span className={`badge ${inferred}`}>{normalized}</span>;
}

export function formatValue(value: unknown): string {
  if (value === null || value === undefined || value === "") return "-";
  if (typeof value === "number") return new Intl.NumberFormat("en-GB").format(value);
  return String(value);
}

export function formatRevenueCatMetric(metric: { value?: string; unit?: string }): string {
  const value = formatValue(metric.value);
  if (!metric.unit) {
    return value;
  }
  if (metric.unit === "EUR" || metric.unit === "USD" || metric.unit === "GBP") {
    return `${metric.unit} ${value}`;
  }
  return `${value} ${metric.unit}`;
}

export function formatRevenueCatChartTick(value: number, currency?: string): string {
  const rounded = value >= 10 ? Math.round(value) : Number(value.toFixed(1));
  if (currency === "EUR" || currency === "USD" || currency === "GBP") {
    return `${currency} ${formatValue(rounded)}`;
  }
  return formatValue(rounded);
}

export function parseDateInput(value?: string): Date | null {
  if (!value) return null;
  const [year, month, day] = value.split("-").map(Number);
  if (!year || !month || !day) return null;
  return new Date(year, month - 1, day);
}

export function toDateInputValue(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function addDaysIso(value: string, amount: number): string {
  const date = parseDateInput(value) ?? new Date();
  date.setDate(date.getDate() + amount);
  return toDateInputValue(date);
}

export function formatDateInputDisplay(value: string): string {
  const date = parseDateInput(value);
  if (!date) return "-";
  return new Intl.DateTimeFormat("en-GB", { day: "2-digit", month: "2-digit", year: "numeric" }).format(date);
}

export function addMonths(date: Date, amount: number): Date {
  return new Date(date.getFullYear(), date.getMonth() + amount, 1);
}

export function calendarDays(viewDate: Date): Date[] {
  const firstOfMonth = new Date(viewDate.getFullYear(), viewDate.getMonth(), 1);
  const mondayOffset = (firstOfMonth.getDay() + 6) % 7;
  const start = new Date(firstOfMonth);
  start.setDate(firstOfMonth.getDate() - mondayOffset);
  return Array.from({ length: 42 }, (_, index) => {
    const day = new Date(start);
    day.setDate(start.getDate() + index);
    return day;
  });
}

export function formatDate(value?: string): string {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("en-GB");
}

export function humanizeFeature(value?: string): string {
  if (!value) return "-";
  switch (value) {
    case "BARCODE_SCANNER":
      return "Barcode Scanner";
    case "MANUAL_FOOD_LOGGING":
      return "Manual Food Logging";
    case "FOOD_DIARY":
      return "Food Diary";
    case "WEIGHT_PROGRESS":
      return "Weight and Progress";
    case "WATER_TRACKING":
      return "Water Tracking";
    case "WORKOUT_LOGGING":
      return "Workout Logging";
    case "SAVED_MEAL_TEMPLATES":
      return "Saved Meal Templates";
    case "RECIPE_BUILDER":
      return "Recipe Builder";
    case "PUBLIC_RECIPE_LIBRARY":
      return "Public Recipe Library";
    case "NEXT_MEAL_SUGGESTIONS":
      return "Next Meal Suggestions";
    case "ADVANCED_MACRO_TARGETS":
      return "Advanced Macro Targets";
    case "MICRONUTRIENT_DETAILS":
      return "Micronutrient Details";
    case "MICRONUTRIENT_ANALYTICS":
      return "Micronutrient Analytics";
    case "DATA_EXPORT":
      return "Data Export";
    case "FASTING_BASIC":
      return "Basic Fasting";
    case "FASTING_ADVANCED":
      return "Advanced Fasting";
    case "AI_MEAL_DRAFTS":
      return "AI Meal Drafts";
    case "AI_RECIPE_GENERATION":
      return "AI Recipe Generation";
    case "AI_WORKOUT_PLANNER":
      return "AI Workout Planner";
    case "AI_INSIGHTS":
      return "AI Coaching Insights";
    default:
      return value.toLowerCase().split("_").map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(" ");
  }
}

export function shortFeature(value?: string): string {
  if (!value) return "-";
  return value.replace(/_/g, " ");
}

export function parsePositiveInt(value: string): number {
  const parsed = Number.parseInt(value || "0", 10);
  if (!Number.isFinite(parsed) || parsed <= 0) return 1;
  return parsed;
}

export function listPreview(values?: string[]): string {
  if (!values?.length) return "-";
  if (values.length <= 2) return values.join(", ");
  return `${values.slice(0, 2).join(", ")} +${values.length - 2}`;
}

export function downloadBlob(blob: Blob, filename: string) {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}

export function notificationSeverityTone(value?: string): "default" | "good" | "warn" | "danger" | "neutral" {
  switch (value) {
    case "CRITICAL":
      return "danger";
    case "WARNING":
      return "warn";
    case "INFO":
      return "neutral";
    default:
      return "neutral";
  }
}

export function notificationTargetLabel(item: Notification): string {
  const target = item.targetType || fallbackNotificationTargetLabel(item.type);
  if (!target) return "-";
  return item.targetId ? `${shortFeature(target)} #${item.targetId}` : shortFeature(target);
}

export function isTargetMatch(targetId: string | undefined, value: unknown): boolean {
  return Boolean(targetId && value !== null && value !== undefined && String(value) === String(targetId));
}

export function fallbackNotificationTargetLabel(type?: string): string | undefined {
  if (type === "ai_rejection_alert") return "AI_REQUEST";
  if (type === "system_alert") return "SYSTEM_ALERT";
  if (type === "subscription_provider_alert") return "SUBSCRIPTION_PROVIDER_EVENT";
  return undefined;
}

export function buildAuditPath(filters: {
  actionType: string;
  targetType: string;
  page: number;
  size: number;
}): string {
  const params = new URLSearchParams();
  if (filters.actionType) params.set("actionType", filters.actionType);
  if (filters.targetType) params.set("targetType", filters.targetType);
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));
  return `/api/v1/admin/audits?${params.toString()}`;
}

export function combineStates(states: LoadState[]): LoadState {
  if (states.includes("error")) return "error";
  if (states.includes("loading")) return "loading";
  if (states.every((state) => state === "ready")) return "ready";
  return "idle";
}

export function countBy<T>(items: T[], readKey: (item: T) => string): Record<string, number> {
  return items.reduce<Record<string, number>>((accumulator, item) => {
    const key = readKey(item) || "Unknown";
    accumulator[key] = (accumulator[key] ?? 0) + 1;
    return accumulator;
  }, {});
}

export function productName(item: FoodProduct): string {
  return item.displayName ?? item.productName ?? item.name ?? item.canonicalName ?? "Unnamed product";
}

export function readNumber(data: SystemHealth | null, key: string): number | undefined {
  const value = data?.[key];
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

export function percent(value?: number, total?: number): number {
  if (!value || !total || total <= 0) return 0;
  return clamp(Math.round((value / total) * 100), 0, 100);
}

export function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

export function formatDurationMs(value?: number): string {
  if (!value || value < 0) return "-";
  const totalSeconds = Math.floor(value / 1000);
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${minutes}m`;
}
