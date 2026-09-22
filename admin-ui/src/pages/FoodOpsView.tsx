import { lazy, ReactNode, Suspense, useEffect, useState } from "react";

import { formatRequestError, PageResponse, request } from "../api";

import { AdminCatalogImportJob, AdminCatalogQualityAnalytics, AdminProductQualityAiValidationResult, DashboardSummary, FoodProduct, ProductQualityScanRun, ProductQualityScanRunDetail, ProductQualityScanRunPage, ProductQualitySuggestion, ProductQualitySuggestionPage, ProductQualitySuggestionScanResult, ProductQualityAiSettings } from "../types";

import { AsyncState, CollapsiblePanel, DataTable, EmptyState, LoadState, MetricCard, PaginationControls, Panel, SectionToolbar, useDialogAccessibility } from "../AdminPrimitives";

import { Badge, DetailItem, MARKET_REGIONS, combineStates, countBy, formatDate, formatValue, parsePositiveInt, percent, shortFeature, useEndpoint } from "./../admin/shared";
import { CatalogImportJobsChart, CatalogPipelineOverviewChart, CatalogRegionChart } from "../CatalogWorkspaceCharts";
import { useAdminLocale } from "../admin/locale";

export const CatalogVerificationChart = lazy(() => import("../CatalogQualityCharts").then((module) => ({ default: module.CatalogVerificationChart })));

export const CatalogIssueChart = lazy(() => import("../CatalogQualityCharts").then((module) => ({ default: module.CatalogIssueChart })));

export const CatalogScanTrendChart = lazy(() => import("../CatalogQualityCharts").then((module) => ({ default: module.CatalogScanTrendChart })));

export const PRODUCT_QUALITY_SUGGESTION_STATUSES = ["OPEN", "ACCEPTED", "REJECTED"];

export type FoodOpsMode = "overview" | "imports" | "regions" | "quality";

