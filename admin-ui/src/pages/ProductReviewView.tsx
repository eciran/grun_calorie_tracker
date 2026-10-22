import { useEffect, useMemo, useRef, useState } from "react";

import { formatRequestError, PageResponse, request, requestBlob, requestFormData } from "../api";

import { AdminFoodServingOptionUpsert, AdminProductQualityWorkbench, AdminProductQualityAiValidationResult, FoodProduct, FoodSearchAlias, ProductQualitySuggestion } from "../types";

import { CollapsiblePanel, DataTable, LoadState, MetricCard, PaginationControls, Panel, SectionToolbar, useDialogAccessibility } from "../AdminPrimitives";

import { Badge, ConfirmDialog, DetailItem, EditableDetail, IMAGE_SOURCES, IMAGE_STATUSES, MARKET_REGIONS, PREFERRED_LANGUAGES, VERIFICATION_STATUSES, downloadBlob, formatDate, formatValue, humanizeFeature, productName, useEndpoint } from "./../admin/shared";
import { normalizeBarcode, validateGtin } from "../admin/barcode";
import { useAdminLocale } from "../admin/locale";
import { ProductQualityWorkloadChart, ProductReviewHealthChart } from "../CatalogWorkspaceCharts";

export const CATALOG_TYPES = ["BRANDED_PRODUCT", "GENERIC_INGREDIENT", "LOCAL_DISH", "USER_CUSTOM"];
export const PREPARATION_STATES = ["UNSPECIFIED", "RAW", "COOKED", "BOILED", "GRILLED", "FRIED", "BAKED", "ROASTED", "STEAMED", "PREPARED"];

export const DATA_SOURCES = ["OPEN_FOOD_FACTS", "ADMIN_IMPORT", "USDA_FOODDATA", "USER_CUSTOM"];
export const PUBLICATION_STATUSES = ["INTERNAL_REVIEW", "PUBLISHED", "HIDDEN", "PRIVATE_USER"];

export const FOOD_SEARCH_ALIAS_TYPES = ["ADMIN_MANUAL", "TRANSLATION", "SYNONYM", "ASCII_NORMALIZED", "COMMON_NAME"];
export const FOOD_SERVING_OPTION_UNITS = ["SERVING", "PIECE", "SLICE", "SCOOP", "CUP", "TABLESPOON", "TEASPOON", "CAN", "BOTTLE", "GLASS", "MUG", "SHOT", "PACKAGE", "BAR", "BOWL", "PLATE", "PORTION"];

