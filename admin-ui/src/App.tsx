import { FormEvent, MouseEvent, lazy, useEffect, useRef, useState, Suspense } from "react";

import { createAdminSessionActivity } from "./adminSessionActivity";

const CatalogEvidenceWorkspace = lazy(() => import("./pages/CatalogEvidenceWorkspace").then(module => ({ default: module.CatalogEvidenceWorkspace })));

const NotificationDefinitionsView = lazy(() => import("./modules/notifications/NotificationDefinitionsView").then(module => ({ default: module.NotificationDefinitionsView })));

const MealReminderAutomationView = lazy(() => import("./modules/notifications/MealReminderAutomationView").then(module => ({ default: module.MealReminderAutomationView })));

const SubscriptionNotificationOperationsView = lazy(() => import("./modules/notifications/SubscriptionNotificationOperationsView").then(module => ({ default: module.SubscriptionNotificationOperationsView })));

const FreePromotionPolicyView = lazy(() => import("./modules/FreePromotionPolicyView").then(module => ({ default: module.FreePromotionPolicyView })));

import { LanguageSelector, localizeGroup, useAdminLocale } from "./admin/locale";
import { commonMessages } from "./admin/commonMessages";
import { PageBoundary, PageFailure } from "./admin/PageBoundary";
import { sectionPaths } from "./admin/navigation";

function followPageLink(event: MouseEvent<HTMLAnchorElement>, section: SectionKey, navigate: (section: SectionKey) => void) {
  if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
  event.preventDefault();
  navigate(section);
}

import { clearTokens, formatRequestError, login, logoutAdmin, restoreAdminSession, renewAdminSession, getAdminSessionTiming, request, saveTokens, subscribeUnauthorized, adminSessionId, subscribeAdminReauth, subscribeAdminReauthResult, resolveAdminReauth } from "./api";

import { AdminAccessProfile } from "./types";

import { LoadState } from "./AdminPrimitives";

import { SectionKey, SectionMeta, UserRouteContext, canViewSection, filterNavigationByAccess, isNavItemActive, navigation, replaceSectionPath, sectionFromLocation, sections, setSectionPath, tabsForSection } from "./admin/navigation";

import { AdminInvitation, AdminTargetContext, ConfirmDialog, humanizeFeature } from "./admin/shared";

const DashboardView = lazy(() => import("./pages/DashboardView").then(module => ({ default: module.DashboardView })));

const IntegrationsView = lazy(() => import("./pages/IntegrationsView").then(module => ({ default: module.IntegrationsView })));

const RevenueCatMonitoringView = lazy(() => import("./pages/RevenueCatMonitoringView").then(module => ({ default: module.RevenueCatMonitoringView })));

const MailOpsView = lazy(() => import("./pages/MailOpsView").then(module => ({ default: module.MailOpsView })));
const MailInboxView = lazy(() => import("./pages/MailInboxView").then(module => ({ default: module.MailInboxView })));


const DeliveryCenterView = lazy(() => import("./pages/DeliveryCenterView").then(module => ({ default: module.DeliveryCenterView })));

const FoodOpsView = lazy(() => import("./pages/FoodOpsView").then(module => ({ default: module.FoodOpsView })));

const CatalogOperationsView = lazy(() => import("./pages/CatalogOperationsView").then(module => ({ default: module.CatalogOperationsView })));

const ProductReviewView = lazy(() => import("./pages/ProductReviewView").then(module => ({ default: module.ProductReviewView })));

const RecipeAdminView = lazy(() => import("./pages/RecipeAdminView").then(module => ({ default: module.RecipeAdminView })));

const AchievementAdminView = lazy(() => import("./pages/AchievementAdminView").then(module => ({ default: module.AchievementAdminView })));

const UsersView = lazy(() => import("./pages/UsersView").then(module => ({ default: module.UsersView })));

const AdminSecurityView = lazy(() => import("./pages/AdminSecurityView").then(module => ({ default: module.AdminSecurityView })));
const ApprovalQueueView = lazy(() => import("./pages/ApprovalQueueView").then(module => ({ default: module.ApprovalQueueView })));

const SubscriptionsView = lazy(() => import("./pages/SubscriptionsView").then(module => ({ default: module.SubscriptionsView })));

const SubscriptionEventsView = lazy(() => import("./pages/SubscriptionEventsView").then(module => ({ default: module.SubscriptionEventsView })));

const PromotionsView = lazy(() => import("./pages/PromotionsView").then(module => ({ default: module.PromotionsView })));

const AiReviewView = lazy(() => import("./pages/AiReviewView").then(module => ({ default: module.AiReviewView })));

const AuditsView = lazy(() => import("./pages/AuditsView").then(module => ({ default: module.AuditsView })));

const RetentionPoliciesView = lazy(() => import("./pages/RetentionPoliciesView").then(module => ({ default: module.RetentionPoliciesView })));

const NotificationCampaignsView = lazy(() => import("./pages/NotificationCampaignsView").then(module => ({ default: module.NotificationCampaignsView })));

const NotificationsView = lazy(() => import("./pages/NotificationsView").then(module => ({ default: module.NotificationsView })));


const EngagementAnalyticsView = lazy(() => import("./pages/EngagementAnalyticsView").then(module => ({ default: module.EngagementAnalyticsView })));

const TrackingMonitoringView = lazy(() => import("./pages/TrackingMonitoringView").then(module => ({ default: module.TrackingMonitoringView })));

const SystemHealthView = lazy(() => import("./pages/SystemHealthView").then(module => ({ default: module.SystemHealthView })));
const OwnerErrorView = lazy(() => import("./pages/OwnerErrorView").then(module => ({ default: module.OwnerErrorView })));
const OwnerAlertsView = lazy(() => import("./pages/OwnerAlertsView").then(module => ({ default: module.OwnerAlertsView })));

