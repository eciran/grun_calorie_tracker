export type DashboardSummary = {
  totalUsers?: number;
  adminUsers?: number;
  standardUsers?: number;
  proUsers?: number;
  totalProducts?: number;
  verifiedProducts?: number;
  rawImportedProducts?: number;
  needsReviewProducts?: number;
  rejectedProducts?: number;
  reviewQueueProducts?: number;
  pendingRecipeApprovals?: number;
  pendingRecipeImportCandidates?: number;
  openRecipeReports?: number;
  openProductCorrectionSuggestions?: number;
  openProductQualitySuggestions?: number;
  refundableAiRequests?: number;
  totalAdminApprovalItems?: number;
  activePlusSubscriptions?: number;
  activeProSubscriptions?: number;
  canceledSubscriptions?: number;
  refundedSubscriptions?: number;
  aiQuotaExhaustedSubscriptions?: number;
  failedSubscriptionProviderEvents?: number;
  subscriptionProviderEventsLast24Hours?: number;
  aiRequestsLast7Days?: number;
  aiConfirmedLast7Days?: number;
  aiRejectedLast7Days?: number;
  aiFailedLast7Days?: number;
  aiRejectionReasonsLast7Days?: Record<string, number>;
};

export type GrowthKpi = {
  key: string;
  label: string;
  value: number;
  unit: "COUNT" | "PERCENT";
  previousValue?: number | null;
  changePercent?: number | null;
  comparisonAvailable: boolean;
  dataStatus: "COMPLETE" | "PARTIAL" | "UNAVAILABLE";
  detail: string;
  targetSection?: string;
};

export type GrowthTrendPoint = {
  date: string;
  registrations: number;
  activeUsers: number;
};

export type GrowthFunnelStep = {
  key: string;
  label: string;
  users: number;
  conversionFromRegistrationPercent: number;
  dataStatus: "COMPLETE" | "PARTIAL" | "UNAVAILABLE";
  targetSection?: string;
};

export type DashboardGrowth = {
  from: string;
  to: string;
  previousFrom: string;
  previousTo: string;
  timeZone: string;
  generatedAt: string;
  rangeDays: number;
  legacyUsersWithoutRegistrationDate: number;
  registrationCoveragePercent: number;
  kpis: GrowthKpi[];
  daily: GrowthTrendPoint[];
  funnel: GrowthFunnelStep[];
  planDistribution: Record<string, number>;
  regionDistribution: Record<string, number>;
  languageDistribution: Record<string, number>;
};

export type SystemHealth = Record<string, unknown>;

export type UserProfile = {
  id?: number;
  email?: string;
  name?: string;
  age?: number;
  gender?: string;
  height?: number;
  weight?: number;
  bmi?: number;
  bodyFat?: number;
  role?: string;
  emailVerified?: boolean;
  passwordSet?: boolean;
  marketRegion?: string;
  preferredLanguage?: string;
  goalRecalculationRecommended?: boolean;
  goalRecalculationReason?: string;
  accountEnabled?: boolean;
  accountLocked?: boolean;
  statusReason?: string;
  createdAt?: string;
  emailVerifiedAt?: string;
  lastLoginAt?: string;
  lastActiveAt?: string;
};

export type AdminUserSupportNote = {
  id?: number;
  note?: string;
  tags?: string[];
  createdBy?: string;
  createdAt?: string;
};

export type AdminCustomer360 = {
  profile: UserProfile;
  subscription: {
    plan?: string; status?: string; billingPeriod?: string; startDate?: string; endDate?: string;
    autoRenew?: boolean; aiMonthlyQuota?: number; aiUsedThisPeriod?: number;
    aiAddonRemaining?: number; aiAddonExpiresAt?: string; activeFeatures?: string[];
  };
  ai: {
    totalRequests?: number; lastRequestAt?: string; recentStatusCounts?: Record<string, number>;
    recentRequestTypeCounts?: Record<string, number>; recentSampleSize?: number;
  };
  notifications: {
    total?: number; unread?: number;
    recent?: Array<{ id?: number; type?: string; severity?: string; source?: string; read?: boolean; createdAt?: string }>;
  };
  security: {
    activeSessions?: number;
    recentEvents?: Array<{ id?: number; eventType?: string; provider?: string; resultCode?: string; createdAt?: string }>;
  };
  consent: {
    total?: number;
    recent?: Array<{ id?: number; consentType?: string; version?: string; status?: string; source?: string; createdAt?: string }>;
  };
  activity: {
    foodLogCount?: number; lastFoodLogAt?: string; productEventCount?: number;
    recentProductEvents?: Array<{ id?: number; eventType?: string; surface?: string; createdAt?: string }>;
  };
  supportNotes?: AdminUserSupportNote[];
};

export type AdminAchievementDefinition = {
  id?: number;
  code?: string;
  title?: string;
  description?: string;
  metricKey?: string;
  category?: string;
  tier?: string;
  targetValue?: number;
  active?: boolean;
  sortOrder?: number;
  createdAt?: string;
};

export type AdminAchievementMetrics = {
  metricKeys?: string[];
};

