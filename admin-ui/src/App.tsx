import { CSSProperties, FormEvent, ReactNode, useEffect, useMemo, useState } from "react";
import {
  clearTokens,
  formatRequestError,
  getToken,
  login,
  PageResponse,
  request,
  requestBlob,
  requestFormData,
  saveTokens,
  subscribeUnauthorized
} from "./api";
import {
  AdminBrevoSender,
  AdminBrevoSenderList,
  AdminMailMonitoring,
  AdminPushMonitoring,
  AdminTrackingModuleSummary,
  AdminTrackingSummary,
  AdminProductQualityWorkbench,
  AdminTrackingTrendPoint,
  AdminProductQualityAiValidationResult,
  AdminAchievementDefinition,
  AdminAchievementMetrics,
  AiMealDraft,
  AiMonitoringSummary,
  AiCreditPricingPolicy,
  AiQuotaRefundResponse,
  AuditEntry,
  DashboardSummary,
  FeatureMatrixItem,
  FoodProduct,
  FoodCanonicalDuplicateGroup,
  FoodCanonicalDuplicateGroupPage,
  FoodSearchAlias,
  ProductQualityScanRun,
  ProductQualityScanRunDetail,
  ProductQualityScanRunPage,
  ProductQualitySuggestion,
  ProductQualitySuggestionPage,
  ProductQualitySuggestionScanResult,
  ProductQualityAiSettings,
  AdminRecipe,
  AdminRecipeImportCandidate,
  AdminRecipeImportResult,
  Notification,
  RevenueCatChart,
  RevenueCatConfigStatus,
  RevenueCatMonitoringCharts,
  RevenueCatMonitoringOverview,
  RetentionPolicy,
  SubscriptionDto,
  SubscriptionFeatureAccess,
  SubscriptionProviderEvent,
  SubscriptionProviderEventPage,
  SystemHealth,
  UserProfile
} from "./types";

type SectionKey =
  | "dashboard"
  | "integrations"
  | "integrationProviders"
  | "revenueCat"
  | "revenueCatProduction"
  | "revenueCatSandbox"
  | "mail"
  | "brevoSenders"
  | "mailEvents"
  | "foodOps"
  | "foodImports"
  | "foodRegions"
  | "foodQuality"
  | "products"
  | "productDuplicates"
  | "productImages"
  | "productNutrition"
  | "productRejected"
  | "recipes"
  | "achievements"
  | "users"
  | "admins"
  | "userVerification"
  | "subscriptions"
  | "subscriptionFeatures"
  | "subscriptionMapping"
  | "subscriptionEntitlements"
  | "subscriptionAccess"
  | "subscriptionAiQuotas"
  | "subscriptionEvents"
  | "ai"
  | "audits"
  | "retentionPolicies"
  | "settings"
  | "notifications"
  | "pushDelivery"
  | "tracking"
  | "trackingWater"
  | "trackingFasting"
  | "trackingSteps"
  | "system"
  | "systemRuntime"
  | "systemDatabase"
  | "systemProviders"
  | "systemProduction";

type LoadState = "idle" | "loading" | "ready" | "error";
type RevenueCatRange = "7d" | "28d" | "90d" | "custom";
type ThemeMode = "light" | "dark";
type SectionMeta = { key: SectionKey; label: string; hint: string; icon: string; logo?: string };

type AdminTargetContext = {
  section: SectionKey;
  source: "notification" | "dashboard";
  notificationId?: number;
  severity?: string;
  type?: string;
  message?: string;
  targetType?: string;
  targetId?: string;
  targetRoute?: string;
};
type NavigationItem = SectionMeta & { children?: SectionMeta[] };

const THEME_KEY = "grun.admin.theme";
const PLAN_ORDER = ["FREE", "PLUS", "PRO"];
const SUBSCRIPTION_STATUS_OPTIONS = ["ACTIVE", "TRIALING", "CANCELED", "EXPIRED", "REFUNDED"];
const BILLING_PERIOD_OPTIONS = ["NONE", "MONTHLY", "YEARLY"];
const PAYMENT_PROVIDER_OPTIONS = ["MANUAL_ADMIN", "REVENUECAT", "APPLE_APP_STORE", "GOOGLE_PLAY_STORE", "STRIPE"];
const FEATURE_ORDER = [
  "BARCODE_SCANNER",
  "MANUAL_FOOD_LOGGING",
  "FOOD_DIARY",
  "WEIGHT_PROGRESS",
  "WATER_TRACKING",
  "WORKOUT_LOGGING",
  "SAVED_MEAL_TEMPLATES",
  "RECIPE_BUILDER",
  "PUBLIC_RECIPE_LIBRARY",
  "ADVANCED_MACRO_TARGETS",
  "MICRONUTRIENT_DETAILS",
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
const AI_REQUEST_TYPES = [
  "VOICE_FOOD_LOG",
  "PHOTO_MEAL_LOG",
  "AI_RECIPE_GENERATION",
  "AI_MEAL_PREPARATION_GUIDE",
  "AI_NUTRITION_PLAN",
  "AI_WORKOUT_PLAN",
  "AI_DAILY_INSIGHT",
  "AI_WEEKLY_INSIGHT"
];
const AI_REQUEST_STATUSES = ["DRAFT_CREATED", "CONFIRMED", "REJECTED", "FAILED"];
const SUBSCRIPTION_EVENT_STATUSES = ["RECEIVED", "PROCESSED", "FAILED", "IGNORED"];
const PRODUCT_QUALITY_SUGGESTION_STATUSES = ["OPEN", "ACCEPTED", "REJECTED"];
const CONTROLLED_AI_FEATURES = [
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

const sections: SectionMeta[] = [
  { key: "dashboard", label: "Dashboard", hint: "Operational overview", icon: "D" },
  { key: "integrations", label: "Integrations", hint: "Provider status", icon: "I" },
  { key: "integrationProviders", label: "Providers", hint: "External services", icon: "P" },
  { key: "revenueCat", label: "RevenueCat", hint: "Subscription analytics", icon: "R", logo: "./revenuecat.svg" },
  { key: "revenueCatProduction", label: "Production", hint: "Live API metrics", icon: "P" },
  { key: "revenueCatSandbox", label: "Sandbox", hint: "Webhook test data", icon: "S" },
  { key: "mail", label: "Mail Ops", hint: "Brevo delivery", icon: "M" },
  { key: "brevoSenders", label: "Brevo Senders", hint: "From addresses", icon: "S" },
  { key: "mailEvents", label: "Mail Events", hint: "Delivery events", icon: "E" },
  { key: "foodOps", label: "Food Ops", hint: "Catalog pipeline", icon: "F" },
  { key: "foodImports", label: "Import Jobs", hint: "Bulk data flow", icon: "I" },
  { key: "foodRegions", label: "Regions", hint: "Market groups", icon: "R" },
  { key: "foodQuality", label: "Quality Rules", hint: "Catalog checks", icon: "Q" },
  { key: "products", label: "Product Review", hint: "Catalog quality", icon: "P" },
  { key: "productDuplicates", label: "Canonical Duplicates", hint: "Generic identity decisions", icon: "D" },
  { key: "productImages", label: "Image Review", hint: "Product media", icon: "I" },
  { key: "productNutrition", label: "Nutrition Review", hint: "Macro quality", icon: "N" },
  { key: "productRejected", label: "Rejected Products", hint: "Review archive", icon: "R" },
  { key: "recipes", label: "Recipes", hint: "User recipes", icon: "C" },
  { key: "achievements", label: "Achievements", hint: "Badge rules", icon: "B" },
  { key: "users", label: "Users", hint: "Accounts", icon: "U" },
  { key: "admins", label: "Admins", hint: "Admin accounts", icon: "A" },
  { key: "userVerification", label: "Verification", hint: "Email status", icon: "V" },
  { key: "subscriptions", label: "Subscriptions", hint: "Overview", icon: "S" },
  { key: "subscriptionFeatures", label: "Feature Matrix", hint: "Plan rules", icon: "F" },
  { key: "subscriptionMapping", label: "Product Mapping", hint: "Store ids", icon: "M" },
  { key: "subscriptionEntitlements", label: "Entitlements", hint: "Snapshot policy", icon: "E" },
  { key: "subscriptionAccess", label: "User Access", hint: "Resolved rights", icon: "U" },
  { key: "subscriptionAiQuotas", label: "AI Quotas", hint: "Credits", icon: "Q" },
  { key: "subscriptionEvents", label: "Provider Events", hint: "Webhook audit", icon: "E" },
  { key: "ai", label: "AI Ops", hint: "Requests/provider", icon: "A" },
  { key: "settings", label: "Settings", hint: "App config", icon: "G" },
  { key: "audits", label: "Audit Logs", hint: "Admin actions", icon: "L" },
  { key: "retentionPolicies", label: "Retention Policies", hint: "Legal data rules", icon: "R" },
  { key: "notifications", label: "Admin Inbox", hint: "Personal alerts", icon: "N" },
  { key: "pushDelivery", label: "Push Delivery", hint: "Device tokens", icon: "P" },
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

const sectionByKey = sections.reduce((accumulator, section) => {
  accumulator[section.key] = section;
  return accumulator;
}, {} as Record<SectionKey, SectionMeta>);

function navSection(key: SectionKey): SectionMeta {
  return sectionByKey[key];
}

const navigation: NavigationItem[] = [
  navSection("dashboard"),
  {
    ...navSection("users"),
    children: [
      { key: "users", label: "Users & access", hint: "Accounts and admin access", icon: "U" }
    ]
  },
  {
    ...navSection("foodOps"),
    children: [
      { key: "foodOps", label: "Catalog ops", hint: "Import and region health", icon: "F" },
      navSection("products"),
      navSection("recipes")
    ]
  },
  {
    ...navSection("subscriptions"),
    children: [
      { key: "subscriptions", label: "Plans & entitlements", hint: "Plan rules and user access", icon: "S" },
      navSection("subscriptionEvents")
    ]
  },
  navSection("ai"),
  {
    ...navSection("notifications"),
    children: [
      { key: "notifications", label: "Admin inbox", hint: "Operational alerts", icon: "N" },
      navSection("mail"),
      navSection("pushDelivery")
    ]
  },
  {
    ...navSection("integrations"),
    children: [
      { key: "integrations", label: "Provider board", hint: "External service status", icon: "I" },
      { key: "revenueCatProduction", label: "RevenueCat", hint: "Subscription provider monitoring", icon: "R", logo: "./revenuecat.svg" }
    ]
  },
  navSection("tracking"),
  {
    ...navSection("system"),
    children: [
      { key: "system", label: "System health", hint: "Runtime and readiness", icon: "H" },
      navSection("audits")
    ]
  },
  navSection("achievements"),
  navSection("settings")
];

const sectionTabGroups: SectionMeta[][] = [
  [navSection("users"), navSection("admins"), navSection("userVerification")],
  [navSection("foodOps"), navSection("foodImports"), navSection("foodRegions"), navSection("foodQuality")],
  [navSection("products"), navSection("productDuplicates"), navSection("productImages"), navSection("productNutrition"), navSection("productRejected")],
  [navSection("subscriptions"), navSection("subscriptionFeatures"), navSection("subscriptionMapping"), navSection("subscriptionEntitlements"), navSection("subscriptionAccess"), navSection("subscriptionAiQuotas"), navSection("subscriptionEvents")],
  [navSection("notifications"), navSection("mail"), navSection("brevoSenders"), navSection("mailEvents"), navSection("pushDelivery")],
  [navSection("integrations"), navSection("integrationProviders"), navSection("revenueCatProduction"), navSection("revenueCatSandbox")],
  [navSection("tracking"), navSection("trackingWater"), navSection("trackingFasting"), navSection("trackingSteps")],
  [navSection("system"), navSection("systemRuntime"), navSection("systemDatabase"), navSection("systemProviders"), navSection("systemProduction"), navSection("audits"), navSection("retentionPolicies")]
];

function tabsForSection(active: SectionKey): SectionMeta[] | undefined {
  return sectionTabGroups.find((group) => group.some((item) => item.key === active));
}
const MARKET_REGIONS = ["GLOBAL", "TR", "UK_IE", "EU"];
const VERIFICATION_STATUSES = ["RAW_IMPORTED", "NEEDS_REVIEW", "VERIFIED", "REJECTED"];
const IMAGE_STATUSES = ["RAW", "NEEDS_REVIEW", "APPROVED", "REJECTED"];
const IMAGE_SOURCES = ["OPEN_FOOD_FACTS", "ADMIN_UPLOAD", "USER_UPLOAD", "BRAND_OFFICIAL", "AI_GENERATED"];
const CATALOG_TYPES = ["BRANDED_PRODUCT", "GENERIC_INGREDIENT", "LOCAL_DISH", "USER_CUSTOM"];
const DATA_SOURCES = ["OPEN_FOOD_FACTS", "ADMIN_IMPORT", "USDA_FOODDATA", "USER_CUSTOM"];
const PREFERRED_LANGUAGES = ["EN", "TR"];
const FOOD_SEARCH_ALIAS_TYPES = ["ADMIN_MANUAL", "TRANSLATION", "SYNONYM", "ASCII_NORMALIZED", "COMMON_NAME"];
const MEAL_TYPES = ["BREAKFAST", "LUNCH", "DINNER", "SNACK"];
const PORTION_UNITS = ["GRAM", "MILLILITER", "TABLESPOON", "TEASPOON", "SLICE", "SERVING", "PIECE"];
const RECIPE_VISIBILITIES = ["PRIVATE", "PUBLIC_ADMIN", "COMMUNITY_PENDING"];
const RECIPE_IMPORT_STATUSES = ["PENDING", "APPROVED", "REJECTED", "FAILED"];
const ACHIEVEMENT_CATEGORIES = ["ONBOARDING", "FOOD", "EXERCISE", "FASTING", "PROGRESS", "WATER"];
const ACHIEVEMENT_TIERS = ["BRONZE", "SILVER", "GOLD"];
const RECIPE_CATEGORIES = [
  "VEGAN",
  "VEGETARIAN",
  "HIGH_PROTEIN",
  "LOW_CARB",
  "LOW_CALORIE",
  "HIGH_FIBER",
  "GLUTEN_FREE",
  "DAIRY_FREE",
  "BREAKFAST",
  "LUNCH",
  "DINNER",
  "SNACK",
  "VEGETABLES",
  "SOUP",
  "SALAD",
  "QUICK_MEAL",
  "MEAL_PREP",
  "TURKISH",
  "MEDITERRANEAN",
  "UK_IE"
];
const QUALITY_ISSUES = [
  "LOW_QUALITY",
  "MISSING_IMAGE",
  "MISSING_CALORIES",
  "SUSPICIOUS_CALORIES",
  "MISSING_MACROS",
  "SUSPICIOUS_MACROS",
  "MISSING_MICRONUTRIENTS",
  "MISSING_NUTRIENT_QUALITY_FIELDS",
  "SUSPICIOUS_NUTRIENT_QUALITY",
  "MISSING_SERVING_SIZE",
  "MISSING_REGION",
  "MISSING_BARCODE",
  "INVALID_BARCODE_FORMAT",
  "UNSUPPORTED_REGION"
];
const AUDIT_ACTION_TYPES = [
  "SUBSCRIPTION_FEATURE_UPDATE",
  "RECIPE_REVIEW_UPDATE",
  "SUBSCRIPTION_USER_PLAN_UPDATE",
  "SUBSCRIPTION_AI_QUOTA_RESET",
  "SUBSCRIPTION_AI_ADDON_GRANT",
  "FOOD_PRODUCT_REVIEW_UPDATE",
  "REVENUECAT_MAPPING_VALIDATION"
];
const AUDIT_TARGET_TYPES = [
  "SUBSCRIPTION_FEATURE",
  "USER_SUBSCRIPTION",
  "FOOD_PRODUCT",
  "RECIPE",
  "REVENUECAT_MAPPING"
];

type ProductReviewDraft = {
  productName: string;
  displayImageUrl: string;
  marketRegion: string;
  verificationStatus: string;
  imageStatus: string;
  imageSource: string;
  catalogType: string;
  calories: string;
  protein: string;
  carbs: string;
  fat: string;
  fiber: string;
  sugar: string;
  sodium: string;
  potassium: string;
  cholesterol: string;
  calcium: string;
  iron: string;
  magnesium: string;
  zinc: string;
  vitaminA: string;
  vitaminC: string;
  vitaminD: string;
  vitaminE: string;
  vitaminB12: string;
  saturatedFat: string;
  transFat: string;
  sugarAlcohol: string;
  servingSizeGrams: string;
  servingUnit: string;
};

type ProductReviewNumberField = Extract<keyof ProductReviewDraft,
  | "calories"
  | "protein"
  | "carbs"
  | "fat"
  | "fiber"
  | "sugar"
  | "sodium"
  | "potassium"
  | "cholesterol"
  | "calcium"
  | "iron"
  | "magnesium"
  | "zinc"
  | "vitaminA"
  | "vitaminC"
  | "vitaminD"
  | "vitaminE"
  | "vitaminB12"
  | "saturatedFat"
  | "transFat"
  | "sugarAlcohol"
  | "servingSizeGrams"
>;

const PRODUCT_MACRO_FIELDS: Array<{ key: ProductReviewNumberField; label: string; suffix: string }> = [
  { key: "calories", label: "Calories", suffix: "kcal" },
  { key: "protein", label: "Protein", suffix: "g" },
  { key: "carbs", label: "Carbs", suffix: "g" },
  { key: "fat", label: "Fat", suffix: "g" },
  { key: "fiber", label: "Fiber", suffix: "g" },
  { key: "sugar", label: "Sugar", suffix: "g" },
  { key: "saturatedFat", label: "Saturated fat", suffix: "g" },
  { key: "transFat", label: "Trans fat", suffix: "g" },
  { key: "sugarAlcohol", label: "Sugar alcohol", suffix: "g" }
];

const PRODUCT_MINERAL_FIELDS: Array<{ key: ProductReviewNumberField; label: string; suffix: string }> = [
  { key: "sodium", label: "Sodium", suffix: "mg" },
  { key: "potassium", label: "Potassium", suffix: "mg" },
  { key: "cholesterol", label: "Cholesterol", suffix: "mg" },
  { key: "calcium", label: "Calcium", suffix: "mg" },
  { key: "iron", label: "Iron", suffix: "mg" },
  { key: "magnesium", label: "Magnesium", suffix: "mg" },
  { key: "zinc", label: "Zinc", suffix: "mg" }
];

const PRODUCT_VITAMIN_FIELDS: Array<{ key: ProductReviewNumberField; label: string; suffix: string }> = [
  { key: "vitaminA", label: "Vitamin A", suffix: "ug" },
  { key: "vitaminC", label: "Vitamin C", suffix: "mg" },
  { key: "vitaminD", label: "Vitamin D", suffix: "ug" },
  { key: "vitaminE", label: "Vitamin E", suffix: "mg" },
  { key: "vitaminB12", label: "Vitamin B12", suffix: "ug" }
];

export default function App() {
  const [authenticated, setAuthenticated] = useState(Boolean(getToken()));
  const [active, setActive] = useState<SectionKey>("dashboard");
  const [error, setError] = useState<string | null>(null);
  const [authNotice, setAuthNotice] = useState<string | null>(null);
  const [theme, setTheme] = useState<ThemeMode>(() => readStoredTheme());
  const [openNavGroup, setOpenNavGroup] = useState<SectionKey | null>(null);
  const [targetContext, setTargetContext] = useState<AdminTargetContext | null>(null);

  function navigateToSection(section: SectionKey) {
    setError(null);
    setTargetContext(null);
    setActive(section);
  }

  function navigateToTarget(section: SectionKey, context?: Omit<AdminTargetContext, "section">) {
    setError(null);
    setTargetContext(context ? { ...context, section } : null);
    setActive(section);
  }

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    window.localStorage.setItem(THEME_KEY, theme);
  }, [theme]);

  useEffect(() => {
    return subscribeUnauthorized(() => {
      clearTokens();
      setAuthenticated(false);
      setError(null);
      setAuthNotice("Session expired. Please sign in again.");
    });
  }, []);

  function toggleTheme() {
    setTheme((current) => current === "dark" ? "light" : "dark");
  }

  if (!authenticated) {
    return <LoginView onLogin={() => {
      setAuthNotice(null);
      setAuthenticated(true);
    }} notice={authNotice} theme={theme} toggleTheme={toggleTheme} />;
  }

  const activeMeta = sections.find((section) => section.key === active) ?? sections[0];

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <img className="brand-symbol" src="./grun/grun-app-icon.svg" alt="" aria-hidden="true" />
          <div className="brand-context">
            <img className="brand-wordmark" src="./grun/grun-wordmark.svg" alt="GRUN" />
            <span>Operations</span>
          </div>
        </div>
        <nav className="nav-list">
          {navigation.map((section) => (
            <div className="nav-group" key={`${section.key}-${section.label}`}>
              <button
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
              >
                <NavIcon section={section} />
                <span>
                  <strong>{section.label}</strong>
                  <small>{section.hint}</small>
                </span>
                {section.children && <span className="nav-chevron">{openNavGroup === section.key ? "-" : "+"}</span>}
              </button>
              {section.children && openNavGroup === section.key && (
                <div className="nav-sublist">
                  {section.children.map((child) => (
                    <button
                      className={child.key === active && child.label !== section.label ? "nav-subitem active" : "nav-subitem"}
                      key={`${child.key}-${child.label}`}
                      onClick={() => {
                        setError(null);
                        navigateToSection(child.key);
                      }}
                      type="button"
                      title={child.hint}
                    >
                      <span>
                        <strong>{child.label}</strong>
                        <small>{child.hint}</small>
                      </span>
                    </button>
                  ))}
                </div>
              )}
            </div>
          ))}
        </nav>
        <button
          className="logout-button"
          onClick={() => {
            clearTokens();
            setAuthenticated(false);
          }}
          type="button"
        >
          Sign out
        </button>
      </aside>

      <main className="main-panel">
        <header className="topbar">
          <div>
            <p className="eyebrow">Admin workspace</p>
            <h1>{activeMeta.label}</h1>
          </div>
          <div className="topbar-actions">
            <ThemeToggle theme={theme} toggleTheme={toggleTheme} />
            <span className="status-pill">API v1</span>
            <span className="status-pill live">Live backend</span>
          </div>
        </header>

        {error && <div className="error-banner">{error}</div>}
        <SectionTabs active={active} onSelect={navigateToSection} />
        <section className="content-surface">
          {active === "dashboard" && <DashboardView onError={setError} onNavigate={navigateToSection} />}
          {active === "integrations" && <IntegrationsView mode="overview" onError={setError} />}
          {active === "integrationProviders" && <IntegrationsView mode="providers" onError={setError} />}
          {active === "revenueCatProduction" && <RevenueCatMonitoringView environment="production" onError={setError} />}
          {active === "revenueCatSandbox" && <RevenueCatMonitoringView environment="sandbox" onError={setError} />}
          {active === "mail" && <MailOpsView onError={setError} targetContext={targetContext?.section === "mail" ? targetContext : null} onClearTarget={() => setTargetContext(null)} />}
          {active === "brevoSenders" && <BrevoSendersView onError={setError} />}
          {active === "mailEvents" && <MailEventsView onError={setError} />}
          {active === "foodOps" && <FoodOpsView mode="overview" onError={setError} />}
          {active === "foodImports" && <FoodOpsView mode="imports" onError={setError} />}
          {active === "foodRegions" && <FoodOpsView mode="regions" onError={setError} />}
          {active === "foodQuality" && <FoodOpsView mode="quality" onError={setError} />}
          {active === "products" && <ProductReviewView mode="queue" onError={setError} />}
          {active === "productDuplicates" && <CanonicalDuplicateWorkspace onError={setError} />}
          {active === "productImages" && <ProductReviewView mode="images" onError={setError} />}
          {active === "productNutrition" && <ProductReviewView mode="nutrition" onError={setError} />}
          {active === "productRejected" && <ProductReviewView mode="rejected" onError={setError} />}
          {active === "recipes" && <RecipeAdminView onError={setError} />}
          {active === "achievements" && <AchievementAdminView onError={setError} />}
          {active === "users" && <UsersView mode="users" onError={setError} />}
          {active === "admins" && <UsersView mode="admins" onError={setError} />}
          {active === "userVerification" && <UsersView mode="verification" onError={setError} />}
          {active === "subscriptions" && <SubscriptionsView mode="overview" onError={setError} />}
          {active === "subscriptionFeatures" && <SubscriptionsView mode="features" onError={setError} />}
          {active === "subscriptionMapping" && <SubscriptionsView mode="mapping" onError={setError} />}
          {active === "subscriptionEntitlements" && <SubscriptionsView mode="entitlements" onError={setError} />}
          {active === "subscriptionAccess" && <SubscriptionsView mode="access" onError={setError} />}
          {active === "subscriptionAiQuotas" && <SubscriptionsView mode="aiQuotas" onError={setError} />}
          {active === "subscriptionEvents" && <SubscriptionEventsView onError={setError} targetContext={targetContext?.section === "subscriptionEvents" ? targetContext : null} onClearTarget={() => setTargetContext(null)} />}
          {active === "ai" && <AiReviewView onError={setError} targetContext={targetContext?.section === "ai" ? targetContext : null} onClearTarget={() => setTargetContext(null)} />}
          {active === "settings" && <GlobalSettingsView />}
          {active === "audits" && <AuditsView onError={setError} />}
          {active === "retentionPolicies" && <RetentionPoliciesView onError={setError} />}
          {active === "notifications" && <NotificationsView onError={setError} onNavigate={navigateToTarget} />}
          {active === "pushDelivery" && <PushDeliveryView onError={setError} />}
          {active === "tracking" && <TrackingMonitoringView mode="overview" onError={setError} />}
          {active === "trackingWater" && <TrackingMonitoringView mode="water" onError={setError} />}
          {active === "trackingFasting" && <TrackingMonitoringView mode="fasting" onError={setError} />}
          {active === "trackingSteps" && <TrackingMonitoringView mode="steps" onError={setError} />}
          {active === "system" && <SystemHealthView mode="overview" onError={setError} />}
          {active === "systemRuntime" && <SystemHealthView mode="runtime" onError={setError} />}
          {active === "systemDatabase" && <SystemHealthView mode="database" onError={setError} />}
          {active === "systemProviders" && <SystemHealthView mode="providers" onError={setError} />}
          {active === "systemProduction" && <SystemHealthView mode="production" onError={setError} />}
        </section>
      </main>
    </div>
  );
}

