import { useEffect, useMemo, useState } from "react";

import { formatRequestError, PageResponse, request } from "../api";

import { AdminAccessProfile, AdminApprovalRequest, AiCreditPricingPolicy, AuditEntry, FeatureMatrixItem, RevenueCatConfigStatus, SubscriptionDto, SubscriptionFeatureAccess, UserProfile } from "../types";

import { DataTable, EmptyState, LoadState, PaginationControls, Panel, SectionToolbar } from "../AdminPrimitives";

import { ApprovalSubmissionNotice, Badge, ConfirmDialog, DatePickerButton, PLAN_ORDER, buildAuditPath, combineStates, formatDate, formatValue, humanizeFeature, parsePositiveInt, submitAdminApproval, useEndpoint } from "./../admin/shared";
import { readUserRouteContext, sectionPaths } from "../admin/navigation";
import { useAdminLocale } from "../admin/locale";

export const SUBSCRIPTION_STATUS_OPTIONS = ["ACTIVE", "TRIALING", "CANCELED", "EXPIRED", "REFUNDED"];

export const BILLING_PERIOD_OPTIONS = ["NONE", "MONTHLY", "YEARLY"];

export const PAYMENT_PROVIDER_OPTIONS = ["MANUAL_ADMIN", "REVENUECAT", "APPLE_APP_STORE", "GOOGLE_PLAY_STORE", "STRIPE"];

export const FEATURE_ORDER = [
  "BARCODE_SCANNER",
  "MANUAL_FOOD_LOGGING",
  "FOOD_DIARY",
  "WEIGHT_PROGRESS",
  "WATER_TRACKING",
  "WORKOUT_LOGGING",
  "SAVED_MEAL_TEMPLATES",
  "RECIPE_BUILDER",
  "PUBLIC_RECIPE_LIBRARY",
  "NEXT_MEAL_SUGGESTIONS",
  "ADVANCED_MACRO_TARGETS",
  "MICRONUTRIENT_DETAILS",
  "MICRONUTRIENT_ANALYTICS",
  "DATA_EXPORT",
  "FASTING_BASIC",
  "FASTING_ADVANCED",
  "AI_MEAL_DRAFTS",
  "AI_RECIPE_GENERATION",
  "AI_MEAL_PREPARATION_GUIDE",
  "AI_NUTRITION_PLAN",
  "AI_WORKOUT_PLANNER",
  "AI_INSIGHTS",
  "HEALTH_INTEGRATION",
  "ADVANCED_ANALYTICS",
  "CUSTOM_FOOD_LIBRARY",
  "AD_FREE"
];

export const CONTROLLED_AI_FEATURES = [
  {
    feature: "AI_MEAL_DRAFTS",
    label: "Meal drafts",
    endpoint: "/api/v1/ai/meal-drafts",
    detail: "Photo and voice food draft flows. User review is required before diary data is written."
  },
  {
    feature: "AI_RECIPE_GENERATION",
    label: "Recipe assistant",
    endpoint: "/api/v1/ai/recipes/generate",
    detail: "Creates a recipe draft only. The reviewed recipe is saved after confirmation."
  },
  {
    feature: "AI_MEAL_PREPARATION_GUIDE",
    label: "Meal preparation guide",
    endpoint: "/api/v1/ai/meal-preparation-guides",
    detail: "Generates preparation guidance from an immutable meal snapshot. Reopening an existing guide does not consume AI quota."
  },
  {
    feature: "AI_NUTRITION_PLAN",
    label: "Nutrition planner",
    endpoint: "/api/v1/ai/nutrition-plans/generate",
    detail: "Creates reviewable nutrition-plan drafts using backend-owned profile, target, preference, and workout context."
  },
  {
    feature: "AI_WORKOUT_PLANNER",
    label: "Workout planner",
    endpoint: "/api/v1/ai/workout-plans/generate",
    detail: "Creates workout plan drafts and active plan snapshots, not exercise logs."
  },
  {
    feature: "AI_INSIGHTS",
    label: "Coaching insights",
    endpoint: "/api/v1/ai/insights",
    detail: "Daily and weekly read-only coaching cards built from app-owned tracking context."
  }
];

export type SubscriptionMode = "overview" | "features" | "mapping" | "entitlements" | "access" | "aiQuotas";

