import { FormEvent, useEffect, useState } from "react";

import { QRCodeSVG } from "qrcode.react";

import { formatRequestError, PageResponse, request } from "../api";

import { AdminAccessProfile, AdminSessionPage, OwnerAdminSessionPage, AdminApprovalPage, AdminApprovalRequest, AdminMfaEnrollment, AdminMfaStatus, AdminMfaVerification, AdminTeamMember, AdminTeamPage } from "../types";

import { CollapsiblePanel, DataTable, MetricCard, PaginationControls, Panel, SectionToolbar, useDialogAccessibility } from "../AdminPrimitives";

import { AdminInvitation, AdminTargetContext, Badge, ConfirmDialog, formatDate, formatValue, humanizeFeature, useEndpoint } from "./../admin/shared";
import { useAdminLocale } from "../admin/locale";
import "../admin-team.css";

export function AdminMfaEnrollmentPanel({ onError }: { onError: (message: string | null) => void }) {
  const { locale } = useAdminLocale();
  const [open, setOpen] = useState(false);
  const { data: status, state, reload } = useEndpoint<AdminMfaStatus>("/api/v1/admin/security/mfa", onError);
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [enrollment, setEnrollment] = useState<AdminMfaEnrollment | null>(null);
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);

  async function begin(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      setEnrollment(await request<AdminMfaEnrollment>("/api/v1/admin/security/mfa/enrollment", { method: "POST", body: { currentPassword: password } }));
      setPassword("");
      setRecoveryCodes([]);
      await reload();
    } catch (failure) { onError(formatRequestError(failure)); } finally { setBusy(false); }
  }

  async function verify() {
    setBusy(true);
    try {
      const result = await request<AdminMfaVerification>("/api/v1/admin/security/mfa/enrollment/verify", { method: "POST", body: { code } });
      setRecoveryCodes(result.recoveryCodes ?? []);
      setEnrollment(null);
      setCode("");
      await reload();
    } catch (failure) { onError(formatRequestError(failure)); } finally { setBusy(false); }
  }

  async function disable() {
    setBusy(true);
    try {
      await request<AdminMfaStatus>("/api/v1/admin/security/mfa/disable", { method: "POST", body: { code } });
      setCode("");
      setRecoveryCodes([]);
      await reload();
    } catch (failure) { onError(formatRequestError(failure)); } finally { setBusy(false); }
  }

  return <CollapsiblePanel
    className="admin-security-panel admin-mfa-card admin-mfa-collapsible"
    title={locale === "tr" ? "Çok faktörlü kimlik doğrulamanız" : "Your multi-factor authentication"}
    description={locale === "tr" ? "Authenticator kurulumu, doğrulama ve kurtarma kodları." : "Authenticator enrollment, verification and recovery codes."}
    open={open}
    onToggle={() => setOpen((value) => !value)}
  >
    <div className="admin-mfa-panel">
      <div className="admin-mfa-status">
        <Badge value={status?.enabled ? "Enrolled" : status?.enrollmentPending ? "Verification pending" : "Not enrolled"} tone={status?.enabled ? "good" : "warn"} />
        <span>{status?.enabled ? `${status.recoveryCodesRemaining ?? 0} recovery codes remaining` : "Use an authenticator app before MFA enforcement is enabled."}</span>
      </div>
      {!status?.enabled && !enrollment && <form className="admin-mfa-enroll" onSubmit={begin}>
        <label>Current password<input type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} /></label>
        <button className="primary-button" disabled={busy || !password} type="submit">Start enrollment</button>
      </form>}
      {enrollment && <div className="admin-mfa-setup">
        <div className="admin-mfa-qr">
          <div className="admin-mfa-qr-frame">
            {enrollment.otpauthUri ? <QRCodeSVG value={enrollment.otpauthUri} size={220} marginSize={2} level="M" role="img" aria-label="QR code for authenticator app enrollment" /> : <span>QR code unavailable. Use manual setup.</span>}
          </div>
          <div><strong>Scan with your authenticator app</strong><small>Use Microsoft Authenticator, Google Authenticator, 1Password, or another TOTP app.</small></div>
        </div>
        <div className="admin-mfa-verification">
          <label>Six-digit code<input inputMode="numeric" pattern="[0-9]*" autoComplete="one-time-code" maxLength={6} value={code} onChange={(event) => setCode(event.target.value.replace(/\D/g, ""))} /></label>
          <button className="primary-button" disabled={busy || code.trim().length !== 6} type="button" onClick={verify}>Verify and enable</button>
          <details className="admin-mfa-manual"><summary>Manual setup</summary><code>{enrollment.secret}</code><small>Enter this key manually only when the QR code cannot be scanned.</small></details>
        </div>
      </div>}
      {status?.enabled && <div className="admin-mfa-disable">
        <div className="admin-mfa-disable-copy"><strong>Disable multi-factor authentication</strong><small>This reduces account security. Confirm with a current authenticator or recovery code.</small></div>
        <div className="admin-mfa-disable-controls">
          <label>Authenticator or recovery code<input autoComplete="one-time-code" maxLength={32} value={code} onChange={(event) => setCode(event.target.value)} /></label>
          <button className="danger-button" disabled={busy || !code.trim()} type="button" onClick={disable}>Disable MFA</button>
        </div>
      </div>}
      {recoveryCodes.length > 0 && <div className="admin-mfa-recovery"><strong>Save these one-time recovery codes now</strong><div>{recoveryCodes.map((item) => <code key={item}>{item}</code>)}</div><small>They are shown once and stored only as hashes.</small></div>}
      {state === "loading" && <span className="muted-text">Loading MFA status...</span>}
    </div>
  </CollapsiblePanel>;
}

const APPROVAL_STATUSES = ["ALL", "PENDING", "APPROVED", "REJECTED", "EXPIRED", "EXECUTION_FAILED"] as const;
const FINANCIAL_APPROVAL_ACTIONS = new Set(["SUBSCRIPTION_UPDATE", "AI_QUOTA_RESET", "AI_ADDON_QUOTA_GRANT", "ENTITLEMENT_MATRIX_APPLY", "PLAN_FEATURE_UPDATE", "AI_QUOTA_REFUND"]);

function approvalImpactDetail(item: AdminApprovalRequest, locale: "tr" | "en") {
  const payload = item.payload ?? {};
  const value = (key: string) => payload[key] == null ? null : String(payload[key]);
  const parts = item.actionType === "SUBSCRIPTION_UPDATE"
    ? [[locale === "tr" ? "Plan" : "Plan", value("planType")], [locale === "tr" ? "Durum" : "Status", value("status")], [locale === "tr" ? "Dönem" : "Period", value("billingPeriod")], [locale === "tr" ? "AI kotası" : "AI quota", value("aiMonthlyQuota")]]
    : item.actionType === "AI_ADDON_QUOTA_GRANT"
      ? [[locale === "tr" ? "Kredi" : "Credits", value("amount")], [locale === "tr" ? "Geçerlilik" : "Validity", value("validityDays") ? `${value("validityDays")} ${locale === "tr" ? "gün" : "days"}` : null]]
      : item.actionType === "AI_QUOTA_REFUND"
        ? [[locale === "tr" ? "İade kredisi" : "Refund credits", value("amount")]]
        : item.actionType === "PLAN_FEATURE_UPDATE"
          ? [[locale === "tr" ? "Etkin" : "Enabled", value("enabled")], [locale === "tr" ? "AI kredi maliyeti" : "AI credit cost", value("aiCreditCost")], [locale === "tr" ? "Geçerlilik" : "Effective", value("effectiveFrom")]]
          : [];
  const summary = parts.filter((part) => part[1] != null).map(([label, detail]) => `${label}: ${detail}`).join(" · ");
  return summary || (locale === "tr" ? "Etki ayrıntısı izin verilen yük içinde gösterilir." : "Impact details are shown in the allowlisted payload.");
}