const RuntimeOperationsView = lazy(() => import("./RuntimeOperationsView").then((module) => ({ default: module.RuntimeOperationsView })));

const TestFeedbackView = lazy(() => import("./TestFeedbackView").then((module) => ({ default: module.TestFeedbackView })));

type ThemeMode = "light" | "dark";

const THEME_KEY = "grun.admin.theme";

export default function App() {
  const { t, sectionText, locale } = useAdminLocale();
  const common = commonMessages[locale];
  const [pageSearch, setPageSearch] = useState("");
  const [accessRetry, setAccessRetry] = useState(0);
  const [authenticated, setAuthenticated] = useState(false);
  const [authRestoring, setAuthRestoring] = useState(true);
  const [active, setActive] = useState<SectionKey>(() => sectionFromLocation());
  const [error, setError] = useState<string | null>(null);
  const [authNotice, setAuthNotice] = useState<string | null>(null);
  const [theme, setTheme] = useState<ThemeMode>(() => readStoredTheme());
  const [openNavGroup, setOpenNavGroup] = useState<SectionKey | null>(null);
  const [mobileNavigationOpen, setMobileNavigationOpen] = useState(false);
  const [mobileViewport, setMobileViewport] = useState(() => window.matchMedia("(max-width: 720px)").matches);
  const [targetContext, setTargetContext] = useState<AdminTargetContext | null>(null);
  const [accessProfile, setAccessProfile] = useState<AdminAccessProfile | null>(null);
  const [sessionWarningOpen, setSessionWarningOpen] = useState(false);
  const [sessionWarningBusy, setSessionWarningBusy] = useState(false);
  const [sessionWarningSeconds, setSessionWarningSeconds] = useState(120);
  const [sessionAbsoluteWarning, setSessionAbsoluteWarning] = useState(false);
  const sessionActivityRef = useRef<ReturnType<typeof createAdminSessionActivity> | null>(null);
  const [reauthPurpose, setReauthPurpose] = useState<string | null>(null);
  const [reauthCode, setReauthCode] = useState("");
  const [reauthBusy, setReauthBusy] = useState(false);
  const [reauthError, setReauthError] = useState<string | null>(null);
  const mainRef = useRef<HTMLElement | null>(null);
  const navigationRef = useRef<HTMLElement | null>(null);
  const navigationTriggerRef = useRef<HTMLButtonElement | null>(null);

  function closeMobileNavigation() {
    setMobileNavigationOpen(false);
    // Wait for the main panel's inert state to be removed before restoring focus.
    window.requestAnimationFrame(() => navigationTriggerRef.current?.focus());
  }
  useEffect(() => {
    const media = window.matchMedia("(max-width: 720px)");
    const update = () => { setMobileViewport(media.matches); if (!media.matches) setMobileNavigationOpen(false); };
    media.addEventListener("change", update);
    return () => media.removeEventListener("change", update);
  }, []);

  function navigateToSection(section: SectionKey, userContext?: UserRouteContext) {
    setError(null);
    setTargetContext(null);
    setActive(section);
    setSectionPath(section, userContext);
    setMobileNavigationOpen(false);
    setPageSearch("");
  }

  function navigateToTarget(section: SectionKey, context?: Omit<AdminTargetContext, "section">) {
    setError(null);
    setTargetContext(context ? { ...context, section } : null);
    setActive(section);
    setSectionPath(section);
    setMobileNavigationOpen(false);
    setPageSearch("");
  }

  useEffect(() => {
    const syncFromLocation = () => {
      setActive(sectionFromLocation());
      replaceSectionPath(sectionFromLocation());
      setTargetContext(null);
      setError(null);
      setMobileNavigationOpen(false);
    };
    syncFromLocation();
    window.addEventListener("hashchange", syncFromLocation);
    window.addEventListener("popstate", syncFromLocation);
    return () => {
      window.removeEventListener("hashchange", syncFromLocation);
      window.removeEventListener("popstate", syncFromLocation);
    };
  }, []);

  useEffect(() => {
    if (!accessProfile) return;
    if (!canViewSection(accessProfile, active)) {
      const fallback = sections.find(section => canViewSection(accessProfile, section.key))?.key;
      if (fallback) {
        setTargetContext(null);
        setActive(fallback);
        replaceSectionPath(fallback);
      }
      return;
    }
    mainRef.current?.focus();
  }, [accessProfile, active]);
  useEffect(() => {
    setOpenNavGroup(navigation.find(item => isNavItemActive(item, active))?.key ?? null);
  }, [active]);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    window.localStorage.setItem(THEME_KEY, theme);
  }, [theme]);

  useEffect(() => {
    if (!mobileNavigationOpen) return;
    navigationRef.current?.querySelector<HTMLElement>("button")?.focus();
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") { event.preventDefault(); closeMobileNavigation(); }
      if (event.key === "Tab") {
        const focusable = Array.from(navigationRef.current?.querySelectorAll<HTMLElement>("button, input, select, a[href]") ?? []).filter(element => !element.hasAttribute("disabled") && element.getClientRects().length);
        const first = focusable[0], last = focusable[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
      }
    };
    document.body.classList.add("mobile-navigation-active");
    window.addEventListener("keydown", closeOnEscape);
    return () => {
      document.body.classList.remove("mobile-navigation-active");
      window.removeEventListener("keydown", closeOnEscape);
    };
  }, [mobileNavigationOpen]);

  useEffect(() => {
    if (!authenticated) {
      setAccessProfile(null);
      return;
    }
    let cancelled = false;
    setError(null);
    request<AdminAccessProfile>("/api/v1/admin/security/me")
      .then(profile => { if (!cancelled) setAccessProfile(profile); })
      .catch((failure) => { if (!cancelled) setError(formatRequestError(failure)); });
    return () => { cancelled = true; };
  }, [authenticated, accessRetry]);

  useEffect(() => {
    return subscribeUnauthorized(() => {
      clearTokens();
      setAuthenticated(false);
      setError(null);
      setAuthNotice("Session expired. Please sign in again.");
    });
  }, []);

  useEffect(() => {
    restoreAdminSession().then(setAuthenticated)
      .catch((failure) => setAuthNotice(`Session could not be restored. ${formatRequestError(failure)} Refresh to retry or sign in.`))
      .finally(() => setAuthRestoring(false));
  }, []);

  useEffect(() => subscribeAdminReauth((purpose) => {
    setReauthPurpose((current) => {
      if (current !== purpose) {
        setReauthCode("");
        setReauthError(null);
      }
      setReauthBusy(false);
      return purpose;
    });
  }), []);

  useEffect(() => subscribeAdminReauthResult((result) => {
    setReauthBusy(false);
    if (result.success) {
      setReauthPurpose(null);
      setReauthCode("");
      setReauthError(null);
    } else {
      setReauthError(result.message ?? "MFA verification failed. Check the code and try again.");
    }
  }), []);

  useEffect(() => {
    if (!authenticated) {
      setSessionWarningOpen(false);
      return;
    }
    const sessionId = adminSessionId();
    const activityChannel = "BroadcastChannel" in window ? new BroadcastChannel("grun-admin-human-activity") : null;
    const authChannel = "BroadcastChannel" in window ? new BroadcastChannel("grun-admin-auth") : null;
    let lastBroadcast = 0;
    const activity = createAdminSessionActivity({
      now: Date.now,
      readSession: getAdminSessionTiming,
      renew: (humanActivity) => humanActivity ? renewAdminSession() : restoreAdminSession(),
      onWarning: (visible, remainingMs, absolute) => {
        setSessionWarningOpen(visible);
        if (visible) setSessionWarningSeconds(Math.max(0, Math.ceil(remainingMs / 1000)));
        setSessionAbsoluteWarning(absolute);
      },
      onContinued: (at) => {
        if (sessionId) activityChannel?.postMessage({ sessionId, at });
      },
      onExpired: () => {
        setAuthNotice("Your admin session ended. Please sign in again.");
        void endAdminSession();
      },
      onFailure: (failure) => setError(`Session renewal could not be confirmed. ${formatRequestError(failure)}`)
    });
    sessionActivityRef.current = activity;
    const recordInput = (event: Event) => {
      if (!event.isTrusted || document.visibilityState !== "visible") return;
      const now = Date.now();
      if (activity.activity(now) && sessionId && now - lastBroadcast >= 1000) {
        lastBroadcast = now;
        activityChannel?.postMessage({ sessionId, at: now });
      }
    };
    if (activityChannel) activityChannel.onmessage = (event) => {
      if (sessionId && event.data?.sessionId === sessionId && typeof event.data.at === "number") {
        activity.activity(event.data.at, true);
      }
    };
    if (authChannel) authChannel.onmessage = (event) => {
      if (event.data?.type !== "logout") return;
      activity.stop();
      clearTokens(false);
      setAuthenticated(false);
      setAuthNotice("This admin session was signed out in another tab.");
    };
    const inputEvents = ["pointerdown", "pointermove", "keydown", "wheel", "touchstart"];
    inputEvents.forEach((name) => window.addEventListener(name, recordInput, { passive: true }));
    const checkDeadline = () => activity.tick();
    document.addEventListener("visibilitychange", checkDeadline);
    window.addEventListener("focus", checkDeadline);
    const timer = window.setInterval(checkDeadline, 1000);
    return () => {
      activity.stop();
      sessionActivityRef.current = null;
      window.clearInterval(timer);
      inputEvents.forEach((name) => window.removeEventListener(name, recordInput));
      document.removeEventListener("visibilitychange", checkDeadline);
      window.removeEventListener("focus", checkDeadline);
      activityChannel?.close();
      authChannel?.close();
    };
  }, [authenticated]);

  async function continueAdminSession() {
    setSessionWarningBusy(true);
    try { await sessionActivityRef.current?.continueSession(); }
    finally { setSessionWarningBusy(false); }
  }

  async function endAdminSession() {
    sessionActivityRef.current?.stop();
    setAuthenticated(false);
    setSessionWarningBusy(true);
    try { await logoutAdmin(); }
    catch { setAuthNotice("Signed out locally. The server could not confirm logout; close the browser if leaving this device."); }
    finally { setSessionWarningOpen(false); setSessionWarningBusy(false); }
  }

  function toggleTheme() {
    setTheme((current) => current === "dark" ? "light" : "dark");
  }

  const query = new URLSearchParams(window.location.search);
  const passwordResetToken = query.get("passwordResetToken");
  const invitationToken = query.get("token");
  if (passwordResetToken) return <AdminPasswordResetView token={passwordResetToken} theme={theme} toggleTheme={toggleTheme} />;
  if (invitationToken) return <AdminInvitationActivationView token={invitationToken} theme={theme} toggleTheme={toggleTheme} />;

  if (authRestoring) return <main className="login-page"><p>{t("restoring")}</p></main>;

  if (!authenticated) {
    return <LoginView onLogin={() => {
      setAuthNotice(null);
      setAuthenticated(true);
    }} notice={authNotice} theme={theme} toggleTheme={toggleTheme} />;
  }

  const activeMeta = sectionText(sections.find((section) => section.key === active) ?? sections[0]);
  const visibleNavigation = filterNavigationByAccess(navigation, accessProfile).map(item => localizeGroup(item, locale, sectionText));
  const searchResults = sections.filter(section => canViewSection(accessProfile, section.key)).filter(section => {
    const translated = sectionText(section);
    return `${translated.label} ${translated.hint} ${section.label} ${section.key}`.toLocaleLowerCase("tr").includes(pageSearch.trim().toLocaleLowerCase("tr"));
  }).map(sectionText);

  return (
    <div className="app-shell">
      <a className="skip-link" href="#admin-main" onClick={event => { event.preventDefault(); mainRef.current?.focus(); }}>{t("skip")}</a>
      <button
        className={mobileNavigationOpen ? "mobile-nav-backdrop visible" : "mobile-nav-backdrop"}
        onClick={closeMobileNavigation}
        type="button"
        aria-label={t("close")}
        tabIndex={mobileNavigationOpen ? 0 : -1}
      />
      <aside ref={navigationRef} id="admin-navigation" className={mobileNavigationOpen ? "sidebar mobile-open" : "sidebar"} aria-label={t("navigation")} inert={mobileViewport && !mobileNavigationOpen}>
        <div className="brand">
          <img className="brand-symbol" src="/admin-ui/grun/grun-app-icon.svg" alt="" aria-hidden="true" />
          <div className="brand-context">
            <img className="brand-wordmark" src="/admin-ui/grun/grun-wordmark.svg" alt="GRUN" />
            <span>{t("operations")}</span>
          </div>
          <button className="mobile-nav-close" onClick={closeMobileNavigation} type="button" aria-label={t("close")}>×</button>
        </div>
        <label className="admin-page-search"><span className="sr-only">{t("search")}</span><input type="search" placeholder={t("search")} aria-label={t("search")} value={pageSearch} onChange={event => setPageSearch(event.target.value)} /></label>
        <nav className="nav-list" aria-label={t("sections")}>
          {pageSearch.trim() ? <div className="nav-sublist">{searchResults.length ? searchResults.map(section => <a href={sectionPaths[section.key]} className={section.key === active ? "nav-subitem active" : "nav-subitem"} key={section.key} aria-current={section.key === active ? "page" : undefined} onClick={event => followPageLink(event, section.key, navigateToSection)}><span><strong>{section.label}</strong><small>{section.hint}</small></span></a>) : <p role="status">{t("noResults")}</p>}</div> : visibleNavigation.map((section) => (
            <div className="nav-group" key={`${section.key}-${section.label}`}>
              {!section.children ? <a href={sectionPaths[section.key]} className={section.key === active ? "nav-item active" : "nav-item"} aria-current={section.key === active ? "page" : undefined} onClick={event => followPageLink(event, section.key, navigateToSection)}><NavIcon section={section} /><span><strong>{section.label}</strong><small>{section.hint}</small></span></a> : <button
                className={isNavItemActive(section, active) ? "nav-item active" : "nav-item"}
                onClick={() => {
                  setError(null);
                  if (section.children) {
                    setOpenNavGroup((current) => current === section.key ? null : section.key);
                  } else {
                    navigateToSection(section.key);
                  }
                }}
                type="button"
                title={section.hint}
                aria-expanded={section.children ? openNavGroup === section.key : undefined}
                aria-current={!section.children && section.key === active ? "page" : undefined}
              >
                <NavIcon section={section} />
                <span>
                  <strong>{section.label}</strong>
                  <small>{section.hint}</small>
                </span>
                {section.children && <span className="nav-chevron">{openNavGroup === section.key ? "-" : "+"}</span>}
              </button>}
              {section.children && openNavGroup === section.key && (
                <div className="nav-sublist">
                  {section.children.map((child) => (
                    <a
                      className={child.key === active ? "nav-subitem active" : "nav-subitem"}
                      key={`${child.key}-${child.label}`}
                      href={sectionPaths[child.key]}
                      onClick={event => followPageLink(event, child.key, navigateToSection)}
                      title={child.hint}
                      aria-current={child.key === active ? "page" : undefined}
                    >
                      <span>
                        <strong>{child.label}</strong>
                        <small>{child.hint}</small>
                      </span>
                    </a>
                  ))}
                </div>
              )}
            </div>
          ))}
        </nav>
        <button
          className="logout-button"
          onClick={() => void endAdminSession()}
          type="button"
        >
          {t("signOut")}
        </button>
      </aside>

      <main className="main-panel" id="admin-main" ref={mainRef} tabIndex={-1} inert={mobileViewport && mobileNavigationOpen}>
        <header className="topbar">
          <div className="topbar-title">
            <button
              className="mobile-nav-trigger"
              ref={navigationTriggerRef}
              onClick={() => setMobileNavigationOpen(true)}
              type="button"
              aria-label={t("open")}
              aria-controls="admin-navigation"
              aria-expanded={mobileNavigationOpen}
            >
              <span aria-hidden="true">☰</span>
            </button>
            <div>
            <p className="eyebrow">{t("workspace")}</p>
            <h1>{activeMeta.label}</h1>
            </div>
          </div>
          <div className="topbar-actions">
            <LanguageSelector />
            <ThemeToggle theme={theme} toggleTheme={toggleTheme} />
            <span className="status-pill">{accessProfile?.role ? humanizeFeature(accessProfile.role) : t("access")}</span>
            <span className="status-pill">API v1</span>
          </div>
        </header>

        {error && <div className="error-banner" role="alert">{error}</div>}
        <SectionTabs active={active} onSelect={navigateToSection} accessProfile={accessProfile} />
        <section className="content-surface">
          {!accessProfile ? <div className="async-state" role="status">{error ? <button className="ghost-button" type="button" onClick={() => setAccessRetry(value => value + 1)}>{t("retry")}</button> : t("access")}</div> : !canViewSection(accessProfile, active) ? <p role="status">{t("unavailable")}</p> : <>
          <PageBoundary key={active} fallback={<PageFailure />}>
          <Suspense fallback={<div className="async-state" role="status" aria-live="polite">{t("loading")}</div>}>
          {active === "dashboard" && accessProfile && <DashboardView accessProfile={accessProfile} onError={setError} onNavigate={navigateToSection} />}
          {active === "integrations" && <IntegrationsView mode="overview" onError={setError} />}
          {active === "revenueCatProduction" && <RevenueCatMonitoringView environment="production" onError={setError} />}
          {active === "revenueCatSandbox" && <RevenueCatMonitoringView environment="sandbox" onError={setError} />}
          {active === "mail" && <MailOpsView onError={setError} targetContext={targetContext?.section === "mail" ? targetContext : null} onClearTarget={() => setTargetContext(null)} />}
          {active === "mailInbox" && <MailInboxView onError={setError} />}
          {active === "foodOps" && <FoodOpsView mode="overview" onError={setError} />}
          {active === "foodImports" && <FoodOpsView mode="overview" onError={setError} />}
          {active === "foodRegions" && <FoodOpsView mode="overview" onError={setError} />}
          {active === "foodQuality" && <FoodOpsView mode="quality" onError={setError} />}
          {active === "catalogExercises" && <CatalogOperationsView mode="exercises" onError={setError} />}
          {active === "products" && <ProductReviewView mode="queue" canManage={Boolean(accessProfile?.permissions?.includes("CATALOG_MANAGE"))} onError={setError} />}
          {active === "productContributions" && <CatalogEvidenceWorkspace accessProfile={accessProfile} onError={setError} targetContext={targetContext?.section === "productContributions" ? targetContext : null} onClearTarget={() => setTargetContext(null)} />}
          {active === "productDuplicates" && <CatalogEvidenceWorkspace initialTab="duplicates" accessProfile={accessProfile} onError={setError} />}
          {active === "productImages" && <ProductReviewView mode="images" canManage={Boolean(accessProfile?.permissions?.includes("CATALOG_MANAGE"))} onError={setError} />}
          {active === "productNutrition" && <ProductReviewView mode="nutrition" canManage={Boolean(accessProfile?.permissions?.includes("CATALOG_MANAGE"))} onError={setError} />}
          {active === "productRejected" && <ProductReviewView mode="rejected" canManage={Boolean(accessProfile?.permissions?.includes("CATALOG_MANAGE"))} onError={setError} />}
          {active === "recipes" && <RecipeAdminView onError={setError} />}
          {active === "achievements" && <AchievementAdminView onError={setError} />}
          {active === "users" && <UsersView mode="users" onError={setError} onNavigate={navigateToSection} />}
          {active === "admins" && <AdminSecurityView accessProfile={accessProfile} onError={setError} targetContext={targetContext?.section === "admins" ? targetContext : null} />}
          {active === "approvals" && <ApprovalQueueView accessProfile={accessProfile} onError={setError} targetContext={targetContext?.section === "approvals" ? targetContext : null} onClearTarget={() => setTargetContext(null)} />}
          {active === "subscriptions" && <SubscriptionsView mode="overview" onError={setError} accessProfile={accessProfile} />}
          {active === "subscriptionFeatures" && <SubscriptionsView mode="features" onError={setError} accessProfile={accessProfile} />}
          {active === "subscriptionMapping" && <SubscriptionsView mode="mapping" onError={setError} accessProfile={accessProfile} />}
          {active === "subscriptionEntitlements" && <SubscriptionsView mode="mapping" onError={setError} accessProfile={accessProfile} />}
          {active === "subscriptionAccess" && <SubscriptionsView mode="access" onError={setError} accessProfile={accessProfile} />}
          {active === "subscriptionAiQuotas" && <SubscriptionsView mode="aiQuotas" onError={setError} accessProfile={accessProfile} />}
          {active === "subscriptionEvents" && <SubscriptionEventsView onError={setError} targetContext={targetContext?.section === "subscriptionEvents" ? targetContext : null} onClearTarget={() => setTargetContext(null)} />}
          {active === "subscriptionNotifications" && <SubscriptionNotificationOperationsView onError={setError} accessProfile={accessProfile} />}
          {active === "promotions" && <PromotionsView onError={setError} />}
          {active === "ai" && <AiReviewView mode="overview" onError={setError} targetContext={targetContext?.section === "ai" ? targetContext : null} onClearTarget={() => setTargetContext(null)} accessProfile={accessProfile} />}
          {active === "aiRequests" && <AiReviewView mode="requests" onError={setError} targetContext={targetContext?.section === "aiRequests" ? targetContext : null} onClearTarget={() => setTargetContext(null)} accessProfile={accessProfile} />}
          {active === "aiPolicy" && <AiReviewView mode="policy" onError={setError} accessProfile={accessProfile} />}
          {active === "audits" && <AuditsView onError={setError} />}
          {active === "retentionPolicies" && <RetentionPoliciesView onError={setError} />}
          {active === "notificationDefinitions" && <NotificationDefinitionsView onError={setError} accessProfile={accessProfile} />}
          {active === "mealReminderAutomation" && <MealReminderAutomationView accessProfile={accessProfile} onError={setError} onNavigate={navigateToSection} />}
          {active === "notificationCampaigns" && <NotificationCampaignsView onError={setError} onNavigate={navigateToSection} accessProfile={accessProfile} />}
          {active === "notifications" && <NotificationsView accessProfile={accessProfile} onError={setError} onNavigate={navigateToTarget} />}
          {(active === "pushDelivery" || active === "brevoSenders" || active === "mailEvents") && <DeliveryCenterView onError={setError} />}
          {active === "engagement" && <EngagementAnalyticsView onError={setError} />}
          {active === "freePromotion" && <FreePromotionPolicyView accessProfile={accessProfile} onError={setError} />}
          {active === "testFeedback" && <TestFeedbackView onError={setError} />}
          {active === "tracking" && <TrackingMonitoringView mode="overview" onError={setError} />}
          {active === "system" && <SystemHealthView mode="overview" onError={setError} />}
          {active === "systemProduction" && <RuntimeOperationsView onError={setError} accessProfile={accessProfile} />}
          {active === "errors" && <OwnerErrorView targetContext={targetContext?.section === "errors" ? targetContext : null} onClearTarget={() => setTargetContext(null)} />}
          {active === "ownerAlerts" && <OwnerAlertsView onError={setError} targetContext={targetContext?.section === "ownerAlerts" ? targetContext : null} onClearTarget={() => setTargetContext(null)} />}
          </Suspense>
          </PageBoundary>
          </>}
        </section>
      </main>
      {reauthPurpose && <div className="modal-backdrop confirm-backdrop" role="presentation"><section className="confirm-dialog reauth-dialog" role="dialog" aria-modal="true" aria-label="Fresh owner MFA required"><div className="confirm-dialog-content"><div className="confirm-dialog-icon neutral" aria-hidden="true">M</div><div className="confirm-dialog-copy"><p className="eyebrow">Sensitive owner action</p><h2>Fresh MFA verification</h2><p>Confirm <strong>{humanizeFeature(reauthPurpose)}</strong> with your authenticator or a recovery code.</p><label>Authenticator or recovery code<input autoFocus value={reauthCode} onChange={(event)=>{setReauthCode(event.target.value);setReauthError(null);}} maxLength={32} autoComplete="one-time-code" /></label>{reauthError && <div className="modal-error" role="alert"><strong>Verification failed</strong><span>{reauthError}</span></div>}</div></div><div className="modal-actions"><button className="ghost-button" disabled={reauthBusy} type="button" onClick={()=>{resolveAdminReauth(null);setReauthPurpose(null);setReauthCode("");setReauthError(null);}}>Cancel</button><button className="primary-button" disabled={reauthBusy || !reauthCode.trim()} type="button" onClick={()=>{setReauthBusy(true);setReauthError(null);resolveAdminReauth(reauthCode.trim());}}>{reauthBusy ? "Verifying..." : "Verify and continue"}</button></div></section></div>}
      {sessionWarningOpen && <ConfirmDialog title={common.sessionTitle} message={sessionAbsoluteWarning ? common.absoluteWarning(sessionWarningSeconds) : common.idleWarning(sessionWarningSeconds)} confirmLabel={sessionAbsoluteWarning ? common.signInAgain : common.continueSession} cancelLabel={common.signOut} busyLabel={common.continuing} dismissOnBackdrop={false} busy={sessionWarningBusy} onCancel={() => void endAdminSession()} onConfirm={() => sessionAbsoluteWarning ? void endAdminSession() : void continueAdminSession()} />}
    </div>
  );
}