export function SubscriptionsView({ mode, onError, accessProfile }: { mode: SubscriptionMode; onError: (message: string | null) => void; accessProfile: AdminAccessProfile | null }) {
  const { locale } = useAdminLocale();
  const tx = (english: string, turkish: string) => locale === "tr" ? turkish : english;
  const [initialUserContext, setInitialUserContext] = useState(() => readUserRouteContext());
  const [userSearchQuery, setUserSearchQuery] = useState<string>(initialUserContext?.userEmail ?? (initialUserContext ? String(initialUserContext.userId) : ""));
  const subscriptionUserPath = useMemo(() => {
    const params = new URLSearchParams({ role: "STANDARD", page: "0", size: "25" });
    if (userSearchQuery.trim()) params.set("search", userSearchQuery.trim());
    return `/api/v1/admin/users?${params.toString()}`;
  }, [userSearchQuery]);
  const { data, state, reload } = useEndpoint<FeatureMatrixItem[]>("/api/v1/admin/subscriptions/features", onError);
  const { data: pricingData, state: pricingState, reload: reloadPricing } = useEndpoint<AiCreditPricingPolicy[]>("/api/v1/admin/ai-credit-pricing", onError);
  const { data: users, state: usersState } = useEndpoint<PageResponse<UserProfile>>(subscriptionUserPath, onError);
  const { data: revenueCat, state: revenueCatState, reload: reloadRevenueCat } = useEndpoint<RevenueCatConfigStatus>("/api/v1/admin/revenuecat/config", onError);
  const [auditPage, setAuditPage] = useState(0);
  const [auditPageSize, setAuditPageSize] = useState(10);
  const subscriptionAuditPath = useMemo(() => buildAuditPath({ actionType: "", targetType: "USER_SUBSCRIPTION", page: auditPage, size: auditPageSize }), [auditPage, auditPageSize]);
  const { data: subscriptionAudits, state: subscriptionAuditState, reload: reloadSubscriptionAudits } = useEndpoint<PageResponse<AuditEntry>>(subscriptionAuditPath, onError);
  const [savingKey, setSavingKey] = useState<string | null>(null);
  const [featureApprovalNotice, setFeatureApprovalNotice] = useState<{ request: AdminApprovalRequest; message: string } | null>(null);
  const [subscriptionActionState, setSubscriptionActionState] = useState<LoadState>("idle");
  const [subscriptionResult, setSubscriptionResult] = useState<SubscriptionDto | null>(null);
  const [matrixApplyConfirmationOpen, setMatrixApplyConfirmationOpen] = useState(false);
  const [accessPreviewUserId, setAccessPreviewUserId] = useState<string>(initialUserContext ? String(initialUserContext.userId) : "");
  const [userPickerOpen, setUserPickerOpen] = useState(false);
  const [previewUserAutoSelected, setPreviewUserAutoSelected] = useState(Boolean(initialUserContext));
  const [accessPreview, setAccessPreview] = useState<SubscriptionFeatureAccess | null>(null);
  const [accessPreviewState, setAccessPreviewState] = useState<LoadState>("idle");
  const [accessFeatureQuery, setAccessFeatureQuery] = useState("");
  const [accessFeatureState, setAccessFeatureState] = useState<"ALL" | "ALLOWED" | "BLOCKED">("ALL");
  const [accessAssignmentOpen, setAccessAssignmentOpen] = useState(false);
  const [subscriptionForm, setSubscriptionForm] = useState({
    planType: "PRO",
    status: "ACTIVE",
    billingPeriod: "MONTHLY",
    startDate: todayIsoDate(),
    endDate: "",
    aiMonthlyQuota: "150",
    aiUsedThisPeriod: "0",
    autoRenew: true,
    provider: "MANUAL_ADMIN",
    providerSubscriptionId: ""
  });
  const [addonForm, setAddonForm] = useState({ amount: "15", validityDays: "30", note: "Admin credit adjustment" });
  const features = data ?? [];
  const pricingPolicies = pricingData ?? [];
  const previewUsers = users?.content ?? [];
  const selectedPreviewUser = previewUsers.find((user) => String(user.id) === accessPreviewUserId);
  const selectedPreviewLabel = selectedPreviewUser ? userOptionLabel(selectedPreviewUser).toLowerCase() : "";
  const previewSearchQuery = userSearchQuery.trim().toLowerCase();
  const shouldFilterPreviewUsers = Boolean(previewSearchQuery && previewSearchQuery !== selectedPreviewLabel);
  const userDropdownSearchValue = shouldFilterPreviewUsers ? userSearchQuery : "";
  const filteredPreviewUsers = previewUsers.filter((user) => {
    if (user.role === "ADMIN" || user.id === undefined) return false;
    if (!shouldFilterPreviewUsers) return true;
    return [user.email, user.name, String(user.id)].some((value) => String(value ?? "").toLowerCase().includes(previewSearchQuery));
  });
  const plans = PLAN_ORDER;
  const grouped = plans.map((plan) => ({
    plan,
    items: features.filter((item) => item.planType === plan),
    enabledCount: features.filter((item) => item.planType === plan && item.enabled).length
  }));
  const enabledTotal = features.filter((item) => item.enabled).length;
  const warningCount = (revenueCat?.missingRequiredConfig?.length ?? 0) + (revenueCat?.warnings?.length ?? 0);
  const selectedUserId = accessPreviewUserId ? Number(accessPreviewUserId) : null;
  const quotaAudits = (subscriptionAudits?.content ?? []).filter((audit) => ["SUBSCRIPTION_UPDATE", "SUBSCRIPTION_ENTITLEMENT_MATRIX_APPLY", "AI_QUOTA_RESET", "AI_QUOTA_ADDON_GRANT"].includes(audit.actionType ?? ""));


  const title = {
    overview: tx("Subscription control center", "Abonelik kontrol merkezi"),
    features: tx("Plan feature matrix", "Plan özellik matrisi"),
    mapping: tx("Store products & entitlement policy", "Mağaza ürünleri ve hak politikası"),
    entitlements: tx("Entitlement snapshot policy", "Hak anlık görüntü politikası"),
    access: tx("User access preview", "Kullanıcı erişim önizlemesi"),
    aiQuotas: tx("AI quota and add-on mapping", "AI kotası ve ek paket eşleştirmesi")
  }[mode];

  function userOptionLabel(user: UserProfile): string {
    return user.email ?? user.name ?? String(user.id ?? "");
  }

  function selectQuotaUser(user: UserProfile) {
    if (user.id === undefined) return;
    setAccessPreviewUserId(String(user.id));
    setUserSearchQuery(userOptionLabel(user));
    setUserPickerOpen(false);
  }


  function updateUserPickerSearch(value: string) {
    setUserSearchQuery(value);
    setAccessPreviewUserId("");
    setUserPickerOpen(true);
  }
  function clearUserContext() {
    setUserSearchQuery("");
    setAccessPreviewUserId("");
    setAccessPreview(null);
    setPreviewUserAutoSelected(false);
    setInitialUserContext(null);
    window.history.replaceState(null, "", sectionPaths.subscriptionAccess);
  }
  function updateSubscriptionForm(key: keyof typeof subscriptionForm, value: string | boolean) {
    if (key === "planType" && typeof value === "string") {
      setSubscriptionForm((current) => {
        const nextBillingPeriod = value === "FREE" ? "NONE" : current.billingPeriod === "NONE" ? "MONTHLY" : current.billingPeriod;
        const expiredEndDate = isPastIsoDate(current.endDate);
        const nextStartDate = value !== "FREE" && expiredEndDate ? todayIsoDate() : current.startDate;
        return {
          ...current,
          planType: value,
          billingPeriod: nextBillingPeriod,
          startDate: nextStartDate,
          endDate: value === "FREE" ? "" : expiredEndDate ? subscriptionPeriodEndDate(nextStartDate, nextBillingPeriod) : current.endDate,
          aiMonthlyQuota: String(defaultAiQuota(value)),
          aiUsedThisPeriod: value === "FREE" ? "0" : current.aiUsedThisPeriod,
          autoRenew: value !== "FREE"
        };
      });
      return;
    }
    setSubscriptionForm((current) => ({ ...current, [key]: value }));
  }

  function updateAddonForm(key: keyof typeof addonForm, value: string) {
    setAddonForm((current) => ({ ...current, [key]: value }));
  }

  async function refreshSelectedAccess(userId: string) {
    const response = await request<SubscriptionFeatureAccess>(`/api/v1/admin/subscriptions/users/${userId}/features`);
    setAccessPreview(response);
    setAccessPreviewState("ready");
    return response;
  }

  useEffect(() => {
    if (previewUserAutoSelected || accessPreviewUserId || !previewUsers.length) return;
    const firstStandardUser = previewUsers.find((user) => user.role !== "ADMIN" && user.id !== undefined) ?? previewUsers.find((user) => user.id !== undefined);
    if (firstStandardUser?.id !== undefined) {
      setAccessPreviewUserId(String(firstStandardUser.id));
      setUserSearchQuery(userOptionLabel(firstStandardUser));
    }
    setPreviewUserAutoSelected(true);
  }, [accessPreviewUserId, previewUserAutoSelected, previewUsers]);

  useEffect(() => {
    if (!accessPreviewUserId) {
      setAccessPreview(null);
      setAccessPreviewState("idle");
      return;
    }
    let active = true;
    setAccessPreviewState("loading");
    onError(null);
    async function loadAccessPreview() {
      try {
        const [featureResponse, subscriptionResponse] = await Promise.all([
          request<SubscriptionFeatureAccess>(`/api/v1/admin/subscriptions/users/${accessPreviewUserId}/features`),
          request<SubscriptionDto>(`/api/v1/admin/subscriptions/users/${accessPreviewUserId}`)
        ]);
        if (!active) return;
        setAccessPreview(featureResponse);
        setSubscriptionResult(subscriptionResponse);
        const planType = subscriptionResponse.planType ?? subscriptionResponse.plan ?? "FREE";
        setSubscriptionForm((current) => ({
          ...current,
          planType,
          status: subscriptionResponse.status ?? "ACTIVE",
          billingPeriod: subscriptionResponse.billingPeriod ?? (planType === "FREE" ? "NONE" : "MONTHLY"),
          startDate: subscriptionResponse.startDate ?? todayIsoDate(),
          endDate: subscriptionResponse.endDate ?? "",
          aiMonthlyQuota: String(subscriptionResponse.aiMonthlyQuota ?? defaultAiQuota(planType)),
          aiUsedThisPeriod: String(subscriptionResponse.aiUsedThisPeriod ?? 0),
          autoRenew: planType !== "FREE" && (subscriptionResponse.autoRenew ?? true),
          provider: subscriptionResponse.provider ?? "MANUAL_ADMIN"
        }));
        setAccessPreviewState("ready");
      } catch (err) {
        if (!active) return;
        setAccessPreview(null);
        setAccessPreviewState("error");
        onError(formatRequestError(err));
      }
    }
    void loadAccessPreview();
    return () => {
      active = false;
    };
  }, [accessPreviewUserId, onError]);

  useEffect(() => {
    if (!accessPreview) return;
    const totalQuota = (accessPreview.aiMonthlyQuota ?? 0) + (accessPreview.aiAddonQuota ?? 0);
    const used = Math.max(0, totalQuota - (accessPreview.aiRemainingThisPeriod ?? 0));
    const planType = accessPreview.planType ?? accessPreview.plan ?? "FREE";
    setSubscriptionForm((current) => ({
      ...current,
      planType,
      billingPeriod: planType === "FREE" ? "NONE" : current.billingPeriod === "NONE" ? "MONTHLY" : current.billingPeriod,
      aiMonthlyQuota: String(accessPreview.aiMonthlyQuota ?? defaultAiQuota(planType)),
      aiUsedThisPeriod: String(used)
    }));
  }, [accessPreview]);

  async function applySubscriptionUpdate() {
    if (!selectedUserId) {
      onError("Select a user before updating subscription.");
      return;
    }
    if (isActivePaidSubscriptionWithExpiredEndDate(subscriptionForm)) {
      onError("Active PLUS/PRO subscriptions must end today or later. Select a new end date before applying.");
      return;
    }
    setSubscriptionActionState("loading");
    onError(null);
    try {
      const approval = await submitAdminApproval(
        "SUBSCRIPTION_UPDATE",
        String(selectedUserId),
        {
          planType: subscriptionForm.planType,
          status: subscriptionForm.status,
          billingPeriod: subscriptionForm.billingPeriod,
          startDate: subscriptionForm.startDate || null,
          endDate: subscriptionForm.endDate || null,
          aiMonthlyQuota: parseNonNegativeInt(subscriptionForm.aiMonthlyQuota),
          aiUsedThisPeriod: parseNonNegativeInt(subscriptionForm.aiUsedThisPeriod),
          autoRenew: subscriptionForm.autoRenew,
          provider: subscriptionForm.provider,
          providerSubscriptionId: subscriptionForm.providerSubscriptionId.trim() || null
        },
        `Update subscription for user ${selectedUserId}`
      );
      setFeatureApprovalNotice({ request: approval, message: "Subscription change is pending approval. Current user access remains unchanged until it is approved." });
      setSubscriptionActionState("ready");
    } catch (err) {
      setSubscriptionActionState("error");
      onError(formatRequestError(err));
    }
  }

  async function resetSelectedAiQuota() {
    if (!selectedUserId) {
      onError("Select a user before resetting AI quota.");
      return;
    }
    setSubscriptionActionState("loading");
    onError(null);
    try {
      const approval = await submitAdminApproval("AI_QUOTA_RESET", String(selectedUserId), {}, "AI quota reset requested from User Access");
      setFeatureApprovalNotice({ request: approval, message: "AI quota reset is pending approval. Current usage remains unchanged until it is approved." });
      setSubscriptionActionState("ready");
    } catch (err) {
      setSubscriptionActionState("error");
      onError(formatRequestError(err));
    }
  }

  async function grantSelectedAddonQuota() {
    if (!selectedUserId) {
      onError("Select a user before granting add-on quota.");
      return;
    }
    setSubscriptionActionState("loading");
    onError(null);
    try {
      const approval = await submitAdminApproval(
        "AI_ADDON_QUOTA_GRANT",
        String(selectedUserId),
        {
          amount: parsePositiveInt(addonForm.amount),
          validityDays: parsePositiveInt(addonForm.validityDays),
          note: addonForm.note.trim() || null
        },
        addonForm.note.trim() || `Grant add-on AI quota to user ${selectedUserId}`
      );
      setFeatureApprovalNotice({ request: approval, message: "Add-on AI quota grant is pending approval. No credits have been added yet." });
      setSubscriptionActionState("ready");
    } catch (err) {
      setSubscriptionActionState("error");
      onError(formatRequestError(err));
    }
  }
  async function applyCurrentFeatureMatrixNow() {
    if (!selectedUserId) {
      onError("Select a user before applying the current feature matrix.");
      return;
    }
    setSubscriptionActionState("loading");
    onError(null);
    try {
      const approval = await submitAdminApproval("ENTITLEMENT_MATRIX_APPLY", String(selectedUserId), {}, "Apply current feature matrix before renewal");
      setFeatureApprovalNotice({ request: approval, message: "Applying the current feature matrix is pending approval. Current user access remains unchanged." });
      setSubscriptionActionState("ready");
      setMatrixApplyConfirmationOpen(false);
    } catch (err) {
      setSubscriptionActionState("error");
      onError(formatRequestError(err));
    }
  }
  async function updateFeature(item: FeatureMatrixItem, enabled: boolean) {
    if (!item.planType || !item.feature) {
      onError("Plan or feature key is missing.");
      return;
    }
    const key = featureKey(item);
    setSavingKey(key);
    setFeatureApprovalNotice(null);
    onError(null);
    try {
      const approval = await submitAdminApproval(
        "PLAN_FEATURE_UPDATE",
        `${item.planType}:${item.feature}`,
        {
          enabled,
          aiCreditCost: item.aiCreditCost ?? 1,
          effectiveFrom: item.effectiveFrom || todayIsoDate()
        },
        `${enabled ? "Enable" : "Disable"} ${item.feature} for ${item.planType}`
      );
      setFeatureApprovalNotice({ request: approval, message: `${humanizeFeature(item.feature)} for ${item.planType} is pending approval. The current matrix remains unchanged until it is approved.` });
    } catch (err) {
      onError(formatRequestError(err));
    } finally {
      setSavingKey(null);
    }
  }


  async function updatePricingPolicy(
    policy: AiCreditPricingPolicy,
    draft: AiCreditPricingPolicy
  ) {
    if (!policy.feature) {
      onError("AI feature key is missing.");
      return;
    }
    const values = {
      baseCreditCost: Number(draft.baseCreditCost),
      includedUnits: Number(draft.includedUnits),
      unitsPerAdditionalCredit: Number(draft.unitsPerAdditionalCredit),
      contextSurcharge: Number(draft.contextSurcharge),
      maxCreditCost: draft.pricingMode === "FIXED" ? Number(draft.baseCreditCost) : Number(draft.maxCreditCost)
    };
    if (!Object.values(values).every(Number.isInteger)
        || values.baseCreditCost < 1
        || values.includedUnits < 0
        || values.unitsPerAdditionalCredit < 1
        || values.contextSurcharge < 0
        || values.maxCreditCost < values.baseCreditCost
        || values.maxCreditCost > 50) {
      onError("AI credit policy contains invalid values.");
      return;
    }
    const key = "pricing:" + policy.feature;
    setSavingKey(key);
    onError(null);
    try {
      await request<AiCreditPricingPolicy>("/api/v1/admin/ai-credit-pricing/" + policy.feature, {
        method: "PUT",
        body: values
      });
      await reloadPricing();
    } catch (err) {
      onError(formatRequestError(err));
    } finally {
      setSavingKey(null);
    }
  }
  const matrixFeatures = uniqueFeatures(features);
  const featureGroups = [matrixFeatures.slice(0, Math.ceil(matrixFeatures.length / 2)), matrixFeatures.slice(Math.ceil(matrixFeatures.length / 2))];
  const accessFeatureUniverse = matrixFeatures.length ? matrixFeatures : FEATURE_ORDER;
  const allowedAccessCount = accessFeatureUniverse.filter((feature) => accessFeatureValue(accessPreview, feature)).length;
  const resolvedAccessFeatures = accessFeatureUniverse.filter((feature) => {
    const enabled = accessFeatureValue(accessPreview, feature);
    const matchesState = accessFeatureState === "ALL" || (accessFeatureState === "ALLOWED" ? enabled : !enabled);
    const query = accessFeatureQuery.trim().toLowerCase();
    return matchesState && (!query || humanizeFeature(feature).toLowerCase().includes(query) || featureDescription(feature).toLowerCase().includes(query));
  });
  return (
    <div className="stack">
      <SectionToolbar title={title} state={combineStates([state, pricingState, revenueCatState, usersState, subscriptionAuditState, subscriptionActionState])} onReload={() => { void reload(); void reloadPricing(); }}>
        <button className="ghost-button" onClick={reloadRevenueCat} type="button">{tx("Reload RevenueCat", "RevenueCat'i yenile")}</button>
      </SectionToolbar>
      {featureApprovalNotice && <ApprovalSubmissionNotice {...featureApprovalNotice} isOwner={accessProfile?.role === "OWNER"} />}

      {(mode === "overview" || mode === "mapping" || mode === "entitlements") && <div className="subscription-hero">
        <div>
          <p className="eyebrow">{tx("Product policy", "Ürün politikası")}</p>
          <h2>{tx("Plan rules for new purchases and renewals.", "Yeni satın almalar ve yenilemeler için plan kuralları.")}</h2>
          <p>{tx("Existing entitlement snapshots remain valid until the active billing period ends.", "Mevcut hak anlık görüntüleri aktif faturalama dönemi bitene kadar geçerli kalır.")}</p>
        </div>
        <div className="subscription-quick-stats">
          <div>
            <span>Feature rules</span>
            <strong>{enabledTotal}/{features.length || 0}</strong>
          </div>
          <div>
            <span>RevenueCat</span>
            <strong>{revenueCat?.productionReady ? "Ready" : "Setup"}</strong>
          </div>
          <div>
            <span>Warnings</span>
            <strong>{warningCount}</strong>
          </div>
        </div>
      </div>}

      {mode === "overview" && <div className="plan-overview-grid">
        {grouped.map(({ plan, items, enabledCount }) => (
          <article className={`plan-card ${plan.toLowerCase()}`} key={plan}>
            <div>
              <span>{plan}</span>
              <strong>{enabledCount}/{items.length || 0}</strong>
              <small>enabled features</small>
            </div>
            <div className="plan-feature-list">
              {items.map((item) => (
                <div key={featureKey(item)}>
                  <span>{humanizeFeature(item.feature)}</span>
                  <Badge value={item.enabled ? "On" : "Off"} tone={item.enabled ? "good" : "neutral"} />
                </div>
              ))}
              {!items.length && <span className="muted-text">No feature rows</span>}
            </div>
          </article>
        ))}
      </div>}

      {mode === "features" && <Panel title={tx("Plan feature matrix", "Plan özellik matrisi")}>
        <div className="feature-matrix">
          {featureGroups.map((group, groupIndex) => (
            <div className="feature-matrix-column" key={`feature-group-${groupIndex}`}>
              <div className="feature-matrix-head">
                <span>Feature rule</span>
                {plans.map((plan) => <span key={plan}>{plan}</span>)}
              </div>
              {group.map((feature) => (
            <div className="feature-matrix-row" key={feature}>
              <div>
                <strong>{humanizeFeature(feature)}</strong>
                <small>{featureDescription(feature)}</small>
              </div>
              {plans.map((plan) => {
                const item = features.find((candidate) => candidate.planType === plan && candidate.feature === feature);
                const key = item ? featureKey(item) : `${plan}:${feature}`;
                return (
                                    <div
                    className={item?.enabled ? "feature-toggle-cell enabled" : "feature-toggle-cell"}
                    key={key}
                    title={item ? `${plan} ${feature}: ${item.enabled ? "enabled" : "disabled"}${item.effectiveFrom ? ` from ${item.effectiveFrom}` : ""}` : `${plan} ${feature}: not configured`}
                  >
                    {item ? (
                      <>
                        <label className="switch-control" title={`${plan} ${feature}`}>
                          <input
                            checked={Boolean(item.enabled)}
                            disabled={savingKey === key || feature === "AD_FREE" || feature === "BARCODE_SCANNER"}
                            onChange={(event) => updateFeature(item, event.target.checked)}
                            type="checkbox"
                          />
                          <span />
                        </label>
                      </>
                    ) : (
                      <span className="muted-text">Not configured</span>
                    )}
                  </div>
                );
              })}
            </div>
              ))}
            </div>
          ))}
          {!features.length && <EmptyState message="No subscription feature rows found." />}
        </div>
      </Panel>}

      {mode === "access" && <div className="subscription-ops-grid user-access-preview-layout">
        {initialUserContext && <div className="target-context-banner"><div><span>{locale === "tr" ? "KULLANICI BAĞLAMI" : "USER CONTEXT"}</span><strong>{initialUserContext.userEmail ?? `${locale === "tr" ? "Kullanıcı" : "User"} #${initialUserContext.userId}`}</strong><small>{locale === "tr" ? "Customer 360 üzerinden açıldı. Kullanıcı kimliği sayfa adresinde korunur." : "Opened from Customer 360. The user id is preserved in the page address."}</small></div><button className="ghost-button" type="button" onClick={clearUserContext}>{locale === "tr" ? "Kullanıcı bağlamını temizle" : "Clear user context"}</button></div>}
        <div className="user-access-top-row">
        <Panel className="access-user-panel" title={tx("Select user", "Kullanıcı seç")} description={tx("Search once to inspect the effective subscription rights for an account.", "Bir hesabın etkin abonelik haklarını incelemek için kullanıcıyı arayın.")}>
          <div className="access-user-command">
          <div className="admin-subscription-grid access-user-grid">
            <label className="user-search-field full-width-field">
              User
              <div className="searchable-select">
                <input
                  aria-expanded={userPickerOpen}
                  aria-haspopup="listbox"
                  role="combobox"
                  value={userSearchQuery}
                  onChange={(event) => {
                    updateUserPickerSearch(event.target.value);
                  }}
                  onFocus={() => setUserPickerOpen(true)}
                  placeholder="Search email, name, or id"
                />
                <button className="searchable-select-toggle" type="button" aria-label="Toggle user list" onMouseDown={(event) => event.preventDefault()} onClick={() => setUserPickerOpen((current) => !current)} />
                {userPickerOpen && (
                  <div className="searchable-select-menu" role="listbox">
                    <div className="searchable-select-menu-search">
                      <input
                        autoFocus
                        type="search"
                        value={userDropdownSearchValue}
                        onChange={(event) => updateUserPickerSearch(event.target.value)}
                        placeholder="Search users..."
                      />
                    </div>
                    {filteredPreviewUsers.slice(0, 30).map((user) => (
                      <button
                        className={String(user.id) === accessPreviewUserId ? "selected" : undefined}
                        key={user.id ?? user.email}
                        type="button"
                        role="option"
                        aria-selected={String(user.id) === accessPreviewUserId}
                        onMouseDown={(event) => {
                          event.preventDefault();
                          selectQuotaUser(user);
                        }}
                      >
                        <strong>{userOptionLabel(user)}</strong>
                        <span>{user.name ?? user.role ?? "STANDARD"}</span>
                      </button>
                    ))}
                    {!filteredPreviewUsers.length && <div className="searchable-select-empty">No user found</div>}
                  </div>
                )}
              </div>
            </label>
          </div>
          <div className="access-selection-overview">
            <div><span>Account</span><strong>{selectedPreviewUser?.email ?? "No user selected"}</strong><small>{selectedPreviewUser?.name ?? selectedPreviewUser?.role ?? "-"}</small></div>
            <div><span>Plan</span><strong>{accessPreview?.planType ?? accessPreview?.plan ?? "-"}</strong><small>{accessPreview?.activeEntitlement ? "Active entitlement" : "No active entitlement"}</small></div>
            <div><span>AI remaining</span><strong>{formatValue(accessPreview?.aiRemainingThisPeriod)}</strong><small>{formatValue(accessPreview?.aiMonthlyQuota)} base + {formatValue(accessPreview?.aiAddonQuota)} add-on</small></div>
          </div>
          </div>
          <div className="subscription-note access-preview-note access-policy-callout">
            <strong>Shows the effective access returned by backend policy.</strong>
            <span>If feature matrix and this preview differ, the active entitlement snapshot or subscription state is controlling the user.</span>
          </div>
        </Panel>

        <Panel className="access-assignment-panel" title={tx("Admin assignment", "Admin ataması")} description={tx("Prepare a controlled subscription snapshot change for the selected user.", "Seçilen kullanıcı için kontrollü abonelik anlık görüntü değişikliği hazırlayın.")} actions={<button className="ghost-button compact" type="button" aria-expanded={accessAssignmentOpen} onClick={() => setAccessAssignmentOpen((current) => !current)}>{accessAssignmentOpen ? tx("Close −", "Kapat −") : tx("Open +", "Aç +")}</button>}>
          {accessAssignmentOpen && <div className="access-assignment-content">
          <div className="admin-subscription-grid user-access-assignment-grid">
            <label>
              Plan
              <select value={subscriptionForm.planType} onChange={(event) => updateSubscriptionForm("planType", event.target.value)}>
                {PLAN_ORDER.map((plan) => <option key={plan} value={plan}>{plan}</option>)}
              </select>
            </label>
            <label>
              Status
              <select value={subscriptionForm.status} onChange={(event) => updateSubscriptionForm("status", event.target.value)}>
                {SUBSCRIPTION_STATUS_OPTIONS.map((status) => <option key={status} value={status}>{status}</option>)}
              </select>
            </label>
            <label>
              Billing period
              <select value={subscriptionForm.billingPeriod} onChange={(event) => updateSubscriptionForm("billingPeriod", event.target.value)}>
                {BILLING_PERIOD_OPTIONS.map((period) => <option key={period} value={period}>{period}</option>)}
              </select>
            </label>
            <label>
              Monthly AI quota
              <input min="0" type="number" value={subscriptionForm.aiMonthlyQuota} onChange={(event) => updateSubscriptionForm("aiMonthlyQuota", event.target.value)} />
            </label>
            <label>
              Used this period
              <input min="0" type="number" value={subscriptionForm.aiUsedThisPeriod} onChange={(event) => updateSubscriptionForm("aiUsedThisPeriod", event.target.value)} />
            </label>
            <label>
              Start date
              <DatePickerButton label="Start date" value={subscriptionForm.startDate} onChange={(value) => updateSubscriptionForm("startDate", value)} />
            </label>
            <label>
              End date
              <DatePickerButton label="End date" min={subscriptionEndDateMinimum(subscriptionForm)} value={subscriptionForm.endDate} onChange={(value) => updateSubscriptionForm("endDate", value)} />
            </label>
            <label className="checkbox-field">
              <input checked={subscriptionForm.autoRenew} onChange={(event) => updateSubscriptionForm("autoRenew", event.target.checked)} type="checkbox" />
              Auto renew
            </label>
          </div>
          <div className="access-assignment-footer">
          <div className="subscription-note access-preview-note">
            <strong>Manual admin changes create an entitlement snapshot for this user.</strong>
            <span>Use this for local testing, support corrections, beta access, or manually granting PLUS/PRO before store integration is complete.</span>
          </div>
          <div className="admin-subscription-actions">
            <button className="primary-button" disabled={!selectedUserId || subscriptionActionState === "loading"} onClick={applySubscriptionUpdate} type="button">Apply subscription</button>
            <button className="ghost-button" disabled={!selectedUserId || subscriptionActionState === "loading"} onClick={resetSelectedAiQuota} type="button">Reset used quota</button>
            <button className="ghost-button matrix-apply-button" disabled={!selectedUserId || subscriptionActionState === "loading"} onClick={() => setMatrixApplyConfirmationOpen(true)} type="button">Apply matrix now</button>
          </div>
          </div>
          </div>}
        </Panel>
        </div>
        <Panel className="resolved-access-panel" title={tx("Resolved user access", "Çözümlenmiş kullanıcı erişimi")} description={tx("Backend-calculated rights after plan, snapshot and quota rules are applied.", "Plan, anlık görüntü ve kota kuralları uygulandıktan sonra backend tarafından hesaplanan haklar.")}>
          <div className="quota-summary-strip access-summary-strip">
            <div>
              <span>{locale === "tr" ? "İzin verilen" : "Allowed features"}</span>
              <strong>{allowedAccessCount}</strong>
              <small>{locale === "tr" ? "Etkin erişim hakkı" : "Effective feature rights"}</small>
            </div>
            <div>
              <span>{locale === "tr" ? "Engellenen" : "Blocked features"}</span>
              <strong>{accessFeatureUniverse.length - allowedAccessCount}</strong>
              <small>{locale === "tr" ? "Mevcut snapshot tarafından kapalı" : "Unavailable in current snapshot"}</small>
            </div>
            <div>
              <span>{locale === "tr" ? "Snapshot sonucu" : "Snapshot result"}</span>
              <strong>{accessPreview?.activeEntitlement ? (locale === "tr" ? "Aktif" : "Active") : (locale === "tr" ? "Pasif" : "Inactive")}</strong>
              <small>{locale === "tr" ? `Ön izleme: ${accessPreviewState}` : `Preview: ${accessPreviewState}`}</small>
            </div>
            <div>
              <span>{locale === "tr" ? "Kullanılan AI kotası" : "AI quota used"}</span>
              <strong>{formatValue(Math.max(0, (accessPreview?.aiMonthlyQuota ?? 0) + (accessPreview?.aiAddonQuota ?? 0) - (accessPreview?.aiRemainingThisPeriod ?? 0)))}</strong>
              <small>{locale === "tr" ? "Bu abonelik döneminde" : "In the current subscription period"}</small>
            </div>
          </div>
          <div className="access-feature-toolbar">
            <label>
              <span>{locale === "tr" ? "Özellik ara" : "Search features"}</span>
              <input type="search" value={accessFeatureQuery} onChange={(event) => setAccessFeatureQuery(event.target.value)} placeholder={locale === "tr" ? "Özellik adı veya açıklama" : "Feature name or description"} />
            </label>
            <label>
              <span>{locale === "tr" ? "Erişim durumu" : "Access state"}</span>
              <select value={accessFeatureState} onChange={(event) => setAccessFeatureState(event.target.value as "ALL" | "ALLOWED" | "BLOCKED")}>
                <option value="ALL">{locale === "tr" ? "Tümü" : "All"}</option>
                <option value="ALLOWED">{locale === "tr" ? "İzin verilen" : "Allowed"}</option>
                <option value="BLOCKED">{locale === "tr" ? "Engellenen" : "Blocked"}</option>
              </select>
            </label>
            <strong>{resolvedAccessFeatures.length} / {matrixFeatures.length || FEATURE_ORDER.length}</strong>
          </div>
          <div className="access-feature-grid">
            {resolvedAccessFeatures.map((feature) => {
              const enabled = accessFeatureValue(accessPreview, feature);
              return (
                <div className={enabled ? "access-feature-card enabled" : "access-feature-card"} key={feature}>
                  <div>
                    <strong>{humanizeFeature(feature)}</strong>
                    <small>{featureDescription(feature)}</small>
                  </div>
                  <Badge value={enabled ? "Allowed" : "Blocked"} tone={enabled ? "good" : "neutral"} />
                </div>
              );
            })}
            {!resolvedAccessFeatures.length && <EmptyState message={locale === "tr" ? "Bu filtrelerle eşleşen erişim özelliği yok." : "No access feature matches these filters."} />}
          </div>
        </Panel>
      </div>}
      {(mode === "mapping" || mode === "aiQuotas") && <div className={mode === "aiQuotas" ? "subscription-ops-grid ai-quota-layout quota-command-center" : "subscription-ops-grid mapping-entitlement-workspace"}>
        {mode === "mapping" && (
        <Panel className="mapping-health-panel" title={tx("RevenueCat configuration", "RevenueCat yapılandırması")} description={tx("Store connection and production readiness checks.", "Mağaza bağlantısı ve canlı ortam hazırlık kontrolleri.")}>
          <div className="config-check-list">
            <ConfigCheck label="Webhook auth" value={Boolean(revenueCat?.webhookAuthorizationConfigured)} />
            <ConfigCheck label="Strict mapping" value={Boolean(revenueCat?.strictProductMapping)} />
            <ConfigCheck label="Production ready" value={Boolean(revenueCat?.productionReady)} />
            <ConfigCheck label="Metrics API enabled" value={Boolean(revenueCat?.apiEnabled)} />
            <ConfigCheck label="Metrics API secret" value={Boolean(revenueCat?.apiSecretConfigured)} />
            <ConfigCheck label="Project id" value={Boolean(revenueCat?.apiProjectConfigured)} />
          </div>
          <div className="subscription-note">
            <strong>{revenueCat?.productionReady ? "Store bridge is ready." : "Store bridge needs configuration."}</strong>
            <span>Status: {revenueCatState} | Metrics currency: {formatValue(revenueCat?.apiCurrency)}</span>
          </div>
        </Panel>
        )}
        {mode === "mapping" && <Panel className="mapping-plans-panel" title={tx("Products and entitlement mapping", "Ürün ve hak eşleştirmesi")} description={tx("Store product ids and the entitlement keys they activate are reviewed together.", "Mağaza ürün kimlikleri ve etkinleştirdikleri hak anahtarları birlikte incelenir.")}>
          <div className="product-mapping-board">
            <RevenueCatPlanMappingCard
              plan="PLUS"
              productIds={revenueCat?.plusProductIds}
              entitlements={revenueCat?.plusEntitlements}
            />
            <RevenueCatPlanMappingCard
              plan="PRO"
              productIds={revenueCat?.proProductIds}
              entitlements={revenueCat?.proEntitlements}
            />
          </div>
        </Panel>}
        {mode === "mapping" && <Panel className="mapping-policy-panel" title={tx("Entitlement snapshot policy", "Hak anlık görüntü politikası")} description={tx("How purchased rights change across the active billing period.", "Satın alınan hakların aktif faturalama dönemi boyunca nasıl değiştiği.")}>
          <div className="entitlement-policy-flow">
            <article><strong>1</strong><div><span>Purchase or renewal</span><small>The current plan rights are resolved from the approved feature matrix.</small></div></article>
            <article><strong>2</strong><div><span>Snapshot created</span><small>The resolved rights are stored for the active billing period.</small></div></article>
            <article><strong>3</strong><div><span>Period protected</span><small>Existing users keep granted rights while the current period remains active.</small></div></article>
            <article><strong>4</strong><div><span>Next renewal</span><small>The latest approved feature matrix becomes the new snapshot.</small></div></article>
          </div>
          <div className="subscription-note mapping-policy-note"><strong>Downgrades require clear user communication.</strong><span>Notify the user before features are removed in the next billing cycle.</span></div>
        </Panel>}
        {mode === "aiQuotas" && <>
          <Panel className="quota-user-control" title={tx("User plan and AI quota control", "Kullanıcı planı ve AI kota kontrolü")} description={tx("Select one account, review its current allowance, then submit a controlled subscription or quota change.", "Bir hesap seçin, mevcut hakkını inceleyin ve kontrollü abonelik veya kota değişikliği gönderin.")}>
            <div className="admin-subscription-grid">
              <label className="user-search-field">
                User
                <div className="searchable-select">
                  <input
                    aria-expanded={userPickerOpen}
                    aria-haspopup="listbox"
                    role="combobox"
                    value={userSearchQuery}
                    onChange={(event) => {
                      updateUserPickerSearch(event.target.value);
                    }}
                    onFocus={() => setUserPickerOpen(true)}
                    placeholder="Search email, name, or id"
                  />
                  <button className="searchable-select-toggle" type="button" aria-label="Toggle user list" onMouseDown={(event) => event.preventDefault()} onClick={() => setUserPickerOpen((current) => !current)} />
                  {userPickerOpen && (
                    <div className="searchable-select-menu" role="listbox">
                      <div className="searchable-select-menu-search">
                        <input
                          autoFocus
                          type="search"
                          value={userDropdownSearchValue}
                          onChange={(event) => updateUserPickerSearch(event.target.value)}
                          placeholder="Search users..."
                        />
                      </div>
                      {filteredPreviewUsers.slice(0, 30).map((user) => (
                        <button
                          className={String(user.id) === accessPreviewUserId ? "selected" : undefined}
                          key={user.id ?? user.email}
                          type="button"
                          role="option"
                          aria-selected={String(user.id) === accessPreviewUserId}
                          onMouseDown={(event) => {
                            event.preventDefault();
                            selectQuotaUser(user);
                          }}
                        >
                          <strong>{userOptionLabel(user)}</strong>
                          <span>{user.name ?? user.role ?? "STANDARD"}</span>
                        </button>
                      ))}
                      {!filteredPreviewUsers.length && <div className="searchable-select-empty">No user found</div>}
                    </div>
                  )}
                </div>
              </label>
              <label>
                Plan
                <select value={subscriptionForm.planType} onChange={(event) => updateSubscriptionForm("planType", event.target.value)}>
                  {PLAN_ORDER.map((plan) => <option key={plan} value={plan}>{plan}</option>)}
                </select>
              </label>
              <label>
                Status
                <select value={subscriptionForm.status} onChange={(event) => updateSubscriptionForm("status", event.target.value)}>
                  {SUBSCRIPTION_STATUS_OPTIONS.map((status) => <option key={status} value={status}>{status}</option>)}
                </select>
              </label>
              <label>
                Billing period
                <select value={subscriptionForm.billingPeriod} onChange={(event) => updateSubscriptionForm("billingPeriod", event.target.value)}>
                  {BILLING_PERIOD_OPTIONS.map((period) => <option key={period} value={period}>{period}</option>)}
                </select>
              </label>
              <label>
                Monthly AI quota
                <input min="0" type="number" value={subscriptionForm.aiMonthlyQuota} onChange={(event) => updateSubscriptionForm("aiMonthlyQuota", event.target.value)} />
              </label>
              <label>
                Used this period
                <input min="0" type="number" value={subscriptionForm.aiUsedThisPeriod} onChange={(event) => updateSubscriptionForm("aiUsedThisPeriod", event.target.value)} />
              </label>
              <label>
                Start date
                <DatePickerButton label="Start date" value={subscriptionForm.startDate} onChange={(value) => updateSubscriptionForm("startDate", value)} />
              </label>
              <label>
                End date
                <DatePickerButton label="End date" min={subscriptionEndDateMinimum(subscriptionForm)} value={subscriptionForm.endDate} onChange={(value) => updateSubscriptionForm("endDate", value)} />
              </label>
              <label>
                Provider
                <select value={subscriptionForm.provider} onChange={(event) => updateSubscriptionForm("provider", event.target.value)}>
                  {PAYMENT_PROVIDER_OPTIONS.map((provider) => <option key={provider} value={provider}>{provider}</option>)}
                </select>
              </label>
              <label className="full-width-field">
                Provider subscription id
                <input value={subscriptionForm.providerSubscriptionId} onChange={(event) => updateSubscriptionForm("providerSubscriptionId", event.target.value)} placeholder="Manual admin change, RevenueCat id, store transaction id" />
              </label>
              <label className="checkbox-field">
                <input checked={subscriptionForm.autoRenew} onChange={(event) => updateSubscriptionForm("autoRenew", event.target.checked)} type="checkbox" />
                Auto renew
              </label>
            </div>
            <div className="admin-subscription-actions">
              <button className="primary-button" disabled={!selectedUserId || subscriptionActionState === "loading"} onClick={applySubscriptionUpdate} type="button">Apply subscription</button>
              <button className="ghost-button" disabled={!selectedUserId || subscriptionActionState === "loading"} onClick={resetSelectedAiQuota} type="button">Reset used quota</button>
            </div>
            <div className="quota-summary-strip">
              <div>
                <span>Selected user</span>
                <strong title={selectedPreviewUser?.email ?? ""}>{selectedPreviewUser?.email ?? "-"}</strong>
                <small>{selectedPreviewUser?.role ?? "No user selected"}</small>
              </div>
              <div>
                <span>Resolved plan</span>
                <strong>{accessPreview?.planType ?? accessPreview?.plan ?? "-"}</strong>
                <small>Access preview: {accessPreviewState}</small>
              </div>
              <div>
                <span>AI remaining</span>
                <strong>{formatValue(accessPreview?.aiRemainingThisPeriod)}</strong>
                <small>{formatValue(accessPreview?.aiMonthlyQuota)} base + {formatValue(accessPreview?.aiAddonQuota)} add-on</small>
              </div>
              <div>
                <span>Last admin result</span>
                <strong>{subscriptionResult?.planType ?? subscriptionResult?.plan ?? "-"}</strong>
                <small>Action state: {subscriptionActionState}</small>
              </div>
            </div>
          </Panel>

          <Panel className="quota-grant-panel" title={tx("Grant one-off add-on quota", "Tek seferlik ek kota ver")} description={tx("Create a time-limited credit grant for the selected account.", "Seçilen hesap için süreli kredi hakkı oluşturun.")}>
            <div className="admin-subscription-grid addon-grant-grid">
              <label>
                Credits
                <input min="1" type="number" value={addonForm.amount} onChange={(event) => updateAddonForm("amount", event.target.value)} />
              </label>
              <label>
                Validity days
                <input min="1" type="number" value={addonForm.validityDays} onChange={(event) => updateAddonForm("validityDays", event.target.value)} />
              </label>
              <label className="full-width-field">
                Note
                <input value={addonForm.note} onChange={(event) => updateAddonForm("note", event.target.value)} placeholder="Why this credit was granted" />
              </label>
            </div>
            <div className="admin-subscription-actions">
              <button className="primary-button" disabled={!selectedUserId || subscriptionActionState === "loading"} onClick={grantSelectedAddonQuota} type="button">Grant add-on quota</button>
            </div>
          </Panel>

          <AiCreditCostPolicyPanel policies={pricingPolicies} savingKey={savingKey} onSave={updatePricingPolicy} />

          <Panel className="quota-product-panel" title={tx("AI add-on product mapping", "AI ek paket ürün eşleştirmesi")} description={tx("RevenueCat products and the credits they grant.", "RevenueCat ürünleri ve sağladıkları krediler.")}>
            <div className="addon-quota-list">
              {Object.entries(revenueCat?.aiAddonQuotas ?? {}).map(([name, value]) => (
                <div key={name}>
                  <div>
                    <span>Product id</span>
                    <strong>{name}</strong>
                  </div>
                  <div>
                    <span>Credits</span>
                    <strong>{formatValue(value)}</strong>
                  </div>
                  <div>
                    <span>Validity</span>
                    <strong>{formatValue(revenueCat?.aiAddonValidityDays?.[name] ?? revenueCat?.defaultAiAddonValidityDays)} days</strong>
                  </div>
                </div>
              ))}
              {!Object.keys(revenueCat?.aiAddonQuotas ?? {}).length && <span className="muted-text">No add-on quota mapping configured.</span>}
            </div>
            <div className="config-block">
              <span>Default validity</span>
              <strong>{formatValue(revenueCat?.defaultAiAddonValidityDays)} days</strong>
            </div>
          </Panel>
          <Panel className="quota-history-panel" title={tx("Recent subscription and quota changes", "Son abonelik ve kota değişiklikleri")} description={tx("Audit history for subscription, reset, matrix and add-on actions.", "Abonelik, sıfırlama, matris ve ek paket işlemlerinin denetim geçmişi.")}>
            <DataTable
              columns={["Action", "Target user", "Change", "Admin", "Created"]}
              rows={quotaAudits.map((audit) => {
                const targetUserId = audit.targetKey ?? audit.targetId;
                const targetUser = previewUsers.find((user) => String(user.id) === String(targetUserId));
                const changeLines = subscriptionAuditChangeLines(audit);
                return [
                  <Badge value={humanizeSubscriptionAuditAction(audit.actionType)} tone={audit.actionType === "AI_QUOTA_ADDON_GRANT" ? "good" : "neutral"} />,
                  <div className="entity-cell">
                    <strong>{targetUser?.name ?? targetUser?.email ?? "Unknown user"}</strong>
                    {targetUser?.name && targetUser.email && <small>{targetUser.email}</small>}
                  </div>,
                  <div className="table-stack subscription-change-cell">
                    {changeLines.map((line, index) => <span key={audit.id + "-" + index}>{line}</span>)}
                  </div>,
                  audit.adminEmail ?? "-",
                  formatDate(audit.createdAt)
                ];
              })}
              empty="No subscription or quota admin changes returned yet."
            />
            <PaginationControls
              page={subscriptionAudits?.page ?? auditPage}
              pageSize={subscriptionAudits?.size ?? auditPageSize}
              totalElements={subscriptionAudits?.totalElements ?? 0}
              totalPages={subscriptionAudits?.totalPages ?? 0}
              first={subscriptionAudits?.first ?? auditPage === 0}
              last={subscriptionAudits?.last ?? true}
              onPageChange={setAuditPage}
              onPageSizeChange={(size) => { setAuditPageSize(size); setAuditPage(0); }}
            />
          </Panel>
        </>}
      </div>}

      {mode === "entitlements" && (
        <Panel title="Entitlement snapshot policy">
          <div className="roadmap-strip">
            <span>Purchased plan rights are snapshotted per billing period</span>
            <span>Current users keep granted rights until renewal</span>
            <span>Next renewal reads the latest feature matrix</span>
            <span>Plan downgrade/removal should notify user before next cycle</span>
          </div>
        </Panel>
      )}

      {(mode === "overview" || mode === "mapping") && Boolean(revenueCat?.missingRequiredConfig?.length || revenueCat?.warnings?.length) && (
        <Panel title="Configuration warnings">
          <div className="warning-list">
            {(revenueCat?.missingRequiredConfig ?? []).map((item) => <span key={`missing-${item}`}>Missing: {item}</span>)}
            {(revenueCat?.warnings ?? []).map((item) => <span key={`warning-${item}`}>{item}</span>)}
          </div>
        </Panel>
      )}
      {matrixApplyConfirmationOpen && <ConfirmDialog
        title="Apply current feature matrix?"
        message={`This replaces the active entitlement snapshot for ${selectedPreviewUser?.email ?? selectedPreviewUser?.name ?? `user #${selectedUserId}`}. Features removed from the matrix may become unavailable immediately. The action will be recorded in audit history.`}
        confirmLabel="Apply matrix"
        danger
        busy={subscriptionActionState === "loading"}
        onCancel={() => setMatrixApplyConfirmationOpen(false)}
        onConfirm={applyCurrentFeatureMatrixNow}
      />}    </div>
  );
}

export function AiCreditCostPolicyPanel({ policies, savingKey, onSave }: {
  policies: AiCreditPricingPolicy[];
  savingKey: string | null;
  onSave: (policy: AiCreditPricingPolicy, draft: AiCreditPricingPolicy) => Promise<void>;
}) {
  const [drafts, setDrafts] = useState<Record<string, AiCreditPricingPolicy>>({});

  useEffect(() => {
    const next: Record<string, AiCreditPricingPolicy> = {};
    for (const policy of policies) next[policy.feature] = { ...policy };
    setDrafts(next);
  }, [policies]);

  function updateField(feature: string, field: keyof AiCreditPricingPolicy, value: string) {
    setDrafts((current) => ({ ...current, [feature]: { ...current[feature], [field]: Number(value) } }));
  }

  function estimate(policy: AiCreditPricingPolicy, units: number, includeContext = false) {
    const extraUnits = Math.max(0, units - policy.includedUnits);
    const extraCredits = extraUnits === 0 ? 0 : Math.ceil(extraUnits / policy.unitsPerAdditionalCredit);
    return Math.min(policy.maxCreditCost, policy.baseCreditCost + extraCredits + (includeContext ? policy.contextSurcharge : 0));
  }

  return (
    <details className="panel ai-credit-policy-panel">
      <summary className="ai-credit-policy-summary">
        <div>
          <h3>AI credit cost policy</h3>
          <span>Global PLUS/PRO credit formulas</span>
        </div>
      </summary>
      <div className="ai-credit-policy-content">
      <div className="ai-credit-policy-intro">
        <div>
          <strong>One global tariff for every paid plan</strong>
          <span>PLUS and PRO consume the same credits for the same request. Nutrition and workout costs grow only with request complexity.</span>
        </div>
        <Badge value="Backend controlled" tone="good" />
      </div>
      <div className="ai-credit-policy-grid">
        <div className="ai-credit-policy-head">
          <span>AI feature</span><span>Pricing formula</span><span>Cost preview</span><span>Action</span>
        </div>
        {CONTROLLED_AI_FEATURES.map((definition) => {
          const policy = policies.find((candidate) => candidate.feature === definition.feature);
          if (!policy) return null;
          const draft = drafts[policy.feature] ?? policy;
          const changed = JSON.stringify(draft) !== JSON.stringify(policy);
          const isNutrition = policy.pricingMode === "NUTRITION_COMPLEXITY";
          const isWorkout = policy.pricingMode === "WORKOUT_COMPLEXITY";
          return <div className="ai-credit-policy-row" key={definition.feature}>
            <div className="ai-credit-policy-feature">
              <strong>{definition.label}</strong><small>{definition.detail}</small>
              <Badge value={policy.pricingMode.replaceAll("_", " ")} />
            </div>
            <div className={`ai-credit-policy-fields ${policy.pricingMode === "FIXED" ? "is-fixed" : "is-dynamic"}`}>
              <label>Base<input min="1" max="50" type="number" value={draft.baseCreditCost} onChange={(event) => updateField(policy.feature, "baseCreditCost", event.target.value)} /></label>
              {(isNutrition || isWorkout) && <>
                <label>Included {isNutrition ? "meal slots" : "minutes"}<input min="0" type="number" value={draft.includedUnits} onChange={(event) => updateField(policy.feature, "includedUnits", event.target.value)} /></label>
                <label>{isNutrition ? "Slots" : "Minutes"} / extra credit<input min="1" type="number" value={draft.unitsPerAdditionalCredit} onChange={(event) => updateField(policy.feature, "unitsPerAdditionalCredit", event.target.value)} /></label>
                {isNutrition && <label>Workout surcharge<input min="0" max="50" type="number" value={draft.contextSurcharge} onChange={(event) => updateField(policy.feature, "contextSurcharge", event.target.value)} /></label>}
                <label>Maximum<input min="1" max="50" type="number" value={draft.maxCreditCost} onChange={(event) => updateField(policy.feature, "maxCreditCost", event.target.value)} /></label>
              </>}
            </div>
            <div className="ai-credit-policy-preview">
              {isNutrition ? <><span>1 day x 2 meals <strong>{estimate(draft, 2)} credit</strong></span><span>3 days x 3 meals <strong>{estimate(draft, 9)} credits</strong></span><span>7 days x 6 meals <strong>{estimate(draft, 42)} credits</strong></span><span>+ workout context <strong>{estimate(draft, 42, true)} credits</strong></span></> : isWorkout ? <><span>1 day x 10 min <strong>{estimate(draft, 10)} credit</strong></span><span>3 days x 45 min <strong>{estimate(draft, 135)} credits</strong></span><span>6 days x 75 min <strong>{estimate(draft, 450)} credits</strong></span></> : <span>Each successful request <strong>{draft.baseCreditCost} credit{draft.baseCreditCost === 1 ? "" : "s"}</strong></span>}
            </div>
            <button className={changed ? "primary-button" : "ghost-button"} disabled={!changed || savingKey === "pricing:" + policy.feature} onClick={() => void onSave(policy, draft)} type="button">{savingKey === "pricing:" + policy.feature ? "Saving" : "Save"}</button>
          </div>;
        })}
      </div>
      </div>
    </details>
  );
}

export function ConfigCheck({ label, value }: { label: string; value: boolean }) {
  return (
    <div className={value ? "config-check good" : "config-check warn"}>
      <span>{label}</span>
      <strong>{value ? "OK" : "Check"}</strong>
    </div>
  );
}

export function RevenueCatPlanMappingCard({
  plan,
  productIds,
  entitlements
}: {
  plan: "PLUS" | "PRO";
  productIds?: string[];
  entitlements?: string[];
}) {
  const hasProducts = Boolean(productIds?.length);
  const hasEntitlements = Boolean(entitlements?.length);
  const mappingState = hasProducts && hasEntitlements ? "Mapped" : hasProducts || hasEntitlements ? "Partial" : "Missing";
  return (
    <article className={`mapping-card ${plan.toLowerCase()}`}>
      <header>
        <span>{plan}</span>
        <Badge value={mappingState} tone={mappingState === "Mapped" ? "good" : "warn"} />
      </header>
      <div className="mapping-section">
        <strong>Product IDs</strong>
        <PillList values={productIds} empty="No product id configured" />
      </div>
      <div className="mapping-section">
        <strong>Entitlements</strong>
        <PillList values={entitlements} empty="No entitlement configured" />
      </div>
    </article>
  );
}

export function PillList({ values, empty }: { values?: string[]; empty: string }) {
  if (!values?.length) {
    return <span className="muted-text">{empty}</span>;
  }
  return (
    <div className="pill-list">
      {values.map((value) => <span key={value}>{value}</span>)}
    </div>
  );
}

export function parseSubscriptionAuditValue(value?: string | null): SubscriptionDto {
  if (!value) return {};
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === "object" ? parsed as SubscriptionDto : {};
  } catch {
    return {};
  }
}

