import { FormEvent, useCallback, useEffect, useState } from "react";
import { formatRequestError, request } from "../api";
import { AdminAccessProfile } from "../types";
import { LoadState, MetricCard, Panel, SectionToolbar } from "../AdminPrimitives";

type Policy = {
  id: number;
  version: number;
  enabled: boolean;
  minimumIntervalHours: number;
  maxImpressions24h: number;
  dismissCooldownHours: number;
  minimumSessionNumber: number;
  rolloutPercentage: number;
  campaignVersion: number;
  changeReason?: string;
  updatedBy?: string;
  updatedAt?: string;
};

type Draft = Pick<Policy, "version" | "enabled" | "minimumIntervalHours" | "maxImpressions24h" | "dismissCooldownHours" | "minimumSessionNumber" | "rolloutPercentage"> & { changeReason: string };
const API = "/api/v1/admin/free-promotion";

function toDraft(policy: Policy): Draft {
  return {
    version: policy.version,
    enabled: policy.enabled,
    minimumIntervalHours: policy.minimumIntervalHours,
    maxImpressions24h: policy.maxImpressions24h,
    dismissCooldownHours: policy.dismissCooldownHours,
    minimumSessionNumber: policy.minimumSessionNumber,
    rolloutPercentage: policy.rolloutPercentage,
    changeReason: ""
  };
}

export function FreePromotionPolicyView({ accessProfile, onError }: { accessProfile: AdminAccessProfile | null; onError: (message: string | null) => void }) {
  const canManage = Boolean(accessProfile?.permissions?.includes("GROWTH_MANAGE"));
  const [policy, setPolicy] = useState<Policy | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [state, setState] = useState<LoadState>("idle");
  const [actionState, setActionState] = useState<LoadState>("idle");
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setState("loading");
    try {
      const next = await request<Policy>(API);
      setPolicy(next);
      setDraft(toDraft(next));
      setState("ready");
      setNotice(null);
      onError(null);
    } catch (error) {
      const message = formatRequestError(error);
      setState("error");
      onError(message);
    }
  }, [onError]);

  useEffect(() => { void load(); }, [load]);

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!draft || !canManage || !draft.changeReason.trim()) return;
    setActionState("loading");
    setNotice(null);
    try {
      const saved = await request<Policy>(API, { method: "PUT", body: { ...draft, changeReason: draft.changeReason.trim() } });
      setPolicy(saved);
      setDraft(toDraft(saved));
      setActionState("ready");
      setNotice(saved.enabled ? `Free promotion is active for ${saved.rolloutPercentage}% of eligible users.` : "Free promotion is disabled. No automatic paywall will be shown.");
      onError(null);
    } catch (error) {
      const message = formatRequestError(error);
      setActionState("error");
      setNotice(message.toLowerCase().includes("version") || message.toLowerCase().includes("conflict")
        ? "This policy changed in another admin session. Refresh before saving again."
        : message);
    }
  }

  return <div className="stack free-promotion-policy-view">
    <SectionToolbar title="Free user paywall" description="Control the optional home-screen plan introduction. Eligibility and frequency are enforced by the backend." state={state} onReload={() => void load()} />
    {notice && <div className={`form-notice ${actionState === "error" ? "warning" : ""}`} role="status">{notice}</div>}
    {!canManage && <div className="form-notice warning" role="note"><strong>Read-only access.</strong> GROWTH_MANAGE is required to change this policy.</div>}
    <div className="metric-grid">
      <MetricCard label="Current state" value={policy?.enabled ? "ACTIVE" : "DISABLED"} hint={policy?.enabled ? `${policy.rolloutPercentage}% deterministic rollout` : "Fail-closed; no automatic display"} />
      <MetricCard label="Minimum interval" value={policy ? `${policy.minimumIntervalHours} hours` : "—"} hint="Measured from recorded impressions" />
      <MetricCard label="24-hour cap" value={String(policy?.maxImpressions24h ?? "—")} hint="Maximum impressions per Free account" />
      <MetricCard label="Campaign version" value={String(policy?.campaignVersion ?? "—")} hint={`Policy row version ${policy?.version ?? "—"}`} />
    </div>
    <Panel title="Eligibility and frequency policy" description="Paid accounts, pending purchases, the first session, unsafe screens, and open dialogs are always suppressed outside these controls.">
      {draft ? <form className="meal-reminder-policy-form" onSubmit={(event) => void save(event)}>
        <label className="toggle-field span-2"><input disabled={!canManage} type="checkbox" checked={draft.enabled} onChange={(event) => setDraft({ ...draft, enabled: event.target.checked })} />Enable automatic Free-user plan introduction</label>
        <label>Minimum interval (hours)<input disabled={!canManage} min={1} max={720} type="number" value={draft.minimumIntervalHours} onChange={(event) => setDraft({ ...draft, minimumIntervalHours: Number(event.target.value) })} /></label>
        <label>Maximum per rolling 24h<input disabled={!canManage} min={1} max={24} type="number" value={draft.maxImpressions24h} onChange={(event) => setDraft({ ...draft, maxImpressions24h: Number(event.target.value) })} /></label>
        <label>Dismiss cooldown (hours)<input disabled={!canManage} min={1} max={720} type="number" value={draft.dismissCooldownHours} onChange={(event) => setDraft({ ...draft, dismissCooldownHours: Number(event.target.value) })} /></label>
        <label>Minimum cold-start session<input disabled={!canManage} min={1} max={100} type="number" value={draft.minimumSessionNumber} onChange={(event) => setDraft({ ...draft, minimumSessionNumber: Number(event.target.value) })} /></label>
        <label>Rollout percentage<input disabled={!canManage} min={0} max={100} type="number" value={draft.rolloutPercentage} onChange={(event) => setDraft({ ...draft, rolloutPercentage: Number(event.target.value) })} /><small>0% is a second kill switch even when enabled.</small></label>
        <label className="span-2">Required audit reason<textarea disabled={!canManage} maxLength={500} required value={draft.changeReason} onChange={(event) => setDraft({ ...draft, changeReason: event.target.value })} placeholder="Explain why this frequency or rollout is changing." /><small>{draft.changeReason.length}/500</small></label>
        {draft.enabled && <div className="form-notice warning span-2" role="note"><strong>Activation warning:</strong> Saving will make eligible Free users in the selected rollout cohort see the paywall on a safe home return.</div>}
        <div className="inline-actions span-2"><button className="primary-button" disabled={!canManage || !draft.changeReason.trim() || actionState === "loading"} type="submit">{actionState === "loading" ? "Saving..." : "Save policy"}</button></div>
      </form> : <p>No policy configuration returned.</p>}
    </Panel>
    <div className="form-notice" role="note"><strong>Runtime safeguards:</strong> one display per app session, server-issued reservation tokens, a five-minute reservation expiry, exact Free-plan verification, and an impression-time kill-switch recheck.</div>
  </div>;
}