export function FoodOpsView({ mode, onError }: { mode: FoodOpsMode; onError: (message: string | null) => void }) {
  const { locale } = useAdminLocale();
  const tr = locale === "tr";
  const { data: summary, state: summaryState, reload: reloadSummary } = useEndpoint<DashboardSummary>("/api/v1/admin/dashboard/summary", onError);
  const { data: products, state: productState, reload: reloadProducts } = useEndpoint<PageResponse<FoodProduct>>("/api/v1/admin/products/review?verificationStatus=RAW_IMPORTED&page=0&size=100", onError);
  const { data: importJobs, state: importState, reload: reloadImportJobs } = useEndpoint<AdminCatalogImportJob[]>("/api/v1/admin/catalog/import-jobs", onError);
  const [qualityWindowDays, setQualityWindowDays] = useState(30);
  const [suggestionStatus, setSuggestionStatus] = useState("OPEN");
  const [suggestionPage, setSuggestionPage] = useState(0);
  const [suggestionPageSize, setSuggestionPageSize] = useState(25);
  const [scanRegion, setScanRegion] = useState("");
  const [scanLimit, setScanLimit] = useState("250");
  const [forceRescan, setForceRescan] = useState(false);
  const [qualityActionState, setQualityActionState] = useState<LoadState>("ready");
  const [scanResult, setScanResult] = useState<ProductQualitySuggestionScanResult | null>(null);
  const [selectedScanRunDetail, setSelectedScanRunDetail] = useState<ProductQualityScanRunDetail | null>(null);
  const [aiValidationResult, setAiValidationResult] = useState<AdminProductQualityAiValidationResult | null>(null);
  const [selectedSuggestionIds, setSelectedSuggestionIds] = useState<number[]>([]);
  const [selectedQualitySuggestion, setSelectedQualitySuggestion] = useState<ProductQualitySuggestion | null>(null);
  const suggestionPath = buildProductQualitySuggestionPath({ status: suggestionStatus, page: suggestionPage, size: suggestionPageSize });
  const { data: suggestions, state: suggestionState, reload: reloadSuggestions } = useEndpoint<ProductQualitySuggestionPage>(suggestionPath, onError);
  const { data: scanRuns, state: scanRunState, reload: reloadScanRuns } = useEndpoint<ProductQualityScanRunPage>("/api/v1/admin/products/quality-suggestions/scan-runs?page=0&size=5", onError);
  const { data: qualityAnalytics, state: qualityAnalyticsState, reload: reloadQualityAnalytics } = useEndpoint<AdminCatalogQualityAnalytics>(`/api/v1/admin/catalog/quality-analytics?windowDays=${qualityWindowDays}`, onError);
  const { data: aiSettings, state: aiSettingsState, reload: reloadAiSettings } = useEndpoint<ProductQualityAiSettings>("/api/v1/admin/products/quality-suggestions/ai-settings", onError);
  const [aiSettingsForm, setAiSettingsForm] = useState({ enabled: true, maxProductsPerRun: "25", dailyProductLimit: "250", monthlyProductLimit: "2000", forceRescanAllowed: true, adminNote: "" });
  const [aiSettingsSaveState, setAiSettingsSaveState] = useState<LoadState>("ready");
  const [qualityGuardrailsOpen, setQualityGuardrailsOpen] = useState(false);
  const [qualityScanOpen, setQualityScanOpen] = useState(false);
  const [qualityScanHistoryOpen, setQualityScanHistoryOpen] = useState(false);
  const rows = products?.content ?? [];
  const suggestionRows = suggestions?.content ?? [];
  const scanRunRows = scanRuns?.content ?? [];
  const selectedOpenSuggestionIds = selectedSuggestionIds.filter((id) => suggestionRows.some((item) => item.id === id && item.status === "OPEN"));
  const byRegion = countBy(rows, (item) => item.marketRegion ?? "Unknown");
  const byImageStatus = countBy(rows, (item) => item.imageStatus ?? "Unknown");
  const byCatalogType = countBy(rows, (item) => item.catalogType ?? "Unknown");
  const localReadyPercent = percent(summary?.verifiedProducts, summary?.totalProducts);
  const openQualityIssueCount = (qualityAnalytics?.openIssueTypes ?? []).reduce((sum, item) => sum + Number(item.count ?? 0), 0);
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
    overview: tr ? "Gıda katalog operasyonları" : "Food catalog operations",
    imports: "Food import jobs",
    regions: "Food market regions",
    quality: tr ? "Katalog kalite merkezi" : "Catalog quality center"
  }[mode];

  function reloadAll() {
    void reloadSummary();
    void reloadProducts();
    void reloadSuggestions();
    void reloadScanRuns();
    void reloadQualityAnalytics();
    void reloadAiSettings();
    void reloadImportJobs();
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
    <div className={`stack modern-operations-page${mode === "quality" ? " catalog-quality-page" : ""}`}>
      <SectionToolbar title={title} state={combineStates([summaryState, productState, importState, suggestionState, scanRunState, qualityAnalyticsState, aiSettingsState, qualityActionState, aiSettingsSaveState])} onReload={reloadAll} />
      {mode === "overview" && <div className="food-ops-hero">
        <div>
          <p className="eyebrow">{tr ? "Öncelik yerel katalog" : "Local catalog first"}</p>
          <h2>{tr ? "İçe aktar, incele, doğrula ve kendi veritabanımızdan sun." : "Import, review, verify, then serve from our database."}</h2>
          <p>{tr ? "Open Food Facts yedek kaynak olarak kalır. İçe aktarılan ürünler güvenilir katalog verisi olmadan önce bölge, görsel ve besin değeri kontrollerinden geçmelidir." : "Open Food Facts remains a fallback source. Imported products should pass region, image, and nutrition review before being treated as high-quality catalog data."}</p>
        </div>
        <div className="food-ops-score">
          <strong>{localReadyPercent}%</strong>
          <span>{tr ? "doğrulanmış katalog oranı" : "verified catalog ratio"}</span>
        </div>
      </div>}
      {mode === "overview" && <div className="metric-grid">
        <MetricCard label={tr ? "Toplam ürün" : "Total products"} value={formatValue(summary?.totalProducts)} hint={tr ? "Mevcut yerel katalog" : "Current local catalog"} />
        <MetricCard label={tr ? "Doğrulandı" : "Verified"} value={formatValue(summary?.verifiedProducts)} hint={tr ? "Kullanıcıya sunulmaya hazır" : "Ready for user-facing use"} />
        <MetricCard label={tr ? "İnceleme bekliyor" : "Pending review"} value={formatValue(summary?.reviewQueueProducts)} hint={tr ? "Yönetici kontrolü gerekiyor" : "Needs admin attention"} />
        <MetricCard label={tr ? "Reddedildi" : "Rejected"} value={formatValue(summary?.rejectedProducts)} hint={tr ? "Güvenilir katalog dışında" : "Excluded from trusted catalog"} />
        <MetricCard label={tr ? "Ham örnek" : "Sampled raw"} value={formatValue(rows.length)} hint={tr ? "İncelenen son ham aktarımlar" : "Latest raw imports inspected here"} />
      </div>}
      {mode === "imports" && <div className="metric-grid">
        <MetricCard label="Raw imported" value={formatValue(summary?.rawImportedProducts)} hint="Imported rows waiting for normalization" />
        <MetricCard label="Needs review" value={formatValue(summary?.needsReviewProducts)} hint="Rows explicitly marked for review" />
        <MetricCard label="Sampled raw" value={formatValue(rows.length)} hint="Latest raw imports inspected here" />
      </div>}
      {mode === "overview" && <div className="catalog-workspace-main-grid">
        <Panel title={tr ? "İçe aktarma işleri" : "Import jobs"} description={tr ? "Son toplu veri akışları, kanıtlar, sorun sayıları ve tamamlanma zamanları." : "Recent bulk data flows, evidence, issue counts, and completion windows."}>
          <DataTable columns={tr ? ["İş", "Kaynak", "Bölge", "İşlenen", "Sorun", "Durum", "Zaman"] : ["Job", "Source", "Region", "Processed", "Issues", "Status", "Window"]} rows={(importJobs ?? []).map((item) => [
            <div className="entity-cell"><strong>{item.jobKey ?? "-"}</strong><small>{item.catalogType ?? "FOOD"}</small></div>,
            item.source ?? "-", item.region ?? "All", formatValue(item.processedItems), formatValue(item.issueItems),
            <Badge value={item.status} tone={item.status === "FAILED" ? "danger" : item.status === "PENDING" ? "warn" : "good"} />,
            <div className="entity-cell"><strong>{formatDate(item.startedAt)}</strong><small>{formatDate(item.completedAt)}</small></div>
          ])} empty={tr ? "Katalog aktarım işi bulunamadı." : "No catalog import jobs returned."} />
        </Panel>
        <div className="catalog-workspace-chart-rail">
          <Panel title={tr ? "Katalog akışı" : "Catalog pipeline"} description={tr ? "İçe aktarımdan güvenilir kataloğa geçiş." : "Movement from import to trusted catalog."}><CatalogPipelineOverviewChart summary={summary} locale={locale} /></Panel>
          <Panel title={tr ? "Bölge kapsamı" : "Region coverage"} description={tr ? "Ham aktarım örneğinin bölgesel dağılımı." : "Regional distribution in the raw-import sample."}><CatalogRegionChart items={Object.entries(byRegion).map(([name, count]) => ({ name, count }))} /></Panel>
          <Panel title={tr ? "Aktarım işi sağlığı" : "Import job health"} description={tr ? "Aktarım ve doğrulama işlerinin güncel durumu." : "Current import and validation job state."}><CatalogImportJobsChart jobs={importJobs ?? []} /></Panel>
        </div>
      </div>}
      {mode === "regions" && <DistributionPanel title="Region sample" items={byRegion} />}
      {mode === "quality" && <>
        <section className="modern-workspace-hero catalog-quality-hero">
          <div className="modern-hero-copy">
            <span className="eyebrow">{tr ? "KATALOG GÜVEN MERKEZİ" : "CATALOG TRUST CENTER"}</span>
            <h2>{tr ? "Kalite sorunlarını bul, kanıtla ve güvenle uygula." : "Detect, validate, and safely apply catalog quality decisions."}</h2>
            <p>{tr ? "Kural tabanlı tarama, sağlayıcı kanıtı ve kontrollü yapay zekâ doğrulaması aynı karar akışında birleşir." : "Rule-based scans, provider evidence, and controlled AI validation meet in one decision flow."}</p>
            <div className="modern-hero-progress"><span><b style={{ width: `${Math.min(100, percent(qualityAnalytics?.validatedProducts, qualityAnalytics?.totalProducts))}%` }} /></span><small>{formatValue(qualityAnalytics?.validatedProducts)} / {formatValue(qualityAnalytics?.totalProducts)} {tr ? "ürün doğrulandı" : "products validated"}</small></div>
          </div>
          <div className="modern-hero-signals">
            <article className="accent"><span>{tr ? "Kalite kapsamı" : "Quality coverage"}</span><strong>{percent(qualityAnalytics?.validatedProducts, qualityAnalytics?.totalProducts)}%</strong><small>{tr ? "doğrulanmış katalog" : "validated catalog"}</small></article>
            <article className="warning"><span>{tr ? "Açık sorun" : "Open issues"}</span><strong>{formatValue(openQualityIssueCount)}</strong><small>{tr ? "yönetici kararı bekliyor" : "awaiting admin decision"}</small></article>
            <article><span>{tr ? "Ortalama puan" : "Average score"}</span><strong>{qualityAnalytics?.averageQualityScore == null ? "-" : Number(qualityAnalytics.averageQualityScore).toFixed(1)}</strong><small>/ 100</small></article>
          </div>
        </section>
        <div className="catalog-quality-summary">
          <div><span className="eyebrow">{tr ? "ANALİTİK GÖRÜNÜM" : "ANALYTICS VIEW"}</span><strong>{tr ? "Kalite hareketi" : "Quality movement"}</strong><small>{tr ? "Grafikler seçilen zaman aralığını kullanır." : "Charts use the selected reporting window."}</small></div>
          <label>
            {tr ? "Analitik aralığı" : "Analytics window"}
            <select value={qualityWindowDays} onChange={(event) => setQualityWindowDays(Number(event.target.value))}>
              <option value={7}>{tr ? "7 gün" : "7 days"}</option>
              <option value={30}>{tr ? "30 gün" : "30 days"}</option>
              <option value={90}>{tr ? "90 gün" : "90 days"}</option>
            </select>
          </label>
        </div>
        <Suspense fallback={<AsyncState state="loading" hasData={false} loadingMessage={tr ? "Katalog kalite grafikleri yükleniyor…" : "Loading catalog quality charts..."} emptyMessage={tr ? "Katalog kalite analitiği kullanılamıyor." : "Catalog quality analytics are unavailable."} />}>
          <div className="catalog-quality-chart-grid">
            <Panel title={tr ? "Katalog doğrulaması" : "Catalog verification"} description={tr ? "Tüm katalog ürünlerinin güncel doğrulama durumuna göre dağılımı." : "All catalog products grouped by current verification state."} className="catalog-quality-chart-panel">
              {qualityAnalytics && <CatalogVerificationChart analytics={qualityAnalytics} />}
            </Panel>
            <Panel title={tr ? "Açık sorun yoğunluğu" : "Open issue concentration"} description={tr ? "Yönetici incelemesi gerektiren en yoğun çözülmemiş kalite sorunları." : "Top unresolved quality issue types requiring admin attention."} className="catalog-quality-chart-panel">
              {qualityAnalytics && <CatalogIssueChart analytics={qualityAnalytics} />}
            </Panel>
            <Panel title={tr ? "Tarama üretkenliği" : "Scan productivity"} description={tr ? "Seçilen aralıkta taranan, doğrulanan ve incelemeye yönlendirilen ürünler." : "Products scanned, validated, and routed to review in the selected window."} className="catalog-quality-chart-panel catalog-quality-trend-panel">
              {qualityAnalytics && <CatalogScanTrendChart analytics={qualityAnalytics} />}
            </Panel>
          </div>
        </Suspense>
      </>}
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
      {mode === "quality" && <CollapsiblePanel
        title={tr ? "Yapay zekâ doğrulama sınırları" : "AI quality validation guardrails"}
        description={tr ? "Kota, maliyet ve yeniden tarama kurallarını gerektiğinde yönetin." : "Manage quota, cost, and rescan rules when needed."}
        open={qualityGuardrailsOpen}
        onToggle={() => setQualityGuardrailsOpen((value) => !value)}
      >
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
      </CollapsiblePanel>}
      {mode === "quality" && <CollapsiblePanel
        title={tr ? "Kalite doğrulama taraması" : "Quality validation scan"}
        description={tr ? "Bölge ve kapsam seçerek kontrollü bir tarama başlatın." : "Run a controlled scan by region and scope."}
        open={qualityScanOpen}
        onToggle={() => setQualityScanOpen((value) => !value)}
      >
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
      </CollapsiblePanel>}
      {mode === "quality" && <CollapsiblePanel
        title={tr ? "Son kalite taramaları" : "Recent quality scan runs"}
        description={tr ? "Önceki taramaların sonuçlarını ve ürün ayrıntılarını gerektiğinde açın." : "Open prior scan results and product details when needed."}
        open={qualityScanHistoryOpen}
        onToggle={() => setQualityScanHistoryOpen((value) => !value)}
      >
        <div className="quality-table-panel compact-empty-panel">
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
        </div>
      </CollapsiblePanel>}
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

export function QualityScanRunDetailModal({ detail, onClose }: { detail: ProductQualityScanRunDetail; onClose: () => void }) {
  const dialogRef = useDialogAccessibility(onClose);
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
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <section ref={dialogRef} tabIndex={-1} className="modal-card scan-run-detail-modal" role="dialog" aria-modal="true" aria-label="Quality scan detail" onClick={(event) => event.stopPropagation()}>
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
      </section>
    </div>
  );
}