function initialApprovalRoute() {
  const search = new URLSearchParams(window.location.search);
  const requestedStatus = search.get("status") ?? "PENDING";
  return {
    status: APPROVAL_STATUSES.includes(requestedStatus as typeof APPROVAL_STATUSES[number]) ? requestedStatus : "PENDING",
    page: Math.max(0, Number.parseInt(search.get("page") ?? "0", 10) || 0),
    pageSize: [10, 25, 50].includes(Number(search.get("size"))) ? Number(search.get("size")) : 10,
    approvalId: Number.parseInt(search.get("approvalId") ?? "", 10) || null
  };
}

export function AdminApprovalQueue({ accessProfile, onError, targetContext, onClearTarget }: { accessProfile: AdminAccessProfile | null; onError: (message: string | null) => void; targetContext?: AdminTargetContext | null; onClearTarget?: () => void }) {
  const { locale } = useAdminLocale();
  const text = locale === "tr" ? {
    title: "Kritik işlem onay kuyruğu", description: "Owner kendi taleplerini güncel MFA ile onaylayabilir. Diğer yöneticilerin talepleri owner kararı için bekler ve 24 saat sonra sona erer.", status: "Durum", refresh: "Yenile", action: "İşlem", target: "Hedef", maker: "Talep eden", expires: "Bitiş", empty: "Bu durumda onay talebi yok.", requestedBy: "Talep eden", payload: "İzin verilen değişiklik yükü", reason: "Karar gerekçesi", mfa: "Güncel doğrulayıcı veya kurtarma kodu", close: "Kapat", reject: "Reddet", approve: "Onayla ve uygula", own: "Bu talebi siz oluşturdunuz. Kararı owner vermelidir.", loading: "Onay kuyruğu yükleniyor...", request: "Talep", decision: "Karar", execution: "Uygulama", created: "Oluşturuldu", decided: "Karar zamanı", checker: "Karar veren", pendingDecision: "Owner kararı bekleniyor", notDecided: "Henüz karar verilmedi", financial: "Finansal etki", operational: "Operasyonel etki", approvedResult: "Onaylandı ve işlem başarıyla uygulandı.", rejectedResult: "Reddedildi; sistemde değişiklik yapılmadı.", expiredResult: "Süresi doldu; sistemde değişiklik yapılmadı.", failedResult: "Onaylanan işlem uygulanamadı; hata kaydı incelenmeli.", pendingResult: "Henüz uygulanmadı; owner kararı bekleniyor.", record: "Onay kaydı"
  } : {
    title: "Critical action approval queue", description: "Owners can approve their own requests with fresh MFA. Requests from other admins remain pending for owner review and expire after 24 hours.", status: "Status", refresh: "Refresh", action: "Action", target: "Target", maker: "Maker", expires: "Expires", empty: "No approval requests in this state.", requestedBy: "requested by", payload: "Whitelisted change payload", reason: "Decision reason", mfa: "Fresh authenticator or recovery code", close: "Close", reject: "Reject", approve: "Approve and execute", own: "You created this request. An owner must decide it.", loading: "Loading approval queue...", request: "Request", decision: "Decision", execution: "Execution", created: "Created", decided: "Decision time", checker: "Decided by", pendingDecision: "Waiting for owner decision", notDecided: "No decision yet", financial: "Financial impact", operational: "Operational impact", approvedResult: "Approved and successfully applied.", rejectedResult: "Rejected; no system change was applied.", expiredResult: "Expired; no system change was applied.", failedResult: "The approved action could not be applied; review the error record.", pendingResult: "Not applied yet; waiting for the owner decision.", record: "Approval record"
  };
  const [initialRoute] = useState(initialApprovalRoute);
  const [status, setStatus] = useState(initialRoute.status);
  const [page, setPage] = useState(initialRoute.page);
  const [pageSize, setPageSize] = useState(initialRoute.pageSize);
  const [selectedId, setSelectedId] = useState<number | null>(initialRoute.approvalId);
  const [selected, setSelected] = useState<AdminApprovalRequest | null>(null);
  const [decisionReason, setDecisionReason] = useState("");
  const [mfaCode, setMfaCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [decisionError, setDecisionError] = useState<string | null>(null);
  const [filtersOpen, setFiltersOpen] = useState(true);
  const [now, setNow] = useState(Date.now());
  useEffect(() => { const timer = window.setInterval(() => setNow(Date.now()), 1000); return () => window.clearInterval(timer); }, []);
  const selectedExpired = selected?.status === "EXPIRED" || (selected?.status === "PENDING" && Date.parse(selected.expiresAt ?? "") <= now);
  const path = `/api/v1/admin/approvals?${status === "ALL" ? "" : `status=${status}&` }page=${page}&size=${pageSize}`;
  const { data, state, reload } = useEndpoint<AdminApprovalPage>(path, onError);
  const canApprove = Boolean(accessProfile?.permissions?.includes("ADMIN_TEAM_MANAGE"));

  useEffect(() => {
    if (targetContext?.targetType !== "ADMIN_APPROVAL" || !targetContext.targetId) return;
    const id = Number(targetContext.targetId);
    if (Number.isSafeInteger(id) && id > 0) setSelectedId(id);
  }, [targetContext?.targetId, targetContext?.targetType]);

  useEffect(() => {
    const match = data?.content?.find((item) => item.id === selectedId) ?? null;
    if (match) setSelected(match);
  }, [data?.content, selected?.id, selectedId]);

  useEffect(() => {
    const search = new URLSearchParams();
    search.set("status", status);
    if (page > 0) search.set("page", String(page));
    if (pageSize !== 10) search.set("size", String(pageSize));
    if (selectedId) search.set("approvalId", String(selectedId));
    window.history.replaceState({}, "", `${window.location.pathname}?${search.toString()}`);
  }, [page, pageSize, selectedId, status]);

  function selectApproval(item: AdminApprovalRequest | null) {
    setDecisionError(null);
    setSelected(item);
    setSelectedId(item?.id ?? null);
    setDecisionReason("");
    setMfaCode("");
    if (!item) onClearTarget?.();
  }

  async function decide(approve: boolean) {
    if (selectedExpired) { setDecisionError(text.expiredResult); await reload(); return; }
    if (!selected?.id || !mfaCode.trim() || !decisionReason.trim()) return;
    setBusy(true);
    try {
      const proof = await request<{ token?: string }>("/api/v1/admin/security/mfa/reauthenticate", { method: "POST", body: { code: mfaCode, purpose: "APPROVAL_DECISION" } });
      if (!proof.token) throw new Error("MFA re-authentication token was not returned.");
      await request(`/api/v1/admin/approvals/${selected.id}/${approve ? "approve" : "reject"}`, {
        method: "POST",
        headers: { "X-Admin-Reauth-Token": proof.token },
        body: { reason: decisionReason.trim() }
      });
      selectApproval(null); await reload();
    } catch (failure) {
      const message = formatRequestError(failure);
      setDecisionError(message); onError(message); setMfaCode("");
      await reload();
    } finally { setBusy(false); }
  }

  const rows = data?.content ?? [];
  const canDecideSelected = !selectedExpired && selected?.status === "PENDING" && canApprove
    && (selected.makerEmail !== accessProfile?.email || accessProfile?.role === "OWNER");
  const executionResult = selected?.status === "APPROVED" ? text.approvedResult : selected?.status === "REJECTED" ? text.rejectedResult : selected?.status === "EXPIRED" ? text.expiredResult : selected?.status === "EXECUTION_FAILED" ? text.failedResult : text.pendingResult;
  return <Panel className="approval-queue-panel" title={text.title} description={text.description}>
    <CollapsiblePanel
      className="approval-filter-panel"
      title={locale === "tr" ? "Onay filtreleri" : "Approval filters"}
      description={locale === "tr" ? "Kuyruğu karar durumuna göre daraltın." : "Narrow the queue by decision status."}
      open={filtersOpen}
      onToggle={() => setFiltersOpen((value) => !value)}
    >
      <div className="approval-queue-toolbar"><label>{text.status}<select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); selectApproval(null); }}>{APPROVAL_STATUSES.map((item) => <option key={item} value={item}>{item === "ALL" ? (locale === "tr" ? "Tümü" : "All") : humanizeFeature(item)}</option>)}</select></label><button className="ghost-button" type="button" onClick={() => void reload()}>{text.refresh}</button></div>
    </CollapsiblePanel>
    <DataTable columns={[text.action, text.target, text.maker, text.status, text.expires]} rows={rows.map((item) => [<div className="entity-cell"><strong>{humanizeFeature(item.actionType)}</strong><small>{item.requestReason}</small></div>, item.targetKey ?? "-", item.makerEmail ?? "-", <Badge value={item.status} tone={item.status === "APPROVED" ? "good" : item.status === "PENDING" ? "warn" : "danger"} />, formatDate(item.expiresAt)])} rowData={rows} onRowClick={selectApproval} empty={text.empty} />
    <PaginationControls page={data?.page ?? page} pageSize={pageSize} totalElements={data?.totalElements ?? 0} totalPages={Math.max(1,data?.totalPages ?? 1)} first={data?.first ?? page===0} last={data?.last ?? true} onPageChange={setPage} onPageSizeChange={(size)=>{setPageSize(size);setPage(0);}} />
    {selected && <div className="approval-decision-panel">
      {decisionError && <div role="alert" className="form-notice">{decisionError}</div>}
      {selectedExpired && <div role="status" className="form-notice">{text.expiredResult}</div>}
      <div className="approval-record-heading"><div><span className="eyebrow">{text.record} #{selected.id}</span><strong>{humanizeFeature(selected.actionType)}</strong><span>{text.target} {selected.targetKey}</span><small>{approvalImpactDetail(selected, locale)}</small></div><Badge value={FINANCIAL_APPROVAL_ACTIONS.has(selected.actionType ?? "") ? text.financial : text.operational} tone={FINANCIAL_APPROVAL_ACTIONS.has(selected.actionType ?? "") ? "warn" : "neutral"} /></div>
      <div className="approval-stage-grid">
        <section><span>{text.request}</span><strong>{selected.makerEmail ?? "-"}</strong><small>{text.created}: {formatDate(selected.createdAt)}</small><p>{selected.requestReason}</p></section>
        <section><span>{text.decision}</span><strong>{selected.checkerEmail ?? text.pendingDecision}</strong><small>{selected.decidedAt ? `${text.decided}: ${formatDate(selected.decidedAt)}` : text.notDecided}</small><p>{selected.decisionReason ?? text.notDecided}</p></section>
        <section><span>{text.execution}</span><strong>{selected.status}</strong><small>{executionResult}</small></section>
      </div>
      <details><summary>{text.payload}</summary><pre>{JSON.stringify(selected.payload ?? {}, null, 2)}</pre></details>
      {canDecideSelected && <>
        <label>{text.reason}<textarea value={decisionReason} onChange={(event)=>setDecisionReason(event.target.value)} maxLength={500} /></label>
        <label>{text.mfa}<input value={mfaCode} onChange={(event)=>setMfaCode(event.target.value)} maxLength={32} autoComplete="one-time-code" /></label>
        <div className="form-actions"><button className="ghost-button" type="button" onClick={()=>selectApproval(null)}>{text.close}</button><button className="danger-button" disabled={busy || !decisionReason.trim() || !mfaCode.trim()} type="button" onClick={()=>void decide(false)}>{text.reject}</button><button className="primary-button" disabled={busy || !decisionReason.trim() || !mfaCode.trim()} type="button" onClick={()=>void decide(true)}>{text.approve}</button></div>
      </>}
      {!canDecideSelected && <div className="form-actions"><button className="ghost-button" type="button" onClick={()=>selectApproval(null)}>{text.close}</button></div>}
      {selected.makerEmail === accessProfile?.email && selected.status === "PENDING" && accessProfile?.role !== "OWNER" && <div className="form-notice">{text.own}</div>}
    </div>}
    {state === "loading" && <span className="muted-text">{text.loading}</span>}
  </Panel>;
}

