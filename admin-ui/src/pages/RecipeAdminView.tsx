import { FormEvent, lazy, Suspense, useEffect, useState } from "react";

import { formatRequestError, PageResponse, request } from "../api";

import { FoodProduct, AdminRecipe, AdminRecipeOperationsAnalytics, AdminRecipeImportCandidate, AdminRecipeImportResult } from "../types";

import { AsyncState, CollapsiblePanel, DataTable, LoadState, MetricCard, PaginationControls, Panel, SectionToolbar, useDialogAccessibility } from "../AdminPrimitives";

import { Badge, DetailItem, EditableDetail, IMAGE_SOURCES, IMAGE_STATUSES, MARKET_REGIONS, VERIFICATION_STATUSES, formatValue, humanizeFeature, productName, useEndpoint } from "./../admin/shared";
import { useAdminLocale } from "../admin/locale";

export const RecipeModerationChart = lazy(() => import("../RecipeOperationsCharts").then((module) => ({ default: module.RecipeModerationChart })));

export const RecipeBacklogChart = lazy(() => import("../RecipeOperationsCharts").then((module) => ({ default: module.RecipeBacklogChart })));

export const RecipeImportPipelineChart = lazy(() => import("../RecipeOperationsCharts").then((module) => ({ default: module.RecipeImportPipelineChart })));

export const RecipeSubmissionTrendChart = lazy(() => import("../RecipeOperationsCharts").then((module) => ({ default: module.RecipeSubmissionTrendChart })));

export const MEAL_TYPES = ["BREAKFAST", "LUNCH", "DINNER", "SNACK"];

export const PORTION_UNITS = ["GRAM", "MILLILITER", "TABLESPOON", "TEASPOON", "SLICE", "SERVING", "PIECE"];

export const RECIPE_VISIBILITIES = ["PRIVATE", "PUBLIC_ADMIN", "COMMUNITY_PENDING"];

export const RECIPE_IMPORT_STATUSES = ["PENDING", "APPROVED", "REJECTED", "FAILED"];

