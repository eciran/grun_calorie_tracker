import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionEntity;
import com.grun.calorietracker.enums.FoodNutritionReferenceUnit;
import com.grun.calorietracker.enums.FoodPortionUnit;
import com.grun.calorietracker.enums.FoodServingOptionUnit;
import com.grun.calorietracker.service.support.FoodPortionCalculator;
import com.grun.calorietracker.service.support.FoodPortionUnitResolver;
import com.grun.calorietracker.mapper.FoodItemMapper;

public class R5RuntimeContractCheck {
    public static void main(String[] args) {
        FoodItemEntity food = new FoodItemEntity();
        food.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100ML);
        if (!FoodPortionUnitResolver.allowedUnits(food).contains(FoodPortionUnit.MILLILITER)) {
            throw new AssertionError("Native volume input unavailable");
        }
        food.setName("Runtime Milk");
        food.setServingUnit("MILLILITER");
        var dto = FoodItemMapper.mapEntityToDto(food);
        if (dto.getNutritionReferenceUnit() != FoodNutritionReferenceUnit.PER_100ML
                || dto.getDefaultPortionUnit() != FoodPortionUnit.MILLILITER
                || !dto.getAllowedPortionUnits().contains(FoodPortionUnit.MILLILITER)) {
            throw new AssertionError("Volume contract lost in public product DTO");
        }
        var nativeVolume = FoodPortionCalculator.normalize(200.0, FoodPortionUnit.MILLILITER, food, null);
        if (nativeVolume.grams() != null || nativeVolume.milliliters() != 200.0 || nativeVolume.nutritionReferenceAmount() != 200.0) {
            throw new AssertionError("Native ml incorrectly treated as grams");
        }
        FoodItemServingOptionEntity option = new FoodItemServingOptionEntity();
        option.setUnitType(FoodServingOptionUnit.SERVING);
        option.setQuantity(1.0);
        option.setMlVolume(200.0);
        FoodPortionCalculator.validateServingOptionUnit(FoodPortionUnit.SERVING, option);
        if (!FoodPortionUnitResolver.supportsOption(food, option)
                || FoodPortionCalculator.normalize(1.0, FoodPortionUnit.SERVING, food, option).nutritionReferenceAmount() != 200.0) {
            throw new AssertionError("Named native-volume serving unsupported");
        }
        food.setNutritionReferenceUnit(FoodNutritionReferenceUnit.PER_100G);
        option.setUnitType(FoodServingOptionUnit.CUP);
        option.setMlVolume(null);
        option.setGramWeight(167.0);
        if (!FoodPortionUnitResolver.supportsOption(food, option)
                || FoodPortionCalculator.normalize(1.0, FoodPortionUnit.SERVING, food, option).nutritionReferenceAmount() != 167.0) {
            throw new AssertionError("Named source-mass serving unsupported");
        }
        boolean rejected = false;
        try {
            FoodPortionCalculator.normalize(200.0, FoodPortionUnit.MILLILITER, food, null);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        if (!rejected) throw new AssertionError("Unsourced cross-dimension conversion accepted");
        System.out.println("R5_RUNTIME_CONTRACT_PASS: native ml, named volume, named mass, no implicit density");
    }
}