function SectionTabs({ active, onSelect }: { active: SectionKey; onSelect: (section: SectionKey) => void }) {
  const tabs = tabsForSection(active);
  if (!tabs || tabs.length <= 1) return null;

  return (
    <div className="section-tabs" aria-label="Section navigation">
      {tabs.map((tab) => (
        <button
          className={tab.key === active ? "active" : ""}
          key={tab.key}
          onClick={() => onSelect(tab.key)}
          title={tab.hint}
          type="button"
        >
          <strong>{tab.label}</strong>
          <span>{tab.hint}</span>
        </button>
      ))}
    </div>
  );
}
function isNavItemActive(section: NavigationItem, active: SectionKey): boolean {
  return section.key === active || Boolean(section.children?.some((child) => child.key === active));
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
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const response = await login(email, password);
      saveTokens(response);
      onLogin();
    } catch (err) {
      setError(formatRequestError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="login-page">
      <div className="login-theme-action">
        <ThemeToggle theme={theme} toggleTheme={toggleTheme} />
      </div>
      <section className="login-hero">
        <div className="hero-copy">
          <div className="login-brand-lockup">
            <img src="./grun/grun-wordmark.svg" alt="GRUN" />
            <span>Operations</span>
          </div>
          <h1>Admin control center for the calorie tracking platform.</h1>
          <p>Monitor users, review food data, inspect AI requests, and manage subscription features from one focused workspace.</p>
        </div>
      </section>
      <form className="login-card" onSubmit={submit}>
        <div>
          <p className="eyebrow">Secure access</p>
          <h2>Admin login</h2>
        </div>
        <label>
          Email
          <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" autoComplete="username" />
        </label>
        <label>
          Password
          <input value={password} onChange={(event) => setPassword(event.target.value)} type="password" autoComplete="current-password" placeholder="Admin password" />
        </label>
        {notice && <div className="form-notice">{notice}</div>}
        {error && <div className="form-error">{error}</div>}
        <button className="primary-button" disabled={busy || !email.trim() || !password} type="submit">
          {busy ? "Signing in..." : "Sign in"}
        </button>
      </form>
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
      section.key === "revenueCat" ? "revenuecat-logo" : ""
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
  const isDark = theme === "dark";
  return (
    <button
      aria-label={isDark ? "Switch to light mode" : "Switch to dark mode"}
      aria-pressed={isDark}
      className="theme-toggle"
      onClick={toggleTheme}
      type="button"
    >
      <span>{isDark ? "Dark" : "Light"}</span>
      <i />
    </button>
  );
}

type OperationCardItem = {
  title: string;
  value: string | number | undefined;
  detail: string;
  tone: "good" | "warn" | "danger";
  target: SectionKey;
};

function OperationCard({ item, onNavigate }: { item: OperationCardItem; onNavigate: (section: SectionKey) => void }) {
  return (
    <button className={`operation-card ${item.tone}`} onClick={() => onNavigate(item.target)} type="button">
      <span>{item.title}</span>
      <strong>{formatValue(item.value)}</strong>
      <small>{item.detail}</small>
    </button>
  );
}
function DashboardView({ onError, onNavigate }: { onError: (message: string | null) => void; onNavigate: (section: SectionKey) => void }) {
  const { data, state, reload } = useEndpoint<DashboardSummary>("/api/v1/admin/dashboard/summary", onError);
  const { data: unreadNotifications, state: unreadNotificationState, reload: reloadUnreadNotifications } = useEndpoint<PageResponse<Notification>>("/api/v1/notifications?unreadOnly=true&page=0&size=5", onError);
  const { data: criticalNotifications, state: criticalNotificationState, reload: reloadCriticalNotifications } = useEndpoint<PageResponse<Notification>>("/api/v1/notifications?unreadOnly=true&severity=CRITICAL&page=0&size=5", onError);
  const activeSubscriptions = (data?.activePlusSubscriptions ?? 0) + (data?.activeProSubscriptions ?? 0);
  const catalogReadyPercent = percent(data?.verifiedProducts, data?.totalProducts);
  const paidUserPercent = percent(activeSubscriptions, data?.totalUsers);
  const failedEvents = data?.failedSubscriptionProviderEvents ?? 0;
  const reviewQueue = data?.reviewQueueProducts ?? 0;
  const exhaustedAiQuota = data?.aiQuotaExhaustedSubscriptions ?? 0;
  const aiRequests7d = data?.aiRequestsLast7Days ?? 0;
  const aiConfirmed7d = data?.aiConfirmedLast7Days ?? 0;
  const aiRejected7d = data?.aiRejectedLast7Days ?? 0;
  const aiFailed7d = data?.aiFailedLast7Days ?? 0;
  const aiAcceptancePercent = percent(aiConfirmed7d, aiRequests7d);
  const aiRejectionPercent = percent(aiRejected7d, aiRequests7d);
  const aiRejectionReasons = Object.entries(data?.aiRejectionReasonsLast7Days ?? {}).filter(([, value]) => value > 0).sort((a, b) => b[1] - a[1]);
  const unreadAlertTotal = unreadNotifications?.totalElements ?? unreadNotifications?.content?.length ?? 0;
  const criticalAlertTotal = criticalNotifications?.totalElements ?? criticalNotifications?.content?.length ?? 0;
  const criticalAlertRows = criticalNotifications?.content ?? [];
  const healthLevel = failedEvents > 0 || criticalAlertTotal > 0 || reviewQueue > 100 ? "Needs attention" : "Stable";
  const healthTone = failedEvents > 0 || criticalAlertTotal > 0 || reviewQueue > 100 ? "warn" : "good";

  const headlineCards = [
    ["Platform state", healthLevel, failedEvents > 0 ? `${failedEvents} failed provider event(s)` : "No failed provider events"],
    ["Users", data?.totalUsers, `${formatValue(activeSubscriptions)} paid / ${paidUserPercent}% paid ratio`],
    ["Catalog readiness", `${catalogReadyPercent}%`, `${formatValue(data?.verifiedProducts)} verified of ${formatValue(data?.totalProducts)}`],
    ["Review queue", reviewQueue, `${formatValue(data?.needsReviewProducts)} data review / ${formatValue(data?.rawImportedProducts)} raw imports`]
  ];
  const operationCards: OperationCardItem[] = [
    {
      title: "Admin alerts",
      value: criticalAlertTotal,
      detail: criticalAlertTotal > 0 ? `${formatValue(criticalAlertTotal)} unread critical alert(s). Open inbox before routine checks.` : `${formatValue(unreadAlertTotal)} unread alert(s), no critical item visible.`,
      tone: criticalAlertTotal > 0 ? "danger" : unreadAlertTotal > 0 ? "warn" : "good",
      target: "notifications"
    },
    {
      title: "Provider events",
      value: failedEvents,
      detail: failedEvents > 0 ? "Failed payment/provider events need retry or config review." : "No failed payment/provider events.",
      tone: failedEvents > 0 ? "danger" : "good",
      target: "subscriptionEvents"
    },
    {
      title: "Catalog review",
      value: reviewQueue,
      detail: reviewQueue > 0 ? "Products are waiting for data, image, or nutrition approval." : "No product review pressure reported.",
      tone: reviewQueue > 100 ? "warn" : "good",
      target: "products"
    },
    {
      title: "AI quality",
      value: aiRejected7d + aiFailed7d,
      detail: `${formatValue(aiRejected7d)} rejected and ${formatValue(aiFailed7d)} failed AI request(s) in the last 7 days.`,
      tone: aiRejected7d + aiFailed7d > 0 ? "warn" : "good",
      target: "ai"
    },
    {
      title: "AI quota pressure",
      value: exhaustedAiQuota,
      detail: exhaustedAiQuota > 0 ? "Users have exhausted AI quota and may need plan/add-on review." : "No exhausted AI quota pressure.",
      tone: exhaustedAiQuota > 0 ? "warn" : "good",
      target: "subscriptionAiQuotas"
    },
    {
      title: "Catalog quality scan",
      value: data?.needsReviewProducts ?? 0,
      detail: "Run quality rules or AI-assisted validation against selected catalog suggestions.",
      tone: (data?.needsReviewProducts ?? 0) > 0 ? "warn" : "good",
      target: "foodQuality"
    }
  ];


  return (
    <div className="stack">
      <SectionToolbar title="Admin command summary" state={combineStates([state, unreadNotificationState, criticalNotificationState])} onReload={() => { void reload(); void reloadUnreadNotifications(); void reloadCriticalNotifications(); }} />
      <div className="metric-grid">
        {headlineCards.map(([label, value, hint]) => (
          <MetricCard key={String(label)} label={String(label)} value={formatValue(value)} hint={String(hint)} />
        ))}
      </div>
      <Panel title="Operations queue">
        <div className="operation-card-grid">
          {operationCards.map((item) => <OperationCard key={item.title} item={item} onNavigate={onNavigate} />)}
        </div>
      </Panel>
      <Panel title="Critical admin alerts">
        <div className="dashboard-alert-panel">
          <div>
            <strong>{formatValue(criticalAlertTotal)}</strong>
            <span>unread critical alert(s)</span>
            <small>{formatValue(unreadAlertTotal)} total unread admin alert(s)</small>
          </div>
          <div>
            <MiniNotificationList notifications={criticalAlertRows} />
          </div>
          <button className="ghost-button" type="button" onClick={() => onNavigate("notifications")}>Open Admin Inbox</button>
        </div>
      </Panel>
      <div className="split-grid">
        <Panel title="Immediate attention">
          <PriorityList
            items={[
              ["Failed payment/provider events", failedEvents],
              ["Products waiting for review", reviewQueue],
              ["AI quota exhausted users", exhaustedAiQuota],
              ["Provider events last 24h", data?.subscriptionProviderEventsLast24Hours ?? 0]
            ]}
          />
        </Panel>
        <Panel title="Business snapshot">
          <div className="readonly-grid">
            <DetailItem label="PLUS active" value={formatValue(data?.activePlusSubscriptions)} />
            <DetailItem label="PRO active" value={formatValue(data?.activeProSubscriptions)} />
            <DetailItem label="Canceled" value={formatValue(data?.canceledSubscriptions)} />
            <DetailItem label="Refunded" value={formatValue(data?.refundedSubscriptions)} />
            <DetailItem label="Admin users" value={formatValue(data?.adminUsers)} />
            <DetailItem label="Standard users" value={formatValue(data?.standardUsers)} />
          </div>
        </Panel>
      </div>
      <div className="split-grid">
        <Panel title="AI result quality">
          <div className="dashboard-ai-grid">
            <MetricCard label="AI requests 7d" value={formatValue(aiRequests7d)} hint="All tracked AI request types" />
            <MetricCard label="Accepted" value={`${aiAcceptancePercent}%`} hint={`${formatValue(aiConfirmed7d)} confirmed by users`} />
            <MetricCard label="Rejected" value={`${aiRejectionPercent}%`} hint={`${formatValue(aiRejected7d)} rejected by users`} />
            <MetricCard label="Failed" value={formatValue(aiFailed7d)} hint="Provider or processing failures" />
          </div>
          <div className="ai-reason-list">
            {aiRejectionReasons.length === 0 && <span className="empty-inline">No rejection reasons in the last 7 days.</span>}
            {aiRejectionReasons.map(([reason, value]) => <ProgressRow key={reason} label={shortFeature(reason)} value={value} total={aiRejected7d || value} tone="warn" />)}
          </div>
        </Panel>
        <Panel title="Catalog health">
          <ProgressRow label="Verified catalog" value={data?.verifiedProducts} total={data?.totalProducts} />
          <ProgressRow label="Needs review" value={data?.needsReviewProducts} total={data?.totalProducts} tone="warn" />
          <ProgressRow label="Rejected" value={data?.rejectedProducts} total={data?.totalProducts} tone="danger" />
        </Panel>
      </div>
      <div className="split-grid">
        <Panel title="Operational routing">
          <div className="roadmap-strip">
            <span><Badge value={healthLevel} tone={healthTone} /> Payment events first if failed count is above zero</span>
            <span>AI Ops shows request-level detail, prompt lifecycle, rejection feedback, and refundable quota cases</span>
            <span>Product Review handles catalog queue and image/nutrition quality</span>
            <span>Provider Events gives webhook detail and retry</span>
          </div>
        </Panel>
      </div>
    </div>
  );
}

type CanonicalDecision = {
  group: FoodCanonicalDuplicateGroup;
  product: FoodProduct;
};

function CanonicalDuplicateWorkspace({ onError }: { onError: (message: string | null) => void }) {
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [filter, setFilter] = useState<"ALL" | "UNRESOLVED" | "DECIDED">("ALL");
  const [pendingDecision, setPendingDecision] = useState<CanonicalDecision | null>(null);
  const [pendingClear, setPendingClear] = useState<FoodCanonicalDuplicateGroup | null>(null);
  const [saving, setSaving] = useState(false);
  const params = new URLSearchParams({ page: String(page), size: String(pageSize) });
  if (filter === "UNRESOLVED") params.set("resolved", "false");
  if (filter === "DECIDED") params.set("resolved", "true");
  const { data, state, reload } = useEndpoint<FoodCanonicalDuplicateGroupPage>(
    `/api/v1/admin/products/duplicates/canonical?${params.toString()}`,
    onError
  );
  const groups = data?.content ?? [];

  useEffect(() => setPage(0), [filter, pageSize]);

  async function resolvePrimary(decision: CanonicalDecision) {
    if (!decision.group.canonicalFoodKey || !decision.product.id) return;
    setSaving(true);
    onError(null);
    try {
      await request("/api/v1/admin/products/duplicates/canonical/resolve", {
        method: "POST",
        body: {
          canonicalFoodKey: decision.group.canonicalFoodKey,
          primaryProductId: decision.product.id
        }
      });
      setPendingDecision(null);
      await reload();
    } catch (error) {
      onError(formatRequestError(error));
    } finally {
      setSaving(false);
    }
  }

  async function clearResolution(group: FoodCanonicalDuplicateGroup) {
    if (!group.canonicalFoodKey) return;
    setSaving(true);
    onError(null);
    try {
      await request(`/api/v1/admin/products/duplicates/canonical/resolution?canonicalFoodKey=${encodeURIComponent(group.canonicalFoodKey)}`, {
        method: "DELETE"
      });
      setPendingClear(null);
      await reload();
    } catch (error) {
      onError(formatRequestError(error));
    } finally {
      setSaving(false);
    }
  }

  return <div className="stack">
    <SectionToolbar title="Canonical duplicate workspace" state={state} onReload={reload} />
    <div className="review-workspace-summary">
      <MetricCard label="Groups" value={formatValue(data?.totalElements ?? groups.length)} hint="Matching the current decision filter" />
      <MetricCard label="Needs attention" value={formatValue(groups.filter((group) => group.resolutionState !== "RESOLVED").length)} hint="Visible page: unresolved, stale, or unsafe" />
      <MetricCard label="Resolved" value={formatValue(groups.filter((group) => group.resolutionState === "RESOLVED").length)} hint="Visible page with a safe primary" />
      <MetricCard label="Candidates" value={formatValue(groups.reduce((sum, group) => sum + (group.productCount ?? 0), 0))} hint="Source records remain preserved" />
    </div>

    <Panel title="Decision filter">
      <div className="canonical-filter-row">
        <label>
          Resolution
          <select value={filter} onChange={(event) => setFilter(event.target.value as typeof filter)}>
            <option value="ALL">All groups</option>
            <option value="UNRESOLVED">No stored decision</option>
            <option value="DECIDED">Stored decision</option>
          </select>
        </label>
        <p>Recommendations are deterministic. An admin must confirm every primary decision.</p>
      </div>
    </Panel>

    {groups.length === 0 && <Panel title="Canonical groups"><p className="empty-state">No duplicate groups match this filter.</p></Panel>}
    {groups.map((group) => {
      const assessments = group.candidateAssessments?.length
        ? group.candidateAssessments
        : (group.products ?? []).map((product) => ({ product, primaryEligible: true, eligibilityIssues: [], recommended: product.id === group.recommendedPrimaryProductId }));
      return <section className="canonical-group" key={group.canonicalFoodKey}>
        <header className="canonical-group-header">
          <div>
            <span className="eyebrow">{group.canonicalFoodKey}</span>
            <h2>{formatValue(group.productCount)} source candidates</h2>
            {group.resolutionStatusReason && <p>{group.resolutionStatusReason}</p>}
          </div>
          <div className="canonical-group-status">
            <Badge value={group.resolutionState} tone={group.resolutionState === "RESOLVED" ? "good" : group.resolutionState === "UNRESOLVED" ? "neutral" : "warn"} />
            {group.primaryProductId && <small>Primary #{group.primaryProductId}</small>}
            {group.primaryProductId && <button className="ghost-button" type="button" onClick={() => setPendingClear(group)}>Clear decision</button>}
          </div>
        </header>
        <div className="canonical-candidate-grid">
          {assessments.map((assessment) => {
            const product = assessment.product;
            if (!product) return null;
            const selected = product.id === group.primaryProductId;
            return <article className={`canonical-candidate${selected ? " selected" : ""}`} key={product.id ?? product.sourceKey}>
              <div className="canonical-candidate-title">
                <div>
                  <h3>{productName(product)}</h3>
                  <span>{product.dataSource ?? "Unknown source"} · {product.sourceKey ?? `Product #${product.id}`}</span>
                </div>
                <div className="badge-stack">
                  {selected && <Badge value="CURRENT PRIMARY" tone="good" />}
                  {assessment.recommended && <Badge value="RECOMMENDED" tone="neutral" />}
                </div>
              </div>
              <dl className="canonical-facts">
                <div><dt>Preparation</dt><dd>{product.preparationState ?? "UNSPECIFIED"}</dd></div>
                <div><dt>Market</dt><dd>{product.marketRegion ?? "GLOBAL"}</dd></div>
                <div><dt>Quality</dt><dd>{formatValue(product.qualityScore)} / 100</dd></div>
                <div><dt>Usage</dt><dd>{formatValue(product.usageCount)}</dd></div>
                <div><dt>Calories</dt><dd>{formatValue(product.calories)} kcal</dd></div>
                <div><dt>Macros</dt><dd>P {formatValue(product.protein)} · C {formatValue(product.carbs)} · F {formatValue(product.fat)}</dd></div>
              </dl>
              <div className={`canonical-eligibility ${assessment.primaryEligible ? "good" : "blocked"}`}>
                <strong>{assessment.primaryEligible ? "Eligible for primary" : "Selection blocked"}</strong>
                {(assessment.eligibilityIssues ?? []).map((issue) => <span key={issue}>{issue}</span>)}
              </div>
              <button
                className="primary-button"
                type="button"
                disabled={saving || !assessment.primaryEligible || selected}
                onClick={() => setPendingDecision({ group, product })}
              >
                {selected ? "Selected primary" : "Select as primary"}
              </button>
            </article>;
          })}
        </div>
      </section>;
    })}

    <PaginationControls
      page={data?.page ?? page}
      pageSize={data?.size ?? pageSize}
      totalElements={data?.totalElements ?? groups.length}
      totalPages={data?.totalPages ?? 1}
      first={Boolean(data?.first)}
      last={Boolean(data?.last)}
      onPageChange={setPage}
      onPageSizeChange={setPageSize}
    />

    {pendingDecision && <ConfirmDialog
      title="Change canonical primary?"
      message={`Select ${productName(pendingDecision.product)} as the user-visible primary for this canonical group? Source records will remain stored.`}
      confirmLabel="Confirm primary"
      busy={saving}
      onCancel={() => setPendingDecision(null)}
      onConfirm={() => resolvePrimary(pendingDecision)}
    />}
    {pendingClear && <ConfirmDialog
      title="Clear canonical decision?"
      message="All eligible source candidates will become visible again until a new primary is selected. No product record will be deleted."
      confirmLabel="Clear decision"
      busy={saving}
      onCancel={() => setPendingClear(null)}
      onConfirm={() => clearResolution(pendingClear)}
    />}
  </div>;
}
type ProductReviewMode = "queue" | "images" | "nutrition" | "rejected";

type NutritionCorrectionImportResult = {
  totalRows?: number;
  updatedRows?: number;
  skippedRows?: number;
  candidateRows?: number;
  dryRun?: boolean;
  errors?: string[];
};

function ProductReviewView({ mode, onError }: { mode: ProductReviewMode; onError: (message: string | null) => void }) {
  const [query, setQuery] = useState("");
  const [verificationStatus, setVerificationStatus] = useState("RAW_IMPORTED");
  const [imageStatus, setImageStatus] = useState("");
  const [region, setRegion] = useState("");
  const [catalogType, setCatalogType] = useState("");
  const [dataSource, setDataSource] = useState("");
  const [qualityIssue, setQualityIssue] = useState("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [selectedProduct, setSelectedProduct] = useState<FoodProduct | null>(null);
  const [reviewDraft, setReviewDraft] = useState<ProductReviewDraft | null>(null);
  const [reviewNote, setReviewNote] = useState("");
  const [rejectConfirmationOpen, setRejectConfirmationOpen] = useState(false);
  const [saving, setSaving] = useState(false);
    const [savedNotice, setSavedNotice] = useState<string | null>(null);
const [correctionFile, setCorrectionFile] = useState<File | null>(null);
  const [correctionResult, setCorrectionResult] = useState<NutritionCorrectionImportResult | null>(null);
  const [markVerifiedOnImport, setMarkVerifiedOnImport] = useState(false);
  const [transferState, setTransferState] = useState<LoadState>("idle");
  const path = buildProductReviewPath({
    query,
    verificationStatus,
    imageStatus,
    region,
    catalogType,
    dataSource,
    qualityIssue,
    page,
    size: pageSize
  });
  const { data, state, reload } = useEndpoint<PageResponse<FoodProduct>>(path, onError);
  const rows = data?.content ?? [];
  const totalElements = data?.totalElements ?? rows.length;
  const highPriorityCount = rows.filter((item) => (item.reviewPriority ?? 0) >= 100).length;
  const missingImageCount = rows.filter((item) => !item.displayImageUrl && !item.imageUrl && !item.externalImageUrl).length;
  const activeFilterCount = [query, verificationStatus, imageStatus, region, catalogType, dataSource, qualityIssue].filter(Boolean).length;
  const modeTitle = {
    queue: "Food product review queue",
    images: "Product image review",
    nutrition: "Product nutrition review",
    rejected: "Rejected products"
  }[mode];

  useEffect(() => {
    setPage(0);
  }, [query, verificationStatus, imageStatus, region, catalogType, dataSource, qualityIssue, pageSize]);

  useEffect(() => {
    applyModeDefaults();
  }, [mode]);

  function applyModeDefaults() {
    setQuery("");
    setRegion("");
    setCatalogType("");
    setDataSource("");
    if (mode === "queue") {
      setVerificationStatus("RAW_IMPORTED");
      setImageStatus("");
      setQualityIssue("");
    }
    if (mode === "images") {
      setVerificationStatus("");
      setImageStatus("NEEDS_REVIEW");
      setQualityIssue("MISSING_IMAGE");
    }
    if (mode === "nutrition") {
      setVerificationStatus("");
      setImageStatus("");
      setQualityIssue("SUSPICIOUS_MACROS");
    }
    if (mode === "rejected") {
      setVerificationStatus("REJECTED");
      setImageStatus("");
      setQualityIssue("");
    }
  }

  function resetFilters() {
    applyModeDefaults();
  }

  function openProduct(item: FoodProduct) {
    setSelectedProduct(item);
    setReviewDraft(toProductReviewDraft(item));
    setReviewNote("");
    setRejectConfirmationOpen(false);
  }

  function closeProductModal() {
    setSelectedProduct(null);
    setReviewDraft(null);
    setReviewNote("");
    setRejectConfirmationOpen(false);
  }

  async function saveReviewChanges(item: FoodProduct, draft: ProductReviewDraft) {
    if (!item.id) {
      onError("Product id is missing.");
      return;
    }
    setSaving(true);
    onError(null);
    try {
      const updated = await request<FoodProduct>(`/api/v1/admin/products/${item.id}/review`, {
        method: "PATCH",
        body: {
          productName: draft.productName || productName(item),
          displayImageUrl: draft.displayImageUrl || null,
          marketRegion: draft.marketRegion || null,
          verificationStatus: draft.verificationStatus || null,
          imageStatus: draft.imageStatus || null,
          imageSource: draft.imageSource || null,
          catalogType: draft.catalogType || null,
          ...productReviewNutritionPayload(draft),
          reviewNote: reviewNote || "Updated from admin panel."
        }
      });
      void updated;
      await reload();
      closeProductModal();
      setSavedNotice("Saved");
      window.setTimeout(() => setSavedNotice(null), 2200);
    } catch (err) {
      onError(formatRequestError(err));
    } finally {
      setSaving(false);
    }
  }

  async function updateReview(item: FoodProduct, status: "VERIFIED" | "REJECTED") {
    if (!item.id) {
      onError("Product id is missing.");
      return;
    }
    const draft = reviewDraft ?? toProductReviewDraft(item);
    setSaving(true);
    onError(null);
    try {
      const updated = await request<FoodProduct>(`/api/v1/admin/products/${item.id}/review`, {
        method: "PATCH",
        body: {
          productName: draft.productName || productName(item),
          displayImageUrl: draft.displayImageUrl || item.displayImageUrl || item.imageUrl || item.externalImageUrl,
          marketRegion: draft.marketRegion || item.marketRegion,
          imageSource: draft.imageSource || item.imageSource,
          catalogType: draft.catalogType || item.catalogType,
          ...productReviewNutritionPayload(draft),
          verificationStatus: status,
          imageStatus: status === "VERIFIED" ? "APPROVED" : "REJECTED",
          reviewNote: reviewNote || (status === "VERIFIED" ? "Reviewed from admin panel." : "Rejected from admin panel.")
        }
      });
      void updated;
      await reload();
      closeProductModal();
      setSavedNotice("Saved");
      window.setTimeout(() => setSavedNotice(null), 2200);
    } catch (err) {
      onError(formatRequestError(err));
    } finally {
      setSaving(false);
    }
  }

  async function exportCurrentFilter() {
    setTransferState("loading");
    onError(null);
    try {
      const exportPath = buildProductReviewExportPath({
        query,
        verificationStatus,
        imageStatus,
        region,
        catalogType,
        dataSource,
        qualityIssue,
        limit: 10000
      });
      const blob = await requestBlob(exportPath, { timeoutMs: 60000 });
      downloadBlob(blob, `grun-product-review-export-${new Date().toISOString().slice(0, 10)}.csv`);
      setTransferState("ready");
    } catch (err) {
      setTransferState("error");
      onError(formatRequestError(err));
    }
  }

  async function importNutritionCorrections(dryRun: boolean) {
    if (!correctionFile) {
      onError("Correction CSV/TSV file is required.");
      return;
    }
    setTransferState("loading");
    onError(null);
    try {
      const formData = new FormData();
      formData.append("file", correctionFile);
      const result = await requestFormData<NutritionCorrectionImportResult>(
        `/api/v1/admin/products/nutrition-corrections/import?dryRun=${dryRun}&markVerified=${markVerifiedOnImport}`,
        formData,
        { timeoutMs: 120000 }
      );
      setCorrectionResult(result);
      setTransferState("ready");
      if (!dryRun) {
        await reload();
      }
    } catch (err) {
      setTransferState("error");
      onError(formatRequestError(err));
    }
  }

  return (
    <div className="stack">
      <SectionToolbar title={modeTitle} state={state} onReload={reload}>
        <button className="ghost-button" onClick={resetFilters} type="button">Reset filters</button>
      </SectionToolbar>
      {savedNotice && <div className="success-banner compact-success">{savedNotice}</div>}

      <div className="review-workspace-summary">
        <MetricCard label="Returned products" value={formatValue(totalElements)} hint="Matching current filters" />
        {mode !== "rejected" && <MetricCard label="High priority" value={formatValue(highPriorityCount)} hint="Priority score 100+" />}
        {mode !== "nutrition" && <MetricCard label="Missing images" value={formatValue(missingImageCount)} hint="Rows without usable image" />}
        <MetricCard label="Active filters" value={formatValue(activeFilterCount)} hint="Applied review filters" />
      </div>

      <Panel title="Review filters">
        <div className="review-filter-grid">
          <label>
            Product search
            <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Name, brand, barcode" />
          </label>
          <label>
            Verification
            <select value={verificationStatus} onChange={(event) => setVerificationStatus(event.target.value)}>
              <option value="">All</option>
              {VERIFICATION_STATUSES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
            </select>
          </label>
          <label>
            Image status
            <select value={imageStatus} onChange={(event) => setImageStatus(event.target.value)}>
              <option value="">All</option>
              {IMAGE_STATUSES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
            </select>
          </label>
          <label>
            Region
            <select value={region} onChange={(event) => setRegion(event.target.value)}>
              <option value="">All</option>
              {MARKET_REGIONS.map((value) => <option key={value} value={value}>{value}</option>)}
            </select>
          </label>
          <label>
            Catalog
            <select value={catalogType} onChange={(event) => setCatalogType(event.target.value)}>
              <option value="">All</option>
              {CATALOG_TYPES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
            </select>
          </label>
          <label>
            Data source
            <select value={dataSource} onChange={(event) => setDataSource(event.target.value)}>
              <option value="">All</option>
              {DATA_SOURCES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
            </select>
          </label>
          <label>
            Quality issue
            <select value={qualityIssue} onChange={(event) => setQualityIssue(event.target.value)}>
              <option value="">Any issue</option>
              {QUALITY_ISSUES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
            </select>
          </label>
        </div>
        <div className="review-transfer-panel">
          <div>
            <strong>Bulk correction workflow</strong>
            <span>Export the current filter, correct values externally, dry-run the file, then apply it. Enable verified marking only after dry-run looks clean.</span>
          </div>
          <div className="review-transfer-actions">
            <button className="ghost-button" type="button" disabled={transferState === "loading"} onClick={exportCurrentFilter}>Export current filter</button>
            <label className="file-picker">
              Correction file
              <input type="file" accept=".csv,.tsv,text/csv,text/tab-separated-values" onChange={(event) => setCorrectionFile(event.target.files?.[0] ?? null)} />
            </label>
            <label className="inline-check review-transfer-check">
              <input type="checkbox" checked={markVerifiedOnImport} onChange={(event) => setMarkVerifiedOnImport(event.target.checked)} />
              Mark imported rows verified
            </label>
            <button className="ghost-button" type="button" disabled={transferState === "loading" || !correctionFile} onClick={() => importNutritionCorrections(true)}>Dry-run import</button>
            <button className="primary-button" type="button" disabled={transferState === "loading" || !correctionFile} onClick={() => importNutritionCorrections(false)}>Apply import</button>
          </div>
          {correctionResult && <div className="correction-result-grid">
            <MetricPill label="Mode" value={correctionResult.dryRun ? "Dry-run" : "Applied"} />
            <MetricPill label="Total rows" value={formatValue(correctionResult.totalRows)} />
            <MetricPill label="Matched rows" value={formatValue(correctionResult.candidateRows ?? correctionResult.updatedRows)} />
            <MetricPill label="Updated rows" value={formatValue(correctionResult.updatedRows)} />
            <MetricPill label="Skipped rows" value={formatValue(correctionResult.skippedRows)} />
          </div>}
          {correctionResult?.errors?.length ? <div className="correction-error-list">
            {correctionResult.errors.slice(0, 5).map((error) => <span key={error}>{error}</span>)}
          </div> : null}
        </div>
      </Panel>

      <DataTable
        columns={["Product", "Source", "Review", "Quality", "Nutrition"]}
        rows={rows.map((item) => [
          <ProductCell item={item} />,
          <div className="table-stack">
            <span>{item.marketRegion ?? "-"}</span>
            <small>{item.dataSource ?? "-"}</small>
          </div>,
          <div className="badge-stack">
            <Badge value={item.verificationStatus} />
            <Badge value={item.imageStatus} tone="neutral" />
          </div>,
          <div className="table-stack">
            <strong>{formatValue(item.qualityScore)} / 100</strong>
            <small>Priority {formatValue(item.reviewPriority)}</small>
          </div>,
          <div className="table-stack">
            <span>{formatValue(item.calories)} kcal</span>
            <small>P {formatValue(item.protein)} / C {formatValue(item.carbs)} / F {formatValue(item.fat)}</small>
          </div>
        ])}
        rowData={rows}
        onRowClick={openProduct}
        empty="No products returned for this filter."
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
      {selectedProduct && reviewDraft && (
        <ProductReviewModal
          item={selectedProduct}
          onClose={closeProductModal}
          onApprove={() => updateReview(selectedProduct, "VERIFIED")}
          onReject={() => setRejectConfirmationOpen(true)}
          onSave={() => saveReviewChanges(selectedProduct, reviewDraft)}
          draft={reviewDraft}
          reviewNote={reviewNote}
          saving={saving}
          setDraft={setReviewDraft}
          setReviewNote={setReviewNote}
          onError={onError}
        />
      )}
      {selectedProduct && rejectConfirmationOpen && (
        <ConfirmDialog
          title="Reject product?"
          message="This will mark the product and image as rejected. The action will be written to review audit history."
          confirmLabel="Reject product"
          danger
          busy={saving}
          onCancel={() => setRejectConfirmationOpen(false)}
          onConfirm={() => updateReview(selectedProduct, "REJECTED")}
        />
      )}
    </div>
  );
}


type AdminRecipeIngredientForm = {
  foodItemId: string;
  productSearchQuery: string;
  productLabel: string;
  portionSize: string;
  portionUnit: string;
};

type AdminRecipeCreateForm = {
  ownerEmail: string;
  name: string;
  description: string;
  mealType: string;
  marketRegion: string;
  language: string;
  imageUrl: string;
  totalYieldGrams: string;
  defaultServingGrams: string;
  servingCount: string;
  visibility: string;
  verificationStatus: string;
  imageStatus: string;
  imageSource: string;
  reviewNote: string;
  categories: string[];
  cookingSteps: string[];
  ingredients: AdminRecipeIngredientForm[];
};

const emptyRecipeCreateForm: AdminRecipeCreateForm = {
  ownerEmail: "",
  name: "",
  description: "",
  mealType: "LUNCH",
  marketRegion: "",
  language: "en",
  imageUrl: "",
  totalYieldGrams: "",
  defaultServingGrams: "",
  servingCount: "",
  visibility: "PRIVATE",
  verificationStatus: "RAW_IMPORTED",
  imageStatus: "",
  imageSource: "",
  reviewNote: "",
  categories: [],
  cookingSteps: [""],
  ingredients: [{ foodItemId: "", productSearchQuery: "", productLabel: "", portionSize: "100", portionUnit: "GRAM" }]
};

function numericOrNull(value: string): number | null {
  const trimmed = value.trim();
  if (!trimmed) return null;
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : null;
}
function RecipeAdminView({ onError }: { onError: (message: string | null) => void }) {
  const [query, setQuery] = useState("");
  const [verificationStatus, setVerificationStatus] = useState("");
  const [visibility, setVisibility] = useState("");
  const [archived, setArchived] = useState("false");
  const [ownerEmail, setOwnerEmail] = useState("");
  const [mealType, setMealType] = useState("");
  const [marketRegion, setMarketRegion] = useState("");
  const [imageStatus, setImageStatus] = useState("");
  const [imageSource, setImageSource] = useState("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [selectedRecipe, setSelectedRecipe] = useState<AdminRecipe | null>(null);
  const [draftStatus, setDraftStatus] = useState("");
  const [draftVisibility, setDraftVisibility] = useState("");
  const [draftArchived, setDraftArchived] = useState("false");
  const [draftImageUrl, setDraftImageUrl] = useState("");
  const [draftImageStatus, setDraftImageStatus] = useState("");
  const [draftImageSource, setDraftImageSource] = useState("");
  const [draftCategories, setDraftCategories] = useState<string[]>([]);
  const [draftCookingSteps, setDraftCookingSteps] = useState<string[]>([]);
  const [reviewNote, setReviewNote] = useState("");
  const [saving, setSaving] = useState(false);
  const [savedNotice, setSavedNotice] = useState<string | null>(null);
  const [showCreateRecipe, setShowCreateRecipe] = useState(false);
  const [creatingRecipe, setCreatingRecipe] = useState(false);
  const [createForm, setCreateForm] = useState<AdminRecipeCreateForm>(emptyRecipeCreateForm);
  const [showImportPanel, setShowImportPanel] = useState(false);
  const [importText, setImportText] = useState("");
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState<AdminRecipeImportResult | null>(null);
  const [importStatus, setImportStatus] = useState("PENDING");
  const [importBatchId, setImportBatchId] = useState("");
  const [importPage, setImportPage] = useState(0);
  const [importPageSize, setImportPageSize] = useState(10);
  const [reviewingImportId, setReviewingImportId] = useState<number | null>(null);
  const [selectedImportCandidate, setSelectedImportCandidate] = useState<AdminRecipeImportCandidate | null>(null);
  const [importIngredientSearchIndex, setImportIngredientSearchIndex] = useState<number | null>(null);
  const [importIngredientSearchResults, setImportIngredientSearchResults] = useState<FoodProduct[]>([]);
  const [importIngredientSearchState, setImportIngredientSearchState] = useState<LoadState>("idle");
  const [recipeFiltersOpen, setRecipeFiltersOpen] = useState(false);
  const [recipeImportStateOpen, setRecipeImportStateOpen] = useState(false);
  const [recipeImportSourceOpen, setRecipeImportSourceOpen] = useState(false);
  const [activeIngredientSearchIndex, setActiveIngredientSearchIndex] = useState<number | null>(null);
  const [ingredientSearchResults, setIngredientSearchResults] = useState<FoodProduct[]>([]);
  const [ingredientSearchState, setIngredientSearchState] = useState<LoadState>("idle");
  const path = buildRecipeAdminPath({
    query,
    verificationStatus,
    visibility,
    archived,
    ownerEmail,
    mealType,
    marketRegion,
    imageStatus,
    imageSource,
    page,
    size: pageSize
  });
  const { data, state, reload } = useEndpoint<PageResponse<AdminRecipe>>(path, onError);
  const importPath = buildRecipeImportPath({
    status: importStatus,
    batchId: importBatchId,
    page: importPage,
    size: importPageSize
  });
  const { data: importData, state: importState, reload: reloadImports } = useEndpoint<PageResponse<AdminRecipeImportCandidate>>(importPath, onError);
  const importRows = importData?.content ?? [];
  const rows = data?.content ?? [];
  const activeFilterCount = [query, verificationStatus, visibility, archived, ownerEmail, mealType, marketRegion, imageStatus, imageSource].filter(Boolean).length;
  const pendingCount = rows.filter((recipe) => recipe.verificationStatus === "RAW_IMPORTED" || recipe.verificationStatus === "NEEDS_REVIEW").length;
  const archivedCount = rows.filter((recipe) => recipe.archived).length;
  const savedTotal = rows.reduce((sum, recipe) => sum + (recipe.savedCount ?? 0), 0);
  const favoriteTotal = rows.reduce((sum, recipe) => sum + (recipe.favoriteCount ?? 0), 0);
  const ratedRows = rows.filter((recipe) => (recipe.ratingCount ?? 0) > 0);
  const averageRating = ratedRows.length
    ? ratedRows.reduce((sum, recipe) => sum + (recipe.averageRating ?? 0), 0) / ratedRows.length
    : 0;

  useEffect(() => {
    setPage(0);
  }, [query, verificationStatus, visibility, archived, ownerEmail, mealType, marketRegion, imageStatus, imageSource, pageSize]);

  useEffect(() => {
    setImportPage(0);
  }, [importStatus, importBatchId, importPageSize]);

  function resetFilters() {
    setQuery("");
    setVerificationStatus("");
    setVisibility("");
    setArchived("false");
    setOwnerEmail("");
    setMealType("");
    setMarketRegion("");
    setImageStatus("");
    setImageSource("");
  }


  function updateCreateForm<K extends keyof AdminRecipeCreateForm>(key: K, value: AdminRecipeCreateForm[K]) {
    setCreateForm((current) => ({ ...current, [key]: value }));
  }

  function updateCreateIngredient(index: number, patch: Partial<AdminRecipeIngredientForm>) {
    setCreateForm((current) => ({
      ...current,
      ingredients: current.ingredients.map((ingredient, itemIndex) => itemIndex === index ? { ...ingredient, ...patch } : ingredient)
    }));
  }

  function addCreateIngredient() {
    setCreateForm((current) => ({
      ...current,
      ingredients: [...current.ingredients, { foodItemId: "", productSearchQuery: "", productLabel: "", portionSize: "100", portionUnit: "GRAM" }]
    }));
  }

  function removeCreateIngredient(index: number) {
    setCreateForm((current) => ({
      ...current,
      ingredients: current.ingredients.length <= 1
        ? current.ingredients
        : current.ingredients.filter((_, itemIndex) => itemIndex !== index)
    }));
    if (activeIngredientSearchIndex === index) {
      setActiveIngredientSearchIndex(null);
      setIngredientSearchResults([]);
    }
  }


  function updateCreateCookingStep(index: number, value: string) {
    setCreateForm((current) => ({
      ...current,
      cookingSteps: current.cookingSteps.map((step, itemIndex) => itemIndex === index ? value : step)
    }));
  }

  function addCreateCookingStep() {
    setCreateForm((current) => ({ ...current, cookingSteps: [...current.cookingSteps, ""] }));
  }

  function removeCreateCookingStep(index: number) {
    setCreateForm((current) => ({
      ...current,
      cookingSteps: current.cookingSteps.length <= 1 ? current.cookingSteps : current.cookingSteps.filter((_, itemIndex) => itemIndex !== index)
    }));
  }

  function updateDraftCookingStep(index: number, value: string) {
    setDraftCookingSteps((current) => current.map((step, itemIndex) => itemIndex === index ? value : step));
  }

  function addDraftCookingStep() {
    setDraftCookingSteps((current) => [...current, ""]);
  }

  function removeDraftCookingStep(index: number) {
    setDraftCookingSteps((current) => current.length <= 1 ? current : current.filter((_, itemIndex) => itemIndex !== index));
  }
  async function searchCreateIngredientProducts(index: number) {
    const ingredient = createForm.ingredients[index];
    const searchText = ingredient?.productSearchQuery.trim();
    if (!searchText || searchText.length < 2) {
      onError("Search with at least 2 characters.");
      return;
    }
    const params = new URLSearchParams({ q: searchText, page: "0", size: "8" });
    if (createForm.marketRegion) params.set("region", createForm.marketRegion);
    setActiveIngredientSearchIndex(index);
    setIngredientSearchState("loading");
    onError(null);
    try {
      const result = await request<PageResponse<FoodProduct>>(`/api/v1/products/search?${params.toString()}`);
      setIngredientSearchResults(result.content ?? []);
      setIngredientSearchState("ready");
    } catch (err) {
      setIngredientSearchResults([]);
      setIngredientSearchState("error");
      onError(formatRequestError(err));
    }
  }

  function selectCreateIngredientProduct(index: number, product: FoodProduct) {
    updateCreateIngredient(index, {
      foodItemId: product.id ? String(product.id) : "",
      productSearchQuery: productName(product),
      productLabel: productIngredientLabel(product)
    });
    setActiveIngredientSearchIndex(null);
    setIngredientSearchResults([]);
    setIngredientSearchState("idle");
  }

  async function searchImportIngredientProducts(index: number, ingredientName?: string) {
    const searchText = (ingredientName ?? "").trim();
    if (!searchText || searchText.length < 2) {
      onError("Ingredient search needs at least 2 characters.");
      return;
    }
    const params = new URLSearchParams({ q: searchText, page: "0", size: "8" });
    if (selectedImportCandidate?.marketRegion) params.set("region", selectedImportCandidate.marketRegion);
    setImportIngredientSearchIndex(index);
    setImportIngredientSearchState("loading");
    setImportIngredientSearchResults([]);
    onError(null);
    try {
      const result = await request<PageResponse<FoodProduct>>(`/api/v1/products/search?${params.toString()}`);
      setImportIngredientSearchResults(result.content ?? []);
      setImportIngredientSearchState("ready");
    } catch (err) {
      setImportIngredientSearchResults([]);
      setImportIngredientSearchState("error");
      onError(formatRequestError(err));
    }
  }

  async function mapImportIngredient(index: number, product: FoodProduct) {
    if (!selectedImportCandidate?.id || !product.id) return;
    setReviewingImportId(selectedImportCandidate.id);
    onError(null);
    try {
      const updated = await request<AdminRecipeImportCandidate>(`/api/v1/admin/recipes/imports/${selectedImportCandidate.id}/ingredients/${index}`, {
        method: "PATCH",
        body: { foodItemId: product.id }
      });
      setSelectedImportCandidate(updated);
      setImportIngredientSearchIndex(null);
      setImportIngredientSearchResults([]);
      setImportIngredientSearchState("idle");
      await reloadImports();
    } catch (err) {
      onError(formatRequestError(err));
    } finally {
      setReviewingImportId(null);
    }
  }


  function readRecipeImportFile(file: File | null) {
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => setImportText(String(reader.result ?? ""));
    reader.onerror = () => onError("Recipe import file could not be read.");
    reader.readAsText(file);
  }

  async function importRecipeJson() {
    if (!importText.trim()) {
      onError("Paste or choose a recipe import JSON file first.");
      return;
    }
    let payload: unknown;
    try {
      payload = JSON.parse(importText);
    } catch {
      onError("Recipe import JSON is not valid JSON.");
      return;
    }
    setImporting(true);
    onError(null);
    try {
      const result = await request<AdminRecipeImportResult>("/api/v1/admin/recipes/imports", {
        method: "POST",
        body: payload,
        timeoutMs: 60000
      });
      setImportResult(result);
      setImportBatchId(result.batchId ?? importBatchId);
      setImportStatus("PENDING");
      await reloadImports();
    } catch (err) {
      onError(formatRequestError(err));
    } finally {
      setImporting(false);
    }
  }

  async function approveRecipeImport(candidate: AdminRecipeImportCandidate) {
    if (!candidate.id) return;
    setReviewingImportId(candidate.id);
    onError(null);
    try {
      const created = await request<AdminRecipe>(`/api/v1/admin/recipes/imports/${candidate.id}/approve`, {
        method: "POST",
        body: { reviewNote: "Approved from admin recipe JSON import queue." },
        timeoutMs: 60000
      });
      setSelectedRecipe(created);
      setSelectedImportCandidate(null);
      await reloadImports();
      await reload();
    } catch (err) {
      onError(formatRequestError(err));
    } finally {
      setReviewingImportId(null);
    }
  }

  async function rejectRecipeImport(candidate: AdminRecipeImportCandidate) {
    if (!candidate.id) return;
    setReviewingImportId(candidate.id);
    onError(null);
    try {
      await request<AdminRecipeImportCandidate>(`/api/v1/admin/recipes/imports/${candidate.id}/reject`, {
        method: "POST",
        body: { reviewNote: "Rejected from admin recipe JSON import queue." }
      });
      await reloadImports();
    } catch (err) {
      onError(formatRequestError(err));
    } finally {
      setReviewingImportId(null);
    }
  }
  async function createAdminRecipe(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const ingredients = createForm.ingredients.map((ingredient) => ({
      foodItemId: numericOrNull(ingredient.foodItemId),
      portionSize: numericOrNull(ingredient.portionSize),
      portionUnit: ingredient.portionUnit || "GRAM"
    }));
    if (!createForm.name.trim()) {
      onError("Recipe name is required.");
      return;
    }
    if (ingredients.some((ingredient) => !ingredient.foodItemId || !ingredient.portionSize)) {
      onError("Each recipe ingredient needs a food item id and amount.");
      return;
    }
    const servingCount = numericOrNull(createForm.servingCount);
    if (servingCount !== null && !Number.isInteger(servingCount)) {
      onError("Serving count must be a whole number.");
      return;
    }
    const createPayload: Record<string, unknown> = {
      ownerEmail: createForm.ownerEmail.trim() || null,
      recipe: {
        name: createForm.name.trim(),
        description: createForm.description.trim() || null,
        mealType: createForm.mealType || null,
        marketRegion: createForm.marketRegion || null,
        language: createForm.language.trim() || null,
        imageUrl: createForm.imageUrl.trim() || null,
        totalYieldGrams: numericOrNull(createForm.totalYieldGrams),
        defaultServingGrams: numericOrNull(createForm.defaultServingGrams),
        servingCount,
        categories: createForm.categories,
        cookingSteps: createForm.cookingSteps.map((instruction) => ({ instruction: instruction.trim() })).filter((step) => step.instruction),
        ingredients
      },
      reviewNote: createForm.reviewNote.trim() || "Created from admin panel."
    };
    if (createForm.visibility && createForm.visibility !== "PRIVATE") {
      createPayload.visibility = createForm.visibility;
    }
    if (createForm.verificationStatus && createForm.verificationStatus !== "RAW_IMPORTED") {
      createPayload.verificationStatus = createForm.verificationStatus;
    }
    if (createForm.imageStatus) {
      createPayload.imageStatus = createForm.imageStatus;
    }
    if (createForm.imageSource && createForm.imageUrl.trim()) {
      createPayload.imageSource = createForm.imageSource;
    }
    setCreatingRecipe(true);
    onError(null);
    try {
      const created = await request<AdminRecipe>("/api/v1/admin/recipes", {
        method: "POST",
        body: createPayload
      });
      setCreateForm(emptyRecipeCreateForm);
      setShowCreateRecipe(false);
      setSelectedRecipe(created);
      await reload();
    } catch (err) {
      onError(formatRequestError(err));
    } finally {
      setCreatingRecipe(false);
    }
  }
  function openRecipe(recipe: AdminRecipe) {
    setSelectedRecipe(recipe);
    setDraftStatus(recipe.verificationStatus ?? "");
    setDraftVisibility(recipe.visibility ?? "");
    setDraftArchived(recipe.archived ? "true" : "false");
    setDraftImageUrl(recipe.imageUrl ?? "");
    setDraftImageStatus(recipe.imageStatus ?? "");
    setDraftImageSource(recipe.imageSource ?? "");
    setDraftCategories(recipe.categories ?? []);
    setDraftCookingSteps((recipe.cookingSteps ?? []).map((step) => step.instruction ?? "").filter(Boolean));
    setReviewNote("");
  }

  function closeRecipe() {
    setSelectedRecipe(null);
    setDraftImageUrl("");
    setDraftImageStatus("");
    setDraftImageSource("");
    setDraftCategories([]);
    setDraftCookingSteps([]);
    setReviewNote("");
  }

  async function saveRecipeReview() {
    if (!selectedRecipe?.id) {
      onError("Recipe id is missing.");
      return;
    }
    setSaving(true);
    onError(null);
    try {
      const updated = await request<AdminRecipe>(`/api/v1/admin/recipes/${selectedRecipe.id}/review`, {
        method: "PATCH",
        body: {
          verificationStatus: draftStatus || null,
          visibility: draftVisibility || null,
          archived: draftArchived === "true",
          imageUrl: draftImageUrl || null,
          imageStatus: draftImageStatus || null,
          imageSource: draftImageSource || null,
          categories: draftCategories,
          cookingSteps: draftCookingSteps.map((instruction) => ({ instruction: instruction.trim() })).filter((step) => step.instruction),
          reviewNote: reviewNote || "Updated from admin panel."
        }
      });
      void updated;
      await reload();
      closeRecipe();
      setSavedNotice("Saved");
      window.setTimeout(() => setSavedNotice(null), 2200);
    } catch (err) {
      onError(formatRequestError(err));
    } finally {
      setSaving(false);
    }
  }


  return (
    <div className="stack">
      <SectionToolbar title="Recipe operations" state={state} onReload={reload}>
        <button className="ghost-button" onClick={() => setShowImportPanel((value) => !value)} type="button">{showImportPanel ? "Close import" : "Import JSON"}</button>
        <button className="ghost-button" onClick={() => setShowCreateRecipe((value) => !value)} type="button">{showCreateRecipe ? "Close create" : "Create recipe"}</button>
        <button className="ghost-button" onClick={resetFilters} type="button">Reset filters</button>
      </SectionToolbar>
      {savedNotice && <div className="success-banner compact-success">{savedNotice}</div>}

      <div className="review-workspace-summary">
        <MetricCard label="Returned recipes" value={formatValue(data?.totalElements ?? rows.length)} hint="Matching current filters" />
        <MetricCard label="Pending review" value={formatValue(pendingCount)} hint="Current page only" />
        <MetricCard label="Saved / favorite" value={`${formatValue(savedTotal)} / ${formatValue(favoriteTotal)}`} hint="Current page engagement" />
        <MetricCard label="Avg rating" value={averageRating ? averageRating.toFixed(1) : "-"} hint={`${formatValue(ratedRows.length)} rated recipes on page`} />
        <MetricCard label="Archived / filters" value={`${formatValue(archivedCount)} / ${formatValue(activeFilterCount)}`} hint="Current page state" />
      </div>



      {showImportPanel && (
        <Panel title="Import recipe JSON">
          <div className="review-transfer-panel">
            <div>
              <strong>Upload open-source recipe candidates</strong>
              <span>Rows are stored as import candidates first. They are not public and are not added to the recipe catalog until admin review.</span>
            </div>
            <div className="review-transfer-actions">
              <label className="file-picker">
                JSON file
                <input accept="application/json,.json" type="file" onChange={(event) => readRecipeImportFile(event.target.files?.[0] ?? null)} />
              </label>
              <button className="primary-button" type="button" disabled={importing || !importText.trim()} onClick={importRecipeJson}>{importing ? "Importing..." : "Import candidates"}</button>
              <button className="ghost-button" type="button" onClick={() => { setImportText(""); setImportResult(null); }}>Clear</button>
            </div>
            <label className="full-width-field">
              JSON preview / paste
              <textarea className="recipe-import-json" value={importText} onChange={(event) => setImportText(event.target.value)} placeholder="Paste recipes-open-source JSON here or choose a file." />
            </label>
            {importResult && (
              <div className="correction-result-grid">
                <MetricCard label="Batch" value={importResult.batchId ?? "-"} hint="Stored import batch" />
                <MetricCard label="Created" value={formatValue(importResult.createdCandidates)} hint="New pending candidates" />
                <MetricCard label="Skipped" value={formatValue(importResult.skippedDuplicates)} hint="Duplicate source keys" />
                <MetricCard label="Failed" value={formatValue(importResult.failedCandidates)} hint="Invalid rows" />
                <MetricCard label="Total" value={formatValue(importResult.totalCandidates)} hint="Rows in JSON" />
              </div>
            )}
          </div>
        </Panel>
      )}

      <Panel title="Recipe import candidates">
        <div className="review-filter-grid recipe-import-filter-grid">
          <label>
            Status
            <select value={importStatus} onChange={(event) => setImportStatus(event.target.value)}>
              <option value="">All</option>
              {RECIPE_IMPORT_STATUSES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
            </select>
          </label>
          <label>
            Batch ID
            <input value={importBatchId} onChange={(event) => setImportBatchId(event.target.value)} placeholder="open-source-recipe-catalog-seed-001" />
          </label>
          <label>
            Page size
            <select value={importPageSize} onChange={(event) => setImportPageSize(Number(event.target.value))}>
              {[5, 10, 25, 50].map((value) => <option key={value} value={value}>{value}</option>)}
            </select>
          </label>
        </div>
        <DataTable
          columns={["Candidate", "Source", "State", "Issues", "Actions"]}
          rows={importRows.map((candidate) => {
            const unresolved = candidate.unresolvedIngredientCount ?? 0;
            const busy = reviewingImportId === candidate.id;
            const pending = candidate.status === "PENDING";
            return [
              <div className="entity-cell">
                <strong>{candidate.recipeName ?? "-"}</strong>
                <small>{candidate.mealType ?? "No meal"} | {candidate.marketRegion ?? "No region"} | {formatValue(candidate.ingredientCount)} ingredients</small>
              </div>,
              <div className="table-stack">
                <span>{candidate.sourceKey ?? "-"}</span>
                <small>{candidate.license ?? "No license"}</small>
              </div>,
              <div className="badge-stack">
                <Badge value={candidate.status} tone={candidate.status === "APPROVED" ? "good" : candidate.status === "REJECTED" ? "danger" : "warn"} />
                {candidate.createdRecipeId && <Badge value={`Recipe #${candidate.createdRecipeId}`} tone="good" />}
              </div>,
              <button className="link-button recipe-issue-button" type="button" onClick={(event) => { event.stopPropagation(); setSelectedImportCandidate(candidate); }}>
                <strong>{unresolved ? `${formatValue(unresolved)} unresolved` : "Ready"}</strong>
                <span>{candidate.validationIssues ? "View details" : "No issues"}</span>
              </button>,
              <div className="toolbar-actions recipe-import-actions">
                <button className="ghost-button" type="button" disabled={!pending || busy || unresolved > 0} onClick={(event) => { event.stopPropagation(); approveRecipeImport(candidate); }}>{busy ? "Working..." : "Move to review"}</button>
                <button className="ghost-button danger-text" type="button" disabled={!pending || busy} onClick={(event) => { event.stopPropagation(); rejectRecipeImport(candidate); }}>Reject</button>
              </div>
            ];
          })}
          empty={importState === "loading" ? "Loading import candidates..." : "No recipe import candidates."}
          rowData={importRows}
          onRowClick={setSelectedImportCandidate}
        />
        <PaginationControls
          page={importData?.page ?? importPage}
          pageSize={importData?.size ?? importPageSize}
          totalElements={importData?.totalElements ?? importRows.length}
          totalPages={importData?.totalPages ?? 1}
          first={Boolean(importData?.first)}
          last={Boolean(importData?.last)}
          onPageChange={setImportPage}
          onPageSizeChange={setImportPageSize}
        />
      </Panel>

      {selectedImportCandidate && (
        <div className="modal-backdrop" role="presentation" onClick={() => setSelectedImportCandidate(null)}>
          <section className="modal-card recipe-import-detail-modal" role="dialog" aria-modal="true" aria-label="Recipe import candidate detail" onClick={(event) => event.stopPropagation()}>
            <header className="modal-header">
              <div>
                <p className="eyebrow">Import candidate</p>
                <h2>{selectedImportCandidate.recipeName ?? "-"}</h2>
                <span>{selectedImportCandidate.batchId ?? "No batch"}</span>
              </div>
              <button className="icon-button" onClick={() => setSelectedImportCandidate(null)} type="button" aria-label="Close">x</button>
            </header>
            <div className="recipe-import-modal-body">
              {selectedImportCandidate.imageUrl && (
                <div className="recipe-import-image-preview">
                  <img src={selectedImportCandidate.imageUrl} alt="Recipe import preview" />
                  <div>
                    <strong>Recipe image from JSON</strong>
                    <span>{selectedImportCandidate.imageUrl}</span>
                  </div>
                </div>
              )}
              <CollapsiblePanel title="Candidate state" open={recipeImportStateOpen} onToggle={() => setRecipeImportStateOpen((value) => !value)}>
                <div className="readonly-grid compact-readonly-grid">
                  <DetailItem label="Status" value={selectedImportCandidate.status} />
                  <DetailItem label="Source key" value={selectedImportCandidate.sourceKey} />
                  <DetailItem label="Meal type" value={selectedImportCandidate.mealType} />
                  <DetailItem label="Region" value={selectedImportCandidate.marketRegion} />
                  <DetailItem label="Language" value={selectedImportCandidate.language} />
                  <DetailItem label="License" value={selectedImportCandidate.license} />
                  <DetailItem label="Ingredients" value={formatValue(selectedImportCandidate.ingredientCount)} />
                  <DetailItem label="Unresolved" value={formatValue(selectedImportCandidate.unresolvedIngredientCount)} />
                </div>
              </CollapsiblePanel>
              <Panel title="Resolve ingredient issues">
                <div className="recipe-import-resolution-summary">
                  <strong>{formatValue(selectedImportCandidate.unresolvedIngredientCount)} unresolved ingredient(s)</strong>
                  <span>Map each unresolved ingredient to a local food product before moving this candidate to review.</span>
                </div>
                <div className="recipe-import-ingredient-list">
                  {(selectedImportCandidate.ingredients ?? []).map((ingredient, fallbackIndex) => {
                    const ingredientIndex = ingredient.index ?? fallbackIndex;
                    const isActiveSearch = importIngredientSearchIndex === ingredientIndex;
                    const isMapped = Boolean(ingredient.foodItemId);
                    return (
                      <article className="recipe-import-ingredient-card" key={`${ingredientIndex}-${ingredient.ingredientName ?? "ingredient"}`}>
                        <div className="recipe-import-ingredient-main">
                          {ingredient.imageUrl && <img className="recipe-import-ingredient-thumb" src={ingredient.imageUrl} alt="" />}
                          <div>
                            <strong>{ingredient.ingredientName ?? `Ingredient ${ingredientIndex + 1}`}</strong>
                            <small>
                              {[ingredient.portionSize ? formatValue(ingredient.portionSize) : null, ingredient.portionUnit, ingredient.estimatedGrams ? `${formatValue(ingredient.estimatedGrams)}g estimated` : null]
                                .filter(Boolean)
                                .join(" | ") || "No amount metadata"}
                            </small>
                          </div>
                          <div className="recipe-import-ingredient-actions">
                            <Badge value={isMapped ? `Mapped #${ingredient.foodItemId}` : "Unresolved"} tone={isMapped ? "good" : "danger"} />
                            <button
                              className="ghost-button compact-button"
                              type="button"
                              disabled={selectedImportCandidate.status !== "PENDING" || reviewingImportId === selectedImportCandidate.id}
                              onClick={() => searchImportIngredientProducts(ingredientIndex, ingredient.ingredientName)}
                            >
                              {isMapped ? "Change" : "Find"}
                            </button>
                          </div>
                        </div>
                        {isActiveSearch && (
                          <div className="ingredient-search-results recipe-import-product-results">
                            {importIngredientSearchState === "loading" && <span>Searching products...</span>}
                            {importIngredientSearchState === "ready" && importIngredientSearchResults.length === 0 && <span>No product found. Create or import the product first, then map again.</span>}
                            {importIngredientSearchResults.map((product) => (
                              <button className="ingredient-search-result" key={product.id ?? product.normalizedBarcode ?? productName(product)} type="button" onClick={() => mapImportIngredient(ingredientIndex, product)}>
                                <strong>{productName(product)}</strong>
                                <span>{productIngredientLabel(product)}</span>
                              </button>
                            ))}
                          </div>
                        )}
                      </article>
                    );
                  })}
                  {(selectedImportCandidate.ingredients ?? []).length === 0 && <div className="empty-state compact-empty">No ingredient details returned for this candidate.</div>}
                </div>
              </Panel>
              <Panel title="Source cooking steps">
                <div className="recipe-step-list readonly-step-list">
                  {(selectedImportCandidate.cookingSteps ?? []).map((step, index) => (
                    <div className="recipe-step-row readonly-step-row" key={`import-step-${step.stepNumber ?? index}`}>
                      <span>{step.stepNumber ?? index + 1}</span>
                      <p>{step.instruction ?? "-"}</p>
                    </div>
                  ))}
                  {(selectedImportCandidate.cookingSteps ?? []).length === 0 && <div className="empty-state compact-empty">No cooking steps returned in this import candidate.</div>}
                </div>
              </Panel>              <Panel title="Validation issues">
                <div className={selectedImportCandidate.validationIssues ? "correction-error-list" : "empty-state compact-empty"}>
                  {selectedImportCandidate.validationIssues
                    ? selectedImportCandidate.validationIssues.split(";").map((issue) => <span key={issue.trim()}>{issue.trim()}</span>)
                    : <span>No validation issue recorded.</span>}
                </div>
              </Panel>
              <CollapsiblePanel title="Source metadata" open={recipeImportSourceOpen} onToggle={() => setRecipeImportSourceOpen((value) => !value)}>
                <div className="readonly-grid compact-readonly-grid">
                  <DetailItem label="Source title" value={selectedImportCandidate.sourceTitle} />
                  <DetailItem label="Recommended status" value={selectedImportCandidate.recommendedImportStatus} />
                  <DetailItem label="Source URL" value={selectedImportCandidate.sourceUrl} />
                  <DetailItem label="Revision URL" value={selectedImportCandidate.sourceRevisionUrl} />
                  <DetailItem label="Created recipe" value={selectedImportCandidate.createdRecipeId ? `#${selectedImportCandidate.createdRecipeId}` : "-"} />
                  <DetailItem label="Reviewed by" value={selectedImportCandidate.reviewedBy} />
                </div>
              </CollapsiblePanel>
            </div>
            <footer className="modal-actions">
              <button className="ghost-button" onClick={() => setSelectedImportCandidate(null)} type="button">Close</button>
              <button
                className="ghost-button danger-text"
                disabled={selectedImportCandidate.status !== "PENDING" || reviewingImportId === selectedImportCandidate.id}
                onClick={() => rejectRecipeImport(selectedImportCandidate)}
                type="button"
              >
                Reject
              </button>
              <button
                className="primary-button"
                disabled={selectedImportCandidate.status !== "PENDING" || (selectedImportCandidate.unresolvedIngredientCount ?? 0) > 0 || reviewingImportId === selectedImportCandidate.id}
                onClick={() => approveRecipeImport(selectedImportCandidate)}
                type="button"
              >
                Move to review
              </button>
            </footer>
          </section>
        </div>
      )}
      {showCreateRecipe && (
        <Panel title="Create recipe">
          <form className="admin-recipe-create-form" onSubmit={createAdminRecipe}>
            <div className="review-filter-grid">
              <label>
                Owner email
                <input value={createForm.ownerEmail} onChange={(event) => updateCreateForm("ownerEmail", event.target.value)} placeholder="Blank uses current admin" />
              </label>
              <label>
                Recipe name
                <input value={createForm.name} onChange={(event) => updateCreateForm("name", event.target.value)} placeholder="Homemade chicken bowl" required />
              </label>
              <label>
                Meal type
                <select value={createForm.mealType} onChange={(event) => updateCreateForm("mealType", event.target.value)}>
                  {MEAL_TYPES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
                </select>
              </label>
              <label>
                Region
                <select value={createForm.marketRegion} onChange={(event) => updateCreateForm("marketRegion", event.target.value)}>
                  <option value="">None</option>
                  {MARKET_REGIONS.map((value) => <option key={value} value={value}>{value}</option>)}
                </select>
              </label>
              <label>
                Language
                <input value={createForm.language} onChange={(event) => updateCreateForm("language", event.target.value)} placeholder="en" />
              </label>
              <label>
                Image URL
                <input value={createForm.imageUrl} onChange={(event) => updateCreateForm("imageUrl", event.target.value)} placeholder="https://..." />
              </label>
              <label>
                Total yield grams
                <input value={createForm.totalYieldGrams} onChange={(event) => updateCreateForm("totalYieldGrams", event.target.value)} inputMode="decimal" placeholder="1200" />
              </label>
              <label>
                Default serving grams
                <input value={createForm.defaultServingGrams} onChange={(event) => updateCreateForm("defaultServingGrams", event.target.value)} inputMode="decimal" placeholder="300" />
              </label>
              <label>
                Serving count
                <input value={createForm.servingCount} onChange={(event) => updateCreateForm("servingCount", event.target.value)} inputMode="numeric" placeholder="4" />
              </label>
              <label>
                Visibility
                <select value={createForm.visibility} onChange={(event) => updateCreateForm("visibility", event.target.value)}>
                  {RECIPE_VISIBILITIES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
                </select>
              </label>
              <label>
                Verification
                <select value={createForm.verificationStatus} onChange={(event) => updateCreateForm("verificationStatus", event.target.value)}>
                  {VERIFICATION_STATUSES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
                </select>
              </label>
              <label>
                Image status
                <select value={createForm.imageStatus} onChange={(event) => updateCreateForm("imageStatus", event.target.value)}>
                  <option value="">Auto / keep generated</option>
                  {IMAGE_STATUSES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
                </select>
              </label>
              <label>
                Image source
                <select value={createForm.imageSource} onChange={(event) => updateCreateForm("imageSource", event.target.value)}>
                  <option value="">Auto</option>
                  {IMAGE_SOURCES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
                </select>
              </label>
            </div>
            <label className="full-width-field">
              Description
              <textarea value={createForm.description} onChange={(event) => updateCreateForm("description", event.target.value)} placeholder="Preparation note or short description" />
            </label>
            <div className="category-editor admin-recipe-category-editor">
              {RECIPE_CATEGORIES.map((category) => (
                <label key={category} className="inline-check category-check">
                  <input
                    type="checkbox"
                    checked={createForm.categories.includes(category)}
                    onChange={(event) => updateCreateForm("categories", event.target.checked
                      ? Array.from(new Set([...createForm.categories, category]))
                      : createForm.categories.filter((item) => item !== category))}
                  />
                  {humanizeFeature(category)}
                </label>
              ))}
            </div>
            <div className="admin-recipe-ingredients">
              <div className="admin-recipe-ingredients-header">
                <strong>Ingredients</strong>
                <button className="ghost-button" type="button" onClick={addCreateIngredient}>Add ingredient</button>
              </div>
              {createForm.ingredients.map((ingredient, index) => (
                <div className="admin-recipe-ingredient-card" key={`ingredient-${index}`}>
                  <div className="admin-recipe-ingredient-row">
                    <label className="ingredient-product-search">
                      Product search
                      <div className="inline-input-action">
                        <input
                          value={ingredient.productSearchQuery}
                          onChange={(event) => updateCreateIngredient(index, {
                            productSearchQuery: event.target.value,
                            foodItemId: "",
                            productLabel: ""
                          })}
                          onKeyDown={(event) => {
                            if (event.key === "Enter") {
                              event.preventDefault();
                              void searchCreateIngredientProducts(index);
                            }
                          }}
                          placeholder="Search name, brand, barcode"
                        />
                        <button className="ghost-button" type="button" onClick={() => searchCreateIngredientProducts(index)} disabled={ingredientSearchState === "loading" && activeIngredientSearchIndex === index}>
                          {ingredientSearchState === "loading" && activeIngredientSearchIndex === index ? "Searching..." : "Search"}
                        </button>
                      </div>
                    </label>
                    <label>
                      Product ID
                      <input value={ingredient.foodItemId} onChange={(event) => updateCreateIngredient(index, { foodItemId: event.target.value, productLabel: "Manual product id" })} inputMode="numeric" placeholder="Auto after select" />
                    </label>
                    <label>
                      Amount
                      <input value={ingredient.portionSize} onChange={(event) => updateCreateIngredient(index, { portionSize: event.target.value })} inputMode="decimal" placeholder="100" />
                    </label>
                    <label>
                      Unit
                      <select value={ingredient.portionUnit} onChange={(event) => updateCreateIngredient(index, { portionUnit: event.target.value })}>
                        {PORTION_UNITS.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
                      </select>
                    </label>
                    <button className="ghost-button danger-text" type="button" onClick={() => removeCreateIngredient(index)} disabled={createForm.ingredients.length <= 1}>Remove</button>
                  </div>
                  {ingredient.productLabel && <div className="selected-product-note">Selected: {ingredient.productLabel}</div>}
                  {activeIngredientSearchIndex === index && (
                    <div className="ingredient-search-results">
                      {ingredientSearchState === "ready" && !ingredientSearchResults.length && <span className="muted-text">No product found for this search.</span>}
                      {ingredientSearchResults.map((product) => (
                        <button className="ingredient-search-result" type="button" key={product.id ?? product.barcode ?? productName(product)} onClick={() => selectCreateIngredientProduct(index, product)}>
                          <strong>{productName(product)}</strong>
                          <span>{productIngredientLabel(product)}</span>
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
            <div className="admin-recipe-steps">
              <div className="admin-recipe-ingredients-header">
                <strong>Cooking steps</strong>
                <button className="ghost-button" type="button" onClick={addCreateCookingStep}>Add step</button>
              </div>
              {createForm.cookingSteps.map((step, index) => (
                <div className="recipe-step-editor-row" key={`create-step-${index}`}>
                  <span>{index + 1}</span>
                  <textarea value={step} onChange={(event) => updateCreateCookingStep(index, event.target.value)} placeholder="Describe this preparation step" />
                  <button className="ghost-button danger-text" type="button" onClick={() => removeCreateCookingStep(index)} disabled={createForm.cookingSteps.length <= 1}>Remove</button>
                </div>
              ))}
            </div>            <label className="full-width-field">
              Admin note
              <textarea value={createForm.reviewNote} onChange={(event) => updateCreateForm("reviewNote", event.target.value)} placeholder="Internal note for audit trail" />
            </label>
            <div className="modal-actions inline-actions">
              <button className="ghost-button" type="button" onClick={() => setCreateForm(emptyRecipeCreateForm)}>Reset</button>
              <button className="primary-button" type="submit" disabled={creatingRecipe}>{creatingRecipe ? "Creating..." : "Create recipe"}</button>
            </div>
          </form>
        </Panel>
      )}
      <CollapsiblePanel title="Recipe filters" open={recipeFiltersOpen} onToggle={() => setRecipeFiltersOpen((value) => !value)}>
        <div className="review-filter-grid">
          <label>
            Search
            <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Recipe name" />
          </label>
          <label>
            Owner email
            <input value={ownerEmail} onChange={(event) => setOwnerEmail(event.target.value)} placeholder="user@example.com" />
          </label>
          <label>
            Verification
            <select value={verificationStatus} onChange={(event) => setVerificationStatus(event.target.value)}>
              <option value="">All</option>
              {VERIFICATION_STATUSES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
            </select>
          </label>
          <label>
            Visibility
            <select value={visibility} onChange={(event) => setVisibility(event.target.value)}>
              <option value="">All</option>
              {RECIPE_VISIBILITIES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
            </select>
          </label>
          <label>
            Archived
            <select value={archived} onChange={(event) => setArchived(event.target.value)}>
              <option value="">All</option>
              <option value="false">Active</option>
              <option value="true">Archived</option>
            </select>
          </label>
          <label>
            Meal type
            <select value={mealType} onChange={(event) => setMealType(event.target.value)}>
              <option value="">All</option>
              {MEAL_TYPES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
            </select>
          </label>
          <label>
            Region
            <select value={marketRegion} onChange={(event) => setMarketRegion(event.target.value)}>
              <option value="">All</option>
              {MARKET_REGIONS.map((value) => <option key={value} value={value}>{value}</option>)}
            </select>
          </label>
          <label>
            Image status
            <select value={imageStatus} onChange={(event) => setImageStatus(event.target.value)}>
              <option value="">All</option>
              {IMAGE_STATUSES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
            </select>
          </label>
          <label>
            Image source
            <select value={imageSource} onChange={(event) => setImageSource(event.target.value)}>
              <option value="">All</option>
              {IMAGE_SOURCES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
            </select>
          </label>
        </div>
      </CollapsiblePanel>

      <DataTable
        columns={["Recipe", "Owner", "State", "Engagement", "Nutrition"]}
        rows={rows.map((recipe) => [
          <div className="entity-cell">
            <strong>{recipe.name ?? "-"}</strong>
            <small>{recipe.mealType ?? "No meal type"} | {formatValue(recipe.ingredientCount)} ingredients</small>
          </div>,
          <div className="table-stack">
            <span>{recipe.ownerEmail ?? "-"}</span>
            <small>User #{formatValue(recipe.ownerUserId)}</small>
          </div>,
          <div className="badge-stack">
            <Badge value={recipe.verificationStatus} />
            <Badge value={recipe.imageStatus} tone="neutral" />
            <Badge value={recipe.archived ? "ARCHIVED" : recipe.visibility} tone={recipe.archived ? "danger" : "neutral"} />
          </div>,
          <RecipeEngagementCell recipe={recipe} />,
          <div className="table-stack">
            <span>{formatValue(recipe.calories)} kcal</span>
            <small>{formatValue(recipe.totalYieldGrams)} g total | P {formatValue(recipe.protein)} / C {formatValue(recipe.carbs)} / F {formatValue(recipe.fat)}</small>
          </div>
        ])}
        rowData={rows}
        onRowClick={openRecipe}
        empty="No recipes returned for this filter."
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

      {selectedRecipe && (
        <div className="modal-backdrop" role="presentation" onClick={closeRecipe}>
          <section className="product-modal recipe-modal" role="dialog" aria-modal="true" aria-label="Recipe admin detail" onClick={(event) => event.stopPropagation()}>
            <header className="modal-header">
              <div>
                <p className="eyebrow">Recipe review</p>
                <h2>{selectedRecipe.name ?? "-"}</h2>
                <span>{selectedRecipe.ownerEmail ?? "Unknown owner"}</span>
              </div>
              <button className="icon-button" onClick={closeRecipe} type="button" aria-label="Close">x</button>
            </header>
            <div className="modal-body">
              <div className="product-image-frame">
                {draftImageUrl ? <img alt={selectedRecipe.name ?? "Recipe"} src={draftImageUrl} /> : <span>No image</span>}
              </div>
              <div className="product-detail-stack">
                <div className="detail-grid editable">
                  <DetailItem label="Owner" value={selectedRecipe.ownerEmail} />
                  <DetailItem label="Meal type" value={selectedRecipe.mealType} />
                  <DetailItem label="Visibility" value={selectedRecipe.visibility} />
                  <DetailItem label="Region" value={selectedRecipe.marketRegion} />
                  <DetailItem label="Image status" value={selectedRecipe.imageStatus} />
                  <DetailItem label="Image source" value={selectedRecipe.imageSource} />
                  <DetailItem label="Yield" value={`${formatValue(selectedRecipe.totalYieldGrams)} g`} />
                  <DetailItem label="Serving" value={`${formatValue(selectedRecipe.defaultServingGrams)} g`} />
                  <DetailItem label="Saved count" value={formatValue(selectedRecipe.savedCount)} />
                  <DetailItem label="Favorite count" value={formatValue(selectedRecipe.favoriteCount)} />
                  <DetailItem label="Rating count" value={formatValue(selectedRecipe.ratingCount)} />
                  <DetailItem label="Average rating" value={(selectedRecipe.averageRating ?? 0) > 0 ? selectedRecipe.averageRating?.toFixed(1) : "-"} />
                  <EditableDetail label="Image URL">
                    <input value={draftImageUrl} onChange={(event) => setDraftImageUrl(event.target.value)} placeholder="https://..." />
                  </EditableDetail>
                  <EditableDetail label="Image status">
                    <select value={draftImageStatus} onChange={(event) => setDraftImageStatus(event.target.value)}>
                      <option value="">Keep current</option>
                      {IMAGE_STATUSES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
                    </select>
                  </EditableDetail>
                  <EditableDetail label="Image source">
                    <select value={draftImageSource} onChange={(event) => setDraftImageSource(event.target.value)}>
                      <option value="">Keep current</option>
                      {IMAGE_SOURCES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
                    </select>
                  </EditableDetail>
                  <EditableDetail label="Verification">
                    <select value={draftStatus} onChange={(event) => setDraftStatus(event.target.value)}>
                      <option value="">Keep current</option>
                      {VERIFICATION_STATUSES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
                    </select>
                  </EditableDetail>
                  <EditableDetail label="Visibility">
                    <select value={draftVisibility} onChange={(event) => setDraftVisibility(event.target.value)}>
                      <option value="">Keep current</option>
                      {RECIPE_VISIBILITIES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
                    </select>
                  </EditableDetail>
                  <EditableDetail label="Archive state">
                    <select value={draftArchived} onChange={(event) => setDraftArchived(event.target.value)}>
                      <option value="false">Active</option>
                      <option value="true">Archived</option>
                    </select>
                  </EditableDetail>
                </div>
                <div className="detail-grid compact">
                  <DetailItem label="Calories" value={`${formatValue(selectedRecipe.calories)} kcal`} />
                  <DetailItem label="Protein" value={`${formatValue(selectedRecipe.protein)} g`} />
                  <DetailItem label="Carbs" value={`${formatValue(selectedRecipe.carbs)} g`} />
                  <DetailItem label="Fat" value={`${formatValue(selectedRecipe.fat)} g`} />
                  <DetailItem label="Fiber" value={`${formatValue(selectedRecipe.fiber)} g`} />
                  <DetailItem label="Sugar" value={`${formatValue(selectedRecipe.sugar)} g`} />
                </div>
                <Panel title="Public categories">
                  <div className="tag-cloud category-editor">
                    {RECIPE_CATEGORIES.map((category) => (
                      <label key={category} className="inline-check category-check">
                        <input
                          type="checkbox"
                          checked={draftCategories.includes(category)}
                          onChange={(event) => setDraftCategories((current) => event.target.checked
                            ? Array.from(new Set([...current, category]))
                            : current.filter((item) => item !== category))}
                        />
                        {humanizeFeature(category)}
                      </label>
                    ))}
                    {!draftCategories.length && <span className="muted-text">At least one category is required before public approval.</span>}
                  </div>
                </Panel>
                <Panel title="Ingredients">
                  <DataTable
                    columns={["Food", "Portion", "Normalized"]}
                    rows={(selectedRecipe.ingredients ?? []).map((ingredient) => [
                      ingredient.foodName ?? "-",
                      `${formatValue(ingredient.portionSize)} ${ingredient.portionUnit ?? ""}`,
                      `${formatValue(ingredient.normalizedPortionGrams)} g`
                    ])}
                    empty="No ingredients returned."
                  />
                </Panel>
                <Panel title="Review cooking steps">
                  <div className="recipe-step-list">
                    {draftCookingSteps.map((step, index) => (
                      <div className="recipe-step-editor-row" key={`review-step-${index}`}>
                        <span>{index + 1}</span>
                        <textarea value={step} onChange={(event) => updateDraftCookingStep(index, event.target.value)} placeholder="Describe this preparation step" />
                        <button className="ghost-button danger-text" type="button" onClick={() => removeDraftCookingStep(index)} disabled={draftCookingSteps.length <= 1}>Remove</button>
                      </div>
                    ))}
                    {!draftCookingSteps.length && <div className="empty-state compact-empty">No cooking steps saved for this recipe.</div>}
                  </div>
                  <button className="ghost-button" type="button" onClick={addDraftCookingStep}>Add step</button>
                </Panel>
                <EditableDetail label="Review note">
                  <textarea value={reviewNote} onChange={(event) => setReviewNote(event.target.value)} placeholder="Optional internal moderation note" />
                </EditableDetail>
              </div>
            </div>
            <footer className="modal-actions">
              <button className="ghost-button" onClick={closeRecipe} type="button">Cancel</button>
              <button
                className="ghost-button"
                disabled={saving}
                onClick={() => {
                  setDraftStatus("REJECTED");
                  setDraftVisibility("PRIVATE");
                  setDraftArchived("false");
                  setDraftImageStatus(draftImageStatus || "REJECTED");
                  setReviewNote(reviewNote || "Rejected from admin recipe review.");
                }}
                type="button"
              >
                Mark rejected
              </button>
              <button
                className="ghost-button"
                disabled={saving}
                onClick={() => {
                  setDraftStatus("VERIFIED");
                  setDraftVisibility("PUBLIC_ADMIN");
                  setDraftArchived("false");
                  setDraftImageStatus("APPROVED");
                  setDraftImageSource(draftImageSource || "ADMIN_UPLOAD");
                  setDraftCategories((current) => current.length ? current : ["HIGH_PROTEIN"]);
                  setReviewNote(reviewNote || "Approved for public recipe discovery.");
                }}
                type="button"
              >
                Prepare public approval
              </button>
              <button className="primary-button" disabled={saving} onClick={saveRecipeReview} type="button">{saving ? "Saving..." : "Save review"}</button>
            </footer>
          </section>
        </div>
      )}
    </div>
  );
}

function RecipeEngagementCell({ recipe }: { recipe: AdminRecipe }) {
  const average = (recipe.averageRating ?? 0) > 0 ? recipe.averageRating?.toFixed(1) : "-";
  return (
    <div className="table-stack">
      <span>{average} avg | {formatValue(recipe.ratingCount)} ratings</span>
      <small>{formatValue(recipe.savedCount)} saved | {formatValue(recipe.favoriteCount)} favorites</small>
    </div>
  );
}

type AchievementForm = {
  code: string;
  title: string;
  description: string;
  metricKey: string;
  category: string;
  tier: string;
  targetValue: string;
  active: string;
  sortOrder: string;
};

const emptyAchievementForm: AchievementForm = {
  code: "",
  title: "",
  description: "",
  metricKey: "",
  category: "FOOD",
  tier: "BRONZE",
  targetValue: "1",
  active: "true",
  sortOrder: "1000"
};

function AchievementAdminView({ onError }: { onError: (message: string | null) => void }) {
  const { data, state, reload } = useEndpoint<AdminAchievementDefinition[]>("/api/v1/admin/achievements", onError);
  const { data: metrics, state: metricState } = useEndpoint<AdminAchievementMetrics>("/api/v1/admin/achievements/metrics", onError);
  const [selected, setSelected] = useState<AdminAchievementDefinition | null>(null);
  const [form, setForm] = useState<AchievementForm>(emptyAchievementForm);
  const [saving, setSaving] = useState(false);
  const definitions = data ?? [];
  const metricKeys = metrics?.metricKeys ?? [];
  const activeCount = definitions.filter((item) => item.active).length;
  const inactiveCount = definitions.length - activeCount;

  function startCreate() {
    setSelected(null);
    setForm({ ...emptyAchievementForm, metricKey: metricKeys[0] ?? "" });
  }

  function startEdit(definition: AdminAchievementDefinition) {
    setSelected(definition);
    setForm({
      code: definition.code ?? "",
      title: definition.title ?? "",
      description: definition.description ?? "",
      metricKey: definition.metricKey ?? metricKeys[0] ?? "",
      category: definition.category ?? "FOOD",
      tier: definition.tier ?? "BRONZE",
      targetValue: String(definition.targetValue ?? 1),
      active: definition.active ? "true" : "false",
      sortOrder: String(definition.sortOrder ?? 1000)
    });
  }

  function updateForm(key: keyof AchievementForm, value: string) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  async function saveDefinition(event: FormEvent) {
    event.preventDefault();
    const payload = {
      code: form.code.trim().toUpperCase(),
      title: form.title.trim(),
      description: form.description.trim(),
      metricKey: form.metricKey,
      category: form.category,
      tier: form.tier,
      targetValue: Number(form.targetValue),
      active: form.active === "true",
      sortOrder: Number(form.sortOrder)
    };
    if (!payload.code || !payload.title || !payload.description || !payload.metricKey) {
      onError("Code, title, description and metric key are required.");
      return;
    }
    setSaving(true);
    onError(null);
    try {
      await request<AdminAchievementDefinition>(
        selected?.code ? `/api/v1/admin/achievements/${selected.code}` : "/api/v1/admin/achievements",
        {
          method: selected?.code ? "PUT" : "POST",
          body: payload
        }
      );
      await reload();
      setSelected(null);
      setForm(emptyAchievementForm);
    } catch (err) {
      onError(formatRequestError(err));
    } finally {
      setSaving(false);
    }
  }

  async function deactivate(definition: AdminAchievementDefinition) {
    if (!definition.code) return;
    setSaving(true);
    onError(null);
    try {
      await request<AdminAchievementDefinition>(`/api/v1/admin/achievements/${definition.code}`, { method: "DELETE" });
      await reload();
    } catch (err) {
      onError(formatRequestError(err));
    } finally {
      setSaving(false);
    }
  }


  return (
    <div className="stack">
      <SectionToolbar title="Achievement definitions" state={combineStates([state, metricState])} onReload={reload}>
        <button className="primary-button" onClick={startCreate} type="button">New achievement</button>
      </SectionToolbar>

      <div className="user-summary-grid">
        <MetricCard label="Definitions" value={formatValue(definitions.length)} hint="Total configured rules" />
        <MetricCard label="Active" value={formatValue(activeCount)} hint="Visible to app users" />
        <MetricCard label="Inactive" value={formatValue(inactiveCount)} hint="Hidden from user achievement list" />
      </div>

      <Panel title={selected ? `Edit ${selected.code}` : "Create achievement"}>
        <form className="review-filter-grid" onSubmit={saveDefinition}>
          <label>
            Code
            <input
              disabled={Boolean(selected)}
              value={form.code}
              onChange={(event) => updateForm("code", event.target.value.toUpperCase())}
              placeholder="FOOD_LOG_30_DAYS"
            />
          </label>
          <label>
            Title
            <input value={form.title} onChange={(event) => updateForm("title", event.target.value)} placeholder="30 Day Food Logger" />
          </label>
          <label>
            Metric
            <select value={form.metricKey} onChange={(event) => updateForm("metricKey", event.target.value)}>
              <option value="">Select metric</option>
              {metricKeys.map((metric) => <option key={metric} value={metric}>{humanizeFeature(metric)}</option>)}
            </select>
          </label>
          <label>
            Category
            <select value={form.category} onChange={(event) => updateForm("category", event.target.value)}>
              {ACHIEVEMENT_CATEGORIES.map((category) => <option key={category} value={category}>{humanizeFeature(category)}</option>)}
            </select>
          </label>
          <label>
            Tier
            <select value={form.tier} onChange={(event) => updateForm("tier", event.target.value)}>
              {ACHIEVEMENT_TIERS.map((tier) => <option key={tier} value={tier}>{humanizeFeature(tier)}</option>)}
            </select>
          </label>
          <label>
            Target
            <input min="1" type="number" value={form.targetValue} onChange={(event) => updateForm("targetValue", event.target.value)} />
          </label>
          <label>
            Sort
            <input min="0" type="number" value={form.sortOrder} onChange={(event) => updateForm("sortOrder", event.target.value)} />
          </label>
          <label>
            Status
            <select value={form.active} onChange={(event) => updateForm("active", event.target.value)}>
              <option value="true">Active</option>
              <option value="false">Inactive</option>
            </select>
          </label>
          <label className="wide-field">
            Description
            <input value={form.description} onChange={(event) => updateForm("description", event.target.value)} placeholder="Describe what the user needs to do." />
          </label>
          <div className="form-actions">
            <button className="ghost-button" onClick={() => {
              setSelected(null);
              setForm(emptyAchievementForm);
            }} type="button">Clear</button>
            <button className="primary-button" disabled={saving} type="submit">{saving ? "Saving..." : selected ? "Save changes" : "Create"}</button>
          </div>
        </form>
      </Panel>

      <DataTable
        columns={["Achievement", "Metric", "Target", "State", "Order"]}
        rows={definitions.map((definition) => [
          <div className="entity-cell">
            <strong>{definition.title ?? definition.code ?? "-"}</strong>
            <small>{definition.code ?? "-"} | {definition.description ?? "-"}</small>
          </div>,
          <div className="badge-stack">
            <Badge value={definition.metricKey} />
            <Badge value={definition.category} tone="neutral" />
          </div>,
          <div className="table-stack">
            <span>{formatValue(definition.targetValue)}</span>
            <small>{definition.tier ?? "-"}</small>
          </div>,
          <Badge value={definition.active ? "ACTIVE" : "INACTIVE"} tone={definition.active ? "good" : "warn"} />,
          <div className="table-stack">
            <span>{formatValue(definition.sortOrder)}</span>
            <button className="ghost-button" disabled={saving || !definition.active} onClick={(event) => {
              event.stopPropagation();
              deactivate(definition);
            }} type="button">Deactivate</button>
          </div>
        ])}
        rowData={definitions}
        onRowClick={startEdit}
        empty="No achievement definitions found."
      />
    </div>
  );
}

type UsersMode = "users" | "admins" | "verification";

function UsersView({ mode, onError }: { mode: UsersMode; onError: (message: string | null) => void }) {
  const { data, state, reload } = useEndpoint<UserProfile[]>("/api/v1/admin/users/userList", onError);
  const [selectedUser, setSelectedUser] = useState<UserProfile | null>(null);
  const [statusActionState, setStatusActionState] = useState<LoadState>("idle");
  const [userPage, setUserPage] = useState(0);
  const [userPageSize, setUserPageSize] = useState(25);
  const users = data ?? [];
  const adminUsers = users.filter((user) => user.role === "ADMIN");
  const standardUsers = users.filter((user) => user.role !== "ADMIN");
  const unverifiedUsers = users.filter((user) => !user.emailVerified);
  const visibleUsers = mode === "admins" ? adminUsers : mode === "verification" ? unverifiedUsers : standardUsers;
  const userTotalPages = Math.max(1, Math.ceil(visibleUsers.length / userPageSize));
  const safeUserPage = Math.min(userPage, userTotalPages - 1);
  const pagedUsers = visibleUsers.slice(safeUserPage * userPageSize, safeUserPage * userPageSize + userPageSize);
  const title = mode === "admins" ? "Admin accounts" : mode === "verification" ? "Email verification queue" : "App users";

  useEffect(() => {
    setUserPage(0);
  }, [mode, userPageSize, visibleUsers.length]);

  async function updateSelectedUserStatus(payload: { accountEnabled: boolean; accountLocked: boolean; reason: string }) {
    if (!selectedUser?.id) return;
    setStatusActionState("loading");
    try {
      const updated = await request<UserProfile>(`/api/v1/admin/users/${selectedUser.id}/status`, {
        method: "PATCH",
        body: JSON.stringify(payload)
      });
      setSelectedUser(updated);
      await reload();
      setStatusActionState("ready");
    } catch (error) {
      setStatusActionState("error");
      onError(formatRequestError(error));
    }
  }

  return (
    <div className="stack">
      <SectionToolbar title={title} state={state} onReload={reload} />
      <div className="user-summary-grid">
        <MetricCard label="Standard users" value={formatValue(standardUsers.length)} hint="Non-admin accounts" />
        <MetricCard label="Admin users" value={formatValue(adminUsers.length)} hint="Privileged accounts" />
        <MetricCard label="Unverified" value={formatValue(unverifiedUsers.length)} hint="Email verification pending" />
      </div>
      <DataTable
        columns={["User", "Role", "Status", "Region", "Language", "Email", "Profile"]}
        rows={pagedUsers.map((user) => [
          <UserCell user={user} />,
          <Badge value={user.role ?? "-"} />,
          <div className="badge-stack">
            <Badge
              value={user.accountLocked ? "Locked" : user.accountEnabled === false ? "Disabled" : "Enabled"}
              tone={user.accountLocked || user.accountEnabled === false ? "danger" : "good"}
            />
            <Badge value={user.emailVerified ? "Verified" : "Unverified"} tone={user.emailVerified ? "good" : "warn"} />
          </div>,
          formatValue(user.marketRegion),
          formatValue(user.preferredLanguage),
          formatValue(user.email),
          `${formatValue(user.age)} yrs | ${formatValue(user.height)} cm / ${formatValue(user.weight)} kg`
        ])}
        rowData={pagedUsers}
        onRowClick={setSelectedUser}
        empty={mode === "admins" ? "No admin users found." : mode === "verification" ? "No unverified users found." : "No standard users found."}
      />
      <PaginationControls
        page={safeUserPage}
        pageSize={userPageSize}
        totalElements={visibleUsers.length}
        totalPages={userTotalPages}
        first={safeUserPage <= 0}
        last={safeUserPage >= userTotalPages - 1}
        onPageChange={setUserPage}
        onPageSizeChange={(size) => {
          setUserPageSize(size);
          setUserPage(0);
        }}
      />
      {selectedUser && <UserDetailsModal user={selectedUser} statusState={statusActionState} onClose={() => setSelectedUser(null)} onStatusUpdate={updateSelectedUserStatus} />}
    </div>
  );
}

type SubscriptionMode = "overview" | "features" | "mapping" | "entitlements" | "access" | "aiQuotas";

function SubscriptionsView({ mode, onError }: { mode: SubscriptionMode; onError: (message: string | null) => void }) {
  const { data, state, reload } = useEndpoint<FeatureMatrixItem[]>("/api/v1/admin/subscriptions/features", onError);
  const { data: pricingData, state: pricingState, reload: reloadPricing } = useEndpoint<AiCreditPricingPolicy[]>("/api/v1/admin/ai-credit-pricing", onError);
  const { data: users, state: usersState } = useEndpoint<UserProfile[]>("/api/v1/admin/users/userList", onError);
  const { data: revenueCat, state: revenueCatState, reload: reloadRevenueCat } = useEndpoint<RevenueCatConfigStatus>("/api/v1/admin/revenuecat/config", onError);
  const { data: subscriptionAudits, state: subscriptionAuditState, reload: reloadSubscriptionAudits } = useEndpoint<PageResponse<AuditEntry>>(buildAuditPath({ actionType: "", targetType: "USER_SUBSCRIPTION", page: 0, size: 20 }), onError);
  const [savingKey, setSavingKey] = useState<string | null>(null);
  const [subscriptionActionState, setSubscriptionActionState] = useState<LoadState>("idle");
  const [subscriptionResult, setSubscriptionResult] = useState<SubscriptionDto | null>(null);
  const [accessPreviewUserId, setAccessPreviewUserId] = useState<string>("");
  const [userSearchQuery, setUserSearchQuery] = useState<string>("");
  const [userPickerOpen, setUserPickerOpen] = useState(false);
  const [previewUserAutoSelected, setPreviewUserAutoSelected] = useState(false);
  const [accessPreview, setAccessPreview] = useState<SubscriptionFeatureAccess | null>(null);
  const [accessPreviewState, setAccessPreviewState] = useState<LoadState>("idle");
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
  const previewUsers = users ?? [];
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
  const quotaAudits = (subscriptionAudits?.content ?? []).filter((audit) => ["SUBSCRIPTION_UPDATE", "AI_QUOTA_RESET", "AI_QUOTA_ADDON_GRANT"].includes(audit.actionType ?? ""));


  const title = {
    overview: "Subscription control center",
    features: "Plan feature matrix",
    mapping: "RevenueCat product mapping",
    entitlements: "Entitlement snapshot policy",
    access: "User access preview",
    aiQuotas: "AI quota and add-on mapping"
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
  function updateSubscriptionForm(key: keyof typeof subscriptionForm, value: string | boolean) {
    if (key === "planType" && typeof value === "string") {
      setSubscriptionForm((current) => ({
        ...current,
        planType: value,
        billingPeriod: value === "FREE" ? "NONE" : current.billingPeriod === "NONE" ? "MONTHLY" : current.billingPeriod,
        aiMonthlyQuota: String(defaultAiQuota(value)),
        aiUsedThisPeriod: value === "FREE" ? "0" : current.aiUsedThisPeriod,
        autoRenew: value !== "FREE"
      }));
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
    setSubscriptionActionState("loading");
    onError(null);
    try {
      const response = await request<SubscriptionDto>(`/api/v1/admin/subscriptions/users/${selectedUserId}`, {
        method: "PATCH",
        body: {
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
        }
      });
      setSubscriptionResult(response);
      await refreshSelectedAccess(String(selectedUserId));
      await reloadSubscriptionAudits();
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
      const response = await request<SubscriptionDto>(`/api/v1/admin/subscriptions/users/${selectedUserId}/ai-quota/reset`, { method: "POST" });
      setSubscriptionResult(response);
      await refreshSelectedAccess(String(selectedUserId));
      await reloadSubscriptionAudits();
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
      const response = await request<SubscriptionDto>(`/api/v1/admin/subscriptions/users/${selectedUserId}/ai-quota/addon`, {
        method: "POST",
        body: {
          amount: parsePositiveInt(addonForm.amount),
          validityDays: parsePositiveInt(addonForm.validityDays),
          note: addonForm.note.trim() || null
        }
      });
      setSubscriptionResult(response);
      await refreshSelectedAccess(String(selectedUserId));
      await reloadSubscriptionAudits();
      setSubscriptionActionState("ready");
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
    onError(null);
    try {
      await request<FeatureMatrixItem>(`/api/v1/admin/subscriptions/features/${item.planType}/${item.feature}`, {
        method: "PUT",
        body: {
          enabled,
          aiCreditCost: item.aiCreditCost ?? 1,
          effectiveFrom: item.effectiveFrom || todayIsoDate()
        }
      });
      await reload();
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
  return (
    <div className="stack">
      <SectionToolbar title={title} state={combineStates([state, pricingState, revenueCatState, usersState, subscriptionAuditState, subscriptionActionState])} onReload={() => { void reload(); void reloadPricing(); }}>
        <button className="ghost-button" onClick={reloadRevenueCat} type="button">Reload RevenueCat</button>
      </SectionToolbar>

      {(mode === "overview" || mode === "entitlements") && <div className="subscription-hero">
        <div>
          <p className="eyebrow">Product policy</p>
          <h2>Plan rules for new purchases and renewals.</h2>
          <p>Existing entitlement snapshots remain valid until the active billing period ends.</p>
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

      {mode === "features" && <Panel title="Plan feature matrix">
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
        <div className="user-access-top-row">
        <Panel title="Select user">
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
          <div className="subscription-note access-preview-note">
            <strong>Shows the effective access returned by backend policy.</strong>
            <span>If feature matrix and this preview differ, the active entitlement snapshot or subscription state is controlling the user.</span>
          </div>
        </Panel>

        <Panel title="Admin assignment">
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
              <DatePickerButton label="End date" min={subscriptionForm.startDate || undefined} value={subscriptionForm.endDate} onChange={(value) => updateSubscriptionForm("endDate", value)} />
            </label>
            <label className="checkbox-field">
              <input checked={subscriptionForm.autoRenew} onChange={(event) => updateSubscriptionForm("autoRenew", event.target.checked)} type="checkbox" />
              Auto renew
            </label>
          </div>
          <div className="subscription-note access-preview-note">
            <strong>Manual admin changes create an entitlement snapshot for this user.</strong>
            <span>Use this for local testing, support corrections, beta access, or manually granting PLUS/PRO before store integration is complete.</span>
          </div>
          <div className="admin-subscription-actions">
            <button className="primary-button" disabled={!selectedUserId || subscriptionActionState === "loading"} onClick={applySubscriptionUpdate} type="button">Apply subscription</button>
            <button className="ghost-button" disabled={!selectedUserId || subscriptionActionState === "loading"} onClick={resetSelectedAiQuota} type="button">Reset used quota</button>
          </div>
        </Panel>
        </div>
        <Panel title="Resolved user access">
          <div className="quota-summary-strip access-summary-strip">
            <div>
              <span>Selected user</span>
              <strong title={selectedPreviewUser?.email ?? ""}>{selectedPreviewUser?.email ?? "-"}</strong>
              <small>{selectedPreviewUser?.role ?? "No user selected"}</small>
            </div>
            <div>
              <span>Resolved plan</span>
              <strong>{accessPreview?.planType ?? accessPreview?.plan ?? "-"}</strong>
              <small>Preview state: {accessPreviewState}</small>
            </div>
            <div>
              <span>Active entitlement</span>
              <strong>{accessPreview?.activeEntitlement ? "Yes" : "No"}</strong>
              <small>Snapshot-aware backend result</small>
            </div>
            <div>
              <span>AI remaining</span>
              <strong>{formatValue(accessPreview?.aiRemainingThisPeriod)}</strong>
              <small>{formatValue(accessPreview?.aiMonthlyQuota)} base + {formatValue(accessPreview?.aiAddonQuota)} add-on</small>
            </div>
          </div>
          <div className="access-feature-grid">
            {(uniqueFeatures(features).length ? uniqueFeatures(features) : FEATURE_ORDER).map((feature) => {
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
          </div>
        </Panel>
      </div>}
      {(mode === "mapping" || mode === "aiQuotas") && <div className={mode === "aiQuotas" ? "subscription-ops-grid ai-quota-layout" : "subscription-ops-grid"}>
        {mode === "mapping" && (
        <Panel title="RevenueCat configuration">
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
        {mode === "mapping" && <Panel title="Product mapping">
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
        {mode === "aiQuotas" && <>
          <Panel title="User plan and AI quota control">
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
                <DatePickerButton label="End date" min={subscriptionForm.startDate || undefined} value={subscriptionForm.endDate} onChange={(value) => updateSubscriptionForm("endDate", value)} />
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

          <Panel title="Grant one-off add-on quota">
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

          <Panel title="AI add-on product mapping">
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
          <Panel title="Recent subscription and quota changes">
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
    </div>
  );
}

function AiCreditCostPolicyPanel({ policies, savingKey, onSave }: {
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
function AiReviewView({ onError, targetContext, onClearTarget }: { onError: (message: string | null) => void; targetContext?: AdminTargetContext | null; onClearTarget?: () => void }) {
  const [requestType, setRequestType] = useState("");
  const [status, setStatus] = useState("");
  const [refundableOnly, setRefundableOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(25);
  const [summaryWindowHours, setSummaryWindowHours] = useState(24);
  const [smokeState, setSmokeState] = useState<LoadState>("idle");
  const [refundState, setRefundState] = useState<LoadState>("idle");
  const [smokeResult, setSmokeResult] = useState<string | null>(null);
  const [refundDraft, setRefundDraft] = useState<{ item: AiMealDraft; amount: string; reason: string } | null>(null);
  const [refundResult, setRefundResult] = useState<AiQuotaRefundResponse | null>(null);
  const path = buildAiOperationsPath({ requestType, status, refundableOnly, page, size: pageSize });
  const summaryPath = `/api/v1/admin/ai/requests/summary?windowHours=${summaryWindowHours}`;
  const { data, state, reload } = useEndpoint<PageResponse<AiMealDraft>>(path, onError);
  const { data: summary, state: summaryState, reload: reloadSummary } = useEndpoint<AiMonitoringSummary>(summaryPath, onError);
  const rows = data?.content ?? [];
  const focusedRequestId = targetContext?.targetType === "AI_REQUEST" ? targetContext.targetId : undefined;

  useEffect(() => {
    if (targetContext?.targetType !== "AI_REQUEST" || !targetContext.targetId) return;
    setStatus("");
    setRefundableOnly(false);
    setPage(0);
  }, [targetContext?.targetType, targetContext?.targetId]);

  function resetFilters() {
    setRequestType("");
    setStatus("");
    setRefundableOnly(false);
    setPage(0);
  }

  async function runProviderSmoke() {
    setSmokeState("loading");
    setSmokeResult(null);
    try {
      const result = await request<unknown>("/api/v1/admin/system/ai-provider/smoke", { method: "POST" });
      setSmokeResult(JSON.stringify(result, null, 2));
      setSmokeState("ready");
    } catch (error) {
      setSmokeState("error");
      onError(formatRequestError(error));
    }
  }

  async function refundSelectedAiQuota(event: FormEvent) {
    event.preventDefault();
    if (!refundDraft) return;
    const requestId = aiRequestId(refundDraft.item);
    const amount = Number(refundDraft.amount);
    const reason = refundDraft.reason.trim();
    const refundableAmount = safeNumber(refundDraft.item.refundableAmount);
    if (!requestId || !Number.isFinite(amount) || amount <= 0 || amount > refundableAmount || !reason) {
      onError("Refund amount must be positive, must not exceed refundable amount, and reason is required.");
      return;
    }
    setRefundState("loading");
    try {
      const result = await request<AiQuotaRefundResponse>(`/api/v1/admin/ai/meal-drafts/${requestId}/quota-refund`, {
        method: "POST",
        body: { amount, reason }
      });
      setRefundResult(result);
      setRefundDraft(null);
      setRefundState("ready");
      await reload();
      await reloadSummary();
    } catch (error) {
      setRefundState("error");
      onError(formatRequestError(error));
      await reload();
      await reloadSummary();
    }
  }


  return (
    <div className="stack">
      <SectionToolbar title="AI request operations" state={combineStates([state, summaryState, smokeState, refundState])} onReload={() => { void reload(); void reloadSummary(); }}>
        <button className="ghost-button" type="button" onClick={resetFilters}>Reset filters</button>
        <button className="primary-button" type="button" disabled={smokeState === "loading"} onClick={runProviderSmoke}>Provider smoke test</button>
      </SectionToolbar>
      {targetContext && <TargetContextBanner context={targetContext} onClear={onClearTarget} />}

      <Panel title="AI monitoring summary">
        <div className="ai-monitoring-toolbar">
          <div>
            <strong>{summaryWindowLabel(summaryWindowHours)}</strong>
            <small>Generated {formatDate(summary?.generatedAt)} | Window start {formatDate(summary?.windowStart)}</small>
          </div>
          <div className="segmented-control" role="group" aria-label="AI monitoring window">
            {[24, 168, 744].map((hours) => (
              <button className={summaryWindowHours === hours ? "active" : ""} key={hours} type="button" onClick={() => setSummaryWindowHours(hours)}>{summaryWindowLabel(hours)}</button>
            ))}
          </div>
        </div>
        <div className="metric-grid compact-grid">
          <MetricCard label="Total requests" value={formatValue(summary?.totalRequests)} hint="All controlled AI requests" />
          <MetricCard label="Drafts created" value={formatValue(summary?.draftCreated)} hint="AI produced reviewable output" />
          <MetricCard label="Confirmed" value={formatValue(summary?.confirmed)} hint="User accepted the result" />
          <MetricCard label="Rejected" value={formatValue(summary?.rejected)} hint="User rejected the result" />
          <MetricCard label="Failures" value={formatValue(summary?.failed)} hint={`${formatFailureRate(summary?.failureRate)} failure rate`} />
          <MetricCard label="Quota consumed" value={formatValue(summary?.quotaConsumedAmount)} hint={`${formatValue(summary?.quotaRefundedAmount)} refunded`} />
          <MetricCard label="Tokens" value={formatValue(summary?.totalTokens)} hint={`${formatValue(summary?.promptTokens)} input / ${formatValue(summary?.completionTokens)} output`} />
          <MetricCard label="Estimated cost" value={formatCurrencyBreakdown(summary?.estimatedCostByCurrency)} hint="Costs are separated by currency" />
        </div>
      </Panel>

      <div className="ai-monitoring-grid">
        <Panel title="Provider, model, and prompt version">
          <DataTable
            columns={["Provider", "Model", "Prompt", "Requests", "Tokens", "Cost", "Quota"]}
            rows={(summary?.providerModels ?? []).map((item) => [
              item.provider ?? "-",
              item.model ?? "-",
              item.promptVersion ?? "-",
              formatValue(item.requestCount),
              `${formatValue(item.promptTokens)} in / ${formatValue(item.completionTokens)} out / ${formatValue(item.totalTokens)} total`,
              formatAiCostAmount(item.estimatedCost, item.costCurrency),
              `${formatValue(item.quotaConsumedAmount)} used / ${formatValue(item.quotaRefundedAmount)} refunded`
            ])}
            empty="No provider/model metrics returned for this window."
          />
        </Panel>
        <Panel title="Request type and status">
          <DataTable
            columns={["Request type", "Status", "Count", "Tokens", "Quota"]}
            rows={(summary?.requestStatuses ?? []).map((item) => [
              humanizeAiRequestType(item.requestType),
              <Badge value={item.status ?? "-"} tone={aiStatusTone(item.status)} />,
              formatValue(item.requestCount),
              formatValue(item.totalTokens),
              `${formatValue(item.quotaConsumedAmount)} used / ${formatValue(item.quotaRefundedAmount)} refunded`
            ])}
            empty="No request status metrics returned for this window."
          />
        </Panel>
      </div>

      <Panel title="Request filters">
        <div className="review-filter-grid ai-review-filter-grid">
          <label>
            Request type
            <select value={requestType} onChange={(event) => { setRequestType(event.target.value); setPage(0); }}>
              <option value="">All request types</option>
              {AI_REQUEST_TYPES.map((item) => <option key={item} value={item}>{humanizeAiRequestType(item)}</option>)}
            </select>
          </label>
          <label>
            Status
            <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
              <option value="">All statuses</option>
              {AI_REQUEST_STATUSES.map((item) => <option key={item} value={item}>{shortFeature(item)}</option>)}
            </select>
          </label>
          <label className="inline-check">
            <input checked={refundableOnly} onChange={(event) => { setRefundableOnly(event.target.checked); setPage(0); }} type="checkbox" />
            Refundable rejected requests only
          </label>
        </div>
      </Panel>
      {refundResult && <Panel title="Last quota refund result">
        <div className="ai-refund-result-grid">
          <DetailItem label="Request" value={refundResult.requestId} />
          <DetailItem label="Refunded now" value={refundResult.refundedNow} />
          <DetailItem label="Total refunded" value={refundResult.quotaRefundedAmount} />
          <DetailItem label="Refunded by" value={refundResult.quotaRefundedBy} />
          <DetailItem label="Refunded at" value={formatDate(refundResult.quotaRefundedAt)} />
          <DetailItem label="Subscription remaining" value={refundResult.subscription?.aiRemainingThisPeriod} />
        </div>
      </Panel>}
      {smokeResult && <Panel title="AI provider smoke result">
        <pre className="audit-value-block">{smokeResult}</pre>
      </Panel>}
      <DataTable
        columns={["Request", "User", "Type", "Status", "Provider", "Performance", "Cost", "Quota", "Rejection", "Refund", "Actions"]}
        rows={rows.map((item) => {
          const refundableAmount = safeNumber(item.refundableAmount);
          const canRefund = canRefundAiRequest(item);
          return [
            <TargetAwareValue value={aiRequestId(item) ?? "-"} focused={isTargetMatch(focusedRequestId, aiRequestId(item))} />,
            <div className="entity-cell"><strong>{item.userEmail ?? "-"}</strong><small>User #{formatValue(item.userId)}</small></div>,
            humanizeAiRequestType(item.requestType),
            <Badge value={item.status} tone={aiStatusTone(item.status)} />,
            <div className="entity-cell"><strong>{formatValue(item.provider)} {formatValue(item.model)}</strong><small>{item.promptVersion ?? "No prompt version"}</small></div>,
            <div className="entity-cell"><strong>{item.latencyMs ? `${formatValue(item.latencyMs)} ms` : "-"}</strong><small>{formatValue(item.totalTokens)} tokens</small></div>,
            formatAiCost(item),
            `${formatValue(item.quotaConsumedAmount ?? item.quotaConsumed ?? 0)} used / ${formatValue(item.quotaRefundedAmount ?? item.quotaRefunded ?? 0)} refunded / ${formatValue(refundableAmount)} refundable`,
            <div className="ai-feedback-cell"><strong>{shortFeature(item.rejectionReason)}</strong><small>{item.rejectionFeedback ?? "-"}</small></div>,
            <div className="ai-feedback-cell"><strong>{item.quotaRefundedBy ?? "-"}</strong><small>{item.quotaRefundedAt ? `${formatDate(item.quotaRefundedAt)} | ${item.quotaRefundReason ?? ""}` : "No refund recorded"}</small></div>,
            <button className="ghost-button" type="button" disabled={!canRefund || refundState === "loading"} onClick={() => setRefundDraft({ item, amount: String(Math.max(1, refundableAmount)), reason: "" })}>Refund quota</button>
          ];
        })}
        empty="No AI requests returned."
      />
      <PaginationControls
        page={data?.page ?? page}
        pageSize={data?.size ?? pageSize}
        totalElements={data?.totalElements ?? rows.length}
        totalPages={data?.totalPages ?? 1}
        first={Boolean(data?.first)}
        last={Boolean(data?.last)}
        onPageChange={setPage}
        onPageSizeChange={(size) => { setPageSize(size); setPage(0); }}
      />
      {refundDraft && <AiQuotaRefundModal
        draft={refundDraft}
        busy={refundState === "loading"}
        onChange={setRefundDraft}
        onClose={() => setRefundDraft(null)}
        onSubmit={refundSelectedAiQuota}
      />}
    </div>
  );
}

function AiQuotaRefundModal({
  busy,
  draft,
  onChange,
  onClose,
  onSubmit
}: {
  busy: boolean;
  draft: { item: AiMealDraft; amount: string; reason: string };
  onChange: (draft: { item: AiMealDraft; amount: string; reason: string }) => void;
  onClose: () => void;
  onSubmit: (event: FormEvent) => void;
}) {
  const item = draft.item;
  const refundableAmount = safeNumber(item.refundableAmount);
  const amount = Number(draft.amount);
  const invalid = !Number.isFinite(amount) || amount <= 0 || amount > refundableAmount || !draft.reason.trim();
  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" onClick={onClose}>
      <form className="modal-card compact ai-refund-modal" onSubmit={onSubmit} onClick={(event) => event.stopPropagation()}>
        <div className="modal-header">
          <div>
            <span>AI QUOTA REFUND</span>
            <h2>Request #{aiRequestId(item) ?? "-"}</h2>
            <p>{item.userEmail ?? `User #${formatValue(item.userId)}`}</p>
          </div>
          <button className="modal-icon-close" type="button" onClick={onClose} aria-label="Close refund modal">x</button>
        </div>
        <div className="modal-body ai-refund-body">
          <div className="ai-refund-overview">
            <div className="ai-refund-summary">
              <DetailItem label="Status" value={shortFeature(item.status)} />
              <DetailItem label="Consumed" value={formatValue(item.quotaConsumedAmount ?? item.quotaConsumed ?? 0)} />
              <DetailItem label="Already refunded" value={item.quotaRefundedAmount ?? item.quotaRefunded ?? 0} />
              <DetailItem label="Refundable" value={refundableAmount} />
            </div>
            <label className="ai-refund-amount-field">
              Refund amount
              <input min="1" max={refundableAmount || undefined} type="number" value={draft.amount} onChange={(event) => onChange({ ...draft, amount: event.target.value })} required />
              <small>Available refundable quota: {formatValue(refundableAmount)}</small>
            </label>
          </div>
          <div className="ai-refund-user-rejection">
            <span>User rejection</span>
            <strong>{shortFeature(item.rejectionReason) || "No reason selected"}</strong>
            <p>{item.rejectionFeedback || "No user feedback submitted."}</p>
          </div>
          <div className="ai-refund-form-grid">
            <label className="ai-refund-reason-field">
              Refund reason
              <textarea maxLength={500} value={draft.reason} onChange={(event) => onChange({ ...draft, reason: event.target.value })} placeholder="Why this AI result deserves quota refund" required />
            </label>
            <aside className="ai-refund-warning">Backend enforces request-level refund limits. If this request is stale, the action returns a safe validation error and the queue refreshes.</aside>
          </div>
        </div>
        <div className="modal-actions padded-actions ai-refund-actions">
          <button className="ghost-button" type="button" onClick={onClose}>Cancel</button>
          <button className="primary-button" type="submit" disabled={busy || invalid}>Refund quota</button>
        </div>
      </form>
    </div>
  );
}
function SubscriptionEventsView({ onError, targetContext, onClearTarget }: { onError: (message: string | null) => void; targetContext?: AdminTargetContext | null; onClearTarget?: () => void }) {
  const [status, setStatus] = useState("");
  const [eventType, setEventType] = useState("");
  const [productId, setProductId] = useState("");
  const [userId, setUserId] = useState("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(25);
  const [selected, setSelected] = useState<SubscriptionProviderEvent | null>(null);
  const [detailState, setDetailState] = useState<LoadState>("idle");
  const [retryState, setRetryState] = useState<LoadState>("idle");
  const path = buildSubscriptionEventsPath({ status, eventType, productId, userId, page, size: pageSize });
  const { data, state, reload } = useEndpoint<SubscriptionProviderEventPage>(path, onError);
  const rows = data?.content ?? [];
  const focusedEventId = targetContext?.targetType === "SUBSCRIPTION_PROVIDER_EVENT" ? targetContext.targetId : undefined;

  useEffect(() => {
    if (targetContext?.targetType !== "SUBSCRIPTION_PROVIDER_EVENT" || !targetContext.targetId) return;
    setStatus("");
    setEventType("");
    setProductId("");
    setUserId("");
    setPage(0);
  }, [targetContext?.targetType, targetContext?.targetId]);

  function resetFilters() {
    setStatus("");
    setEventType("");
    setProductId("");
    setUserId("");
    setPage(0);
  }

  async function openDetail(item: SubscriptionProviderEvent) {
    if (!item.id) return;
    setSelected(item);
    setDetailState("loading");
    try {
      const detail = await request<SubscriptionProviderEvent>(`/api/v1/admin/subscription-events/${item.id}`);
      setSelected(detail);
      setDetailState("ready");
    } catch (error) {
      setDetailState("error");
      onError(formatRequestError(error));
    }
  }

  async function retrySelectedEvent() {
    if (!selected?.id) return;
    setRetryState("loading");
    try {
      await request<unknown>(`/api/v1/admin/subscription-events/${selected.id}/retry`, { method: "POST" });
      setRetryState("ready");
      await reload();
      await openDetail(selected);
    } catch (error) {
      setRetryState("error");
      onError(formatRequestError(error));
    }
  }


  return (
    <div className="stack">
      <SectionToolbar title="Subscription provider events" state={combineStates([state, detailState, retryState])} onReload={reload}>
        <button className="ghost-button" onClick={resetFilters} type="button">Reset filters</button>
      </SectionToolbar>
      {targetContext && <TargetContextBanner context={targetContext} onClear={onClearTarget} />}
      <Panel title="Event filters">
        <div className="review-filter-grid">
          <label>
            Status
            <select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
              <option value="">All statuses</option>
              {SUBSCRIPTION_EVENT_STATUSES.map((item) => <option key={item} value={item}>{shortFeature(item)}</option>)}
            </select>
          </label>
          <label>
            Event type
            <input value={eventType} onChange={(event) => { setEventType(event.target.value); setPage(0); }} placeholder="INITIAL_PURCHASE" />
          </label>
          <label>
            Product id
            <input value={productId} onChange={(event) => { setProductId(event.target.value); setPage(0); }} placeholder="grun_pro_monthly" />
          </label>
          <label>
            User id
            <input value={userId} onChange={(event) => { setUserId(event.target.value.replace(/[^0-9]/g, "")); setPage(0); }} placeholder="123" />
          </label>
        </div>
      </Panel>
      <DataTable
        columns={["ID", "Provider", "Event", "Product", "User", "Status", "Received", "Processed"]}
        rows={rows.map((item) => [
          <TargetAwareValue value={item.id ?? "-"} focused={isTargetMatch(focusedEventId, item.id)} />,
          item.provider ?? "-",
          item.eventType ?? item.providerEventId ?? "-",
          item.productId ?? "-",
          item.userEmail ?? item.userId ?? item.providerAppUserId ?? "-",
          <Badge value={item.status} tone={subscriptionEventTone(item.status)} />,
          formatDate(item.receivedAt),
          formatDate(item.processedAt)
        ])}
        rowData={rows}
        onRowClick={openDetail}
        empty="No subscription provider events returned."
      />
      <PaginationControls
        page={data?.page ?? page}
        pageSize={data?.size ?? pageSize}
        totalElements={data?.totalElements ?? rows.length}
        totalPages={data?.totalPages ?? 1}
        first={Boolean(data?.first)}
        last={Boolean(data?.last)}
        onPageChange={setPage}
        onPageSizeChange={(size) => { setPageSize(size); setPage(0); }}
      />
      {selected && <SubscriptionEventModal event={selected} retryState={retryState} onClose={() => setSelected(null)} onRetry={retrySelectedEvent} />}
    </div>
  );
}

function RetentionPoliciesView({ onError }: { onError: (message: string | null) => void }) {
  const { data, state, reload } = useEndpoint<RetentionPolicy[]>("/api/v1/admin/legal/retention-policies", onError);
  const [selected, setSelected] = useState<RetentionPolicy | null>(null);
  const [draft, setDraft] = useState({ retentionDays: "", legalBasis: "", description: "", active: true });
  const [saveState, setSaveState] = useState<LoadState>("idle");
  const rows = data ?? [];

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
        body: JSON.stringify({
          retentionDays: Number(draft.retentionDays),
          legalBasis: draft.legalBasis.trim(),
          description: draft.description.trim(),
          active: draft.active
        })
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
function GlobalSettingsView() {

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
type IntegrationMode = "overview" | "providers";

function IntegrationsView({ mode, onError }: { mode: IntegrationMode; onError: (message: string | null) => void }) {
  const { data: health, state: healthState, reload: reloadHealth } = useEndpoint<SystemHealth>("/api/v1/admin/system/health", onError);
  const { data: revenueCat, state: revenueCatState, reload: reloadRevenueCat } = useEndpoint<RevenueCatConfigStatus>("/api/v1/admin/revenuecat/config", onError);
  const { data: notifications, state: notificationState, reload: reloadNotifications } = useEndpoint<PageResponse<Notification>>("/api/v1/notifications?page=0&size=8", onError);
  const warnings = Array.isArray(health?.warnings) ? health.warnings.map(String) : [];
  const notificationRows = notifications?.content ?? [];
  const title = mode === "providers" ? "External providers" : "Integration control board";

  function reloadAll() {
    void reloadHealth();
    void reloadRevenueCat();
    void reloadNotifications();
  }

  const providerCards: IntegrationCardModel[] = [
    {
      title: "RevenueCat",
      status: revenueCat?.productionReady ? "Ready" : "Needs setup",
      tone: revenueCat?.productionReady ? "good" : "warn",
      description: "App Store / Google Play subscription bridge.",
      metrics: [
        ["Webhook auth", revenueCat?.webhookAuthorizationConfigured ? "Configured" : "Missing"],
        ["Strict mapping", revenueCat?.strictProductMapping ? "On" : "Off"],
        ["Metrics API", revenueCat?.apiEnabled && revenueCat?.apiSecretConfigured && revenueCat?.apiProjectConfigured ? "Ready" : "Disabled"],
        ["Failed events", formatValue(health?.failedRevenueCatEvents)]
      ]
    },
    {
      title: "Brevo Mail",
      status: readNumber(health, "systemAlertsLast24h") ? "Watch alerts" : "Operational",
      tone: readNumber(health, "systemAlertsLast24h") ? "warn" : "good",
      description: "Transactional mail delivery for verification, reset, and plan changes.",
      metrics: [
        ["Provider", "Brevo"],
        ["Cooldown", "30s / 2m / 5m"],
        ["Alerts 24h", formatValue(health?.systemAlertsLast24h)]
      ]
    },
    {
      title: "Open Food Facts",
      status: "Fallback source",
      tone: "neutral",
      description: "External product lookup when local catalog does not contain a barcode.",
      metrics: [
        ["Mode", "Local first"],
        ["Cache policy", "Persist after fetch"],
        ["Review flow", "Required"]
      ]
    },
    {
      title: "AI Provider",
      status: health?.aiEnabled ? "Enabled" : "Disabled",
      tone: health?.aiEnabled ? "good" : "neutral",
      description: "Meal/photo/voice AI features and quota-driven usage.",
      metrics: [
        ["Provider", formatValue(health?.aiProvider)],
        ["Model", formatValue(health?.aiModel)],
        ["Failures 24h", formatValue(health?.failedAiRequestsLast24h)]
      ]
    },
    {
      title: "AWS / Production",
      status: "Planned",
      tone: "neutral",
      description: "Future hosting, storage, logs, backup, and monitoring layer.",
      metrics: [
        ["Secrets", "Backend only"],
        ["Admin exposure", "Safe metadata"],
        ["Endpoint", "Not wired yet"]
      ]
    },
    {
      title: "Database",
      status: String(health?.databaseStatus ?? "-"),
      tone: health?.databaseStatus === "UP" ? "good" : "warn",
      description: "Primary PostgreSQL connectivity and runtime health.",
      metrics: [
        ["Latency", `${formatValue(health?.databaseLatencyMs)} ms`],
        ["App status", formatValue(health?.status)],
        ["Checked", formatDate(typeof health?.checkedAt === "string" ? health.checkedAt : undefined)]
      ]
    }
  ];


  return (
    <div className="stack">
      <SectionToolbar title={title} state={combineStates([healthState, revenueCatState, notificationState])} onReload={reloadAll} />
      <div className="integration-grid">
        {providerCards
          .filter((card) => card.title !== "RevenueCat")
          .map((card) => <IntegrationCard key={card.title} card={card} />)}
      </div>
      {mode === "overview" && <div className="ops-grid">
        <Panel title="Provider warnings">
          <div className="warning-list">
            {(revenueCat?.missingRequiredConfig ?? []).map((item) => <span key={`missing-${item}`}>RevenueCat missing: {item}</span>)}
            {(revenueCat?.warnings ?? []).map((item) => <span key={`rc-warning-${item}`}>RevenueCat warning: {item}</span>)}
            {warnings.map((item, index) => <span key={`health-warning-${index}`}>{item}</span>)}
            {!revenueCat?.missingRequiredConfig?.length && !revenueCat?.warnings?.length && !warnings.length && <span>No provider warning returned.</span>}
          </div>
        </Panel>
        <Panel title="Recent operational notifications">
          <MiniNotificationList notifications={notificationRows} />
        </Panel>
      </div>}
    </div>
  );
}

function MailOpsView({ onError, targetContext, onClearTarget }: { onError: (message: string | null) => void; targetContext?: AdminTargetContext | null; onClearTarget?: () => void }) {
  const { data: health, state: healthState, reload: reloadHealth } = useEndpoint<SystemHealth>("/api/v1/admin/system/health", onError);
  const { data: notifications, state: notificationState, reload: reloadNotifications } = useEndpoint<PageResponse<Notification>>("/api/v1/notifications?page=0&size=25&type=system", onError);
  const { data: mailMonitoring, state: mailState, reload: reloadMail } = useEndpoint<AdminMailMonitoring>("/api/v1/admin/mail/monitoring?days=7&limit=10", onError);
  const rows = notifications?.content ?? [];
  const mailRows = rows.filter((item) => {
    const text = `${item.type ?? ""} ${item.message ?? ""}`.toLowerCase();
    return text.includes("mail") || text.includes("email") || text.includes("brevo") || text.includes("verification") || text.includes("reset");
  });
  const alertCount = readNumber(health, "systemAlertsLast24h") ?? 0;
  const counters = Object.entries(mailMonitoring?.counters ?? {}).sort(([left], [right]) => left.localeCompare(right));
  const providerReady = mailMonitoring?.provider === "BREVO" && Boolean(mailMonitoring?.apiKeyConfigured);
  const providerHealthy = providerReady && Boolean(mailMonitoring?.providerReachable);
  const statusLabel = providerHealthy ? "Brevo reachable" : providerReady ? "Check provider" : "Setup required";
  const statusTone = providerHealthy ? "good" : providerReady || alertCount > 0 ? "warn" : "neutral";
  const requests = readCounter(mailMonitoring, "requests");
  const delivered = readCounter(mailMonitoring, "delivered");
  const opened = readCounter(mailMonitoring, "opened");
  const clicked = readCounter(mailMonitoring, "clicked");
  const hardBounces = readCounter(mailMonitoring, "hardBounces");
  const softBounces = readCounter(mailMonitoring, "softBounces");
  const blocked = readCounter(mailMonitoring, "blocked");
  const spamReports = readCounter(mailMonitoring, "spamReports");
  const unsubscribed = readCounter(mailMonitoring, "unsubscribed");
  const failed = hardBounces + softBounces + blocked;
  const funnelItems = [
    { label: "Requests", value: requests, percent: 100 },
    { label: "Delivered", value: delivered, percent: percent(delivered, requests) },
    { label: "Opened", value: opened, percent: percent(opened, requests) },
    { label: "Clicked", value: clicked, percent: percent(clicked, requests) }
  ];
  const issueItems = [
    { label: "Hard bounce", value: hardBounces },
    { label: "Soft bounce", value: softBounces },
    { label: "Blocked", value: blocked },
    { label: "Spam", value: spamReports },
    { label: "Unsubscribed", value: unsubscribed }
  ];

  function reloadAll() {
    void reloadHealth();
    void reloadNotifications();
    void reloadMail();
  }


  return (
    <div className="stack">
      <SectionToolbar title="Brevo mail operations" state={combineStates([healthState, notificationState, mailState])} onReload={reloadAll} />
      <div className="mail-hero">
        <div>
          <p className="eyebrow">Transactional delivery</p>
          <h2>Mail state is monitored without exposing provider secrets.</h2>
          <p>Verification and reset flows stay on backend config. Admin panel only shows delivery policy, alerts, and recent related notifications.</p>
        </div>
        <div className="mail-hero-status">
          <Badge value={statusLabel} tone={statusTone} />
          <span>{mailMonitoring?.statusMessage ?? "Backend monitoring endpoint is active."}</span>
        </div>
      </div>

      <Panel title="Monitoring overview">
        <div className="mail-monitor-grid">
          <MetricCard label="Provider" value={formatValue(mailMonitoring?.provider)} hint="Configured mail provider" />
          <MetricCard label="Reachability" value={mailMonitoring?.providerReachable ? "Online" : "Offline"} hint={formatValue(mailMonitoring?.providerBaseUrl)} />
          <MetricCard label="Delivered" value={formatValue(delivered)} hint={`${percent(delivered, requests)}% of requests`} />
          <MetricCard label="Failed" value={formatValue(failed)} hint={`${percent(failed, requests)}% of requests`} />
        </div>
        <div className="mail-chart-grid">
          <MailFunnelChart items={funnelItems} />
          <MailIssueChart items={issueItems} total={requests} />
        </div>
        {!counters.length && <EmptyState message="No Brevo counters returned." />}
      </Panel>

      <Panel title="Delivery policy">
        <div className="mail-policy-grid">
          <PolicyStep step="1" title="First resend" value="30 seconds" />
          <PolicyStep step="2" title="Second resend" value="2 minutes" />
          <PolicyStep step="3" title="Further resend" value="5 minutes" />
          <PolicyStep step="4" title="Token rule" value="Newest token active" />
        </div>
      </Panel>

      <div className="mail-diagnostics-grid">
        <Panel title="Mail related system notifications">
          <MiniNotificationList notifications={mailRows} />
        </Panel>
      </div>
    </div>
  );
}

function RevenueCatMonitoringView({ environment, onError }: { environment: "production" | "sandbox"; onError: (message: string | null) => void }) {
  const [range, setRange] = useState<RevenueCatRange>("28d");
  const [customStartDate, setCustomStartDate] = useState(() => dateInputDaysAgo(27));
  const [customEndDate, setCustomEndDate] = useState(() => dateInputDaysAgo(0));
  const chartPath = range === "custom"
    ? `/api/v1/admin/revenuecat/monitoring/charts?environment=${environment}&range=custom&startDate=${customStartDate}&endDate=${customEndDate}`
    : `/api/v1/admin/revenuecat/monitoring/charts?environment=${environment}&range=${range}`;
  const { data: overview, state: overviewState, reload: reloadOverview } = useEndpoint<RevenueCatMonitoringOverview>(`/api/v1/admin/revenuecat/monitoring/overview?environment=${environment}`, onError);
  const { data: charts, state: chartsState, reload: reloadCharts } = useEndpoint<RevenueCatMonitoringCharts>(chartPath, onError);
  function reloadAll() {
    void reloadOverview();
    void reloadCharts();
  }

  return (
    <div className="stack">
      <SectionToolbar title={`RevenueCat ${environment === "production" ? "production" : "sandbox"} monitoring`} state={combineStates([overviewState, chartsState])} onReload={reloadAll}>
        <div className="revenuecat-range-control">
          <div className="segmented-control compact">
          {(["7d", "28d", "90d", "custom"] as const).map((item) => (
            <button className={range === item ? "active" : ""} key={item} onClick={() => setRange(item)} type="button">{item}</button>
          ))}
          </div>
          {range === "custom" && (
            <div className="custom-range-fields">
              <DatePickerButton
                label="Start date"
                max={customEndDate}
                onChange={setCustomStartDate}
                value={customStartDate}
              />
              <span>to</span>
              <DatePickerButton
                label="End date"
                max={dateInputDaysAgo(0)}
                min={customStartDate}
                onChange={setCustomEndDate}
                value={customEndDate}
              />
            </div>
          )}
        </div>
      </SectionToolbar>
      <RevenueCatMonitoringPanel charts={charts} environment={environment} overview={overview} range={range} />
    </div>
  );
}

function RevenueCatMonitoringPanel({
  charts,
  environment,
  overview,
  range,
  setEnvironment
}: {
  charts: RevenueCatMonitoringCharts | null;
  environment: "production" | "sandbox";
  overview: RevenueCatMonitoringOverview | null;
  range: RevenueCatRange;
  setEnvironment?: (value: "production" | "sandbox") => void;
}) {
  const metricRows = overview?.metrics ?? [];
  const chartRows = sortRevenueCatCharts(charts?.charts?.length
    ? charts.charts
    : revenueCatPlaceholderCharts(metricRows, environment, overview?.currency ?? charts?.currency, charts?.statusMessage ?? overview?.statusMessage));
  const [selectedChartKey, setSelectedChartKey] = useState<string | null>(null);
  const selectedChart = chartRows.find((chart) => chartKey(chart) === selectedChartKey) ?? chartRows[0] ?? null;

  return (
    <div className="stack">
      <div className="revenuecat-monitor-hero">
        <div>
          <p className="eyebrow">RevenueCat analytics</p>
          <h2>Subscription revenue, customer, and trial signals.</h2>
          <p>{formatMonitoringStatus(overview?.statusMessage)} Chart range: {range}.</p>
        </div>
        {setEnvironment ? <div className="segmented-control">
          <button className={environment === "production" ? "active" : ""} onClick={() => setEnvironment("production")} type="button">
            Production
          </button>
          <button className={environment === "sandbox" ? "active" : ""} onClick={() => setEnvironment("sandbox")} type="button">
            Sandbox
          </button>
        </div> : <Badge value={environment === "production" ? "Production" : "Sandbox"} tone={environment === "production" ? "good" : "neutral"} />}
      </div>
      <div className="revenuecat-metric-grid">
        {metricRows.map((metric) => (
          <article className="revenuecat-metric-card" key={metric.key ?? metric.label}>
            <span>{metric.label ?? "-"}</span>
            <strong>{formatRevenueCatMetricClean(metric)}</strong>
            <small>{metric.description ?? "-"}</small>
          </article>
        ))}
        {!metricRows.length && <EmptyState message="No RevenueCat metric returned." />}
      </div>
      <RevenueCatChartWorkspace charts={chartRows} selectedChart={selectedChart} onSelect={setSelectedChartKey} />
    </div>
  );
}

function RevenueCatChartWorkspace({
  charts,
  onSelect,
  selectedChart
}: {
  charts: RevenueCatChart[];
  onSelect: (key: string) => void;
  selectedChart: RevenueCatChart | null;
}) {
  if (!charts.length || !selectedChart) {
    return <EmptyState message="No RevenueCat chart returned." />;
  }
  return (
    <article className="revenuecat-chart-workspace">
      <div className="revenuecat-metric-selector" aria-label="RevenueCat metric selector">
        {charts.map((chart) => {
          const summary = revenueCatChartSummary(chart);
          const active = chartKey(chart) === chartKey(selectedChart);
          return (
            <button className={active ? "active" : ""} key={chartKey(chart)} onClick={() => onSelect(chartKey(chart))} type="button">
              <span>{chart.label ?? chart.chartName ?? "-"}</span>
              <small>{summary.hasPoints ? `${summary.count} points` : "Waiting for data"}</small>
            </button>
          );
        })}
      </div>
      <RevenueCatAnalyticsChart chart={selectedChart} />
    </article>
  );
}

function DatePickerButton({
  label,
  max,
  min,
  onChange,
  value
}: {
  label: string;
  max?: string;
  min?: string;
  onChange: (value: string) => void;
  value: string;
}) {
  const [open, setOpen] = useState(false);
  const [viewDate, setViewDate] = useState(() => parseDateInput(value) ?? new Date());
  useEffect(() => {
    const parsed = parseDateInput(value);
    if (parsed) setViewDate(parsed);
  }, [value]);
  const days = calendarDays(viewDate);
  const selected = parseDateInput(value);
  const monthLabel = new Intl.DateTimeFormat("en-GB", { month: "long", year: "numeric" }).format(viewDate);
  return (
    <div className="admin-date-picker">
      <button className="admin-date-trigger" onClick={() => setOpen((current) => !current)} type="button">
        <span>{formatDateInputDisplay(value)}</span>
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
            {["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"].map((item) => <span key={item}>{item}</span>)}
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
            <button onClick={() => setViewDate(new Date())} type="button">Today</button>
            <button onClick={() => setOpen(false)} type="button">Close</button>
          </div>
        </div>
      )}
    </div>
  );
}

function revenueCatPlaceholderCharts(
  metrics: Array<{ key?: string; label?: string }>,
  environment: "production" | "sandbox",
  currency?: string,
  statusMessage?: string
): RevenueCatChart[] {
  return metrics
    .filter((metric) => Boolean(metric.key))
    .map((metric) => ({
      chartName: metric.key,
      label: metric.label,
      environment,
      currency,
      providerReachable: false,
      statusMessage: statusMessage ?? "RevenueCat chart points are not available yet.",
      points: []
    }));
}

function sortRevenueCatCharts(charts: RevenueCatChart[]): RevenueCatChart[] {
  const order = ["revenue", "actives", "trials", "mrr", "customers_new", "customers_active"];
  return [...charts].sort((left, right) => {
    const leftIndex = order.indexOf(chartKey(left));
    const rightIndex = order.indexOf(chartKey(right));
    return (leftIndex === -1 ? 99 : leftIndex) - (rightIndex === -1 ? 99 : rightIndex);
  });
}

function chartKey(chart: RevenueCatChart): string {
  return chart.chartName ?? chart.label ?? "chart";
}

function revenueCatChartSummary(chart: RevenueCatChart) {
  const values = (chart.points ?? [])
    .map((point) => Number(point.value ?? 0))
    .filter((value) => Number.isFinite(value));
  const latest = values[values.length - 1] ?? 0;
  const previous = values[values.length - 2] ?? latest;
  const total = values.reduce((sum, value) => sum + value, 0);
  return {
    average: values.length ? total / values.length : 0,
    count: values.length,
    delta: latest - previous,
    hasPoints: values.length > 0,
    latest,
    max: Math.max(...values, 0),
    min: Math.min(...values, 0)
  };
}

function RevenueCatAnalyticsChart({ chart }: { chart: RevenueCatChart }) {
  const points = (chart.points ?? []).filter((point) => Number.isFinite(Number(point.value ?? 0))).slice(-90);
  const isMoney = isRevenueCatMoneyChart(chart.chartName);
  const summary = revenueCatChartSummary(chart);
  const maxValue = summary.max;
  const minValue = summary.min;
  const latest = summary.latest;
  const delta = summary.delta;
  const average = summary.average;
  const hasTimeAxis = points.some((point) => isDateLike(point.date));
  const width = 960;
  const height = 340;
  const top = 28;
  const right = 34;
  const bottom = 48;
  const left = 76;
  const innerWidth = width - left - right;
  const innerHeight = height - top - bottom;
  const range = Math.max(maxValue - minValue, 1);
  const plotted = points.map((point, index, list) => {
    const x = left + (list.length <= 1 ? 0 : (index / (list.length - 1)) * innerWidth);
    const y = top + innerHeight - ((Number(point.value ?? 0) - minValue) / range) * innerHeight;
    return { ...point, x, y };
  });
  const linePath = plotted.map((point, index) => `${index === 0 ? "M" : "L"} ${point.x.toFixed(2)} ${point.y.toFixed(2)}`).join(" ");
  const areaPath = plotted.length
    ? `${linePath} L ${plotted[plotted.length - 1].x.toFixed(2)} ${top + innerHeight} L ${plotted[0].x.toFixed(2)} ${top + innerHeight} Z`
    : "";
  const yTicks = [1, 0.75, 0.5, 0.25, 0];
  const xTicks = plotted.filter((_, index, list) => {
    if (list.length <= 2) return true;
    const step = Math.max(1, Math.floor((list.length - 1) / 4));
    return index === 0 || index === list.length - 1 || index % step === 0;
  });

  return (
    <article className="revenuecat-chart-card">
      <div className="revenuecat-chart-heading">
        <div>
          <span>{chart.label ?? chart.chartName ?? "-"}</span>
          <small>{formatMonitoringStatus(chart.statusMessage)}</small>
        </div>
        <Badge value={chart.environment === "sandbox" ? "Sandbox" : "Production"} tone={chart.environment === "sandbox" ? "neutral" : "good"} />
      </div>
      <div className="revenuecat-chart-summary">
        <div>
          <span>Latest</span>
          <strong>{formatRevenueCatChartValue(latest, chart.currency, isMoney)}</strong>
        </div>
        <div>
          <span>Change</span>
          <strong className={delta >= 0 ? "positive" : "negative"}>{delta >= 0 ? "+" : ""}{formatRevenueCatChartValue(delta, chart.currency, isMoney)}</strong>
        </div>
        <div>
          <span>Average</span>
          <strong>{formatRevenueCatChartValue(average, chart.currency, isMoney)}</strong>
        </div>
        <div>
          <span>High</span>
          <strong>{formatRevenueCatChartValue(maxValue, chart.currency, isMoney)}</strong>
        </div>
      </div>
      {!points.length ? (
        <div className="revenuecat-chart-empty">
          <strong>No chart points yet</strong>
          <span>{formatMonitoringStatus(chart.statusMessage)}</span>
          <small>RevenueCat API configuration is checked, but this metric did not return drawable time-series data.</small>
        </div>
      ) : !hasTimeAxis ? (
        <div className="revenuecat-category-chart">
          {points.map((point) => {
            const value = Number(point.value ?? 0);
            const widthPercent = maxValue <= 0 ? 0 : Math.max(3, (value / maxValue) * 100);
            return (
              <div className="category-chart-row" key={point.date}>
                <span>{point.date ?? "-"}</span>
                <div><i style={{ width: `${widthPercent}%` }} /></div>
                <strong>{formatRevenueCatChartValue(value, chart.currency, isMoney)}</strong>
              </div>
            );
          })}
        </div>
      ) : (
        <div className="revenuecat-line-chart">
          <svg aria-label={`${chart.label ?? chart.chartName ?? "RevenueCat"} chart`} role="img" viewBox={`0 0 ${width} ${height}`}>
            {yTicks.map((tick) => {
              const y = top + innerHeight - tick * innerHeight;
              const value = minValue + range * tick;
              return (
                <g key={tick}>
                  <line className="chart-grid-line" x1={left} x2={width - right} y1={y} y2={y} />
                  <text className="chart-axis-label" x={left - 12} y={y + 4}>{formatRevenueCatChartValue(value, chart.currency, isMoney)}</text>
                </g>
              );
            })}
            {areaPath && <path className="chart-area-path" d={areaPath} />}
            {linePath && <path className="chart-line-path" d={linePath} />}
            {plotted.map((point, index) => (
              <circle className="chart-point" cx={point.x} cy={point.y} key={`${point.date ?? index}-${point.value ?? 0}`} r={index === plotted.length - 1 ? "4.5" : "2.5"}>
                <title>{`${formatChartDate(point.date)}: ${formatRevenueCatChartValue(Number(point.value ?? 0), chart.currency, isMoney)}`}</title>
              </circle>
            ))}
            {xTicks.map((point, index) => (
              <text className={`chart-x-label ${index === xTicks.length - 1 ? "end" : ""}`} key={`${point.date}-${point.x}`} x={point.x} y={height - 12}>
                {formatChartDate(point.date)}
              </text>
            ))}
          </svg>
        </div>
      )}
    </article>
  );
}

function MailFunnelChart({ items }: { items: Array<{ label: string; value: number; percent: number }> }) {
  return (
    <div className="mail-chart-card">
      <div className="mail-chart-heading">
        <strong>Delivery funnel</strong>
        <span>Last 7 days</span>
      </div>
      <div className="mail-funnel">
        {items.map((item) => (
          <div key={item.label}>
            <div>
              <span>{item.label}</span>
              <strong>{formatValue(item.value)}</strong>
            </div>
            <div className="mail-bar">
              <i style={{ width: `${item.percent}%` }} />
            </div>
            <small>{item.percent}%</small>
          </div>
        ))}
      </div>
    </div>
  );
}

function MailIssueChart({ items, total }: { items: Array<{ label: string; value: number }>; total: number }) {
  const issueTotal = items.reduce((sum, item) => sum + item.value, 0);
  return (
    <div className="mail-chart-card danger">
      <div className="mail-chart-heading">
        <strong>Issue breakdown</strong>
        <span>{formatValue(issueTotal)} issue events</span>
      </div>
      <div className="mail-issues">
        {items.map((item) => (
          <div key={item.label}>
            <span>{item.label}</span>
            <div className="mail-bar">
              <i style={{ width: `${percent(item.value, Math.max(total, issueTotal))}%` }} />
            </div>
            <strong>{formatValue(item.value)}</strong>
          </div>
        ))}
      </div>
    </div>
  );
}

function MailEventList({ events }: { events: NonNullable<AdminMailMonitoring["recentEvents"]> }) {
  const [eventFilter, setEventFilter] = useState("");

  if (!events.length) {
    return <EmptyState message="No Brevo event returned for this period." />;
  }

  const eventTypes = Array.from(new Set(events.map((event) => event.event).filter(Boolean))).sort();
  const filteredEvents = eventFilter ? events.filter((event) => event.event === eventFilter) : events;

  return (
    <div className="mail-event-manager">
      <div className="mail-event-toolbar">
        <div>
          <strong>{formatValue(filteredEvents.length)}</strong>
          <span>{eventFilter ? `${humanizeFeature(eventFilter)} events` : "events returned"}</span>
        </div>
        <label>
          Event type
          <select value={eventFilter} onChange={(event) => setEventFilter(event.target.value)}>
            <option value="">All events</option>
            {eventTypes.map((eventType) => (
              <option key={eventType} value={eventType}>{humanizeFeature(eventType)}</option>
            ))}
          </select>
        </label>
      </div>
      <div className="mail-event-list" role="table" aria-label="Recent Brevo events">
        <div className="mail-event-row head" role="row">
          <span>Event</span>
          <span>Recipient</span>
          <span>Subject</span>
          <span>Date</span>
        </div>
        {filteredEvents.map((event, index) => (
          <div className="mail-event-row" key={`${event.messageId ?? event.email ?? "mail-event"}-${index}`} role="row">
            <div>
              <Badge value={event.event} />
              {event.reason && <small>{event.reason}</small>}
            </div>
            <span>{formatValue(event.email)}</span>
            <div>
              <strong>{formatValue(event.subject)}</strong>
              {event.messageId && <small>{event.messageId}</small>}
            </div>
            <small>{formatValue(event.date)}</small>
          </div>
        ))}
        {!filteredEvents.length && <EmptyState message="No event matches this filter." />}
      </div>
    </div>
  );
}

function MailEventsView({ onError }: { onError: (message: string | null) => void }) {
  const { data, state, reload } = useEndpoint<AdminMailMonitoring>("/api/v1/admin/mail/monitoring?days=7&limit=50", onError);
  const events = data?.recentEvents ?? [];


  return (
    <div className="stack">
      <SectionToolbar title="Recent Brevo mail events" state={state} onReload={reload} />
      <div className="mail-events-hero">
        <div>
          <p className="eyebrow">Delivery audit</p>
          <h2>Review recent transactional mail events separately from Mail Ops overview.</h2>
          <p>This page is focused on provider events only. Monitoring counters and delivery policy remain on Mail Ops.</p>
        </div>
        <Badge value={data?.providerReachable ? "Brevo reachable" : "Safe proxy"} tone={data?.providerReachable ? "good" : "neutral"} />
      </div>
      <div className="metric-grid">
        <MetricCard label="Returned events" value={formatValue(events.length)} hint="Current provider response" />
        <MetricCard label="Provider" value={formatValue(data?.provider)} hint={data?.statusMessage ?? "Backend proxy"} />
        <MetricCard label="Checked" value={formatDate(data?.checkedAt)} hint={formatValue(data?.providerBaseUrl)} />
      </div>
      <Panel title="Recent Brevo events">
        <MailEventList events={events} />
      </Panel>
    </div>
  );
}

function BrevoSendersView({ onError }: { onError: (message: string | null) => void }) {
  const [selected, setSelected] = useState<AdminBrevoSender | null>(null);
  const [createForm, setCreateForm] = useState({ name: "", email: "" });
  const [editForm, setEditForm] = useState({ name: "", email: "" });
  const [saveState, setSaveState] = useState<LoadState>("idle");
  const [notice, setNotice] = useState<string | null>(null);
  const { data, state, reload } = useEndpoint<AdminBrevoSenderList>("/api/v1/admin/mail/brevo/senders", onError);
  const rows = data?.senders ?? [];

  function startNewSender() {
    setSelected(null);
    setCreateForm({ name: "", email: "" });
    setNotice(null);
  }

  function editSender(sender: AdminBrevoSender) {
    setSelected(sender);
    setEditForm({ name: sender.name ?? "", email: sender.email ?? "" });
    setNotice(null);
  }

  async function createSender(event: FormEvent) {
    event.preventDefault();
    setSaveState("loading");
    setNotice(null);
    try {
      await request<AdminBrevoSender>("/api/v1/admin/mail/brevo/senders", {
        method: "POST",
        body: createForm
      });
      setCreateForm({ name: "", email: "" });
      setNotice("Brevo sender created. Verification may be required in Brevo.");
      setSaveState("ready");
      void reload();
    } catch (error) {
      setSaveState("error");
      onError(formatRequestError(error));
    }
  }

  async function updateSender(event: FormEvent) {
    event.preventDefault();
    if (!selected?.id) return;
    setSaveState("loading");
    setNotice(null);
    try {
      await request<AdminBrevoSender>(`/api/v1/admin/mail/brevo/senders/${selected.id}`, {
        method: "PUT",
        body: editForm
      });
      setNotice("Brevo sender updated.");
      setSelected(null);
      setSaveState("ready");
      void reload();
    } catch (error) {
      setSaveState("error");
      onError(formatRequestError(error));
    }
  }


  return (
    <div className="stack">
      <SectionToolbar title="Brevo sender management" state={combineStates([state, saveState])} onReload={reload}>
        <button className="ghost-button" type="button" onClick={startNewSender}>New sender</button>
      </SectionToolbar>
      <div className="brevo-sender-hero">
        <div>
          <p className="eyebrow">Sender identities</p>
          <h2>Manage sender names and from addresses without exposing provider secrets.</h2>
          <p>Sender delete is intentionally not available here. Destructive sender removal stays inside Brevo.</p>
        </div>
        <Badge value={data?.providerReachable ? "Brevo reachable" : "Safe proxy"} tone={data?.providerReachable ? "good" : "neutral"} />
      </div>

      <Panel title="Senders">
        {data?.statusMessage && <p className="form-note">{data.statusMessage}</p>}
        <div className="brevo-sender-list">
          <div className="brevo-sender-row head">
            <span>Name</span>
            <span>Email</span>
            <span>Verification</span>
            <span>IPs</span>
          </div>
          {rows.map((sender) => (
            <button className="brevo-sender-row" key={sender.id ?? sender.email} type="button" onClick={() => editSender(sender)}>
              <strong>{formatValue(sender.name)}</strong>
              <span className="sender-email">{formatValue(sender.email)}</span>
              <span>{sender.dkimError || sender.spfError ? "DNS attention" : sender.active === false ? "Inactive" : "Looks ready"}</span>
              <small>{formatValue(sender.ips?.length ?? 0)}</small>
            </button>
          ))}
          {!rows.length && <EmptyState message="No Brevo sender returned." />}
        </div>
      </Panel>

      <Panel title="Create sender">
        {notice && <div className="form-notice">{notice}</div>}
        <form className="brevo-sender-form horizontal" onSubmit={createSender}>
          <label>
            Sender name
            <input value={createForm.name} onChange={(event) => setCreateForm({ ...createForm, name: event.target.value })} placeholder="GRun Support" required />
          </label>
          <label>
            Sender email
            <input value={createForm.email} onChange={(event) => setCreateForm({ ...createForm, email: event.target.value })} placeholder="support@grun.app" required />
          </label>
          <button className="primary-button" type="submit" disabled={saveState === "loading"}>Create sender</button>
        </form>
      </Panel>

      {selected && (
        <div className="modal-backdrop" role="presentation" onClick={() => setSelected(null)}>
          <div className="modal-card compact" role="dialog" aria-modal="true" aria-label="Update Brevo sender" onClick={(event) => event.stopPropagation()}>
            <header className="modal-header">
              <div>
                <span>Brevo sender</span>
                <h2>Update sender</h2>
              </div>
              <button className="icon-button" type="button" onClick={() => setSelected(null)}>x</button>
            </header>
            <form className="sender-update-form" onSubmit={updateSender}>
              {notice && <div className="form-notice">{notice}</div>}
              <div className="sender-update-summary">
                <div>
                  <span>Sender ID</span>
                  <strong>{formatValue(selected.id)}</strong>
                </div>
                <div>
                  <span>Status</span>
                  <strong>{selected.dkimError || selected.spfError ? "DNS attention" : selected.active === false ? "Inactive" : "Looks ready"}</strong>
                </div>
              </div>
              <div className="sender-update-fields">
                <label>
                  Sender name
                <input value={editForm.name} onChange={(event) => setEditForm({ ...editForm, name: event.target.value })} placeholder="GRun Support" required />
                </label>
                <label>
                  Sender email
                <input value={editForm.email} onChange={(event) => setEditForm({ ...editForm, email: event.target.value })} placeholder="support@grun.app" required />
                </label>
              </div>
              <p className="form-note">Changing sender email may require verification in Brevo.</p>
              <div className="modal-actions">
                <button className="ghost-button" type="button" onClick={() => setSelected(null)}>Cancel</button>
                <button className="primary-button" type="submit" disabled={saveState === "loading"}>Update sender</button>
              </div>
          </form>
          </div>
        </div>
      )}
    </div>
  );
}

type FoodOpsMode = "overview" | "imports" | "regions" | "quality";

function FoodOpsView({ mode, onError }: { mode: FoodOpsMode; onError: (message: string | null) => void }) {
  const { data: summary, state: summaryState, reload: reloadSummary } = useEndpoint<DashboardSummary>("/api/v1/admin/dashboard/summary", onError);
  const { data: products, state: productState, reload: reloadProducts } = useEndpoint<PageResponse<FoodProduct>>("/api/v1/admin/products/review?verificationStatus=RAW_IMPORTED&page=0&size=100", onError);
  const [suggestionStatus, setSuggestionStatus] = useState("OPEN");
  const [suggestionPage, setSuggestionPage] = useState(0);
  const [suggestionPageSize, setSuggestionPageSize] = useState(25);
  const [scanRegion, setScanRegion] = useState("");
  const [scanLimit, setScanLimit] = useState("250");
  const [forceRescan, setForceRescan] = useState(false);
  const [qualityActionState, setQualityActionState] = useState<LoadState>("idle");
  const [scanResult, setScanResult] = useState<ProductQualitySuggestionScanResult | null>(null);
  const [selectedScanRunDetail, setSelectedScanRunDetail] = useState<ProductQualityScanRunDetail | null>(null);
  const [aiValidationResult, setAiValidationResult] = useState<AdminProductQualityAiValidationResult | null>(null);
  const [selectedSuggestionIds, setSelectedSuggestionIds] = useState<number[]>([]);
  const [selectedQualitySuggestion, setSelectedQualitySuggestion] = useState<ProductQualitySuggestion | null>(null);
  const suggestionPath = buildProductQualitySuggestionPath({ status: suggestionStatus, page: suggestionPage, size: suggestionPageSize });
  const { data: suggestions, state: suggestionState, reload: reloadSuggestions } = useEndpoint<ProductQualitySuggestionPage>(suggestionPath, onError);
  const { data: scanRuns, state: scanRunState, reload: reloadScanRuns } = useEndpoint<ProductQualityScanRunPage>("/api/v1/admin/products/quality-suggestions/scan-runs?page=0&size=5", onError);
  const { data: aiSettings, state: aiSettingsState, reload: reloadAiSettings } = useEndpoint<ProductQualityAiSettings>("/api/v1/admin/products/quality-suggestions/ai-settings", onError);
  const [aiSettingsForm, setAiSettingsForm] = useState({ enabled: true, maxProductsPerRun: "25", dailyProductLimit: "250", monthlyProductLimit: "2000", forceRescanAllowed: true, adminNote: "" });
  const [aiSettingsSaveState, setAiSettingsSaveState] = useState<LoadState>("idle");
  const rows = products?.content ?? [];
  const suggestionRows = suggestions?.content ?? [];
  const scanRunRows = scanRuns?.content ?? [];
  const selectedOpenSuggestionIds = selectedSuggestionIds.filter((id) => suggestionRows.some((item) => item.id === id && item.status === "OPEN"));
  const byRegion = countBy(rows, (item) => item.marketRegion ?? "Unknown");
  const byImageStatus = countBy(rows, (item) => item.imageStatus ?? "Unknown");
  const byCatalogType = countBy(rows, (item) => item.catalogType ?? "Unknown");
  const localReadyPercent = percent(summary?.verifiedProducts, summary?.totalProducts);
  useEffect(() => {
    if (!aiSettings) return;
    setAiSettingsForm({
      enabled: aiSettings.enabled !== false,
      maxProductsPerRun: String(aiSettings.maxProductsPerRun ?? 25),
      dailyProductLimit: String(aiSettings.dailyProductLimit ?? 250),
      monthlyProductLimit: String(aiSettings.monthlyProductLimit ?? 2000),
      forceRescanAllowed: aiSettings.forceRescanAllowed !== false,
      adminNote: aiSettings.adminNote ?? ""
    });
  }, [aiSettings]);


  const title = {
    overview: "Food catalog operations",
    imports: "Food import jobs",
    regions: "Food market regions",
    quality: "Food quality rules"
  }[mode];

  function reloadAll() {
    void reloadSummary();
    void reloadProducts();
    void reloadSuggestions();
    void reloadScanRuns();
    void reloadAiSettings();
  }


  async function saveAiQualitySettings() {
    setAiSettingsSaveState("loading");
    try {
      await request<ProductQualityAiSettings>("/api/v1/admin/products/quality-suggestions/ai-settings", {
        method: "PATCH",
        body: {
          enabled: aiSettingsForm.enabled,
          maxProductsPerRun: Math.min(parsePositiveInt(aiSettingsForm.maxProductsPerRun), 25),
          dailyProductLimit: parsePositiveInt(aiSettingsForm.dailyProductLimit),
          monthlyProductLimit: parsePositiveInt(aiSettingsForm.monthlyProductLimit),
          forceRescanAllowed: aiSettingsForm.forceRescanAllowed,
          adminNote: aiSettingsForm.adminNote.trim()
        }
      });
      await reloadAiSettings();
      setAiSettingsSaveState("ready");
    } catch (error) {
      setAiSettingsSaveState("error");
      onError(formatRequestError(error));
    }
  }
  async function scanQualitySuggestions() {
    setQualityActionState("loading");
    try {
      const params = new URLSearchParams();
      if (scanRegion) params.set("region", scanRegion);
      params.set("limit", String(Math.min(parsePositiveInt(scanLimit), 500)));
      params.set("forceRescan", String(forceRescan));
      const result = await request<ProductQualitySuggestionScanResult>(`/api/v1/admin/products/quality-suggestions/scan?${params.toString()}`, { method: "POST" });
      setScanResult(result);
      setAiValidationResult(null);
      setSelectedSuggestionIds([]);
      setSuggestionStatus("OPEN");
      setSuggestionPage(0);
      await reloadSuggestions();
      await reloadScanRuns();
      setQualityActionState("ready");
    } catch (error) {
      setQualityActionState("error");
      onError(formatRequestError(error));
    }
  }


  async function openScanRunDetail(item: ProductQualityScanRun) {
    if (!item.id) return;
    setQualityActionState("loading");
    try {
      const detail = await request<ProductQualityScanRunDetail>(`/api/v1/admin/products/quality-suggestions/scan-runs/${item.id}`);
      setSelectedScanRunDetail(detail);
      setQualityActionState("ready");
    } catch (error) {
      setQualityActionState("error");
      onError(formatRequestError(error));
    }
  }
  async function reviewQualitySuggestion(item: ProductQualitySuggestion, action: "accept" | "reject") {
    if (!item.id) return;
    setQualityActionState("loading");
    try {
      await request<ProductQualitySuggestion>(`/api/v1/admin/products/quality-suggestions/${item.id}/${action}`, { method: "PATCH" });
      await reloadSuggestions();
      await reloadScanRuns();
      setSelectedSuggestionIds((current) => current.filter((id) => id !== item.id));
      setSelectedQualitySuggestion((current) => current?.id === item.id ? null : current);
      setQualityActionState("ready");
    } catch (error) {
      setQualityActionState("error");
      onError(formatRequestError(error));
    }
  }

  function toggleSuggestionSelection(item: ProductQualitySuggestion, checked: boolean) {
    if (!item.id || item.status !== "OPEN") return;
    setSelectedSuggestionIds((current) => {
      if (checked) return Array.from(new Set([...current, item.id as number]));
      return current.filter((id) => id !== item.id);
    });
  }

  async function validateSelectedSuggestionsWithAi() {
    if (selectedOpenSuggestionIds.length === 0) return;
    setQualityActionState("loading");
    try {
      const result = await request<AdminProductQualityAiValidationResult>("/api/v1/admin/products/quality-suggestions/ai-validate-selected", {
        method: "POST",
        body: {
          suggestionIds: selectedOpenSuggestionIds,
          limit: Math.min(selectedOpenSuggestionIds.length, 25),
          forceRescan
        }
      });
      setAiValidationResult(result);
      setScanResult(null);
      setSelectedSuggestionIds([]);
      setSuggestionStatus("OPEN");
      setSuggestionPage(0);
      await reloadSuggestions();
      await reloadScanRuns();
      setQualityActionState("ready");
    } catch (error) {
      setQualityActionState("error");
      onError(formatRequestError(error));
    }
  }


  return (
    <div className="stack">
      <SectionToolbar title={title} state={combineStates([summaryState, productState, suggestionState, scanRunState, aiSettingsState, qualityActionState, aiSettingsSaveState])} onReload={reloadAll} />
      {mode === "overview" && <div className="food-ops-hero">
        <div>
          <p className="eyebrow">Local catalog first</p>
          <h2>Import, review, verify, then serve from our database.</h2>
          <p>Open Food Facts remains a fallback source. Imported products should pass region, image, and nutrition review before being treated as high-quality catalog data.</p>
        </div>
        <div className="food-ops-score">
          <strong>{localReadyPercent}%</strong>
          <span>verified catalog ratio</span>
        </div>
      </div>}
      {mode === "overview" && <div className="metric-grid">
        <MetricCard label="Total products" value={formatValue(summary?.totalProducts)} hint="Current local catalog" />
        <MetricCard label="Verified" value={formatValue(summary?.verifiedProducts)} hint="Ready for user-facing use" />
        <MetricCard label="Pending review" value={formatValue(summary?.reviewQueueProducts)} hint="Needs admin attention" />
        <MetricCard label="Rejected" value={formatValue(summary?.rejectedProducts)} hint="Excluded from trusted catalog" />
        <MetricCard label="Sampled raw" value={formatValue(rows.length)} hint="Latest raw imports inspected here" />
      </div>}
      {mode === "imports" && <div className="metric-grid">
        <MetricCard label="Raw imported" value={formatValue(summary?.rawImportedProducts)} hint="Imported rows waiting for normalization" />
        <MetricCard label="Needs review" value={formatValue(summary?.needsReviewProducts)} hint="Rows explicitly marked for review" />
        <MetricCard label="Sampled raw" value={formatValue(rows.length)} hint="Latest raw imports inspected here" />
      </div>}
      {mode === "overview" && <div className="ops-grid three">
        <DistributionPanel title="Region sample" items={byRegion} />
        <DistributionPanel title="Image status sample" items={byImageStatus} />
        <DistributionPanel title="Catalog type sample" items={byCatalogType} />
      </div>}
      {mode === "regions" && <DistributionPanel title="Region sample" items={byRegion} />}
      {mode === "quality" && <div className="ops-grid">
        <DistributionPanel title="Image status sample" items={byImageStatus} />
        <DistributionPanel title="Catalog type sample" items={byCatalogType} />
      </div>}
      {mode === "imports" && <Panel title="Import pipeline controls to add next">
        <div className="roadmap-strip">
          <span>Bulk import job status</span>
          <span>Region import batches</span>
          <span>Duplicate barcode checks</span>
          <span>Import rollback preview</span>
          <span>OpenFoodFacts source profile</span>
        </div>
      </Panel>}
      {mode === "regions" && <Panel title="Region policy">
        <div className="roadmap-strip">
          {MARKET_REGIONS.map((item) => <span key={item}>{item}</span>)}
          <span>EU / UK_IE / TR import targeting</span>
          <span>User locale to market-group mapping</span>
        </div>
      </Panel>}
      {mode === "quality" && <Panel title="AI quality validation guardrails">
        <div className="ai-quality-settings-grid">
          <div className="ai-quality-quota-card">
            <span>Today</span>
            <strong>{formatValue(aiSettings?.remainingToday)} left</strong>
            <small>{formatValue(aiSettings?.usedToday)} used / {formatValue(aiSettings?.dailyProductLimit)} daily cap</small>
          </div>
          <div className="ai-quality-quota-card">
            <span>This month</span>
            <strong>{formatValue(aiSettings?.remainingThisMonth)} left</strong>
            <small>{formatValue(aiSettings?.usedThisMonth)} used / {formatValue(aiSettings?.monthlyProductLimit)} monthly cap</small>
          </div>
          <label className="inline-check ai-quality-toggle">
            <input checked={aiSettingsForm.enabled} onChange={(event) => setAiSettingsForm((current) => ({ ...current, enabled: event.target.checked }))} type="checkbox" />
            AI validation enabled
          </label>
          <label className="inline-check ai-quality-toggle">
            <input checked={aiSettingsForm.forceRescanAllowed} onChange={(event) => setAiSettingsForm((current) => ({ ...current, forceRescanAllowed: event.target.checked }))} type="checkbox" />
            Force rescan allowed
          </label>
          <label>
            Max products per run
            <input value={aiSettingsForm.maxProductsPerRun} onChange={(event) => setAiSettingsForm((current) => ({ ...current, maxProductsPerRun: event.target.value.replace(/[^0-9]/g, "") }))} />
            <small>Hard capped at 25 for cost and safety.</small>
          </label>
          <label>
            Daily product cap
            <input value={aiSettingsForm.dailyProductLimit} onChange={(event) => setAiSettingsForm((current) => ({ ...current, dailyProductLimit: event.target.value.replace(/[^0-9]/g, "") }))} />
          </label>
          <label>
            Monthly product cap
            <input value={aiSettingsForm.monthlyProductLimit} onChange={(event) => setAiSettingsForm((current) => ({ ...current, monthlyProductLimit: event.target.value.replace(/[^0-9]/g, "") }))} />
          </label>
          <label className="ai-quality-note-field">
            Admin note
            <textarea value={aiSettingsForm.adminNote} onChange={(event) => setAiSettingsForm((current) => ({ ...current, adminNote: event.target.value }))} maxLength={1000} placeholder="Internal note for why limits were changed." />
          </label>
          <div className="ai-quality-settings-actions">
            <span>Last update: {formatDate(aiSettings?.updatedAt)} by {aiSettings?.updatedBy ?? "-"}</span>
            <button className="primary-button" type="button" disabled={aiSettingsSaveState === "loading"} onClick={saveAiQualitySettings}>Save AI settings</button>
          </div>
        </div>
      </Panel>}      {mode === "quality" && <Panel title="Quality validation scan">
        <div className="review-filter-grid quality-scan-grid">
          <label>
            Region
            <select value={scanRegion} onChange={(event) => setScanRegion(event.target.value)}>
              <option value="">All regions</option>
              {MARKET_REGIONS.map((item) => <option key={item} value={item}>{item}</option>)}
            </select>
          </label>
          <label>
            Scan limit
            <input value={scanLimit} onChange={(event) => setScanLimit(event.target.value.replace(/[^0-9]/g, ""))} />
            <small>Manual scans are capped at 500 products. Previously validated products are skipped unless forced.</small>
          </label>
          <label className="quality-force-toggle">
            <input type="checkbox" checked={forceRescan} onChange={(event) => setForceRescan(event.target.checked)} />
            <span>Force rescan validated products</span>
          </label>
          <div className="quality-scan-actions"><button className="primary-button" type="button" disabled={qualityActionState === "loading"} onClick={scanQualitySuggestions}>Run scan</button></div>
        </div>
        {scanResult && <div className="metric-grid compact-grid">
          <MetricCard label="Run" value={formatValue(scanResult.scanRunId)} hint="Scan history id" />
          <MetricCard label="Scanned" value={formatValue(scanResult.scannedProducts)} hint={`Effective limit ${formatValue(scanResult.effectiveLimit)}`} />
          <MetricCard label="Created" value={formatValue(scanResult.createdSuggestions)} hint="New review suggestions" />
          <MetricCard label="Existing" value={formatValue(scanResult.skippedExistingSuggestions)} hint="Duplicate open suggestions skipped" />
          <MetricCard label="Validated" value={formatValue(scanResult.validatedProducts)} hint="No current rule issues found" />
        </div>}
      </Panel>}
      {mode === "quality" && <Panel title="Recent quality scan runs" className="quality-table-panel compact-empty-panel">
        <DataTable
          columns={["Run", "Source", "Trigger", "Region", "Limit", "Scanned", "Created", "Validated", "Status", "Actions"]}
          rows={scanRunRows.map((item) => [
            <div className="entity-cell"><strong>#{item.id ?? "-"}</strong><small>{formatDate(item.startedAt)}</small></div>,
            <Badge value={item.source ?? "-"} tone={item.source === "AI_ASSISTED" ? "warn" : "neutral"} />,
            <div className="badge-stack"><Badge value={item.triggerType ?? "-"} /><Badge value={item.forceRescan ? "FORCED" : "SKIP_VALIDATED"} tone={item.forceRescan ? "warn" : "good"} /></div>,
            item.marketRegion ?? "All",
            `${formatValue(item.effectiveLimit)} / requested ${formatValue(item.requestedLimit)}`,
            formatValue(item.scannedProducts),
            formatValue(item.createdSuggestions),
            formatValue(item.validatedProducts),
            <Badge value={item.status ?? "-"} tone={item.status === "COMPLETED" ? "good" : item.status === "FAILED" ? "danger" : "warn"} />,
            <button className="ghost-button" type="button" disabled={qualityActionState === "loading"} onClick={(event) => { event.stopPropagation(); void openScanRunDetail(item); }}>View</button>
          ])}
          empty="No quality scan runs yet."
        />
      </Panel>}
      {selectedScanRunDetail && <QualityScanRunDetailModal detail={selectedScanRunDetail} onClose={() => setSelectedScanRunDetail(null)} />}
      {mode === "quality" && <Panel title="Quality suggestion queue" className="quality-table-panel compact-empty-panel">
        <div className="quality-suggestion-toolbar">
          <div>
            <strong>{formatValue(selectedOpenSuggestionIds.length)} selected</strong>
            <small>AI validation is capped at 25 selected open suggestions.</small>
          </div>
          <button className="primary-button" type="button" disabled={qualityActionState === "loading" || selectedOpenSuggestionIds.length === 0} onClick={validateSelectedSuggestionsWithAi}>Validate selected with AI</button>
        </div>
        {aiValidationResult && <div className="metric-grid compact-grid">
          <MetricCard label="AI run" value={formatValue(aiValidationResult.scanRunId)} hint="AI-assisted scan history id" />
          <MetricCard label="Requested" value={formatValue(aiValidationResult.requestedProducts)} hint={`Effective limit ${formatValue(aiValidationResult.effectiveLimit)}`} />
          <MetricCard label="New suggestions" value={formatValue(aiValidationResult.createdSuggestions)} hint="Created for admin review" />
          <MetricCard label="Already open" value={formatValue(aiValidationResult.skippedExistingSuggestions)} hint="Duplicates skipped" />
          <MetricCard label="Validated" value={formatValue(aiValidationResult.validatedProducts)} hint="No AI issues found" />
        </div>}
        <div className="audit-filter-grid">
          <label>
            Status
            <select value={suggestionStatus} onChange={(event) => { setSuggestionStatus(event.target.value); setSuggestionPage(0); setSelectedSuggestionIds([]); }}>
              {PRODUCT_QUALITY_SUGGESTION_STATUSES.map((item) => <option key={item} value={item}>{shortFeature(item)}</option>)}
            </select>
          </label>
        </div>
        <DataTable
          columns={["", "Product", "Type", "Field", "Confidence", "Current", "Suggested", "Decision", "Actions"]}
          rows={suggestionRows.map((item) => [
            <input aria-label="Select suggestion for AI validation" type="checkbox" disabled={item.status !== "OPEN"} checked={item.id ? selectedOpenSuggestionIds.includes(item.id) : false} onChange={(event) => toggleSuggestionSelection(item, event.target.checked)} onClick={(event) => event.stopPropagation()} />,
            <div className="entity-cell"><strong>{item.productName ?? `Product #${item.foodItemId ?? "-"}`}</strong><small>{item.brand ?? "-"}</small></div>,
            <div className="badge-stack"><Badge value={item.suggestionType} /><Badge value={item.source} tone="neutral" /></div>,
            item.fieldName ?? "-",
            formatValue(item.confidenceScore),
            item.currentValue ?? "-",
            item.suggestedValue ?? "-",
            <QualitySuggestionDecision item={item} />,
            <div className="table-stack">
              <button className="ghost-button" type="button" onClick={(event) => { event.stopPropagation(); setSelectedQualitySuggestion(item); }}>View details</button>
              <Badge value={item.status} tone={item.status === "OPEN" ? "warn" : item.status === "ACCEPTED" ? "good" : "neutral"} />
              {item.status === "OPEN" && <button className="ghost-button" type="button" disabled={qualityActionState === "loading"} onClick={(event) => { event.stopPropagation(); void reviewQualitySuggestion(item, "accept"); }}>Accept</button>}
              {item.status === "OPEN" && <button className="ghost-button danger-text" type="button" disabled={qualityActionState === "loading"} onClick={(event) => { event.stopPropagation(); void reviewQualitySuggestion(item, "reject"); }}>Reject</button>}
            </div>
          ])}
          empty="No product quality suggestions returned."
        />
        <PaginationControls
          page={suggestions?.page ?? suggestionPage}
          pageSize={suggestions?.size ?? suggestionPageSize}
          totalElements={suggestions?.totalElements ?? suggestionRows.length}
          totalPages={suggestions?.totalPages ?? 1}
          first={(suggestions?.page ?? suggestionPage) <= 0}
          last={(suggestions?.page ?? suggestionPage) >= (suggestions?.totalPages ?? 1) - 1}
          onPageChange={setSuggestionPage}
          onPageSizeChange={(size) => { setSuggestionPageSize(size); setSuggestionPage(0); }}
        />
      </Panel>}
      {selectedQualitySuggestion && <QualitySuggestionDetailModal
        item={selectedQualitySuggestion}
        busy={qualityActionState === "loading"}
        onClose={() => setSelectedQualitySuggestion(null)}
        onAccept={(item) => reviewQualitySuggestion(item, "accept")}
        onReject={(item) => reviewQualitySuggestion(item, "reject")}
      />}
    </div>
  );
}

function QualityScanRunDetailModal({ detail, onClose }: { detail: ProductQualityScanRunDetail; onClose: () => void }) {
  const run = detail.run;
  const items = detail.items ?? [];
  const createdItems = items.filter((item) => item.status === "SUGGESTION_CREATED").length;
  const validatedItems = items.filter((item) => item.status === "VALIDATED").length;
  const issueItems = items.filter((item) => item.status && item.status !== "VALIDATED").length;
  const completion = run?.startedAt && run?.completedAt ? `${formatDate(run.startedAt)} -> ${formatDate(run.completedAt)}` : formatDate(run?.startedAt);
  const sourceTone = run?.source === "AI_ASSISTED" ? "warn" : "neutral";
  const quotaHint = run?.source === "AI_ASSISTED"
    ? `${formatValue(run.scannedProducts)} AI product validation credit(s) consumed by this run.`
    : "Rule-based scan. No AI validation quota was consumed.";

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="modal-card scan-run-detail-modal" onClick={(event) => event.stopPropagation()}>
        <div className="modal-header">
          <div>
            <span>QUALITY SCAN DETAIL</span>
            <h2>Run #{run?.id ?? "-"}</h2>
            <p>{completion}</p>
          </div>
          <button className="ghost-button" type="button" onClick={onClose}>Close</button>
        </div>
        <div className="modal-body">
          <div className="quality-run-meta-grid">
            <article>
              <span>Source</span>
              <strong><Badge value={run?.source ?? "-"} tone={sourceTone} /></strong>
              <small>{quotaHint}</small>
            </article>
            <article>
              <span>Trigger</span>
              <strong>{shortFeature(run?.triggerType)}</strong>
              <small>{run?.triggeredBy ? `Triggered by ${run.triggeredBy}` : "No admin actor recorded"}</small>
            </article>
            <article>
              <span>Region and rescan</span>
              <strong>{run?.marketRegion ?? "All regions"}</strong>
              <small>{run?.forceRescan ? "Forced rescan included validated products" : "Validated products were skipped"}</small>
            </article>
          </div>
          <div className="metric-grid compact-grid">
            <MetricCard label="Requested" value={formatValue(run?.requestedLimit)} hint={`Effective limit ${formatValue(run?.effectiveLimit)}`} />
            <MetricCard label="Scanned" value={formatValue(run?.scannedProducts)} hint="Products inspected" />
            <MetricCard label="Created" value={formatValue(run?.createdSuggestions)} hint={`${formatValue(createdItems)} stored item row(s)`} />
            <MetricCard label="Validated" value={formatValue(run?.validatedProducts)} hint={`${formatValue(validatedItems)} item row(s) without issue`} />
            <MetricCard label="Existing" value={formatValue(run?.skippedExistingSuggestions)} hint="Duplicate open suggestions skipped" />
            <MetricCard label="Skipped" value={formatValue(run?.skippedPreviouslyValidatedProducts)} hint="Previously validated products skipped" />
          </div>
          {run?.errorMessage && <div className="quality-run-error"><strong>Run error</strong><span>{run.errorMessage}</span></div>}
          <div className="quality-run-status-strip">
            <span>{formatValue(items.length)} item detail row(s)</span>
            <span>{formatValue(issueItems)} row(s) requiring admin attention</span>
            <span>Status: {shortFeature(run?.status)}</span>
          </div>
          <DataTable
            columns={["Product", "Result", "Field", "Suggested value", "Confidence", "Admin note"]}
            rows={items.map((item) => [
              <div className="entity-cell"><strong>{item.productName ?? `Product #${item.foodItemId ?? "-"}`}</strong><small>{item.brand ?? "-"}</small></div>,
              <div className="badge-stack"><Badge value={item.status ?? "-"} tone={item.status === "VALIDATED" ? "good" : item.status === "SUGGESTION_CREATED" ? "warn" : "neutral"} />{item.suggestionType && <Badge value={item.suggestionType} tone="neutral" />}</div>,
              qualityFieldLabel(item.fieldName),
              item.suggestedValue ?? "-",
              formatValue(item.confidenceScore),
              <div className="quality-run-note"><strong>{item.note ?? "-"}</strong><small>{item.reason ?? ""}</small></div>
            ])}
            empty="No product-level detail was stored for this run. Older scan runs only contain aggregate counts."
          />
        </div>
      </div>
    </div>
  );
}
function QualitySuggestionDecision({ item }: { item: ProductQualitySuggestion }) {
  const decision = qualitySuggestionDecision(item);
  return (
    <div className="quality-decision-cell">
      <Badge value={decision.label} tone={decision.tone} />
      <small>{decision.detail}</small>
    </div>
  );
}

function QualitySuggestionDetailModal({
  busy,
  item,
  onAccept,
  onClose,
  onReject
}: {
  busy: boolean;
  item: ProductQualitySuggestion;
  onAccept: (item: ProductQualitySuggestion) => void;
  onClose: () => void;
  onReject: (item: ProductQualitySuggestion) => void;
}) {
  const decision = qualitySuggestionDecision(item);
  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" onClick={onClose}>
      <section className="modal-card quality-suggestion-modal" aria-label="Quality suggestion detail" onClick={(event) => event.stopPropagation()}>
        <div className="modal-header">
          <div>
            <span>QUALITY ASSISTANT SUGGESTION</span>
            <h2>{item.productName ?? `Product #${item.foodItemId ?? "-"}`}</h2>
            <p>{item.brand ?? "Unknown brand"}</p>
          </div>
          <button className="ghost-button" type="button" onClick={onClose}>Close</button>
        </div>
        <div className="modal-body quality-suggestion-body">
          <div className="quality-suggestion-summary">
            <div>
              <span>Source</span>
              <strong>{shortFeature(item.source)}</strong>
            </div>
            <div>
              <span>Type</span>
              <strong>{shortFeature(item.suggestionType)}</strong>
            </div>
            <div>
              <span>Confidence</span>
              <strong>{formatValue(item.confidenceScore)}%</strong>
            </div>
            <div>
              <span>Status</span>
              <strong>{shortFeature(item.status)}</strong>
            </div>
          </div>
          <div className={`quality-decision-banner ${decision.tone}`}>
            <Badge value={decision.label} tone={decision.tone} />
            <div>
              <strong>{decision.title}</strong>
              <p>{decision.detail}</p>
            </div>
          </div>
          <div className="quality-diff-grid">
            <article>
              <span>Field</span>
              <strong>{qualityFieldLabel(item.fieldName)}</strong>
              <small>{item.fieldName ?? "No field supplied"}</small>
            </article>
            <article>
              <span>Current value</span>
              <strong>{item.currentValue ?? "-"}</strong>
              <small>Stored catalog value before admin decision</small>
            </article>
            <article>
              <span>Suggested value</span>
              <strong>{item.suggestedValue ?? "-"}</strong>
              <small>Value proposed by rule or AI assistant</small>
            </article>
          </div>
          <QualityImpactSummary item={item} />
          <div className="quality-preview-grid">
            <article>
              <header>
                <span>Before accept</span>
                <Badge value="Current product" tone="neutral" />
              </header>
              <QualityPreviewRows item={item} mode="before" />
            </article>
            <article>
              <header>
                <span>After accept</span>
                <Badge value={isQualitySuggestionApplyable(item) || item.suggestionType === "NAME_CLEANUP" || item.suggestionType === "SEARCH_ALIAS" ? "Preview" : "Review only"} tone={isQualitySuggestionApplyable(item) || item.suggestionType === "NAME_CLEANUP" || item.suggestionType === "SEARCH_ALIAS" ? "good" : "warn"} />
              </header>
              <QualityPreviewRows item={item} mode="after" />
            </article>
          </div>
          <Panel title="Reason and audit context">
            <div className="quality-reason-box">
              <p>{item.reason ?? "No reason returned for this suggestion."}</p>
              <div>
                <DetailItem label="Created" value={formatDate(item.createdAt)} />
                <DetailItem label="Reviewed" value={formatDate(item.reviewedAt)} />
                <DetailItem label="Reviewed by" value={item.reviewedBy ?? "-"} />
              </div>
            </div>
          </Panel>
        </div>
        <div className="modal-actions padded-actions">
          <button className="ghost-button" type="button" onClick={onClose}>Cancel</button>
          {item.status === "OPEN" && <button className="ghost-button danger-text" type="button" disabled={busy} onClick={() => onReject(item)}>Reject</button>}
          {item.status === "OPEN" && <button className="primary-button" type="button" disabled={busy} onClick={() => onAccept(item)}>Accept suggestion</button>}
        </div>
      </section>
    </div>
  );
}

function QualityImpactSummary({ item }: { item: ProductQualitySuggestion }) {
  const impact = qualityFieldImpact(item);
  return (
    <div className="quality-impact-grid">
      <article>
        <span>Field group</span>
        <strong>{impact.group}</strong>
        <small>{impact.description}</small>
      </article>
      <article>
        <span>Catalog write</span>
        <strong>{impact.writePath}</strong>
        <small>{impact.resetValidation ? "Quality validation will be reset after accept." : "Existing product validation marker is not changed."}</small>
      </article>
      <article>
        <span>Audit trail</span>
        <strong>{impact.audit}</strong>
        <small>{impact.adminAction}</small>
      </article>
    </div>
  );
}
function QualityPreviewRows({ item, mode }: { item: ProductQualitySuggestion; mode: "before" | "after" }) {
  const canPreviewApply = isQualitySuggestionApplyable(item) || item.suggestionType === "NAME_CLEANUP";
  const isAfter = mode === "after";
  const fieldValue = isAfter && canPreviewApply
    ? item.suggestedValue ?? "-"
    : item.currentValue ?? "-";
  const rows: Array<[string, ReactNode]> = [
    ["Product", item.productName ?? `Product #${item.foodItemId ?? "-"}`],
    ["Brand", item.brand ?? "-"],
    ["Field", qualityFieldLabel(item.fieldName)],
    ["Value", fieldValue]
  ];

  if (item.suggestionType === "SEARCH_ALIAS") {
    rows.splice(2, 2,
      ["Alias action", isAfter ? "Alias will be created or reactivated" : "No alias change yet"],
      ["Alias", isAfter ? item.suggestedValue ?? "-" : item.currentValue ?? "-"]
    );
  }

  if (isAfter && !canPreviewApply && item.suggestionType !== "SEARCH_ALIAS") {
    rows.push(["Catalog write", "No automatic product field update"]);
  }

  return (
    <div className="quality-preview-rows">
      {rows.map(([label, value]) => (
        <div key={label}>
          <span>{label}</span>
          <strong>{value}</strong>
        </div>
      ))}
    </div>
  );
}
function qualitySuggestionDecision(item: ProductQualitySuggestion): { label: string; title: string; detail: string; tone: "default" | "good" | "warn" | "danger" | "neutral" } {
  if (item.status && item.status !== "OPEN") {
    return {
      label: shortFeature(item.status),
      title: "Already reviewed",
      detail: item.reviewedBy ? `Reviewed by ${item.reviewedBy}` : "This suggestion is closed.",
      tone: item.status === "ACCEPTED" ? "good" : "neutral"
    };
  }
  if (isQualitySuggestionApplyable(item)) {
    return {
      label: "Field apply",
      title: "Accept can update this catalog field",
      detail: "The backend applies this whitelisted field, writes audit history, and resets quality validation for a future re-check.",
      tone: "good"
    };
  }
  if (item.suggestionType === "SEARCH_ALIAS" || item.suggestionType === "NAME_CLEANUP") {
    return {
      label: "Auto apply",
      title: "Accept has a dedicated backend action",
      detail: "This suggestion type has a specific safe apply path and audit entry.",
      tone: "good"
    };
  }
  return {
    label: "Review only",
    title: "Accept closes the issue without changing product fields",
    detail: "Use this for warning-style AI findings that require manual product review before editing data.",
    tone: "warn"
  };
}

type QualityFieldImpact = {
  group: string;
  description: string;
  writePath: string;
  audit: string;
  adminAction: string;
  resetValidation: boolean;
};

function qualityFieldImpact(item: ProductQualitySuggestion): QualityFieldImpact {
  if (item.suggestionType === "SEARCH_ALIAS") {
    return {
      group: "Search alias",
      description: "Improves barcode/name search without changing nutrition values.",
      writePath: "Alias create/reactivate",
      audit: "SEARCH_ALIAS_CHANGE",
      adminAction: "Accept creates or reactivates a safe search alias.",
      resetValidation: false
    };
  }
  if (item.suggestionType === "NAME_CLEANUP") {
    return {
      group: "Identity",
      description: "Improves the visible product name stored in the catalog.",
      writePath: "Product name update",
      audit: "REVIEW_UPDATE",
      adminAction: "Accept updates name and records product audit history.",
      resetValidation: true
    };
  }
  const field = normalizeQualityField(item.fieldName);
  if (!field || !item.suggestedValue || !QUALITY_APPLY_FIELDS.has(field)) {
    return {
      group: "Review only",
      description: "This finding is useful for admin review but is not auto-written to product fields.",
      writePath: "No automatic write",
      audit: "Suggestion decision only",
      adminAction: "Accept closes the suggestion; manual product edit may still be needed.",
      resetValidation: false
    };
  }
  if (NUTRITION_QUALITY_FIELDS.has(field)) {
    return {
      group: "Nutrition and micronutrients",
      description: "Calories, macros, serving size, or micronutrient catalog values.",
      writePath: "Food item nutrition field update",
      audit: "REVIEW_UPDATE",
      adminAction: "Accept writes the suggested numeric/text value to the product.",
      resetValidation: true
    };
  }
  if (IMAGE_QUALITY_FIELDS.has(field)) {
    return {
      group: "Image quality",
      description: "Image URL/source/status fields used by product presentation.",
      writePath: "Food item image field update",
      audit: "IMAGE_CHANGE",
      adminAction: "Accept updates image metadata and keeps a product audit entry.",
      resetValidation: true
    };
  }
  if (STATUS_REGION_QUALITY_FIELDS.has(field)) {
    return {
      group: "Status, region, and preparation",
      description: "Verification, market-region, preparation-state, or label quality metadata.",
      writePath: "Food item metadata update",
      audit: field === "verificationstatus" ? "STATUS_CHANGE" : "REVIEW_UPDATE",
      adminAction: "Accept updates catalog metadata for downstream filtering and trust controls.",
      resetValidation: true
    };
  }
  return {
    group: "Catalog field",
    description: "Supported product field change.",
    writePath: "Food item field update",
    audit: "REVIEW_UPDATE",
    adminAction: "Accept applies the suggested value through backend whitelist validation.",
    resetValidation: true
  };
}
function isQualitySuggestionApplyable(item: ProductQualitySuggestion): boolean {
  const field = normalizeQualityField(item.fieldName);
  if (!field || !item.suggestedValue) return false;
  return QUALITY_APPLY_FIELDS.has(field);
}

const NUTRITION_QUALITY_FIELDS = new Set([
  "calories", "protein", "fat", "carbs", "fiber", "sugar", "sodium", "potassium", "cholesterol",
  "calcium", "iron", "magnesium", "zinc", "vitamina", "vitaminc", "vitamind", "vitamine",
  "vitaminb12", "saturatedfat", "transfat", "sugaralcohol", "servingsizegrams", "servingunit",
  "nutriscore", "allergens"
]);

const IMAGE_QUALITY_FIELDS = new Set([
  "imagesource", "imagestatus", "imageurl", "externalimageurl", "displayimageurl"
]);

const STATUS_REGION_QUALITY_FIELDS = new Set([
  "verificationstatus", "marketregion", "preparationstate"
]);
const QUALITY_APPLY_FIELDS = new Set([
  "calories", "protein", "fat", "carbs", "fiber", "sugar", "sodium", "potassium", "cholesterol",
  "calcium", "iron", "magnesium", "zinc", "vitamina", "vitaminc", "vitamind", "vitamine",
  "vitaminb12", "saturatedfat", "transfat", "sugaralcohol", "servingsizegrams", "servingunit",
  "imagesource", "imagestatus", "imageurl", "externalimageurl", "displayimageurl", "verificationstatus",
  "marketregion", "preparationstate", "nutriscore", "allergens"
]);

function normalizeQualityField(value?: string): string {
  return String(value ?? "").replace(/[_-]/g, "").toLowerCase();
}

function qualityFieldLabel(value?: string): string {
  if (!value) return "No field";
  return shortFeature(value.replace(/([a-z])([A-Z])/g, "$1 $2"));
}
function AuditsView({ onError }: { onError: (message: string | null) => void }) {
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


  return (
    <div className="stack">
      <SectionToolbar title="Admin action audits" state={state} onReload={reload}>
        <button className="ghost-button" onClick={() => { setActionType(""); setTargetType(""); }} type="button">Clear filters</button>
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

function NotificationsView({ onError, onNavigate }: { onError: (message: string | null) => void; onNavigate: (section: SectionKey, context?: Omit<AdminTargetContext, "section">) => void }) {
  const [typeFilter, setTypeFilter] = useState("");
  const [severityFilter, setSeverityFilter] = useState("");
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(25);
  const [actionState, setActionState] = useState<LoadState>("idle");
  const [actionNotice, setActionNotice] = useState<string | null>(null);
  const path = buildNotificationPath({ type: typeFilter, severity: severityFilter, unreadOnly, page, size: pageSize });
  const { data, state, reload } = useEndpoint<PageResponse<Notification>>(path, onError);
  const rows = data?.content ?? [];
  const unread = rows.filter((item) => !item.read).length;
  const critical = rows.filter((item) => item.severity === "CRITICAL").length;
  const byType = countBy(rows, (item) => item.type ?? "Unknown");
  const bySeverity = countBy(rows, (item) => item.severity ?? "INFO");
  const bySource = countBy(rows, (item) => item.source ?? "UNKNOWN");
  const byReadState = countBy(rows, (item) => item.read ? "READ" : "UNREAD");
  const alertRules = notificationRuleCards();

  function resetFilters() {
    setTypeFilter("");
    setSeverityFilter("");
    setUnreadOnly(false);
    setPage(0);
    setActionNotice(null);
  }

  function applyQuickFilter(next: { type?: string; severity?: string; unreadOnly?: boolean }) {
    setTypeFilter(next.type ?? "");
    setSeverityFilter(next.severity ?? "");
    setUnreadOnly(Boolean(next.unreadOnly));
    setPage(0);
    setActionNotice(null);
  }

  async function markAllRead() {
    setActionState("loading");
    try {
      await request<unknown>("/api/v1/notifications/read-all", { method: "PATCH" });
      await reload();
      setActionNotice("Notification action completed.");
      setActionState("ready");
    } catch (error) {
      setActionState("error");
      onError(formatRequestError(error));
    }
  }

  async function markRead(item: Notification) {
    if (!item.id) return;
    setActionState("loading");
    try {
      await request<Notification>(`/api/v1/notifications/${item.id}/read`, { method: "PATCH" });
      setActionNotice("Notification action completed.");
      await reload();
      setActionState("ready");
    } catch (error) {
      setActionState("error");
      onError(formatRequestError(error));
    }
  }

  function goToTarget(item: Notification) {
    const target = notificationTargetSection(item);
    if (target) onNavigate(target, notificationTargetContext(item));
  }


  return (
    <div className="stack">
      <SectionToolbar title="Admin notifications" state={combineStates([state, actionState])} onReload={reload}>
        <button className="ghost-button" type="button" onClick={resetFilters}>Reset filters</button>
        <button className="primary-button" type="button" disabled={actionState === "loading"} onClick={markAllRead}>Mark all read</button>
      </SectionToolbar>
      {actionNotice && <div className="form-notice">{actionNotice}</div>}
      <div className="notification-command-strip">
        <button type="button" className={severityFilter === "CRITICAL" && unreadOnly ? "active" : ""} onClick={() => applyQuickFilter({ severity: "CRITICAL", unreadOnly: true })}>Unread critical</button>
        <button type="button" className={typeFilter === "subscription_provider_alert" ? "active" : ""} onClick={() => applyQuickFilter({ type: "subscription_provider_alert", unreadOnly: true })}>Provider failures</button>
        <button type="button" className={typeFilter === "ai_rejection_alert" ? "active" : ""} onClick={() => applyQuickFilter({ type: "ai_rejection_alert", unreadOnly: true })}>AI rejections</button>
        <button type="button" className={typeFilter === "system_alert" ? "active" : ""} onClick={() => applyQuickFilter({ type: "system_alert", unreadOnly: true })}>System/mail alerts</button>
      </div>
      <div className="metric-grid compact-grid">
        <MetricCard label="Unread on page" value={formatValue(unread)} hint="Unread operational alerts in this result page" />
        <MetricCard label="Critical on page" value={formatValue(critical)} hint="Critical severity alerts visible here" />
        <MetricCard label="Returned" value={formatValue(data?.totalElements ?? rows.length)} hint="Notifications matching the current filters" />
        <MetricCard label="Alert types" value={formatValue(Object.keys(byType).length)} hint="Distinct notification types visible here" />
      </div>
      <Panel title="Notification distribution">
        <div className="notification-insight-grid">
          <NotificationDistribution title="By severity" items={bySeverity} labelFormatter={shortFeature} />
          <NotificationDistribution title="By source" items={bySource} labelFormatter={notificationSourceLabel} />
          <NotificationDistribution title="Read state" items={byReadState} labelFormatter={shortFeature} />
        </div>
      </Panel>
      <Panel title="Notification filters">
        <div className="review-filter-grid">
          <label>
            Type
            <select value={typeFilter} onChange={(event) => { setTypeFilter(event.target.value); setPage(0); }}>
              <option value="">All types</option>
              <option value="ai_rejection_alert">AI rejection alert</option>
              <option value="system_alert">System alert</option>
              <option value="subscription_provider_alert">Subscription provider alert</option>
              <option value="subscription">Subscription</option>
            </select>
          </label>
          <label>
            Severity
            <select value={severityFilter} onChange={(event) => { setSeverityFilter(event.target.value); setPage(0); }}>
              <option value="">All severities</option>
              <option value="CRITICAL">Critical</option>
              <option value="WARNING">Warning</option>
              <option value="INFO">Info</option>
            </select>
          </label>
          <label className="inline-check">
            <input checked={unreadOnly} onChange={(event) => { setUnreadOnly(event.target.checked); setPage(0); }} type="checkbox" />
            Unread only
          </label>
        </div>
      </Panel>
      <Panel title="Alert routing rulebook">
        <div className="notification-rule-grid">
          {alertRules.map((rule) => (
            <article className={`notification-rule-card ${rule.tone}`} key={rule.type}>
              <header>
                <div>
                  <span>{rule.source}</span>
                  <strong>{rule.label}</strong>
                </div>
                <Badge value={rule.severity} tone={rule.tone} />
              </header>
              <p>{rule.description}</p>
              <div>
                <small>Route</small>
                <button className="ghost-button" type="button" onClick={() => onNavigate(rule.route)}>{sectionByKey[rule.route]?.label ?? rule.route}</button>
              </div>
            </article>
          ))}
        </div>
      </Panel>
      <DataTable
        columns={["Severity", "Type", "Message", "Target", "Read", "Created", "Actions"]}
        rows={rows.map((item) => [
          <Badge value={item.severity ?? "INFO"} tone={notificationSeverityTone(item.severity)} />,
          <div className="badge-stack"><Badge value={notificationTypeLabel(item.type)} /><Badge value={notificationSourceLabel(item.source)} tone="neutral" /></div>,
          <span className="notification-message-cell">{item.message ?? "-"}</span>,
          notificationTargetLabel(item),
          <Badge value={item.read ? "Read" : "Unread"} tone={item.read ? "neutral" : "warn"} />,
          formatDate(item.createdAt),
          <div className="table-stack notification-actions">
            {notificationTargetSection(item) && <button className="ghost-button" type="button" onClick={(event) => { event.stopPropagation(); goToTarget(item); }}>Open target</button>}
            {!item.read && <button className="ghost-button" type="button" disabled={actionState === "loading"} onClick={(event) => { event.stopPropagation(); void markRead(item); }}>Mark read</button>}
          </div>
        ])}
        empty="No notifications returned."
      />
      <PaginationControls
        page={data?.page ?? page}
        pageSize={data?.size ?? pageSize}
        totalElements={data?.totalElements ?? rows.length}
        totalPages={data?.totalPages ?? 1}
        first={Boolean(data?.first)}
        last={Boolean(data?.last)}
        onPageChange={setPage}
        onPageSizeChange={(size) => { setPageSize(size); setPage(0); }}
      />
    </div>
  );
}
function PushDeliveryView({ onError }: { onError: (message: string | null) => void }) {
  const { data, state, reload } = useEndpoint<AdminPushMonitoring>("/api/v1/admin/system/push-monitoring", onError);
  const providerEntries = Object.entries(data?.activeTokensByProvider ?? {}).sort(([left], [right]) => left.localeCompare(right));
  const configuredProviders = [
    ["Expo", data?.expoConfigured],
    ["FCM", data?.fcmConfigured],
    ["OneSignal", data?.oneSignalConfigured]
  ];
  const failed = data?.failedLast24h ?? 0;
  const sent = data?.sentLast24h ?? 0;
  const totalDelivery = sent + failed;


  return (
    <div className="stack">
      <SectionToolbar title="Push delivery monitoring" state={state} onReload={reload} />
      <div className="review-workspace-summary">
        <MetricCard label="Delivery state" value={data?.enabled ? "Enabled" : "Disabled"} hint={`Selected provider: ${formatValue(data?.provider)}`} />
        <MetricCard label="Active tokens" value={formatValue(data?.activeTokenCount)} hint="Enabled device token records" />
        <MetricCard label="Sent 24h" value={formatValue(sent)} hint="Push delivery logs marked SENT" />
        <MetricCard label="Failed 24h" value={formatValue(failed)} hint={`${percent(failed, totalDelivery)}% of logged attempts`} />
      </div>

      <div className="ops-grid">
        <Panel title="Provider token distribution">
          <div className="distribution-list">
            {providerEntries.map(([provider, count]) => (
              <div key={provider}>
                <div>
                  <span>{provider}</span>
                  <strong>{formatValue(count)}</strong>
                </div>
                <div className="progress-track">
                  <span className="good" style={{ width: `${percent(count, data?.activeTokenCount ?? 0)}%` }} />
                </div>
              </div>
            ))}
            {!providerEntries.length && <EmptyState message="No active push token returned." />}
          </div>
        </Panel>
        <Panel title="Provider configuration">
          <div className="config-grid">
            {configuredProviders.map(([provider, configured]) => (
              <div className="config-block" key={String(provider)}>
                <span>{provider}</span>
                <strong>{configured ? "Configured" : "Missing"}</strong>
              </div>
            ))}
          </div>
        </Panel>
      </div>

      <Panel title="Operational policy">
        <div className="roadmap-strip">
          <span>Raw device tokens are never exposed in admin responses</span>
          <span>Invalid provider-token responses disable the stored token</span>
          <span>Provider credentials stay in environment configuration</span>
          <span>Real delivery validation requires mobile device tokens</span>
        </div>
      </Panel>
    </div>
  );
}

type TrackingMode = "overview" | "water" | "fasting" | "steps";

function TrackingMonitoringView({ mode, onError }: { mode: TrackingMode; onError: (message: string | null) => void }) {
  const [days, setDays] = useState(30);
  const [trackingPage, setTrackingPage] = useState(0);
  const [trackingPageSize, setTrackingPageSize] = useState(25);
  const { data, state, reload } = useEndpoint<AdminTrackingSummary>(`/api/v1/admin/tracking/summary?days=${days}`, onError);



  const title = {
    overview: "Tracking monitoring",
    water: "Water tracking",
    fasting: "Fasting tracking",
    steps: "Step tracking"
  }[mode];
  const selectedModule = mode === "water"
    ? data?.water
    : mode === "fasting"
      ? data?.fasting
      : mode === "steps"
        ? data?.steps
        : undefined;
  const visibleModules = mode === "overview"
    ? [data?.water, data?.fasting, data?.steps].filter(Boolean) as AdminTrackingModuleSummary[]
    : [selectedModule].filter(Boolean) as AdminTrackingModuleSummary[];
  const trends = data?.trends ?? [];
  const trendRows = trackingTrendRows(mode, trends);
  const trackingTotalPages = Math.max(1, Math.ceil(trendRows.length / trackingPageSize));
  const safeTrackingPage = Math.min(trackingPage, trackingTotalPages - 1);
  const pagedTrendRows = trendRows.slice(safeTrackingPage * trackingPageSize, safeTrackingPage * trackingPageSize + trackingPageSize);

  useEffect(() => {
    setTrackingPage(0);
  }, [mode, days, trackingPageSize, trendRows.length]);


  return (
    <div className="stack">
      <SectionToolbar title={title} state={state} onReload={reload}>
        <select value={days} onChange={(event) => setDays(Number(event.target.value))}>
          <option value={7}>7 days</option>
          <option value={30}>30 days</option>
          <option value={90}>90 days</option>
        </select>
      </SectionToolbar>

      <div className="mail-hero">
        <div>
          <p className="eyebrow">Aggregate tracking</p>
          <h2>Water, fasting, and step adoption without exposing user-level logs.</h2>
          <p>{formatDate(data?.startDate)} - {formatDate(data?.endDate)} window. Generated {formatDate(data?.generatedAt)}.</p>
        </div>
        <div className="mail-hero-status">
          <Badge value={`${formatValue(data?.rangeDays ?? days)} days`} />
          <span>Admin-only operational summary</span>
        </div>
      </div>

      <div className="review-workspace-summary">
        {visibleModules.map((module) => (
          <MetricCard
            key={module.module}
            label={humanizeFeature(module.module)}
            value={formatModuleTotal(module)}
            hint={`${formatValue(module.activeUsersLastRange)} active users / ${formatValue(module.recordsLastRange)} records`}
          />
        ))}
      </div>

      {mode === "overview" && (
        <div className="chart-grid">
          <MiniBarChart
            label="Records by module"
            items={[
              ["Water", data?.water?.recordsLastRange ?? 0],
              ["Fasting", data?.fasting?.recordsLastRange ?? 0],
              ["Steps", data?.steps?.recordsLastRange ?? 0]
            ]}
          />
          <MiniBarChart
            label="Active users by module"
            items={[
              ["Water", data?.water?.activeUsersLastRange ?? 0],
              ["Fasting", data?.fasting?.activeUsersLastRange ?? 0],
              ["Steps", data?.steps?.activeUsersLastRange ?? 0]
            ]}
          />
          <MiniBarChart
            label="Reminder enabled users"
            items={[
              ["Water", data?.water?.reminderEnabledUsers ?? 0],
              ["Fasting", data?.fasting?.reminderEnabledUsers ?? 0],
              ["Steps", data?.steps?.reminderEnabledUsers ?? 0]
            ]}
          />
        </div>
      )}

      {mode !== "overview" && visibleModules[0] && (
        <div className="ops-grid">
          <Panel title="Module configuration">
            <div className="config-grid">
              <div className="config-block">
                <span>Configured users</span>
                <strong>{formatValue(visibleModules[0].configuredUsers)}</strong>
              </div>
              <div className="config-block">
                <span>Reminder enabled</span>
                <strong>{formatValue(visibleModules[0].reminderEnabledUsers)}</strong>
              </div>
              <div className="config-block">
                <span>Active now</span>
                <strong>{formatValue(visibleModules[0].activeNow)}</strong>
              </div>
            </div>
          </Panel>
          <Panel title="Recent trend">
            <MiniBarChart label={humanizeFeature(mode)} items={trendBarItems(mode, trends)} />
          </Panel>
        </div>
      )}

      <DataTable
        columns={trackingTrendColumns(mode)}
        rows={pagedTrendRows}
        empty="No tracking trend returned."
      />
      <PaginationControls
        page={safeTrackingPage}
        pageSize={trackingPageSize}
        totalElements={trendRows.length}
        totalPages={trackingTotalPages}
        first={safeTrackingPage <= 0}
        last={safeTrackingPage >= trackingTotalPages - 1}
        onPageChange={setTrackingPage}
        onPageSizeChange={(size) => {
          setTrackingPageSize(size);
          setTrackingPage(0);
        }}
      />
    </div>
  );
}

type SystemHealthMode = "overview" | "runtime" | "database" | "providers" | "production";

function SystemHealthView({ mode, onError }: { mode: SystemHealthMode; onError: (message: string | null) => void }) {
  const { data, state, reload } = useEndpoint<SystemHealth>("/api/v1/admin/system/health", onError);
  const heapPercent = percent(readNumber(data, "heapUsedMb"), readNumber(data, "heapMaxMb"));
  const aiFailurePercent = Math.round((readNumber(data, "aiFailureRateLast24h") ?? 0) * 100);
  const aiConfirmationPercent = Math.round((readNumber(data, "aiDraftConfirmationRateLast7d") ?? 0) * 100);
  const warnings = Array.isArray(data?.warnings) ? data.warnings : [];
  const categories = data ? healthCategories(data) : [];
  const visibleCategories = categories.filter((category) => {
    if (mode === "overview") return false;
    if (mode === "runtime") return ["Application Runtime", "Memory"].includes(category.title);
    if (mode === "database") return category.title === "Database";
    if (mode === "providers") return ["Subscriptions", "AI Provider", "AI Meal Drafts"].includes(category.title);
    return ["Alerts", "Application Runtime", "Database"].includes(category.title);
  });



  const title = {
    overview: "System health",
    runtime: "Runtime health",
    database: "Database health",
    providers: "Provider health",
    production: "Production readiness"
  }[mode];

  return (
    <div className="stack">
      <SectionToolbar title={title} state={state} onReload={reload} />
      {data && mode === "overview" && (
        <>
          <div className="health-summary-grid">
            <HealthStatusCard label="Application" value={String(data.status ?? "-")} detail={String(data.appVersion ?? data.appName ?? "-")} />
            <HealthStatusCard label="Database" value={String(data.databaseStatus ?? "-")} detail={`${formatValue(data.databaseLatencyMs)} ms latency`} />
            <HealthStatusCard label="AI provider" value={String(data.aiProvider ?? "-")} detail={data.aiEnabled ? "Enabled" : "Disabled"} />
            <HealthStatusCard label="Alerts 24h" value={formatValue(data.systemAlertsLast24h)} detail={`${warnings.length} active warning(s)`} tone={warnings.length ? "warn" : "good"} />
          </div>
          <div className="chart-grid">
            <GaugeChart label="Heap usage" value={heapPercent} detail={`${formatValue(data.heapUsedMb)} / ${formatValue(data.heapMaxMb)} MB`} />
            <GaugeChart label="AI failure rate" value={aiFailurePercent} detail={`${formatValue(data.failedAiRequestsLast24h)} failed / ${formatValue(data.aiRequestsLast24h)} requests`} tone={aiFailurePercent > 5 ? "danger" : "good"} />
            <GaugeChart label="AI confirmation" value={aiConfirmationPercent} detail={`${formatValue(data.confirmedAiDraftsLast7d)} confirmed / ${formatValue(data.aiDraftsLast7d)} drafts`} />
            <MiniBarChart
              label="RevenueCat events"
              items={[
                ["24h events", readNumber(data, "revenueCatEventsLast24h") ?? 0],
                ["Failed", readNumber(data, "failedRevenueCatEvents") ?? 0],
                ["Active subs", readNumber(data, "activeSubscriptions") ?? 0],
                ["AI quota done", readNumber(data, "exhaustedAiQuotaSubscriptions") ?? 0]
              ]}
            />
          </div>
          {warnings.length > 0 && (
            <Panel title="Warnings">
              <div className="warning-list">
                {warnings.map((warning, index) => <span key={`${warning}-${index}`}>{String(warning)}</span>)}
              </div>
            </Panel>
          )}
        </>
      )}
      {data && mode === "production" && (
        <Panel title="Production readiness focus">
          <div className="roadmap-strip">
            <span>Runtime: {String(data.status ?? "-")}</span>
            <span>Database: {String(data.databaseStatus ?? "-")}</span>
            <span>Warnings: {formatValue(warnings.length)}</span>
            <span>Alerts 24h: {formatValue(data.systemAlertsLast24h)}</span>
            <span>RevenueCat failed events: {formatValue(data.failedRevenueCatEvents)}</span>
          </div>
        </Panel>
      )}
      <div className="health-category-grid">
        {visibleCategories.map((category) => (
          <HealthCategoryCard key={category.title} category={category} />
        ))}
        {!visibleCategories.length && <EmptyState message="No system health payload returned." />}
      </div>
    </div>
  );
}

type IntegrationCardModel = {
  title: string;
  status: string;
  tone: string;
  description: string;
  metrics: Array<[string, string]>;
};

function IntegrationCard({ card }: { card: IntegrationCardModel }) {
  return (
    <article className={`integration-card ${card.tone}`}>
      <header>
        <div>
          <span>{card.title}</span>
          <strong>{card.status}</strong>
        </div>
        <i />
      </header>
      <p>{card.description}</p>
      <div className="integration-metric-list">
        {card.metrics.map(([label, value]) => (
          <div key={label}>
            <span>{label}</span>
            <strong>{value}</strong>
          </div>
        ))}
      </div>
    </article>
  );
}

function NotificationDistribution({
  items,
  labelFormatter,
  title
}: {
  items: Record<string, number>;
  labelFormatter?: (value?: string) => string;
  title: string;
}) {
  const entries = Object.entries(items).sort((left, right) => right[1] - left[1]);
  const total = entries.reduce((sum, [, value]) => sum + value, 0);
  return (
    <article className="notification-distribution-card">
      <header>
        <strong>{title}</strong>
        <span>{formatValue(total)}</span>
      </header>
      <div>
        {entries.map(([label, value]) => (
          <div key={label}>
            <span>{labelFormatter ? labelFormatter(label) : label}</span>
            <div className="progress-track"><i style={{ width: `${percent(value, total)}%` }} /></div>
            <strong>{formatValue(value)}</strong>
          </div>
        ))}
        {!entries.length && <small>No data on this page.</small>}
      </div>
    </article>
  );
}
function MiniNotificationList({ notifications }: { notifications: Notification[] }) {
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

function PolicyStep({ step, title, value }: { step: string; title: string; value: string }) {
  return (
    <article className="policy-step">
      <span>{step}</span>
      <div>
        <strong>{title}</strong>
        <small>{value}</small>
      </div>
    </article>
  );
}

function DistributionPanel({ title, items }: { title: string; items: Record<string, number> }) {
  const entries = Object.entries(items).sort((a, b) => b[1] - a[1]).slice(0, 8);
  const total = entries.reduce((sum, [, value]) => sum + value, 0);
  return (
    <Panel title={title}>
      <div className="distribution-list">
        {entries.map(([label, value]) => (
          <div key={label}>
            <div>
              <span>{label}</span>
              <strong>{formatValue(value)}</strong>
            </div>
            <div className="progress-track">
              <span className="good" style={{ width: `${percent(value, total)}%` }} />
            </div>
          </div>
        ))}
        {!entries.length && <EmptyState message="No sample data returned." />}
      </div>
    </Panel>
  );
}

function useEndpoint<T>(path: string, onError: (message: string | null) => void) {
  const [data, setData] = useState<T | null>(null);
  const [state, setState] = useState<LoadState>("idle");
  const [reloadToken, setReloadToken] = useState(0);
  const stablePath = useMemo(() => path, [path]);

  async function load() {
    setReloadToken((current) => current + 1);
  }

  useEffect(() => {
    let active = true;
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
  }, [stablePath, reloadToken]);

  return { data, state, reload: load };
}

function SectionToolbar({ title, state, onReload, children }: { title: string; state: LoadState; onReload: () => void; children?: ReactNode }) {
  return (
    <div className="section-toolbar">
      <div>
        <h2>{title}</h2>
        <span className={`load-state ${state}`}>{state}</span>
      </div>
      <div className="toolbar-actions">
        {children}
        <button className="ghost-button" onClick={onReload} type="button">Refresh</button>
      </div>
    </div>
  );
}

function MetricCard({ label, value, hint }: { label: string; value: string; hint: string }) {
  return (
    <article className="metric-card">
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{hint}</small>
    </article>
  );
}

function Panel({ title, children, className }: { title: string; children: ReactNode; className?: string }) {
  return (
    <article className={`panel ${className ?? ""}`.trim()}>
      <h3>{title}</h3>
      {children}
    </article>
  );
}


function CollapsiblePanel({ title, open, onToggle, children }: { title: string; open: boolean; onToggle: () => void; children: ReactNode }) {
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
function DataTable<T = unknown>({
  columns,
  rows,
  empty,
  rowData,
  onRowClick
}: {
  columns: string[];
  rows: ReactNode[][];
  empty: string;
  rowData?: T[];
  onRowClick?: (row: T) => void;
}) {
  if (!rows.length) {
    return <EmptyState message={empty} />;
  }
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            {columns.map((column) => <th key={column}>{column}</th>)}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr
              className={onRowClick ? "clickable-row" : undefined}
              key={index}
              onClick={() => {
                if (onRowClick && rowData?.[index]) {
                  onRowClick(rowData[index]);
                }
              }}
            >
              {row.map((cell, cellIndex) => <td key={cellIndex}>{cell}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function PaginationControls({
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
    <div className="pagination-bar">
      <div>
        <strong>{formatValue(from)}-{formatValue(to)}</strong>
        <span>of {formatValue(totalElements)} items</span>
      </div>
      <div className="pagination-actions">
        <label>
          Page size
          <select value={pageSize} onChange={(event) => onPageSizeChange(Number(event.target.value))}>
            <option value={10}>10</option>
            <option value={20}>20</option>
            <option value={50}>50</option>
            <option value={100}>100</option>
          </select>
        </label>
        <button className="ghost-button" disabled={first || page <= 0} onClick={() => onPageChange(0)} type="button">First</button>
        <button className="ghost-button" disabled={first || page <= 0} onClick={() => onPageChange(Math.max(0, page - 1))} type="button">Previous</button>
        <span className="page-indicator">Page {formatValue(page + 1)} / {formatValue(safeTotalPages)}</span>
        <button className="ghost-button" disabled={last || page >= safeTotalPages - 1} onClick={() => onPageChange(Math.min(safeTotalPages - 1, page + 1))} type="button">Next</button>
        <button className="ghost-button" disabled={last || page >= safeTotalPages - 1} onClick={() => onPageChange(safeTotalPages - 1)} type="button">Last</button>
      </div>
    </div>
  );
}

function ProductCell({ item }: { item: FoodProduct }) {
  return (
    <div className="entity-cell">
      <strong>{productName(item)}</strong>
      <small>{item.brand ?? item.barcode ?? "-"}</small>
    </div>
  );
}

function ProductReviewModal({
  item,
  onClose,
  onApprove,
  onReject,
  onSave,
  draft,
  reviewNote,
  saving,
  setDraft,
  setReviewNote,
  onError
}: {
  item: FoodProduct;
  onClose: () => void;
  onApprove: () => void;
  onReject: () => void;
  onSave: () => void;
  draft: ProductReviewDraft;
  reviewNote: string;
  saving: boolean;
  setDraft: (value: ProductReviewDraft) => void;
  setReviewNote: (value: string) => void;
  onError: (message: string | null) => void;
}) {
  const image = item.displayImageUrl ?? item.imageUrl ?? item.externalImageUrl;
  function updateDraft<K extends keyof ProductReviewDraft>(key: K, value: ProductReviewDraft[K]) {
    setDraft({ ...draft, [key]: value });
  }
  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <section className="product-modal" role="dialog" aria-modal="true" aria-label="Product review detail" onClick={(event) => event.stopPropagation()}>
        <header className="modal-header">
          <div>
            <p className="eyebrow">Product review</p>
            <h2>{productName(item)}</h2>
            <span>{item.brand ?? item.barcode ?? "No brand or barcode"}</span>
          </div>
          <button className="icon-button" onClick={onClose} type="button" aria-label="Close">x</button>
        </header>
        <div className="modal-body">
          <div className="product-image-frame">
            {image ? <img alt={productName(item)} src={image} /> : <span>No image</span>}
          </div>
            <div className="product-detail-stack">
            <div className="detail-grid editable">
            <ProductQualityWorkbench product={item} onError={onError} />
              <EditableDetail label="Product name">
                <input value={draft.productName} onChange={(event) => updateDraft("productName", event.target.value)} />
              </EditableDetail>
              <EditableDetail label="Display image URL">
                <input value={draft.displayImageUrl} onChange={(event) => updateDraft("displayImageUrl", event.target.value)} placeholder="https://..." />
              </EditableDetail>
              <EditableDetail label="Region">
                <select value={draft.marketRegion} onChange={(event) => updateDraft("marketRegion", event.target.value)}>
                  <option value="">Keep current</option>
                  {MARKET_REGIONS.map((value) => <option key={value} value={value}>{value}</option>)}
                </select>
              </EditableDetail>
              <EditableDetail label="Catalog">
                <select value={draft.catalogType} onChange={(event) => updateDraft("catalogType", event.target.value)}>
                  <option value="">Keep current</option>
                  {CATALOG_TYPES.map((value) => <option key={value} value={value}>{value}</option>)}
                </select>
              </EditableDetail>
              <DetailItem label="Data source" value={item.dataSource} />
              <EditableDetail label="Verification">
                <select value={draft.verificationStatus} onChange={(event) => updateDraft("verificationStatus", event.target.value)}>
                  <option value="">Keep current</option>
                  {VERIFICATION_STATUSES.map((value) => <option key={value} value={value}>{value}</option>)}
                </select>
              </EditableDetail>
              <EditableDetail label="Image status">
                <select value={draft.imageStatus} onChange={(event) => updateDraft("imageStatus", event.target.value)}>
                  <option value="">Keep current</option>
                  {IMAGE_STATUSES.map((value) => <option key={value} value={value}>{value}</option>)}
                </select>
              </EditableDetail>
              <EditableDetail label="Image source">
                <select value={draft.imageSource} onChange={(event) => updateDraft("imageSource", event.target.value)}>
                  <option value="">Keep current</option>
                  {IMAGE_SOURCES.map((value) => <option key={value} value={value}>{value}</option>)}
                </select>
              </EditableDetail>
              <DetailItem label="Quality score" value={formatValue(item.qualityScore)} />
              <DetailItem label="Review priority" value={formatValue(item.reviewPriority)} />
              <DetailItem label="Usage count" value={formatValue(item.usageCount)} />
            </div>
            <div className="nutrition-editor">
              <div className="nutrition-editor-heading">
                <div>
                  <span>Nutrition values</span>
                  <strong>Editable per 100g/ml review data</strong>
                </div>
                <small>Saved values update the catalog product directly.</small>
              </div>
              <NutritionInputGrid title="Macros" fields={PRODUCT_MACRO_FIELDS} draft={draft} onChange={updateDraft} />
              <NutritionInputGrid title="Minerals" fields={PRODUCT_MINERAL_FIELDS} draft={draft} onChange={updateDraft} />
              <NutritionInputGrid title="Vitamins" fields={PRODUCT_VITAMIN_FIELDS} draft={draft} onChange={updateDraft} />
              <div className="nutrition-section">
                <div className="nutrition-section-header">
                  <h3>Serving</h3>
                  <span>Optional display metadata</span>
                </div>
                <div className="nutrition-input-grid serving-edit-grid">
                  <label className="nutrition-input">
                    <span>Serving size</span>
                    <div className="nutrition-input-control">
                      <input
                        inputMode="decimal"
                        type="number"
                        min="0"
                        step="0.01"
                        value={draft.servingSizeGrams}
                        onChange={(event) => updateDraft("servingSizeGrams", event.target.value)}
                      />
                      <em>g/ml</em>
                    </div>
                  </label>
                  <label className="nutrition-input">
                    <span>Serving unit</span>
                    <div className="nutrition-input-control single">
                      <input
                        value={draft.servingUnit}
                        onChange={(event) => updateDraft("servingUnit", event.target.value)}
                        placeholder="g, ml, piece"
                      />
                    </div>
                  </label>
                </div>
              </div>
            </div>
            <label>
              Review note
              <textarea value={reviewNote} onChange={(event) => setReviewNote(event.target.value)} placeholder="Optional note for this review decision" />
            </label>
            <div className="modal-actions">
              <button className="ghost-button danger-action" disabled={saving} onClick={onReject} type="button">Reject</button>
              <button className="ghost-button" disabled={saving} onClick={onSave} type="button">Save changes</button>
              <button className="primary-button" disabled={saving} onClick={onApprove} type="button">{saving ? "Saving..." : "Approve"}</button>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}

type ProductWorkbenchTab = "overview" | "nutrition" | "names" | "aliases" | "serving" | "evidence" | "ai" | "audit";

const HIGH_IMPACT_PRODUCT_FIELDS = new Set([
  "calories", "protein", "fat", "carbs", "fiber", "sugar", "sodium", "potassium", "cholesterol",
  "calcium", "iron", "magnesium", "zinc", "vitaminA", "vitaminC", "vitaminD", "vitaminE", "vitaminB12",
  "saturatedFat", "transFat", "sugarAlcohol"
]);

function ProductQualityWorkbench({ product, onError }: { product: FoodProduct; onError: (message: string | null) => void }) {
  const [tab, setTab] = useState<ProductWorkbenchTab>("overview");
  const [data, setData] = useState<AdminProductQualityWorkbench | null>(null);
  const [state, setState] = useState<LoadState>("idle");
  const [pendingHighImpact, setPendingHighImpact] = useState<ProductQualitySuggestion | null>(null);

  useEffect(() => {
    void loadWorkbench();
  }, [product.id]);

  async function loadWorkbench() {
    if (!product.id) return;
    setState("loading");
    try {
      setData(await request<AdminProductQualityWorkbench>(`/api/v1/admin/products/${product.id}/quality-workbench`));
      setState("ready");
    } catch (error) {
      setState("error");
      onError(formatRequestError(error));
    }
  }

  async function analyzeWithAi() {
    if (!product.id) return;
    setState("loading");
    try {
      await request<AdminProductQualityAiValidationResult>("/api/v1/admin/products/quality-suggestions/ai-validate-selected", {
        method: "POST",
        body: { productIds: [product.id], limit: 1, forceRescan: true }
      });
      await loadWorkbench();
      setTab("ai");
    } catch (error) {
      setState("error");
      onError(formatRequestError(error));
    }
  }

  async function reviewSuggestion(item: ProductQualitySuggestion, action: "accept" | "reject", confirmed = false) {
    if (!item.id) return;
    if (action === "accept" && isHighImpactProductSuggestion(item) && !confirmed) {
      setPendingHighImpact(item);
      return;
    }
    setState("loading");
    try {
      await request(`/api/v1/admin/products/quality-suggestions/${item.id}/${action}`, { method: "PATCH" });
      setPendingHighImpact(null);
      await loadWorkbench();
    } catch (error) {
      setState("error");
      onError(formatRequestError(error));
    }
  }

  const suggestions = data?.suggestions ?? [];
  const openSuggestions = suggestions.filter((item) => item.status === "OPEN");
  const comparisons = data?.evidence?.comparisons ?? [];
  const evidence = data?.evidence?.evidence ?? [];
  const nameSuggestions = openSuggestions.filter((item) => ["NAME_CLEANUP", "DISPLAY_NAME", "LOCALIZATION"].includes(item.suggestionType ?? ""));
  const aliasSuggestions = openSuggestions.filter((item) => item.suggestionType === "SEARCH_ALIAS");
  const servingSuggestions = openSuggestions.filter((item) => item.suggestionType === "SERVING_OPTION" || item.suggestionType === "MISSING_SERVING_SIZE");
  const nutritionSuggestions = openSuggestions.filter(isHighImpactProductSuggestion);

  return <section className="product-quality-workbench" aria-label="AI product quality workbench">
    <div className="product-workbench-toolbar">
      <div>
        <span className="eyebrow">AI quality workbench</span>
        <strong>{formatValue(openSuggestions.length)} open recommendation(s)</strong>
      </div>
      <div className="product-workbench-actions">
        <button className="ghost-button" type="button" disabled={state === "loading"} onClick={loadWorkbench}>Refresh</button>
        <button className="primary-button" type="button" disabled={state === "loading" || !product.id} onClick={analyzeWithAi}>Analyze product with AI</button>
      </div>
    </div>
    <div className="product-workbench-tabs" role="tablist">
      {(["overview", "nutrition", "names", "aliases", "serving", "evidence", "ai", "audit"] as ProductWorkbenchTab[]).map((value) =>
        <button key={value} type="button" role="tab" aria-selected={tab === value} className={tab === value ? "active" : ""} onClick={() => setTab(value)}>{humanizeFeature(value)}</button>
      )}
    </div>

    {state === "loading" && !data && <div className="empty-state">Loading product quality context...</div>}
    {tab === "overview" && data && <div className="workbench-pane">
      <div className="metric-grid compact-grid">
        <MetricCard label="Quality" value={`${formatValue(data.product?.qualityScore)} / 100`} hint={`Confidence ${formatValue(data.product?.confidenceScore)}`} />
        <MetricCard label="Active issues" value={formatValue((data.qualityIssues ?? []).filter((item) => !item.resolved).length)} hint="Deterministic quality rules" />
        <MetricCard label="Evidence rows" value={formatValue(evidence.length)} hint={`${formatValue(comparisons.length)} field comparisons`} />
        <MetricCard label="AI review" value={formatValue(openSuggestions.length)} hint="Pending admin decisions" />
      </div>
      <DataTable columns={["Issue", "Reason", "State"]} rows={(data.qualityIssues ?? []).map((item) => [<strong>{humanizeFeature(item.issueType)}</strong>, item.reason ?? "-", <Badge value={item.resolved ? "RESOLVED" : "OPEN"} tone={item.resolved ? "good" : "warn"} />])} empty="No quality issues detected." />
      <div className="canonical-workbench-strip">
        <div><span>Canonical identity</span><strong>{data.canonicalDuplicate?.canonicalFoodKey ?? "Not assigned"}</strong></div>
        <div><span>Resolved primary</span><strong>{formatValue(data.canonicalDuplicate?.resolvedPrimaryProductId)}</strong></div>
        <div><span>Candidates</span><strong>{formatValue(data.canonicalDuplicate?.candidates?.length)}</strong></div>
      </div>
      {(data.canonicalDuplicate?.candidates?.length ?? 0) > 1 && <DataTable columns={["Candidate", "Source", "State", "Quality"]} rows={(data.canonicalDuplicate?.candidates ?? []).map((item) => [<div className="entity-cell"><strong>{item.displayName ?? `Product ${item.productId}`}</strong><small>{item.brand ?? "Generic"}</small></div>, `${item.dataSource ?? "-"} / ${item.marketRegion ?? "-"}`, `${item.preparationState ?? "-"} / ${item.verificationStatus ?? "-"}`, `${formatValue(item.qualityScore)} / 100`])} empty="No canonical duplicate candidates." />}
    </div>}

    {tab === "nutrition" && <WorkbenchSuggestionTable suggestions={nutritionSuggestions} comparisons={comparisons} onReview={reviewSuggestion} />}
    {tab === "names" && <div className="workbench-pane">
      <DataTable columns={["Language", "Display name", "Short name", "Source", "State"]} rows={(data?.localizations ?? []).map((item) => [item.language ?? "-", item.displayName ?? "-", item.shortDisplayName ?? "-", item.source ?? "-", <Badge value={item.active ? "ACTIVE" : "INACTIVE"} tone={item.active ? "good" : "neutral"} />])} empty="No localized names." />
      <WorkbenchSuggestionTable suggestions={nameSuggestions} comparisons={comparisons} onReview={reviewSuggestion} />
    </div>}
    {tab === "aliases" && <div className="workbench-pane"><ProductAliasManager productId={product.id} onError={onError} /><WorkbenchSuggestionTable suggestions={aliasSuggestions} comparisons={comparisons} onReview={reviewSuggestion} /></div>}
    {tab === "serving" && <div className="workbench-pane">
      <DataTable columns={["Serving", "Conversion", "Source", "Quality", "Localizations"]} rows={(data?.servingOptions ?? []).map((item) => [<div className="entity-cell"><strong>{item.label ?? "-"}</strong><small>{item.defaultOption ? "Default" : item.unitType ?? "-"}</small></div>, item.gramWeight != null ? `${item.gramWeight} g` : item.mlVolume != null ? `${item.mlVolume} ml` : `${formatValue(item.quantity)} ${item.unitType ?? ""}`, item.source ?? "-", <Badge value={item.qualityStatus ?? "-"} />, (item.localizations ?? []).map((value) => `${value.language}: ${value.label}`).join(", ") || "-"])} empty="No serving options." />
      <WorkbenchSuggestionTable suggestions={servingSuggestions} comparisons={comparisons} onReview={reviewSuggestion} />
    </div>}
    {tab === "evidence" && <div className="workbench-pane">
      <DataTable columns={["Field", "Provider", "Value", "Basis", "Confidence", "Freshness", "Reviewer"]} rows={evidence.map((item) => [item.fieldName ?? "-", item.provider ?? "-", formatValue(item.numericValue), item.basis ?? "-", `${formatValue(item.confidenceScore)}%`, <Badge value={item.stale ? "STALE" : "CURRENT"} tone={item.stale ? "warn" : "good"} />, item.reviewerIdentity ?? "system"])} empty="No source evidence captured." />
      <DataTable columns={["Field", "Basis", "Comparison", "Difference", "Reason"]} rows={comparisons.map((item) => [item.fieldName ?? "-", item.basis ?? "-", <Badge value={item.state ?? "-"} tone={item.state === "MATCH" ? "good" : item.state === "CONFLICT" ? "danger" : "warn"} />, formatValue(item.maximumDifference), item.reason ?? "-"])} empty="No evidence comparisons." />
    </div>}
    {tab === "ai" && <WorkbenchSuggestionTable suggestions={suggestions} comparisons={comparisons} onReview={reviewSuggestion} showReviewed />}
    {tab === "audit" && <DataTable columns={["When", "Action", "Field", "Change", "Admin", "Note"]} rows={(data?.audit ?? []).map((item) => [formatDate(item.createdAt), item.actionType ?? "-", item.fieldName ?? "-", `${item.oldValue ?? "-"} -> ${item.newValue ?? "-"}`, item.reviewedBy ?? "-", item.note ?? "-"])} empty="No product audit history." />}

    {pendingHighImpact && <ConfirmDialog title="Apply nutrition change?" message={`This changes ${pendingHighImpact.fieldName ?? "a nutrition field"} from ${pendingHighImpact.currentValue ?? "empty"} to ${pendingHighImpact.suggestedValue ?? "empty"}. Confirm only after checking the provider evidence shown in this workbench.`} confirmLabel="Apply verified change" danger busy={state === "loading"} onCancel={() => setPendingHighImpact(null)} onConfirm={() => reviewSuggestion(pendingHighImpact, "accept", true)} />}
  </section>;
}

function WorkbenchSuggestionTable({ suggestions, comparisons, onReview, showReviewed = false }: { suggestions: ProductQualitySuggestion[]; comparisons: NonNullable<AdminProductQualityWorkbench["evidence"]>["comparisons"]; onReview: (item: ProductQualitySuggestion, action: "accept" | "reject") => void; showReviewed?: boolean }) {
  return <DataTable columns={["Recommendation", "Current", "Provider evidence", "AI proposal", "Confidence", "Reason", "Actions"]} rows={suggestions.map((item) => {
    const comparison = (comparisons ?? []).find((value) => value.fieldName === evidenceFieldForSuggestion(item.fieldName));
    return [<div className="entity-cell"><strong>{humanizeFeature(item.suggestionType)}</strong><small>{item.fieldName ?? "-"}</small></div>, item.currentValue ?? "-", <div className="badge-stack"><Badge value={comparison?.state ?? "NO_EVIDENCE"} tone={comparison?.state === "MATCH" ? "good" : comparison?.state === "CONFLICT" ? "danger" : "warn"} /><small>{comparison?.reason ?? "No comparable provider evidence."}</small></div>, item.suggestedValue ?? "Review only", `${formatValue(item.confidenceScore)}%`, item.reason ?? "-", item.status === "OPEN" ? <div className="inline-actions"><button className="ghost-button" type="button" disabled={!canApplyWorkbenchSuggestion(item)} onClick={() => onReview(item, "accept")}>{canApplyWorkbenchSuggestion(item) ? "Accept" : "Review only"}</button><button className="ghost-button danger-text" type="button" onClick={() => onReview(item, "reject")}>Reject</button></div> : showReviewed ? <Badge value={item.status ?? "-"} tone={item.status === "ACCEPTED" ? "good" : "neutral"} /> : "-"];
  })} empty="No recommendations for this view." />;
}

const SAFE_NUTRITION_SUGGESTION_TYPES = new Set([
  "MISSING_MACRO_DATA", "MISSING_MICRO_DATA", "SUSPICIOUS_CALORIE_VALUE",
  "MACRO_CALORIE_MISMATCH", "SUSPICIOUS_SODIUM_VALUE"
]);
const SAFE_NUTRITION_PRODUCT_FIELDS = new Set([
  "calories", "protein", "fat", "carbs", "fiber", "sugar", "sodium", "potassium", "cholesterol",
  "calcium", "iron", "magnesium", "zinc", "vitaminA", "vitaminC", "vitaminD", "vitaminE", "vitaminB12",
  "saturatedFat", "transFat", "sugarAlcohol"
]);
const SAFE_SERVING_PRODUCT_FIELDS = new Set(["servingSizeGrams", "servingUnit"]);

function canApplyWorkbenchSuggestion(item: ProductQualitySuggestion) {
  if (!item.suggestedValue) return false;
  const suggestionType = item.suggestionType ?? "";
  if (["NAME_CLEANUP", "DISPLAY_NAME", "LOCALIZATION", "SEARCH_ALIAS"].includes(suggestionType)) return true;
  if (SAFE_NUTRITION_SUGGESTION_TYPES.has(suggestionType)) {
    return SAFE_NUTRITION_PRODUCT_FIELDS.has(item.fieldName ?? "");
  }
  return suggestionType === "MISSING_SERVING_SIZE"
    && SAFE_SERVING_PRODUCT_FIELDS.has(item.fieldName ?? "");
}

function isHighImpactProductSuggestion(item: ProductQualitySuggestion) {
  return HIGH_IMPACT_PRODUCT_FIELDS.has(item.fieldName ?? "");
}

function evidenceFieldForSuggestion(fieldName?: string) {
  return fieldName?.replace(/([a-z])([A-Z])/g, "$1_$2").toUpperCase();
}

function ProductAliasManager({ productId, onError }: { productId?: number; onError: (message: string | null) => void }) {
  const [aliases, setAliases] = useState<FoodSearchAlias[]>([]);
  const [aliasText, setAliasText] = useState("");
  const [language, setLanguage] = useState("TR");
  const [aliasType, setAliasType] = useState("ADMIN_MANUAL");
  const [loading, setLoading] = useState(false);
  const [savingAlias, setSavingAlias] = useState(false);

  useEffect(() => {
    if (!productId) {
      setAliases([]);
      return;
    }
    void loadAliases();
  }, [productId]);

  async function loadAliases() {
    if (!productId) return;
    setLoading(true);
    try {
      const result = await request<FoodSearchAlias[]>(`/api/v1/admin/products/${productId}/search-aliases?activeOnly=false`);
      setAliases(result ?? []);
    } catch (err) {
      onError(formatRequestError(err));
    } finally {
      setLoading(false);
    }
  }

  async function addAlias() {
    if (!productId) {
      onError("Product id is missing.");
      return;
    }
    const trimmed = aliasText.trim();
    if (!trimmed) {
      onError("Search alias is required.");
      return;
    }
    setSavingAlias(true);
    onError(null);
    try {
      await request<FoodSearchAlias>(`/api/v1/admin/products/${productId}/search-aliases`, {
        method: "POST",
        body: {
          alias: trimmed,
          language,
          aliasType,
          source: "admin-ui",
          active: true
        }
      });
      setAliasText("");
      await loadAliases();
    } catch (err) {
      onError(formatRequestError(err));
    } finally {
      setSavingAlias(false);
    }
  }

  async function setAliasActive(alias: FoodSearchAlias, active: boolean) {
    if (!productId || !alias.id) return;
    setSavingAlias(true);
    onError(null);
    try {
      const updated = await request<FoodSearchAlias>(`/api/v1/admin/products/${productId}/search-aliases/${alias.id}/status?active=${active}`, {
        method: "PATCH"
      });
      setAliases((current) => current.map((item) => item.id === updated.id ? updated : item));
    } catch (err) {
      onError(formatRequestError(err));
    } finally {
      setSavingAlias(false);
    }
  }

  return (
    <div className="alias-manager">
      <div className="alias-manager-heading">
        <div>
          <span>Search aliases</span>
          <strong>Multilingual product discovery</strong>
        </div>
        <small>{loading ? "Loading aliases..." : `${formatValue(aliases.length)} aliases`}</small>
      </div>
      <div className="alias-form">
        <label>
          Alias
          <input value={aliasText} onChange={(event) => setAliasText(event.target.value)} placeholder="sut, yarim yagli sut, skimmed milk" />
        </label>
        <label>
          Language
          <select value={language} onChange={(event) => setLanguage(event.target.value)}>
            {PREFERRED_LANGUAGES.map((value) => <option key={value} value={value}>{value}</option>)}
          </select>
        </label>
        <label>
          Type
          <select value={aliasType} onChange={(event) => setAliasType(event.target.value)}>
            {FOOD_SEARCH_ALIAS_TYPES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
          </select>
        </label>
        <button className="primary-button" type="button" disabled={savingAlias || !aliasText.trim()} onClick={addAlias}>Add alias</button>
      </div>
      <div className="alias-list">
        {aliases.length ? aliases.map((alias) => (
          <div className={alias.active ? "alias-row" : "alias-row inactive"} key={alias.id ?? `${alias.language}-${alias.alias}`}>
            <div>
              <strong>{alias.alias}</strong>
              <small>{alias.normalizedAlias ?? "-"}</small>
            </div>
            <Badge value={alias.language} tone="neutral" />
            <Badge value={alias.aliasType} />
            <button
              className="ghost-button"
              type="button"
              disabled={savingAlias}
              onClick={() => setAliasActive(alias, !alias.active)}
            >
              {alias.active ? "Disable" : "Enable"}
            </button>
          </div>
        )) : <span className="muted-text">No aliases yet. Add Turkish or English search terms without duplicating this product.</span>}
      </div>
    </div>
  );
}
function TargetContextBanner({ context, onClear }: { context: AdminTargetContext; onClear?: () => void }) {
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

function TargetAwareValue({ value, focused }: { value: ReactNode; focused: boolean }) {
  return (
    <span className={focused ? "target-aware-value focused" : "target-aware-value"}>
      {focused && <i>Target</i>}
      <strong>{value}</strong>
    </span>
  );
}
function DetailItem({ label, value }: { label: string; value?: string | number | null }) {
  return (
    <div className="detail-item">
      <span>{label}</span>
      <strong>{formatValue(value)}</strong>
    </div>
  );
}

function EditableDetail({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="detail-item editable-detail">
      <span>{label}</span>
      {children}
    </div>
  );
}

function NutritionInputGrid({
  title,
  fields,
  draft,
  onChange
}: {
  title: string;
  fields: Array<{ key: ProductReviewNumberField; label: string; suffix: string }>;
  draft: ProductReviewDraft;
  onChange: <K extends keyof ProductReviewDraft>(key: K, value: ProductReviewDraft[K]) => void;
}) {
  return (
    <div className="nutrition-section">
      <div className="nutrition-section-header">
        <h3>{title}</h3>
        <span>Leave blank to clear a value</span>
      </div>
      <div className="nutrition-input-grid">
        {fields.map((field) => (
          <label className="nutrition-input" key={field.key}>
            <span>{field.label}</span>
            <div className="nutrition-input-control">
              <input
                inputMode="decimal"
                type="number"
                min="0"
                step="0.01"
                value={draft[field.key]}
                onChange={(event) => onChange(field.key, event.target.value)}
              />
              <em>{field.suffix}</em>
            </div>
          </label>
        ))}
      </div>
    </div>
  );
}
function MetricPill({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric-pill">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function HealthStatusCard({ label, value, detail, tone = "good" }: { label: string; value: string; detail: string; tone?: "good" | "warn" }) {
  return (
    <article className={`health-status-card ${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{detail}</small>
    </article>
  );
}

function GaugeChart({ label, value, detail, tone = "good" }: { label: string; value: number; detail: string; tone?: "good" | "danger" }) {
  const safeValue = clamp(value, 0, 100);
  return (
    <article className={`chart-card ${tone}`}>
      <div className="gauge" style={{ "--value": safeValue } as CSSProperties}>
        <strong>{safeValue}%</strong>
      </div>
      <div>
        <h3>{label}</h3>
        <p>{detail}</p>
      </div>
    </article>
  );
}

function MiniBarChart({ label, items }: { label: string; items: Array<[string, number]> }) {
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

type HealthCategory = {
  title: string;
  description: string;
  tone?: "default" | "good" | "warn" | "danger";
  items: Array<[string, unknown]>;
};

function HealthCategoryCard({ category }: { category: HealthCategory }) {
  return (
    <article className={`health-category-card ${category.tone ?? "default"}`}>
      <header>
        <div>
          <h3>{category.title}</h3>
          <p>{category.description}</p>
        </div>
        <span>{category.items.length}</span>
      </header>
      <div className="health-kv-list">
        {category.items.map(([label, value]) => (
          <div key={label}>
            <span>{label}</span>
            <strong>{formatHealthValue(value)}</strong>
          </div>
        ))}
      </div>
    </article>
  );
}

function ConfigCheck({ label, value }: { label: string; value: boolean }) {
  return (
    <div className={value ? "config-check good" : "config-check warn"}>
      <span>{label}</span>
      <strong>{value ? "OK" : "Check"}</strong>
    </div>
  );
}

function RevenueCatPlanMappingCard({
  plan,
  productIds,
  entitlements
}: {
  plan: "PLUS" | "PRO";
  productIds?: string[];
  entitlements?: string[];
}) {
  return (
    <article className={`mapping-card ${plan.toLowerCase()}`}>
      <header>
        <span>{plan}</span>
        <Badge value={(productIds?.length || entitlements?.length) ? "Mapped" : "Missing"} tone={(productIds?.length || entitlements?.length) ? "good" : "warn"} />
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

function PillList({ values, empty }: { values?: string[]; empty: string }) {
  if (!values?.length) {
    return <span className="muted-text">{empty}</span>;
  }
  return (
    <div className="pill-list">
      {values.map((value) => <span key={value}>{value}</span>)}
    </div>
  );
}

function UserCell({ user }: { user: UserProfile }) {
  return (
    <div className="entity-cell">
      <strong>{user.name ?? user.email ?? "Unnamed user"}</strong>
      <small>{user.email ?? `ID ${user.id ?? "-"}`}</small>
    </div>
  );
}

function SubscriptionEventModal({
  event,
  retryState,
  onClose,
  onRetry
}: {
  event: SubscriptionProviderEvent;
  retryState: LoadState;
  onClose: () => void;
  onRetry: () => void;
}) {
  const canRetry = event.status === "FAILED";
  return (
    <div className="modal-backdrop">
      <section className="audit-modal" role="dialog" aria-modal="true" aria-label="Subscription provider event detail" onClick={(event) => event.stopPropagation()}>
        <header>
          <div>
            <p className="eyebrow">Provider event</p>
            <h2>{event.providerEventId ?? `Event #${formatValue(event.id)}`}</h2>
          </div>
          <button className="icon-button" onClick={onClose} type="button" aria-label="Close">x</button>
        </header>
        <div className="modal-grid">
          <DetailItem label="Provider" value={event.provider} />
          <DetailItem label="Event type" value={event.eventType} />
          <DetailItem label="Product" value={event.productId} />
          <DetailItem label="Entitlements" value={event.entitlementIds} />
          <DetailItem label="User" value={event.userEmail ?? event.userId ?? event.providerAppUserId} />
          <DetailItem label="Transaction" value={event.transactionId} />
          <DetailItem label="Original transaction" value={event.originalTransactionId} />
          <DetailItem label="Status" value={event.status} />
          <DetailItem label="Received" value={formatDate(event.receivedAt)} />
          <DetailItem label="Processed" value={formatDate(event.processedAt)} />
        </div>
        {event.processingError && <Panel title="Processing error">
          <pre className="audit-value-block">{event.processingError}</pre>
        </Panel>}
        {event.rawPayload && <Panel title="Raw payload">
          <pre className="audit-value-block">{event.rawPayload}</pre>
        </Panel>}
        <footer className="modal-actions">
          <button className="ghost-button" onClick={onClose} type="button">Close</button>
          {canRetry && <button className="primary-button" disabled={retryState === "loading"} onClick={onRetry} type="button">Retry event</button>}
        </footer>
      </section>
    </div>
  );
}

function UserDetailsModal({
  user,
  statusState,
  onClose,
  onStatusUpdate
}: {
  user: UserProfile;
  statusState: LoadState;
  onClose: () => void;
  onStatusUpdate: (payload: { accountEnabled: boolean; accountLocked: boolean; reason: string }) => Promise<void>;
}) {
  const [accountEnabled, setAccountEnabled] = useState(user.accountEnabled !== false);
  const [accountLocked, setAccountLocked] = useState(Boolean(user.accountLocked));
  const [reason, setReason] = useState("");

  useEffect(() => {
    setAccountEnabled(user.accountEnabled !== false);
    setAccountLocked(Boolean(user.accountLocked));
    setReason("");
  }, [user.id, user.accountEnabled, user.accountLocked]);

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <section className="user-modal" role="dialog" aria-modal="true" aria-label="User details" onClick={(event) => event.stopPropagation()}>
        <header className="modal-header">
          <div>
            <p className="eyebrow">Profile and account status</p>
            <h2>{user.name ?? "Unnamed user"}</h2>
            <span>{user.email ?? `ID ${user.id ?? "-"}`}</span>
          </div>
          <button className="icon-button" onClick={onClose} type="button" aria-label="Close">x</button>
        </header>
        <div className="user-detail-layout">
          <Panel title="Account">
            <div className="readonly-grid">
              <DetailItem label="ID" value={formatValue(user.id)} />
              <DetailItem label="Email" value={user.email} />
              <DetailItem label="Role" value={user.role} />
              <DetailItem label="Account enabled" value={user.accountEnabled === false ? "No" : "Yes"} />
              <DetailItem label="Account locked" value={user.accountLocked ? "Yes" : "No"} />
              <DetailItem label="Email verified" value={user.emailVerified ? "Yes" : "No"} />
              <DetailItem label="Password set" value={user.passwordSet ? "Yes" : "No"} />
              <DetailItem label="Region" value={user.marketRegion} />
              <DetailItem label="Language" value={user.preferredLanguage} />
            </div>
          </Panel>
          <Panel title="Account status controls">
            <form className="account-status-panel" onSubmit={(event) => {
              event.preventDefault();
              void onStatusUpdate({ accountEnabled, accountLocked, reason: reason.trim() });
            }}>
              <div className="account-status-summary">
                <div>
                  <span>Current state</span>
                  <strong>{user.accountLocked ? "Locked" : user.accountEnabled === false ? "Disabled" : "Enabled"}</strong>
                </div>
                <Badge value={user.accountLocked || user.accountEnabled === false ? "Restricted" : "Can sign in"} tone={user.accountLocked || user.accountEnabled === false ? "warn" : "good"} />
              </div>
              <div className="account-status-toggles">
                <label className="status-toggle-card">
                  <input checked={accountEnabled} onChange={(event) => setAccountEnabled(event.target.checked)} type="checkbox" />
                  <span>
                    <strong>Allow sign in</strong>
                    <small>Disabled users cannot authenticate.</small>
                  </span>
                </label>
                <label className="status-toggle-card danger">
                  <input checked={accountLocked} onChange={(event) => setAccountLocked(event.target.checked)} type="checkbox" />
                  <span>
                    <strong>Security lock</strong>
                    <small>Locked users are blocked until unlocked by admin.</small>
                  </span>
                </label>
              </div>
              <label className="account-status-reason">
                Admin reason
                <textarea value={reason} onChange={(event) => setReason(event.target.value)} placeholder="Example: Temporary lock after suspicious activity." maxLength={500} />
              </label>
              <div className="account-status-footer">
                <span>{reason.trim().length}/500 audit note characters</span>
                <button className="primary-button" disabled={statusState === "loading"} type="submit">Apply status</button>
              </div>
            </form>
          </Panel>
          <Panel title="Body profile">
            <div className="readonly-grid">
              <DetailItem label="Age" value={formatValue(user.age)} />
              <DetailItem label="Gender" value={user.gender} />
              <DetailItem label="Height" value={user.height === undefined ? "-" : `${formatValue(user.height)} cm`} />
              <DetailItem label="Weight" value={user.weight === undefined ? "-" : `${formatValue(user.weight)} kg`} />
              <DetailItem label="BMI" value={formatValue(user.bmi)} />
              <DetailItem label="Body fat" value={user.bodyFat === undefined ? "-" : `${formatValue(user.bodyFat)}%`} />
            </div>
          </Panel>
          <Panel title="Goal recalculation">
            <div className="readonly-grid">
              <DetailItem label="Recommended" value={user.goalRecalculationRecommended ? "Yes" : "No"} />
              <DetailItem label="Reason" value={user.goalRecalculationReason ?? "-"} />
            </div>
          </Panel>
        </div>
      </section>
    </div>
  );
}
function AuditDetailsModal({ audit, onClose }: { audit: AuditEntry; onClose: () => void }) {
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

function ConfirmDialog({
  title,
  message,
  confirmLabel,
  danger = false,
  busy,
  onCancel,
  onConfirm
}: {
  title: string;
  message: string;
  confirmLabel: string;
  danger?: boolean;
  busy: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <div className="modal-backdrop confirm-backdrop" role="presentation" onClick={onCancel}>
      <section className="confirm-dialog" role="dialog" aria-modal="true" aria-label={title} onClick={(event) => event.stopPropagation()}>
        <div>
          <p className="eyebrow">{danger ? "Confirmation required" : "Confirm action"}</p>
          <h2>{title}</h2>
          <p>{message}</p>
        </div>
        <div className="modal-actions">
          <button className="ghost-button" disabled={busy} onClick={onCancel} type="button">Cancel</button>
          <button className={danger ? "primary-button danger-button" : "primary-button"} disabled={busy} onClick={onConfirm} type="button">
            {busy ? "Saving..." : confirmLabel}
          </button>
        </div>
      </section>
    </div>
  );
}

function Badge({ value, tone = "default" }: { value?: string | null; tone?: "default" | "good" | "warn" | "danger" | "neutral" }) {
  const normalized = value || "-";
  const inferred = normalized.toLowerCase().includes("fail") || normalized.toLowerCase().includes("reject")
    ? "danger"
    : normalized.toLowerCase().includes("verified") || normalized.toLowerCase().includes("enabled")
      ? "good"
      : tone;
  return <span className={`badge ${inferred}`}>{normalized}</span>;
}

function ProgressRow({ label, value, total, tone = "good" }: { label: string; value?: number; total?: number; tone?: "good" | "warn" | "danger" }) {
  const percent = total && total > 0 && value ? Math.min(100, Math.round((value / total) * 100)) : 0;
  return (
    <div className="progress-row">
      <div>
        <span>{label}</span>
        <strong>{formatValue(value)}</strong>
      </div>
      <div className="progress-track">
        <span className={tone} style={{ width: `${percent}%` }} />
      </div>
    </div>
  );
}

function PriorityList({ items }: { items: Array<[string, string | number]> }) {
  return (
    <div className="priority-list">
      {items.map(([label, value]) => (
        <div key={label}>
          <span>{label}</span>
          <strong>{value}</strong>
        </div>
      ))}
    </div>
  );
}

function EmptyState({ message }: { message: string }) {
  return <div className="empty-state">{message}</div>;
}

function formatValue(value: unknown): string {
  if (value === null || value === undefined || value === "") return "-";
  if (typeof value === "number") return new Intl.NumberFormat("en-GB").format(value);
  return String(value);
}

function formatRevenueCatMetric(metric: { value?: string; unit?: string }): string {
  const value = formatValue(metric.value);
  if (!metric.unit) {
    return value;
  }
  if (metric.unit === "EUR" || metric.unit === "USD" || metric.unit === "GBP") {
    return `${metric.unit} ${value}`;
  }
  return `${value} ${metric.unit}`;
}

function formatRevenueCatChartTick(value: number, currency?: string): string {
  const rounded = value >= 10 ? Math.round(value) : Number(value.toFixed(1));
  if (currency === "EUR" || currency === "USD" || currency === "GBP") {
    return `${currency} ${formatValue(rounded)}`;
  }
  return formatValue(rounded);
}

function formatRevenueCatMetricClean(metric: { value?: string; unit?: string }): string {
  const value = formatValue(metric.value);
  if (!metric.unit) {
    return value;
  }
  if (metric.unit === "EUR" || metric.unit === "USD" || metric.unit === "GBP") {
    return `${metric.unit} ${value}`;
  }
  return `${value} ${metric.unit}`;
}

function isRevenueCatMoneyChart(chartName?: string): boolean {
  return chartName === "revenue" || chartName === "mrr";
}

function formatRevenueCatChartValue(value: number, currency?: string, isMoney = false): string {
  const rounded = Math.abs(value) >= 10 ? Math.round(value) : Number(value.toFixed(1));
  if (!isMoney) {
    return formatValue(rounded);
  }
  if (currency === "EUR" || currency === "USD" || currency === "GBP") {
    return `${currency} ${formatValue(rounded)}`;
  }
  return formatValue(rounded);
}

function isDateLike(value?: string): boolean {
  if (!value) return false;
  return /^\d{4}-\d{2}-\d{2}/.test(value) || /^\d{4}-\d{2}/.test(value);
}

function formatChartDate(value?: string): string {
  if (!value) return "-";
  if (!isDateLike(value)) return value;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("en-GB", { day: "2-digit", month: "short" }).format(date);
}

function dateInputDaysAgo(daysAgo: number): string {
  const date = new Date();
  date.setDate(date.getDate() - daysAgo);
  return date.toISOString().slice(0, 10);
}

function parseDateInput(value?: string): Date | null {
  if (!value) return null;
  const [year, month, day] = value.split("-").map(Number);
  if (!year || !month || !day) return null;
  return new Date(year, month - 1, day);
}

function toDateInputValue(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function addDaysIso(value: string, amount: number): string {
  const date = parseDateInput(value) ?? new Date();
  date.setDate(date.getDate() + amount);
  return toDateInputValue(date);
}
function formatDateInputDisplay(value: string): string {
  const date = parseDateInput(value);
  if (!date) return "-";
  return new Intl.DateTimeFormat("en-GB", { day: "2-digit", month: "2-digit", year: "numeric" }).format(date);
}

function addMonths(date: Date, amount: number): Date {
  return new Date(date.getFullYear(), date.getMonth() + amount, 1);
}

function calendarDays(viewDate: Date): Date[] {
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

function formatMonitoringStatus(value?: string): string {
  if (!value) {
    return "RevenueCat monitoring endpoint is active.";
  }
  if (value.includes("authentication_error") || value.includes("Invalid API key") || value.includes("401")) {
    return "Authentication failed. Check the RevenueCat API secret key and project access.";
  }
  if (value.includes("parameter_error") || value.includes("400 Bad Request")) {
    return "RevenueCat rejected the request parameters. Check the project id, chart range, and currency.";
  }
  return value.length > 180 ? `${value.slice(0, 177)}...` : value;
}

function formatDate(value?: string): string {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("en-GB");
}

function formatHealthValue(value: unknown): string {
  if (value === null || value === undefined || value === "") return "-";
  if (Array.isArray(value)) return value.length ? value.map(String).join(", ") : "-";
  if (typeof value === "object") {
    const entries = Object.entries(value as Record<string, unknown>);
    if (!entries.length) return "-";
    return entries.map(([key, item]) => `${key}: ${formatValue(item)}`).join(" | ");
  }
  if (typeof value === "boolean") return value ? "Yes" : "No";
  if (typeof value === "number" && value > 0 && value < 1) return `${Math.round(value * 100)}%`;
  return formatValue(value);
}

function formatAuditValue(value?: string | null): string {
  if (!value) return "-";
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function parseSubscriptionAuditValue(value?: string | null): SubscriptionDto {
  if (!value) return {};
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === "object" ? parsed as SubscriptionDto : {};
  } catch {
    return {};
  }
}

function humanizeSubscriptionAuditAction(action?: string): string {
  switch (action) {
    case "SUBSCRIPTION_UPDATE": return "Subscription update";
    case "AI_QUOTA_RESET": return "AI quota reset";
    case "AI_QUOTA_ADDON_GRANT": return "AI quota grant";
    default: return action ? humanize(action.toLowerCase()) : "-";
  }
}

function subscriptionAuditChangeLines(audit: AuditEntry): string[] {
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

function humanize(value: string): string {
  return value.replace(/([A-Z])/g, " $1").replace(/^./, (char) => char.toUpperCase());
}

function formatModuleTotal(module?: AdminTrackingModuleSummary): string {
  if (!module) return "-";
  const total = formatValue(module.totalValueLastRange);
  switch (module.totalValueUnit) {
    case "ml":
      return `${total} ml`;
    case "minutes":
      return `${total} min`;
    case "steps":
      return `${total} steps`;
    default:
      return total;
  }
}

function trendBarItems(mode: TrackingMode, trends: AdminTrackingTrendPoint[]): Array<[string, number]> {
  return trends.slice(-10).map((point) => [
    formatChartDate(point.date),
    trackingTrendValue(mode, point)
  ]);
}

function trackingTrendValue(mode: TrackingMode, point: AdminTrackingTrendPoint): number {
  if (mode === "water") return point.waterMl ?? 0;
  if (mode === "fasting") return point.fastingMinutes ?? 0;
  if (mode === "steps") return point.steps ?? 0;
  return (point.waterLogs ?? 0) + (point.fastingSessions ?? 0) + (point.stepRecords ?? 0);
}

function trackingTrendColumns(mode: TrackingMode): string[] {
  if (mode === "water") return ["Date", "Water", "Logs", "Users"];
  if (mode === "fasting") return ["Date", "Fasting", "Sessions", "Users"];
  if (mode === "steps") return ["Date", "Steps", "Records", "Users"];
  return ["Date", "Water", "Fasting", "Steps", "Users"];
}

function trackingTrendRows(mode: TrackingMode, trends: AdminTrackingTrendPoint[]): ReactNode[][] {
  return trends.slice().reverse().map((point) => {
    if (mode === "water") {
      return [
        formatDate(point.date),
        `${formatValue(point.waterMl)} ml`,
        formatValue(point.waterLogs),
        formatValue(point.waterUsers)
      ];
    }
    if (mode === "fasting") {
      return [
        formatDate(point.date),
        `${formatValue(point.fastingMinutes)} min`,
        formatValue(point.fastingSessions),
        formatValue(point.fastingUsers)
      ];
    }
    if (mode === "steps") {
      return [
        formatDate(point.date),
        formatValue(point.steps),
        formatValue(point.stepRecords),
        formatValue(point.stepUsers)
      ];
    }
    return [
      formatDate(point.date),
      `${formatValue(point.waterMl)} ml`,
      `${formatValue(point.fastingMinutes)} min`,
      formatValue(point.steps),
      `${formatValue(Math.max(point.waterUsers ?? 0, point.fastingUsers ?? 0, point.stepUsers ?? 0))} max`
    ];
  });
}

function healthCategories(data: SystemHealth): HealthCategory[] {
  const warnings = Array.isArray(data.warnings) ? data.warnings : [];
  return [
    {
      title: "Application Runtime",
      description: "Application identity, active profile, uptime, and JVM capacity.",
      tone: data.status === "UP" ? "good" : "danger",
      items: [
        ["Status", data.status],
        ["Application", data.appName],
        ["Version", data.appVersion],
        ["Profiles", data.activeProfiles],
        ["Uptime", formatDurationMs(readNumber(data, "uptimeMs"))],
        ["Processors", data.availableProcessors],
        ["Checked at", formatDate(typeof data.checkedAt === "string" ? data.checkedAt : undefined)]
      ]
    },
    {
      title: "Database",
      description: "Primary database connectivity and response latency.",
      tone: data.databaseStatus === "UP" ? "good" : "danger",
      items: [
        ["Status", data.databaseStatus],
        ["Latency", `${formatValue(data.databaseLatencyMs)} ms`]
      ]
    },
    {
      title: "Memory",
      description: "Heap usage and available runtime memory envelope.",
      tone: percent(readNumber(data, "heapUsedMb"), readNumber(data, "heapMaxMb")) > 85 ? "warn" : "good",
      items: [
        ["Heap used", `${formatValue(data.heapUsedMb)} MB`],
        ["Heap max", `${formatValue(data.heapMaxMb)} MB`],
        ["Heap usage", `${percent(readNumber(data, "heapUsedMb"), readNumber(data, "heapMaxMb"))}%`]
      ]
    },
    {
      title: "Subscriptions",
      description: "RevenueCat provider events, paid state, and quota pressure.",
      tone: readNumber(data, "failedRevenueCatEvents") ? "warn" : "good",
      items: [
        ["RevenueCat events 24h", data.revenueCatEventsLast24h],
        ["Failed events", data.failedRevenueCatEvents],
        ["Active subscriptions", data.activeSubscriptions],
        ["Exhausted AI quota", data.exhaustedAiQuotaSubscriptions]
      ]
    },
    {
      title: "AI Provider",
      description: "AI provider switch, model selection, and request reliability.",
      tone: readNumber(data, "failedAiRequestsLast24h") ? "warn" : "good",
      items: [
        ["Enabled", data.aiEnabled],
        ["Provider", data.aiProvider],
        ["Model", data.aiModel],
        ["Requests 24h", data.aiRequestsLast24h],
        ["Failed requests 24h", data.failedAiRequestsLast24h],
        ["Failure rate 24h", data.aiFailureRateLast24h]
      ]
    },
    {
      title: "AI Meal Drafts",
      description: "User review behavior and recent AI draft lifecycle quality.",
      tone: readNumber(data, "aiDraftConfirmationRateLast7d") && (readNumber(data, "aiDraftConfirmationRateLast7d") ?? 0) < 0.5 ? "warn" : "good",
      items: [
        ["Drafts 7d", data.aiDraftsLast7d],
        ["Confirmed 7d", data.confirmedAiDraftsLast7d],
        ["Rejected 7d", data.rejectedAiDraftsLast7d],
        ["Open 7d", data.openAiDraftsLast7d],
        ["Confirmation rate", data.aiDraftConfirmationRateLast7d],
        ["Rejection reasons", data.aiRejectionReasonsLast7d]
      ]
    },
    {
      title: "Alerts",
      description: "Operational warnings and system alert notification volume.",
      tone: warnings.length || readNumber(data, "systemAlertsLast24h") ? "warn" : "good",
      items: [
        ["System alerts 24h", data.systemAlertsLast24h],
        ["Warnings", warnings]
      ]
    }
  ];
}


function accessFeatureValue(access: SubscriptionFeatureAccess | null, feature: string): boolean {
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
    case "ADVANCED_MACRO_TARGETS":
      return Boolean(access.advancedMacroTargets);
    case "MICRONUTRIENT_DETAILS":
      return Boolean(access.micronutrientDetails);
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
function uniqueFeatures(items: FeatureMatrixItem[]): string[] {
  return Array.from(new Set(items.map((item) => item.feature).filter(Boolean) as string[])).sort((a, b) => {
    const orderA = FEATURE_ORDER.indexOf(a);
    const orderB = FEATURE_ORDER.indexOf(b);
    if (orderA !== -1 || orderB !== -1) {
      return (orderA === -1 ? Number.MAX_SAFE_INTEGER : orderA) - (orderB === -1 ? Number.MAX_SAFE_INTEGER : orderB);
    }
    return a.localeCompare(b);
  });
}

function featureKey(item: FeatureMatrixItem): string {
  return `${item.planType ?? "-"}:${item.feature ?? "-"}`;
}

function humanizeFeature(value?: string): string {
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
    case "ADVANCED_MACRO_TARGETS":
      return "Advanced Macro Targets";
    case "MICRONUTRIENT_DETAILS":
      return "Micronutrient Details";
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

function shortFeature(value?: string): string {
  if (!value) return "-";
  return value.replace(/_/g, " ");
}

function featureDescription(value?: string): string {
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
    case "ADVANCED_MACRO_TARGETS":
      return "Flexible macro targets and meal-level planning.";
    case "MICRONUTRIENT_DETAILS":
      return "Detailed vitamins and minerals in nutrition views.";
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

function defaultAiQuota(planType?: string): number {
  if (planType === "PRO") return 150;
  if (planType === "PLUS") return 50;
  return 0;
}

function parseNonNegativeInt(value: string): number {
  const parsed = Number.parseInt(value || "0", 10);
  if (!Number.isFinite(parsed) || parsed < 0) return 0;
  return parsed;
}

function parsePositiveInt(value: string): number {
  const parsed = Number.parseInt(value || "0", 10);
  if (!Number.isFinite(parsed) || parsed <= 0) return 1;
  return parsed;
}
function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10);
}

function listPreview(values?: string[]): string {
  if (!values?.length) return "-";
  if (values.length <= 2) return values.join(", ");
  return `${values.slice(0, 2).join(", ")} +${values.length - 2}`;
}

function buildProductReviewPath(filters: {
  query: string;
  verificationStatus: string;
  imageStatus: string;
  region: string;
  catalogType: string;
  dataSource: string;
  qualityIssue: string;
  page: number;
  size: number;
}): string {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (key !== "page" && key !== "size" && value) params.set(key, String(value));
  });
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));
  return `/api/v1/admin/products/review?${params.toString()}`;
}

function buildProductReviewExportPath(filters: {
  query: string;
  verificationStatus: string;
  imageStatus: string;
  region: string;
  catalogType: string;
  dataSource: string;
  qualityIssue: string;
  limit: number;
}): string {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value) params.set(key, String(value));
  });
  return `/api/v1/admin/products/review/export?${params.toString()}`;
}

function downloadBlob(blob: Blob, filename: string) {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}
function buildRecipeImportPath(filters: {
  status: string;
  batchId: string;
  page: number;
  size: number;
}): string {
  const params = new URLSearchParams();
  if (filters.status) params.set("status", filters.status);
  if (filters.batchId.trim()) params.set("batchId", filters.batchId.trim());
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));
  return `/api/v1/admin/recipes/imports?${params.toString()}`;
}
function buildRecipeAdminPath(filters: {
  query: string;
  verificationStatus: string;
  visibility: string;
  archived: string;
  ownerEmail: string;
  mealType: string;
  marketRegion: string;
  imageStatus: string;
  imageSource: string;
  page: number;
  size: number;
}): string {
  const params = new URLSearchParams();
  if (filters.query) params.set("query", filters.query);
  if (filters.verificationStatus) params.set("verificationStatus", filters.verificationStatus);
  if (filters.visibility) params.set("visibility", filters.visibility);
  if (filters.archived) params.set("archived", filters.archived);
  if (filters.ownerEmail) params.set("ownerEmail", filters.ownerEmail);
  if (filters.mealType) params.set("mealType", filters.mealType);
  if (filters.marketRegion) params.set("marketRegion", filters.marketRegion);
  if (filters.imageStatus) params.set("imageStatus", filters.imageStatus);
  if (filters.imageSource) params.set("imageSource", filters.imageSource);
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));
  return `/api/v1/admin/recipes?${params.toString()}`;
}

function buildAiOperationsPath(filters: {
  requestType: string;
  status: string;
  refundableOnly: boolean;
  page: number;
  size: number;
}): string {
  const params = new URLSearchParams();
  if (filters.requestType) params.set("requestType", filters.requestType);
  if (filters.status) params.set("status", filters.status);
  params.set("refundableOnly", String(filters.refundableOnly));
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));
  return `/api/v1/admin/ai/requests?${params.toString()}`;
}

function buildProductQualitySuggestionPath(filters: { status: string; page: number; size: number }): string {
  const params = new URLSearchParams();
  params.set("status", filters.status || "OPEN");
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));
  return `/api/v1/admin/products/quality-suggestions?${params.toString()}`;
}

function buildSubscriptionEventsPath(filters: {
  status: string;
  eventType: string;
  productId: string;
  userId: string;
  page: number;
  size: number;
}): string {
  const params = new URLSearchParams();
  if (filters.status) params.set("status", filters.status);
  if (filters.eventType.trim()) params.set("eventType", filters.eventType.trim());
  if (filters.productId.trim()) params.set("productId", filters.productId.trim());
  if (filters.userId.trim()) params.set("userId", filters.userId.trim());
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));
  return `/api/v1/admin/subscription-events?${params.toString()}`;
}

function notificationRuleCards(): Array<{ type: string; label: string; source: string; severity: "CRITICAL" | "WARNING" | "INFO"; tone: "danger" | "warn" | "neutral"; route: SectionKey; description: string }> {
  return [
    {
      type: "subscription_provider_alert",
      label: "Subscription provider failure",
      source: "RevenueCat",
      severity: "CRITICAL",
      tone: "danger",
      route: "subscriptionEvents",
      description: "Payment/provider events failed or could not be mapped. Admin should inspect raw payload and retry when safe."
    },
    {
      type: "system_alert",
      label: "Mail/provider system alert",
      source: "Mail provider",
      severity: "CRITICAL",
      tone: "danger",
      route: "mail",
      description: "Transactional mail delivery failed or provider configuration needs attention. Admin should verify Brevo health and event logs."
    },
    {
      type: "ai_rejection_alert",
      label: "AI result rejected by user",
      source: "AI Ops",
      severity: "WARNING",
      tone: "warn",
      route: "ai",
      description: "A user rejected an AI result with feedback. Admin should review request quality, prompt behavior, and refundable quota state."
    }
  ];
}

function notificationTypeLabel(value?: string): string {
  switch (value) {
    case "subscription_provider_alert":
      return "Provider failure";
    case "system_alert":
      return "System alert";
    case "ai_rejection_alert":
      return "AI rejection";
    case "subscription":
      return "Subscription";
    default:
      return shortFeature(value);
  }
}

function notificationSourceLabel(value?: string): string {
  switch (value) {
    case "REVENUECAT":
      return "RevenueCat";
    case "MAIL_PROVIDER":
      return "Mail provider";
    case "AI_OPS":
      return "AI Ops";
    default:
      return shortFeature(value);
  }
}
function buildNotificationPath(filters: { type: string; severity: string; unreadOnly: boolean; page: number; size: number }): string {
  const params = new URLSearchParams();
  if (filters.type) params.set("type", filters.type);
  if (filters.severity) params.set("severity", filters.severity);
  if (filters.unreadOnly) params.set("unreadOnly", "true");
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));
  return `/api/v1/notifications?${params.toString()}`;
}

function notificationSeverityTone(value?: string): "default" | "good" | "warn" | "danger" | "neutral" {
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

function notificationTargetSection(item: Notification): SectionKey | null {
  const candidate = item.targetRoute || fallbackNotificationTargetRoute(item.type, item.targetType);
  return isSectionKey(candidate) ? candidate : null;
}

function notificationTargetLabel(item: Notification): string {
  const target = item.targetType || fallbackNotificationTargetLabel(item.type);
  if (!target) return "-";
  return item.targetId ? `${shortFeature(target)} #${item.targetId}` : shortFeature(target);
}

function notificationTargetContext(item: Notification): Omit<AdminTargetContext, "section"> {
  return {
    source: "notification",
    notificationId: item.id,
    severity: item.severity,
    type: item.type,
    message: item.message,
    targetType: item.targetType,
    targetId: item.targetId,
    targetRoute: item.targetRoute
  };
}

function isTargetMatch(targetId: string | undefined, value: unknown): boolean {
  return Boolean(targetId && value !== null && value !== undefined && String(value) === String(targetId));
}

function fallbackNotificationTargetRoute(type?: string, targetType?: string): string | undefined {
  if (targetType === "AI_REQUEST" || type === "ai_rejection_alert") return "ai";
  if (targetType === "MAIL_MONITORING" || type === "system_alert") return "mail";
  if (targetType === "SUBSCRIPTION_PROVIDER_EVENT" || type === "subscription_provider_alert") return "subscriptionEvents";
  return undefined;
}

function fallbackNotificationTargetLabel(type?: string): string | undefined {
  if (type === "ai_rejection_alert") return "AI_REQUEST";
  if (type === "system_alert") return "SYSTEM_ALERT";
  if (type === "subscription_provider_alert") return "SUBSCRIPTION_PROVIDER_EVENT";
  return undefined;
}

function isSectionKey(value?: string): value is SectionKey {
  return Boolean(value && sectionByKey[value as SectionKey]);
}
function humanizeAiRequestType(value?: string): string {
  switch (value) {
    case "VOICE_FOOD_LOG":
      return "Voice meal draft";
    case "PHOTO_MEAL_LOG":
      return "Photo meal draft";
    case "AI_RECIPE_GENERATION":
      return "Recipe assistant";
    case "AI_WORKOUT_PLAN":
      return "Workout planner";
    case "AI_DAILY_INSIGHT":
      return "Daily insight";
    case "AI_WEEKLY_INSIGHT":
      return "Weekly insight";
    default:
      return shortFeature(value);
  }
}

function aiStatusTone(value?: string): "default" | "good" | "warn" | "danger" | "neutral" {
  switch (value) {
    case "CONFIRMED":
      return "good";
    case "REJECTED":
      return "warn";
    case "FAILED":
      return "danger";
    case "DRAFT_CREATED":
    case "PENDING":
      return "neutral";
    default:
      return "default";
  }
}

function subscriptionEventTone(value?: string): "default" | "good" | "warn" | "danger" | "neutral" {
  switch (value) {
    case "PROCESSED":
      return "good";
    case "FAILED":
      return "danger";
    case "IGNORED":
      return "neutral";
    case "RECEIVED":
      return "warn";
    default:
      return "default";
  }
}

function safeNumber(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) ? value : 0;
}

function summaryWindowLabel(hours: number): string {
  if (hours === 24) return "24h";
  if (hours === 168) return "7d";
  if (hours === 744) return "31d";
  return `${hours}h`;
}

function formatFailureRate(value?: number): string {
  if (typeof value !== "number" || !Number.isFinite(value)) return "-";
  return `${(value * 100).toFixed(1)}%`;
}

function formatCurrencyBreakdown(value?: Record<string, number>): string {
  const entries = Object.entries(value ?? {}).filter(([, amount]) => typeof amount === "number" && Number.isFinite(amount));
  if (!entries.length) return "-";
  return entries.map(([currency, amount]) => formatAiCostAmount(amount, currency)).join(" / ");
}

function formatAiCostAmount(amount?: number, currency?: string): string {
  if (typeof amount !== "number" || !Number.isFinite(amount)) return "-";
  return `${amount.toFixed(4)} ${currency ?? ""}`.trim();
}

function aiRequestId(item: AiMealDraft): number | undefined {
  return item.requestId ?? item.id;
}

function canRefundAiRequest(item: AiMealDraft): boolean {
  return item.status === "REJECTED"
    && Boolean(item.quotaConsumed)
    && safeNumber(item.refundableAmount) > 0;
}
function formatAiCost(item: AiMealDraft): string {
  if (typeof item.estimatedCost !== "number") return "-";
  return `${item.estimatedCost.toFixed(4)} ${item.costCurrency ?? ""}`.trim();
}
function buildAuditPath(filters: {
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

function combineStates(states: LoadState[]): LoadState {
  if (states.includes("error")) return "error";
  if (states.includes("loading")) return "loading";
  if (states.every((state) => state === "ready")) return "ready";
  return "idle";
}

function countBy<T>(items: T[], readKey: (item: T) => string): Record<string, number> {
  return items.reduce<Record<string, number>>((accumulator, item) => {
    const key = readKey(item) || "Unknown";
    accumulator[key] = (accumulator[key] ?? 0) + 1;
    return accumulator;
  }, {});
}

function toProductReviewDraft(item: FoodProduct): ProductReviewDraft {
  return {
    productName: productName(item),
    displayImageUrl: item.displayImageUrl ?? item.imageUrl ?? item.externalImageUrl ?? "",
    marketRegion: item.marketRegion ?? "",
    verificationStatus: item.verificationStatus ?? "",
    imageStatus: item.imageStatus ?? "",
    imageSource: item.imageSource ?? "",
    catalogType: item.catalogType ?? "",
    calories: numberInputValue(item.calories),
    protein: numberInputValue(item.protein),
    carbs: numberInputValue(item.carbs),
    fat: numberInputValue(item.fat),
    fiber: numberInputValue(item.fiber),
    sugar: numberInputValue(item.sugar),
    sodium: numberInputValue(item.sodium),
    potassium: numberInputValue(item.potassium),
    cholesterol: numberInputValue(item.cholesterol),
    calcium: numberInputValue(item.calcium),
    iron: numberInputValue(item.iron),
    magnesium: numberInputValue(item.magnesium),
    zinc: numberInputValue(item.zinc),
    vitaminA: numberInputValue(item.vitaminA),
    vitaminC: numberInputValue(item.vitaminC),
    vitaminD: numberInputValue(item.vitaminD),
    vitaminE: numberInputValue(item.vitaminE),
    vitaminB12: numberInputValue(item.vitaminB12),
    saturatedFat: numberInputValue(item.saturatedFat),
    transFat: numberInputValue(item.transFat),
    sugarAlcohol: numberInputValue(item.sugarAlcohol),
    servingSizeGrams: numberInputValue(item.servingSize),
    servingUnit: item.servingUnit ?? ""
  };
}

function productReviewNutritionPayload(draft: ProductReviewDraft) {
  return {
    calories: parseOptionalNumber(draft.calories),
    protein: parseOptionalNumber(draft.protein),
    carbs: parseOptionalNumber(draft.carbs),
    fat: parseOptionalNumber(draft.fat),
    fiber: parseOptionalNumber(draft.fiber),
    sugar: parseOptionalNumber(draft.sugar),
    sodium: parseOptionalNumber(draft.sodium),
    potassium: parseOptionalNumber(draft.potassium),
    cholesterol: parseOptionalNumber(draft.cholesterol),
    calcium: parseOptionalNumber(draft.calcium),
    iron: parseOptionalNumber(draft.iron),
    magnesium: parseOptionalNumber(draft.magnesium),
    zinc: parseOptionalNumber(draft.zinc),
    vitaminA: parseOptionalNumber(draft.vitaminA),
    vitaminC: parseOptionalNumber(draft.vitaminC),
    vitaminD: parseOptionalNumber(draft.vitaminD),
    vitaminE: parseOptionalNumber(draft.vitaminE),
    vitaminB12: parseOptionalNumber(draft.vitaminB12),
    saturatedFat: parseOptionalNumber(draft.saturatedFat),
    transFat: parseOptionalNumber(draft.transFat),
    sugarAlcohol: parseOptionalNumber(draft.sugarAlcohol),
    servingSizeGrams: parseOptionalNumber(draft.servingSizeGrams),
    servingUnit: draft.servingUnit.trim() || null
  };
}

function numberInputValue(value?: number | null): string {
  return typeof value === "number" && Number.isFinite(value) ? String(value) : "";
}

function parseOptionalNumber(value: string): number | null {
  const trimmed = value.trim();
  if (!trimmed) return null;
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : null;
}

function productName(item: FoodProduct): string {
  return item.productName ?? item.name ?? "Unnamed product";
}

function productIngredientLabel(item: FoodProduct): string {
  const parts = [
    item.id ? `#${item.id}` : null,
    item.brand || null,
    item.barcode || null,
    item.marketRegion || null,
    typeof item.calories === "number" ? `${formatValue(item.calories)} kcal` : null
  ].filter(Boolean);
  return parts.length ? parts.join(" | ") : "Product selected";
}

function readNumber(data: SystemHealth | null, key: string): number | undefined {
  const value = data?.[key];
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

function readCounter(data: AdminMailMonitoring | null, key: string): number {
  const value = data?.counters?.[key];
  return typeof value === "number" && Number.isFinite(value) ? value : 0;
}

function percent(value?: number, total?: number): number {
  if (!value || !total || total <= 0) return 0;
  return clamp(Math.round((value / total) * 100), 0, 100);
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

function readStoredTheme(): ThemeMode {
  const stored = window.localStorage.getItem(THEME_KEY);
  if (stored === "light" || stored === "dark") return stored;
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function formatDurationMs(value?: number): string {
  if (!value || value < 0) return "-";
  const totalSeconds = Math.floor(value / 1000);
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${minutes}m`;
}