export function AdminSessionPanel({ canRevoke, onError }: { canRevoke: boolean; onError: (message: string | null) => void }) {
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const path = `/api/v1/admin/security/sessions?page=${page}&size=${pageSize}`;
  const { data, state, reload } = useEndpoint<AdminSessionPage>(path, onError);
  const [busy, setBusy] = useState(false);

  const [pendingRevoke, setPendingRevoke] = useState<string | "others" | null>(null);
  const [reason, setReason] = useState("");

  async function confirmRevoke() {
    if (!pendingRevoke || !reason.trim()) return;
    setBusy(true);
    try {
      const path = pendingRevoke === "others" ? "/api/v1/admin/security/sessions/others" : `/api/v1/admin/security/sessions/${pendingRevoke}`;
      await request(path, { method: "DELETE", body: { reason: reason.trim() } });
      setPendingRevoke(null); setReason(""); await reload();
    } catch (failure) { onError(formatRequestError(failure)); } finally { setBusy(false); }
  }

  const sessions = data?.content ?? [];
  return <Panel className="admin-security-panel admin-session-card" title="Your active admin sessions" description="Server-side sessions. Network addresses are masked and revocation takes effect on the next backend request.">
    <div className="form-actions"><button className="ghost-button" type="button" onClick={() => void reload()}>Refresh sessions</button>{canRevoke && sessions.some((item) => !item.current) && <button className="danger-button" disabled={busy} type="button" onClick={() => setPendingRevoke("others")}>Revoke all other sessions</button>}</div>
    <DataTable columns={["Device", "Network", "Created", "Last activity", "Expiry", "Action"]} rows={sessions.map((item) => [
      <div className="entity-cell"><strong>{item.device ?? "Unknown browser"}</strong><small>{item.current ? "Current session" : item.id ?? "-"}</small></div>,
      item.maskedIp ?? "Unknown", formatDate(item.createdAt), formatDate(item.lastActivityAt),
      <div className="table-stack"><span>Idle {formatDate(item.idleExpiresAt)}</span><small>Absolute {formatDate(item.absoluteExpiresAt)}</small></div>,
      item.current ? <Badge value="CURRENT" tone="good" /> : canRevoke ? <button className="danger-button" disabled={busy} type="button" onClick={() => item.id && setPendingRevoke(item.id)}>Revoke</button> : <Badge value="ACTIVE" />
    ])} empty={state === "loading" ? "Loading sessions..." : "No active sessions found."} />
    {pendingRevoke && <div className="approval-decision-panel"><strong>{pendingRevoke === "others" ? "Revoke all other sessions" : "Revoke selected session"}</strong><p>{pendingRevoke === "others" ? "Every other active admin session will be signed out immediately. This session remains active." : "The selected browser will be signed out on its next backend request."}</p><label>Required audit reason<textarea value={reason} onChange={(event)=>setReason(event.target.value)} maxLength={500} /></label><div className="form-actions"><button className="ghost-button" disabled={busy} type="button" onClick={()=>{setPendingRevoke(null);setReason("");}}>Cancel</button><button className="danger-button" disabled={busy||!reason.trim()} type="button" onClick={()=>void confirmRevoke()}>Confirm revocation</button></div></div>}
    <PaginationControls page={data?.page ?? page} pageSize={pageSize} totalElements={data?.totalElements ?? 0} totalPages={Math.max(1,data?.totalPages ?? 1)} first={data?.first ?? page===0} last={data?.last ?? true} onPageChange={setPage} onPageSizeChange={(size)=>{setPageSize(size);setPage(0);}} />
  </Panel>;
}

