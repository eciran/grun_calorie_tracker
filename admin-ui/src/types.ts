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

export type FeatureMatrixItem = {
  planType?: string;
  feature?: string;
  enabled?: boolean;
  monthlyLimit?: number;
  effectiveFrom?: string;
  updatedAt?: string;
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
  userEmail?: string;
  status?: string;
  requestType?: string;
  quotaConsumed?: number;
  quotaRefunded?: number;
  createdAt?: string;
  rejectionReason?: string;
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