export function humanizeSubscriptionAuditAction(action?: string): string {
  switch (action) {
    case "SUBSCRIPTION_UPDATE": return "Subscription update";
    case "AI_QUOTA_RESET": return "AI quota reset";
    case "AI_QUOTA_ADDON_GRANT": return "AI quota grant";
    case "SUBSCRIPTION_ENTITLEMENT_MATRIX_APPLY": return "Matrix applied now";
    default: return action ? humanize(action.toLowerCase()) : "-";
  }
}

export function subscriptionAuditChangeLines(audit: AuditEntry): string[] {
  const before = parseSubscriptionAuditValue(audit.oldValue);
  const after = parseSubscriptionAuditValue(audit.newValue);
  const fromPlan = before.planType ?? before.plan ?? "-";
  const toPlan = after.planType ?? after.plan ?? "-";

  if (audit.actionType === "AI_QUOTA_ADDON_GRANT") {
    const granted = Math.max(0, (after.aiAddonQuota ?? 0) - (before.aiAddonQuota ?? 0));
    const lines = ["+" + formatValue(granted) + " AI credits granted"];
    if (before.aiAddonQuota !== after.aiAddonQuota) {
      lines.push("Add-on quota: " + formatValue(before.aiAddonQuota ?? 0) + " -> " + formatValue(after.aiAddonQuota ?? 0));
    }
    if (after.aiAddonQuotaExpiresAt) lines.push("Valid until: " + formatDate(after.aiAddonQuotaExpiresAt));
    return lines;
  }

  if (audit.actionType === "SUBSCRIPTION_ENTITLEMENT_MATRIX_APPLY") {
    return ["Current feature matrix applied to active user snapshot"];
  }

  if (audit.actionType === "AI_QUOTA_RESET") {
    const restored = Math.max(0, (before.aiUsedThisPeriod ?? 0) - (after.aiUsedThisPeriod ?? 0));
    return [
      formatValue(restored) + " AI credits restored",
      "Used quota: " + formatValue(before.aiUsedThisPeriod ?? 0) + " -> " + formatValue(after.aiUsedThisPeriod ?? 0)
    ];
  }

  const changes: string[] = [];
  if (fromPlan !== toPlan) changes.push("Plan: " + fromPlan + " -> " + toPlan);
  if (before.status !== after.status) changes.push("Status: " + (before.status ?? "-") + " -> " + (after.status ?? "-"));
  if (before.billingPeriod !== after.billingPeriod) changes.push("Billing: " + (before.billingPeriod ?? "-") + " -> " + (after.billingPeriod ?? "-"));
  if (before.aiMonthlyQuota !== after.aiMonthlyQuota) changes.push("Monthly AI quota: " + formatValue(before.aiMonthlyQuota ?? 0) + " -> " + formatValue(after.aiMonthlyQuota ?? 0));
  if (before.aiUsedThisPeriod !== after.aiUsedThisPeriod) changes.push("Used AI quota: " + formatValue(before.aiUsedThisPeriod ?? 0) + " -> " + formatValue(after.aiUsedThisPeriod ?? 0));
  return changes.length ? changes : ["Subscription details updated"];
}