function SectionTabs({ active, onSelect, accessProfile }: { active: SectionKey; onSelect: (section: SectionKey) => void; accessProfile: AdminAccessProfile | null }) {
  const { sectionText, t } = useAdminLocale();
  const tabs = tabsForSection(active)?.filter((tab) => canViewSection(accessProfile, tab.key)).map(sectionText);
  if (!tabs || tabs.length <= 1) return null;

  return (
    <nav className="section-tabs" aria-label={t("sections")}>
      {tabs.map((tab) => (
        <a
          className={tab.key === active ? "active" : ""}
          key={tab.key}
          href={sectionPaths[tab.key]}
          onClick={event => followPageLink(event, tab.key, onSelect)}
          title={tab.hint}
          aria-current={tab.key === active ? "page" : undefined}
        >
          <strong>{tab.label}</strong>
          <span>{tab.hint}</span>
        </a>
      ))}
    </nav>
  );
}

function LoginView({
  onLogin,
  notice,
  theme,
  toggleTheme
}: {
  onLogin: () => void;
  notice: string | null;
  theme: ThemeMode;
  toggleTheme: () => void;
}) {
  const [email, setEmail] = useState("admin@grun.local");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [mfaCode, setMfaCode] = useState("");
  const [showMfa, setShowMfa] = useState(false);
  const [mfaError, setMfaError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [forgotMode, setForgotMode] = useState(false);
  const [resetNotice, setResetNotice] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (forgotMode) {
        const result = await request<{ message?: string }>("/api/v1/auth/password-reset/request", { method: "POST", auth: false, body: { email: email.trim() } });
        setResetNotice(result.message ?? "If the email exists, a password reset link has been sent.");
        return;
      }
      const response = await login(email, password);
      saveTokens(response);
      onLogin();
    } catch (err) {
      const message = formatRequestError(err);
      if (message.toLowerCase().includes("mfa") || message.toLowerCase().includes("authenticator")) {
        setMfaCode("");
        setMfaError(null);
        setShowMfa(true);
      } else {
        setError(message);
      }
    } finally {
      setBusy(false);
    }
  }

  async function submitMfa(event: FormEvent) {
    event.preventDefault();
    if (mfaCode.length !== 6) return;
    setBusy(true);
    setMfaError(null);
    try {
      const response = await login(email, password, mfaCode);
      saveTokens(response);
      onLogin();
    } catch (err) {
      setMfaError(formatRequestError(err));
    } finally {
      setBusy(false);
    }
  }

  function closeMfa() {
    if (busy) return;
    setShowMfa(false);
    setMfaCode("");
    setMfaError(null);
  }

  return (
    <main className="login-page">
      <div className="login-theme-action">
        <ThemeToggle theme={theme} toggleTheme={toggleTheme} />
      </div>
      <section className="login-hero">
        <div className="hero-copy">
          <div className="login-brand-lockup">
            <img src="/admin-ui/grun/grun-wordmark.svg" alt="GRUN" />
            <span>Operations</span>
          </div>
          <h1>Admin control center for the calorie tracking platform.</h1>
          <p>Monitor users, review food data, inspect AI requests, and manage subscription features from one focused workspace.</p>
        </div>
      </section>
      <form className="login-card" onSubmit={submit}>
        <div>
          <p className="eyebrow">Secure access</p>
          <h2>{forgotMode ? "Reset admin password" : "Admin login"}</h2>
          {forgotMode && <p className="muted-text">Enter your admin email. We will send a single-use reset link if the account exists.</p>}
        </div>
        <label>
          Email
          <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" autoComplete="username" />
        </label>
        {!forgotMode && <label>
          Password
          <input value={password} onChange={(event) => setPassword(event.target.value)} type="password" autoComplete="current-password" placeholder="Admin password" />
        </label>}
        {notice && !forgotMode && <div className="form-notice">{notice}</div>}
        {resetNotice && <div className="form-notice">{resetNotice}</div>}
        {error && <div className="form-error">{error}</div>}
        <button className="primary-button" disabled={busy || !email.trim() || (!forgotMode && !password)} type="submit">
          {busy ? "Working..." : forgotMode ? "Send reset link" : "Sign in"}
        </button>
        <button className="text-button" type="button" onClick={() => { setForgotMode((current) => !current); setError(null); setResetNotice(null); closeMfa(); }}>
          {forgotMode ? "Back to admin login" : "Forgot password?"}
        </button>
      </form>
      {showMfa && <div className="modal-backdrop confirm-backdrop login-mfa-backdrop" role="presentation">
        <form className="confirm-dialog login-mfa-dialog" onSubmit={submitMfa} role="dialog" aria-modal="true" aria-labelledby="login-mfa-title">
          <div className="confirm-dialog-content">
            <div className="confirm-dialog-icon neutral" aria-hidden="true">6</div>
            <div className="confirm-dialog-copy">
              <p className="eyebrow">Secure verification</p>
              <h2 id="login-mfa-title">Enter your 6-digit code</h2>
              <p>Open your authenticator app and enter the current code to complete sign-in.</p>
              <label>
                Authenticator code
                <input
                  autoFocus
                  inputMode="numeric"
                  pattern="[0-9]*"
                  autoComplete="one-time-code"
                  maxLength={6}
                  value={mfaCode}
                  onChange={(event) => setMfaCode(event.target.value.replace(/\D/g, "").slice(0, 6))}
                  placeholder="000000"
                  aria-invalid={Boolean(mfaError)}
                />
              </label>
              {mfaError && <div className="form-error" role="alert">{mfaError}</div>}
            </div>
          </div>
          <div className="modal-actions">
            <button className="ghost-button" disabled={busy} onClick={closeMfa} type="button">Cancel</button>
            <button className="primary-button" disabled={busy || mfaCode.length !== 6} type="submit">{busy ? "Verifying..." : "Verify and sign in"}</button>
          </div>
        </form>
      </div>}
    </main>
  );
}

