import { useEffect, useMemo, useRef, useState } from "react";

import { formatRequestError, PageResponse, request, requestBlob, requestFormData } from "../api";

import { AdminProductQualityWorkbench, AdminProductQualityAiValidationResult, FoodProduct, FoodSearchAlias, ProductQualitySuggestion } from "../types";

import { CollapsiblePanel, DataTable, LoadState, MetricCard, PaginationControls, Panel, SectionToolbar, useDialogAccessibility } from "../AdminPrimitives";

import { Badge, ConfirmDialog, DetailItem, EditableDetail, IMAGE_SOURCES, IMAGE_STATUSES, MARKET_REGIONS, PREFERRED_LANGUAGES, VERIFICATION_STATUSES, downloadBlob, formatDate, formatValue, humanizeFeature, productName, useEndpoint } from "./../admin/shared";
import { BarcodeScanner } from "../admin/BarcodeScanner";
import { normalizeBarcode, validateGtin } from "../admin/barcode";
import { useAdminLocale } from "../admin/locale";
import { ProductQualityWorkloadChart, ProductReviewHealthChart } from "../CatalogWorkspaceCharts";

export const CATALOG_TYPES = ["BRANDED_PRODUCT", "GENERIC_INGREDIENT", "LOCAL_DISH", "USER_CUSTOM"];

export const DATA_SOURCES = ["OPEN_FOOD_FACTS", "ADMIN_IMPORT", "USDA_FOODDATA", "USER_CUSTOM"];

export const FOOD_SEARCH_ALIAS_TYPES = ["ADMIN_MANUAL", "TRANSLATION", "SYNONYM", "ASCII_NORMALIZED", "COMMON_NAME"];

