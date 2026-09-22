package com.grun.calorietracker.service.support;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.dto.CustomFoodRequestDto;
import com.grun.calorietracker.enums.FoodNutritionReferenceUnit;

/** Backward-compatible extension of the persisted review submission contract. Values are per 100 g or 100 ml. */
public final class ManualContributionNutrition {
  private ManualContributionNutrition() {}
  public static Map<String,Object> parse(String json) {
    try { return new ObjectMapper().readValue(json, new TypeReference<Map<String,Object>>() {}); }
    catch (Exception e) { throw new IllegalArgumentException("Invalid submitted nutrition", e); }
  }
  public static FoodNutritionReferenceUnit reference(Map<String,Object> fields) {
    Object value = fields.get("nutritionReferenceUnit");
    if (value == null) return FoodNutritionReferenceUnit.PER_100G;
    try { return FoodNutritionReferenceUnit.valueOf(String.valueOf(value)); }
    catch (Exception e) { throw new IllegalArgumentException("Invalid nutrition reference unit"); }
  }
  public static Double nutrient(Map<String,Object> fields, String key) {
    Object raw = fields.get(key);
    if (raw == null) return null;
    double max = key.equals("saturatedFat") || key.equals("transFat") || key.equals("sugarAlcohol") ? (reference(fields) == FoodNutritionReferenceUnit.PER_100ML ? 1000 : 100) : key.equals("vitaminA") || key.equals("vitaminD") || key.equals("vitaminB12") ? 100000000 : 100000;
    if (!(raw instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() < 0 || n.doubleValue() > max)
      throw new IllegalArgumentException("Invalid nutrient: " + key);
    return ((Number)raw).doubleValue();
  }
  public static double serving(Map<String,Object> fields) {
    Object raw = fields.getOrDefault("servingAmount", fields.getOrDefault("servingSizeGrams", 100));
    if (!(raw instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() <= 0 || n.doubleValue() > 100000)
      throw new IllegalArgumentException("Invalid serving amount");
    return ((Number)raw).doubleValue();
  }
  public static void validate(Map<String,Object> fields) {
    reference(fields); serving(fields);
    Object label = fields.get("servingUnit");
    if (label != null && (!(label instanceof String s) || s.length() > 100)) throw new IllegalArgumentException("Invalid serving label");
    nutrient(fields, "saturatedFat");
    nutrient(fields, "transFat");
    nutrient(fields, "sugarAlcohol");
    nutrient(fields, "cholesterol");
    nutrient(fields, "potassium");
    nutrient(fields, "calcium");
    nutrient(fields, "iron");
    nutrient(fields, "magnesium");
    nutrient(fields, "zinc");
    nutrient(fields, "vitaminA");
    nutrient(fields, "vitaminC");
    nutrient(fields, "vitaminD");
    nutrient(fields, "vitaminE");
    nutrient(fields, "vitaminB12");
  }
  public static void apply(FoodItemEntity food, Map<String,Object> fields) {
    validate(fields);
    food.setNutritionReferenceUnit(reference(fields));
    food.setSaturatedFat(nutrient(fields, "saturatedFat"));
    food.setTransFat(nutrient(fields, "transFat"));
    food.setSugarAlcohol(nutrient(fields, "sugarAlcohol"));
    food.setCholesterol(nutrient(fields, "cholesterol"));
    food.setPotassium(nutrient(fields, "potassium"));
    food.setCalcium(nutrient(fields, "calcium"));
    food.setIron(nutrient(fields, "iron"));
    food.setMagnesium(nutrient(fields, "magnesium"));
    food.setZinc(nutrient(fields, "zinc"));
    food.setVitaminA(nutrient(fields, "vitaminA"));
    food.setVitaminC(nutrient(fields, "vitaminC"));
    food.setVitaminD(nutrient(fields, "vitaminD"));
    food.setVitaminE(nutrient(fields, "vitaminE"));
    food.setVitaminB12(nutrient(fields, "vitaminB12"));
    food.setServingSizeGrams(serving(fields));
    Object servingUnit = fields.get("servingUnit");
    food.setServingUnit(servingUnit instanceof String value && !value.isBlank() ? value.trim() : null);
  }
  public static void apply(CustomFoodRequestDto food, Map<String,Object> fields) {
    validate(fields);
    food.setNutritionReferenceUnit(reference(fields));
    food.setSaturatedFat(nutrient(fields, "saturatedFat"));
    food.setTransFat(nutrient(fields, "transFat"));
    food.setCholesterol(nutrient(fields, "cholesterol"));
    food.setPotassium(nutrient(fields, "potassium"));
    food.setCalcium(nutrient(fields, "calcium"));
    food.setIron(nutrient(fields, "iron"));
    food.setMagnesium(nutrient(fields, "magnesium"));
    food.setZinc(nutrient(fields, "zinc"));
    food.setVitaminA(nutrient(fields, "vitaminA"));
    food.setVitaminC(nutrient(fields, "vitaminC"));
    food.setVitaminD(nutrient(fields, "vitaminD"));
    food.setVitaminE(nutrient(fields, "vitaminE"));
    food.setVitaminB12(nutrient(fields, "vitaminB12"));
  }
}