export function OwnerAdminSessionsPanel({ onError }: { onError: (message: string | null) => void }) {
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(25);
  const [pending, setPending] = useState<{ id: string; email: string } | null>(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const path = `/api/v1/admin/security/owner-sessions?page=${page}&size=${pageSize}`;
  const { data, state, reload } = useEndpoint<OwnerAdminSessionPage>(path, onError);

  async function revoke() {
    if (!pending || !reason.trim()) return;
    setBusy(true);
    try {
      await request(`/api/v1/admin/security/owner-sessions/${pending.id}`, { method: "DELETE", body: { reason: reason.trim() } });
      setPending(null); setReason(""); await reload();
    } catch (failure) { onError(formatRequestError(failure)); } finally { setBusy(false); }
  }

  const sessions = data?.content ?? [];
  return <Panel className="admin-security-panel admin-session-card" title="All active admin sessions" description="OWNER-only view. Network addresses are masked; revocation requires fresh owner verification and is audit logged.">
    <div className="form-actions"><button className="ghost-button" type="button" onClick={() => void reload()}>Refresh all sessions</button></div>
    <DataTable columns={["Admin", "Device", "Network", "Last activity", "Expiry", "Action"]} rows={sessions.map((item) => [
      <div className="entity-cell"><strong>{item.adminEmail ?? "Unknown admin"}</strong><small>{humanizeFeature(item.adminRole)}</small></div>,
      <div className="entity-cell"><strong>{item.device ?? "Unknown browser"}</strong><small>{formatDate(item.createdAt)}</small></div>,
      item.maskedIp ?? "Unknown", formatDate(item.lastActivityAt),
      <div className="table-stack"><span>Idle {formatDate(item.idleExpiresAt)}</span><small>Absolute {formatDate(item.absoluteExpiresAt)}</small></div>,
      item.current ? <Badge value="CURRENT ? PROTECTED" tone="good" /> : <button className="danger-button" disabled={busy || !item.id} type="button" onClick={() => item.id && setPending({ id: item.id, email: item.adminEmail ?? "this admin" })}>Revoke</button>
    ])} empty={state === "loading" ? "Loading all admin sessions..." : "No active admin sessions found."} />
    {pending && <div className="approval-decision-panel"><strong>Revoke {pending.email}'s session</strong><p>The selected admin will be signed out on the next backend request. Your current OWNER session is protected.</p><label>Required audit reason<textarea value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} /></label><div className="form-actions"><button className="ghost-button" disabled={busy} type="button" onClick={() => { setPending(null); setReason(""); }}>Cancel</button><button className="danger-button" disabled={busy || !reason.trim()} type="button" onClick={() => void revoke()}>Confirm revocation</button></div></div>}
    <PaginationControls page={data?.page ?? page} pageSize={pageSize} totalElements={data?.totalElements ?? 0} totalPages={Math.max(1, data?.totalPages ?? 1)} first={data?.first ?? page === 0} last={data?.last ?? true} onPageChange={setPage} onPageSizeChange={(size) => { setPageSize(size); setPage(0); }} />
  </Panel>;
}

export type AdminInvitationPage = PageResponse<AdminInvitation>;

function OwnerLiveSessionMetric({ tr, onOpen, onError }: { tr: boolean; onOpen: () => void; onError: (message: string | null) => void }) {
  const { data } = useEndpoint<OwnerAdminSessionPage>("/api/v1/admin/security/owner-sessions?page=0&size=100", onError);
  const sessions = data?.content ?? [];
  const admins = Array.from(new Set(sessions.map((item) => item.adminEmail).filter(Boolean)));
  const detail = admins.length
    ? `${admins.slice(0, 2).join(" · ")}${admins.length > 2 ? ` +${admins.length - 2}` : ""}`
    : tr ? "Aktif admin oturumu yok" : "No active admin sessions";
  return <button type="button" onClick={onOpen}><span>{tr ? "Canlı oturum" : "Live sessions"}</span><strong>{data?.totalElements ?? sessions.length}</strong><small title={admins.join(", ")}>{detail}</small></button>;
}

export function AdminInvitationPanel({ onError }: { onError: (message: string | null) => void }) {
  const { locale } = useAdminLocale();
  const tr = locale === "tr";
  const tx = (en: string, trText: string) => tr ? trText : en;
  const [page, setPage] = useState(0);
  const { data, state, reload } = useEndpoint<AdminInvitationPage>("/api/v1/admin/security/invitations?page=" + page + "&size=10", onError);
  const [form, setForm] = useState({ email: "", role: "ADMIN_READ_ONLY" });
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [pendingAction, setPendingAction] = useState<{ id: number; method: "resend" | "revoke" } | null>(null);
  const [inviteOpen, setInviteOpen] = useState(false);
  const invitations = data?.content ?? [];
  async function create(event: FormEvent) {
    event.preventDefault(); setBusy(true); setFormError(null);
    try { await request("/api/v1/admin/security/invitations", { method: "POST", body: { email: form.email.trim(), role: form.role } }); setForm((current) => ({ ...current, email: "" })); setInviteOpen(false); await reload(); }
    catch (error) { setFormError(formatRequestError(error)); } finally { setBusy(false); }
  }
  function action(id: number | undefined, method: "resend" | "revoke") {
    if (!id) return;
    setPendingAction({ id, method });
  }
  async function confirmAction() {
    if (!pendingAction) return;
    setBusy(true);
    try {
      await request("/api/v1/admin/security/invitations/" + pendingAction.id + (pendingAction.method === "resend" ? "/resend" : ""), { method: pendingAction.method === "resend" ? "POST" : "DELETE" });
      setPendingAction(null);
      await reload();
    }
    catch (error) { onError(formatRequestError(error)); } finally { setBusy(false); }
  }
  return <Panel className="admin-invitation-center" title={tx("Invitation operations", "Davet işlemleri")} description={tx("Create secure administrator invitations and follow every link through its lifecycle.", "Güvenli yönetici davetleri oluşturun ve her bağlantının yaşam döngüsünü izleyin.")}>
    <section className="admin-invitation-command">
      <span className="admin-invitation-command-icon" aria-hidden="true">+</span>
      <div><span>{tx("SECURE ONBOARDING", "GÜVENLİ KATILIM")}</span><h3>{tx("Invite a new administrator", "Yeni yönetici davet edin")}</h3><p>{tx("Choose the least-privilege role. The link is single-use and expires automatically.", "En az yetkili rolü seçin. Bağlantı tek kullanımlıktır ve otomatik olarak sona erer.")}</p></div>
      <button className="primary-button" type="button" onClick={() => setInviteOpen(true)}>{tx("Create invitation", "Davet oluştur")}</button>
    </section>
    <div className="admin-invitation-list">
      <div className="admin-invitation-history-heading"><div><span>{tx("INVITATION LEDGER", "DAVET KAYITLARI")}</span><h3>{tx("Invitation history", "Davet geçmişi")}</h3><p>{tx("Pending, accepted, revoked and expired links.", "Bekleyen, kabul edilen, iptal edilen ve süresi dolan bağlantılar.")}</p></div><div><b>{data?.totalElements ?? invitations.length}</b><button className="ghost-button" type="button" onClick={() => void reload()}>{tx("Refresh", "Yenile")}</button></div></div>
      <DataTable columns={[tx("Recipient", "Alıcı"),tx("Role", "Rol"),tx("Status", "Durum"),tx("Sent", "Gönderim"),tx("Expires", "Bitiş"),tx("Actions", "İşlemler")]} rows={invitations.map((item) => [
        <div className="entity-cell"><strong>{item.email}</strong><small>{tx("Invited by", "Davet eden")} {item.invitedBy}</small></div>,
        <Badge value={item.role} />, <Badge value={item.status} tone={item.status === "ACCEPTED" ? "good" : item.status === "PENDING" ? "warn" : "danger"} />,
        formatDate(item.createdAt), formatDate(item.expiresAt),
        item.status === "PENDING" ? <div className="table-actions"><button className="ghost-button compact" disabled={busy} type="button" onClick={() => void action(item.id, "resend")}>{tx("Resend", "Yeniden gönder")}</button><button className="danger-button compact" disabled={busy} type="button" onClick={() => void action(item.id, "revoke")}>{tx("Revoke", "İptal et")}</button></div> : "-"
      ])} empty={state === "loading" ? tx("Loading invitations...", "Davetler yükleniyor...") : tx("No admin invitations yet.", "Henüz yönetici daveti yok.")} />
      <PaginationControls page={data?.page ?? page} pageSize={10} totalElements={data?.totalElements ?? 0} totalPages={Math.max(1,data?.totalPages ?? 1)} first={data?.first ?? page === 0} last={data?.last ?? true} onPageChange={setPage} onPageSizeChange={() => undefined} />
    </div>
    {inviteOpen && <div className="modal-backdrop" role="presentation" onClick={() => !busy && setInviteOpen(false)}><form className="modal-card admin-invite-modal" role="dialog" aria-modal="true" aria-label={tx("Invite an administrator", "Yönetici davet et")} onSubmit={create} onClick={(event) => event.stopPropagation()}><header className="modal-header"><div><span>{tx("SECURE ONBOARDING", "GÜVENLİ KATILIM")}</span><h2>{tx("Invite an administrator", "Yönetici davet et")}</h2><p>{tx("The invitation is single-use and expires automatically.", "Davet tek kullanımlıktır ve otomatik olarak sona erer.")}</p></div><button className="icon-button" disabled={busy} type="button" onClick={() => setInviteOpen(false)}>×</button></header><div className="modal-body admin-invite-body"><label>{tx("Email address", "E-posta adresi")}<input type="email" required value={form.email} onChange={(event) => setForm((current) => ({ ...current, email: event.target.value }))} placeholder="admin@company.com" /></label><label>{tx("Least-privilege role", "En az yetkili rol")}<select value={form.role} onChange={(event) => setForm((current) => ({ ...current, role: event.target.value }))}>{["ADMIN_SUPPORT","ADMIN_CATALOG","ADMIN_GROWTH","ADMIN_FINANCE","ADMIN_TECHNICAL","ADMIN_READ_ONLY"].map((role) => <option key={role} value={role}>{humanizeFeature(role)}</option>)}</select></label><div className="admin-invite-preview"><span>{tx("ACCESS PREVIEW", "ERİŞİM ÖNİZLEMESİ")}</span><strong>{humanizeFeature(form.role)}</strong><p>{tr ? ROLE_MATRIX.find((item) => item.role === form.role)?.tr : ROLE_MATRIX.find((item) => item.role === form.role)?.en}</p></div>{formError && <div className="form-error admin-invitation-error" role="alert"><strong>{tx("Invitation could not be sent", "Davet gönderilemedi")}</strong><span>{formError}</span></div>}</div><footer className="modal-actions"><button className="ghost-button" disabled={busy} type="button" onClick={() => setInviteOpen(false)}>{tx("Cancel", "İptal")}</button><button className="primary-button" disabled={busy || !form.email.trim()} type="submit">{busy ? tx("Sending...", "Gönderiliyor...") : tx("Send invitation", "Daveti gönder")}</button></footer></form></div>}
    {pendingAction && <ConfirmDialog
      title={pendingAction.method === "revoke" ? "Revoke admin invitation?" : "Send a new invitation link?"}
      message={pendingAction.method === "revoke" ? "This invitation link will stop working immediately." : "The existing invitation link will be invalidated before a new one is sent."}
      confirmLabel={pendingAction.method === "revoke" ? "Revoke invitation" : "Resend invitation"}
      danger={pendingAction.method === "revoke"}
      busy={busy}
      onCancel={() => !busy && setPendingAction(null)}
      onConfirm={() => void confirmAction()}
    />}
  </Panel>;
}

export function AdminSecurityView({ accessProfile, onError, targetContext }: { accessProfile: AdminAccessProfile | null; onError: (message: string | null) => void; targetContext?: AdminTargetContext | null }) {
  const { locale } = useAdminLocale();
  const tr = locale === "tr";
  const tx = (en: string, trText: string) => tr ? trText : en;
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(25);
  const [activeWorkspace, setActiveWorkspace] = useState<"team" | "invitations" | "security" | "roles">("team");
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [roleFilter, setRoleFilter] = useState("");
  const [accessFilter, setAccessFilter] = useState("");
  const [mfaFilter, setMfaFilter] = useState("");
  const path = `/api/v1/admin/security/team?page=${page}&size=${pageSize}`;
  const { data, state, reload } = useEndpoint<AdminTeamPage>(path, onError);
  const [selected, setSelected] = useState<AdminTeamMember | null>(null);
  const [draft, setDraft] = useState({ role: "ADMIN_READ_ONLY", enabled: true, mfaEnabled: false, reason: "" });
  const [saving, setSaving] = useState(false);
  const [passwordResetConfirmationOpen, setPasswordResetConfirmationOpen] = useState(false);
  const memberDialogRef = useDialogAccessibility(() => !saving && setSelected(null), Boolean(selected));
  const canManage = Boolean(accessProfile?.permissions?.includes("ADMIN_TEAM_MANAGE"));
  const members = data?.content ?? [];
  const filteredMembers = members.filter((member) => {
    const needle = query.trim().toLocaleLowerCase(locale === "tr" ? "tr-TR" : "en-GB");
    const matchesQuery = !needle || `${member.name ?? ""} ${member.email ?? ""}`.toLocaleLowerCase(locale === "tr" ? "tr-TR" : "en-GB").includes(needle);
    const accessState = member.enabled === false ? "DISABLED" : member.locked ? "LOCKED" : "ENABLED";
    return matchesQuery && (!roleFilter || member.role === roleFilter) && (!accessFilter || accessState === accessFilter)
      && (!mfaFilter || (mfaFilter === "ENABLED") === Boolean(member.mfaEnabled));
  });
  const activeMembers = members.filter((member) => member.enabled !== false && !member.locked).length;
  const mfaMissing = members.filter((member) => !member.mfaEnabled).length;
  const restrictedMembers = members.filter((member) => member.enabled === false || member.locked).length;
  const activeSessions = members.reduce((sum, member) => sum + (member.activeSessions ?? 0), 0);
  const roleCounts = members.reduce<Record<string, number>>((counts, member) => {
    const role = member.role ?? "ADMIN_READ_ONLY"; counts[role] = (counts[role] ?? 0) + 1; return counts;
  }, {});
  const roleEntries = Object.entries(roleCounts).sort((left, right) => right[1] - left[1]);
  const maximumRoleCount = Math.max(1, ...roleEntries.map(([, count]) => count));

  useEffect(() => {
    if (targetContext?.targetType !== "ADMIN_ACCOUNT" || !targetContext.targetId) return;
    const id = Number(targetContext.targetId);
    const match = members.find(member => member.id === id);
    if (match && selected?.id !== match.id) selectMember(match);
  }, [members, selected?.id, targetContext?.targetId, targetContext?.targetType]);

  function selectMember(member: AdminTeamMember) {
    setSelected(member);
    setDraft({
      role: member.role ?? "ADMIN_READ_ONLY",
      enabled: member.enabled !== false,
      mfaEnabled: Boolean(member.mfaEnabled),
      reason: ""
    });
  }

  async function saveMember() {
    if (!selected?.id || !canManage) return;
    setSaving(true);
    try {
      await request<AdminTeamMember>(`/api/v1/admin/security/team/${selected.id}`, {
        method: "PATCH",
        body: { ...draft, reason: draft.reason.trim() }
      });
      setSelected(null);
      await reload();
    } catch (failure) {
      onError(formatRequestError(failure));
    } finally {
      setSaving(false);
    }
  }

  async function sendPasswordReset() {
    if (!selected?.id || !canManage) return;
    setSaving(true);
    try {
      const result = await request<{ message?: string }>(`/api/v1/admin/security/team/${selected.id}/password-reset`, { method: "POST" });
      setPasswordResetConfirmationOpen(false);
      onError(result.message ?? "Password reset link sent.");
    } catch (failure) { onError(formatRequestError(failure)); }
    finally { setSaving(false); }
  }
  function showTeamFilters(next: { role?: string; access?: string; mfa?: string } = {}) {
    setRoleFilter(next.role ?? "");
    setAccessFilter(next.access ?? "");
    setMfaFilter(next.mfa ?? "");
    setFiltersOpen(true);
    setActiveWorkspace("team");
  }
  return <div className="stack admin-team-control-center">
    <SectionToolbar title={tx("Admin team control center", "Admin ekip kontrol merkezi")} description={tx("Manage access, invitations, security posture and active sessions from one operational workspace.", "Erişimleri, davetleri, güvenlik durumunu ve aktif oturumları tek operasyon alanından yönetin.")} state={state} onReload={reload} />

    <section className="admin-team-command" aria-label={tx("Team operations summary", "Ekip operasyon özeti")}>
      <div className="admin-team-command-copy"><span>{tx("ACCESS OPERATIONS", "ERİŞİM OPERASYONLARI")}</span><h2>{tx("Keep every administrator accountable and secure.", "Tüm yöneticileri güvenli ve denetlenebilir yönetin.")}</h2><p>{tx("Role boundaries, MFA readiness and live sessions remain visible before every access decision.", "Her erişim kararından önce rol sınırları, MFA hazırlığı ve canlı oturumlar görünür kalır.")}</p></div>
      <div className="admin-team-command-actions"><button className="ghost-button" type="button" onClick={() => setActiveWorkspace("security")}>{tx("Review security", "Güvenliği incele")}</button><button className="ghost-button" type="button" onClick={() => setActiveWorkspace("roles")}>{tx("Compare roles", "Rolleri karşılaştır")}</button></div>
    </section>

    <div className="admin-team-metrics">
      <button type="button" onClick={() => showTeamFilters()}><span>{tx("Team members", "Ekip üyesi")}</span><strong>{formatValue(data?.totalElements ?? 0)}</strong><small>{tx("All administrator accounts", "Tüm yönetici hesapları")}</small></button>
      <button type="button" onClick={() => showTeamFilters({ access: "ENABLED" })}><span>{tx("Active access", "Aktif erişim")}</span><strong>{activeMembers}</strong><small>{tx("Enabled and unlocked", "Etkin ve kilitsiz")}</small></button>
      <button type="button" className={mfaMissing ? "needs-attention" : ""} onClick={() => showTeamFilters({ mfa: "MISSING" })}><span>{tx("MFA missing", "MFA eksik")}</span><strong>{mfaMissing}</strong><small>{tx("Accounts needing enrollment", "Kurulum gereken hesaplar")}</small></button>
      <button type="button" className={restrictedMembers ? "needs-attention" : ""} onClick={() => showTeamFilters({ access: restrictedMembers ? "DISABLED" : "" })}><span>{tx("Restricted", "Kısıtlı")}</span><strong>{restrictedMembers}</strong><small>{tx("Disabled or locked", "Devre dışı veya kilitli")}</small></button>
      {accessProfile?.role === "OWNER" ? <OwnerLiveSessionMetric tr={tr} onError={onError} onOpen={() => setActiveWorkspace("security")} /> : <button type="button" onClick={() => setActiveWorkspace("security")}><span>{tx("My live sessions", "Canlı oturumlarım")}</span><strong>{activeSessions}</strong><small>{tx("Open session security", "Oturum güvenliğini aç")}</small></button>}
    </div>

    <nav className="admin-team-workspaces" aria-label={tx("Admin management areas", "Admin yönetim alanları")}>
      {(["team", "invitations", "security", "roles"] as const).map((workspace) => <button key={workspace} className={activeWorkspace === workspace ? "active" : ""} type="button" onClick={() => setActiveWorkspace(workspace)}><strong>{workspace === "team" ? tx("Team", "Ekip") : workspace === "invitations" ? tx("Invitations", "Davetler") : workspace === "security" ? tx("Security & sessions", "Güvenlik ve oturumlar") : tx("Roles & permissions", "Roller ve yetkiler")}</strong><small>{workspace === "team" ? tx("Accounts and access", "Hesaplar ve erişim") : workspace === "invitations" ? tx("Onboarding lifecycle", "Katılım yaşam döngüsü") : workspace === "security" ? tx("MFA and live access", "MFA ve canlı erişim") : tx("Effective boundaries", "Etkin sınırlar")}</small></button>)}
    </nav>

    {activeWorkspace === "team" && <div className="admin-team-workspace-grid">
      <section className="admin-team-roster">
        <header><div><span>{tx("TEAM DIRECTORY", "EKİP DİZİNİ")}</span><h3>{tx("Administrator accounts", "Yönetici hesapları")}</h3><p>{tx("Search, narrow and inspect effective access without leaving the roster.", "Listeden ayrılmadan arayın, daraltın ve etkin erişimi inceleyin.")}</p></div><button className="ghost-button" type="button" onClick={() => setFiltersOpen(value => !value)}>{filtersOpen ? tx("Hide filters", "Filtreleri kapat") : tx("Filter team", "Ekibi filtrele")}</button></header>
        {filtersOpen && <div className="admin-team-filters">
          <label>{tx("Search", "Ara")}<input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={tx("Name or email", "İsim veya e-posta")} /></label>
          <label>{tx("Role", "Rol")}<select value={roleFilter} onChange={(event) => setRoleFilter(event.target.value)}><option value="">{tx("All roles", "Tüm roller")}</option>{Object.keys(roleCounts).map((role) => <option key={role} value={role}>{humanizeFeature(role)}</option>)}</select></label>
          <label>{tx("Access", "Erişim")}<select value={accessFilter} onChange={(event) => setAccessFilter(event.target.value)}><option value="">{tx("All states", "Tüm durumlar")}</option><option value="ENABLED">{tx("Enabled", "Etkin")}</option><option value="DISABLED">{tx("Disabled", "Devre dışı")}</option><option value="LOCKED">{tx("Locked", "Kilitli")}</option></select></label>
          <label>MFA<select value={mfaFilter} onChange={(event) => setMfaFilter(event.target.value)}><option value="">{tx("All", "Tümü")}</option><option value="ENABLED">{tx("Enabled", "Etkin")}</option><option value="MISSING">{tx("Not enrolled", "Kurulmamış")}</option></select></label>
          <button className="ghost-button" type="button" onClick={() => { setQuery(""); setRoleFilter(""); setAccessFilter(""); setMfaFilter(""); }}>{tx("Clear", "Temizle")}</button>
        </div>}
        <DataTable columns={[tx("Admin", "Yönetici"), tx("Role", "Rol"), tx("Access", "Erişim"), "MFA", tx("Sessions", "Oturum"), tx("Last active", "Son etkinlik")]} rows={filteredMembers.map((member) => [
          <div className="entity-cell"><strong>{member.name ?? tx("Admin", "Yönetici")}</strong><small>{member.email ?? "-"}</small></div>, <Badge value={member.role} />,
          <Badge value={member.enabled === false ? tx("Disabled", "Devre dışı") : member.locked ? tx("Locked", "Kilitli") : tx("Enabled", "Etkin")} tone={member.enabled === false || member.locked ? "danger" : "good"} />,
          <Badge value={member.mfaEnabled ? tx("Enabled", "Etkin") : tx("Not enrolled", "Kurulmamış")} tone={member.mfaEnabled ? "good" : "warn"} />, formatValue(member.activeSessions), formatDate(member.lastActiveAt)
        ])} rowData={filteredMembers} onRowClick={canManage ? selectMember : undefined} empty={tx("No administrators match these filters.", "Bu filtrelerle eşleşen yönetici yok.")} />
        <PaginationControls page={data?.page ?? page} pageSize={pageSize} totalElements={data?.totalElements ?? 0} totalPages={Math.max(1, data?.totalPages ?? 1)} first={data?.first ?? page === 0} last={data?.last ?? true} onPageChange={setPage} onPageSizeChange={(size) => { setPageSize(size); setPage(0); }} />
      </section>
      <aside className="admin-team-insights">
        <div><span>{tx("ROLE DISTRIBUTION", "ROL DAĞILIMI")}</span><h3>{tx("Current responsibility mix", "Mevcut sorumluluk dağılımı")}</h3><p>{tx("Select a role to filter the directory.", "Dizini filtrelemek için bir role tıklayın.")}</p></div>
        <div className="admin-role-bars">{roleEntries.map(([role, count]) => <button type="button" key={role} onClick={() => showTeamFilters({ role })}><span><b>{humanizeFeature(role)}</b><strong>{count}</strong></span><i><b style={{ width: `${Math.max(8, count / maximumRoleCount * 100)}%` }} /></i></button>)}</div>
        {!roleEntries.length && <p className="muted-text">{tx("Role distribution will appear when the team loads.", "Ekip yüklendiğinde rol dağılımı görünür.")}</p>}
        <div className="admin-security-posture"><span>{tx("SECURITY POSTURE", "GÜVENLİK DURUMU")}</span><strong>{members.length ? Math.round((members.length - mfaMissing) / members.length * 100) : 0}%</strong><small>{tx("of loaded accounts have MFA", "yüklenen hesaplarda MFA etkin")}</small><i><b style={{ width: `${members.length ? (members.length - mfaMissing) / members.length * 100 : 0}%` }} /></i></div>
      </aside>
    </div>}

    {activeWorkspace === "invitations" && (accessProfile?.role === "OWNER" ? <AdminInvitationPanel onError={onError} /> : <Panel title={tx("Invitations", "Davetler")} description={tx("Owner access is required to invite administrators.", "Yönetici davet etmek için Owner erişimi gerekir.")}><div className="form-notice">{tx("Ask an owner to create or manage administrator invitations.", "Yönetici davetlerini oluşturmak veya yönetmek için bir Owner ile iletişime geçin.")}</div></Panel>)}
    {activeWorkspace === "security" && <div className="admin-team-section-stack"><AdminMfaEnrollmentPanel onError={onError} /><AdminSessionPanel canRevoke={accessProfile?.role === "OWNER"} onError={onError} />{accessProfile?.role === "OWNER" && <OwnerAdminSessionsPanel onError={onError} />}</div>}
    {activeWorkspace === "roles" && <RolePermissionMatrix tr={tr} currentRole={accessProfile?.role} onSelectRole={(role) => showTeamFilters({ role })} />}
    {selected && <div className="modal-backdrop" role="presentation" onClick={() => !saving && setSelected(null)}><section ref={memberDialogRef} tabIndex={-1} className="modal-card admin-member-modal admin-member-drawer" role="dialog" aria-modal="true" aria-label={`${tx("Manage", "Yönet")} ${selected.email ?? "admin"}`} onClick={(event) => event.stopPropagation()}>
      <header className="modal-header"><div><span>{tx("ADMIN ACCESS", "ADMIN ERİŞİMİ")}</span><h2>{selected.name ?? tx("Administrator", "Yönetici")}</h2><p>{selected.email ?? "-"}</p></div><button className="icon-button" disabled={saving} type="button" onClick={() => setSelected(null)} aria-label={tx("Close admin management", "Admin yönetimini kapat")}>×</button></header>
      <div className="admin-member-profile-strip"><div><span>{tx("Current role", "Mevcut rol")}</span><strong>{humanizeFeature(selected.role)}</strong></div><div><span>{tx("Access", "Erişim")}</span><strong>{selected.enabled === false ? tx("Disabled", "Devre dışı") : selected.locked ? tx("Locked", "Kilitli") : tx("Enabled", "Etkin")}</strong></div><div><span>MFA</span><strong>{selected.mfaEnabled ? tx("Enabled", "Etkin") : tx("Not enrolled", "Kurulmamış")}</strong></div><div><span>{tx("Live sessions", "Canlı oturum")}</span><strong>{formatValue(selected.activeSessions)}</strong></div></div>
      {selected.role === "OWNER" ? <div className="admin-owner-protection"><strong>{tx("Protected owner account", "Korumalı Owner hesabı")}</strong><p>{tx("Owner roles cannot be changed through team management. Session and password controls remain available through their dedicated security flows.", "Owner rolleri ekip yönetimi üzerinden değiştirilemez. Oturum ve parola kontrolleri ilgili güvenlik akışlarından kullanılabilir.")}</p></div> : <form className="admin-security-form modal-body" onSubmit={(event) => { event.preventDefault(); void saveMember(); }}>
        <label>{tx("Role", "Rol")}<select value={draft.role} onChange={(event) => setDraft((current) => ({ ...current, role: event.target.value }))}>{["ADMIN_SUPPORT", "ADMIN_CATALOG", "ADMIN_GROWTH", "ADMIN_FINANCE", "ADMIN_TECHNICAL", "ADMIN_READ_ONLY"].map((role) => <option key={role} value={role}>{humanizeFeature(role)}</option>)}</select><small>{tx("Only least-privilege roles can be assigned.", "Yalnızca en az yetkili roller atanabilir.")}</small></label>
        <label className="admin-enabled-control"><span>{tx("Account access", "Hesap erişimi")}</span><span className="toggle-field"><input type="checkbox" checked={draft.enabled} onChange={(event) => setDraft((current) => ({ ...current, enabled: event.target.checked }))} />{draft.enabled ? tx("Enabled", "Etkin") : tx("Disabled", "Devre dışı")}</span></label>
        <div className="admin-member-role-preview"><span>{tx("EFFECTIVE SCOPE", "ETKİN KAPSAM")}</span><strong>{humanizeFeature(draft.role)}</strong><p>{tr ? ROLE_MATRIX.find((item) => item.role === draft.role)?.tr : ROLE_MATRIX.find((item) => item.role === draft.role)?.en}</p><small>{draft.role !== selected.role ? tx("Changing the role revokes existing sessions.", "Rol değişikliği mevcut oturumları sonlandırır.") : tx("No role change selected.", "Rol değişikliği seçilmedi.")}</small></div>
        <label className="wide-field">{tx("Required audit reason", "Zorunlu denetim gerekçesi")}<textarea maxLength={500} required value={draft.reason} onChange={(event) => setDraft((current) => ({ ...current, reason: event.target.value }))} placeholder={tx("Explain why this role or access state is changing.", "Bu rolün veya erişim durumunun neden değiştiğini açıklayın.")} /><small>{draft.reason.length}/500</small></label>
      </form>}
      <footer className="modal-actions admin-member-actions"><button className="ghost-button" disabled={saving} type="button" onClick={() => setSelected(null)}>{tx("Cancel", "Kapat")}</button>{selected.role !== "OWNER" && <button className="ghost-button" disabled={saving} type="button" onClick={() => setPasswordResetConfirmationOpen(true)}>{tx("Send password reset", "Parola sıfırlama gönder")}</button>}{selected.role !== "OWNER" && <button className="primary-button" disabled={saving || !draft.reason.trim()} type="button" onClick={() => void saveMember()}>{saving ? tx("Applying...", "Uygulanıyor...") : tx("Apply access change", "Erişim değişikliğini uygula")}</button>}</footer>
    </section></div>}
    {selected && passwordResetConfirmationOpen && <ConfirmDialog
      title="Send password reset link?"
      message={`A single-use password reset link will be sent to ${selected.email}. Existing sessions will be revoked only after the password is changed.`}
      confirmLabel="Send reset link"
      busy={saving}
      onCancel={() => !saving && setPasswordResetConfirmationOpen(false)}
      onConfirm={() => void sendPasswordReset()}
    />}
  </div>;
}

const ROLE_MATRIX = [
  { role: "ADMIN_SUPPORT", areas: [true, false, false, false, false], en: "User support and account assistance", tr: "Kullanıcı desteği ve hesap yardımı" },
  { role: "ADMIN_CATALOG", areas: [false, true, false, false, false], en: "Catalog, recipes and review operations", tr: "Katalog, tarif ve inceleme operasyonları" },
  { role: "ADMIN_GROWTH", areas: [false, false, true, false, false], en: "Campaigns, notifications and analytics", tr: "Kampanyalar, bildirimler ve analitik" },
  { role: "ADMIN_FINANCE", areas: [false, false, false, true, false], en: "Subscriptions, quotas and commercial audit", tr: "Abonelikler, kotalar ve ticari denetim" },
  { role: "ADMIN_TECHNICAL", areas: [false, false, false, false, true], en: "Runtime, integrations, delivery and AI", tr: "Çalışma zamanı, entegrasyon, teslimat ve AI" },
  { role: "ADMIN_READ_ONLY", areas: [true, true, true, true, true], en: "Broad inspection with all writes blocked", tr: "Yazma işlemleri kapalı geniş inceleme erişimi" }
];

function RolePermissionMatrix({ tr, currentRole, onSelectRole }: { tr: boolean; currentRole?: string; onSelectRole: (role: string) => void }) {
  const headers = tr ? ["Destek", "Katalog", "Büyüme", "Finans", "Teknik"] : ["Support", "Catalog", "Growth", "Finance", "Technical"];
  return <Panel className="admin-role-matrix-panel" title={tr ? "Rol ve yetki sınırları" : "Role and permission boundaries"} description={tr ? "Her rolün operasyon alanını karşılaştırın; bir role tıklayarak ekip dizinini filtreleyin." : "Compare each operational boundary, then select a role to filter the team directory."}>
    <div className="admin-role-overview">{ROLE_MATRIX.map((item, index) => <button type="button" key={item.role} className={currentRole === item.role ? "current" : ""} onClick={() => onSelectRole(item.role)}><span>{String(index + 1).padStart(2,"0")}</span><div><strong>{humanizeFeature(item.role)}</strong><small>{tr ? item.tr : item.en}</small></div><b>→</b></button>)}</div>
    <div className="admin-role-matrix" role="table" aria-label={tr ? "Rol yetki karşılaştırması" : "Role permission comparison"}>
      <div className="admin-role-matrix-row admin-role-matrix-head" role="row"><span role="columnheader">{tr ? "Rol" : "Role"}</span>{headers.map((header) => <span key={header} role="columnheader">{header}</span>)}<span role="columnheader">{tr ? "Kapsam" : "Scope"}</span></div>
      {ROLE_MATRIX.map((item) => <button className={`admin-role-matrix-row ${currentRole === item.role ? "current" : ""}`} type="button" role="row" key={item.role} onClick={() => onSelectRole(item.role)}><strong role="cell">{humanizeFeature(item.role)}{currentRole === item.role && <small>{tr ? "Sizin rolünüz" : "Your role"}</small>}</strong>{item.areas.map((enabled, index) => <span role="cell" key={`${item.role}-${headers[index]}`} className={enabled ? "allowed" : "blocked"}>{enabled ? "✓" : "–"}</span>)}<span role="cell">{tr ? item.tr : item.en}</span></button>)}
    </div>
    <div className="admin-role-guidance"><strong>{tr ? "En az yetki ilkesi" : "Least-privilege guidance"}</strong><p>{tr ? "Yöneticiyi yalnızca günlük sorumluluğu için gereken role atayın. Read-only rolü geniş görünürlük sağlar ancak tüm yazma işlemlerini backend seviyesinde engeller." : "Assign each administrator only to the role required for their daily responsibility. Read-only provides broad visibility while backend enforcement blocks every write."}</p></div>
  </Panel>;
}
