import { AdminAccessProfile } from "../types";
import routePaths from "../../public/routes.json";

export type SectionKey =
  | "errors"
  | "dashboard"
  | "integrations"
  | "integrationProviders"
  | "revenueCatProduction"
  | "revenueCatSandbox"
  | "mail"
  | "mailInbox"
  | "brevoSenders"
  | "mailEvents"
  | "foodOps"
  | "foodImports"
  | "foodRegions"
  | "foodQuality"
  | "catalogExercises"
  | "products"
  | "productContributions"
  | "productDuplicates"
  | "productImages"
  | "productNutrition"
  | "productRejected"
  | "recipes"
  | "achievements"
  | "users"
  | "admins"
  | "approvals"
  | "subscriptions"
  | "subscriptionFeatures"
  | "subscriptionMapping"
  | "subscriptionEntitlements"
  | "subscriptionAccess"
  | "subscriptionAiQuotas"
  | "subscriptionEvents"
  | "subscriptionNotifications"
  | "promotions"
  | "freePromotion"
  | "ai"
  | "aiRequests"
  | "aiPolicy"
  | "audits"
  | "retentionPolicies"
  | "settings"
  | "notifications"
  | "notificationDefinitions"
  | "mealReminderAutomation"
  | "notificationCampaigns"
  | "pushDelivery"
  | "engagement"
  | "ownerAlerts"
  | "tracking"
  | "trackingWater"
  | "trackingFasting"
  | "trackingSteps"
  | "system"
  | "systemRuntime"
  | "systemDatabase"
  | "systemProviders"
  | "systemProduction"
  | "testFeedback";

export type SectionMeta = { key: SectionKey; label: string; hint: string; icon: string; logo?: string };

export type NavigationItem = SectionMeta & { children?: SectionMeta[] };
export type UserRouteContext = { userId: number; userEmail?: string };

