import { FormEvent, useEffect, useMemo, useRef, useState } from "react";

import { formatRequestError, PageResponse, request } from "../api";

import { AdminCustomer360, AdminUserAnalytics, UserProfile } from "../types";

import { AsyncState, DataTable, EmptyState, LoadState, PaginationControls, Panel, SectionToolbar, useDialogAccessibility } from "../AdminPrimitives";

import { SectionKey, UserRouteContext } from "./../admin/navigation";

import { Badge, ConfirmDialog, DatePickerButton, DetailItem, formatDate, formatValue, humanizeFeature, useEndpoint } from "./../admin/shared";
import { useAdminLocale } from "../admin/locale";
import { UserActivityChart, UserVerificationChart } from "../UsersCharts";
import { GrowthTrendChart } from "../GrowthTrendChart";

export type UsersMode = "users" | "admins";

export type UsersRouteState = {
  search: string;
  accountState: string;
  plan: string;
  region: string;
  language: string;
  verification: string;
  activity: string;
  from: string;
  to: string;
  page: number;
  size: number;
  selectedUserId?: number;
};

const USER_PAGE_SIZES = new Set([10, 25, 50, 100]);
const USER_ACCOUNT_STATES = new Set(["ANY", "ENABLED", "DISABLED", "LOCKED"]);
const USER_PLANS = new Set(["", "FREE", "PLUS", "PRO"]);
const USER_REGIONS = new Set(["", "TR", "UK_IE", "GLOBAL"]);
const USER_LANGUAGES = new Set(["", "EN", "TR"]);
const USER_VERIFICATION = new Set(["", "true", "false"]);
const USER_ACTIVITY = new Set(["", "ACTIVE_30_DAYS", "INACTIVE_30_DAYS", "NEVER_ACTIVE"]);
const isoDay = (date: Date) => date.toISOString().slice(0, 10);
const daysAgo = (days: number) => { const date = new Date(); date.setDate(date.getDate() - days); return isoDay(date); };
const daysBefore = (value: string, days: number) => { const date = new Date(`${value}T12:00:00`); date.setDate(date.getDate() - days); return isoDay(date); };

export function readUsersRouteState(search = window.location.search): UsersRouteState {
  const params = new URLSearchParams(search);
  const enumValue = (key: string, allowed: Set<string>, fallback = "") => {
    const value = params.get(key) ?? fallback;
    return allowed.has(value) ? value : fallback;
  };
  const page = Number(params.get("page"));
  const size = Number(params.get("size"));
  const selectedUserId = Number(params.get("userId"));
  return {
    search: (params.get("search") ?? "").trim(),
    accountState: enumValue("account", USER_ACCOUNT_STATES, "ANY"),
    plan: enumValue("plan", USER_PLANS),
    region: enumValue("region", USER_REGIONS),
    language: enumValue("language", USER_LANGUAGES),
    verification: enumValue("verified", USER_VERIFICATION),
    activity: enumValue("activity", USER_ACTIVITY),
    from: params.get("from") ?? "",
    to: params.get("to") ?? "",
    page: Number.isSafeInteger(page) && page >= 0 ? page : 0,
    size: Number.isSafeInteger(size) && USER_PAGE_SIZES.has(size) ? size : 10,
    selectedUserId: Number.isSafeInteger(selectedUserId) && selectedUserId > 0 ? selectedUserId : undefined
  };
}

export function usersRouteSearch(state: UsersRouteState): string {
  const params = new URLSearchParams();
  if (state.search) params.set("search", state.search);
  if (state.accountState !== "ANY") params.set("account", state.accountState);
  if (state.plan) params.set("plan", state.plan);
  if (state.region) params.set("region", state.region);
  if (state.language) params.set("language", state.language);
  if (state.verification) params.set("verified", state.verification);
  if (state.activity) params.set("activity", state.activity);
  if (state.from) params.set("from", state.from);
  if (state.to) params.set("to", state.to);
  if (state.page > 0) params.set("page", String(state.page));
  if (state.size !== 10) params.set("size", String(state.size));
  if (state.selectedUserId) params.set("userId", String(state.selectedUserId));
  const query = params.toString();
  return query ? `?${query}` : "";
}

