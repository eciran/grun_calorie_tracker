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
  AdminTrackingTrendPoint,
  AdminAchievementDefinition,
  AdminAchievementMetrics,
  AiMealDraft,
  AuditEntry,
  DashboardSummary,
  FeatureMatrixItem,
  FoodProduct,
  FoodSearchAlias,
  AdminRecipe,
  Notification,
  RevenueCatChart,
  RevenueCatConfigStatus,
  RevenueCatMonitoringCharts,
  RevenueCatMonitoringOverview,
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
  | "subscriptionAiQuotas"
  | "ai"
  | "audits"
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
type NavigationItem = SectionMeta & { children?: SectionMeta[] };

const THEME_KEY = "grun.admin.theme";

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
  { key: "productImages", label: "Image Review", hint: "Product media", icon: "I" },
  { key: "productNutrition", label: "Nutrition Review", hint: "Macro quality", icon: "N" },
  { key: "productRejected", label: "Rejected Products", hint: "Review archive", icon: "R" },
  { key: "recipes", label: "Recipes", hint: "User recipes", icon: "C" },
  { key: "achievements", label: "Achievements", hint: "Badge rules", icon: "B" },
  { key: "users", label: "Users", hint: "Accounts", icon: "U" },
  { key: "admins", label: "Admins", hint: "Admin accounts", icon: "A" },
  { key: "userVerification", label: "Verification", hint: "Email status", icon: "V" },
  { key: "subscriptions", label: "Subscriptions", hint: "Feature matrix", icon: "S" },
  { key: "subscriptionFeatures", label: "Feature Matrix", hint: "Plan rules", icon: "F" },
  { key: "subscriptionMapping", label: "Product Mapping", hint: "Store ids", icon: "M" },
  { key: "subscriptionEntitlements", label: "Entitlements", hint: "Snapshot policy", icon: "E" },
  { key: "subscriptionAiQuotas", label: "AI Quotas", hint: "Credits", icon: "Q" },
  { key: "ai", label: "AI Review", hint: "Drafts/refunds", icon: "A" },
  { key: "audits", label: "Audit Logs", hint: "Admin actions", icon: "L" },
  { key: "notifications", label: "Notifications", hint: "System alerts", icon: "N" },
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

const navigation: NavigationItem[] = [
  sections[0],
  {
    ...sections[1],
    children: [
      { key: "integrations", label: "Overview", hint: "Provider board", icon: "O" },
      sections[2]
    ]
  },
  {
    ...sections[3],
    children: [
      sections[4],
      sections[5]
    ]
  },
  {
    ...sections[6],
    children: [
      { key: "mail", label: "Overview", hint: "Delivery summary", icon: "O" },
      sections[7],
      sections[8]
    ]
  },
  {
    ...sections[9],
    children: [
      { key: "foodOps", label: "Overview", hint: "Catalog summary", icon: "O" },
      sections[10],
      sections[11],
      sections[12]
    ]
  },
  {
    ...sections[13],
    children: [
      { key: "products", label: "Review Queue", hint: "Pending products", icon: "Q" },
      sections[14],
      sections[15],
      sections[16],
      sections[17],
      sections[18]
    ]
  },
  {
    ...sections[19],
    children: [
      { key: "users", label: "App Users", hint: "Customer accounts", icon: "U" },
      sections[20],
      sections[21]
    ]
  },
  {
    ...sections[22],
    children: [
      { key: "subscriptions", label: "Overview", hint: "Plan summary", icon: "O" },
      sections[23],
      sections[24],
      sections[25],
      sections[26]
    ]
  },
  sections[27],
  sections[28],
  {
    ...sections[29],
    children: [
      { key: "notifications", label: "In-app Alerts", hint: "System alerts", icon: "N" },
      sections[30]
    ]
  },
  {
    ...sections[31],
    children: [
      { key: "tracking", label: "Overview", hint: "Module summary", icon: "O" },
      sections[32],
      sections[33],
      sections[34]
    ]
  },
  {
    ...sections[35],
    children: [
      { key: "system", label: "Overview", hint: "Health summary", icon: "O" },
      sections[36],
      sections[37],
      sections[38],
      sections[39]
    ]
  }
];