function NavIcon({ compact = false, section }: { compact?: boolean; section: SectionMeta }) {
  if (section.key === "mail") {
    return <span className={compact ? "nav-icon brevo-logo compact" : "nav-icon brevo-logo"}>Brevo</span>;
  }
  if (section.logo) {
    const logoClassName = [
      "nav-icon",
      "logo-icon",
      compact ? "compact" : "",
      section.key === "revenueCatProduction" ? "revenuecat-logo" : ""
    ].filter(Boolean).join(" ");
    return (
      <span className={logoClassName}>
        <img
          alt=""
          src={section.logo}
          onError={(event) => {
            event.currentTarget.style.display = "none";
            event.currentTarget.parentElement?.classList.add("logo-missing");
          }}
        />
        <span className="nav-icon-fallback">{section.icon}</span>
      </span>
    );
  }
  return <span className={compact ? "nav-icon compact" : "nav-icon"}>{section.icon}</span>;
}

function ThemeToggle({ theme, toggleTheme }: { theme: ThemeMode; toggleTheme: () => void }) {
  const { t } = useAdminLocale();
  const isDark = theme === "dark";
  return (
    <button
      aria-label={isDark ? t("lightMode") : t("darkMode")}
      aria-pressed={isDark}
      className="theme-toggle"
      onClick={toggleTheme}
      type="button"
    >
      <span>{isDark ? t("dark") : t("light")}</span>
      <i />
    </button>
  );
}