export function UsersView({
  mode,
  onError,
  onNavigate
}: {
  mode: UsersMode;
  onError: (message: string | null) => void;
  onNavigate: (section: SectionKey, context?: UserRouteContext) => void;
}) {
  const { locale } = useAdminLocale();
  const copy = locale === "tr" ? {
    title: "Kullanıcılar", description: "Kullanıcıları bulun, hesap sağlığını izleyin ve destek işlemlerini tek çalışma alanından yönetin.", search: "Kullanıcı ara", placeholder: "E-posta, ad veya kullanıcı kimliği", account: "Hesap", anyState: "Tüm durumlar", enabled: "Etkin", disabled: "Devre dışı", locked: "Kilitli", plan: "Plan", allPlans: "Tüm planlar", region: "Bölge", allRegions: "Tüm bölgeler", language: "Dil", allLanguages: "Tüm diller", verification: "Doğrulama", any: "Tümü", verified: "Doğrulandı", unverified: "Bekliyor", activity: "Etkinlik", anyActivity: "Tüm etkinlikler", active30: "Son 30 gün aktif", inactive30: "30+ gün pasif", never: "Hiç aktif olmadı", apply: "Ara", clear: "Tümünü temizle", advanced: "Gelişmiş filtreler", advancedHint: "Plan, bölge ve dil ile kapsamı daraltın", activeFilters: "Aktif filtreler", total: "Toplam kullanıcı", totalHint: "Standart kullanıcı hesapları", pending: "Doğrulama bekliyor", pendingHint: "E-posta doğrulaması tamamlanmamış", active: "Aktif kullanıcı", activeHint: "Son 30 günde etkin", results: "Filtre sonucu", resultsHint: "Geçerli arama ve filtrelere uyan", verificationChart: "Doğrulama durumu", verificationHelp: "E-posta doğrulaması artık bu çalışma alanından izlenir.", activityChart: "Kullanıcı etkinliği", activityHelp: "Son aktiviteye göre kullanıcı dağılımı.", list: "Kullanıcı listesi", listHelp: "Bir kullanıcı seçerek Customer 360 görünümünü ve güvenli yönetim işlemlerini açın.", loading: "Kullanıcı hesapları yükleniyor…", empty: "Filtrelere uyan kullanıcı bulunamadı.", user: "Kullanıcı", role: "Rol", status: "Durum", created: "Oluşturuldu", lastActive: "Son etkinlik" }
  : { title: "Users", description: "Find users, monitor account health, and manage support actions from one workspace.", search: "User search", placeholder: "Email, name, or user id", account: "Account", anyState: "Any state", enabled: "Enabled", disabled: "Disabled", locked: "Locked", plan: "Plan", allPlans: "All plans", region: "Region", allRegions: "All regions", language: "Language", allLanguages: "All languages", verification: "Verification", any: "Any", verified: "Verified", unverified: "Unverified", activity: "Activity", anyActivity: "Any activity", active30: "Active in 30 days", inactive30: "Inactive 30+ days", never: "Never active", apply: "Search", clear: "Clear all", advanced: "Advanced filters", advancedHint: "Narrow by plan, region and language", activeFilters: "Active filters", total: "Total users", totalHint: "Standard user accounts", pending: "Pending verification", pendingHint: "Email verification not completed", active: "Active users", activeHint: "Active in the last 30 days", results: "Filter results", resultsHint: "Matching the current search and filters", verificationChart: "Verification status", verificationHelp: "Email verification is monitored in this workspace.", activityChart: "User activity", activityHelp: "User distribution by recent activity.", list: "User directory", listHelp: "Select a user to open Customer 360 and controlled management actions.", loading: "Loading user accounts…", empty: "No users match these filters.", user: "User", role: "Role", status: "Status", created: "Created", lastActive: "Last active" };
  const initialRouteState = useMemo(() => readUsersRouteState(), []);
  const skipInitialFilterReset = useRef(true);
  const [search, setSearch] = useState(initialRouteState.search);
  const [appliedSearch, setAppliedSearch] = useState(initialRouteState.search);
  const [accountState, setAccountState] = useState(initialRouteState.accountState);
  const [plan, setPlan] = useState(initialRouteState.plan);
  const [region, setRegion] = useState(initialRouteState.region);
  const [language, setLanguage] = useState(initialRouteState.language);
  const [verification, setVerification] = useState(initialRouteState.verification);
  const [activity, setActivity] = useState(initialRouteState.activity);
  const [from, setFrom] = useState(initialRouteState.from);
  const [to, setTo] = useState(initialRouteState.to);
  const [trendMetric, setTrendMetric] = useState<"active" | "registrations">("active");
  const [selectedActivityDay, setSelectedActivityDay] = useState("");
  const [filtersOpen, setFiltersOpen] = useState(true);
  const [userPage, setUserPage] = useState(initialRouteState.page);
  const [userPageSize, setUserPageSize] = useState(initialRouteState.size);
  const [selectedUserId, setSelectedUserId] = useState<number | undefined>(initialRouteState.selectedUserId);
  const path = useMemo(() => {
    const params = new URLSearchParams({
      role: mode === "admins" ? "ADMIN" : "STANDARD",
      page: String(userPage),
      size: String(userPageSize)
    });
    if (verification) params.set("emailVerified", verification);
    if (appliedSearch) params.set("search", appliedSearch);
    if (accountState === "ENABLED") params.set("accountEnabled", "true");
    if (accountState === "DISABLED") params.set("accountEnabled", "false");
    if (accountState === "LOCKED") params.set("accountLocked", "true");
    if (plan) params.set("plan", plan);
    if (region) params.set("region", region);
    if (language) params.set("language", language);
    if (activity) params.set("activity", activity);
    if (selectedActivityDay) {
      const start = new Date(`${selectedActivityDay}T00:00:00`); const end = new Date(start); end.setDate(end.getDate() + 1);
      params.set("activeFrom", start.toISOString()); params.set("activeTo", end.toISOString());
    } else {
      if (from) params.set("createdFrom", new Date(`${from}T00:00:00`).toISOString());
      if (to) { const end = new Date(`${to}T00:00:00`); end.setDate(end.getDate() + 1); params.set("createdTo", end.toISOString()); }
    }
    return `/api/v1/admin/users?${params.toString()}`;
  }, [mode, userPage, userPageSize, appliedSearch, accountState, plan, region, language, verification, activity, from, to, selectedActivityDay]);
  const analyticsTo = to || isoDay(new Date());
  const earliestAnalyticsDate = daysBefore(analyticsTo, 365);
  const requestedAnalyticsFrom = from || daysBefore(analyticsTo, 89);
  const analyticsFrom = requestedAnalyticsFrom < earliestAnalyticsDate ? earliestAnalyticsDate : requestedAnalyticsFrom;
  const analyticsPath = `/api/v1/admin/users/analytics?from=${analyticsFrom}&to=${analyticsTo}&timeZone=Europe%2FDublin`;
  const { data: analytics } = useEndpoint<AdminUserAnalytics>(analyticsPath, onError);
  const { data, state, reload } = useEndpoint<PageResponse<UserProfile>>(path, onError);
  const baseStatsPath = "/api/v1/admin/users?role=STANDARD&page=0&size=1";
  const { data: standardUsers } = useEndpoint<PageResponse<UserProfile>>(baseStatsPath, onError);
  const { data: verifiedUsers } = useEndpoint<PageResponse<UserProfile>>(`${baseStatsPath}&emailVerified=true`, onError);
  const { data: unverifiedUsers } = useEndpoint<PageResponse<UserProfile>>(`${baseStatsPath}&emailVerified=false`, onError);
  const { data: activeUsers } = useEndpoint<PageResponse<UserProfile>>(`${baseStatsPath}&activity=ACTIVE_30_DAYS`, onError);
  const { data: inactiveUsers } = useEndpoint<PageResponse<UserProfile>>(`${baseStatsPath}&activity=INACTIVE_30_DAYS`, onError);
  const { data: neverActiveUsers } = useEndpoint<PageResponse<UserProfile>>(`${baseStatsPath}&activity=NEVER_ACTIVE`, onError);
  const [selectedUser, setSelectedUser] = useState<UserProfile | null>(null);
  const [statusActionState, setStatusActionState] = useState<LoadState>("idle");
  const users = data?.content ?? [];
  const title = mode === "admins" ? (locale === "tr" ? "Yönetici hesapları" : "Admin accounts") : copy.title;

  useEffect(() => {
    const timer = window.setTimeout(() => setAppliedSearch(search.trim()), 350);
    return () => window.clearTimeout(timer);
  }, [search]);

  useEffect(() => { setSelectedActivityDay(""); }, [from, to, trendMetric]);

  useEffect(() => {
    if (skipInitialFilterReset.current) {
      skipInitialFilterReset.current = false;
      return;
    }
    setUserPage(0);
  }, [mode, userPageSize, accountState, plan, region, language, verification, activity, appliedSearch, from, to]);

  useEffect(() => {
    const query = usersRouteSearch({ search: appliedSearch, accountState, plan, region, language, verification, activity, from, to, page: userPage, size: userPageSize, selectedUserId });
    const next = `${window.location.pathname}${query}`;
    if (`${window.location.pathname}${window.location.search}` !== next) window.history.replaceState(null, "", next);
  }, [accountState, activity, appliedSearch, from, language, plan, region, selectedUserId, to, userPage, userPageSize, verification]);

  useEffect(() => {
    if (!selectedUserId || selectedUser?.id === selectedUserId) return;
    const restoredUser = users.find((user) => user.id === selectedUserId);
    if (restoredUser) setSelectedUser(restoredUser);
  }, [selectedUser, selectedUserId, users]);

  function selectUser(user: UserProfile | null) {
    setSelectedUser(user);
    setSelectedUserId(user?.id);
  }

  async function updateSelectedUserStatus(payload: { accountEnabled: boolean; accountLocked: boolean; reason: string }) {
    if (!selectedUser?.id) return;
    setStatusActionState("loading");
    try {
      const updated = await request<UserProfile>(`/api/v1/admin/users/${selectedUser.id}/status`, {
        method: "PATCH",
        body: { ...payload, confirmed: true }
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
      <SectionToolbar
        title={title}
        description={copy.description}
        state={state}
        onReload={reload}
      />
      <div className="user-filter-panel">
        <button className="user-filter-toggle" type="button" aria-expanded={filtersOpen} onClick={() => setFiltersOpen(value => !value)}><span><strong>{locale === "tr" ? "Kullanıcı filtreleri" : "User filters"}</strong><small>{locale === "tr" ? "Tarih, hesap ve kullanıcı özelliklerine göre listeyi daraltın." : "Narrow the directory by date, account and user attributes."}</small></span><b aria-hidden="true">{filtersOpen ? "−" : "+"}</b></button>
        {filtersOpen && <div className="user-filter-content">
        <section className="user-date-filter"><div><strong>{locale === "tr" ? "Kayıt tarihi" : "Registration date"}</strong><small>{locale === "tr" ? "Varsayılan görünüm tüm kayıtları listeler. Tarih seçerseniz yalnızca o dönemde kaydolan kullanıcılar gösterilir." : "The default view lists every account. Choose dates only to limit users by registration period."}</small></div><div className="user-range-presets"><button className={!from && !to ? "active" : ""} type="button" onClick={() => { setFrom(""); setTo(""); }}>{locale === "tr" ? "Tüm zamanlar" : "All time"}</button>{([7,30,90] as const).map(days => <button className={from === daysAgo(days - 1) && to === isoDay(new Date()) ? "active" : ""} key={days} type="button" onClick={() => { setFrom(daysAgo(days - 1)); setTo(isoDay(new Date())); }}>{days} {locale === "tr" ? "gün" : "days"}</button>)}</div><div className="user-date-controls"><DatePickerButton label={locale === "tr" ? "Başlangıç" : "From"} value={from} max={to || undefined} onChange={setFrom}/><span>→</span><DatePickerButton label={locale === "tr" ? "Bitiş" : "To"} value={to} min={from || undefined} onChange={setTo}/></div></section>
        <section className="user-filter-main">
          <label className="user-filter-search">{copy.search}<span><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder={copy.placeholder} />{search && <button type="button" onClick={() => setSearch("")} aria-label={copy.clear}>×</button>}</span></label>
          <label>{copy.account}<select value={accountState} onChange={(event) => setAccountState(event.target.value)}><option value="ANY">{copy.anyState}</option><option value="ENABLED">{copy.enabled}</option><option value="DISABLED">{copy.disabled}</option><option value="LOCKED">{copy.locked}</option></select></label>
          <label>{copy.verification}<select value={verification} onChange={(event) => setVerification(event.target.value)}><option value="">{copy.any}</option><option value="true">{copy.verified}</option><option value="false">{copy.unverified}</option></select></label>
          <label>{copy.activity}<select value={activity} onChange={(event) => setActivity(event.target.value)}><option value="">{copy.anyActivity}</option><option value="ACTIVE_30_DAYS">{copy.active30}</option><option value="INACTIVE_30_DAYS">{copy.inactive30}</option><option value="NEVER_ACTIVE">{copy.never}</option></select></label>
        </section>
        <details className="user-advanced-filters"><summary><span><strong>{copy.advanced}</strong><small>{copy.advancedHint}</small></span></summary><div>
          <label>{copy.plan}<select value={plan} onChange={(event) => setPlan(event.target.value)}><option value="">{copy.allPlans}</option><option value="FREE">Free</option><option value="PLUS">Plus</option><option value="PRO">Pro</option></select></label>
          <label>{copy.region}<select value={region} onChange={(event) => setRegion(event.target.value)}><option value="">{copy.allRegions}</option><option value="TR">TR</option><option value="UK_IE">UK / IE</option><option value="GLOBAL">Global</option></select></label>
          <label>{copy.language}<select value={language} onChange={(event) => setLanguage(event.target.value)}><option value="">{copy.allLanguages}</option><option value="EN">English</option><option value="TR">Türkçe</option></select></label>
        </div></details>
        <div className="user-filter-actions">
          <button className="ghost-button" type="button" onClick={() => {
            setSearch(""); setAppliedSearch(""); setAccountState("ANY"); setPlan("");
            setRegion(""); setLanguage(""); setVerification(""); setActivity(""); setFrom(""); setTo(""); setSelectedActivityDay(""); setUserPage(0);
          }}>{copy.clear}</button>
        </div>
        </div>}
      </div>
      {mode !== "admins" && <>
        <div className="users-analytics-hero"><Panel title={locale === "tr" ? "Kullanıcı hareketleri" : "User movement"} description={locale === "tr" ? "Günlük aktif görünümünde bir güne tıklayarak kullanıcıları listeleyin." : "Select a day in the daily-active view to list those users."}><div className="users-trend-switch" role="group" aria-label={locale === "tr" ? "Grafik verisi" : "Chart data"}>{selectedActivityDay && <button type="button" className="selected-day" onClick={() => setSelectedActivityDay("")}>{selectedActivityDay} ×</button>}<button type="button" className={trendMetric === "active" ? "active" : ""} onClick={() => setTrendMetric("active")}>{locale === "tr" ? "Günlük aktif" : "Daily active"}</button><button type="button" className={trendMetric === "registrations" ? "active" : ""} onClick={() => setTrendMetric("registrations")}>{locale === "tr" ? "Yeni kayıtlar" : "New registrations"}</button></div><GrowthTrendChart points={analytics?.daily ?? []} locale={locale} metric={trendMetric} onPointSelect={trendMetric === "active" ? point => { setSelectedActivityDay(point.date); setUserPage(0); } : undefined}/></Panel></div>
        <div className="users-insight-grid users-insight-grid-four">
          <Panel title={copy.verificationChart} description={copy.verificationHelp}><UserVerificationChart verified={verifiedUsers?.totalElements ?? 0} unverified={unverifiedUsers?.totalElements ?? 0} locale={locale}/></Panel>
          <Panel title={copy.activityChart} description={copy.activityHelp}><UserActivityChart active={activeUsers?.totalElements ?? 0} inactive={inactiveUsers?.totalElements ?? 0} never={neverActiveUsers?.totalElements ?? 0} locale={locale}/></Panel>
          <Panel title={locale === "tr" ? "Plan dağılımı" : "Plan distribution"} description={locale === "tr" ? "Mevcut kullanıcı tabanının plan karışımı." : "Current plan mix across the user base."}><div className="users-plan-mix">{["FREE", "PLUS", "PRO"].map((name) => { const value = analytics?.planDistribution?.[name] ?? 0; return <div key={name}><span>{name}</span><i><b style={{width:`${analytics?.totalUsers ? value/analytics.totalUsers*100 : 0}%`}}/></i><strong>{value}</strong></div>; })}</div></Panel>
          <Panel className="users-summary-panel" title={locale === "tr" ? "Hesap özeti" : "Account summary"} description={locale === "tr" ? "Temel hesap göstergeleri ve mevcut liste sonucu." : "Essential account indicators and the current directory result."}><div className="users-summary-card"><div><span>{copy.total}</span><strong>{formatValue(standardUsers?.totalElements ?? 0)}</strong><small>{copy.totalHint}</small></div><div><span>{copy.pending}</span><strong>{formatValue(unverifiedUsers?.totalElements ?? 0)}</strong><small>{copy.pendingHint}</small></div><div><span>{copy.results}</span><strong>{formatValue(data?.totalElements ?? 0)}</strong><small>{copy.resultsHint}</small></div></div></Panel>
        </div>
      </>}
      <Panel className="users-directory-panel" title={copy.list} description={copy.listHelp}>
      <AsyncState
        state={state}
        hasData={users.length > 0}
        loadingMessage={copy.loading}
        emptyMessage={copy.empty}
      />
      {(data || state === "ready") && <DataTable
        caption={`${title} table`}
        columns={[copy.user, copy.role, copy.status, copy.region, copy.language, copy.created, copy.lastActive]}
        rows={users.map((user) => [
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
          formatDate(user.createdAt),
          formatDate(user.lastActiveAt)
        ])}
        rowData={users}
        rowKeys={users.map((user) => user.id ?? user.email ?? "unknown-user")}
        onRowClick={selectUser}
        empty={mode === "admins" ? (locale === "tr" ? "Yönetici hesabı bulunamadı." : "No admin users found.") : copy.empty}
      />}
      {(data || state === "ready") && <PaginationControls
        page={data?.page ?? userPage}
        pageSize={userPageSize}
        totalElements={data?.totalElements ?? 0}
        totalPages={Math.max(1, data?.totalPages ?? 1)}
        first={data?.first ?? userPage <= 0}
        last={data?.last ?? true}
        onPageChange={setUserPage}
        onPageSizeChange={(size) => {
          setUserPageSize(size);
          setUserPage(0);
        }}
      />}
      </Panel>
      {selectedUser && <UserDetailsModal
        user={selectedUser}
        statusState={statusActionState}
        onClose={() => selectUser(null)}
        onNavigate={onNavigate}
        onStatusUpdate={updateSelectedUserStatus}
        onReloadUsers={reload}
        onError={onError}
        locale={locale}
      />}
    </div>
  );
}

export function UserCell({ user }: { user: UserProfile }) {
  return (
    <div className="entity-cell">
      <strong>{user.name ?? user.email ?? "Unnamed user"}</strong>
      <small>{user.email ?? `ID ${user.id ?? "-"}`}</small>
    </div>
  );
}

export function UserDetailsModal({
  user,
  statusState,
  onClose,
  onStatusUpdate,
  onReloadUsers,
  onNavigate,
  onError,
  locale
}: {
  user: UserProfile;
  statusState: LoadState;
  onClose: () => void;
  onStatusUpdate: (payload: { accountEnabled: boolean; accountLocked: boolean; reason: string }) => Promise<void>;
  onReloadUsers: () => Promise<void>;
  onNavigate: (section: SectionKey, context?: UserRouteContext) => void;
  onError: (message: string | null) => void;
  locale: "tr" | "en";
}) {
  const m = locale === "tr" ? {
    customer:"Kullanıcı 360", unnamed:"Adsız kullanıcı", close:"Kapat", account:"Hesap", subscription:"Abonelik", ai:"AI ve iadeler", notifications:"Bildirimler", security:"Güvenlik", consent:"Onaylar", activity:"Etkinlik", notes:"Destek notları", loading:"Kullanıcı özeti yükleniyor…", unavailable:"Kullanıcı özeti kullanılamıyor.", accountSummary:"Hesap özeti", email:"E-posta", role:"Rol", status:"Durum", emailVerified:"E-posta doğrulaması", passwordSet:"Parola", region:"Bölge", language:"Dil", created:"Oluşturuldu", lastActive:"Son etkinlik", yes:"Evet", no:"Hayır", controlled:"Kontrollü hesap işlemi", allowSignIn:"Girişe izin ver", allowHelp:"Devre dışı kullanıcılar giriş yapamaz.", lock:"Güvenlik kilidi", lockHelp:"Bir yönetici kilidi kaldırana kadar hesap kapalı kalır.", auditReason:"Zorunlu denetim gerekçesi", reasonPlaceholder:"Destek veya güvenlik gerekçesini açıklayın.", reviewStatus:"Durum değişikliğini incele", entitlement:"Abonelik ve haklar", planLabel:"Plan", billing:"Faturalama", autoRenew:"Otomatik yenileme", start:"Başlangıç", end:"Bitiş", monthlyAi:"Aylık AI kotası", used:"Kullanılan", addonRemaining:"Ek paket bakiyesi", addonExpiry:"Ek paket bitişi", features:"Etkin özellikler", openEntitlement:"Hak kontrollerini aç", aiSummary:"AI destek özeti", totalRequests:"Toplam istek", lastRequest:"Son istek", sample:"Yakın dönem örneği", outcomes:"Yakın dönem sonuçları", openAi:"AI isteklerini aç", requestTypes:"Yakın dönem istek türleri", unread:"okunmamış", activeSessions:"Etkin oturumlar", activeSessionHint:"etkin yenileme belirteci oturumu", revokeWhy:"Tüm oturumlar neden kapatılmalı?", reviewRevoke:"Oturum kapatmayı incele", securityEvents:"Yakın dönem güvenlik olayları", consentHistory:"Onay geçmişi", activitySummary:"Yakın dönem etkinlik özeti", foodLogs:"Besin kayıtları", lastFood:"Son besin kaydı", productEvents:"Ürün olayları", productActivity:"Yakın dönem ürün etkinliği", addNote:"İç destek notu ekle", note:"Not", tags:"Etiketler", saveNote:"Notu kaydet", supportHistory:"Destek geçmişi", noNotes:"İç destek notu bulunmuyor.", applyTitle:"Hesap durumu değiştirilsin mi?", revokeTitle:"Tüm etkin oturumlar kapatılsın mı?", statusMessage:"Bu işlem {email} hesabının giriş erişimini değiştirir. Gerekçe denetim kaydında saklanır.", revokeMessage:"Bu işlem {email} hesabını tüm etkin oturumlardan çıkarır. Gerekçe denetim kaydında saklanır.", apply:"Durumu uygula", revoke:"Oturumları kapat" }
  : { customer:"Customer 360", unnamed:"Unnamed user", close:"Close", account:"Account", subscription:"Subscription", ai:"AI & refunds", notifications:"Notifications", security:"Security", consent:"Consent", activity:"Activity", notes:"Support notes", loading:"Loading customer summary…", unavailable:"Customer summary is unavailable.", accountSummary:"Account summary", email:"Email", role:"Role", status:"Status", emailVerified:"Email verified", passwordSet:"Password set", region:"Region", language:"Language", created:"Created", lastActive:"Last active", yes:"Yes", no:"No", controlled:"Controlled account action", allowSignIn:"Allow sign in", allowHelp:"Disabled users cannot authenticate.", lock:"Security lock", lockHelp:"Locked until an admin unlocks the account.", auditReason:"Required audit reason", reasonPlaceholder:"Describe the support or security reason.", reviewStatus:"Review status change", entitlement:"Subscription and entitlement", planLabel:"Plan", billing:"Billing", autoRenew:"Auto renew", start:"Start", end:"End", monthlyAi:"Monthly AI", used:"Used", addonRemaining:"Add-on remaining", addonExpiry:"Add-on expiry", features:"Resolved features", openEntitlement:"Open entitlement controls", aiSummary:"AI support summary", totalRequests:"Total requests", lastRequest:"Last request", sample:"Recent sample", outcomes:"Recent outcome counts", openAi:"Open AI requests", requestTypes:"Recent request types", unread:"unread", activeSessions:"Active sessions", activeSessionHint:"active refresh-token sessions", revokeWhy:"Why must all sessions be revoked?", reviewRevoke:"Review session revoke", securityEvents:"Recent security events", consentHistory:"Consent history", activitySummary:"Recent activity summary", foodLogs:"Food logs", lastFood:"Last food log", productEvents:"Product events", productActivity:"Recent product activity", addNote:"Add internal support note", note:"Note", tags:"Tags", saveNote:"Save note", supportHistory:"Support history", noNotes:"No internal support notes.", applyTitle:"Apply account status change?", revokeTitle:"Revoke all active sessions?", statusMessage:"This changes sign-in access for {email}. The reason is stored in the audit trail.", revokeMessage:"This signs {email} out of every active session. The reason is stored in the audit trail.", apply:"Apply status", revoke:"Revoke sessions" };
  const notificationCopy = locale === "tr" ? {
    compose: "Kişisel bildirim gönder", composeHelp: "Bu içerik yalnızca seçili kullanıcının bildirim alanına eklenir.",
    title: "Başlık", titlePlaceholder: "Bildirim başlığını yazın", message: "Mesaj", messagePlaceholder: "Kullanıcıya iletilecek kısa ve açık mesaj",
    category: "Kategori", announcement: "Duyuru", accountCategory: "Hesap", support: "Destek", reminder: "Hatırlatma", severity: "Önem", delivery: "Gönderim", inApp: "Yalnızca uygulama içi", push: "Uygulama içi + push",
    destination: "Açılacak alan", noDestination: "Yönlendirme yok", dashboard: "Ana ekran", diary: "Günlük", recipes: "Tarifler", subscription: "Abonelik", profile: "Profil",
    review: "Gönderimi incele", sent: "Bildirim kullanıcıya gönderildi.", history: "Gönderilmiş bildirimler", historyHelp: "Yakın dönem bildirim geçmişini göster veya gizle.",
    confirmTitle: "Kişisel bildirim gönderilsin mi?", confirmMessage: "Bildirim yalnızca {email} kullanıcısına gönderilecek. İşlem denetim kaydına yazılır.", confirm: "Bildirimi gönder", empty: "Gönderilmiş bildirim bulunmuyor."
  } : {
    compose: "Send a personal notification", composeHelp: "This content is added only to the selected user's notification feed.",
    title: "Title", titlePlaceholder: "Enter a notification title", message: "Message", messagePlaceholder: "A short, clear message for the user",
    category: "Category", announcement: "Announcement", accountCategory: "Account", support: "Support", reminder: "Reminder", severity: "Severity", delivery: "Delivery", inApp: "In-app only", push: "In-app + push",
    destination: "Open destination", noDestination: "No destination", dashboard: "Dashboard", diary: "Diary", recipes: "Recipes", subscription: "Subscription", profile: "Profile",
    review: "Review delivery", sent: "Notification sent to the user.", history: "Sent notifications", historyHelp: "Show or hide the recent notification history.",
    confirmTitle: "Send personal notification?", confirmMessage: "This notification will be sent only to {email}. The action is recorded in the audit trail.", confirm: "Send notification", empty: "No notifications have been sent."
  };
  const dialogRef = useDialogAccessibility(onClose);
  const customerPath = `/api/v1/admin/users/${user.id}/customer-360`;
  const { data: customer, state, reload } = useEndpoint<AdminCustomer360>(customerPath, onError);
  const [tab, setTab] = useState<"account" | "subscription" | "ai" | "notifications" | "security" | "consent" | "activity" | "notes">("account");
  const [accountEnabled, setAccountEnabled] = useState(user.accountEnabled !== false);
  const [accountLocked, setAccountLocked] = useState(Boolean(user.accountLocked));
  const [reason, setReason] = useState("");
  const [sessionReason, setSessionReason] = useState("");
  const [note, setNote] = useState("");
  const [tags, setTags] = useState("");
  const [actionState, setActionState] = useState<LoadState>("idle");
  const [confirmation, setConfirmation] = useState<"status" | "sessions" | null>(null);
  const [notificationTitle, setNotificationTitle] = useState("");
  const [notificationMessage, setNotificationMessage] = useState("");
  const [notificationCategory, setNotificationCategory] = useState("ANNOUNCEMENT");
  const [notificationSeverity, setNotificationSeverity] = useState("INFO");
  const [notificationDelivery, setNotificationDelivery] = useState("IN_APP");
  const [notificationTarget, setNotificationTarget] = useState("");
  const [notificationReview, setNotificationReview] = useState(false);
  const [notificationNotice, setNotificationNotice] = useState("");
  const profile = customer?.profile ?? user;

  useEffect(() => {
    setAccountEnabled(user.accountEnabled !== false);
    setAccountLocked(Boolean(user.accountLocked));
    setReason("");
  }, [user.id, user.accountEnabled, user.accountLocked]);

  async function confirmRiskAction() {
    if (!user.id || !confirmation) return;
    if (confirmation === "status") {
      await onStatusUpdate({ accountEnabled, accountLocked, reason: reason.trim() });
      setReason("");
    } else {
      setActionState("loading");
      try {
        await request(`/api/v1/admin/users/${user.id}/sessions/revoke`, {
          method: "POST",
          body: { reason: sessionReason.trim(), confirmed: true }
        });
        setSessionReason("");
        setActionState("ready");
      } catch (error) {
        setActionState("error");
        onError(formatRequestError(error));
      }
    }
    setConfirmation(null);
    await reload();
    await onReloadUsers();
  }

  async function addSupportNote(event: FormEvent) {
    event.preventDefault();
    if (!user.id || !note.trim()) return;
    setActionState("loading");
    try {
      await request(`/api/v1/admin/users/${user.id}/support-notes`, {
        method: "POST",
        body: {
          note: note.trim(),
          tags: tags.split(",").map((tag) => tag.trim()).filter(Boolean)
        }
      });
      setNote(""); setTags(""); setActionState("ready");
      await reload();
    } catch (error) {
      setActionState("error");
      onError(formatRequestError(error));
    }
  }

  async function sendPersonalNotification() {
    if (!user.id || !notificationTitle.trim() || !notificationMessage.trim()) return;
    setActionState("loading");
    setNotificationNotice("");
    try {
      await request(`/api/v1/admin/users/${user.id}/notifications`, {
        method: "POST",
        body: {
          title: notificationTitle.trim(), message: notificationMessage.trim(), category: notificationCategory, severity: notificationSeverity,
          delivery: notificationDelivery, targetRoute: notificationTarget || null,
          primaryAction: notificationTarget ? "OPEN_TARGET" : null
        }
      });
      setNotificationTitle(""); setNotificationMessage(""); setNotificationCategory("ANNOUNCEMENT"); setNotificationSeverity("INFO");
      setNotificationDelivery("IN_APP"); setNotificationTarget(""); setNotificationReview(false);
      setNotificationNotice(notificationCopy.sent); setActionState("ready");
      await reload();
    } catch (error) {
      setActionState("error"); setNotificationReview(false); onError(formatRequestError(error));
    }
  }

  const tabs = [
    ["account", m.account], ["subscription", m.subscription], ["ai", m.ai],
    ["notifications", m.notifications], ["security", m.security], ["consent", m.consent],
    ["activity", m.activity], ["notes", m.notes]
  ] as const;

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <section ref={dialogRef} tabIndex={-1} className="user-modal customer-360-modal" role="dialog" aria-modal="true" aria-label={m.customer} onClick={(event) => event.stopPropagation()}>
        <header className="modal-header">
          <div>
            <p className="eyebrow">{m.customer}</p>
            <h2>{profile.name ?? m.unnamed}</h2>
            <span>{profile.email ?? `ID ${profile.id ?? "-"}`}</span>
          </div>
          <button className="icon-button" onClick={onClose} type="button" aria-label={m.close}>×</button>
        </header>
        <nav className="customer-360-tabs" aria-label={locale === "tr" ? "Kullanıcı detay bölümleri" : "Customer detail sections"}>
          {tabs.map(([key, label]) => <button className={tab === key ? "active" : ""} key={key} onClick={() => setTab(key)} type="button">{label}</button>)}
        </nav>
        <div className="customer-360-content">
          <AsyncState state={state} hasData={Boolean(customer)} loadingMessage={m.loading} emptyMessage={m.unavailable} />
          {customer && tab === "account" && <div className="customer-360-two-column">
            <Panel title={m.accountSummary}>
              <div className="readonly-grid">
                <DetailItem label="ID" value={formatValue(profile.id)} />
                <DetailItem label={m.email} value={profile.email} />
                <DetailItem label={m.role} value={profile.role} />
                <DetailItem label={m.status} value={profile.accountLocked ? m.lock : profile.accountEnabled === false ? (locale === "tr" ? "Devre dışı" : "Disabled") : (locale === "tr" ? "Etkin" : "Enabled")} />
                <DetailItem label={m.emailVerified} value={profile.emailVerified ? m.yes : m.no} />
                <DetailItem label={m.passwordSet} value={profile.passwordSet ? m.yes : m.no} />
                <DetailItem label={m.region} value={profile.marketRegion} />
                <DetailItem label={m.language} value={profile.preferredLanguage} />
                <DetailItem label={m.created} value={formatDate(profile.createdAt)} />
                <DetailItem label={m.lastActive} value={formatDate(profile.lastActiveAt)} />
              </div>
            </Panel>
            <Panel title={m.controlled}>
              <form className="account-status-panel" onSubmit={(event) => {
                event.preventDefault();
                if (reason.trim()) setConfirmation("status");
              }}>
                <div className="account-status-toggles">
                  <label className="status-toggle-card"><input checked={accountEnabled} onChange={(event) => setAccountEnabled(event.target.checked)} type="checkbox" /><span><strong>{m.allowSignIn}</strong><small>{m.allowHelp}</small></span></label>
                  <label className="status-toggle-card danger"><input checked={accountLocked} onChange={(event) => setAccountLocked(event.target.checked)} type="checkbox" /><span><strong>{m.lock}</strong><small>{m.lockHelp}</small></span></label>
                </div>
                <label className="account-status-reason">{m.auditReason}<textarea value={reason} onChange={(event) => setReason(event.target.value)} placeholder={m.reasonPlaceholder} maxLength={500} required /></label>
                <div className="account-status-footer"><span>{reason.trim().length}/500</span><button className="primary-button" disabled={statusState === "loading" || !reason.trim()} type="submit">{m.reviewStatus}</button></div>
              </form>
            </Panel>
          </div>}
          {customer && tab === "subscription" && <div className="customer-360-two-column">
            <Panel title={m.entitlement}>
              <div className="readonly-grid">
                <DetailItem label={m.planLabel} value={customer.subscription.plan} /><DetailItem label={m.status} value={customer.subscription.status} />
                <DetailItem label={m.billing} value={customer.subscription.billingPeriod} /><DetailItem label={m.autoRenew} value={customer.subscription.autoRenew ? m.yes : m.no} />
                <DetailItem label={m.start} value={customer.subscription.startDate} /><DetailItem label={m.end} value={customer.subscription.endDate} />
                <DetailItem label={m.monthlyAi} value={customer.subscription.aiMonthlyQuota} /><DetailItem label={m.used} value={customer.subscription.aiUsedThisPeriod} />
                <DetailItem label={m.addonRemaining} value={customer.subscription.aiAddonRemaining} /><DetailItem label={m.addonExpiry} value={customer.subscription.aiAddonExpiresAt} />
              </div>
            </Panel>
            <Panel title={m.features}><div className="customer-tag-list">{(customer.subscription.activeFeatures ?? []).map((feature) => <Badge key={feature} value={humanizeFeature(feature)} tone="good" />)}</div><button className="ghost-button" onClick={() => { onClose(); if (profile.id) onNavigate("subscriptionAccess", { userId: profile.id, userEmail: profile.email }); }} type="button">{m.openEntitlement}</button></Panel>
          </div>}
          {customer && tab === "ai" && <div className="customer-360-two-column">
            <Panel title={m.aiSummary}><div className="readonly-grid"><DetailItem label={m.totalRequests} value={customer.ai.totalRequests} /><DetailItem label={m.lastRequest} value={formatDate(customer.ai.lastRequestAt)} /><DetailItem label={m.sample} value={customer.ai.recentSampleSize} /></div></Panel>
            <Panel title={m.outcomes}><div className="customer-count-list">{Object.entries(customer.ai.recentStatusCounts ?? {}).map(([key, value]) => <div key={key}><span>{humanizeFeature(key)}</span><strong>{value}</strong></div>)}</div><button className="ghost-button" onClick={() => { onClose(); if (profile.id) onNavigate("aiRequests", { userId: profile.id, userEmail: profile.email }); }} type="button">{m.openAi}</button></Panel>
            <Panel title={m.requestTypes}><div className="customer-count-list">{Object.entries(customer.ai.recentRequestTypeCounts ?? {}).map(([key, value]) => <div key={key}><span>{humanizeFeature(key)}</span><strong>{value}</strong></div>)}</div></Panel>
          </div>}
          {customer && tab === "notifications" && <div className="customer-notification-workspace">
            <details className="customer-notification-section">
              <summary><span><strong>{notificationCopy.compose}</strong><small>{notificationCopy.composeHelp}</small></span></summary>
              <form className="customer-notification-form customer-notification-section-content" onSubmit={(event) => { event.preventDefault(); setNotificationReview(true); }}>
                {notificationNotice && <div className="form-notice success">{notificationNotice}</div>}
                <label>{notificationCopy.title}<input value={notificationTitle} onChange={(event) => setNotificationTitle(event.target.value)} placeholder={notificationCopy.titlePlaceholder} maxLength={120} required /></label>
                <label>{notificationCopy.message}<textarea value={notificationMessage} onChange={(event) => setNotificationMessage(event.target.value)} placeholder={notificationCopy.messagePlaceholder} maxLength={500} required /></label>
                <div className="customer-notification-options">
                  <label>{notificationCopy.category}<select value={notificationCategory} onChange={(event) => setNotificationCategory(event.target.value)}><option value="ANNOUNCEMENT">{notificationCopy.announcement}</option><option value="ACCOUNT">{notificationCopy.accountCategory}</option><option value="SUPPORT">{notificationCopy.support}</option><option value="REMINDER">{notificationCopy.reminder}</option></select></label>
                  <label>{notificationCopy.severity}<select value={notificationSeverity} onChange={(event) => setNotificationSeverity(event.target.value)}><option value="INFO">Info</option><option value="WARNING">Warning</option><option value="CRITICAL">Critical</option></select></label>
                  <label>{notificationCopy.delivery}<select value={notificationDelivery} onChange={(event) => setNotificationDelivery(event.target.value)}><option value="IN_APP">{notificationCopy.inApp}</option><option value="IN_APP_AND_PUSH">{notificationCopy.push}</option></select></label>
                  <label>{notificationCopy.destination}<select value={notificationTarget} onChange={(event) => setNotificationTarget(event.target.value)}><option value="">{notificationCopy.noDestination}</option><option value="dashboard">{notificationCopy.dashboard}</option><option value="diary">{notificationCopy.diary}</option><option value="recipes">{notificationCopy.recipes}</option><option value="subscription">{notificationCopy.subscription}</option><option value="profile">{notificationCopy.profile}</option></select></label>
                </div>
                <div className="customer-notification-footer"><span>{notificationMessage.length}/500</span><button className="primary-button" disabled={actionState === "loading" || !notificationTitle.trim() || !notificationMessage.trim()} type="submit">{notificationCopy.review}</button></div>
              </form>
            </details>
            <details className="customer-notification-history">
              <summary><span><strong>{notificationCopy.history}</strong><small>{notificationCopy.historyHelp}</small></span><Badge value={String(customer.notifications.total ?? 0)} /></summary>
              <div className="customer-notification-history-content"><DataTable caption={notificationCopy.history} columns={[notificationCopy.title, notificationCopy.category, notificationCopy.message, notificationCopy.severity, m.status, m.created]} rows={(customer.notifications.recent ?? []).map((item) => [item.title || humanizeFeature(item.type), humanizeFeature(item.category), item.message || "—", <Badge value={item.severity} />, item.read ? (locale==="tr"?"Okundu":"Read") : (locale==="tr"?"Okunmadı":"Unread"), formatDate(item.createdAt)])} empty={notificationCopy.empty} /></div>
            </details>
          </div>}
          {customer && tab === "security" && <div className="customer-360-two-column">
            <Panel title={m.activeSessions}><div className="customer-risk-summary"><strong>{customer.security.activeSessions ?? 0}</strong><span>{m.activeSessionHint}</span></div><label>{m.auditReason}<textarea value={sessionReason} onChange={(event) => setSessionReason(event.target.value)} placeholder={m.revokeWhy} maxLength={500} /></label><button className="primary-button danger-button" disabled={!sessionReason.trim() || actionState === "loading" || (customer.security.activeSessions ?? 0) === 0} onClick={() => setConfirmation("sessions")} type="button">{m.reviewRevoke}</button></Panel>
            <Panel title={m.securityEvents}><DataTable caption={m.securityEvents} columns={[locale==="tr"?"Olay":"Event",locale==="tr"?"Sağlayıcı":"Provider",locale==="tr"?"Sonuç":"Result",m.created]} rows={(customer.security.recentEvents ?? []).map((item) => [humanizeFeature(item.eventType), item.provider, item.resultCode, formatDate(item.createdAt)])} empty={locale==="tr"?"Güvenlik olayı bulunmuyor.":"No security events."} /></Panel>
          </div>}
          {customer && tab === "consent" && <Panel title={`${m.consentHistory} (${customer.consent.total ?? 0})`}><DataTable caption={m.consentHistory} columns={[locale==="tr"?"Tür":"Type",locale==="tr"?"Sürüm":"Version",m.status,locale==="tr"?"Kaynak":"Source",m.created]} rows={(customer.consent.recent ?? []).map((item) => [humanizeFeature(item.consentType), item.version, <Badge value={item.status} />, item.source, formatDate(item.createdAt)])} empty={locale==="tr"?"Onay kaydı bulunmuyor.":"No consent records."} /></Panel>}
          {customer && tab === "activity" && <div className="customer-360-two-column"><Panel title={m.activitySummary}><div className="readonly-grid"><DetailItem label={m.foodLogs} value={customer.activity.foodLogCount} /><DetailItem label={m.lastFood} value={formatDate(customer.activity.lastFoodLogAt)} /><DetailItem label={m.productEvents} value={customer.activity.productEventCount} /></div></Panel><Panel title={m.productActivity}><DataTable caption={m.productActivity} columns={[locale==="tr"?"Olay":"Event",locale==="tr"?"Yüzey":"Surface",m.created]} rows={(customer.activity.recentProductEvents ?? []).map((item) => [humanizeFeature(item.eventType), item.surface, formatDate(item.createdAt)])} empty={locale==="tr"?"Ürün etkinliği bulunmuyor.":"No product activity."} /></Panel></div>}
          {customer && tab === "notes" && <div className="customer-360-two-column">
            <Panel title={m.addNote}><form className="customer-note-form" onSubmit={addSupportNote}><label>{m.note}<textarea value={note} onChange={(event) => setNote(event.target.value)} maxLength={1000} required /></label><label>{m.tags}<input value={tags} onChange={(event) => setTags(event.target.value)} placeholder="BETA, BILLING, FOLLOW_UP" /></label><button className="primary-button" disabled={!note.trim() || actionState === "loading"} type="submit">{m.saveNote}</button></form></Panel>
            <Panel title={m.supportHistory}><div className="customer-note-list">{(customer.supportNotes ?? []).map((item) => <article key={item.id}><div><strong>{item.createdBy ?? "Admin"}</strong><span>{formatDate(item.createdAt)}</span></div><p>{item.note}</p><div className="customer-tag-list">{(item.tags ?? []).map((tag) => <Badge key={tag} value={tag} />)}</div></article>)}{!customer.supportNotes?.length && <EmptyState message={m.noNotes} />}</div></Panel>
          </div>}
        </div>
        <footer className="modal-actions"><button className="ghost-button" onClick={onClose} type="button">{m.close}</button></footer>
        {confirmation && <ConfirmDialog
          title={confirmation === "status" ? m.applyTitle : m.revokeTitle}
          message={(confirmation === "status" ? m.statusMessage : m.revokeMessage).replace("{email}", profile.email ?? `ID ${profile.id ?? "-"}`)}
          confirmLabel={confirmation === "status" ? m.apply : m.revoke}
          danger
          busy={statusState === "loading" || actionState === "loading"}
          onCancel={() => setConfirmation(null)}
          onConfirm={() => void confirmRiskAction()}
        />}
        {notificationReview && <ConfirmDialog
          title={notificationCopy.confirmTitle}
          message={notificationCopy.confirmMessage.replace("{email}", profile.email ?? `ID ${profile.id ?? "-"}`)}
          confirmLabel={notificationCopy.confirm}
          busy={actionState === "loading"}
          onCancel={() => setNotificationReview(false)}
          onConfirm={() => void sendPersonalNotification()}
        />}
      </section>
    </div>
  );
}