export function QualitySuggestionDecision({ item }: { item: ProductQualitySuggestion }) {
  const decision = qualitySuggestionDecision(item);
  return (
    <div className="quality-decision-cell">
      <Badge value={decision.label} tone={decision.tone} />
      <small>{decision.detail}</small>
    </div>
  );
}

export function QualitySuggestionDetailModal({
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
  const { locale } = useAdminLocale();
  const tr = locale === "tr";
  const [activeTab, setActiveTab] = useState<"overview" | "comparison" | "evidence" | "decision">("overview");
  const dialogRef = useDialogAccessibility(onClose);
  const decision = qualitySuggestionDecision(item);
  const tabs = [
    ["overview", tr ? "Genel bakış" : "Overview"],
    ["comparison", tr ? "Karşılaştırma" : "Comparison"],
    ["evidence", tr ? "Kanıt ve gerekçe" : "Evidence & reason"],
    ["decision", tr ? "Karar" : "Decision"]
  ] as const;
  const decisionTitle = tr
    ? item.status && item.status !== "OPEN"
      ? "Öneri daha önce değerlendirildi"
      : isQualitySuggestionApplyable(item)
        ? "Kabul edildiğinde katalog alanı güncellenebilir"
        : item.suggestionType === "SEARCH_ALIAS" || item.suggestionType === "NAME_CLEANUP"
          ? "Bu öneri için güvenli bir uygulama akışı var"
          : "Kabul işlemi bulguyu kapatır; ürün alanını otomatik değiştirmez"
    : decision.title;
  const decisionDetail = tr
    ? item.status && item.status !== "OPEN"
      ? item.reviewedBy ? `${item.reviewedBy} tarafından değerlendirildi.` : "Bu öneri kapatıldı."
      : isQualitySuggestionApplyable(item)
        ? "Backend izin verilen alanı günceller, denetim kaydı oluşturur ve kalite doğrulamasını yeni kontrol için sıfırlar."
        : item.suggestionType === "SEARCH_ALIAS" || item.suggestionType === "NAME_CLEANUP"
          ? "Kabul işlemi öneriye özel güvenli uygulama yolunu çalıştırır ve denetim kaydı oluşturur."
          : "Manuel ürün incelemesi gerektiren uyarı türündeki yapay zekâ bulguları için kullanılır."
    : decision.detail;
  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <section ref={dialogRef} tabIndex={-1} className="modal-card quality-suggestion-modal quality-assistant-modal" role="dialog" aria-modal="true" aria-label={tr ? "Kalite asistanı öneri detayı" : "Quality assistant suggestion detail"} onClick={(event) => event.stopPropagation()}>
        <div className="modal-header">
          <div>
            <span>{tr ? "KALİTE ASİSTANI ÖNERİSİ" : "QUALITY ASSISTANT SUGGESTION"}</span>
            <h2>{item.productName ?? `Product #${item.foodItemId ?? "-"}`}</h2>
            <p>{item.brand ?? (tr ? "Bilinmeyen marka" : "Unknown brand")}</p>
          </div>
          <button className="ghost-button" type="button" onClick={onClose}>{tr ? "Kapat" : "Close"}</button>
        </div>
        <div className="modal-body quality-suggestion-body">
          <nav className="quality-assistant-tabs" aria-label={tr ? "Öneri detay bölümleri" : "Suggestion detail sections"}>
            {tabs.map(([value, label]) => (
              <button key={value} type="button" className={activeTab === value ? "active" : ""} aria-selected={activeTab === value} onClick={() => setActiveTab(value)}>{label}</button>
            ))}
          </nav>

          {activeTab === "overview" && <div className="quality-assistant-pane">
            <div className="quality-suggestion-summary">
              <div><span>{tr ? "Kaynak" : "Source"}</span><strong>{shortFeature(item.source)}</strong></div>
              <div><span>{tr ? "Tür" : "Type"}</span><strong>{shortFeature(item.suggestionType)}</strong></div>
              <div><span>{tr ? "Güven" : "Confidence"}</span><strong>{formatValue(item.confidenceScore)}%</strong></div>
              <div><span>{tr ? "Durum" : "Status"}</span><strong>{shortFeature(item.status)}</strong></div>
            </div>
            <div className={`quality-decision-banner ${decision.tone}`}>
              <Badge value={decision.label} tone={decision.tone} />
              <div><strong>{decisionTitle}</strong><p>{decisionDetail}</p></div>
            </div>
            <QualityImpactSummary item={item} locale={locale} />
          </div>}

          {activeTab === "comparison" && <div className="quality-assistant-pane">
            <div className="quality-diff-grid">
              <article><span>{tr ? "Alan" : "Field"}</span><strong>{qualityFieldLabel(item.fieldName)}</strong><small>{item.fieldName ?? (tr ? "Alan bilgisi yok" : "No field supplied")}</small></article>
              <article><span>{tr ? "Mevcut değer" : "Current value"}</span><strong>{item.currentValue ?? "-"}</strong><small>{tr ? "Karar öncesi katalog değeri" : "Stored catalog value before admin decision"}</small></article>
              <article><span>{tr ? "Önerilen değer" : "Suggested value"}</span><strong>{item.suggestedValue ?? "-"}</strong><small>{tr ? "Kural veya yapay zekâ tarafından önerildi" : "Value proposed by rule or AI assistant"}</small></article>
            </div>
            <div className="quality-preview-grid">
              <article><header><span>{tr ? "Kabul öncesi" : "Before accept"}</span><Badge value={tr ? "Mevcut ürün" : "Current product"} tone="neutral" /></header><QualityPreviewRows item={item} mode="before" locale={locale} /></article>
              <article><header><span>{tr ? "Kabul sonrası" : "After accept"}</span><Badge value={isQualitySuggestionApplyable(item) || item.suggestionType === "NAME_CLEANUP" || item.suggestionType === "SEARCH_ALIAS" ? (tr ? "Ön izleme" : "Preview") : (tr ? "Yalnızca inceleme" : "Review only")} tone={isQualitySuggestionApplyable(item) || item.suggestionType === "NAME_CLEANUP" || item.suggestionType === "SEARCH_ALIAS" ? "good" : "warn"} /></header><QualityPreviewRows item={item} mode="after" locale={locale} /></article>
            </div>
          </div>}

          {activeTab === "evidence" && <div className="quality-assistant-pane">
            <Panel title={tr ? "Gerekçe ve denetim bağlamı" : "Reason and audit context"}>
              <div className="quality-reason-box">
                <p>{item.reason ?? (tr ? "Bu öneri için gerekçe sağlanmadı." : "No reason returned for this suggestion.")}</p>
                <div>
                  <DetailItem label={tr ? "Oluşturuldu" : "Created"} value={formatDate(item.createdAt)} />
                  <DetailItem label={tr ? "İncelendi" : "Reviewed"} value={formatDate(item.reviewedAt)} />
                  <DetailItem label={tr ? "İnceleyen" : "Reviewed by"} value={item.reviewedBy ?? "-"} />
                </div>
              </div>
            </Panel>
          </div>
          }

          {activeTab === "decision" && <div className="quality-assistant-pane quality-assistant-decision">
            <div className={`quality-decision-banner ${decision.tone}`}>
              <Badge value={decision.label} tone={decision.tone} />
              <div><strong>{decisionTitle}</strong><p>{decisionDetail}</p></div>
            </div>
            <div className="quality-assistant-decision-card">
              <span>{tr ? "İşlem sonucu" : "Action outcome"}</span>
              <strong>{item.status === "OPEN" ? (tr ? "Yönetici kararı bekleniyor" : "Awaiting admin decision") : shortFeature(item.status)}</strong>
              <p>{tr ? "Kabul veya ret işlemi denetim geçmişine kaydedilir. Kabulün veri etkisi yukarıdaki karar özetinde belirtilir." : "Accept or reject is recorded in audit history. The data effect of acceptance is described in the decision summary above."}</p>
            </div>
          </div>}
        </div>
        <div className="modal-actions padded-actions">
          <button className="ghost-button" type="button" onClick={onClose}>{tr ? "İptal" : "Cancel"}</button>
          {item.status === "OPEN" && <button className="ghost-button danger-text" type="button" disabled={busy} onClick={() => onReject(item)}>{tr ? "Reddet" : "Reject"}</button>}
          {item.status === "OPEN" && <button className="primary-button" type="button" disabled={busy} onClick={() => onAccept(item)}>{tr ? "Öneriyi kabul et" : "Accept suggestion"}</button>}
        </div>
      </section>
    </div>
  );
}