function AdminPasswordResetView({ token, theme, toggleTheme }: { token: string; theme: ThemeMode; toggleTheme: () => void }) {
  const [form, setForm] = useState({ password: "", confirmPassword: "" });
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [complete, setComplete] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const passwordPattern = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z\d\s])\S{8,}$/;
    if (!token.trim()) { setMessage("This password reset link is incomplete. Request a new link."); return; }
    if (form.password !== form.confirmPassword) { setMessage("Passwords do not match."); return; }
    if (!passwordPattern.test(form.password)) {
      setMessage("Password must be at least 8 characters and include uppercase, lowercase, a number, and a special character. Spaces are not allowed.");
      return;
    }
    setBusy(true); setMessage(null);
    try {
      await request("/api/v1/auth/password-reset/confirm", { method: "POST", auth: false, body: { token, newPassword: form.password } });
      setComplete(true);
      setMessage("Password changed. All existing admin sessions have been signed out.");
      window.history.replaceState(null, "", window.location.pathname);
    } catch (error) { setMessage(formatRequestError(error)); }
    finally { setBusy(false); }
  }

  return <main className="login-page">
    <div className="login-theme-action"><ThemeToggle theme={theme} toggleTheme={toggleTheme} /></div>
    <section className="login-hero"><div className="hero-copy"><div className="login-brand-lockup"><img src="/admin-ui/grun/grun-wordmark.svg" alt="GRUN" /><span>Operations</span></div><h1>Recover secure admin access.</h1><p>The link is single-use. Completing this reset revokes every existing session for the admin account.</p></div></section>
    <form className="login-card" onSubmit={submit}>
      <div><p className="eyebrow">Account recovery</p><h2>Set a new password</h2></div>
      {!complete && <>
        <label>New password<input required type="password" minLength={8} autoComplete="new-password" value={form.password} onChange={(event) => setForm((current) => ({ ...current, password: event.target.value }))} /></label>
        <label>Confirm password<input required type="password" minLength={8} autoComplete="new-password" value={form.confirmPassword} onChange={(event) => setForm((current) => ({ ...current, confirmPassword: event.target.value }))} /></label>
        <small>Use at least 8 characters with uppercase, lowercase, a number, and a special character. Do not use spaces.</small>
        <button className="primary-button" disabled={busy || !form.password || !form.confirmPassword} type="submit">{busy ? "Changing password..." : "Change password"}</button>
      </>}
      {message && <div className={complete ? "form-notice" : "form-error"}>{message}</div>}
      {complete && <button className="primary-button" type="button" onClick={() => window.location.assign(window.location.pathname)}>Continue to admin login</button>}
    </form>
  </main>;
}