export const QUALITY_ISSUES = [
  "MISSING_CANONICAL_CATEGORY",
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

const QUALITY_ISSUE_GROUPS = [
  { key: "category", tr: "Kategori ve sınıflandırma", en: "Category and classification", values: ["MISSING_CANONICAL_CATEGORY"] },
  { key: "identity", tr: "Kimlik ve katalog", en: "Identity and catalog", values: ["LOW_QUALITY", "MISSING_BARCODE", "INVALID_BARCODE_FORMAT"] },
  { key: "nutrition", tr: "Besin değerleri ve porsiyon", en: "Nutrition and serving", values: ["MISSING_CALORIES", "SUSPICIOUS_CALORIES", "MISSING_MACROS", "SUSPICIOUS_MACROS", "MISSING_MICRONUTRIENTS", "MISSING_NUTRIENT_QUALITY_FIELDS", "SUSPICIOUS_NUTRIENT_QUALITY", "MISSING_SERVING_SIZE"] },
  { key: "media", tr: "Görsel", en: "Media", values: ["MISSING_IMAGE"] },
  { key: "market", tr: "Pazar ve bölge", en: "Market and region", values: ["MISSING_REGION", "UNSUPPORTED_REGION"] }
] as const;

export type ProductReviewDraft = {
  productName: string;
  brand: string;
  displayImageUrl: string;
  marketRegion: string;
  verificationStatus: string;
  imageStatus: string;
  imageSource: string;
  catalogType: string;
  preparationState: string;
  nutritionBasis: string;
  nutritionReferenceUnit: string;
  ingredientsText: string;
  allergens: string;
  sourceCategoryTags: string;
  adminSourceName: string;
  adminSourceUrl: string;
  adminCreationNote: string;
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
  publicationStatus: string;
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
    publicationStatus: "",
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
    publicationStatus: enumValue("publication", PUBLICATION_STATUSES, defaults.publicationStatus),
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
  if (state.publicationStatus) params.set("publication", state.publicationStatus);
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

type CatalogImportIssue = { rowNumber?: number; identifier?: string | null; reason?: string; code?: string };
type CatalogImportResult = {
  totalRows?: number;
  insertedRows?: number;
  updatedRows?: number;
  skippedRows?: number;
  savedRows?: number;
  duplicateInputRows?: number;
  reviewRequiredRows?: number;
  importQualityScore?: number;
  errors?: CatalogImportIssue[];
  warnings?: CatalogImportIssue[];
};

type CorrectionImportNotice = { tone: "success" | "warning" | "info"; message: string };

const productReviewMessages = {
  tr: {
    modeTitle: { queue: "Ürün inceleme kuyruğu", images: "Ürün görseli inceleme", nutrition: "Besin değeri inceleme", rejected: "Reddedilen ürünler" },
    modeDescription: { queue: "Öncelikli ürünleri bulun, kanıtları kontrol edin ve katalog kararını tamamlayın.", images: "Eksik veya inceleme bekleyen ürün görsellerini doğrulayın.", nutrition: "Şüpheli besin değerlerini kaynak kanıtlarıyla karşılaştırın.", rejected: "Reddedilen ürünleri ve karar gerekçelerini yeniden değerlendirin." },
    resetFilters: "Filtreleri sıfırla", returnedProducts: "Eşleşen ürün", returnedProductsHint: "Geçerli filtre sonucundaki toplam", highPriority: "Yüksek öncelik", highPriorityHint: "Öncelik puanı 100 ve üzeri", missingImages: "Görseli eksik", missingImagesHint: "Kullanılabilir görseli olmayan satırlar", activeFilters: "Etkin filtre", activeFiltersHint: "Kuyruğa uygulanan ölçütler",
    filtersTitle: "Kuyruğu daralt", filtersDescription: "İncelenecek ürünleri görev, kaynak ve kalite sorununa göre odaklayın.", productSearch: "Ürün ara", productSearchPlaceholder: "Ad, marka veya barkod", verification: "Doğrulama", publication: "Yayın durumu", imageStatus: "Görsel durumu", region: "Bölge", catalog: "Katalog", dataSource: "Veri kaynağı", qualityIssue: "Kalite sorunu", all: "Tümü", anyIssue: "Tüm sorunlar",
    categoryQueue: "Kategori kararları", categoryQueueHint: "Birincil kategorisi bulunmayan ürünler", openQueue: "Kuyruğu aç",
    bulkTools: "CSV işlemleri", bulkWorkflow: "Mevcut ürün düzeltme akışı", bulkDescription: "Bu bölüm yalnızca katalogda bulunan ürünleri günceller. Yeni ürün eklemek için aşağıdaki katalog içe aktarma alanını kullanın.", catalogImport: "Yeni ürün kataloğu içe aktar", catalogImportDescription: "GRUN_STANDARD CSV dosyasını önce geri alınabilir prova ile doğrulayın. Uygulanan kayıtlar RAW_EXTERNAL olarak inceleme kuyruğuna eklenir.", catalogFile: "Yeni katalog dosyası", previewRequired: "Gerçek içe aktarmadan önce hatasız prova zorunludur.", exportFilter: "Filtreyi dışa aktar", correctionFile: "Mevcut ürün düzeltme dosyası", chooseFile: "Dosya seç", noFile: "Henüz dosya seçilmedi", markVerified: "Aktarılan satırları doğrulandı olarak işaretle", dryRun: "Prova çalıştır", applyImport: "İçe aktarmayı uygula", importMode: "İşlem", totalRows: "Toplam satır", matchedRows: "Eşleşen satır", updatedRows: "Güncellenen satır", skippedRows: "Atlanan satır",
    queueEyebrow: "ÇALIŞMA KUYRUĞU", queueTitle: "İncelenecek ürünler", queueDescription: "Bir satır seçerek kanıt, kalite önerileri ve düzenleme alanlarını açın.", product: "Ürün", source: "Kaynak", review: "İnceleme", publicationColumn: "Yayın", quality: "Kalite", nutrition: "Besin değerleri", empty: "Bu filtre için incelenecek ürün bulunamadı.", rejectTitle: "Ürün reddedilsin mi?", rejectMessage: "Ürün ve görsel reddedildi olarak işaretlenecek; karar denetim geçmişine kaydedilecek.", rejectAction: "Ürünü reddet"
  },
  en: {
    modeTitle: { queue: "Product review queue", images: "Product image review", nutrition: "Nutrition review", rejected: "Rejected products" },
    modeDescription: { queue: "Find priority products, verify evidence, and complete the catalog decision.", images: "Validate missing product images and images waiting for review.", nutrition: "Compare suspicious nutrition values against source evidence.", rejected: "Reassess rejected products and their decision history." },
    resetFilters: "Reset filters", returnedProducts: "Matching products", returnedProductsHint: "Total in the current filter", highPriority: "High priority", highPriorityHint: "Priority score 100 or above", missingImages: "Missing images", missingImagesHint: "Rows without a usable image", activeFilters: "Active filters", activeFiltersHint: "Criteria applied to the queue",
    filtersTitle: "Refine the queue", filtersDescription: "Focus the workload by task, source, and quality issue.", productSearch: "Product search", productSearchPlaceholder: "Name, brand, or barcode", verification: "Verification", publication: "Publication", imageStatus: "Image status", region: "Region", catalog: "Catalog", dataSource: "Data source", qualityIssue: "Quality issue", all: "All", anyIssue: "Any issue",
    categoryQueue: "Category decisions", categoryQueueHint: "Products without a primary category", openQueue: "Open queue",
    bulkTools: "CSV operations", bulkWorkflow: "Existing product correction workflow", bulkDescription: "This section only updates products already in the catalog. Use the catalog import area below to add new products.", catalogImport: "Import new product catalog", catalogImportDescription: "Validate the GRUN_STANDARD CSV with a rollback-only dry run first. Applied rows enter the review queue as RAW_EXTERNAL.", catalogFile: "New catalog file", previewRequired: "A clean dry run is required before the real import.", exportFilter: "Export current filter", correctionFile: "Existing product correction file", chooseFile: "Choose file", noFile: "No file selected", markVerified: "Mark imported rows verified", dryRun: "Run dry run", applyImport: "Apply import", importMode: "Operation", totalRows: "Total rows", matchedRows: "Matched rows", updatedRows: "Updated rows", skippedRows: "Skipped rows",
    queueEyebrow: "WORK QUEUE", queueTitle: "Products to review", queueDescription: "Select a row to open evidence, quality recommendations, and editing fields.", product: "Product", source: "Source", review: "Review", publicationColumn: "Publication", quality: "Quality", nutrition: "Nutrition", empty: "No products need review for this filter.", rejectTitle: "Reject product?", rejectMessage: "The product and its image will be marked rejected and the decision will be recorded in audit history.", rejectAction: "Reject product"
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
  const [publicationStatus, setPublicationStatus] = useState(initialRouteState.publicationStatus);
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
  const [correctionNotice, setCorrectionNotice] = useState<CorrectionImportNotice | null>(null);
  const [markVerifiedOnImport, setMarkVerifiedOnImport] = useState(false);
  const [transferState, setTransferState] = useState<LoadState>("idle");
  const [catalogFile, setCatalogFile] = useState<File | null>(null);
  const [catalogResult, setCatalogResult] = useState<CatalogImportResult | null>(null);
  const [catalogNotice, setCatalogNotice] = useState<CorrectionImportNotice | null>(null);
  const [catalogTransferState, setCatalogTransferState] = useState<LoadState>("idle");
  const [catalogPreviewPassed, setCatalogPreviewPassed] = useState(false);
  const [selectedPublishIds, setSelectedPublishIds] = useState<Set<number>>(new Set());
  const [batchPublishReason, setBatchPublishReason] = useState("");
  const [batchPublishing, setBatchPublishing] = useState(false);
  const path = buildProductReviewPath({
    query,
    verificationStatus,
    imageStatus,
    region,
    catalogType,
    dataSource,
    qualityIssue,
    publicationStatus,
    page,
    size: pageSize
  });
  const { data, state, reload } = useEndpoint<PageResponse<FoodProduct>>(path, onError);
  const { data: imageWorkload } = useEndpoint<PageResponse<FoodProduct>>("/api/v1/admin/products/review?imageStatus=NEEDS_REVIEW&qualityIssue=MISSING_IMAGE&page=0&size=1", onError);
  const { data: nutritionWorkload } = useEndpoint<PageResponse<FoodProduct>>("/api/v1/admin/products/review?qualityIssue=SUSPICIOUS_MACROS&page=0&size=1", onError);
  const { data: categoryWorkload } = useEndpoint<PageResponse<FoodProduct>>("/api/v1/admin/products/review?qualityIssue=MISSING_CANONICAL_CATEGORY&page=0&size=1", onError);
  const { data: rejectedWorkload } = useEndpoint<PageResponse<FoodProduct>>("/api/v1/admin/products/review?verificationStatus=REJECTED&page=0&size=1", onError);
  const rows = data?.content ?? [];
  const publishableRows = rows.filter((item) => item.id && item.verificationStatus === "VERIFIED" && item.publicationStatus !== "PUBLISHED");
  const totalElements = data?.totalElements ?? rows.length;
  const highPriorityCount = rows.filter((item) => (item.reviewPriority ?? 0) >= 100).length;
  const missingImageCount = rows.filter((item) => !item.displayImageUrl && !item.imageUrl && !item.externalImageUrl).length;
  const averageQuality = rows.length ? Math.round(rows.reduce((sum, item) => sum + Number(item.qualityScore ?? 0), 0) / rows.length) : 0;
  const activeFilterCount = [query, verificationStatus, imageStatus, region, catalogType, dataSource, qualityIssue, publicationStatus].filter(Boolean).length;
  const qualityWorkspace = mode !== "queue";
  const modeTitle = qualityWorkspace ? (locale === "tr" ? "Ürün kalite merkezi" : "Product quality center") : text.modeTitle[viewMode];

  useEffect(() => {
    if (skipInitialFilterReset.current) {
      skipInitialFilterReset.current = false;
      return;
    }
    setPage(0);
  }, [query, verificationStatus, imageStatus, region, catalogType, dataSource, qualityIssue, publicationStatus, pageSize]);

  useEffect(() => {
    const next = `${window.location.pathname}${productReviewRouteSearch({ query, verificationStatus, imageStatus, region, catalogType, dataSource, qualityIssue, publicationStatus, page, size: pageSize, selectedProductId }, viewMode)}`;
    if (`${window.location.pathname}${window.location.search}` !== next) window.history.replaceState(null, "", next);
  }, [catalogType, dataSource, imageStatus, page, pageSize, publicationStatus, qualityIssue, query, region, selectedProductId, verificationStatus, viewMode]);

  useEffect(() => { setSelectedPublishIds(new Set()); }, [data]);

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
    setPublicationStatus("");
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

  function openCategoryQueue() {
    setActiveMode("queue");
    setQuery("");
    setVerificationStatus("");
    setImageStatus("");
    setRegion("");
    setCatalogType("");
    setDataSource("");
    setPublicationStatus("");
    setQualityIssue("MISSING_CANONICAL_CATEGORY");
    setPage(0);
    setSelectedProductId(undefined);
    closeProductModal();
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
          brand: draft.brand,
          displayImageUrl: draft.displayImageUrl || null,
          marketRegion: draft.marketRegion || null,
          verificationStatus: draft.verificationStatus || null,
          imageStatus: draft.imageStatus || null,
          imageSource: draft.imageSource || null,
          catalogType: draft.catalogType || null,
          preparationState: draft.preparationState || null,
          nutritionBasis: draft.nutritionBasis || null,
          nutritionReferenceUnit: draft.nutritionReferenceUnit || null,
          ingredientsText: draft.ingredientsText,
          allergens: draft.allergens,
          sourceCategoryTags: draft.sourceCategoryTags.split(",").map((value) => value.trim()).filter(Boolean),
          adminSourceName: draft.adminSourceName,
          adminSourceUrl: draft.adminSourceUrl,
          adminCreationNote: draft.adminCreationNote,
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
          brand: draft.brand,
          displayImageUrl: draft.displayImageUrl || item.displayImageUrl || item.imageUrl || item.externalImageUrl,
          marketRegion: draft.marketRegion || item.marketRegion,
          imageSource: draft.imageSource || item.imageSource,
          catalogType: draft.catalogType || item.catalogType,
          preparationState: draft.preparationState || item.preparationState,
          nutritionBasis: draft.nutritionBasis || item.nutritionBasis,
          nutritionReferenceUnit: draft.nutritionReferenceUnit || item.nutritionReferenceUnit,
          ingredientsText: draft.ingredientsText,
          allergens: draft.allergens,
          sourceCategoryTags: draft.sourceCategoryTags.split(",").map((value) => value.trim()).filter(Boolean),
          adminSourceName: draft.adminSourceName,
          adminSourceUrl: draft.adminSourceUrl,
          adminCreationNote: draft.adminCreationNote,
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

  async function publishSelectedProducts() {
    const ids = [...selectedPublishIds];
    if (!ids.length || !batchPublishReason.trim()) return;
    setBatchPublishing(true); onError(null);
    try {
      await request<FoodProduct[]>("/api/v1/admin/products/publish-batch", {
        method: "POST", body: { productIds: ids, reason: batchPublishReason.trim() }
      });
      setSelectedPublishIds(new Set()); setBatchPublishReason("");
      setSavedNotice(locale === "tr" ? `${ids.length} ürün mobil kataloğa yayınlandı.` : `${ids.length} products published to the mobile catalog.`);
      await reload();
    } catch (error) { onError(formatRequestError(error)); }
    finally { setBatchPublishing(false); }
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
        publicationStatus,
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
    setCorrectionNotice(null);
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
      const updated = result.updatedRows ?? 0;
      const skipped = result.skippedRows ?? 0;
      const errors = result.errors?.length ?? 0;
      if (dryRun) {
        setCorrectionNotice({
          tone: errors > 0 ? "warning" : "info",
          message: locale === "tr"
            ? `Prova tamamlandı. ${result.totalRows ?? 0} satır kontrol edildi; ${result.candidateRows ?? updated} satır eşleşti, ${skipped} satır atlanacak.${errors ? ` ${errors} hata bulundu.` : " Dosyada henüz değişiklik uygulanmadı."}`
            : `Dry run completed. ${result.totalRows ?? 0} rows checked; ${result.candidateRows ?? updated} matched and ${skipped} will be skipped.${errors ? ` ${errors} errors found.` : " No data has been changed yet."}`
        });
      } else {
        setCorrectionNotice({
          tone: errors > 0 || updated === 0 ? "warning" : "success",
          message: locale === "tr"
            ? (updated > 0
              ? `İçe aktarma tamamlandı. ${updated} satırdaki ürün verileri güncellendi, ${skipped} satır atlandı.${errors ? ` ${errors} satır hatası ayrıntılarda gösteriliyor.` : " Güncel veriler listeye yeniden yüklendi."}`
              : `İçe aktarma tamamlandı ancak hiçbir ürün verisi güncellenmedi. ${skipped} satır atlandı${errors ? ` ve ${errors} hata bulundu.` : "."}`)
            : (updated > 0
              ? `Import completed. Product data in ${updated} rows was updated and ${skipped} rows were skipped.${errors ? ` ${errors} row errors are shown below.` : " The list has been reloaded with current data."}`
              : `Import completed, but no product data was updated. ${skipped} rows were skipped${errors ? ` and ${errors} errors were found.` : "."}`)
        });
      }
      if (!dryRun) {
        await reload();
      }
    } catch (err) {
      setTransferState("error");
      setCorrectionNotice(null);
      onError(formatRequestError(err));
    }
  }

  async function importNewCatalog(dryRun: boolean) {
    if (!catalogFile) {
      onError(locale === "tr" ? "Yeni katalog CSV dosyası zorunludur." : "New catalog CSV file is required.");
      return;
    }
    setCatalogTransferState("loading");
    setCatalogNotice(null);
    onError(null);
    try {
      const formData = new FormData();
      formData.append("file", catalogFile);
      const result = await requestFormData<CatalogImportResult>(
        `/api/v1/admin/products/import?importMode=RAW_EXTERNAL&importFormat=GRUN_STANDARD&dryRun=${dryRun}`,
        formData,
        { timeoutMs: 180000 }
      );
      setCatalogResult(result);
      setCatalogTransferState("ready");
      const errors = result.errors?.length ?? 0;
      const skipped = result.skippedRows ?? 0;
      const complete = errors === 0 && skipped === 0 && (result.savedRows ?? 0) === (result.totalRows ?? 0);
      setCatalogPreviewPassed(dryRun && complete);
      setCatalogNotice({
        tone: complete ? "success" : "warning",
        message: locale === "tr"
          ? `${dryRun ? "Prova" : "İçe aktarma"} tamamlandı. ${result.totalRows ?? 0} satır kontrol edildi; ${result.insertedRows ?? 0} yeni, ${result.updatedRows ?? 0} güncelleme, ${skipped} atlanan satır.${dryRun ? " Prova değişiklikleri geri alındı." : " Kayıtlar inceleme kuyruğuna gönderildi."}`
          : `${dryRun ? "Dry run" : "Import"} completed. ${result.totalRows ?? 0} rows checked; ${result.insertedRows ?? 0} new, ${result.updatedRows ?? 0} updates, ${skipped} skipped.${dryRun ? " Dry-run changes were rolled back." : " Records were sent to the review queue."}`
      });
      if (!dryRun) await reload();
    } catch (err) {
      setCatalogTransferState("error");
      setCatalogPreviewPassed(false);
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
          <article className="category"><span>{text.categoryQueue}</span><strong>{formatValue(categoryWorkload?.totalElements ?? 0)}</strong><small>{text.categoryQueueHint}</small><button className="text-action" type="button" onClick={openCategoryQueue}>{text.openQueue}</button></article>
          <article className="accent"><span>{locale === "tr" ? "Ortalama kalite" : "Average quality"}</span><strong>{averageQuality}</strong><small>/ 100</small></article>
          <article className="warning"><span>{text.highPriority}</span><strong>{formatValue(highPriorityCount)}</strong><small>{text.highPriorityHint}</small></article>
          <article className="danger"><span>{text.missingImages}</span><strong>{formatValue(missingImageCount)}</strong><small>{text.missingImagesHint}</small></article>
        </div>
      </section>}
      {qualityWorkspace && <div className="product-quality-overview">
        <Panel title={locale === "tr" ? "İnceleme iş yükü" : "Review workload"} description={locale === "tr" ? "Dört ürün karar kuyruğunun güncel dağılımı." : "Current distribution across the four product decision queues."}><ProductQualityWorkloadChart categories={categoryWorkload?.totalElements ?? 0} images={imageWorkload?.totalElements ?? 0} nutrition={nutritionWorkload?.totalElements ?? 0} rejected={rejectedWorkload?.totalElements ?? 0} locale={locale} /></Panel>
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
        ,publicationStatus && `${text.publication}: ${humanizeFeature(publicationStatus)}`
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
            {text.publication}
            <select value={publicationStatus} onChange={(event) => setPublicationStatus(event.target.value)}>
              <option value="">{text.all}</option>
              {PUBLICATION_STATUSES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
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
              {QUALITY_ISSUE_GROUPS.map((group) => <optgroup key={group.key} label={locale === "tr" ? group.tr : group.en}>{group.values.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}</optgroup>)}
            </select>
          </label>
        </div>
        <details className="review-transfer-panel product-review-bulk-tools">
          <summary>{text.bulkTools}</summary>
          <div className="product-review-bulk-intro">
            <div><span>GRUN_STANDARD</span><strong>{text.catalogImport}</strong><p>{text.catalogImportDescription}</p></div>
            <ol aria-label={text.catalogImport}>
              <li><b>1</b><span>{text.catalogFile}</span></li>
              <li><b>2</b><span>{text.dryRun}</span></li>
              <li><b>3</b><span>{text.applyImport}</span></li>
            </ol>
          </div>
          <div className="review-transfer-actions">
            <label className="file-picker product-review-file-picker">
              <span>{text.catalogFile}</span>
              <input type="file" accept=".csv,text/csv" onChange={(event) => { setCatalogFile(event.target.files?.[0] ?? null); setCatalogResult(null); setCatalogNotice(null); setCatalogPreviewPassed(false); }} />
              <span className="product-review-file-control"><b>{text.chooseFile}</b><em>{catalogFile?.name ?? text.noFile}</em></span>
            </label>
            <span className="muted-text">{text.previewRequired}</span>
            <div className="product-review-import-actions"><button className="ghost-button" type="button" disabled={catalogTransferState === "loading" || !catalogFile} onClick={() => importNewCatalog(true)}>{text.dryRun}</button><button className="primary-button" type="button" disabled={catalogTransferState === "loading" || !catalogFile || !catalogPreviewPassed} onClick={() => importNewCatalog(false)}>{text.applyImport}</button></div>
          </div>
          {catalogNotice && <div className={`product-review-import-notice ${catalogNotice.tone}`} role="status" aria-live="polite"><strong>{catalogNotice.tone === "success" ? (locale === "tr" ? "Katalog dosyası hazır" : "Catalog file ready") : (locale === "tr" ? "Kontrol gerekli" : "Attention required")}</strong><span>{catalogNotice.message}</span></div>}
          {catalogResult && <div className="correction-result-grid">
            <MetricPill label={text.totalRows} value={formatValue(catalogResult.totalRows)} />
            <MetricPill label={locale === "tr" ? "Yeni ürün" : "New products"} value={formatValue(catalogResult.insertedRows)} />
            <MetricPill label={text.updatedRows} value={formatValue(catalogResult.updatedRows)} />
            <MetricPill label={text.skippedRows} value={formatValue(catalogResult.skippedRows)} />
            <MetricPill label={locale === "tr" ? "İnceleme gerekli" : "Review required"} value={formatValue(catalogResult.reviewRequiredRows)} />
            <MetricPill label={locale === "tr" ? "Kalite puanı" : "Quality score"} value={formatValue(catalogResult.importQualityScore)} />
          </div>}
          {catalogResult?.errors?.length ? <div className="correction-error-list">
            {catalogResult.errors.slice(0, 5).map((error, index) => <span key={`${error.rowNumber ?? index}-${error.identifier ?? "row"}`}>{locale === "tr" ? "Satır" : "Row"} {error.rowNumber ?? "-"}: {error.reason ?? error.code ?? "Import error"}</span>)}
          </div> : null}
          <hr />
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
              <input type="file" accept=".csv,.tsv,text/csv,text/tab-separated-values" onChange={(event) => { setCorrectionFile(event.target.files?.[0] ?? null); setCorrectionResult(null); setCorrectionNotice(null); }} />
              <span className="product-review-file-control"><b>{text.chooseFile}</b><em>{correctionFile?.name ?? text.noFile}</em></span>
            </label>
            <label className="inline-check review-transfer-check">
              <input type="checkbox" checked={markVerifiedOnImport} onChange={(event) => setMarkVerifiedOnImport(event.target.checked)} />
              <span>{text.markVerified}</span>
            </label>
            <div className="product-review-import-actions"><button className="ghost-button" type="button" disabled={transferState === "loading" || !correctionFile} onClick={() => importNutritionCorrections(true)}>{text.dryRun}</button><button className="primary-button" type="button" disabled={transferState === "loading" || !correctionFile} onClick={() => importNutritionCorrections(false)}>{text.applyImport}</button></div>
          </div>
          {correctionNotice && <div className={`product-review-import-notice ${correctionNotice.tone}`} role="status" aria-live="polite"><strong>{correctionNotice.tone === "success" ? (locale === "tr" ? "İçe aktarma başarılı" : "Import successful") : correctionNotice.tone === "warning" ? (locale === "tr" ? "İşlem tamamlandı, kontrol gerekli" : "Completed with attention required") : (locale === "tr" ? "Prova sonucu" : "Dry-run result")}</strong><span>{correctionNotice.message}</span></div>}
          {correctionResult && <div className="correction-result-grid">
            <MetricPill label={text.importMode} value={correctionResult.dryRun ? (locale === "tr" ? "Prova" : "Dry-run") : (locale === "tr" ? "Uygulandı" : "Applied")} />
            <MetricPill label={text.totalRows} value={formatValue(correctionResult.totalRows)} />
            <MetricPill label={text.matchedRows} value={formatValue(correctionResult.candidateRows ?? correctionResult.updatedRows)} />
            <MetricPill label={text.updatedRows} value={formatValue(correctionResult.updatedRows)} />
            <MetricPill label={text.skippedRows} value={formatValue(correctionResult.skippedRows)} />
          </div>}
          {correctionResult?.errors?.length ? <div className="correction-error-list">
            {correctionResult.errors.slice(0, 5).map((error) => <span key={error}>{error}</span>)}
          </div> : null}
        </details>
      </CollapsiblePanel>

      <section className="product-review-queue" aria-labelledby="product-review-queue-title">
        <div className="product-review-queue-heading"><div><span className="eyebrow">{text.queueEyebrow}</span><h3 id="product-review-queue-title">{text.queueTitle}</h3><p>{text.queueDescription}</p></div><strong>{formatValue(totalElements)}</strong></div>
      {canManage && <div className="product-review-publish-toolbar">
        <label className="inline-check"><input type="checkbox" aria-label={locale === "tr" ? "Sayfadaki doğrulanmış yayınlanmamış ürünleri seç" : "Select verified unpublished products on this page"} checked={publishableRows.length > 0 && publishableRows.every(item => selectedPublishIds.has(item.id!))} onChange={event => { const ids = publishableRows.map(item => item.id!); setSelectedPublishIds(current => { const next = new Set(current); ids.forEach(id => event.target.checked ? next.add(id) : next.delete(id)); return next; }); }} /><span>{locale === "tr" ? "Sayfayı seç" : "Select page"}</span></label>
        <span><strong>{selectedPublishIds.size}</strong> {locale === "tr" ? "ürün seçildi" : "products selected"}</span>
        <input maxLength={500} value={batchPublishReason} onChange={event => setBatchPublishReason(event.target.value)} placeholder={locale === "tr" ? "Toplu yayın gerekçesi" : "Batch publication reason"} />
        <button className="primary-button" type="button" disabled={!selectedPublishIds.size || !batchPublishReason.trim() || batchPublishing} onClick={() => void publishSelectedProducts()}>{batchPublishing ? (locale === "tr" ? "Yayınlanıyor…" : "Publishing…") : (locale === "tr" ? "Seçilenleri yayınla" : "Publish selected")}</button>
      </div>}
      <DataTable
        caption={text.queueTitle}
        columns={[...(canManage ? [locale === "tr" ? "Seç" : "Select"] : []), text.product, text.source, text.review, text.publicationColumn, text.quality, text.nutrition]}
        rows={rows.map((item) => [
          ...(canManage ? [<input key={`select-${item.id}`} type="checkbox" aria-label={`${productName(item)} ${locale === "tr" ? "ürününü seç" : "select product"}`} disabled={!item.id || item.verificationStatus !== "VERIFIED" || item.publicationStatus === "PUBLISHED"} checked={Boolean(item.id && selectedPublishIds.has(item.id))} onClick={event => event.stopPropagation()} onChange={event => { if (!item.id) return; setSelectedPublishIds(current => { const next = new Set(current); if (event.target.checked) next.add(item.id!); else next.delete(item.id!); return next; }); }} />] : []),
          <ProductCell item={item} />,
          <div className="table-stack">
            <span>{item.marketRegion ?? "-"}</span>
            <small>{item.dataSource ?? "-"}</small>
          </div>,
          <div className="badge-stack">
            <Badge value={item.verificationStatus} />
            <Badge value={item.imageStatus} tone="neutral" />
          </div>,
          <Badge value={item.publicationStatus ?? "INTERNAL_REVIEW"} tone={item.publicationStatus === "PUBLISHED" ? "good" : item.publicationStatus === "HIDDEN" ? "danger" : "warn"} />,
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
          onPublish={async () => {
            if (!selectedProduct.id || !reviewNote.trim()) return;
            setSaving(true);
            onError(null);
            try {
              const published = await request<FoodProduct>(`/api/v1/admin/products/${selectedProduct.id}/publish`, { method: "POST", body: { reason: reviewNote.trim() } });
              setSelectedProduct(published);
              setReviewDraft(toProductReviewDraft(published));
              setReviewNote("");
              await reload();
            } catch (error) { onError(formatRequestError(error)); }
            finally { setSaving(false); }
          }}
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
  onPublish,
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
  onPublish: () => void;
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
    eyebrow: "Ürün inceleme", noIdentity: "Marka veya barkod yok", close: "Kapat", quality: "Kalite ve kanıt", edit: "Ürünü düzenle", barcode: "Barkod", snapshot: "Ürün özeti", noImage: "Görsel yok", editIntro: "Katalog bilgileri", editHint: "Temel alanları ve 100 g/ml başına besin değerlerini doğrulayın.", reviewNote: "İnceleme notu", reviewPlaceholder: "Yayın için zorunlu karar ve kaynak notu", reject: "Reddet", save: "Değişiklikleri kaydet", approve: "Doğrula", publish: "Mobil kataloğa yayınla", saving: "Kaydediliyor..."
  } : {
    eyebrow: "Product review", noIdentity: "No brand or barcode", close: "Close", quality: "Quality & evidence", edit: "Edit product", barcode: "Barcode", snapshot: "Product snapshot", noImage: "No image", editIntro: "Catalog information", editHint: "Verify core fields and nutrition values per 100g/ml.", reviewNote: "Review note", reviewPlaceholder: "Required publication decision and source note", reject: "Reject", save: "Save changes", approve: "Verify", publish: "Publish to mobile catalog", saving: "Saving..."
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
              <Badge value={item.publicationStatus} tone={item.publicationStatus === "PUBLISHED" ? "good" : "warn"} />
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
              <EditableDetail label={locale === "tr" ? "Marka" : "Brand"}>
                <input value={draft.brand} onChange={(event) => updateDraft("brand", event.target.value)} />
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
              <EditableDetail label={locale === "tr" ? "Hazırlama durumu" : "Preparation state"}>
                <select value={draft.preparationState} onChange={(event) => updateDraft("preparationState", event.target.value)}>
                  {PREPARATION_STATES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
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
            <section className="product-review-editor-section">
              <header><div><span>{locale === "tr" ? "Ölçüm ve kaynak" : "Measurement & source"}</span><strong>{locale === "tr" ? "Besin referansı ve katalog kaynağı" : "Nutrition reference and catalog provenance"}</strong></div><small>{locale === "tr" ? "Mobil uygulama bu ölçüye göre doğru porsiyon seçeneklerini gösterir." : "The mobile app uses this basis to present the correct portion options."}</small></header>
              <div className="product-review-editor-grid">
                <label><span>{locale === "tr" ? "Besin verisinin türü" : "Nutrition provenance"}</span><select value={draft.nutritionBasis} onChange={(event) => updateDraft("nutritionBasis", event.target.value)}><option value="SOURCE_REPORTED">{locale === "tr" ? "Kaynakta bildirilen" : "Source reported"}</option><option value="CALCULATED">{locale === "tr" ? "Hesaplanan" : "Calculated"}</option><option value="ESTIMATED">{locale === "tr" ? "Tahmini" : "Estimated"}</option></select></label>
                <label><span>{locale === "tr" ? "Referans ölçü" : "Reference unit"}</span><select value={draft.nutritionReferenceUnit} onChange={(event) => updateDraft("nutritionReferenceUnit", event.target.value)}><option value="PER_100G">100 g</option><option value="PER_100ML">100 ml</option><option value="PER_SERVING">{locale === "tr" ? "1 servis" : "1 serving"}</option></select></label>
                <label className="wide"><span>{locale === "tr" ? "Kaynak kategori etiketleri" : "Source category tags"}</span><input value={draft.sourceCategoryTags} onChange={(event) => updateDraft("sourceCategoryTags", event.target.value)} placeholder={locale === "tr" ? "Virgülle ayırın" : "Comma separated"} /></label>
                <label><span>{locale === "tr" ? "Kaynak adı" : "Source name"}</span><input maxLength={160} value={draft.adminSourceName} onChange={(event) => updateDraft("adminSourceName", event.target.value)} /></label>
                <label><span>{locale === "tr" ? "Kaynak bağlantısı" : "Source URL"}</span><input type="url" maxLength={1000} value={draft.adminSourceUrl} onChange={(event) => updateDraft("adminSourceUrl", event.target.value)} placeholder="https://..." /></label>
              </div>
            </section>
            <section className="product-review-editor-section">
              <header><div><span>{locale === "tr" ? "İçerik ve güvenlik" : "Content & safety"}</span><strong>{locale === "tr" ? "Etiket üzerinde bildirilen bilgiler" : "Label-declared product information"}</strong></div></header>
              <div className="product-review-editor-grid">
                <label className="wide"><span>{locale === "tr" ? "İçindekiler" : "Ingredients"}</span><textarea maxLength={5000} value={draft.ingredientsText} onChange={(event) => updateDraft("ingredientsText", event.target.value)} /></label>
                <label className="wide"><span>{locale === "tr" ? "Alerjenler" : "Allergens"}</span><textarea maxLength={1000} value={draft.allergens} onChange={(event) => updateDraft("allergens", event.target.value)} /></label>
                <label className="wide"><span>{locale === "tr" ? "Yönetici kaynak notu" : "Admin source note"}</span><textarea maxLength={500} value={draft.adminCreationNote} onChange={(event) => updateDraft("adminCreationNote", event.target.value)} /></label>
              </div>
            </section>
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
              {item.verificationStatus === "VERIFIED" && item.publicationStatus !== "PUBLISHED" && <button className="primary-button" disabled={saving || !reviewNote.trim()} onClick={onPublish} type="button">{saving ? detailText.saving : detailText.publish}</button>}
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
  const [servingEditor, setServingEditor] = useState<{ id?: number; value: AdminFoodServingOptionUpsert } | null>(null);

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

  function openServingEditor(item?: NonNullable<AdminProductQualityWorkbench["servingOptions"]>[number]) {
    const localized = (language: "EN" | "TR") => item?.localizations?.find((value) => value.language === language)?.label ?? "";
    setServingEditor({
      id: item?.id,
      value: {
        label: item?.label ?? "",
        unitType: item?.unitType ?? "SERVING",
        quantity: item?.quantity ?? 1,
        gramWeight: item?.gramWeight,
        mlVolume: item?.mlVolume,
        defaultOption: item?.defaultOption ?? false,
        userVisible: item?.userVisible ?? true,
        qualityStatus: item?.qualityStatus === "NEEDS_REVIEW" ? "NEEDS_REVIEW" : "VERIFIED",
        localizations: [
          { language: "EN", label: localized("EN") },
          { language: "TR", label: localized("TR") }
        ]
      }
    });
  }

  async function saveServingOption() {
    if (!product.id || !servingEditor) return;
    setState("loading");
    try {
      await request(`/api/v1/admin/products/${product.id}/serving-options${servingEditor.id ? `/${servingEditor.id}` : ""}`, {
        method: servingEditor.id ? "PUT" : "POST",
        body: servingEditor.value
      });
      setServingEditor(null);
      await loadWorkbench();
      setTab("serving");
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
      <div className="product-workbench-toolbar serving-option-toolbar">
        <div><strong>{locale === "tr" ? "Manuel porsiyon seçenekleri" : "Manual serving options"}</strong><small>{locale === "tr" ? "Gram/ml dönüşümü ve EN/TR etiketleri birlikte yönetilir." : "Manage gram/ml conversion and EN/TR labels together."}</small></div>
        <button className="primary-button" type="button" onClick={() => openServingEditor()}>{locale === "tr" ? "Porsiyon ekle" : "Add serving"}</button>
      </div>
      {servingEditor && <ServingOptionEditor editor={servingEditor} locale={locale} busy={state === "loading"} allowNativeServing={product.nutritionReferenceUnit === "PER_SERVING"} onChange={setServingEditor} onCancel={() => setServingEditor(null)} onSave={saveServingOption} />}
      <DataTable columns={locale === "tr" ? ["Porsiyon", "Dönüşüm", "Kaynak", "Kalite", "Diller", "İşlem"] : ["Serving", "Conversion", "Source", "Quality", "Localizations", "Action"]} rows={(data?.servingOptions ?? []).map((item) => [<div className="entity-cell"><strong>{item.label ?? "-"}</strong><small>{item.defaultOption ? (locale === "tr" ? "Varsayılan" : "Default") : item.unitType ?? "-"} · {item.userVisible === false ? (locale === "tr" ? "Gizli" : "Hidden") : (locale === "tr" ? "Görünür" : "Visible")}</small></div>, [item.gramWeight != null ? `${item.gramWeight} g` : null, item.mlVolume != null ? `${item.mlVolume} ml` : null].filter(Boolean).join(" / ") || `${formatValue(item.quantity)} ${item.unitType ?? ""}`, item.source ?? "-", <Badge value={item.qualityStatus ?? "-"} tone={item.qualityStatus === "VERIFIED" ? "good" : "warn"} />, (item.localizations ?? []).map((value) => `${value.language}: ${value.label}`).join(", ") || "-", <button className="ghost-button" type="button" onClick={() => openServingEditor(item)}>{locale === "tr" ? "Düzenle" : "Edit"}</button>])} empty={locale === "tr" ? "Porsiyon seçeneği yok." : "No serving options."} />
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

export function ServingOptionEditor({ editor, locale, busy, allowNativeServing, onChange, onCancel, onSave }: { editor: { id?: number; value: AdminFoodServingOptionUpsert }; locale: "tr" | "en"; busy: boolean; allowNativeServing: boolean; onChange: (value: { id?: number; value: AdminFoodServingOptionUpsert }) => void; onCancel: () => void; onSave: () => void }) {
  const tr = locale === "tr";
  const update = (patch: Partial<AdminFoodServingOptionUpsert>) => onChange({ ...editor, value: { ...editor.value, ...patch } });
  const updateLocalization = (language: "EN" | "TR", label: string) => update({ localizations: editor.value.localizations.map((item) => item.language === language ? { ...item, label } : item) });
  const nativeServing = allowNativeServing && ["SERVING", "PORTION", "PIECE"].includes(editor.value.unitType);
  const valid = editor.value.label.trim() && editor.value.quantity > 0 && (nativeServing || Number(editor.value.gramWeight) > 0 || Number(editor.value.mlVolume) > 0) && editor.value.localizations.every((item) => item.label.trim()) && (!editor.value.defaultOption || (editor.value.userVisible && editor.value.qualityStatus === "VERIFIED")) && (!editor.value.userVisible || editor.value.qualityStatus === "VERIFIED");
  return <section className="serving-option-editor" aria-label={tr ? "Porsiyon seçeneği düzenleyici" : "Serving option editor"}>
    <header><div><span className="eyebrow">{editor.id ? (tr ? "PORSİYON DÜZENLE" : "EDIT SERVING") : (tr ? "YENİ PORSİYON" : "NEW SERVING")}</span><strong>{tr ? "Kullanıcıya sunulacak ölçüyü tanımlayın" : "Define the measure shown to users"}</strong></div></header>
    <div className="serving-option-form-grid">
      <label><span>{tr ? "İç etiket" : "Internal label"}</span><input value={editor.value.label} maxLength={120} onChange={(event) => update({ label: event.target.value })} /></label>
      <label><span>{tr ? "Birim" : "Unit"}</span><select value={editor.value.unitType} onChange={(event) => update({ unitType: event.target.value })}>{FOOD_SERVING_OPTION_UNITS.map((unit) => <option key={unit} value={unit}>{humanizeFeature(unit)}</option>)}</select></label>
      <label><span>{tr ? "Miktar" : "Quantity"}</span><input type="number" min="0.01" step="0.01" value={editor.value.quantity} onChange={(event) => update({ quantity: Number(event.target.value) })} /></label>
      <label><span>{tr ? "Gram karşılığı" : "Gram equivalent"}</span><input type="number" min="0.01" step="0.01" value={editor.value.gramWeight ?? ""} placeholder={tr ? "Sıvıda opsiyonel" : "Optional for liquids"} onChange={(event) => update({ gramWeight: event.target.value ? Number(event.target.value) : undefined })} /></label>
      <label><span>{tr ? "Mililitre karşılığı" : "Milliliter equivalent"}</span><input type="number" min="0.01" step="0.01" value={editor.value.mlVolume ?? ""} placeholder={tr ? "Katıda opsiyonel" : "Optional for solids"} onChange={(event) => update({ mlVolume: event.target.value ? Number(event.target.value) : undefined })} /></label>
      <label><span>{tr ? "Kalite durumu" : "Quality status"}</span><select value={editor.value.qualityStatus} onChange={(event) => update({ qualityStatus: event.target.value as AdminFoodServingOptionUpsert["qualityStatus"] })}><option value="VERIFIED">VERIFIED</option><option value="NEEDS_REVIEW">NEEDS_REVIEW</option></select></label>
      <label><span>{tr ? "İngilizce etiket" : "English label"}</span><input value={editor.value.localizations.find((item) => item.language === "EN")?.label ?? ""} maxLength={120} onChange={(event) => updateLocalization("EN", event.target.value)} /></label>
      <label><span>{tr ? "Türkçe etiket" : "Turkish label"}</span><input value={editor.value.localizations.find((item) => item.language === "TR")?.label ?? ""} maxLength={120} onChange={(event) => updateLocalization("TR", event.target.value)} /></label>
    </div>
    <div className="serving-option-switches">
      <label><input type="checkbox" checked={editor.value.userVisible} onChange={(event) => update({ userVisible: event.target.checked })} /><span>{tr ? "Mobil uygulamada göster" : "Show in mobile app"}</span></label>
      <label><input type="checkbox" checked={editor.value.defaultOption} onChange={(event) => update({ defaultOption: event.target.checked })} /><span>{tr ? "Varsayılan seçenek" : "Default option"}</span></label>
    </div>
    <p className="serving-option-help">{allowNativeServing ? (tr ? "Servis bazlı üründe gram/ml dönüşümü yalnızca kaynakta varsa girilir. Görünür ve varsayılan seçenekler doğrulanmış olmalıdır." : "For per-serving products, add gram/ml only when the source provides it. Visible and default options must be verified.") : (tr ? "En az bir dönüşüm değeri zorunludur. Görünür ve varsayılan seçenekler doğrulanmış olmalıdır." : "At least one conversion is required. Visible and default options must be verified.")}</p>
    <div className="inline-actions"><button className="ghost-button" type="button" onClick={onCancel}>{tr ? "İptal" : "Cancel"}</button><button className="primary-button" type="button" disabled={busy || !valid} onClick={onSave}>{busy ? (tr ? "Kaydediliyor…" : "Saving...") : (tr ? "Kaydet" : "Save")}</button></div>
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
  publicationStatus: string;
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
  publicationStatus: string;
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
    brand: item.brand ?? "",
    displayImageUrl: item.displayImageUrl ?? item.imageUrl ?? item.externalImageUrl ?? "",
    marketRegion: item.marketRegion ?? "",
    verificationStatus: item.verificationStatus ?? "",
    imageStatus: item.imageStatus ?? "",
    imageSource: item.imageSource ?? "",
    catalogType: item.catalogType ?? "",
    preparationState: item.preparationState ?? "UNSPECIFIED",
    nutritionBasis: item.nutritionBasis ?? "SOURCE_REPORTED",
    nutritionReferenceUnit: item.nutritionReferenceUnit ?? "PER_100G",
    ingredientsText: item.ingredientsText ?? "",
    allergens: item.allergens ?? "",
    sourceCategoryTags: item.sourceCategoryTags?.join(", ") ?? "",
    adminSourceName: item.adminSourceName ?? "",
    adminSourceUrl: item.adminSourceUrl ?? "",
    adminCreationNote: item.adminCreationNote ?? "",
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
import "./product-review.css";