export function humanize(value: string): string {
  return value.replace(/([A-Z])/g, " $1").replace(/^./, (char) => char.toUpperCase());
}

export function accessFeatureValue(access: SubscriptionFeatureAccess | null, feature: string): boolean {
  if (!access) return false;
  switch (feature) {
    case "BARCODE_SCANNER":
      return Boolean(access.barcodeScanner);
    case "MANUAL_FOOD_LOGGING":
      return Boolean(access.manualFoodLogging);
    case "FOOD_DIARY":
      return Boolean(access.foodDiary);
    case "WEIGHT_PROGRESS":
      return Boolean(access.weightProgress);
    case "WATER_TRACKING":
      return Boolean(access.waterTracking);
    case "WORKOUT_LOGGING":
      return Boolean(access.workoutLogging);
    case "SAVED_MEAL_TEMPLATES":
      return Boolean(access.savedMealTemplates);
    case "RECIPE_BUILDER":
      return Boolean(access.recipeBuilder);
    case "PUBLIC_RECIPE_LIBRARY":
      return Boolean(access.publicRecipeLibrary);
    case "NEXT_MEAL_SUGGESTIONS":
      return Boolean(access.nextMealSuggestions);
    case "ADVANCED_MACRO_TARGETS":
      return Boolean(access.advancedMacroTargets);
    case "MICRONUTRIENT_DETAILS":
      return Boolean(access.micronutrientDetails);
    case "MICRONUTRIENT_ANALYTICS":
      return Boolean(access.micronutrientAnalytics);
    case "DATA_EXPORT":
      return Boolean(access.dataExport);
    case "FASTING_BASIC":
      return Boolean(access.fastingBasic);
    case "FASTING_ADVANCED":
      return Boolean(access.fastingAdvanced);
    case "AI_MEAL_DRAFTS":
      return Boolean(access.aiMealDrafts);
    case "AI_RECIPE_GENERATION":
      return Boolean(access.aiRecipeGeneration);
    case "AI_MEAL_PREPARATION_GUIDE":
      return Boolean(access.aiMealPreparationGuide);
    case "AI_NUTRITION_PLAN":
      return Boolean(access.aiNutritionPlan);
    case "AI_WORKOUT_PLANNER":
      return Boolean(access.aiWorkoutPlanner);
    case "AI_INSIGHTS":
      return Boolean(access.aiInsights);
    case "HEALTH_INTEGRATION":
      return Boolean(access.healthIntegration);
    case "ADVANCED_ANALYTICS":
      return Boolean(access.advancedAnalytics);
    case "CUSTOM_FOOD_LIBRARY":
      return Boolean(access.customFoodLibrary);
    case "AD_FREE":
      return Boolean(access.adFree);
    default:
      return false;
  }
}