export type FoodProduct = {
  id?: number;
  barcode?: string;
  normalizedBarcode?: string;
  sourceKey?: string;
  canonicalFoodKey?: string;
  name?: string;
  productName?: string;
  canonicalName?: string;
  displayName?: string;
  shortDisplayName?: string;
  brand?: string;
  imageUrl?: string;
  externalImageUrl?: string;
  displayImageUrl?: string;
  dataSource?: string;
  marketRegion?: string;
  preparationState?: string;
  verificationStatus?: string;
  imageSource?: string;
  imageStatus?: string;
  catalogType?: string;
  usageCount?: number;
  qualityScore?: number;
  confidenceScore?: number;
  autoApprovedForCatalog?: boolean;
  reviewPriority?: number;
  lastExternalSyncAt?: string;
  lastReviewedAt?: string;
  reviewedBy?: string;
  calories?: number;
  protein?: number;
  carbs?: number;
  fat?: number;
  fiber?: number;
  sugar?: number;
  sodium?: number;
  potassium?: number;
  cholesterol?: number;
  calcium?: number;
  iron?: number;
  magnesium?: number;
  zinc?: number;
  vitaminA?: number;
  vitaminC?: number;
  vitaminD?: number;
  vitaminE?: number;
  vitaminB12?: number;
  saturatedFat?: number;
  transFat?: number;
  sugarAlcohol?: number;
  servingSize?: number;
  servingUnit?: string;
  ingredientsText?: string;
  allergens?: string;
  nutriScore?: string;
  custom?: boolean;
};
export type FoodCanonicalCandidateAssessment = {
  product?: FoodProduct;
  primaryEligible?: boolean;
  eligibilityIssues?: string[];
  recommended?: boolean;
};

export type FoodCanonicalDuplicateGroup = {
  canonicalFoodKey?: string;
  productCount?: number;
  products?: FoodProduct[];
  candidateAssessments?: FoodCanonicalCandidateAssessment[];
  resolved?: boolean;
  resolutionState?: "UNRESOLVED" | "RESOLVED" | "STALE" | "NEEDS_REVIEW";
  resolutionStatusReason?: string;
  primaryProductId?: number;
  recommendedPrimaryProductId?: number;
  resolvedBy?: string;
  resolvedAt?: string;
};