export const sections: SectionMeta[] = [
  { key: "errors", label: "Error center", hint: "Backend request failures · Owner", icon: "!" },
  { key: "dashboard", label: "Dashboard", hint: "Operational overview", icon: "D" },
  { key: "integrations", label: "Integrations", hint: "Provider status", icon: "I" },
  { key: "integrationProviders", label: "Providers", hint: "External services", icon: "P" },
  { key: "revenueCatProduction", label: "Production", hint: "Live API metrics", icon: "P" },
  { key: "revenueCatSandbox", label: "Sandbox", hint: "Webhook test data", icon: "S" },
  { key: "mail", label: "Mail Ops", hint: "Brevo delivery", icon: "M" },
  { key: "mailInbox", label: "Mail Center", hint: "Connected inboxes", icon: "I" },
  { key: "brevoSenders", label: "Brevo Senders", hint: "From addresses", icon: "S" },
  { key: "mailEvents", label: "Mail Events", hint: "Delivery events", icon: "E" },
  { key: "foodOps", label: "Food Operations", hint: "Food, imports and regions", icon: "F" },
  { key: "foodImports", label: "Import Jobs", hint: "Bulk data flow", icon: "I" },
  { key: "foodRegions", label: "Regions", hint: "Market groups", icon: "R" },
  { key: "foodQuality", label: "Quality Rules", hint: "Catalog checks", icon: "Q" },
  { key: "catalogExercises", label: "Exercise Library", hint: "Technique and media", icon: "E" },
  { key: "products", label: "Product Review", hint: "Catalog quality", icon: "P" },
  { key: "productContributions", label: "Evidence & Duplicates", hint: "Contributions and identity decisions", icon: "L" },
  { key: "productDuplicates", label: "Canonical Duplicates", hint: "Generic identity decisions", icon: "D" },
  { key: "productImages", label: "Product Quality", hint: "Images, nutrition and rejected", icon: "Q" },
  { key: "productNutrition", label: "Nutrition Review", hint: "Macro quality", icon: "N" },
  { key: "productRejected", label: "Rejected Products", hint: "Review archive", icon: "R" },
  { key: "recipes", label: "Recipes", hint: "User recipes", icon: "C" },
  { key: "achievements", label: "Achievements", hint: "Badge rules", icon: "B" },
  { key: "users", label: "Users", hint: "Accounts", icon: "U" },
  { key: "admins", label: "Admins", hint: "Admin accounts", icon: "A" },
  { key: "approvals", label: "Owner Approvals", hint: "Critical financial and operational decisions", icon: "O" },
  { key: "subscriptions", label: "Subscriptions", hint: "Overview", icon: "S" },
  { key: "subscriptionFeatures", label: "Feature Matrix", hint: "Plan rules", icon: "F" },
  { key: "subscriptionMapping", label: "Products & Entitlements", hint: "Store mapping and snapshots", icon: "M" },
  { key: "subscriptionEntitlements", label: "Entitlements", hint: "Snapshot policy", icon: "E" },
  { key: "subscriptionAccess", label: "User Access", hint: "Resolved rights", icon: "U" },
  { key: "subscriptionAiQuotas", label: "AI Quotas", hint: "Credits", icon: "Q" },
  { key: "subscriptionEvents", label: "Provider Events", hint: "Webhook audit", icon: "E" },
  { key: "subscriptionNotifications", label: "Account Notifications", hint: "Lifecycle delivery controls", icon: "N" },
  { key: "promotions", label: "Promotions", hint: "Offers and conversion", icon: "%" },
  { key: "freePromotion", label: "Free Paywall", hint: "Frequency and rollout", icon: "P" },
  { key: "ai", label: "AI Ops", hint: "Requests/provider", icon: "A" },
  { key: "aiRequests", label: "AI Requests", hint: "OCR, review and refunds", icon: "R" },
  { key: "aiPolicy", label: "AI Policy", hint: "Provider, model and budgets", icon: "P" },
  { key: "settings", label: "Settings", hint: "App config", icon: "G" },
  { key: "audits", label: "Audit Logs", hint: "Admin actions", icon: "L" },
  { key: "testFeedback", label: "Test Feedback", hint: "Preview build reports", icon: "T" },
  { key: "retentionPolicies", label: "Retention Policies", hint: "Legal data rules", icon: "R" },
  { key: "notifications", label: "My Work Inbox", hint: "Role-based decisions and reviews", icon: "N" },
  { key: "notificationDefinitions", label: "Definitions", hint: "System notification policy", icon: "D" },
  { key: "mealReminderAutomation", label: "Meal Reminders", hint: "Schedule, preview and release", icon: "M" },
  { key: "notificationCampaigns", label: "Campaigns", hint: "Broadcast messages", icon: "C" },
  { key: "pushDelivery", label: "Delivery Center", hint: "Push, senders and mail events", icon: "D" },
  { key: "engagement", label: "Product Analytics", hint: "Funnels and adoption", icon: "P" },
  { key: "ownerAlerts", label: "Owner Alerts", hint: "Critical email delivery · Owner", icon: "!" },
  { key: "tracking", label: "Tracking", hint: "Usage analytics", icon: "T" },
  { key: "trackingWater", label: "Water", hint: "Hydration usage", icon: "W" },
  { key: "trackingFasting", label: "Fasting", hint: "Session usage", icon: "F" },
  { key: "trackingSteps", label: "Steps", hint: "Device activity", icon: "S" },
  { key: "system", label: "System Health", hint: "Runtime state", icon: "H" },
  { key: "systemRuntime", label: "Runtime", hint: "App process", icon: "R" },
  { key: "systemDatabase", label: "Database", hint: "Postgres/Flyway", icon: "D" },
  { key: "systemProviders", label: "Providers", hint: "External health", icon: "P" },
  { key: "systemProduction", label: "Production", hint: "Readiness", icon: "P" }
];

export const sectionByKey = sections.reduce((accumulator, section) => {
  accumulator[section.key] = section;
  return accumulator;
}, {} as Record<SectionKey, SectionMeta>);