export const QUALITY_ISSUES = [
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

export type ProductReviewDraft = {
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

export type ProductReviewNumberField = Extract<keyof ProductReviewDraft,
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

export const PRODUCT_MACRO_FIELDS: Array<{ key: ProductReviewNumberField; label: string; suffix: string }> = [
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

export const PRODUCT_MINERAL_FIELDS: Array<{ key: ProductReviewNumberField; label: string; suffix: string }> = [
  { key: "sodium", label: "Sodium", suffix: "mg" },
  { key: "potassium", label: "Potassium", suffix: "mg" },
  { key: "cholesterol", label: "Cholesterol", suffix: "mg" },
  { key: "calcium", label: "Calcium", suffix: "mg" },
  { key: "iron", label: "Iron", suffix: "mg" },
  { key: "magnesium", label: "Magnesium", suffix: "mg" },
  { key: "zinc", label: "Zinc", suffix: "mg" }
];

export const PRODUCT_VITAMIN_FIELDS: Array<{ key: ProductReviewNumberField; label: string; suffix: string }> = [
  { key: "vitaminA", label: "Vitamin A", suffix: "ug" },
  { key: "vitaminC", label: "Vitamin C", suffix: "mg" },
  { key: "vitaminD", label: "Vitamin D", suffix: "ug" },
  { key: "vitaminE", label: "Vitamin E", suffix: "mg" },
  { key: "vitaminB12", label: "Vitamin B12", suffix: "ug" }
];

export type ProductReviewMode = "queue" | "images" | "nutrition" | "rejected";

export type ProductReviewRouteState = {
  query: string;
  verificationStatus: string;
  imageStatus: string;
  region: string;
  catalogType: string;
  dataSource: string;
  qualityIssue: string;
  page: number;
  size: number;
  selectedProductId?: number;
};

const PRODUCT_PAGE_SIZES = new Set([10, 20, 25, 50, 100]);

export function defaultProductReviewRouteState(mode: ProductReviewMode): ProductReviewRouteState {
  return {
    query: "",
    verificationStatus: mode === "queue" ? "RAW_IMPORTED" : mode === "rejected" ? "REJECTED" : "",
    imageStatus: mode === "images" ? "NEEDS_REVIEW" : "",
    region: "",
    catalogType: "",
    dataSource: "",
    qualityIssue: mode === "images" ? "MISSING_IMAGE" : mode === "nutrition" ? "SUSPICIOUS_MACROS" : "",
    page: 0,
    size: 20
  };
}

export function readProductReviewRouteState(mode: ProductReviewMode, search = window.location.search): ProductReviewRouteState {
  const defaults = defaultProductReviewRouteState(mode);
  const params = new URLSearchParams(search);
  const enumValue = (key: string, allowed: string[], fallback: string) => {
    const value = params.get(key);
    return value !== null && (value === "" || allowed.includes(value)) ? value : fallback;
  };
  const page = Number(params.get("page"));
  const size = Number(params.get("size"));
  const selectedProductId = Number(params.get("productId"));
  return {
    query: (params.get("query") ?? defaults.query).trim(),
    verificationStatus: enumValue("verification", VERIFICATION_STATUSES, defaults.verificationStatus),
    imageStatus: enumValue("image", IMAGE_STATUSES, defaults.imageStatus),
    region: enumValue("region", MARKET_REGIONS, defaults.region),
    catalogType: enumValue("catalog", CATALOG_TYPES, defaults.catalogType),
    dataSource: enumValue("source", DATA_SOURCES, defaults.dataSource),
    qualityIssue: enumValue("issue", QUALITY_ISSUES, defaults.qualityIssue),
    page: Number.isSafeInteger(page) && page >= 0 ? page : defaults.page,
    size: Number.isSafeInteger(size) && PRODUCT_PAGE_SIZES.has(size) ? size : defaults.size,
    selectedProductId: Number.isSafeInteger(selectedProductId) && selectedProductId > 0 ? selectedProductId : undefined
  };
}

export function productReviewRouteSearch(state: ProductReviewRouteState, mode: ProductReviewMode): string {
  const defaults = defaultProductReviewRouteState(mode);
  const params = new URLSearchParams();
  if (state.query) params.set("query", state.query);
  if (state.verificationStatus !== defaults.verificationStatus) params.set("verification", state.verificationStatus);
  if (state.imageStatus !== defaults.imageStatus) params.set("image", state.imageStatus);
  if (state.region) params.set("region", state.region);
  if (state.catalogType) params.set("catalog", state.catalogType);
  if (state.dataSource) params.set("source", state.dataSource);
  if (state.qualityIssue !== defaults.qualityIssue) params.set("issue", state.qualityIssue);
  if (state.page > 0) params.set("page", String(state.page));
  if (state.size !== defaults.size) params.set("size", String(state.size));
  if (state.selectedProductId) params.set("productId", String(state.selectedProductId));
  const query = params.toString();
  return query ? `?${query}` : "";
}

export type NutritionCorrectionImportResult = {
  totalRows?: number;
  updatedRows?: number;
  skippedRows?: number;
  candidateRows?: number;
  dryRun?: boolean;
  errors?: string[];
};

const productReviewMessages = {
  tr: {
    modeTitle: { queue: "Ürün inceleme kuyruğu", images: "Ürün görseli inceleme", nutrition: "Besin değeri inceleme", rejected: "Reddedilen ürünler" },
    modeDescription: { queue: "Öncelikli ürünleri bulun, kanıtları kontrol edin ve katalog kararını tamamlayın.", images: "Eksik veya inceleme bekleyen ürün görsellerini doğrulayın.", nutrition: "Şüpheli besin değerlerini kaynak kanıtlarıyla karşılaştırın.", rejected: "Reddedilen ürünleri ve karar gerekçelerini yeniden değerlendirin." },
    resetFilters: "Filtreleri sıfırla", returnedProducts: "Eşleşen ürün", returnedProductsHint: "Geçerli filtre sonucundaki toplam", highPriority: "Yüksek öncelik", highPriorityHint: "Öncelik puanı 100 ve üzeri", missingImages: "Görseli eksik", missingImagesHint: "Kullanılabilir görseli olmayan satırlar", activeFilters: "Etkin filtre", activeFiltersHint: "Kuyruğa uygulanan ölçütler",
    filtersTitle: "Kuyruğu daralt", filtersDescription: "İncelenecek ürünleri görev, kaynak ve kalite sorununa göre odaklayın.", productSearch: "Ürün ara", productSearchPlaceholder: "Ad, marka veya barkod", verification: "Doğrulama", imageStatus: "Görsel durumu", region: "Bölge", catalog: "Katalog", dataSource: "Veri kaynağı", qualityIssue: "Kalite sorunu", all: "Tümü", anyIssue: "Tüm sorunlar",
    bulkTools: "Toplu düzeltme araçları", bulkWorkflow: "Toplu düzeltme akışı", bulkDescription: "Mevcut filtreyi dışa aktarın, değerleri dosyada düzeltin, önce prova çalıştırın ve sonuç temizse uygulayın.", exportFilter: "Filtreyi dışa aktar", correctionFile: "Düzeltme dosyası", chooseFile: "Dosya seç", noFile: "Henüz dosya seçilmedi", markVerified: "Aktarılan satırları doğrulandı olarak işaretle", dryRun: "Prova çalıştır", applyImport: "İçe aktarmayı uygula",
    queueEyebrow: "ÇALIŞMA KUYRUĞU", queueTitle: "İncelenecek ürünler", queueDescription: "Bir satır seçerek kanıt, kalite önerileri ve düzenleme alanlarını açın.", product: "Ürün", source: "Kaynak", review: "İnceleme", quality: "Kalite", nutrition: "Besin değerleri", empty: "Bu filtre için incelenecek ürün bulunamadı.", rejectTitle: "Ürün reddedilsin mi?", rejectMessage: "Ürün ve görsel reddedildi olarak işaretlenecek; karar denetim geçmişine kaydedilecek.", rejectAction: "Ürünü reddet"
  },
  en: {
    modeTitle: { queue: "Product review queue", images: "Product image review", nutrition: "Nutrition review", rejected: "Rejected products" },
    modeDescription: { queue: "Find priority products, verify evidence, and complete the catalog decision.", images: "Validate missing product images and images waiting for review.", nutrition: "Compare suspicious nutrition values against source evidence.", rejected: "Reassess rejected products and their decision history." },
    resetFilters: "Reset filters", returnedProducts: "Matching products", returnedProductsHint: "Total in the current filter", highPriority: "High priority", highPriorityHint: "Priority score 100 or above", missingImages: "Missing images", missingImagesHint: "Rows without a usable image", activeFilters: "Active filters", activeFiltersHint: "Criteria applied to the queue",
    filtersTitle: "Refine the queue", filtersDescription: "Focus the workload by task, source, and quality issue.", productSearch: "Product search", productSearchPlaceholder: "Name, brand, or barcode", verification: "Verification", imageStatus: "Image status", region: "Region", catalog: "Catalog", dataSource: "Data source", qualityIssue: "Quality issue", all: "All", anyIssue: "Any issue",
    bulkTools: "Bulk correction tools", bulkWorkflow: "Bulk correction workflow", bulkDescription: "Export the current filter, correct values in the file, run a dry run, and apply only after the result is clean.", exportFilter: "Export current filter", correctionFile: "Correction file", chooseFile: "Choose file", noFile: "No file selected", markVerified: "Mark imported rows verified", dryRun: "Run dry run", applyImport: "Apply import",
    queueEyebrow: "WORK QUEUE", queueTitle: "Products to review", queueDescription: "Select a row to open evidence, quality recommendations, and editing fields.", product: "Product", source: "Source", review: "Review", quality: "Quality", nutrition: "Nutrition", empty: "No products need review for this filter.", rejectTitle: "Reject product?", rejectMessage: "The product and its image will be marked rejected and the decision will be recorded in audit history.", rejectAction: "Reject product"
  }
} as const;

export function ProductReviewView({ mode, onError, canManage = false }: { mode: ProductReviewMode; onError: (message: string | null) => void; canManage?: boolean }) {
  const { locale } = useAdminLocale();
  const text = productReviewMessages[locale];
  const [activeMode, setActiveMode] = useState<ProductReviewMode>(mode === "queue" ? "queue" : mode);
  const viewMode = mode === "queue" ? "queue" : activeMode;
  const initialRouteState = useMemo(() => readProductReviewRouteState(viewMode), []);
  const skipInitialFilterReset = useRef(true);
  const [query, setQuery] = useState(initialRouteState.query);
  const [verificationStatus, setVerificationStatus] = useState(initialRouteState.verificationStatus);
  const [imageStatus, setImageStatus] = useState(initialRouteState.imageStatus);
  const [region, setRegion] = useState(initialRouteState.region);
  const [catalogType, setCatalogType] = useState(initialRouteState.catalogType);
  const [dataSource, setDataSource] = useState(initialRouteState.dataSource);
  const [qualityIssue, setQualityIssue] = useState(initialRouteState.qualityIssue);
  const [page, setPage] = useState(initialRouteState.page);
  const [pageSize, setPageSize] = useState(initialRouteState.size);
  const [selectedProductId, setSelectedProductId] = useState<number | undefined>(initialRouteState.selectedProductId);
  const [selectedProduct, setSelectedProduct] = useState<FoodProduct | null>(null);
  const [reviewDraft, setReviewDraft] = useState<ProductReviewDraft | null>(null);
  const [reviewNote, setReviewNote] = useState("");
  const [rejectConfirmationOpen, setRejectConfirmationOpen] = useState(false);
  const [filtersOpen, setFiltersOpen] = useState(false);
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
  const { data: imageWorkload } = useEndpoint<PageResponse<FoodProduct>>("/api/v1/admin/products/review?imageStatus=NEEDS_REVIEW&qualityIssue=MISSING_IMAGE&page=0&size=1", onError);
  const { data: nutritionWorkload } = useEndpoint<PageResponse<FoodProduct>>("/api/v1/admin/products/review?qualityIssue=SUSPICIOUS_MACROS&page=0&size=1", onError);
  const { data: rejectedWorkload } = useEndpoint<PageResponse<FoodProduct>>("/api/v1/admin/products/review?verificationStatus=REJECTED&page=0&size=1", onError);
  const rows = data?.content ?? [];
  const totalElements = data?.totalElements ?? rows.length;
  const highPriorityCount = rows.filter((item) => (item.reviewPriority ?? 0) >= 100).length;
  const missingImageCount = rows.filter((item) => !item.displayImageUrl && !item.imageUrl && !item.externalImageUrl).length;
  const averageQuality = rows.length ? Math.round(rows.reduce((sum, item) => sum + Number(item.qualityScore ?? 0), 0) / rows.length) : 0;
  const activeFilterCount = [query, verificationStatus, imageStatus, region, catalogType, dataSource, qualityIssue].filter(Boolean).length;
  const qualityWorkspace = mode !== "queue";
  const modeTitle = qualityWorkspace ? (locale === "tr" ? "Ürün kalite merkezi" : "Product quality center") : text.modeTitle[viewMode];

  useEffect(() => {
    if (skipInitialFilterReset.current) {
      skipInitialFilterReset.current = false;
      return;
    }
    setPage(0);
  }, [query, verificationStatus, imageStatus, region, catalogType, dataSource, qualityIssue, pageSize]);

  useEffect(() => {
    const next = `${window.location.pathname}${productReviewRouteSearch({ query, verificationStatus, imageStatus, region, catalogType, dataSource, qualityIssue, page, size: pageSize, selectedProductId }, viewMode)}`;
    if (`${window.location.pathname}${window.location.search}` !== next) window.history.replaceState(null, "", next);
  }, [catalogType, dataSource, imageStatus, page, pageSize, qualityIssue, query, region, selectedProductId, verificationStatus, viewMode]);

  useEffect(() => {
    if (!selectedProductId || selectedProduct?.id === selectedProductId) return;
    const restoredProduct = rows.find((item) => item.id === selectedProductId);
    if (restoredProduct) openProduct(restoredProduct);
  }, [rows, selectedProduct, selectedProductId]);

  function applyModeDefaults(targetMode: ProductReviewMode = viewMode) {
    const defaults = defaultProductReviewRouteState(targetMode);
    setQuery("");
    setRegion("");
    setCatalogType("");
    setDataSource("");
    setPage(0);
    setPageSize(defaults.size);
    setSelectedProductId(undefined);
    closeProductModal();
    if (targetMode === "queue") {
      setVerificationStatus("RAW_IMPORTED");
      setImageStatus("");
      setQualityIssue("");
    }
    if (targetMode === "images") {
      setVerificationStatus("");
      setImageStatus("NEEDS_REVIEW");
      setQualityIssue("MISSING_IMAGE");
    }
    if (targetMode === "nutrition") {
      setVerificationStatus("");
      setImageStatus("");
      setQualityIssue("SUSPICIOUS_MACROS");
    }
    if (targetMode === "rejected") {
      setVerificationStatus("REJECTED");
      setImageStatus("");
      setQualityIssue("");
    }
  }

  function resetFilters() {
    applyModeDefaults();
  }

  function selectQualityLane(nextMode: "images" | "nutrition" | "rejected") {
    setActiveMode(nextMode);
    applyModeDefaults(nextMode);
  }

  function openProduct(item: FoodProduct) {
    setSelectedProduct(item);
    setSelectedProductId(item.id);
    setReviewDraft(toProductReviewDraft(item));
    setReviewNote("");
    setRejectConfirmationOpen(false);
  }

  function closeProductModal() {
    setSelectedProduct(null);
    setSelectedProductId(undefined);
    setReviewDraft(null);
    setReviewNote("");
    setRejectConfirmationOpen(false);
  }

  async function lookupBarcode(barcode: string) {
    onError(null);
    try {
      const result = await request<PageResponse<FoodProduct>>(buildProductReviewPath({
        query: barcode, verificationStatus: "", imageStatus: "", region: "", catalogType: "", dataSource: "", qualityIssue: "", page: 0, size: 10
      }));
      const exact = (result.content ?? []).find(item => normalizeBarcode(item.normalizedBarcode ?? item.barcode ?? "") === barcode);
      if (exact) {
        setQuery(barcode);
        setVerificationStatus("");
        setImageStatus("");
        setRegion("");
        setCatalogType("");
        setDataSource("");
        setQualityIssue("");
        setPage(0);
        openProduct(exact);
      }
      return Boolean(exact);
    } catch (error) {
      onError(formatRequestError(error));
      throw error;
    }
  }

  async function barcodeSaved(updated: FoodProduct) {
    setSelectedProduct(updated);
    setReviewDraft(toProductReviewDraft(updated));
    await reload();
    setSavedNotice("Saved");
    window.setTimeout(() => setSavedNotice(null), 2200);
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
    <div className="stack product-review-page modern-operations-page">
      <SectionToolbar title={modeTitle} description={qualityWorkspace ? (locale === "tr" ? "Görsel, besin değeri ve reddedilen ürün incelemelerini tek çalışma alanından yönetin." : "Manage image, nutrition, and rejected-product reviews from one workspace.") : text.modeDescription[viewMode]} state={state} onReload={reload}>
        <button className="ghost-button" onClick={resetFilters} type="button">{text.resetFilters}</button>
      </SectionToolbar>
      {savedNotice && <div className="success-banner compact-success">{savedNotice}</div>}
      {viewMode === "queue" && <section className="modern-workspace-hero product-review-hero">
        <div className="modern-hero-chart">
          <div className="modern-section-heading"><div><span className="eyebrow">{locale === "tr" ? "KATALOG KARAR MERKEZİ" : "CATALOG DECISION CENTER"}</span><h3>{locale === "tr" ? "İnceleme sağlığı" : "Review health"}</h3><p>{locale === "tr" ? "Kalite dağılımı ve öncelikli sorunlar, sıradaki doğru kararı hızlandırır." : "Quality distribution and priority signals guide the next decision."}</p></div><strong>{formatValue(totalElements)}</strong></div>
          <ProductReviewHealthChart products={rows} locale={locale} />
        </div>
        <div className="modern-hero-signals">
          <article className="accent"><span>{locale === "tr" ? "Ortalama kalite" : "Average quality"}</span><strong>{averageQuality}</strong><small>/ 100</small></article>
          <article className="warning"><span>{text.highPriority}</span><strong>{formatValue(highPriorityCount)}</strong><small>{text.highPriorityHint}</small></article>
          <article className="danger"><span>{text.missingImages}</span><strong>{formatValue(missingImageCount)}</strong><small>{text.missingImagesHint}</small></article>
        </div>
      </section>}
      {viewMode === "queue" && <BarcodeScanner onLookup={lookupBarcode} />}

      {qualityWorkspace && <div className="product-quality-overview">
        <Panel title={locale === "tr" ? "İnceleme iş yükü" : "Review workload"} description={locale === "tr" ? "Üç kalite kuyruğunun güncel dağılımı." : "Current distribution across the three quality queues."}><ProductQualityWorkloadChart images={imageWorkload?.totalElements ?? 0} nutrition={nutritionWorkload?.totalElements ?? 0} rejected={rejectedWorkload?.totalElements ?? 0} locale={locale} /></Panel>
        <div className="product-quality-lanes" role="tablist" aria-label={locale === "tr" ? "Kalite kuyruğu" : "Quality queue"}>
          {(["images", "nutrition", "rejected"] as const).map((lane) => <button key={lane} type="button" role="tab" aria-selected={viewMode === lane} className={viewMode === lane ? "active" : ""} onClick={() => selectQualityLane(lane)}><span>{text.modeTitle[lane]}</span><strong>{formatValue(lane === "images" ? imageWorkload?.totalElements : lane === "nutrition" ? nutritionWorkload?.totalElements : rejectedWorkload?.totalElements)}</strong><small>{text.modeDescription[lane]}</small></button>)}
        </div>
      </div>}

      {qualityWorkspace && <div className="review-workspace-summary product-review-kpis">
        {viewMode !== "rejected" && <MetricCard label={text.highPriority} value={formatValue(highPriorityCount)} hint={text.highPriorityHint} />}
        {viewMode !== "nutrition" && <MetricCard label={text.missingImages} value={formatValue(missingImageCount)} hint={text.missingImagesHint} />}
        <MetricCard label={text.activeFilters} value={formatValue(activeFilterCount)} hint={text.activeFiltersHint} />
      </div>}

      {activeFilterCount > 0 && <div className="product-review-active-filters" aria-label={locale === "tr" ? "Etkin filtreler" : "Active filters"}>{[
        query && `${text.productSearch}: ${query}`,
        query && verificationStatus === "RAW_IMPORTED"
          ? (locale === "tr" ? "Arama kapsamı: Tüm katalog" : "Search scope: Entire catalog")
          : verificationStatus && `${text.verification}: ${humanizeFeature(verificationStatus)}`,
        imageStatus && `${text.imageStatus}: ${humanizeFeature(imageStatus)}`,
        region && `${text.region}: ${region}`,
        catalogType && `${text.catalog}: ${humanizeFeature(catalogType)}`,
        dataSource && `${text.dataSource}: ${humanizeFeature(dataSource)}`,
        qualityIssue && `${text.qualityIssue}: ${humanizeFeature(qualityIssue)}`
      ].filter(Boolean).map((item) => <span key={String(item)}>{item}</span>)}</div>}

      <CollapsiblePanel title={text.filtersTitle} description={text.filtersDescription} open={filtersOpen} onToggle={() => setFiltersOpen((current) => !current)}>
        <div className="review-filter-grid product-review-filter-grid">
          <label className="user-filter-search">
            {text.productSearch}
            <span><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={text.productSearchPlaceholder} />{query && <button type="button" aria-label={text.resetFilters} onClick={() => setQuery("")}>×</button>}</span>
            <small className="product-search-scope" aria-live="polite">{query.trim() && verificationStatus === "RAW_IMPORTED" ? (locale === "tr" ? "Arama tüm katalog durumlarında çalışır." : "Search runs across every catalog status.") : "\u00a0"}</small>
          </label>
          <label>
            {text.verification}
            <select value={verificationStatus} onChange={(event) => setVerificationStatus(event.target.value)}>
              <option value="">{text.all}</option>
              {VERIFICATION_STATUSES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
            </select>
          </label>
          <label>
            {text.imageStatus}
            <select value={imageStatus} onChange={(event) => setImageStatus(event.target.value)}>
              <option value="">{text.all}</option>
              {IMAGE_STATUSES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
            </select>
          </label>
          <label>
            {text.region}
            <select value={region} onChange={(event) => setRegion(event.target.value)}>
              <option value="">{text.all}</option>
              {MARKET_REGIONS.map((value) => <option key={value} value={value}>{value}</option>)}
            </select>
          </label>
          <label>
            {text.catalog}
            <select value={catalogType} onChange={(event) => setCatalogType(event.target.value)}>
              <option value="">{text.all}</option>
              {CATALOG_TYPES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
            </select>
          </label>
          <label>
            {text.dataSource}
            <select value={dataSource} onChange={(event) => setDataSource(event.target.value)}>
              <option value="">{text.all}</option>
              {DATA_SOURCES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
            </select>
          </label>
          <label>
            {text.qualityIssue}
            <select value={qualityIssue} onChange={(event) => setQualityIssue(event.target.value)}>
              <option value="">{text.anyIssue}</option>
              {QUALITY_ISSUES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
            </select>
          </label>
        </div>
        <details className="review-transfer-panel product-review-bulk-tools">
          <summary>{text.bulkTools}</summary>
          <div className="product-review-bulk-intro">
            <div><span>CSV / TSV</span><strong>{text.bulkWorkflow}</strong><p>{text.bulkDescription}</p></div>
            <ol aria-label={text.bulkWorkflow}>
              <li><b>1</b><span>{text.exportFilter}</span></li>
              <li><b>2</b><span>{text.dryRun}</span></li>
              <li><b>3</b><span>{text.applyImport}</span></li>
            </ol>
          </div>
          <div className="review-transfer-actions">
            <button className="ghost-button product-review-export-action" type="button" disabled={transferState === "loading"} onClick={exportCurrentFilter}>{text.exportFilter}</button>
            <label className="file-picker product-review-file-picker">
              <span>{text.correctionFile}</span>
              <input type="file" accept=".csv,.tsv,text/csv,text/tab-separated-values" onChange={(event) => setCorrectionFile(event.target.files?.[0] ?? null)} />
              <span className="product-review-file-control"><b>{text.chooseFile}</b><em>{correctionFile?.name ?? text.noFile}</em></span>
            </label>
            <label className="inline-check review-transfer-check">
              <input type="checkbox" checked={markVerifiedOnImport} onChange={(event) => setMarkVerifiedOnImport(event.target.checked)} />
              <span>{text.markVerified}</span>
            </label>
            <div className="product-review-import-actions"><button className="ghost-button" type="button" disabled={transferState === "loading" || !correctionFile} onClick={() => importNutritionCorrections(true)}>{text.dryRun}</button><button className="primary-button" type="button" disabled={transferState === "loading" || !correctionFile} onClick={() => importNutritionCorrections(false)}>{text.applyImport}</button></div>
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
        </details>
      </CollapsiblePanel>

      <section className="product-review-queue" aria-labelledby="product-review-queue-title">
        <div className="product-review-queue-heading"><div><span className="eyebrow">{text.queueEyebrow}</span><h3 id="product-review-queue-title">{text.queueTitle}</h3><p>{text.queueDescription}</p></div><strong>{formatValue(totalElements)}</strong></div>
      <DataTable
        caption={text.queueTitle}
        columns={[text.product, text.source, text.review, text.quality, text.nutrition]}
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
        empty={text.empty}
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
      </section>
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
          canManage={canManage}
          onBarcodeSaved={barcodeSaved}
        />
      )}
      {selectedProduct && rejectConfirmationOpen && (
        <ConfirmDialog
          title={text.rejectTitle}
          message={text.rejectMessage}
          confirmLabel={text.rejectAction}
          danger
          busy={saving}
          onCancel={() => setRejectConfirmationOpen(false)}
          onConfirm={() => updateReview(selectedProduct, "REJECTED")}
        />
      )}
    </div>
  );
}

export function ProductCell({ item }: { item: FoodProduct }) {
  return (
    <div className="entity-cell">
      <strong>{productName(item)}</strong>
      <small>{[item.id ? `#${item.id}` : null, item.brand ?? item.barcode ?? null].filter(Boolean).join(" | ") || "-"}</small>
    </div>
  );
}

export function ProductReviewModal({
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
  onError,
  canManage,
  onBarcodeSaved
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
  canManage: boolean;
  onBarcodeSaved: (product: FoodProduct) => Promise<void>;
}) {
  const dialogRef = useDialogAccessibility(onClose);
  const { locale } = useAdminLocale();
  const barcodeText = locale === "tr" ? {
    title: "Barkod yönetimi", current: "Mevcut barkod", next: "Yeni barkod", reason: "Değişiklik gerekçesi", placeholder: "Doğrulanmış etiket kaynağını belirtin", action: "Barkodu değiştir", invalid: "Geçerli kontrol basamaklı bir GTIN girin.", confirmTitle: "Barkod değiştirilsin mi?", confirm: "Barkodu güncelle", cancel: "Vazgeç", warning: "Eski barkod aramada tutulmaz; eski ve yeni değer audit geçmişine kaydedilir. Başka ürüne ait barkod taşınmaz."
  } : {
    title: "Barcode management", current: "Current barcode", next: "New barcode", reason: "Change reason", placeholder: "Describe the verified label source", action: "Replace barcode", invalid: "Enter a GTIN with a valid check digit.", confirmTitle: "Replace this barcode?", confirm: "Update barcode", cancel: "Cancel", warning: "The old barcode will no longer resolve in search; old and new values are retained in audit history. A barcode assigned to another product is never moved."
  };
  const currentBarcode = normalizeBarcode(item.normalizedBarcode ?? item.barcode ?? "");
  const [barcode, setBarcode] = useState(currentBarcode);
  const [barcodeReason, setBarcodeReason] = useState("");
  const [barcodeBusy, setBarcodeBusy] = useState(false);
  const [barcodeConfirmOpen, setBarcodeConfirmOpen] = useState(false);
  const [detailTab, setDetailTab] = useState<"quality" | "edit" | "barcode">("quality");
  const detailText = locale === "tr" ? {
    eyebrow: "Ürün inceleme", noIdentity: "Marka veya barkod yok", close: "Kapat", quality: "Kalite ve kanıt", edit: "Ürünü düzenle", barcode: "Barkod", snapshot: "Ürün özeti", noImage: "Görsel yok", editIntro: "Katalog bilgileri", editHint: "Temel alanları ve 100 g/ml başına besin değerlerini doğrulayın.", reviewNote: "İnceleme notu", reviewPlaceholder: "Bu karar için isteğe bağlı not", reject: "Reddet", save: "Değişiklikleri kaydet", approve: "Onayla", saving: "Kaydediliyor..."
  } : {
    eyebrow: "Product review", noIdentity: "No brand or barcode", close: "Close", quality: "Quality & evidence", edit: "Edit product", barcode: "Barcode", snapshot: "Product snapshot", noImage: "No image", editIntro: "Catalog information", editHint: "Verify core fields and nutrition values per 100g/ml.", reviewNote: "Review note", reviewPlaceholder: "Optional note for this decision", reject: "Reject", save: "Save changes", approve: "Approve", saving: "Saving..."
  };
  const barcodeValidation = validateGtin(barcode);
  const image = item.displayImageUrl ?? item.imageUrl ?? item.externalImageUrl;
  function updateDraft<K extends keyof ProductReviewDraft>(key: K, value: ProductReviewDraft[K]) {
    setDraft({ ...draft, [key]: value });
  }
  async function replaceBarcode() {
    if (!item.id || !barcodeValidation.valid || !barcodeReason.trim()) return;
    setBarcodeBusy(true);
    onError(null);
    try {
      const updated = await request<FoodProduct>(`/api/v1/admin/products/${item.id}/barcode`, { method: "PATCH", body: { barcode: barcodeValidation.barcode, reason: barcodeReason.trim() } });
      setBarcodeConfirmOpen(false);
      setBarcodeReason("");
      await onBarcodeSaved(updated);
    } catch (error) { onError(formatRequestError(error)); }
    finally { setBarcodeBusy(false); }
  }
  return <>
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <section ref={dialogRef} tabIndex={-1} className="product-modal product-review-modal" role="dialog" aria-modal="true" aria-labelledby="product-review-detail-title" onClick={(event) => event.stopPropagation()}>
        <header className="modal-header">
          <div>
            <p className="eyebrow">{detailText.eyebrow}</p>
            <h2 id="product-review-detail-title">{productName(item)}</h2>
            <span>{[item.id ? `Product ID #${item.id}` : null, item.brand ?? item.barcode ?? null].filter(Boolean).join(" | ") || detailText.noIdentity}</span>
          </div>
          <button className="icon-button" onClick={onClose} type="button" aria-label={detailText.close}>×</button>
        </header>
        <div className="modal-body product-review-layout">
          <aside className="product-review-snapshot" aria-label={detailText.snapshot}>
            <div className="product-image-frame">
              {image ? <img alt={productName(item)} src={image} /> : <span>{detailText.noImage}</span>}
            </div>
            <div className="product-review-snapshot-meta">
              <Badge value={item.verificationStatus} />
              <Badge value={item.imageStatus} tone="neutral" />
              <div><span>{locale === "tr" ? "Kalite" : "Quality"}</span><strong>{formatValue(item.qualityScore)} / 100</strong></div>
              <div><span>{locale === "tr" ? "Öncelik" : "Priority"}</span><strong>{formatValue(item.reviewPriority)}</strong></div>
            </div>
          </aside>
          <div className="product-detail-stack product-review-workspace">
            <div className="product-review-detail-tabs" role="tablist" aria-label={locale === "tr" ? "Ürün çalışma alanı" : "Product workspace"}>
              <button role="tab" aria-selected={detailTab === "quality"} className={detailTab === "quality" ? "active" : ""} onClick={() => setDetailTab("quality")} type="button">{detailText.quality}</button>
              {canManage && <button role="tab" aria-selected={detailTab === "edit"} className={detailTab === "edit" ? "active" : ""} onClick={() => setDetailTab("edit")} type="button">{detailText.edit}</button>}
              {canManage && <button role="tab" aria-selected={detailTab === "barcode"} className={detailTab === "barcode" ? "active" : ""} onClick={() => setDetailTab("barcode")} type="button">{detailText.barcode}</button>}
            </div>
            {detailTab === "quality" && <ProductQualityWorkbench product={item} onError={onError} />}
            {detailTab === "barcode" && canManage && <section className="product-barcode-editor" aria-label={barcodeText.title}>
              <div><span>{barcodeText.title}</span><strong>{barcodeText.current}: {currentBarcode || "-"}</strong><small>{barcodeText.warning}</small></div>
              <label>{barcodeText.next}<input inputMode="numeric" maxLength={14} value={barcode} onChange={event => setBarcode(normalizeBarcode(event.target.value))} /></label>
              <label>{barcodeText.reason}<input maxLength={500} value={barcodeReason} placeholder={barcodeText.placeholder} onChange={event => setBarcodeReason(event.target.value)} /></label>
              {barcode && !barcodeValidation.valid && <small className="danger-text">{barcodeText.invalid}</small>}
              <button className="ghost-button" type="button" disabled={barcodeBusy || !barcodeValidation.valid || barcodeValidation.barcode === currentBarcode || !barcodeReason.trim()} onClick={() => setBarcodeConfirmOpen(true)}>{barcodeText.action}</button>
            </section>}
            {detailTab === "edit" && canManage && <div className="product-review-edit-pane">
            <div className="product-review-pane-heading"><span className="eyebrow">{detailText.editIntro}</span><p>{detailText.editHint}</p></div>
            <div className="detail-grid editable">
              <DetailItem label="Product ID" value={formatValue(item.id)} />
              <DetailItem label="Publication status" value={item.publicationStatus ?? "-"} />
              <EditableDetail label="Product name">
                <input value={draft.productName} onChange={(event) => updateDraft("productName", event.target.value)} />
              </EditableDetail>
              <EditableDetail label="Display image URL">
                <input value={draft.displayImageUrl} onChange={(event) => updateDraft("displayImageUrl", event.target.value)} placeholder="https://..." />
              </EditableDetail>
              <EditableDetail label="Region">
                <select value={draft.marketRegion} onChange={(event) => updateDraft("marketRegion", event.target.value)}>
                  <option value="">{locale === "tr" ? "Mevcut değeri koru" : "Keep current"}</option>
                  {MARKET_REGIONS.map((value) => <option key={value} value={value}>{value}</option>)}
                </select>
              </EditableDetail>
              <EditableDetail label="Catalog">
                <select value={draft.catalogType} onChange={(event) => updateDraft("catalogType", event.target.value)}>
                  <option value="">{locale === "tr" ? "Mevcut değeri koru" : "Keep current"}</option>
                  {CATALOG_TYPES.map((value) => <option key={value} value={value}>{value}</option>)}
                </select>
              </EditableDetail>
              <DetailItem label="Data source" value={item.dataSource} />
              <EditableDetail label="Verification">
                <select value={draft.verificationStatus} onChange={(event) => updateDraft("verificationStatus", event.target.value)}>
                  <option value="">{locale === "tr" ? "Mevcut değeri koru" : "Keep current"}</option>
                  {VERIFICATION_STATUSES.map((value) => <option key={value} value={value}>{value}</option>)}
                </select>
              </EditableDetail>
              <EditableDetail label="Image status">
                <select value={draft.imageStatus} onChange={(event) => updateDraft("imageStatus", event.target.value)}>
                  <option value="">{locale === "tr" ? "Mevcut değeri koru" : "Keep current"}</option>
                  {IMAGE_STATUSES.map((value) => <option key={value} value={value}>{value}</option>)}
                </select>
              </EditableDetail>
              <EditableDetail label="Image source">
                <select value={draft.imageSource} onChange={(event) => updateDraft("imageSource", event.target.value)}>
                  <option value="">{locale === "tr" ? "Mevcut değeri koru" : "Keep current"}</option>
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
                  <span>{locale === "tr" ? "Besin değerleri" : "Nutrition values"}</span>
                  <strong>{locale === "tr" ? "100 g/ml başına düzenlenebilir inceleme verisi" : "Editable per 100g/ml review data"}</strong>
                </div>
                <small>{locale === "tr" ? "Kaydedilen değerler katalog ürününü doğrudan günceller." : "Saved values update the catalog product directly."}</small>
              </div>
              <NutritionInputGrid title="Macros" fields={PRODUCT_MACRO_FIELDS} draft={draft} onChange={updateDraft} />
              <NutritionInputGrid title="Minerals" fields={PRODUCT_MINERAL_FIELDS} draft={draft} onChange={updateDraft} />
              <NutritionInputGrid title="Vitamins" fields={PRODUCT_VITAMIN_FIELDS} draft={draft} onChange={updateDraft} />
              <div className="nutrition-section">
                <div className="nutrition-section-header">
                  <h3>{locale === "tr" ? "Porsiyon" : "Serving"}</h3>
                  <span>{locale === "tr" ? "İsteğe bağlı gösterim bilgisi" : "Optional display metadata"}</span>
                </div>
                <div className="nutrition-input-grid serving-edit-grid">
                  <label className="nutrition-input">
                    <span>{locale === "tr" ? "Porsiyon miktarı" : "Serving size"}</span>
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
                    <span>{locale === "tr" ? "Porsiyon birimi" : "Serving unit"}</span>
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
              {detailText.reviewNote}
              <textarea value={reviewNote} onChange={(event) => setReviewNote(event.target.value)} placeholder={detailText.reviewPlaceholder} />
            </label>
            <div className="modal-actions">
              <button className="ghost-button danger-action" disabled={saving} onClick={onReject} type="button">{detailText.reject}</button>
              <button className="ghost-button" disabled={saving} onClick={onSave} type="button">{detailText.save}</button>
              <button className="primary-button" disabled={saving} onClick={onApprove} type="button">{saving ? detailText.saving : detailText.approve}</button>
            </div>
            </div>}
          </div>
        </div>
      </section>
    </div>
    {barcodeConfirmOpen && <ConfirmDialog title={barcodeText.confirmTitle} message={`${currentBarcode || "-"} → ${barcodeValidation.barcode}. ${barcodeText.warning}`} confirmLabel={barcodeText.confirm} cancelLabel={barcodeText.cancel} busy={barcodeBusy} onCancel={() => setBarcodeConfirmOpen(false)} onConfirm={() => void replaceBarcode()} />}
  </>;
}

export type ProductWorkbenchTab = "overview" | "nutrition" | "names" | "aliases" | "serving" | "evidence" | "ai" | "audit";

export const HIGH_IMPACT_PRODUCT_FIELDS = new Set([
  "calories", "protein", "fat", "carbs", "fiber", "sugar", "sodium", "potassium", "cholesterol",
  "calcium", "iron", "magnesium", "zinc", "vitaminA", "vitaminC", "vitaminD", "vitaminE", "vitaminB12",
  "saturatedFat", "transFat", "sugarAlcohol"
]);

export function ProductQualityWorkbench({ product, onError }: { product: FoodProduct; onError: (message: string | null) => void }) {
  const { locale } = useAdminLocale();
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

  return <section className="product-quality-workbench" aria-label={locale === "tr" ? "Yapay zekâ ürün kalite çalışma alanı" : "AI product quality workbench"}>
    <div className="product-workbench-toolbar">
      <div>
        <span className="eyebrow">{locale === "tr" ? "YAPAY ZEKÂ KALİTE ÇALIŞMA ALANI" : "AI QUALITY WORKBENCH"}</span>
        <strong>{formatValue(openSuggestions.length)} {locale === "tr" ? "açık öneri" : "open recommendation(s)"}</strong>
      </div>
      <div className="product-workbench-actions">
        <button className="ghost-button" type="button" disabled={state === "loading"} onClick={loadWorkbench}>{locale === "tr" ? "Yenile" : "Refresh"}</button>
        <button className="primary-button" type="button" disabled={state === "loading" || !product.id} onClick={analyzeWithAi}>{locale === "tr" ? "Ürünü yapay zekâ ile analiz et" : "Analyze product with AI"}</button>
      </div>
    </div>
    <div className="product-workbench-tabs" role="tablist">
      {(["overview", "nutrition", "names", "aliases", "serving", "evidence", "ai", "audit"] as ProductWorkbenchTab[]).map((value) =>
        <button key={value} type="button" role="tab" aria-selected={tab === value} className={tab === value ? "active" : ""} onClick={() => setTab(value)}>{locale === "tr" ? ({ overview:"Genel bakış", nutrition:"Besin değerleri", names:"Adlar", aliases:"Arama adları", serving:"Porsiyon", evidence:"Kanıt", ai:"Yapay zekâ", audit:"Denetim" } as Record<string,string>)[value] : humanizeFeature(value)}</button>
      )}
    </div>

    {state === "loading" && !data && <div className="empty-state">{locale === "tr" ? "Ürün kalite bilgileri yükleniyor…" : "Loading product quality context..."}</div>}
    {tab === "overview" && data && <div className="workbench-pane">
      <div className="metric-grid compact-grid">
        <MetricCard label={locale === "tr" ? "Kalite" : "Quality"} value={`${formatValue(data.product?.qualityScore)} / 100`} hint={`${locale === "tr" ? "Güven" : "Confidence"} ${formatValue(data.product?.confidenceScore)}`} />
        <MetricCard label={locale === "tr" ? "Aktif sorun" : "Active issues"} value={formatValue((data.qualityIssues ?? []).filter((item) => !item.resolved).length)} hint={locale === "tr" ? "Kural tabanlı kalite kontrolleri" : "Deterministic quality rules"} />
        <MetricCard label={locale === "tr" ? "Kanıt satırı" : "Evidence rows"} value={formatValue(evidence.length)} hint={`${formatValue(comparisons.length)} ${locale === "tr" ? "alan karşılaştırması" : "field comparisons"}`} />
        <MetricCard label={locale === "tr" ? "Yapay zekâ incelemesi" : "AI review"} value={formatValue(openSuggestions.length)} hint={locale === "tr" ? "Yönetici kararı bekliyor" : "Pending admin decisions"} />
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

export function WorkbenchSuggestionTable({ suggestions, comparisons, onReview, showReviewed = false }: { suggestions: ProductQualitySuggestion[]; comparisons: NonNullable<AdminProductQualityWorkbench["evidence"]>["comparisons"]; onReview: (item: ProductQualitySuggestion, action: "accept" | "reject") => void; showReviewed?: boolean }) {
  const { locale } = useAdminLocale();
  return <DataTable columns={locale === "tr" ? ["Öneri", "Mevcut", "Sağlayıcı kanıtı", "Yapay zekâ önerisi", "Güven", "Gerekçe", "İşlemler"] : ["Recommendation", "Current", "Provider evidence", "AI proposal", "Confidence", "Reason", "Actions"]} rows={suggestions.map((item) => {
    const comparison = (comparisons ?? []).find((value) => value.fieldName === evidenceFieldForSuggestion(item.fieldName));
    return [<div className="entity-cell"><strong>{humanizeFeature(item.suggestionType)}</strong><small>{item.fieldName ?? "-"}</small></div>, item.currentValue ?? "-", <div className="badge-stack"><Badge value={comparison?.state ?? "NO_EVIDENCE"} tone={comparison?.state === "MATCH" ? "good" : comparison?.state === "CONFLICT" ? "danger" : "warn"} /><small>{comparison?.reason ?? (locale === "tr" ? "Karşılaştırılabilir sağlayıcı kanıtı yok." : "No comparable provider evidence.")}</small></div>, item.suggestedValue ?? (locale === "tr" ? "Yalnızca incele" : "Review only"), `${formatValue(item.confidenceScore)}%`, item.reason ?? "-", item.status === "OPEN" ? <div className="inline-actions"><button className="ghost-button" type="button" disabled={!canApplyWorkbenchSuggestion(item)} onClick={() => onReview(item, "accept")}>{canApplyWorkbenchSuggestion(item) ? (locale === "tr" ? "Kabul et" : "Accept") : (locale === "tr" ? "Yalnızca incele" : "Review only")}</button><button className="ghost-button danger-text" type="button" onClick={() => onReview(item, "reject")}>{locale === "tr" ? "Reddet" : "Reject"}</button></div> : showReviewed ? <Badge value={item.status ?? "-"} tone={item.status === "ACCEPTED" ? "good" : "neutral"} /> : "-"];
  })} empty={locale === "tr" ? "Bu görünüm için öneri bulunmuyor." : "No recommendations for this view."} />;
}

export const SAFE_NUTRITION_SUGGESTION_TYPES = new Set([
  "MISSING_MACRO_DATA", "MISSING_MICRO_DATA", "SUSPICIOUS_CALORIE_VALUE",
  "MACRO_CALORIE_MISMATCH", "SUSPICIOUS_SODIUM_VALUE"
]);

export const SAFE_NUTRITION_PRODUCT_FIELDS = new Set([
  "calories", "protein", "fat", "carbs", "fiber", "sugar", "sodium", "potassium", "cholesterol",
  "calcium", "iron", "magnesium", "zinc", "vitaminA", "vitaminC", "vitaminD", "vitaminE", "vitaminB12",
  "saturatedFat", "transFat", "sugarAlcohol"
]);

export const SAFE_SERVING_PRODUCT_FIELDS = new Set(["servingSizeGrams", "servingUnit"]);

export function canApplyWorkbenchSuggestion(item: ProductQualitySuggestion) {
  if (!item.suggestedValue) return false;
  const suggestionType = item.suggestionType ?? "";
  if (["NAME_CLEANUP", "DISPLAY_NAME"].includes(suggestionType)) return true;
  if (suggestionType === "LOCALIZATION") {
    return /^localizations\.(EN|TR)\.(displayName|shortDisplayName)$/.test(item.fieldName ?? "");
  }
  if (suggestionType === "SEARCH_ALIAS") {
    return /^searchAliases\.(EN|TR)$/.test(item.fieldName ?? "");
  }
  if (SAFE_NUTRITION_SUGGESTION_TYPES.has(suggestionType)) {
    return SAFE_NUTRITION_PRODUCT_FIELDS.has(item.fieldName ?? "");
  }
  return suggestionType === "MISSING_SERVING_SIZE"
    && SAFE_SERVING_PRODUCT_FIELDS.has(item.fieldName ?? "");
}

export function isHighImpactProductSuggestion(item: ProductQualitySuggestion) {
  return HIGH_IMPACT_PRODUCT_FIELDS.has(item.fieldName ?? "");
}

export function evidenceFieldForSuggestion(fieldName?: string) {
  return fieldName?.replace(/([a-z])([A-Z])/g, "$1_$2").toUpperCase();
}

export function ProductAliasManager({ productId, onError }: { productId?: number; onError: (message: string | null) => void }) {
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

export function NutritionInputGrid({
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

export function MetricPill({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric-pill">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

export function buildProductReviewPath(filters: {
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
    if (key === "verificationStatus" && filters.query.trim() && value === "RAW_IMPORTED") return;
    if (key !== "page" && key !== "size" && value) params.set(key, String(value));
  });
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));
  return `/api/v1/admin/products/review?${params.toString()}`;
}

export function buildProductReviewExportPath(filters: {
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
    if (key === "verificationStatus" && filters.query.trim() && value === "RAW_IMPORTED") return;
    if (value) params.set(key, String(value));
  });
  return `/api/v1/admin/products/review/export?${params.toString()}`;
}

export function toProductReviewDraft(item: FoodProduct): ProductReviewDraft {
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

export function productReviewNutritionPayload(draft: ProductReviewDraft) {
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

export function numberInputValue(value?: number | null): string {
  return typeof value === "number" && Number.isFinite(value) ? String(value) : "";
}

export function parseOptionalNumber(value: string): number | null {
  const trimmed = value.trim();
  if (!trimmed) return null;
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : null;
}