const MARKET_REGIONS = ["GLOBAL", "TR", "UK_IE", "EU"];
const VERIFICATION_STATUSES = ["RAW_IMPORTED", "NEEDS_REVIEW", "VERIFIED", "REJECTED"];
const IMAGE_STATUSES = ["RAW", "NEEDS_REVIEW", "APPROVED", "REJECTED"];
const IMAGE_SOURCES = ["OPEN_FOOD_FACTS", "ADMIN_UPLOAD", "USER_UPLOAD", "BRAND_OFFICIAL", "AI_GENERATED"];
const CATALOG_TYPES = ["BRANDED_PRODUCT", "GENERIC_INGREDIENT", "LOCAL_DISH", "USER_CUSTOM"];
const DATA_SOURCES = ["OPEN_FOOD_FACTS", "ADMIN_IMPORT", "USDA_FOODDATA", "USER_CUSTOM"];
const PREFERRED_LANGUAGES = ["EN", "TR"];
const FOOD_SEARCH_ALIAS_TYPES = ["ADMIN_MANUAL", "TRANSLATION", "SYNONYM", "ASCII_NORMALIZED", "COMMON_NAME"];
const MEAL_TYPES = ["BREAKFAST", "LUNCH", "DINNER", "SNACK"];
const RECIPE_VISIBILITIES = ["PRIVATE", "PUBLIC_ADMIN", "COMMUNITY_PENDING"];
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
  const [openNavGroups, setOpenNavGroups] = useState<Partial<Record<SectionKey, boolean>>>({});

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
          <div className="brand-mark">G</div>
          <div>
            <strong>GRun Admin</strong>
            <span>Control Center</span>
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
                    setOpenNavGroups((current) => ({ ...current, [section.key]: !current[section.key] }));
                  } else {
                    setActive(section.key);
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
                {section.children && <span className="nav-chevron">{openNavGroups[section.key] ? "-" : "+"}</span>}
              </button>
              {section.children && openNavGroups[section.key] && (
                <div className="nav-sublist">
                  {section.children.map((child) => (
                    <button
                      className={child.key === active && child.label !== section.label ? "nav-subitem active" : "nav-subitem"}
                      key={`${child.key}-${child.label}`}
                      onClick={() => {
                        setError(null);
                        setActive(child.key);
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
        <section className="content-surface">
          {active === "dashboard" && <DashboardView onError={setError} />}
          {active === "integrations" && <IntegrationsView mode="overview" onError={setError} />}
          {active === "integrationProviders" && <IntegrationsView mode="providers" onError={setError} />}
          {active === "revenueCatProduction" && <RevenueCatMonitoringView environment="production" onError={setError} />}
          {active === "revenueCatSandbox" && <RevenueCatMonitoringView environment="sandbox" onError={setError} />}
          {active === "mail" && <MailOpsView onError={setError} />}
          {active === "brevoSenders" && <BrevoSendersView onError={setError} />}
          {active === "mailEvents" && <MailEventsView onError={setError} />}
          {active === "foodOps" && <FoodOpsView mode="overview" onError={setError} />}
          {active === "foodImports" && <FoodOpsView mode="imports" onError={setError} />}
          {active === "foodRegions" && <FoodOpsView mode="regions" onError={setError} />}
          {active === "foodQuality" && <FoodOpsView mode="quality" onError={setError} />}
          {active === "products" && <ProductReviewView mode="queue" onError={setError} />}
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
          {active === "subscriptionAiQuotas" && <SubscriptionsView mode="aiQuotas" onError={setError} />}
          {active === "ai" && <AiReviewView onError={setError} />}
          {active === "audits" && <AuditsView onError={setError} />}
          {active === "notifications" && <NotificationsView onError={setError} />}
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
        <div className="hero-orbit">
          <div className="pulse-card">
            <span>Catalog</span>
            <strong>Review queue</strong>
          </div>
          <div className="pulse-card secondary">
            <span>System</span>
            <strong>Health online</strong>
          </div>
        </div>
        <div className="hero-copy">
          <p className="eyebrow">GRun Operations</p>
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

function DashboardView({ onError }: { onError: (message: string | null) => void }) {
  const { data, state, reload } = useEndpoint<DashboardSummary>("/api/v1/admin/dashboard/summary", onError);
  const activeSubscriptions = (data?.activePlusSubscriptions ?? 0) + (data?.activeProSubscriptions ?? 0);
  const cards = [
    ["Users", data?.totalUsers, "Total registered accounts"],
    ["Pending reviews", data?.reviewQueueProducts, "Products requiring attention"],
    ["Verified products", data?.verifiedProducts, "Approved catalog items"],
    ["Active subs", activeSubscriptions, "Current paid users"],
    ["Failed events", data?.failedSubscriptionProviderEvents, "RevenueCat events to inspect"]
  ];

  return (
    <div className="stack">
      <SectionToolbar title="Operational snapshot" state={state} onReload={reload} />
      <div className="metric-grid">
        {cards.map(([label, value, hint]) => (
          <MetricCard key={label} label={String(label)} value={formatValue(value)} hint={String(hint)} />
        ))}
      </div>
      <div className="split-grid">
        <Panel title="Catalog quality">
          <ProgressRow label="Verified" value={data?.verifiedProducts} total={data?.totalProducts} />
          <ProgressRow label="Pending" value={data?.reviewQueueProducts} total={data?.totalProducts} tone="warn" />
          <ProgressRow label="Rejected" value={data?.rejectedProducts} total={data?.totalProducts} tone="danger" />
        </Panel>
        <Panel title="Admin priorities">
          <PriorityList
            items={[
              ["Review failed payment events", data?.failedSubscriptionProviderEvents ?? 0],
              ["Clear high-priority products", data?.reviewQueueProducts ?? 0],
              ["Check mail/system alerts", "Notifications"]
            ]}
          />
        </Panel>
      </div>
    </div>
  );
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
  const [reviewNote, setReviewNote] = useState("");
  const [saving, setSaving] = useState(false);
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

  function openRecipe(recipe: AdminRecipe) {
    setSelectedRecipe(recipe);
    setDraftStatus(recipe.verificationStatus ?? "");
    setDraftVisibility(recipe.visibility ?? "");
    setDraftArchived(recipe.archived ? "true" : "false");
    setDraftImageUrl(recipe.imageUrl ?? "");
    setDraftImageStatus(recipe.imageStatus ?? "");
    setDraftImageSource(recipe.imageSource ?? "");
    setReviewNote("");
  }

  function closeRecipe() {
    setSelectedRecipe(null);
    setDraftImageUrl("");
    setDraftImageStatus("");
    setDraftImageSource("");
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
          reviewNote: reviewNote || "Updated from admin panel."
        }
      });
      setSelectedRecipe(updated);
      await reload();
      closeRecipe();
    } catch (err) {
      onError(formatRequestError(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="stack">
      <SectionToolbar title="Recipe operations" state={state} onReload={reload}>
        <button className="ghost-button" onClick={resetFilters} type="button">Reset filters</button>
      </SectionToolbar>

      <div className="review-workspace-summary">
        <MetricCard label="Returned recipes" value={formatValue(data?.totalElements ?? rows.length)} hint="Matching current filters" />
        <MetricCard label="Pending review" value={formatValue(pendingCount)} hint="Current page only" />
        <MetricCard label="Saved / favorite" value={`${formatValue(savedTotal)} / ${formatValue(favoriteTotal)}`} hint="Current page engagement" />
        <MetricCard label="Avg rating" value={averageRating ? averageRating.toFixed(1) : "-"} hint={`${formatValue(ratedRows.length)} rated recipes on page`} />
        <MetricCard label="Archived / filters" value={`${formatValue(archivedCount)} / ${formatValue(activeFilterCount)}`} hint="Current page state" />
      </div>

      <Panel title="Recipe filters">
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
      </Panel>

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
          <section className="product-modal" role="dialog" aria-modal="true" aria-label="Recipe admin detail" onClick={(event) => event.stopPropagation()}>
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
                  <div className="tag-cloud">
                    {(selectedRecipe.categories ?? []).length
                      ? selectedRecipe.categories?.map((category) => <Badge key={category} value={category} tone="neutral" />)
                      : <span className="muted-text">No category assigned. Public approval will be blocked until at least one category is present.</span>}
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
  const users = data ?? [];
  const adminUsers = users.filter((user) => user.role === "ADMIN");
  const standardUsers = users.filter((user) => user.role !== "ADMIN");
  const unverifiedUsers = users.filter((user) => !user.emailVerified);
  const visibleUsers = mode === "admins" ? adminUsers : mode === "verification" ? unverifiedUsers : standardUsers;
  const title = mode === "admins" ? "Admin accounts" : mode === "verification" ? "Email verification queue" : "App users";
  return (
    <div className="stack">
      <SectionToolbar title={title} state={state} onReload={reload} />
      <div className="user-summary-grid">
        <MetricCard label="Standard users" value={formatValue(standardUsers.length)} hint="Non-admin accounts" />
        <MetricCard label="Admin users" value={formatValue(adminUsers.length)} hint="Privileged accounts" />
        <MetricCard label="Unverified" value={formatValue(unverifiedUsers.length)} hint="Email verification pending" />
      </div>
      <DataTable
        columns={["User", "Role", "Region", "Language", "Email", "Profile"]}
        rows={visibleUsers.map((user) => [
          <UserCell user={user} />,
          <Badge value={user.role ?? "-"} />,
          formatValue(user.marketRegion),
          formatValue(user.preferredLanguage),
          <Badge value={user.emailVerified ? "Verified" : "Unverified"} tone={user.emailVerified ? "good" : "warn"} />,
          `${formatValue(user.age)} yrs | ${formatValue(user.height)} cm / ${formatValue(user.weight)} kg`
        ])}
        rowData={visibleUsers}
        onRowClick={setSelectedUser}
        empty={mode === "admins" ? "No admin users found." : mode === "verification" ? "No unverified users found." : "No standard users found."}
      />
      {selectedUser && <UserDetailsModal user={selectedUser} onClose={() => setSelectedUser(null)} />}
    </div>
  );
}

type SubscriptionMode = "overview" | "features" | "mapping" | "entitlements" | "aiQuotas";

function SubscriptionsView({ mode, onError }: { mode: SubscriptionMode; onError: (message: string | null) => void }) {
  const { data, state, reload } = useEndpoint<FeatureMatrixItem[]>("/api/v1/admin/subscriptions/features", onError);
  const { data: revenueCat, state: revenueCatState, reload: reloadRevenueCat } = useEndpoint<RevenueCatConfigStatus>("/api/v1/admin/revenuecat/config", onError);
  const [savingKey, setSavingKey] = useState<string | null>(null);
  const features = data ?? [];
  const plans = ["FREE", "PLUS", "PRO"];
  const grouped = plans.map((plan) => ({
    plan,
    items: features.filter((item) => item.planType === plan),
    enabledCount: features.filter((item) => item.planType === plan && item.enabled).length
  }));
  const enabledTotal = features.filter((item) => item.enabled).length;
  const warningCount = (revenueCat?.missingRequiredConfig?.length ?? 0) + (revenueCat?.warnings?.length ?? 0);
  const title = {
    overview: "Subscription control center",
    features: "Plan feature matrix",
    mapping: "RevenueCat product mapping",
    entitlements: "Entitlement snapshot policy",
    aiQuotas: "AI quota and add-on mapping"
  }[mode];

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

  return (
    <div className="stack">
      <SectionToolbar title={title} state={combineStates([state, revenueCatState])} onReload={reload}>
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
          <div className="feature-matrix-head">
            <span>Feature rule</span>
            {plans.map((plan) => <span key={plan}>{plan}</span>)}
          </div>
          {uniqueFeatures(features).map((feature) => (
            <div className="feature-matrix-row" key={feature}>
              <div>
                <strong>{humanizeFeature(feature)}</strong>
                <small>{featureDescription(feature)}</small>
              </div>
              {plans.map((plan) => {
                const item = features.find((candidate) => candidate.planType === plan && candidate.feature === feature);
                const key = item ? featureKey(item) : `${plan}:${feature}`;
                return (
                  <div className={item?.enabled ? "feature-toggle-cell enabled" : "feature-toggle-cell"} key={key}>
                    {item ? (
                      <>
                        <label className="switch-control" title={`${plan} ${feature}`}>
                          <input
                            checked={Boolean(item.enabled)}
                            disabled={savingKey === key}
                            onChange={(event) => updateFeature(item, event.target.checked)}
                            type="checkbox"
                          />
                          <span />
                        </label>
                        <small>{item.effectiveFrom ?? "today"}</small>
                      </>
                    ) : (
                      <span className="muted-text">Not configured</span>
                    )}
                  </div>
                );
              })}
            </div>
          ))}
          {!features.length && <EmptyState message="No subscription feature rows found." />}
        </div>
      </Panel>}

      {(mode === "mapping" || mode === "aiQuotas") && <div className="subscription-ops-grid">
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
        {mode === "aiQuotas" && <Panel title="AI add-on mapping">
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
        </Panel>}
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

function AiReviewView({ onError }: { onError: (message: string | null) => void }) {
  const [refundableOnly, setRefundableOnly] = useState(false);
  const path = `/api/v1/admin/ai/meal-drafts?page=0&size=20&refundableOnly=${refundableOnly}`;
  const { data, state, reload } = useEndpoint<PageResponse<AiMealDraft>>(path, onError);
  return (
    <div className="stack">
      <SectionToolbar title="AI meal draft review" state={state} onReload={reload}>
        <label className="inline-check">
          <input checked={refundableOnly} onChange={(event) => setRefundableOnly(event.target.checked)} type="checkbox" />
          Refundable only
        </label>
      </SectionToolbar>
      <DataTable
        columns={["Request", "User", "Type", "Status", "Quota", "Created"]}
        rows={(data?.content ?? []).map((item) => [
          item.requestId ?? item.id ?? "-",
          item.userEmail ?? "-",
          item.requestType ?? "-",
          <Badge value={item.status} />,
          `${formatValue(item.quotaConsumed)} used / ${formatValue(item.quotaRefunded)} refunded`,
          formatDate(item.createdAt)
        ])}
        empty="No AI draft requests returned."
      />
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

function MailOpsView({ onError }: { onError: (message: string | null) => void }) {
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
              <button onClick={() => setViewDate(addMonths(viewDate, -1))} type="button">Ã¢â‚¬Â¹</button>
              <button onClick={() => setViewDate(addMonths(viewDate, 1))} type="button">Ã¢â‚¬Âº</button>
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
  const rows = products?.content ?? [];
  const byRegion = countBy(rows, (item) => item.marketRegion ?? "Unknown");
  const byImageStatus = countBy(rows, (item) => item.imageStatus ?? "Unknown");
  const byCatalogType = countBy(rows, (item) => item.catalogType ?? "Unknown");
  const localReadyPercent = percent(summary?.verifiedProducts, summary?.totalProducts);
  const title = {
    overview: "Food catalog operations",
    imports: "Food import jobs",
    regions: "Food market regions",
    quality: "Food quality rules"
  }[mode];

  function reloadAll() {
    void reloadSummary();
    void reloadProducts();
  }

  return (
    <div className="stack">
      <SectionToolbar title={title} state={combineStates([summaryState, productState])} onReload={reloadAll} />
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
      {mode === "quality" && <Panel title="Quality rules">
        <div className="roadmap-strip">
          <span>Quality score rules</span>
          <span>Image moderation queue</span>
          {QUALITY_ISSUES.slice(0, 8).map((item) => <span key={item}>{humanizeFeature(item)}</span>)}
        </div>
      </Panel>}
    </div>
  );
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

function NotificationsView({ onError }: { onError: (message: string | null) => void }) {
  const { data, state, reload } = useEndpoint<PageResponse<Notification>>("/api/v1/notifications?page=0&size=25", onError);
  return (
    <div className="stack">
      <SectionToolbar title="Admin notifications" state={state} onReload={reload} />
      <DataTable
        columns={["Type", "Message", "Read", "Created"]}
        rows={(data?.content ?? []).map((item) => [
          <Badge value={item.type} />,
          item.message ?? "-",
          <Badge value={item.read ? "Read" : "Unread"} tone={item.read ? "neutral" : "warn"} />,
          formatDate(item.createdAt)
        ])}
        empty="No notifications returned."
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
        rows={trackingTrendRows(mode, trends)}
        empty="No tracking trend returned."
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

function MiniNotificationList({ notifications }: { notifications: Notification[] }) {
  if (!notifications.length) {
    return <EmptyState message="No recent notification returned." />;
  }
  return (
    <div className="mini-notification-list">
      {notifications.map((item) => (
        <div key={item.id ?? `${item.type}-${item.createdAt}-${item.message}`}>
          <Badge value={item.type} />
          <p>{item.message ?? "-"}</p>
          <small>{formatDate(item.createdAt)}</small>
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

function Panel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <article className="panel">
      <h3>{title}</h3>
      {children}
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
            <ProductAliasManager productId={item.id} onError={onError} />
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
          <input value={aliasText} onChange={(event) => setAliasText(event.target.value)} placeholder="süt, yarım yağlı süt, skimmed milk" />
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

function UserDetailsModal({ user, onClose }: { user: UserProfile; onClose: () => void }) {
  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <section className="user-modal" role="dialog" aria-modal="true" aria-label="User details" onClick={(event) => event.stopPropagation()}>
        <header className="modal-header">
          <div>
            <p className="eyebrow">Read-only profile</p>
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
              <DetailItem label="Email verified" value={user.emailVerified ? "Yes" : "No"} />
              <DetailItem label="Password set" value={user.passwordSet ? "Yes" : "No"} />
              <DetailItem label="Region" value={user.marketRegion} />
              <DetailItem label="Language" value={user.preferredLanguage} />
            </div>
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
  if (metric.unit === "EUR") {
    return `Ã¢â€šÂ¬${value}`;
  }
  if (metric.unit === "USD" || metric.unit === "GBP") {
    return `${metric.unit} ${value}`;
  }
  return `${value} ${metric.unit}`;
}

function formatRevenueCatChartTick(value: number, currency?: string): string {
  const rounded = value >= 10 ? Math.round(value) : Number(value.toFixed(1));
  if (currency === "EUR") {
    return `Ã¢â€šÂ¬${formatValue(rounded)}`;
  }
  if (currency === "USD" || currency === "GBP") {
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

function uniqueFeatures(items: FeatureMatrixItem[]): string[] {
  return Array.from(new Set(items.map((item) => item.feature).filter(Boolean) as string[])).sort();
}

function featureKey(item: FeatureMatrixItem): string {
  return `${item.planType ?? "-"}:${item.feature ?? "-"}`;
}

function humanizeFeature(value?: string): string {
  if (!value) return "-";
  return value.toLowerCase().split("_").map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(" ");
}

function shortFeature(value?: string): string {
  if (!value) return "-";
  return value.replace(/_/g, " ");
}

function featureDescription(value?: string): string {
  switch (value) {
    case "AI_WORKOUT_PLANNER":
      return "AI-assisted planning and recommendation capabilities.";
    case "AI_RECIPE_GENERATION":
      return "AI-assisted recipe drafting with user review before saving.";
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