export const sectionPaths: Record<SectionKey, string> = routePaths;

export function sectionFromLocation(): SectionKey {
  const candidate = window.location.hash.replace(/^#\/?/, "") as SectionKey;
  if (isSectionKey(candidate)) return candidate; // Compatibility with bookmarked V1 links.
  const pathname = window.location.pathname.replace(/\/$/, "");
  if (pathname === "/admin/catalog/sources") return "foodOps";
  if (pathname === sectionPaths.brevoSenders || pathname === sectionPaths.mailEvents) return "pushDelivery";
  if (pathname === sectionPaths.subscriptionEntitlements) return "subscriptionMapping";
  if (pathname === sectionPaths.trackingWater || pathname === sectionPaths.trackingFasting || pathname === sectionPaths.trackingSteps) return "tracking";
  if (pathname === sectionPaths.systemRuntime || pathname === sectionPaths.systemDatabase || pathname === sectionPaths.systemProviders) return "system";
  if (pathname === sectionPaths.integrationProviders) return "integrations";
  if (pathname === sectionPaths.settings) return "systemProduction";
  return sections.find(section => sectionPaths[section.key] === pathname)?.key ?? "dashboard";
}

export function sectionHref(section: SectionKey, context?: UserRouteContext) {
  const params = new URLSearchParams();
  if (context?.userId) params.set("userId", String(context.userId));
  if (context?.userEmail) params.set("userEmail", context.userEmail);
  const query = params.toString();
  return `${sectionPaths[section]}${query ? `?${query}` : ""}`;
}

export function readUserRouteContext(search = window.location.search): UserRouteContext | null {
  const params = new URLSearchParams(search);
  const userId = Number(params.get("userId"));
  if (!Number.isSafeInteger(userId) || userId <= 0) return null;
  return { userId, userEmail: params.get("userEmail")?.trim() || undefined };
}

export function setSectionPath(section: SectionKey, context?: UserRouteContext) {
  const next = sectionHref(section, context);
  if (`${window.location.pathname}${window.location.search}` !== next || window.location.hash) window.history.pushState(null, "", next);
}

export function replaceSectionPath(section: SectionKey) {
  window.history.replaceState(null, "", sectionPaths[section] + window.location.search);
}

export function navSection(key: SectionKey): SectionMeta {
  return sectionByKey[key];
}

export const navigation: NavigationItem[] = [
  navSection("dashboard"),
  navSection("errors"),
  navSection("notifications"),
    navSection("users"),
  { ...navSection("foodOps"), children: [navSection("foodOps"), navSection("products"), navSection("productContributions"), navSection("productImages"), navSection("recipes"), navSection("catalogExercises"), navSection("foodQuality")] },
  { ...navSection("subscriptions"), children: [navSection("subscriptions"), navSection("subscriptionFeatures"), navSection("subscriptionMapping"), navSection("subscriptionAccess"), navSection("subscriptionAiQuotas"), navSection("subscriptionEvents"), navSection("promotions"), navSection("freePromotion"), navSection("revenueCatProduction"), navSection("revenueCatSandbox")] },
  { ...navSection("notificationDefinitions"), children: [navSection("notificationDefinitions"), navSection("mealReminderAutomation"), navSection("notificationCampaigns"), navSection("subscriptionNotifications"), navSection("mailInbox"), navSection("mail"), navSection("pushDelivery"), navSection("achievements")] },
  { ...navSection("ai"), children: [navSection("ai"), navSection("aiRequests"), navSection("aiPolicy")] },
  { ...navSection("engagement"), children: [navSection("engagement"), navSection("ownerAlerts"), navSection("tracking")] },
  { ...navSection("system"), children: [navSection("system"), navSection("systemProduction"), navSection("integrations"), navSection("testFeedback")] },
  { ...navSection("admins"), children: [navSection("admins"), navSection("approvals"), navSection("audits"), navSection("retentionPolicies")] }
];

export const sectionTabGroups: SectionMeta[][] = [
  [navSection("foodOps"), navSection("foodQuality")],
  [navSection("products"), navSection("productContributions"), navSection("productImages")],
  [navSection("subscriptions"), navSection("subscriptionFeatures"), navSection("subscriptionMapping"), navSection("subscriptionAccess"), navSection("subscriptionAiQuotas")],
  [navSection("promotions"), navSection("freePromotion")],
  [navSection("revenueCatProduction"), navSection("revenueCatSandbox")],
  [navSection("notificationDefinitions"), navSection("mealReminderAutomation"), navSection("notificationCampaigns")],
  [navSection("mailInbox"), navSection("mail"), navSection("pushDelivery")],
  [navSection("ai"), navSection("aiRequests"), navSection("aiPolicy")],
  [navSection("engagement"), navSection("ownerAlerts"), navSection("tracking")],
  [navSection("system"), navSection("systemProduction"), navSection("integrations"), navSection("testFeedback")],
  [navSection("admins"), navSection("approvals"), navSection("audits"), navSection("retentionPolicies")]
];

export function tabsForSection(active: SectionKey): SectionMeta[] | undefined {
  return sectionTabGroups.find((group) => group.some((item) => item.key === active));
}

export function permissionForSection(section: SectionKey): string {
  if (section === "errors") return "TECHNICAL_READ";
  if (section === "admins" || section === "approvals") return "ADMIN_TEAM_READ";
  if (section === "users") return "USERS_READ";
  if (["foodOps", "foodImports", "foodRegions", "foodQuality", "catalogExercises", "products", "productContributions", "productDuplicates", "productImages", "productNutrition", "productRejected", "recipes", "achievements"].includes(section)) return "CATALOG_READ";
  if (["subscriptions", "subscriptionFeatures", "subscriptionMapping", "subscriptionEntitlements", "subscriptionAccess", "subscriptionAiQuotas", "subscriptionEvents", "subscriptionNotifications", "promotions", "revenueCatProduction", "revenueCatSandbox"].includes(section)) return "FINANCE_READ";
  if (section === "notifications") return "DASHBOARD_READ";
  if (["notificationDefinitions", "mealReminderAutomation", "notificationCampaigns", "engagement", "tracking", "trackingWater", "trackingFasting", "trackingSteps", "freePromotion", "testFeedback"].includes(section)) return "GROWTH_READ";
  if (section === "ownerAlerts") return "TECHNICAL_READ";
  if (section === "retentionPolicies") return "COMPLIANCE_READ";
  if (section === "audits") return "AUDIT_READ";
  if (["integrations", "integrationProviders", "mail", "mailInbox", "brevoSenders", "mailEvents", "pushDelivery", "ai", "aiRequests", "aiPolicy", "system", "systemRuntime", "systemDatabase", "systemProviders", "systemProduction", "settings"].includes(section)) return "TECHNICAL_READ";
  return "DASHBOARD_READ";
}

export function canViewSection(profile: AdminAccessProfile | null, section: SectionKey): boolean {
  if (!profile) return false;
  if (profile.mfaRequired && !profile.mfaEnabled) return section === "admins";
  if ((section === "errors" || section === "approvals" || section === "ownerAlerts") && profile.role !== "OWNER") return false;
  return Boolean(profile.permissions?.includes(permissionForSection(section)));
}

export function filterNavigationByAccess(items: NavigationItem[], profile: AdminAccessProfile | null): NavigationItem[] {
  return items
    .map((item) => ({ ...item, children: item.children?.filter((child) => canViewSection(profile, child.key)) }))
    .filter((item) => canViewSection(profile, item.key) || Boolean(item.children?.length));
}

export function isNavItemActive(section: NavigationItem, active: SectionKey): boolean {
  return section.key === active || Boolean(section.children?.some((child) => child.key === active));
}

export function isSectionKey(value?: string): value is SectionKey {
  return Boolean(value && Object.prototype.hasOwnProperty.call(sectionByKey, value));
}