export const RECIPE_CATEGORIES = [
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

export type AdminRecipeIngredientForm = {
  foodItemId: string;
  productSearchQuery: string;
  productLabel: string;
  portionSize: string;
  portionUnit: string;
};

export type AdminRecipeCreateForm = {
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

export type AdminCatalogProductCreateForm = {
  name: string;
  catalogType: string;
  marketRegion: string;
  preparationState: string;
  brand: string;
  calories: string;
  protein: string;
  carbs: string;
  fat: string;
  fiber: string;
  sugar: string;
  sourceName: string;
  sourceUrl: string;
  reviewNote: string;
  saveSearchAlias: boolean;
  searchAlias: string;
  searchAliasLanguage: string;
};

export function emptyAdminCatalogProductForm(name = "", marketRegion = "GLOBAL", language = "EN"): AdminCatalogProductCreateForm {
  return {
    name,
    catalogType: "GENERIC_INGREDIENT",
    marketRegion: marketRegion || "GLOBAL",
    preparationState: "UNSPECIFIED",
    brand: "",
    calories: "",
    protein: "",
    carbs: "",
    fat: "",
    fiber: "",
    sugar: "",
    sourceName: "",
    sourceUrl: "",
    reviewNote: "",
    saveSearchAlias: true,
    searchAlias: name,
    searchAliasLanguage: language.toUpperCase() === "TR" ? "TR" : "EN"
  };
}

export const emptyRecipeCreateForm: AdminRecipeCreateForm = {
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

export function numericOrNull(value: string): number | null {
  const trimmed = value.trim();
  if (!trimmed) return null;
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : null;
}

export function RecipeAdminView({ onError }: { onError: (message: string | null) => void }) {
  const { locale } = useAdminLocale();
  const tr = locale === "tr";
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
  const [recipeDetailTab, setRecipeDetailTab] = useState<"overview" | "details" | "content" | "moderation">("overview");
  const [draftName, setDraftName] = useState("");
  const [draftDescription, setDraftDescription] = useState("");
  const [draftMealType, setDraftMealType] = useState("");
  const [draftMarketRegion, setDraftMarketRegion] = useState("");
  const [draftLanguage, setDraftLanguage] = useState("");
  const [draftTotalYieldGrams, setDraftTotalYieldGrams] = useState("");
  const [draftDefaultServingGrams, setDraftDefaultServingGrams] = useState("");
  const [draftServingCount, setDraftServingCount] = useState("");
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
  const [importFiltersOpen, setImportFiltersOpen] = useState(false);
  const [reviewingImportId, setReviewingImportId] = useState<number | null>(null);
  const [selectedImportCandidate, setSelectedImportCandidate] = useState<AdminRecipeImportCandidate | null>(null);
  const [importIngredientSearchIndex, setImportIngredientSearchIndex] = useState<number | null>(null);
  const [importIngredientSearchQuery, setImportIngredientSearchQuery] = useState("");
  const [importIngredientProductId, setImportIngredientProductId] = useState("");
  const [importIngredientSearchResults, setImportIngredientSearchResults] = useState<FoodProduct[]>([]);
  const [importIngredientSearchState, setImportIngredientSearchState] = useState<LoadState>("idle");
  const [showImportProductCreate, setShowImportProductCreate] = useState(false);
  const [creatingImportProduct, setCreatingImportProduct] = useState(false);
  const [importProductCreateForm, setImportProductCreateForm] = useState<AdminCatalogProductCreateForm>(() => emptyAdminCatalogProductForm());
  const [importProductPreflight, setImportProductPreflight] = useState<{ duplicateCandidates?: FoodProduct[]; nutritionWarnings?: string[] } | null>(null);
  const [recipeFiltersOpen, setRecipeFiltersOpen] = useState(false);
  const [recipeAnalyticsWindowDays, setRecipeAnalyticsWindowDays] = useState(30);
  const [recipeImportStateOpen, setRecipeImportStateOpen] = useState(false);
  const [recipeImportSourceOpen, setRecipeImportSourceOpen] = useState(false);
  const [activeIngredientSearchIndex, setActiveIngredientSearchIndex] = useState<number | null>(null);
  const [ingredientSearchResults, setIngredientSearchResults] = useState<FoodProduct[]>([]);
  const [ingredientSearchState, setIngredientSearchState] = useState<LoadState>("idle");
  const importCandidateDialogRef = useDialogAccessibility(() => setSelectedImportCandidate(null), Boolean(selectedImportCandidate));
  const recipeDialogRef = useDialogAccessibility(closeRecipe, Boolean(selectedRecipe));
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
  const {
    data: recipeAnalytics,
    state: recipeAnalyticsState,
    reload: reloadRecipeAnalytics
  } = useEndpoint<AdminRecipeOperationsAnalytics>(`/api/v1/admin/recipes/analytics?windowDays=${recipeAnalyticsWindowDays}`, onError);
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
    setActiveIngredientSearchIndex(index);
    setIngredientSearchState("loading");
    onError(null);
    try {
      setIngredientSearchResults(await searchAdminRecipeMappingProducts(searchText, createForm.marketRegion));
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

  function openImportIngredientResolver(index: number, ingredientName?: string, foodItemId?: number) {
    setImportIngredientSearchIndex(index);
    setImportIngredientSearchQuery((ingredientName ?? "").trim());
    setImportIngredientProductId(foodItemId ? String(foodItemId) : "");
    setImportIngredientSearchResults([]);
    setImportIngredientSearchState("idle");
    setShowImportProductCreate(false);
    setImportProductCreateForm(emptyAdminCatalogProductForm(ingredientName, selectedImportCandidate?.marketRegion, selectedImportCandidate?.language));
    setImportProductPreflight(null);
    onError(null);
  }

  function updateImportProductCreateForm<K extends keyof AdminCatalogProductCreateForm>(key: K, value: AdminCatalogProductCreateForm[K]) {
    setImportProductCreateForm((current) => ({ ...current, [key]: value }));
    setImportProductPreflight(null);
  }

  async function searchImportIngredientProducts(index: number) {
    const searchText = importIngredientSearchQuery.trim();
    if (!searchText || searchText.length < 2) {
      onError("Ingredient search needs at least 2 characters.");
      return;
    }
    setImportIngredientSearchIndex(index);
    setImportIngredientSearchState("loading");
    setImportIngredientSearchResults([]);
    onError(null);
    try {
      setImportIngredientSearchResults(await searchAdminRecipeMappingProducts(
        searchText,
        selectedImportCandidate?.marketRegion
      ));
      setImportIngredientSearchState("ready");
    } catch (err) {
      setImportIngredientSearchResults([]);
      setImportIngredientSearchState("error");
      onError(formatRequestError(err));
    }
  }

  async function mapImportIngredientById(index: number, foodItemId: number) {
    if (!selectedImportCandidate?.id) return;
    setReviewingImportId(selectedImportCandidate.id);
    onError(null);
    try {
      const updated = await request<AdminRecipeImportCandidate>(`/api/v1/admin/recipes/imports/${selectedImportCandidate.id}/ingredients/${index}`, {
        method: "PATCH",
        body: { foodItemId }
      });
      setSelectedImportCandidate(updated);
      setImportIngredientSearchIndex(null);
      setImportIngredientSearchQuery("");
      setImportIngredientProductId("");
      setImportIngredientSearchResults([]);
      setImportIngredientSearchState("idle");
      await reloadImports();
    } catch (err) {
      onError(formatRequestError(err));
    } finally {
      setReviewingImportId(null);
    }
  }

  async function mapImportIngredient(index: number, product: FoodProduct) {
    if (!product.id) return;
    await mapImportIngredientById(index, product.id);
  }

  async function mapImportIngredientFromInput(index: number) {
    const normalizedId = importIngredientProductId.trim();
    if (!/^\d+$/.test(normalizedId) || Number(normalizedId) <= 0) {
      onError("Enter a valid positive product ID.");
      return;
    }
    await mapImportIngredientById(index, Number(normalizedId));
  }

  async function createAndMapImportProduct(event: FormEvent, index: number) {
    event.preventDefault();
    const calories = numericOrNull(importProductCreateForm.calories);
    if (calories === null || calories < 0) {
      onError("Calories per 100g is required and must be zero or greater.");
      return;
    }
    setCreatingImportProduct(true);
    onError(null);
    try {
      const body = {
        name: importProductCreateForm.name.trim(),
        catalogType: importProductCreateForm.catalogType,
        marketRegion: importProductCreateForm.marketRegion,
        preparationState: importProductCreateForm.preparationState,
        brand: importProductCreateForm.brand.trim() || null,
        calories,
        protein: numericOrNull(importProductCreateForm.protein),
        carbs: numericOrNull(importProductCreateForm.carbs),
        fat: numericOrNull(importProductCreateForm.fat),
        fiber: numericOrNull(importProductCreateForm.fiber),
        sugar: numericOrNull(importProductCreateForm.sugar),
        sourceName: importProductCreateForm.sourceName.trim(),
        sourceUrl: importProductCreateForm.sourceUrl.trim(),
        reviewNote: importProductCreateForm.reviewNote.trim() || null,
        searchAlias: importProductCreateForm.saveSearchAlias ? importProductCreateForm.searchAlias.trim() || null : null,
        searchAliasLanguage: importProductCreateForm.saveSearchAlias ? importProductCreateForm.searchAliasLanguage : null,
        allowPotentialDuplicate: Boolean(importProductPreflight?.duplicateCandidates?.length),
        confirmNutritionWarnings: Boolean(importProductPreflight?.nutritionWarnings?.length)
      };
      if (!importProductPreflight) {
        const preflight = await request<{ duplicateCandidates?: FoodProduct[]; nutritionWarnings?: string[] }>("/api/v1/admin/products/preflight", { method: "POST", body });
        if (preflight.duplicateCandidates?.length || preflight.nutritionWarnings?.length) {
          setImportProductPreflight(preflight);
          return;
        }
      }
      const product = await request<FoodProduct>("/api/v1/admin/products", { method: "POST", body });
      if (!product.id) {
        throw new Error("Created product did not return an ID.");
      }
      setImportIngredientProductId(String(product.id));
      await mapImportIngredientById(index, product.id);
    } catch (err) {
      onError(formatRequestError(err));
    } finally {
      setCreatingImportProduct(false);
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
    setRecipeDetailTab("overview");
    setSelectedRecipe(recipe);
    setDraftName(recipe.name ?? "");
    setDraftDescription(recipe.description ?? "");
    setDraftMealType(recipe.mealType ?? "");
    setDraftMarketRegion(recipe.marketRegion ?? "");
    setDraftLanguage(recipe.language ?? "");
    setDraftTotalYieldGrams(recipe.totalYieldGrams == null ? "" : String(recipe.totalYieldGrams));
    setDraftDefaultServingGrams(recipe.defaultServingGrams == null ? "" : String(recipe.defaultServingGrams));
    setDraftServingCount(recipe.servingCount == null ? "" : String(recipe.servingCount));
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
    setRecipeDetailTab("overview");
    setDraftName("");
    setDraftDescription("");
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
          name: draftName.trim(),
          description: draftDescription,
          mealType: draftMealType || null,
          marketRegion: draftMarketRegion || null,
          language: draftLanguage || null,
          totalYieldGrams: numericOrNull(draftTotalYieldGrams),
          defaultServingGrams: numericOrNull(draftDefaultServingGrams),
          servingCount: numericOrNull(draftServingCount),
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

  async function removeRecipeFromDiscover() {
    if (!selectedRecipe?.id || saving) return;
    setSaving(true);
    onError(null);
    try {
      await request<AdminRecipe>(`/api/v1/admin/recipes/${selectedRecipe.id}/review`, {
        method: "PATCH",
        body: {
          verificationStatus: "NEEDS_REVIEW",
          visibility: "PRIVATE",
          archived: false,
          reviewNote: reviewNote || "Removed from public recipe discovery by admin."
        }
      });
      await reload();
      closeRecipe();
      setSavedNotice("Removed from Discover");
      window.setTimeout(() => setSavedNotice(null), 2200);
    } catch (err) {
      onError(formatRequestError(err));
    } finally {
      setSaving(false);
    }
  }

  async function toggleRecipeArchive() {
    if (!selectedRecipe?.id || saving) return;
    const restoring = Boolean(selectedRecipe.archived);
    setSaving(true);
    onError(null);
    try {
      if (restoring) {
        await request<AdminRecipe>(`/api/v1/admin/recipes/${selectedRecipe.id}/review`, {
          method: "PATCH",
          body: { archived: false, visibility: "PRIVATE", verificationStatus: "NEEDS_REVIEW", reviewNote: "Restored by admin." }
        });
      } else {
        await request<void>(`/api/v1/admin/recipes/${selectedRecipe.id}`, { method: "DELETE" });
      }
      await reload();
      closeRecipe();
      setSavedNotice(restoring ? "Recipe restored as private" : "Recipe archived");
      window.setTimeout(() => setSavedNotice(null), 2200);
    } catch (err) {
      onError(formatRequestError(err));
    } finally {
      setSaving(false);
    }
  }


  return (
    <div className="stack recipe-operations-page modern-operations-page">
      <SectionToolbar title={tr ? "Tarif operasyonları" : "Recipe operations"} description={tr ? "Tarif yayınlama, moderasyon, içe aktarma ve etkileşim sinyallerini tek çalışma alanında yönetin." : "Manage recipe publishing, moderation, imports, and engagement signals in one workspace."} state={state} onReload={() => { reload(); reloadRecipeAnalytics(); }}>
        <button className="ghost-button" onClick={() => setShowImportPanel((value) => !value)} type="button">{showImportPanel ? (tr ? "İçe aktarmayı kapat" : "Close import") : (tr ? "JSON içe aktar" : "Import JSON")}</button>
        <button className="primary-button" onClick={() => setShowCreateRecipe((value) => !value)} type="button">{showCreateRecipe ? (tr ? "Oluşturmayı kapat" : "Close create") : (tr ? "Tarif oluştur" : "Create recipe")}</button>
        <button className="ghost-button" onClick={resetFilters} type="button">{tr ? "Filtreleri sıfırla" : "Reset filters"}</button>
      </SectionToolbar>
      {savedNotice && <div className="success-banner compact-success">{savedNotice}</div>}

      <section className="modern-workspace-hero recipe-operations-hero">
        <div className="modern-hero-copy">
          <span className="eyebrow">{tr ? "TARİF YAYIN MERKEZİ" : "RECIPE PUBLISHING CENTER"}</span>
          <h2>{tr ? "Kuyruktan yayına uzanan tarif akışını yönetin." : "Control the recipe journey from queue to publication."}</h2>
          <p>{tr ? "İçe aktarılan ve kullanıcı kaynaklı tarifleri kanıt, içerik kalitesi ve yayın uygunluğuyla birlikte değerlendirin." : "Review imported and user-created recipes with evidence, content quality, and publication readiness in context."}</p>
          <div className="recipe-engagement-pills"><span>{tr ? "Kaydedilen" : "Saved"} <b>{formatValue(recipeAnalytics?.engagement?.saved)}</b></span><span>{tr ? "Favori" : "Favorite"} <b>{formatValue(recipeAnalytics?.engagement?.favorite)}</b></span><span>{tr ? "Ortalama puan" : "Average rating"} <b>{recipeAnalytics?.engagement?.averageRating == null ? "-" : recipeAnalytics.engagement.averageRating.toFixed(1)}</b></span></div>
        </div>
        <div className="modern-hero-signals">
          <article className="accent"><span>{tr ? "Toplam tarif" : "Total recipes"}</span><strong>{formatValue(recipeAnalytics?.totalRecipes)}</strong><small>{formatValue(recipeAnalytics?.activeRecipes)} {tr ? "aktif" : "active"}</small></article>
          <article className="warning"><span>{tr ? "İnceleme bekliyor" : "Pending review"}</span><strong>{formatValue(recipeAnalytics?.pendingReview)}</strong><small>{formatValue(recipeAnalytics?.unassignedReview)} {tr ? "atanmamış" : "unassigned"}</small></article>
          <article className="danger"><span>{tr ? "Süresi geçti" : "Overdue review"}</span><strong>{formatValue(recipeAnalytics?.overdueReview)}</strong><small>{tr ? "atanan tarih aşıldı" : "past assigned due date"}</small></article>
        </div>
      </section>

      <div className="recipe-operations-analytics-head">
        <div>
          <strong>{tr ? "Tarif operasyon analitiği" : "Recipe operations analytics"}</strong>
          <span>{tr ? `Moderasyon, içe aktarma, etkileşim ve gönderim sinyalleri. ${formatValue(activeFilterCount)} aktif liste filtresi.` : `Aggregate moderation, import, engagement, and submission signals. ${formatValue(activeFilterCount)} active list filter${activeFilterCount === 1 ? "" : "s"}.`}</span>
        </div>
        <label>
          {tr ? "Analitik aralığı" : "Analytics window"}
          <select value={recipeAnalyticsWindowDays} onChange={(event) => setRecipeAnalyticsWindowDays(Number(event.target.value))}>
            <option value={7}>{tr ? "7 gün" : "7 days"}</option>
            <option value={30}>{tr ? "30 gün" : "30 days"}</option>
            <option value={90}>{tr ? "90 gün" : "90 days"}</option>
          </select>
        </label>
      </div>
      <AsyncState state={recipeAnalyticsState} hasData={Boolean(recipeAnalytics)} loadingMessage={tr ? "Tarif operasyon analitiği yükleniyor…" : "Loading recipe operations analytics..."} emptyMessage={tr ? "Tarif operasyon analitiği kullanılamıyor." : "Recipe operations analytics are unavailable."} />
      {recipeAnalytics && (
        <Suspense fallback={<AsyncState state="loading" hasData={false} loadingMessage="Loading recipe charts..." emptyMessage="Recipe charts are unavailable." />}>
          <div className="recipe-operations-chart-grid">
            <Panel title={tr ? "Moderasyon durumu" : "Moderation state"} description={tr ? "Tüm tariflerin güncel doğrulama durumuna göre dağılımı." : "All recipes grouped by current verification state."} className="recipe-operations-chart-panel">
              <RecipeModerationChart analytics={recipeAnalytics} />
            </Panel>
            <Panel title={tr ? "İnceleme kuyruğu yaşı" : "Review queue age"} description={tr ? "Bekleyen tariflerin yönetici inceleme süresine göre dağılımı." : "Pending recipes grouped by time waiting for admin review."} className="recipe-operations-chart-panel">
              <RecipeBacklogChart analytics={recipeAnalytics} />
            </Panel>
            <Panel title={tr ? "İçe aktarma hattı" : "Import pipeline"} description={tr ? "JSON adaylarının güncel işlem durumuna göre dağılımı." : "All JSON import candidates grouped by current processing state."} className="recipe-operations-chart-panel">
              <RecipeImportPipelineChart analytics={recipeAnalytics} />
            </Panel>
            <Panel title={tr ? "Tarif gönderimleri" : "Recipe submissions"} description={tr ? `Son ${recipeAnalytics.windowDays ?? recipeAnalyticsWindowDays} günde oluşturulan tarifler.` : `Created recipes across the last ${recipeAnalytics.windowDays ?? recipeAnalyticsWindowDays} days.`} className="recipe-operations-chart-panel recipe-submission-trend-panel">
              <RecipeSubmissionTrendChart analytics={recipeAnalytics} />
            </Panel>
          </div>
        </Suspense>
      )}

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

      <Panel title={tr ? "Tarif içe aktarma adayları" : "Recipe import candidates"}>
        <CollapsiblePanel title={tr ? "Aday filtreleri" : "Candidate filters"} description={tr ? "Durum, aktarım grubu ve liste boyutunu yönetin." : "Control status, import batch, and list size."} open={importFiltersOpen} onToggle={() => setImportFiltersOpen((value) => !value)}>
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
        </CollapsiblePanel>
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
          <section ref={importCandidateDialogRef} tabIndex={-1} className="modal-card recipe-import-detail-modal" role="dialog" aria-modal="true" aria-label="Recipe import candidate detail" onClick={(event) => event.stopPropagation()}>
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
                              onClick={() => openImportIngredientResolver(ingredientIndex, ingredient.ingredientName, ingredient.foodItemId)}
                            >
                              {isMapped ? "Change" : "Find"}
                            </button>
                          </div>
                        </div>
                        {isActiveSearch && (
                          <div className="recipe-import-resolver">
                            <label className="recipe-import-resolver-field">
                              Manual product search
                              <div className="inline-input-action">
                                <input autoFocus value={importIngredientSearchQuery} onChange={(event) => setImportIngredientSearchQuery(event.target.value)} onKeyDown={(event) => {
                                  if (event.key === "Enter") {
                                    event.preventDefault();
                                    void searchImportIngredientProducts(ingredientIndex);
                                  }
                                }} placeholder="Search another name, brand or barcode" />
                                <button className="ghost-button" type="button" disabled={importIngredientSearchState === "loading"} onClick={() => searchImportIngredientProducts(ingredientIndex)}>
                                  {importIngredientSearchState === "loading" ? "Searching..." : "Search"}
                                </button>
                              </div>
                            </label>
                            <label className="recipe-import-resolver-field">
                              Map by product ID
                              <div className="inline-input-action">
                                <input value={importIngredientProductId} onChange={(event) => setImportIngredientProductId(event.target.value)} onKeyDown={(event) => {
                                  if (event.key === "Enter") {
                                    event.preventDefault();
                                    void mapImportIngredientFromInput(ingredientIndex);
                                  }
                                }} inputMode="numeric" placeholder="Product ID" />
                                <button className="ghost-button" type="button" disabled={reviewingImportId === selectedImportCandidate.id} onClick={() => mapImportIngredientFromInput(ingredientIndex)}>
                                  {reviewingImportId === selectedImportCandidate.id ? "Mapping..." : "Map ID"}
                                </button>
                              </div>
                            </label>
                            <div className="ingredient-search-results recipe-import-product-results">
                              {importIngredientSearchState === "idle" && <span>Search covers all shared admin catalog regions and review states. Product ID, region, publication, and verification state are shown in each result.</span>}
                              {importIngredientSearchState === "loading" && <span>Searching products...</span>}
                              {importIngredientSearchState === "ready" && importIngredientSearchResults.length === 0 && <span>No product found for this search. Try another name or map a known product ID.</span>}
                              {importIngredientSearchResults.map((product) => (
                                <button className="ingredient-search-result recipe-import-product-result" key={product.id ?? product.normalizedBarcode ?? productName(product)} type="button" onClick={() => mapImportIngredient(ingredientIndex, product)}>
                                  <strong>{productName(product)}</strong>
                                  <span>{productIngredientLabel(product)}</span>
                                  <span>{productNutritionLabel(product)}</span>
                                </button>
                              ))}
                            </div>
                            <div className="recipe-import-create-toggle">
                              <span>Product not in the shared catalog?</span>
                              <button className="ghost-button" type="button" onClick={() => setShowImportProductCreate((current) => !current)}>
                                {showImportProductCreate ? "Close product form" : "Create product"}
                              </button>
                            </div>
                            {showImportProductCreate && (
                              <form className="recipe-import-product-create-form" onSubmit={(event) => createAndMapImportProduct(event, ingredientIndex)}>
                                <div className="recipe-import-product-create-head">
                                  <div>
                                    <strong>Create shared catalog product</strong>
                                    <span>Nutrition values must be per 100g. The product will remain in internal review and will be mapped to this ingredient automatically.</span>
                                  </div>
                                  <Badge value="Needs review" tone="warn" />
                                </div>
                                <div className="recipe-import-product-create-grid">
                                  <label>Product name<input required maxLength={255} value={importProductCreateForm.name} onChange={(event) => updateImportProductCreateForm("name", event.target.value)} /></label>
                                  <label>Catalog type<select value={importProductCreateForm.catalogType} onChange={(event) => updateImportProductCreateForm("catalogType", event.target.value)}><option value="GENERIC_INGREDIENT">Generic ingredient</option><option value="BRANDED_PRODUCT">Branded product</option></select></label>
                                  <label>Region<select value={importProductCreateForm.marketRegion} onChange={(event) => updateImportProductCreateForm("marketRegion", event.target.value)}>{MARKET_REGIONS.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}</select></label>
                                  <label>Preparation<select value={importProductCreateForm.preparationState} onChange={(event) => updateImportProductCreateForm("preparationState", event.target.value)}>{["UNSPECIFIED", "RAW", "COOKED", "BOILED", "GRILLED", "FRIED", "BAKED", "ROASTED", "STEAMED", "PREPARED"].map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}</select></label>
                                  <label>Brand (optional)<input maxLength={255} value={importProductCreateForm.brand} onChange={(event) => updateImportProductCreateForm("brand", event.target.value)} /></label>
                                  <label>Calories / 100g<input required min="0" step="0.1" type="number" value={importProductCreateForm.calories} onChange={(event) => updateImportProductCreateForm("calories", event.target.value)} /></label>
                                  <label>Protein / 100g<input min="0" step="0.1" type="number" value={importProductCreateForm.protein} onChange={(event) => updateImportProductCreateForm("protein", event.target.value)} /></label>
                                  <label>Carbs / 100g<input min="0" step="0.1" type="number" value={importProductCreateForm.carbs} onChange={(event) => updateImportProductCreateForm("carbs", event.target.value)} /></label>
                                  <label>Fat / 100g<input min="0" step="0.1" type="number" value={importProductCreateForm.fat} onChange={(event) => updateImportProductCreateForm("fat", event.target.value)} /></label>
                                  <label>Fiber / 100g<input min="0" step="0.1" type="number" value={importProductCreateForm.fiber} onChange={(event) => updateImportProductCreateForm("fiber", event.target.value)} /></label>
                                  <label>Sugar / 100g<input min="0" step="0.1" type="number" value={importProductCreateForm.sugar} onChange={(event) => updateImportProductCreateForm("sugar", event.target.value)} /></label>
                                  <label>Source name<input required maxLength={160} placeholder="USDA FoodData Central" value={importProductCreateForm.sourceName} onChange={(event) => updateImportProductCreateForm("sourceName", event.target.value)} /></label>
                                  <label className="recipe-import-product-create-wide">Source URL<input required type="url" maxLength={1000} placeholder="https://..." value={importProductCreateForm.sourceUrl} onChange={(event) => updateImportProductCreateForm("sourceUrl", event.target.value)} /></label>
                                  <label className="recipe-import-product-create-wide">Review note<textarea maxLength={500} rows={2} value={importProductCreateForm.reviewNote} onChange={(event) => updateImportProductCreateForm("reviewNote", event.target.value)} placeholder="How the values were selected or converted" /></label>
                                </div>
                                <div className="recipe-import-alias-option">
                                  <label className="checkbox-row">
                                    <input type="checkbox" checked={importProductCreateForm.saveSearchAlias} onChange={(event) => updateImportProductCreateForm("saveSearchAlias", event.target.checked)} />
                                    <span>Save the imported ingredient name as a searchable alias for this product.</span>
                                  </label>
                                  {importProductCreateForm.saveSearchAlias && (
                                    <div className="recipe-import-alias-fields">
                                      <label>Search alias<input required maxLength={255} value={importProductCreateForm.searchAlias} onChange={(event) => updateImportProductCreateForm("searchAlias", event.target.value)} /></label>
                                      <label>Language<select value={importProductCreateForm.searchAliasLanguage} onChange={(event) => updateImportProductCreateForm("searchAliasLanguage", event.target.value)}><option value="EN">English</option><option value="TR">Turkish</option></select></label>
                                    </div>
                                  )}
                                </div>
                                {importProductPreflight && (
                                  <div className="recipe-import-preflight">
                                    <strong>Review before creating</strong>
                                    {(importProductPreflight.nutritionWarnings ?? []).map((warning) => <p className="recipe-import-preflight-warning" key={warning}>{warning}</p>)}
                                    {(importProductPreflight.duplicateCandidates ?? []).length > 0 && <span>Potential catalog matches were found. Use an existing product when appropriate.</span>}
                                    <div className="recipe-import-preflight-candidates">
                                      {(importProductPreflight.duplicateCandidates ?? []).map((product) => (
                                        <button className="ingredient-search-result recipe-import-product-result" key={product.id ?? productName(product)} type="button" onClick={() => mapImportIngredient(ingredientIndex, product)}>
                                          <strong>{productName(product)}</strong>
                                          <span>{productIngredientLabel(product)}</span>
                                          <span>{productNutritionLabel(product)}</span>
                                        </button>
                                      ))}
                                    </div>
                                  </div>
                                )}
                                <div className="recipe-import-product-create-actions">
                                  <button className="primary-button" type="submit" disabled={creatingImportProduct || reviewingImportId === selectedImportCandidate.id}>
                                    {creatingImportProduct ? "Checking..." : importProductPreflight ? "Create anyway and map" : "Check and create"}
                                  </button>
                                </div>
                              </form>
                            )}
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
        columns={["Recipe", "Owner", "State", "Engagement", "Nutrition", "Actions"]}
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
          </div>,
          <button className="ghost-button" type="button" onClick={(event) => { event.stopPropagation(); openRecipe(recipe); }}>
            Edit / moderate
          </button>
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
          <section ref={recipeDialogRef} tabIndex={-1} className="product-modal recipe-modal" role="dialog" aria-modal="true" aria-label="Recipe admin detail" onClick={(event) => event.stopPropagation()}>
            <header className="modal-header">
              <div>
                <p className="eyebrow">{tr ? "TARİF İNCELEMESİ" : "RECIPE REVIEW"}</p>
                <h2>{draftName || selectedRecipe.name || "-"}</h2>
                <span>{selectedRecipe.ownerEmail ?? (tr ? "Sahip bilinmiyor" : "Unknown owner")}</span>
              </div>
              <button className="icon-button" onClick={closeRecipe} type="button" aria-label={tr ? "Kapat" : "Close"}>×</button>
            </header>
            <div className="modal-body">
              <div className="product-image-frame">
                {draftImageUrl ? <img alt={selectedRecipe.name ?? "Recipe"} src={draftImageUrl} /> : <span>No image</span>}
              </div>
              <div className="product-detail-stack">
                <nav className="recipe-review-tabs" aria-label={tr ? "Tarif inceleme bölümleri" : "Recipe review sections"}>
                  {([[
                    "overview", tr ? "Genel bakış" : "Overview"
                  ], ["details", tr ? "Tarif bilgileri" : "Recipe details"], ["content", tr ? "İçerik" : "Content"], ["moderation", tr ? "Moderasyon" : "Moderation"]] as const).map(([tab, label]) => (
                    <button key={tab} className={recipeDetailTab === tab ? "active" : ""} type="button" aria-current={recipeDetailTab === tab ? "page" : undefined} onClick={() => setRecipeDetailTab(tab)}>{label}</button>
                  ))}
                </nav>
                {recipeDetailTab === "overview" && <section className="recipe-review-pane recipe-review-overview">
                  <div className="recipe-review-state-strip">
                    <div><span>{tr ? "Doğrulama" : "Verification"}</span><Badge value={selectedRecipe.verificationStatus} /></div>
                    <div><span>{tr ? "Görünürlük" : "Visibility"}</span><Badge value={selectedRecipe.archived ? "ARCHIVED" : selectedRecipe.visibility} tone={selectedRecipe.archived ? "danger" : "neutral"} /></div>
                    <div><span>{tr ? "Görsel" : "Image"}</span><Badge value={selectedRecipe.imageStatus} tone="neutral" /></div>
                  </div>
                  <div className="recipe-review-fact-grid">
                    <DetailItem label={tr ? "Sahip" : "Owner"} value={selectedRecipe.ownerEmail} />
                    <DetailItem label={tr ? "Öğün" : "Meal type"} value={selectedRecipe.mealType} />
                    <DetailItem label={tr ? "Bölge" : "Region"} value={selectedRecipe.marketRegion} />
                    <DetailItem label={tr ? "Malzeme" : "Ingredients"} value={formatValue(selectedRecipe.ingredientCount)} />
                    <DetailItem label={tr ? "Kaydedilme" : "Saved"} value={formatValue(selectedRecipe.savedCount)} />
                    <DetailItem label={tr ? "Favori" : "Favorite"} value={formatValue(selectedRecipe.favoriteCount)} />
                    <DetailItem label={tr ? "Puan" : "Rating"} value={(selectedRecipe.averageRating ?? 0) > 0 ? `${selectedRecipe.averageRating?.toFixed(1)} / 5` : "-"} />
                    <DetailItem label={tr ? "Porsiyon" : "Serving"} value={`${formatValue(selectedRecipe.defaultServingGrams)} g`} />
                  </div>
                  <div className="recipe-review-macro-grid">
                    <article><span>kcal</span><strong>{formatValue(selectedRecipe.calories)}</strong></article>
                    <article><span>{tr ? "Protein" : "Protein"}</span><strong>{formatValue(selectedRecipe.protein)} g</strong></article>
                    <article><span>{tr ? "Karbonhidrat" : "Carbs"}</span><strong>{formatValue(selectedRecipe.carbs)} g</strong></article>
                    <article><span>{tr ? "Yağ" : "Fat"}</span><strong>{formatValue(selectedRecipe.fat)} g</strong></article>
                  </div>
                  {selectedRecipe.description && <p className="recipe-review-description">{selectedRecipe.description}</p>}
                </section>}
                {recipeDetailTab === "details" && <section className="recipe-review-pane">
                <div className="detail-grid editable">
                  <DetailItem label="Owner" value={selectedRecipe.ownerEmail} />
                  <DetailItem label="Visibility" value={selectedRecipe.visibility} />
                  <DetailItem label="Image status" value={selectedRecipe.imageStatus} />
                  <DetailItem label="Image source" value={selectedRecipe.imageSource} />
                  <DetailItem label="Saved count" value={formatValue(selectedRecipe.savedCount)} />
                  <DetailItem label="Favorite count" value={formatValue(selectedRecipe.favoriteCount)} />
                  <DetailItem label="Rating count" value={formatValue(selectedRecipe.ratingCount)} />
                  <DetailItem label="Average rating" value={(selectedRecipe.averageRating ?? 0) > 0 ? selectedRecipe.averageRating?.toFixed(1) : "-"} />
                  <EditableDetail label="Recipe name">
                    <input value={draftName} onChange={(event) => setDraftName(event.target.value)} maxLength={160} />
                  </EditableDetail>
                  <EditableDetail label="Meal type">
                    <select value={draftMealType} onChange={(event) => setDraftMealType(event.target.value)}>
                      <option value="">None</option>
                      {MEAL_TYPES.map((value) => <option key={value} value={value}>{humanizeFeature(value)}</option>)}
                    </select>
                  </EditableDetail>
                  <EditableDetail label="Region">
                    <select value={draftMarketRegion} onChange={(event) => setDraftMarketRegion(event.target.value)}>
                      <option value="">None</option>
                      {MARKET_REGIONS.map((value) => <option key={value} value={value}>{value}</option>)}
                    </select>
                  </EditableDetail>
                  <EditableDetail label="Language">
                    <input value={draftLanguage} onChange={(event) => setDraftLanguage(event.target.value)} maxLength={12} placeholder="en / tr" />
                  </EditableDetail>
                  <EditableDetail label="Total yield (g)">
                    <input type="number" min="0.01" step="0.01" value={draftTotalYieldGrams} onChange={(event) => setDraftTotalYieldGrams(event.target.value)} />
                  </EditableDetail>
                  <EditableDetail label="Default serving (g)">
                    <input type="number" min="0.01" step="0.01" value={draftDefaultServingGrams} onChange={(event) => setDraftDefaultServingGrams(event.target.value)} />
                  </EditableDetail>
                  <EditableDetail label="Serving count">
                    <input type="number" min="1" step="1" value={draftServingCount} onChange={(event) => setDraftServingCount(event.target.value)} />
                  </EditableDetail>
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
                <EditableDetail label="Description">
                  <textarea value={draftDescription} onChange={(event) => setDraftDescription(event.target.value)} maxLength={1000} placeholder="Recipe description" />
                </EditableDetail>
                </section>}
                {recipeDetailTab === "content" && <section className="recipe-review-pane recipe-review-content">
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
                </section>}
                {recipeDetailTab === "moderation" && <section className="recipe-review-pane recipe-review-moderation">
                  <div className="recipe-review-decision-grid">
                    <article><span>{tr ? "Hedef doğrulama" : "Target verification"}</span><strong>{humanizeFeature(draftStatus || selectedRecipe.verificationStatus)}</strong></article>
                    <article><span>{tr ? "Hedef görünürlük" : "Target visibility"}</span><strong>{humanizeFeature(draftVisibility || selectedRecipe.visibility)}</strong></article>
                    <article><span>{tr ? "Arşiv durumu" : "Archive state"}</span><strong>{draftArchived === "true" ? (tr ? "Arşivlenecek" : "Archived") : (tr ? "Aktif" : "Active")}</strong></article>
                  </div>
                <EditableDetail label="Review note">
                  <textarea value={reviewNote} onChange={(event) => setReviewNote(event.target.value)} placeholder="Required for rejection; optional for approval" />
                </EditableDetail>
                  <p className="recipe-review-moderation-hint">{tr ? "Hazır karar düğmeleri alanları doldurur; değişiklik yalnızca Kaydet ile uygulanır." : "Decision presets prepare the fields; changes are applied only when you save."}</p>
                </section>}
              </div>
            </div>
            <footer className="modal-actions">
              <button className="ghost-button" onClick={closeRecipe} type="button">{tr ? "İptal" : "Cancel"}</button>
              {(selectedRecipe.visibility === "PUBLIC_ADMIN" || draftVisibility === "PUBLIC_ADMIN") && !selectedRecipe.archived && (
                <button className="ghost-button danger-text" disabled={saving} onClick={removeRecipeFromDiscover} type="button">
                    {tr ? "Keşfet'ten kaldır" : "Remove from Discover"}
                </button>
              )}
              <button className="ghost-button danger-text" disabled={saving} onClick={toggleRecipeArchive} type="button">
                {selectedRecipe.archived ? (tr ? "Özel olarak geri yükle" : "Restore as private") : (tr ? "Tarifi arşivle" : "Archive recipe")}
              </button>
              <button
                className="ghost-button danger-text"
                disabled={saving}
                onClick={() => {
                  setRecipeDetailTab("moderation");
                  setDraftStatus("REJECTED");
                  setDraftVisibility("PRIVATE");
                  setDraftArchived("false");
                  setDraftImageStatus(draftImageStatus || "REJECTED");
                  setReviewNote(reviewNote || "Rejected from admin recipe review.");
                }}
                type="button"
              >
                {tr ? "Reddetmeye hazırla" : "Prepare rejection"}
              </button>
              <button
                className="ghost-button"
                disabled={saving}
                onClick={() => {
                  setRecipeDetailTab("moderation");
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
                {tr ? "Yayın onayına hazırla" : "Prepare public approval"}
              </button>
              <button className="primary-button" disabled={saving} onClick={saveRecipeReview} type="button">{saving ? (tr ? "Kaydediliyor…" : "Saving...") : (tr ? "İncelemeyi kaydet" : "Save review")}</button>
            </footer>
          </section>
        </div>
      )}
    </div>
  );
}

export function RecipeEngagementCell({ recipe }: { recipe: AdminRecipe }) {
  const average = (recipe.averageRating ?? 0) > 0 ? recipe.averageRating?.toFixed(1) : "-";
  return (
    <div className="table-stack">
      <span>{average} avg | {formatValue(recipe.ratingCount)} ratings</span>
      <small>{formatValue(recipe.savedCount)} saved | {formatValue(recipe.favoriteCount)} favorites</small>
    </div>
  );
}

export function buildRecipeImportPath(filters: {
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

export function buildRecipeAdminPath(filters: {
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

export function productIngredientLabel(item: FoodProduct): string {
  const parts = [
    item.id ? `#${item.id}` : null,
    item.brand || null,
    item.barcode || null,
    item.marketRegion || null,
    item.catalogType || null,
    item.preparationState || null,
    item.publicationStatus || null,
    item.verificationStatus || null
  ].filter(Boolean);
  return parts.length ? parts.join(" | ") : "Product selected";
}

export async function searchAdminRecipeMappingProducts(searchText: string, preferredRegion?: string): Promise<FoodProduct[]> {
  const verificationStatuses = ["VERIFIED", "NEEDS_REVIEW", "RAW_IMPORTED"];
  const pages = await Promise.all(verificationStatuses.map((verificationStatus) => {
    const params = new URLSearchParams({
      query: searchText,
      verificationStatus,
      page: "0",
      size: "25"
    });
    return request<PageResponse<FoodProduct>>(`/api/v1/admin/products/review?${params.toString()}`);
  }));
  const productsById = new Map<number | string, FoodProduct>();
  for (const product of pages.flatMap((page) => page.content ?? [])) {
    productsById.set(product.id ?? `${product.sourceKey ?? ""}:${productName(product)}`, product);
  }
  const normalizedQuery = searchText.trim().toLocaleLowerCase();
  const verificationRank: Record<string, number> = { VERIFIED: 0, NEEDS_REVIEW: 1, RAW_IMPORTED: 2 };
  return [...productsById.values()]
    .sort((left, right) => {
      const leftExact = productName(left).trim().toLocaleLowerCase() === normalizedQuery ? 0 : 1;
      const rightExact = productName(right).trim().toLocaleLowerCase() === normalizedQuery ? 0 : 1;
      if (leftExact !== rightExact) return leftExact - rightExact;
      const leftRegion = left.marketRegion === preferredRegion ? 0 : left.marketRegion === "GLOBAL" ? 1 : 2;
      const rightRegion = right.marketRegion === preferredRegion ? 0 : right.marketRegion === "GLOBAL" ? 1 : 2;
      if (leftRegion !== rightRegion) return leftRegion - rightRegion;
      const leftVerification = verificationRank[left.verificationStatus ?? ""] ?? 3;
      const rightVerification = verificationRank[right.verificationStatus ?? ""] ?? 3;
      if (leftVerification !== rightVerification) return leftVerification - rightVerification;
      return productName(left).localeCompare(productName(right));
    })
    .slice(0, 20);
}

export function productNutritionLabel(item: FoodProduct): string {
  const nutrients = [
    typeof item.calories === "number" ? `${formatValue(item.calories)} kcal` : null,
    typeof item.protein === "number" ? `P ${formatValue(item.protein)}g` : null,
    typeof item.carbs === "number" ? `C ${formatValue(item.carbs)}g` : null,
    typeof item.fat === "number" ? `F ${formatValue(item.fat)}g` : null
  ].filter(Boolean);
  return nutrients.length ? `${nutrients.join(" | ")} per 100g` : "Nutrition values unavailable";
}