export function uniqueFeatures(items: FeatureMatrixItem[]): string[] {
  return Array.from(new Set(items.map((item) => item.feature).filter(Boolean) as string[])).sort((a, b) => {
    const orderA = FEATURE_ORDER.indexOf(a);
    const orderB = FEATURE_ORDER.indexOf(b);
    if (orderA !== -1 || orderB !== -1) {
      return (orderA === -1 ? Number.MAX_SAFE_INTEGER : orderA) - (orderB === -1 ? Number.MAX_SAFE_INTEGER : orderB);
    }
    return a.localeCompare(b);
  });
}

export function featureKey(item: FeatureMatrixItem): string {
  return `${item.planType ?? "-"}:${item.feature ?? "-"}`;
}

export function featureDescription(value?: string): string {
  switch (value) {
    case "BARCODE_SCANNER":
      return "Barcode scanning is available in every plan.";
    case "MANUAL_FOOD_LOGGING":
      return "Log foods manually with serving and gram-based quantities.";
    case "FOOD_DIARY":
      return "Core daily meal diary and nutrition logging.";
    case "WEIGHT_PROGRESS":
      return "Weight history, targets, and progress tracking.";
    case "WATER_TRACKING":
      return "Daily water logging and progress tracking.";
    case "WORKOUT_LOGGING":
      return "Manual exercise and workout history tracking.";
    case "SAVED_MEAL_TEMPLATES":
      return "Save meals and reuse them in the food diary.";
    case "RECIPE_BUILDER":
      return "Create and manage user recipes.";
    case "PUBLIC_RECIPE_LIBRARY":
      return "Browse the curated public recipe catalogue.";
    case "NEXT_MEAL_SUGGESTIONS":
      return "Home-screen meal targets with matched recipe suggestions and AI recipe prefill.";
    case "ADVANCED_MACRO_TARGETS":
      return "Flexible macro targets and meal-level planning.";
    case "MICRONUTRIENT_DETAILS":
      return "Detailed vitamins and minerals in nutrition views.";
    case "MICRONUTRIENT_ANALYTICS":
      return "Advanced micronutrient trends, coverage, and period comparisons.";
    case "DATA_EXPORT":
      return "Export personal tracking data for portability.";
    case "FASTING_BASIC":
      return "Basic fasting timer and session history.";
    case "FASTING_ADVANCED":
      return "Advanced fasting schedules and analytics.";
    case "AI_MEAL_DRAFTS":
      return "Photo and voice meal draft generation. No diary entry is written before user confirmation.";
    case "AI_WORKOUT_PLANNER":
      return "AI workout plan drafts and confirmed plan snapshots. Exercise logs are not created automatically.";
    case "AI_RECIPE_GENERATION":
      return "AI-assisted recipe drafting with user review before saving.";
    case "AI_INSIGHTS":
      return "Daily and weekly coaching cards generated from app-owned context, not open-ended chat.";
    case "HEALTH_INTEGRATION":
      return "Health data integrations and advanced body metrics.";
    case "ADVANCED_ANALYTICS":
      return "Deeper trend, nutrition, and behavior analytics.";
    case "AD_FREE":
      return "Removes ad placements from the user experience.";
    case "CUSTOM_FOOD_LIBRARY":
      return "Allows users to manage their own food catalog records.";
    default:
      return "Feature entitlement managed by backend policy.";
  }
}