function AdminInvitationActivationView({ token, theme, toggleTheme }: { token: string; theme: ThemeMode; toggleTheme: () => void }) {
  const [invitation, setInvitation] = useState<AdminInvitation | null>(null);
  const [state, setState] = useState<LoadState>("loading");
  const [message, setMessage] = useState<string | null>(null);
  const [form, setForm] = useState({ name: "", password: "", confirmPassword: "" });
  useEffect(() => {
    request<AdminInvitation>("/api/v1/auth/admin-invitations/inspect?token=" + encodeURIComponent(token), { auth: false })
      .then((value) => { setInvitation(value); setState("ready"); })
      .catch((error) => { setMessage(formatRequestError(error)); setState("error"); });
  }, [token]);
  async function activate(event: FormEvent) {
    event.preventDefault();
    if (form.password !== form.confirmPassword) { setMessage("Passwords do not match."); return; }
    setState("loading"); setMessage(null);
    try {
      await request("/api/v1/auth/admin-invitations/accept", { method: "POST", auth: false, body: { token, name: form.name.trim(), password: form.password } });
      setState("ready"); setMessage("Admin account activated. You can now sign in.");
      window.history.replaceState(null, "", window.location.pathname);
      setInvitation((current) => current ? { ...current, status: "ACCEPTED" } : current);
    } catch (error) { setState("error"); setMessage(formatRequestError(error)); }
  }
  return <main className="login-page">
    <div className="login-theme-action"><button className="icon-button" type="button" onClick={toggleTheme}>{theme === "dark" ? "Light" : "Dark"}</button></div>
    <section className="login-hero"><div><div className="login-brand-lockup"><img src="/admin-ui/grun/grun-app-icon.svg" alt="" /><strong>GRUN Admin</strong></div><h1>Join the admin team</h1><p>Secure invitation activation. Your role is fixed by the owner and MFA enrollment follows after sign-in.</p></div></section>
    <form className="login-card" onSubmit={activate}>
      <div><span className="eyebrow">ADMIN INVITATION</span><h2>Activate account</h2><p>{invitation ? invitation.email + " - " + humanizeFeature(invitation.role) : "Validating your invitation..."}</p></div>
      {invitation?.status === "PENDING" && <>
        <label>Full name<input required minLength={2} maxLength={100} value={form.name} onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))} /></label>
        <label>Password<input required type="password" minLength={8} value={form.password} onChange={(event) => setForm((current) => ({ ...current, password: event.target.value }))} /></label>
        <label>Confirm password<input required type="password" minLength={8} value={form.confirmPassword} onChange={(event) => setForm((current) => ({ ...current, confirmPassword: event.target.value }))} /></label>
        <small>Use uppercase, lowercase, number, and a special character.</small>
        <button className="primary-button" disabled={state === "loading"} type="submit">{state === "loading" ? "Activating..." : "Activate admin account"}</button>
      </>}
      {message && <div className={invitation?.status === "ACCEPTED" ? "status-banner success" : "status-banner error"}>{message}</div>}
      {invitation?.status === "ACCEPTED" && <button className="primary-button" type="button" onClick={() => window.location.assign(window.location.pathname)}>Continue to admin login</button>}
    </form>
  </main>;
}

function readStoredTheme(): ThemeMode {
  const stored = window.localStorage.getItem(THEME_KEY);
  if (stored === "light" || stored === "dark") return stored;
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}