export type FoodCanonicalDuplicateGroupPage = {
  content?: FoodCanonicalDuplicateGroup[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
  first?: boolean;
  last?: boolean;
};
export type ProductQualitySuggestion = {
  id?: number;
  foodItemId?: number;
  productName?: string;
  brand?: string;
  suggestionType?: string;
  source?: string;
  status?: string;
  confidenceScore?: number;
  fieldName?: string;
  currentValue?: string;
  suggestedValue?: string;
  reason?: string;
  createdAt?: string;
  reviewedAt?: string;
  reviewedBy?: string;
};

export type ProductQualitySuggestionPage = {
  content?: ProductQualitySuggestion[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
};


export type ProductQualityAiSettings = {
  enabled?: boolean;
  maxProductsPerRun?: number;
  dailyProductLimit?: number;
  monthlyProductLimit?: number;
  forceRescanAllowed?: boolean;
  usedToday?: number;
  usedThisMonth?: number;
  remainingToday?: number;
  remainingThisMonth?: number;
  adminNote?: string;
  updatedAt?: string;
  updatedBy?: string;
};
export type AdminProductQualityAiValidationResult = {
  scanRunId?: number;
  requestedProducts?: number;
  validatedProducts?: number;
  createdSuggestions?: number;
  skippedExistingSuggestions?: number;
  skippedPreviouslyValidatedProducts?: number;
  effectiveLimit?: number;
};

export type ProductQualitySuggestionScanResult = {
  scanRunId?: number;
  scannedProducts?: number;
  createdSuggestions?: number;
  skippedExistingSuggestions?: number;
  skippedPreviouslyValidatedProducts?: number;
  validatedProducts?: number;
  effectiveLimit?: number;
  forceRescan?: boolean;
};

export type ProductQualityScanRun = {
  id?: number;
  source?: string;
  triggerType?: string;
  status?: string;
  marketRegion?: string;
  requestedLimit?: number;
  effectiveLimit?: number;
  forceRescan?: boolean;
  scannedProducts?: number;
  createdSuggestions?: number;
  skippedExistingSuggestions?: number;
  skippedPreviouslyValidatedProducts?: number;
  validatedProducts?: number;
  triggeredBy?: string;
  startedAt?: string;
  completedAt?: string;
  errorMessage?: string;
};


export type ProductQualityScanRunItem = {
  id?: number;
  foodItemId?: number;
  productName?: string;
  brand?: string;
  status?: string;
  suggestionType?: string;
  fieldName?: string;
  suggestedValue?: string;
  reason?: string;
  confidenceScore?: number;
  note?: string;
};

export type ProductQualityScanRunDetail = {
  run?: ProductQualityScanRun;
  items?: ProductQualityScanRunItem[];
};
export type ProductQualityScanRunPage = {
  content?: ProductQualityScanRun[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
};

export type FoodSearchAlias = {
  id?: number;
  foodItemId?: number;
  alias?: string;
  normalizedAlias?: string;
  language?: string;
  aliasType?: string;
  source?: string;
  active?: boolean;
  createdAt?: string;
};


export type FoodProductEvidence = {
  evidenceId?: number;
  productId?: number;
  provider?: string;
  externalId?: string;
  fieldName?: string;
  numericValue?: number;
  basis?: string;
  confidenceScore?: number;
  observedAt?: string;
  stale?: boolean;
  sourceVersion?: string;
  reviewerIdentity?: string;
};

export type FoodProductEvidenceComparison = {
  fieldName?: string;
  basis?: string;
  state?: string;
  preferredEvidenceId?: number;
  maximumDifference?: number;
  reason?: string;
  evidenceIds?: number[];
};

export type AdminProductQualityWorkbench = {
  product?: FoodProduct;
  localizations?: Array<{ id?: number; language?: string; displayName?: string; shortDisplayName?: string; source?: string; active?: boolean }>;
  aliases?: FoodSearchAlias[];
  servingOptions?: Array<{
    id?: number; label?: string; unitType?: string; quantity?: number; gramWeight?: number; mlVolume?: number;
    defaultOption?: boolean; source?: string; qualityStatus?: string;
    localizations?: Array<{ id?: number; language?: string; label?: string; source?: string; active?: boolean }>;
  }>;
  evidence?: { evidence?: FoodProductEvidence[]; comparisons?: FoodProductEvidenceComparison[] };
  qualityIssues?: Array<{
    id?: number; foodItemId?: number; issueType?: string; identifier?: string; reason?: string; resolved?: boolean;
    firstDetectedAt?: string; lastDetectedAt?: string; resolvedAt?: string; resolvedBy?: string;
  }>;
  suggestions?: ProductQualitySuggestion[];
  canonicalDuplicate?: {
    canonicalFoodKey?: string; resolvedPrimaryProductId?: number;
    candidates?: Array<{
      productId?: number; displayName?: string; brand?: string; dataSource?: string; marketRegion?: string;
      preparationState?: string; verificationStatus?: string; qualityScore?: number; confidenceScore?: number; usageCount?: number;
    }>;
  };
  audit?: Array<{
    id?: number; foodItemId?: number; reviewedBy?: string; actionType?: string; fieldName?: string;
    oldValue?: string; newValue?: string; note?: string; createdAt?: string;
  }>;
};
export type RecipeStep = {
  stepNumber?: number;
  instruction?: string;
};

export type RecipeIngredient = {
  foodItemId?: number;
  foodName?: string;
  portionSize?: number;
  portionUnit?: string;
  normalizedPortionGrams?: number;
};

export type AdminRecipeOperationsAnalytics = {
  windowDays?: number;
  totalRecipes?: number;
  activeRecipes?: number;
  pendingReview?: number;
  publicVerified?: number;
  rejected?: number;
  archived?: number;
  overdueReview?: number;
  unassignedReview?: number;
  verificationStatuses?: Array<{ name?: string; count?: number }>;
  visibilityStatuses?: Array<{ name?: string; count?: number }>;
  pendingAgeBands?: Array<{ name?: string; count?: number }>;
  importStatuses?: Array<{ name?: string; count?: number }>;
  engagement?: { saved?: number; favorite?: number; rated?: number; averageRating?: number };
  submissionTrend?: Array<{ date?: string; createdRecipes?: number }>;
};

export type AdminRecipe = {
  id?: number;
  ownerUserId?: number;
  ownerEmail?: string;
  name?: string;
  description?: string;
  mealType?: string;
  visibility?: string;
  verificationStatus?: string;
  marketRegion?: string;
  language?: string;
  imageUrl?: string;
  imageSource?: string;
  imageStatus?: string;
  imageReviewNote?: string;
  imageReviewedBy?: string;
  imageReviewedAt?: string;
  totalYieldGrams?: number;
  defaultServingGrams?: number;
  servingCount?: number;
  calories?: number;
  protein?: number;
  carbs?: number;
  fat?: number;
  fiber?: number;
  sugar?: number;
  sodium?: number;
  savedCount?: number;
  favoriteCount?: number;
  ratingCount?: number;
  averageRating?: number;
  categories?: string[];
  archived?: boolean;
  ingredientCount?: number;
  createdAt?: string;
  updatedAt?: string;
  ingredients?: RecipeIngredient[];
  cookingSteps?: RecipeStep[];
};


export type AdminRecipeImportCookingStep = {
  stepNumber?: number;
  instruction?: string;
};

export type AdminRecipeImportIngredient = {
  index?: number;
  foodItemId?: number;
  ingredientName?: string;
  imageUrl?: string;
  portionSize?: number;
  portionUnit?: string;
  estimatedGrams?: number;
};
export type AdminRecipeImportCandidate = {
  id?: number;
  batchId?: string;
  sourceKey?: string;
  sourceTitle?: string;
  sourceUrl?: string;
  sourceRevisionUrl?: string;
  license?: string;
  recommendedImportStatus?: string;
  status?: string;
  recipeName?: string;
  mealType?: string;
  marketRegion?: string;
  language?: string;
  imageUrl?: string;
  ingredientCount?: number;
  unresolvedIngredientCount?: number;
  validationIssues?: string;
  createdRecipeId?: number;
  reviewedBy?: string;
  reviewedAt?: string;
  reviewNote?: string;
  createdAt?: string;
  updatedAt?: string;
  ingredients?: AdminRecipeImportIngredient[];
  cookingSteps?: AdminRecipeImportCookingStep[];
};

export type AdminRecipeImportResult = {
  batchId?: string;
  totalCandidates?: number;
  createdCandidates?: number;
  skippedDuplicates?: number;
  failedCandidates?: number;
  candidates?: AdminRecipeImportCandidate[];
};
export type FeatureMatrixItem = {
  planType?: string;
  feature?: string;
  enabled?: boolean;
  aiCreditCost?: number;
  monthlyLimit?: number;
  effectiveFrom?: string;
  updatedAt?: string;
};


export type AiCreditPricingPolicy = {
  feature: string;
  pricingMode: "FIXED" | "NUTRITION_COMPLEXITY" | "WORKOUT_COMPLEXITY";
  baseCreditCost: number;
  includedUnits: number;
  unitsPerAdditionalCredit: number;
  contextSurcharge: number;
  maxCreditCost: number;
  updatedAt?: string;
};

export type SubscriptionFeatureAccess = {
  planType?: string;
  barcodeScanner?: boolean;
  manualFoodLogging?: boolean;
  foodDiary?: boolean;
  weightProgress?: boolean;
  waterTracking?: boolean;
  workoutLogging?: boolean;
  savedMealTemplates?: boolean;
  recipeBuilder?: boolean;
  publicRecipeLibrary?: boolean;
  nextMealSuggestions?: boolean;
  advancedMacroTargets?: boolean;
  micronutrientDetails?: boolean;
  micronutrientAnalytics?: boolean;
  dataExport?: boolean;
  fastingBasic?: boolean;
  fastingAdvanced?: boolean;
  plan?: string;
  activeEntitlement?: boolean;
  aiMealDrafts?: boolean;
  aiMealDraftsCreditCost?: number;
  aiWorkoutPlanner?: boolean;
  aiWorkoutPlannerCreditCost?: number;
  aiRecipeGeneration?: boolean;
  aiRecipeGenerationCreditCost?: number;
  aiMealPreparationGuide?: boolean;
  aiMealPreparationGuideCreditCost?: number;
  aiNutritionPlan?: boolean;
  aiNutritionPlanBaseCreditCost?: number;
  aiInsights?: boolean;
  aiInsightsCreditCost?: number;
  aiCreditCosts?: Record<string, number>;
  healthIntegration?: boolean;
  advancedAnalytics?: boolean;
  adFree?: boolean;
  customFoodLibrary?: boolean;
  aiMonthlyQuota?: number;
  aiAddonQuota?: number;
  aiRemainingThisPeriod?: number;
};

export type SubscriptionDto = {
  planType?: string;
  plan?: string;
  status?: string;
  billingPeriod?: string;
  startDate?: string;
  endDate?: string;
  quotaResetDate?: string;
  aiAddonQuotaExpiresAt?: string;
  aiMonthlyQuota?: number;
  aiAddonQuota?: number;
  aiTotalQuotaThisPeriod?: number;
  aiUsedThisPeriod?: number;
  aiBaseRemainingThisPeriod?: number;
  aiAddonRemainingThisPeriod?: number;
  aiRemainingThisPeriod?: number;
  activeEntitlement?: boolean;
  aiAccessAllowed?: boolean;
  upgradeRecommended?: boolean;
  autoRenew?: boolean;
  provider?: string;
  providerProductId?: string;
};

export type RevenueCatConfigStatus = {
  webhookAuthorizationConfigured?: boolean;
  strictProductMapping?: boolean;
  productionReady?: boolean;
  apiEnabled?: boolean;
  apiSecretConfigured?: boolean;
  apiProjectConfigured?: boolean;
  apiBaseUrl?: string;
  apiCurrency?: string;
  missingRequiredConfig?: string[];
  warnings?: string[];
  plusEntitlements?: string[];
  proEntitlements?: string[];
  plusProductIds?: string[];
  proProductIds?: string[];
  aiAddonQuotas?: Record<string, number>;
  aiAddonValidityDays?: Record<string, number>;
  defaultAiAddonValidityDays?: number;
};

export type RevenueCatMetricCard = {
  key?: string;
  label?: string;
  value?: string;
  unit?: string;
  description?: string;
};

export type RevenueCatChartPoint = {
  date?: string;
  value?: number;
};

export type RevenueCatChart = {
  chartName?: string;
  label?: string;
  environment?: string;
  currency?: string;
  providerReachable?: boolean;
  statusMessage?: string;
  points?: RevenueCatChartPoint[];
};

export type RevenueCatMonitoringOverview = {
  environment?: string;
  currency?: string;
  apiEnabled?: boolean;
  apiSecretConfigured?: boolean;
  apiProjectConfigured?: boolean;
  providerReachable?: boolean;
  statusMessage?: string;
  checkedAt?: string;
  metrics?: RevenueCatMetricCard[];
};

export type RevenueCatMonitoringCharts = {
  environment?: string;
  currency?: string;
  providerReachable?: boolean;
  statusMessage?: string;
  checkedAt?: string;
  charts?: RevenueCatChart[];
};

export type AuditEntry = {
  id?: number;
  adminEmail?: string;
  actionType?: string;
  targetType?: string;
  targetKey?: string;
  targetId?: string;
  oldValue?: string;
  newValue?: string;
  correlationId?: string;
  details?: string;
  createdAt?: string;
};

export type NotificationCampaign = {
  id?: number;
  name?: string;
  title?: string;
  message?: string;
  category?: "SYSTEM" | "MARKETING";
  channel?: "IN_APP" | "PUSH" | "IN_APP_AND_PUSH";
  status?: "DRAFT" | "SCHEDULED" | "PROCESSING" | "COMPLETED" | "CANCELLED" | "FAILED";
  targetRoute?: string;
  targetPlan?: string;
  targetRegion?: string;
  targetLanguage?: string;
  scheduledAt?: string;
  startedAt?: string;
  completedAt?: string;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
  estimatedAudience?: number;
  processedCount?: number;
  inAppCount?: number;
  pushSentCount?: number;
  pushSkippedCount?: number;
  pushFailedCount?: number;
  openedCount?: number;
  clickedCount?: number;
  dismissedCount?: number;
  convertedCount?: number;
  suppressedCount?: number;
  frequencyCapHours?: number;
  frequencyCapMax?: number;
  failureMessage?: string;
};


export type NotificationCampaignSummary = {
  windowDays?: number;
  from?: string;
  to?: string;
  campaignCount?: number;
  estimatedAudience?: number;
  processedCount?: number;
  deliveredCount?: number;
  suppressedCount?: number;
  failedRecipientCount?: number;
  openedCount?: number;
  clickedCount?: number;
  dismissedCount?: number;
  convertedCount?: number;
  pushSentCount?: number;
  pushSkippedCount?: number;
  pushFailedCount?: number;
  campaignStatuses?: Array<{ name?: string; count?: number }>;
  recipientStatuses?: Array<{ name?: string; count?: number }>;
};

export type NotificationCampaignRecipient = {
  id?: number;
  userReference?: string;
  status?: "CREATED" | "DELIVERED" | "SUPPRESSED" | "FAILED";
  pushSent?: number;
  pushFailed?: number;
  suppressionReason?: string;
  processedAt?: string;
  openedAt?: string;
  clickedAt?: string;
  dismissedAt?: string;
  convertedAt?: string;
};

export type NotificationCampaignPreview = {
  campaignId?: number;
  estimatedAudience?: number;
  marketingConsentRequired?: boolean;
};
export type Notification = {
  id?: number;
  title?: string;
  message?: string;
  type?: string;
  severity?: string;
  source?: string;
  targetType?: string;
  targetId?: string;
  targetRoute?: string;
  read?: boolean;
  createdAt?: string;
};

export type AiMealDraft = {
  id?: number;
  requestId?: number;
  userId?: number;
  userEmail?: string;
  requestType?: string;
  provider?: string;
  model?: string;
  promptVersion?: string;
  status?: string;
  quotaConsumed?: boolean | number;
  quotaConsumedAmount?: number;
  quotaRefunded?: number;
  quotaRefundedAmount?: number;
  refundableAmount?: number;
  latencyMs?: number;
  totalTokens?: number;
  estimatedCost?: number;
  costCurrency?: string;
  createdAt?: string;
  rejectedAt?: string;
  rejectionReason?: string;
  rejectionFeedback?: string;
  quotaRefundReason?: string;
  quotaRefundedBy?: string;
  quotaRefundedAt?: string;
  quotaRefundDecision?: "PENDING" | "APPROVED" | "REJECTED";
  quotaRefundDecisionReason?: string;
  quotaRefundDecidedBy?: string;
  quotaRefundDecidedAt?: string;
};

export type AiRequestInspection = {
  requestId?: number;
  userId?: number;
  userEmail?: string;
  requestType?: string;
  provider?: string;
  model?: string;
  promptVersion?: string;
  status?: string;
  latencyMs?: number;
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
  estimatedCost?: number;
  costCurrency?: string;
  quotaConsumed?: boolean;
  quotaConsumedAmount?: number;
  quotaRefundedAmount?: number;
  rejectionReason?: string;
  rejectionFeedback?: string;
  quotaRefundDecision?: string;
  quotaRefundDecisionReason?: string;
  createdAt?: string;
  confirmedAt?: string;
  rejectedAt?: string;
  requestContext?: unknown;
  result?: unknown;
  confirmation?: unknown;
  correctionSummary?: string;
  failureSummary?: string;
};
export type AiMonitoringSummary = {
  generatedAt?: string;
  windowStart?: string;
  windowHours?: number;
  totalRequests?: number;
  draftCreated?: number;
  confirmed?: number;
  rejected?: number;
  failed?: number;
  failureRate?: number;
  rejectionRate?: number;
  successRate?: number;
  timeoutCount?: number;
  latencyP50Ms?: number;
  latencyP95Ms?: number;
  latencyP99Ms?: number;
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
  quotaConsumedAmount?: number;
  quotaRefundedAmount?: number;
  estimatedCostByCurrency?: Record<string, number>;
  subscriptionRevenueByCurrency?: Record<string, number>;
  costToRevenueRatioByCurrency?: Record<string, number>;
  providerModels?: AiProviderModelMetric[];
  requestStatuses?: AiRequestStatusMetric[];
  segments?: AiOperationsSegmentMetric[];
  attentionRequired?: boolean;
  alerts?: { code?: string; severity?: string; message?: string; requestType?: string; currency?: string }[];
};

export type AiOperationsPolicy = {
  version?: number;
  circuitOpen?: boolean;
  failureRateThreshold?: number;
  rejectionRateThreshold?: number;
  maxTokensPer24Hours?: number;
  maxCostPer24Hours?: number;
  costCurrency?: string;
  activeModel?: string;
  activePromptVersion?: string;
  rollbackAvailable?: boolean;
  updatedBy?: string;
  updatedAt?: string;
};

export type AiOperationsSegmentMetric = {
  requestType?: string;
  plan?: string;
  region?: string;
  language?: string;
  costCurrency?: string;
  requestCount?: number;
  failedCount?: number;
  rejectedCount?: number;
  estimatedCost?: number;
};
export type AiProviderModelMetric = {
  provider?: string;
  model?: string;
  promptVersion?: string;
  costCurrency?: string;
  requestCount?: number;
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
  estimatedCost?: number;
  quotaConsumedAmount?: number;
  quotaRefundedAmount?: number;
};

export type AiRequestStatusMetric = {
  requestType?: string;
  status?: string;
  requestCount?: number;
  totalTokens?: number;
  quotaConsumedAmount?: number;
  quotaRefundedAmount?: number;
};

export type AiQuotaRefundResponse = {
  requestId?: number;
  userId?: number;
  status?: string;
  quotaConsumedAmount?: number;
  quotaRefundedAmount?: number;
  refundedNow?: number;
  quotaRefundReason?: string;
  quotaRefundedBy?: string;
  quotaRefundedAt?: string;
  quotaRefundDecision?: "PENDING" | "APPROVED" | "REJECTED";
  quotaRefundDecisionReason?: string;
  quotaRefundDecidedBy?: string;
  quotaRefundDecidedAt?: string;
  subscription?: SubscriptionDto;
};
export type RetentionPolicy = {
  id?: number;
  policyKey?: string;
  retentionDays?: number;
  legalBasis?: string;
  description?: string;
  active?: boolean;
  updatedBy?: string;
  updatedAt?: string;
};

export type SubscriptionProviderEvent = {
  id?: number;
  provider?: string;
  providerEventId?: string;
  providerAppUserId?: string;
  eventType?: string;
  productId?: string;
  entitlementIds?: string;
  transactionId?: string;
  originalTransactionId?: string;
  userId?: number;
  userEmail?: string;
  status?: string;
  processingError?: string;
  receivedAt?: string;
  processedAt?: string;
  rawPayload?: string;
};

export type SubscriptionProviderEventPage = {
  content?: SubscriptionProviderEvent[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
  first?: boolean;
  last?: boolean;
};

export type AdminMailEvent = {
  event?: string;
  email?: string;
  subject?: string;
  messageId?: string;
  date?: string;
  reason?: string;
};

export type AdminMailMonitoring = {
  provider?: string;
  apiKeyConfigured?: boolean;
  providerReachable?: boolean;
  providerBaseUrl?: string;
  fromEmail?: string;
  fromName?: string;
  statusMessage?: string;
  counters?: Record<string, number>;
  recentEvents?: AdminMailEvent[];
  checkedAt?: string;
};

export type AdminPushMonitoring = {
  enabled?: boolean;
  provider?: string;
  activeTokenCount?: number;
  activeTokensByProvider?: Record<string, number>;
  sentLast24h?: number;
  failedLast24h?: number;
  expoConfigured?: boolean;
  fcmConfigured?: boolean;
  oneSignalConfigured?: boolean;
};

export type AdminEngagementMetric = {
  started?: number;
  completed?: number;
  firstCompletions?: number;
  failed?: number;
  uniqueUsers?: number;
  completionRate?: number;
  averageDurationMs?: number;
};

export type AdminEngagementAnalytics = {
  eventContractVersion?: number;
  hours?: number;
  since?: string;
  generatedAt?: string;
  filters?: { region?: string; language?: string; plan?: string };
  onboarding?: {
    started?: number;
    stepViewed?: number;
    stepCompleted?: number;
    stepFailed?: number;
    resumed?: number;
    previewed?: number;
    completed?: number;
    abandoned?: number;
    completionRate?: number;
    failureRate?: number;
  rejectionRate?: number;
  successRate?: number;
  timeoutCount?: number;
  latencyP50Ms?: number;
  latencyP95Ms?: number;
  latencyP99Ms?: number;
    averageCompletionDurationMs?: number;
  };
  search?: {
    searches?: number;
    zeroResultSearches?: number;
    selectedSearches?: number;
    noSelectionSearches?: number;
    zeroResultRate?: number;
    selectionRate?: number;
    planFilterApplied?: boolean;
    topZeroResultQueries?: Array<{ query?: string; searches?: number }>;
  };
  foodLogging?: AdminEngagementMetric;
  barcode?: AdminEngagementMetric;
  featureAdoption?: Array<{
    feature?: string;
    events?: number;
    uniqueUsers?: number;
    repeatEvents?: number;
    averageDurationMs?: number;
  }>;
  regionComparison?: Array<{ segment?: string; events?: number; uniqueUsers?: number }>;
  languageComparison?: Array<{ segment?: string; events?: number; uniqueUsers?: number }>;
  planComparison?: Array<{ segment?: string; events?: number; uniqueUsers?: number }>;
};

export type AdminTrackingModuleSummary = {
  module?: string;
  recordsLastRange?: number;
  activeUsersLastRange?: number;
  totalValueLastRange?: number;
  totalValueUnit?: string;
  configuredUsers?: number;
  reminderEnabledUsers?: number;
  activeNow?: number;
};

export type AdminTrackingTrendPoint = {
  date?: string;
  waterMl?: number;
  waterLogs?: number;
  waterUsers?: number;
  fastingMinutes?: number;
  fastingSessions?: number;
  fastingUsers?: number;
  steps?: number;
  stepRecords?: number;
  stepUsers?: number;
};

export type AdminTrackingSummary = {
  generatedAt?: string;
  rangeDays?: number;
  startDate?: string;
  endDate?: string;
  water?: AdminTrackingModuleSummary;
  fasting?: AdminTrackingModuleSummary;
  steps?: AdminTrackingModuleSummary;
  trends?: AdminTrackingTrendPoint[];
};

export type AdminBrevoSender = {
  id?: number;
  name?: string;
  email?: string;
  active?: boolean;
  dkimError?: boolean;
  spfError?: boolean;
  ips?: Array<Record<string, unknown>>;
};

export type AdminBrevoSenderList = {
  providerReachable?: boolean;
  statusMessage?: string;
  senders?: AdminBrevoSender[];
};
export type FoodProductContribution = {
  id?: number;
  submittedByUserId?: number;
  barcode?: string;
  productName?: string;
  brand?: string;
  marketRegion?: string;
  calories?: number;
  protein?: number;
  fat?: number;
  carbs?: number;
  fiber?: number;
  sugar?: number;
  sodium?: number;
  servingSizeGrams?: number;
  servingUnit?: string;
  evidenceUrl?: string;
  evidenceContentType?: string;
  evidenceSizeBytes?: number;
  evidenceChecksum?: string;
  evidenceRetrievedAt?: string;
  commercialUseAllowed?: boolean;
  persistentStorageAllowed?: boolean;
  status?: "PENDING_REVIEW" | "APPROVED" | "REJECTED";
  reviewNote?: string;
  reviewedAt?: string;
  createdAt?: string;
};
export type AdminPromotion = {
  id?: number;
  version?: number;
  code?: string;
  name?: string;
  description?: string;
  discountPercent?: number;
  status?: "DRAFT" | "ACTIVE" | "DEACTIVATED" | "EXPIRED";
  promoType?: "CAMPAIGN" | "INTRO_OFFER" | "WIN_BACK" | "SUPPORT_GRANT";
  active?: boolean;
  startAt?: string;
  endAt?: string;
  targetPlan?: "FREE" | "PLUS" | "PRO";
  targetProductId?: string;
  targetStore?: "ALL" | "REVENUECAT" | "APPLE_APP_STORE" | "GOOGLE_PLAY";
  targetRegion?: "GLOBAL" | "TR" | "UK_IE" | "EU";
  currency?: string;
  eligibilityRule?: string;
  perUserLimit?: number;
  globalLimit?: number;
  usedCount?: number;
  campaignKey?: string;
  providerOfferId?: string;
  providerProductId?: string;
  providerMappingReady?: boolean;
  createdBy?: string;
  createdAt?: string;
  updatedBy?: string;
  updatedAt?: string;
  deactivatedReason?: string;
};

export type AdminPromotionPage = {
  content?: AdminPromotion[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
  first?: boolean;
  last?: boolean;
};

export type AdminPromotionPreview = {
  promoId?: number;
  estimatedAudience?: number;
  providerMappingReady?: boolean;
  activationReady?: boolean;
  validationIssues?: string[];
};

export type AdminPromotionMetrics = {
  activePromos?: number;
  totalRedemptions?: number;
  convertedRedemptions?: number;
  rejectedRedemptions?: number;
  uniqueUsers?: number;
  revenueByCurrency?: Array<{ currency?: string; amountMinor?: number }>;
  conversionRate?: number;
rejectionRate?: number;
  duplicateAttempts?: number;
  limitRejections?: number;
  abuseSignals?: number;
};

export type AdminPromotionOperationsAnalytics = {
  windowDays?: number;
  promotionStatuses?: Array<{ name?: string; count?: number }>;
  promotionTypes?: Array<{ name?: string; count?: number }>;
  redemptionStatuses?: Array<{ name?: string; count?: number }>;
  rejectionCategories?: Array<{ name?: string; count?: number }>;
  redemptionTrend?: Array<{
    date?: string;
    attempts?: number;
    converted?: number;
    rejected?: number;
    duplicateAttempts?: number;
  }>;
};
export type AdminPromotionReconciliation = {
  promoId?: number; store?: string; mappingReady?: boolean; providerOfferId?: string; providerProductId?: string;
  observedProviderEvents?: number; lastObservedAt?: string; providerRoute?: string; issues?: string[]; entitlementGuardrail?: string;
};

export type AdminPromotionRedemption = {
  id?: number; promoId?: number; promoCode?: string; userId?: number; maskedUserEmail?: string;
  status?: "RESERVED" | "PROVIDER_VERIFIED" | "CONVERTED" | "REJECTED";
  providerEventReference?: string; amountMinor?: number; currency?: string; rejectionReason?: string;
  duplicateHits?: number; appliedAt?: string; convertedAt?: string; lastDuplicateAt?: string;
};

export type AdminPromotionRedemptionPage = {
  content?: AdminPromotionRedemption[]; page?: number; size?: number; totalElements?: number;
  totalPages?: number; first?: boolean; last?: boolean;
};

export type AdminAccessProfile = {
  userId?: number;
  email?: string;
  role?: string;
  permissions?: string[];
  mfaRequired?: boolean;
  mfaEnabled?: boolean;
};

export type AdminTeamMember = {
  id?: number;
  name?: string;
  email?: string;
  role?: string;
  enabled?: boolean;
  locked?: boolean;
  mfaEnabled?: boolean;
  activeSessions?: number;
  lastActiveAt?: string;
  roleUpdatedAt?: string;
};

export type AdminTeamPage = {
  content?: AdminTeamMember[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
  first?: boolean;
  last?: boolean;
};
export type CatalogTypeSummary = {
  total?: number; approved?: number; pendingReview?: number; missingMedia?: number;
  staleSource?: number; overdueReview?: number;
};

export type AdminCatalogSummary = {
  food?: CatalogTypeSummary;
  recipes?: CatalogTypeSummary;
  exercises?: CatalogTypeSummary;
  sources?: Array<{ source?: string; itemCount?: number; staleCount?: number; missingLicenseCount?: number }>;
};

export type AdminCatalogQualityAnalytics = {
  windowDays?: number;
  totalProducts?: number;
  validatedProducts?: number;
  averageQualityScore?: number;
  verificationStatuses?: Array<{ name?: string; count?: number }>;
  openIssueTypes?: Array<{ name?: string; count?: number }>;
  scanTrend?: Array<{
    date?: string;
    scannedProducts?: number;
    createdSuggestions?: number;
    validatedProducts?: number;
    failedRuns?: number;
  }>;
};

export type AdminCatalogImportJob = {
  jobKey?: string; catalogType?: string; source?: string; triggerType?: string; region?: string;
  status?: string; processedItems?: number; issueItems?: number; licenseEvidence?: string;
  retryable?: boolean; failureDetail?: string; startedAt?: string; completedAt?: string;
};

export type ExerciseCatalogItem = {
  id?: number; name?: string; metCode?: string; caloriesPerMinute?: number; description?: string;
  iconUrl?: string; primaryMuscleGroup?: string; secondaryMuscleGroups?: string; equipment?: string;
  difficulty?: string; instructions?: string; safetyNotes?: string; thumbnailUrl?: string; videoUrl?: string;
  animationUrl?: string; defaultMeasurementType?: string; allowedMeasurementTypes?: string[];
  aiEligible?: boolean; active?: boolean; techniqueReviewStatus?: string; techniqueReviewNote?: string;
  techniqueReviewedBy?: string; techniqueReviewedAt?: string; sourceName?: string; sourceUrl?: string;
  licenseName?: string; licenseUrl?: string; sourceLastRefreshedAt?: string; reviewAssignee?: string;
  reviewDueAt?: string; reviewClaimedAt?: string;
};

export type ExerciseCatalogPage = {
  content?: ExerciseCatalogItem[]; page?: number; size?: number; totalElements?: number;
  totalPages?: number; first?: boolean; last?: boolean;
};

export type RuntimeOperationsPolicy = {
  version: number;
  maintenanceEnabled: boolean;
  maintenanceMessage: string;
  releaseVersion: string;
  deploymentEnvironment: string;
  minimumIosVersion: string;
  minimumAndroidVersion: string;
  rolloutFeature: string;
  rolloutEnabled: boolean;
  rolloutPlan?: string | null;
  rolloutRegion?: string | null;
  rolloutSegment: string;
  rolloutPercentage: number;
  apiLatencyWarningMs: number;
  apiErrorRateThreshold: number;
  escalationTarget?: string | null;
  rollbackAvailable: boolean;
  updatedBy?: string;
  updatedAt?: string;
};

export type RuntimeApiMetrics = {
  requests: number;
  errors: number;
  errorRate: number;
  latencyP50Ms: number;
  latencyP95Ms: number;
  latencyP99Ms: number;
  rateLimited: number;
  authenticationFailures: number;
  authorizationFailures: number;
  latencyThresholdBreached: boolean;
  errorRateThresholdBreached: boolean;
  windowStartedAt?: string;
};

export type RuntimeOperationRecord = {
  id: number;
  recordType: string;
  status: string;
  operationKey: string;
  title: string;
  summary: string;
  startedAt?: string | null;
  completedAt?: string | null;
  nextRunAt?: string | null;
  retryable: boolean;
  retryCount: number;
  parentRecordId?: number | null;
  createdBy: string;
  createdAt: string;
};
export type SystemReliabilityApiTrendPoint = {
  bucket: string;
  requests: number;
  errors: number;
  errorRate: number;
  latencyP95Ms: number;
};

export type SystemReliabilityInfrastructureMetric = {
  component: string;
  status: string;
  latencyMs?: number | null;
  utilizationPercent?: number | null;
};

export type SystemReliabilityProviderMetric = {
  provider: string;
  status: string;
  attempts: number;
  successes: number;
  failures: number;
  successRate?: number | null;
};

export type SystemReliabilityOperationMetric = {
  recordType: string;
  total: number;
  succeeded: number;
  failed: number;
  deadLetters: number;
  open: number;
};

export type SystemReliabilityOperationTrendPoint = {
  date: string;
  succeeded: number;
  failed: number;
  deadLetters: number;
};

export type SystemReliabilityAnalytics = {
  windowHours: number;
  generatedAt: string;
  apiWindowStartedAt: string;
  apiTrend: SystemReliabilityApiTrendPoint[];
  infrastructure: SystemReliabilityInfrastructureMetric[];
  providers: SystemReliabilityProviderMetric[];
  operations: SystemReliabilityOperationMetric[];
  operationTrend: SystemReliabilityOperationTrendPoint[];
};