export function defaultAiQuota(planType?: string): number {
  if (planType === "PRO") return 150;
  if (planType === "PLUS") return 50;
  return 0;
}

export function parseNonNegativeInt(value: string): number {
  const parsed = Number.parseInt(value || "0", 10);
  if (!Number.isFinite(parsed) || parsed < 0) return 0;
  return parsed;
}

export function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10);
}

export function isPastIsoDate(value?: string): boolean {
  return Boolean(value && value < todayIsoDate());
}

export function subscriptionPeriodEndDate(startDate: string, billingPeriod: string): string {
  const [year, month, day] = startDate.split("-").map(Number);
  const monthOffset = billingPeriod === "YEARLY" ? 12 : 1;
  const targetMonthIndex = month - 1 + monthOffset;
  const targetYear = year + Math.floor(targetMonthIndex / 12);
  const targetMonth = targetMonthIndex % 12;
  const targetMonthLastDay = new Date(Date.UTC(targetYear, targetMonth + 1, 0)).getUTCDate();
  const result = new Date(Date.UTC(targetYear, targetMonth, Math.min(day, targetMonthLastDay)));
  result.setUTCDate(result.getUTCDate() - 1);
  return result.toISOString().slice(0, 10);
}

export function isActivePaidSubscriptionWithExpiredEndDate(form: { planType: string; status: string; endDate: string }): boolean {
  return form.planType !== "FREE"
    && (form.status === "ACTIVE" || form.status === "TRIALING")
    && isPastIsoDate(form.endDate);
}

export function subscriptionEndDateMinimum(form: { planType: string; status: string; startDate: string }): string | undefined {
  if (form.planType !== "FREE" && (form.status === "ACTIVE" || form.status === "TRIALING")) {
    return form.startDate > todayIsoDate() ? form.startDate : todayIsoDate();
  }
  return form.startDate || undefined;
}