export function QualityImpactSummary({ item, locale = "en" }: { item: ProductQualitySuggestion; locale?: "tr" | "en" }) {
  const impact = qualityFieldImpact(item);
  const tr = locale === "tr";
  return (
    <div className="quality-impact-grid">
      <article>
        <span>{tr ? "Alan grubu" : "Field group"}</span>
        <strong>{impact.group}</strong>
        <small>{impact.description}</small>
      </article>
      <article>
        <span>{tr ? "Katalog yazımı" : "Catalog write"}</span>
        <strong>{impact.writePath}</strong>
        <small>{impact.resetValidation ? (tr ? "Kabul sonrası kalite doğrulaması sıfırlanır." : "Quality validation will be reset after accept.") : (tr ? "Mevcut ürün doğrulama işareti değiştirilmez." : "Existing product validation marker is not changed.")}</small>
      </article>
      <article>
        <span>{tr ? "Denetim izi" : "Audit trail"}</span>
        <strong>{impact.audit}</strong>
        <small>{impact.adminAction}</small>
      </article>
    </div>
  );
}

export function QualityPreviewRows({ item, mode, locale = "en" }: { item: ProductQualitySuggestion; mode: "before" | "after"; locale?: "tr" | "en" }) {
  const tr = locale === "tr";
  const canPreviewApply = isQualitySuggestionApplyable(item) || item.suggestionType === "NAME_CLEANUP";
  const isAfter = mode === "after";
  const fieldValue = isAfter && canPreviewApply
    ? item.suggestedValue ?? "-"
    : item.currentValue ?? "-";
  const rows: Array<[string, ReactNode]> = [
    [tr ? "Ürün" : "Product", item.productName ?? `Product #${item.foodItemId ?? "-"}`],
    [tr ? "Marka" : "Brand", item.brand ?? "-"],
    [tr ? "Alan" : "Field", qualityFieldLabel(item.fieldName)],
    [tr ? "Değer" : "Value", fieldValue]
  ];

  if (item.suggestionType === "SEARCH_ALIAS") {
    rows.splice(2, 2,
      [tr ? "Takma ad işlemi" : "Alias action", isAfter ? (tr ? "Takma ad oluşturulur veya yeniden etkinleştirilir" : "Alias will be created or reactivated") : (tr ? "Henüz takma ad değişikliği yok" : "No alias change yet")],
      [tr ? "Takma ad" : "Alias", isAfter ? item.suggestedValue ?? "-" : item.currentValue ?? "-"]
    );
  }

  if (isAfter && !canPreviewApply && item.suggestionType !== "SEARCH_ALIAS") {
    rows.push([tr ? "Katalog yazımı" : "Catalog write", tr ? "Otomatik ürün alanı güncellemesi yok" : "No automatic product field update"]);
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

export function qualitySuggestionDecision(item: ProductQualitySuggestion): { label: string; title: string; detail: string; tone: "default" | "good" | "warn" | "danger" | "neutral" } {
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

export type QualityFieldImpact = {
  group: string;
  description: string;
  writePath: string;
  audit: string;
  adminAction: string;
  resetValidation: boolean;
};

export function qualityFieldImpact(item: ProductQualitySuggestion): QualityFieldImpact {
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

export function isQualitySuggestionApplyable(item: ProductQualitySuggestion): boolean {
  const field = normalizeQualityField(item.fieldName);
  if (!field || !item.suggestedValue) return false;
  return QUALITY_APPLY_FIELDS.has(field);
}

export const NUTRITION_QUALITY_FIELDS = new Set([
  "calories", "protein", "fat", "carbs", "fiber", "sugar", "sodium", "potassium", "cholesterol",
  "calcium", "iron", "magnesium", "zinc", "vitamina", "vitaminc", "vitamind", "vitamine",
  "vitaminb12", "saturatedfat", "transfat", "sugaralcohol", "servingsizegrams", "servingunit",
  "nutriscore", "allergens"
]);

export const IMAGE_QUALITY_FIELDS = new Set([
  "imagesource", "imagestatus", "imageurl", "externalimageurl", "displayimageurl"
]);

export const STATUS_REGION_QUALITY_FIELDS = new Set([
  "verificationstatus", "marketregion", "preparationstate"
]);

export const QUALITY_APPLY_FIELDS = new Set([
  "calories", "protein", "fat", "carbs", "fiber", "sugar", "sodium", "potassium", "cholesterol",
  "calcium", "iron", "magnesium", "zinc", "vitamina", "vitaminc", "vitamind", "vitamine",
  "vitaminb12", "saturatedfat", "transfat", "sugaralcohol", "servingsizegrams", "servingunit",
  "imagesource", "imagestatus", "imageurl", "externalimageurl", "displayimageurl", "verificationstatus",
  "marketregion", "preparationstate", "nutriscore", "allergens"
]);

export function normalizeQualityField(value?: string): string {
  return String(value ?? "").replace(/[_-]/g, "").toLowerCase();
}

export function qualityFieldLabel(value?: string): string {
  if (!value) return "No field";
  return shortFeature(value.replace(/([a-z])([A-Z])/g, "$1 $2"));
}

export function DistributionPanel({ title, items }: { title: string; items: Record<string, number> }) {
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

export function buildProductQualitySuggestionPath(filters: { status: string; page: number; size: number }): string {
  const params = new URLSearchParams();
  params.set("status", filters.status || "OPEN");
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));
  return `/api/v1/admin/products/quality-suggestions?${params.toString()}`;
}
