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
  activePlusSubscriptions?: number;
  activeProSubscriptions?: number;
  canceledSubscriptions?: number;
  refundedSubscriptions?: number;
  aiQuotaExhaustedSubscriptions?: number;
  failedSubscriptionProviderEvents?: number;
  subscriptionProviderEventsLast24Hours?: number;
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
  name?: string;
  productName?: string;
  brand?: string;
  imageUrl?: string;
  externalImageUrl?: string;
  displayImageUrl?: string;
  dataSource?: string;
  marketRegion?: string;
  verificationStatus?: string;
  imageSource?: string;
  imageStatus?: string;
  catalogType?: string;
  usageCount?: number;
  qualityScore?: number;
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
export type ProductQualitySuggestion = {
  id?: number;
  foodItemId?: number;
  productName?: string;
  brand?: string;
  suggestionType?: string;
  source?: string;
  status?: string;
  confidenceScore?: number;
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

export type ProductQualitySuggestionScanResult = {
  scannedProducts?: number;
  createdSuggestions?: number;
  skippedExistingSuggestions?: number;
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

export type RecipeIngredient = {
  foodItemId?: number;
  foodName?: string;
  portionSize?: number;
  portionUnit?: string;
  normalizedPortionGrams?: number;
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
  monthlyLimit?: number;
  effectiveFrom?: string;
  updatedAt?: string;
};


export type SubscriptionFeatureAccess = {
  planType?: string;
  plan?: string;
  activeEntitlement?: boolean;
  aiMealDrafts?: boolean;
  aiWorkoutPlanner?: boolean;
  aiRecipeGeneration?: boolean;
  aiInsights?: boolean;
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

export type Notification = {
  id?: number;
  message?: string;
  type?: string;
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
