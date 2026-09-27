import fs from 'node:fs';
import path from 'node:path';

const output = path.resolve('data/recipe-import/generated/grun-athlete-recipes-stage-20.json');

const sources = {
  ais: 'https://www.ais.gov.au/nutrition/recipes',
  aisMexicanRice: 'https://www.ais.gov.au/nutrition/recipes/mexican_rice',
  aisSmoothie: 'https://www.ais.gov.au/nutrition/recipes/mango_smoothie',
  proteinPancakes: 'https://www.bbcgoodfood.com/recipes/easy-protein-pancakes',
  tunaQuinoa: 'https://www.bbcgoodfood.com/recipes/tuna-avocado-quinoa-salad',
  coronationChicken: 'https://www.bbcgoodfood.com/recipes/easy-coronation-chicken',
  bbqChicken: 'https://www.bbcgoodfood.com/recipes/healthy-bbq-chicken',
  postWorkout: 'https://www.bbcgoodfood.com/health/fitness/best-post-workout-meals',
  popularChicken: 'https://www.bbcgoodfood.com/howto/guide/most-popular-chicken-recipes',
  pancakes: 'https://www.bbcgoodfood.com/recipes/collection/breakfast-pancake-recipes',
};

const gram = (ingredientName, estimatedGrams) => ({ ingredientName, portionSize: estimatedGrams, portionUnit: 'GRAM', estimatedGrams });
const ml = (ingredientName, amount) => ({ ingredientName, portionSize: amount, portionUnit: 'MILLILITER', estimatedGrams: amount });
const piece = (ingredientName, count, estimatedGrams) => ({ ingredientName, portionSize: count, portionUnit: 'PIECE', estimatedGrams });

const recipe = (id, sourceTitle, sourceUrl, name, description, mealType, marketRegion, yieldGrams, servingGrams, categories, allergens, ingredients, steps) => ({
  sourceKey: `grun-athlete-curation-${String(id).padStart(2, '0')}`,
  sourceTitle,
  sourceUrl,
  sourceRevisionUrl: sourceUrl,
  license: 'Original GRun editorial adaptation; source used for selection evidence only',
  recommendedImportStatus: 'EDITORIAL_AND_INGREDIENT_MAPPING_REQUIRED',
  recipe: {
    name,
    description,
    mealType,
    marketRegion,
    marketRegions: [marketRegion],
    language: 'en',
    imageUrl: null,
    totalYieldGrams: yieldGrams,
    defaultServingGrams: servingGrams,
    servingCount: Math.round(yieldGrams / servingGrams),
    categories,
    allergens,
    ingredients,
    cookingSteps: steps.map(instruction => ({ instruction })),
    translations: [{
      language: 'EN',
      name,
      description,
      cookingSteps: steps.map(instruction => ({ instruction })),
    }],
  },
});

const recipes = [
  recipe(1, 'Easy protein pancakes - 4.2/5 from 87 ratings at research time', sources.proteinPancakes,
    'Banana Oat Protein Pancakes', 'Post-training breakfast with protein, carbohydrate and fruit. Original GRun formulation inspired by a highly rated protein pancake format.',
    'BREAKFAST', 'UK_IE', 500, 250, ['BREAKFAST', 'HIGH_PROTEIN', 'QUICK_MEAL', 'VEGETARIAN'], ['MILK', 'EGGS', 'GLUTEN'],
    [gram('Rolled oats', 80), piece('Banana', 1, 120), piece('Eggs', 3, 150), gram('Greek yogurt', 100), ml('Semi-skimmed milk', 60), gram('Whey protein powder', 30), gram('Baking powder', 5), gram('Ground cinnamon', 2), gram('Blueberries', 80)],
    ['Blend the oats into a coarse flour.', 'Blend in banana, eggs, yogurt, milk, protein powder, baking powder and cinnamon.', 'Cook small pancakes in a non-stick pan over medium heat for 1-2 minutes per side.', 'Serve with blueberries.']),

  recipe(2, 'Tuna, avocado and quinoa salad - 4.7/5 from 48 ratings at research time', sources.tunaQuinoa,
    'Tuna Quinoa Recovery Bowl', 'Balanced recovery lunch with complete protein, carbohydrate, vegetables and unsaturated fat.',
    'LUNCH', 'UK_IE', 760, 380, ['LUNCH', 'HIGH_PROTEIN', 'FISH', 'SALAD', 'MEAL_PREP', 'GLUTEN_FREE'], ['FISH', 'MILK'],
    [gram('Dry quinoa', 120), gram('Tuna in spring water, drained', 240), gram('Avocado', 140), gram('Cherry tomatoes', 180), gram('Baby spinach', 80), gram('Feta cheese', 60), gram('Mixed seeds', 20), ml('Extra virgin olive oil', 20), ml('Lemon juice', 30)],
    ['Rinse and cook quinoa until tender, then cool slightly.', 'Whisk olive oil and lemon juice.', 'Fold quinoa with tuna, tomatoes, spinach, avocado and feta.', 'Finish with mixed seeds and dressing.']),

  recipe(3, 'Chicken and chorizo jambalaya - top popular chicken recipe with 2,664 ratings at research time', sources.popularChicken,
    'Lean Chicken Jambalaya', 'A lighter high-protein rice meal designed for refuelling after demanding training.',
    'DINNER', 'UK_IE', 1600, 400, ['DINNER', 'HIGH_PROTEIN', 'CHICKEN', 'MEAL_PREP'], [],
    [gram('Chicken breast', 600), gram('Long-grain rice', 300), gram('Red bell pepper', 200), gram('Onion', 150), gram('Chopped tomatoes', 400), ml('Low-salt chicken stock', 500), gram('Smoked paprika', 8), gram('Garlic', 12), ml('Olive oil', 15)],
    ['Brown diced chicken in olive oil.', 'Soften onion, pepper and garlic, then add paprika.', 'Stir in rice, tomatoes and stock.', 'Cover and simmer until the rice is tender and chicken is cooked through.']),

  recipe(4, 'Healthy BBQ chicken - high-protein and low-calorie community recipe', sources.bbqChicken,
    'Smoky BBQ Chicken and Sweet Potato', 'Lean protein with slow-release carbohydrate for a practical training-day dinner.',
    'DINNER', 'UK_IE', 1600, 400, ['DINNER', 'HIGH_PROTEIN', 'CHICKEN', 'LOW_FAT', 'MEAL_PREP', 'GLUTEN_FREE'], ['MUSTARD'],
    [gram('Chicken breast', 720), gram('Sweet potato', 600), gram('Passata', 160), gram('Red onion', 120), gram('Smoked paprika', 8), gram('Mustard powder', 4), ml('Balsamic vinegar', 20), ml('Olive oil', 20)],
    ['Cut sweet potato into wedges and roast with half the oil.', 'Mix passata, paprika, mustard and vinegar into a glaze.', 'Coat chicken with glaze and bake or grill until fully cooked.', 'Serve sliced chicken with the roasted sweet potato.']),

  recipe(5, 'Easy coronation chicken - 4.8/5 from 189 ratings at research time', sources.coronationChicken,
    'Curried Yogurt Chicken Wrap Filling', 'High-protein meal-prep filling with a lighter yogurt dressing.',
    'LUNCH', 'UK_IE', 880, 220, ['LUNCH', 'HIGH_PROTEIN', 'CHICKEN', 'QUICK_MEAL', 'MEAL_PREP'], ['MILK', 'MUSTARD'],
    [gram('Cooked chicken breast, shredded', 600), gram('Greek yogurt', 180), gram('Mango chutney', 40), gram('Mild curry powder', 8), gram('Dijon mustard', 8), gram('Sultanas', 30), ml('Lemon juice', 14)],
    ['Mix yogurt, chutney, curry powder, mustard and lemon juice.', 'Fold in shredded chicken and sultanas.', 'Chill before serving in a wholegrain wrap, potato or salad.']),

  recipe(6, 'Australian Institute of Sport athlete recipe collection - Mexican rice pattern', sources.aisMexicanRice,
    'High-Fuel Bean and Mexican Rice', 'Carbohydrate-forward meal for endurance or high-volume training days, with beans for added protein and fibre.',
    'LUNCH', 'GLOBAL', 1800, 450, ['LUNCH', 'HIGH_FIBER', 'VEGETARIAN', 'MEAL_PREP', 'GLUTEN_FREE'], [],
    [gram('Long-grain rice', 360), gram('Mixed beans, drained', 480), gram('Carrots', 200), gram('Green peas', 160), gram('Onion', 140), gram('Tomato puree', 120), ml('Low-salt vegetable stock', 720), gram('Garlic', 10), gram('Ground cumin', 6), ml('Olive oil', 20)],
    ['Soften onion and garlic in olive oil.', 'Stir in rice, cumin and tomato puree.', 'Add stock, carrots, peas and beans.', 'Cover and simmer until rice is tender, adding water if needed.']),

  recipe(7, 'Australian Institute of Sport athlete recipe collection - smoothie guidance', sources.aisSmoothie,
    'Mango Yogurt Recovery Smoothie', 'Fast post-training snack with fruit carbohydrate and dairy protein; adjust milk fat to energy needs.',
    'SNACK', 'GLOBAL', 760, 380, ['SNACK', 'HIGH_PROTEIN', 'QUICK_MEAL', 'VEGETARIAN', 'GLUTEN_FREE'], ['MILK'],
    [gram('Mango flesh', 300), ml('Semi-skimmed milk', 300), gram('Greek yogurt', 150), gram('Honey', 15), gram('Ground cinnamon', 2)],
    ['Add all ingredients to a blender.', 'Blend until smooth.', 'Serve immediately or keep chilled for the same day.']),

  recipe(8, 'Healthy banana pancakes - 4.6/5 from 169 ratings at research time', sources.pancakes,
    'Wholegrain Banana Training Pancakes', 'Whole-food breakfast for moderate training days, with oats, eggs and fruit.',
    'BREAKFAST', 'UK_IE', 480, 240, ['BREAKFAST', 'VEGETARIAN', 'HIGH_FIBER', 'QUICK_MEAL'], ['EGGS', 'MILK', 'GLUTEN'],
    [piece('Bananas', 2, 240), piece('Eggs', 2, 100), gram('Rolled oats', 100), ml('Milk', 60), gram('Baking powder', 5), gram('Ground cinnamon', 2), gram('Greek yogurt', 80)],
    ['Blend banana, eggs, oats, milk, baking powder and cinnamon.', 'Rest the batter for five minutes.', 'Cook small pancakes in a non-stick pan until golden on both sides.', 'Serve with Greek yogurt.']),

  recipe(9, 'Post-workout meal guidance by performance nutritionist James Collins', sources.postWorkout,
    'Cajun Chicken Quinoa Bowl', 'Protein and carbohydrate recovery bowl with colourful vegetables.',
    'DINNER', 'GLOBAL', 1400, 350, ['DINNER', 'HIGH_PROTEIN', 'CHICKEN', 'MEAL_PREP', 'GLUTEN_FREE'], [],
    [gram('Chicken breast', 600), gram('Dry quinoa', 280), gram('Red bell pepper', 200), gram('Courgette', 200), gram('Sweetcorn', 160), gram('Cajun seasoning', 12), ml('Olive oil', 20), ml('Lime juice', 30)],
    ['Cook quinoa and drain well.', 'Season chicken with Cajun spice and cook until done.', 'Saute pepper, courgette and sweetcorn.', 'Divide quinoa, vegetables and sliced chicken into bowls; finish with lime.']),

  recipe(10, 'Post-workout meal guidance by performance nutritionist James Collins', sources.postWorkout,
    'Salmon Potato Recovery Tray', 'Omega-3-rich fish with potatoes and greens for a complete recovery meal.',
    'DINNER', 'EU', 1400, 350, ['DINNER', 'HIGH_PROTEIN', 'FISH', 'MEAL_PREP', 'GLUTEN_FREE'], ['FISH', 'MUSTARD'],
    [gram('Salmon fillets', 600), gram('Baby potatoes', 600), gram('Broccoli', 300), gram('Dijon mustard', 20), ml('Lemon juice', 30), ml('Olive oil', 20), gram('Fresh dill', 8)],
    ['Roast halved potatoes with olive oil until nearly tender.', 'Add salmon brushed with mustard and lemon.', 'Add broccoli and roast until salmon is cooked and vegetables are tender.', 'Finish with dill.']),

  recipe(11, 'Australian Institute of Sport food-first athlete meal pattern', sources.ais,
    'Turkey Meatballs with Tomato Pasta', 'Meal-prep friendly protein and carbohydrate meal for team-sport or strength-training days.',
    'DINNER', 'EU', 1600, 400, ['DINNER', 'HIGH_PROTEIN', 'MEAT', 'MEAL_PREP'], ['WHEAT', 'GLUTEN', 'EGGS'],
    [gram('Lean turkey mince', 600), gram('Wholewheat pasta', 320), gram('Passata', 500), piece('Egg', 1, 50), gram('Wholemeal breadcrumbs', 40), gram('Onion', 140), gram('Garlic', 12), ml('Olive oil', 15), gram('Dried oregano', 5)],
    ['Combine turkey, egg, breadcrumbs and oregano; shape into meatballs.', 'Brown meatballs, then set aside.', 'Soften onion and garlic, add passata and return meatballs to simmer.', 'Cook pasta and serve with the meatball sauce.']),

  recipe(12, 'Australian Institute of Sport food-first athlete meal pattern', sources.ais,
    'Beef Broccoli Rice Bowl', 'Iron-rich lean beef, rice and vegetables for high-output training days.',
    'DINNER', 'GLOBAL', 1500, 375, ['DINNER', 'HIGH_PROTEIN', 'MEAT', 'MEAL_PREP'], ['SOYBEANS', 'SESAME'],
    [gram('Lean beef strips', 600), gram('Dry jasmine rice', 300), gram('Broccoli', 400), gram('Carrots', 180), ml('Reduced-salt soy sauce', 45), ml('Sesame oil', 10), gram('Garlic', 12), gram('Fresh ginger', 15)],
    ['Cook rice according to packet instructions.', 'Stir-fry beef in batches over high heat.', 'Cook broccoli and carrots with garlic and ginger.', 'Return beef, add soy sauce and sesame oil, then serve over rice.']),

  recipe(13, 'Australian Institute of Sport food-first athlete meal pattern', sources.ais,
    'Egg and Spinach Breakfast Wrap', 'Portable breakfast with protein, carbohydrate and vegetables for busy training mornings.',
    'BREAKFAST', 'UK_IE', 700, 350, ['BREAKFAST', 'HIGH_PROTEIN', 'QUICK_MEAL', 'VEGETARIAN'], ['EGGS', 'MILK', 'WHEAT', 'GLUTEN'],
    [piece('Eggs', 4, 200), gram('Wholegrain wraps', 120), gram('Baby spinach', 100), gram('Cottage cheese', 120), gram('Tomatoes', 120), ml('Olive oil', 10), gram('Black pepper', 2)],
    ['Wilt spinach and tomatoes in a pan.', 'Add beaten eggs and scramble gently.', 'Warm wraps and spread with cottage cheese.', 'Add egg mixture, roll tightly and serve.']),

  recipe(14, 'Australian Institute of Sport food-first athlete snack pattern', sources.ais,
    'Greek Yogurt Berry Overnight Oats', 'Prepare-ahead breakfast or recovery snack with protein, carbohydrate and berries.',
    'BREAKFAST', 'EU', 760, 380, ['BREAKFAST', 'HIGH_PROTEIN', 'MEAL_PREP', 'VEGETARIAN', 'HIGH_FIBER'], ['MILK', 'GLUTEN'],
    [gram('Rolled oats', 140), gram('Greek yogurt', 240), ml('Milk', 180), gram('Mixed berries', 160), gram('Chia seeds', 20), gram('Honey', 20)],
    ['Mix oats, yogurt, milk, chia seeds and honey.', 'Divide into containers and refrigerate overnight.', 'Top with berries before serving.']),

  recipe(15, 'Australian Institute of Sport plant-based athlete meal pattern', sources.ais,
    'Tofu Edamame Noodle Bowl', 'Plant-based protein and carbohydrate bowl suitable for post-training meal prep.',
    'LUNCH', 'GLOBAL', 1400, 350, ['LUNCH', 'VEGAN', 'VEGETARIAN', 'HIGH_PROTEIN', 'MEAL_PREP'], ['SOYBEANS', 'WHEAT', 'GLUTEN', 'SESAME'],
    [gram('Firm tofu', 500), gram('Wholewheat noodles', 280), gram('Shelled edamame', 240), gram('Pak choi', 250), gram('Carrots', 180), ml('Reduced-salt soy sauce', 40), ml('Sesame oil', 10), gram('Fresh ginger', 15)],
    ['Press and cube tofu, then brown in a non-stick pan.', 'Cook noodles and drain.', 'Stir-fry vegetables and edamame with ginger.', 'Add noodles, tofu, soy sauce and sesame oil; toss well.']),

  recipe(16, 'Australian Institute of Sport plant-based athlete meal pattern', sources.ais,
    'Red Lentil Sweet Potato Curry', 'High-fibre vegan meal with legumes and carbohydrate for training-day fuel.',
    'DINNER', 'GLOBAL', 1800, 450, ['DINNER', 'VEGAN', 'VEGETARIAN', 'HIGH_FIBER', 'MEAL_PREP', 'GLUTEN_FREE'], [],
    [gram('Dry red lentils', 300), gram('Sweet potato', 500), gram('Chopped tomatoes', 400), ml('Light coconut milk', 300), gram('Baby spinach', 180), gram('Onion', 150), gram('Garlic', 12), gram('Curry powder', 12), ml('Olive oil', 15)],
    ['Soften onion and garlic in oil.', 'Add curry powder, lentils, sweet potato, tomatoes and coconut milk.', 'Add water as needed and simmer until lentils and potato are tender.', 'Fold in spinach just before serving.']),

  recipe(17, 'Australian Institute of Sport convenient athlete snack pattern', sources.ais,
    'No-Bake Oat Date Energy Bites', 'Portable carbohydrate-rich snack for long sessions; portion according to training load.',
    'SNACK', 'GLOBAL', 600, 60, ['SNACK', 'VEGAN', 'VEGETARIAN', 'MEAL_PREP', 'QUICK_MEAL'], ['PEANUTS', 'GLUTEN'],
    [gram('Pitted dates', 240), gram('Rolled oats', 160), gram('Peanut butter', 120), gram('Cocoa powder', 20), gram('Chia seeds', 20), ml('Water', 40)],
    ['Blend dates into a paste.', 'Pulse in oats, peanut butter, cocoa and chia seeds.', 'Add water gradually until the mixture holds together.', 'Roll into ten equal bites and chill.']),

  recipe(18, 'Australian Institute of Sport food-first recovery pattern', sources.ais,
    'Cottage Cheese Berry Recovery Cup', 'No-cook high-protein snack with fruit and cereal carbohydrate.',
    'SNACK', 'EU', 600, 300, ['SNACK', 'HIGH_PROTEIN', 'QUICK_MEAL', 'VEGETARIAN'], ['MILK', 'GLUTEN'],
    [gram('Cottage cheese', 300), gram('Greek yogurt', 160), gram('Mixed berries', 160), gram('Low-sugar granola', 80), gram('Honey', 15)],
    ['Mix cottage cheese and yogurt.', 'Layer with berries and granola.', 'Drizzle with honey immediately before serving.']),

  recipe(19, 'Post-workout meal guidance by performance nutritionist James Collins', sources.postWorkout,
    'Prawn Tomato Wholewheat Pasta', 'High-protein seafood pasta supplying carbohydrate for glycogen replacement.',
    'DINNER', 'EU', 1300, 325, ['DINNER', 'HIGH_PROTEIN', 'FISH', 'MEAL_PREP'], ['CRUSTACEAN_SHELLFISH', 'WHEAT', 'GLUTEN'],
    [gram('Raw king prawns', 500), gram('Wholewheat pasta', 320), gram('Cherry tomatoes', 300), gram('Courgette', 200), gram('Garlic', 12), ml('Olive oil', 20), ml('Lemon juice', 25), gram('Fresh parsley', 15)],
    ['Cook pasta until al dente and reserve a little cooking water.', 'Saute courgette, tomatoes and garlic.', 'Add prawns and cook until opaque.', 'Toss with pasta, lemon, parsley and enough pasta water to coat.']),

  recipe(20, 'Australian Institute of Sport balanced athlete breakfast pattern', sources.ais,
    'Turkish Eggs and Yogurt Bowl', 'Protein-rich savoury breakfast with eggs, yogurt and wholegrain toast.',
    'BREAKFAST', 'TR', 720, 360, ['BREAKFAST', 'HIGH_PROTEIN', 'VEGETARIAN', 'TURKISH'], ['EGGS', 'MILK', 'WHEAT', 'GLUTEN'],
    [piece('Eggs', 4, 200), gram('Greek yogurt', 240), gram('Wholegrain bread', 120), gram('Cucumber', 100), gram('Garlic', 4), ml('Olive oil', 15), gram('Aleppo pepper', 3), gram('Fresh dill', 10)],
    ['Mix yogurt with grated garlic and spread between bowls.', 'Poach eggs until whites are set and yolks remain soft.', 'Place eggs over yogurt and finish with olive oil, pepper and dill.', 'Serve with cucumber and wholegrain toast.']),
];

const turkish = [
  ['Muzlu Yulaflı Protein Pankeki', 'Protein, karbonhidrat ve meyve içeren antrenman sonrası kahvaltısı.', ['Yulafı iri un kıvamına gelene kadar çekin.', 'Muz, yumurta, yoğurt, süt, protein tozu, kabartma tozu ve tarçını ekleyip karıştırın.', 'Küçük pankekleri yapışmaz tavada orta ateşte her yüzünü 1-2 dakika pişirin.', 'Yaban mersiniyle servis edin.']],
  ['Ton Balıklı Kinoa Toparlanma Kasesi', 'Kaliteli protein, karbonhidrat, sebze ve doymamış yağ içeren dengeli toparlanma öğünü.', ['Kinoayı yıkayıp yumuşayana kadar pişirin ve biraz soğutun.', 'Zeytinyağı ile limon suyunu çırpın.', 'Kinoa, ton balığı, domates, ıspanak, avokado ve beyaz peyniri karıştırın.', 'Tohumları ve sosu ekleyerek servis edin.']],
  ['Yağsız Tavuklu Jambalaya', 'Yoğun antrenman sonrası enerji yenilemeye uygun, daha hafif ve yüksek proteinli pirinç yemeği.', ['Küp doğranmış tavuğu zeytinyağında renk alana kadar pişirin.', 'Soğan, biber ve sarımsağı yumuşatıp kırmızı toz biberi ekleyin.', 'Pirinç, domates ve tavuk suyunu karıştırın.', 'Kapağı kapalı şekilde pirinç yumuşayana ve tavuk tamamen pişene kadar kısık ateşte pişirin.']],
  ['İsli Barbekü Tavuk ve Tatlı Patates', 'Antrenman günü için yağsız protein ve uzun süreli enerji sağlayan karbonhidrat öğünü.', ['Tatlı patatesi dilimleyip yağın yarısıyla fırınlayın.', 'Domates püresi, kırmızı toz biber, hardal ve sirkeyi sos haline getirin.', 'Tavuğu soslayıp tamamen pişene kadar fırınlayın veya ızgara yapın.', 'Dilimlenmiş tavuğu tatlı patatesle servis edin.']],
  ['Körili Yoğurtlu Tavuk Dürüm Harcı', 'Daha hafif yoğurt sosuyla hazırlanan yüksek proteinli öğün hazırlık harcı.', ['Yoğurt, mango chutney, köri, hardal ve limon suyunu karıştırın.', 'Didiklenmiş tavuk ve kuru üzümü ekleyin.', 'Tam tahıllı dürüm, fırın patates veya salatayla servis etmeden önce soğutun.']],
  ['Yüksek Enerjili Fasulyeli Meksika Pilavı', 'Dayanıklılık ve yüksek hacimli antrenman günleri için karbonhidrat ağırlıklı, lifli bir öğün.', ['Soğan ve sarımsağı zeytinyağında yumuşatın.', 'Pirinç, kimyon ve domates püresini ekleyip karıştırın.', 'Sebze suyu, havuç, bezelye ve fasulyeyi ekleyin.', 'Kapağı kapalı olarak pirinç yumuşayana kadar pişirin; gerekirse su ekleyin.']],
  ['Mangolu Yoğurtlu Toparlanma Smoothie’si', 'Meyve karbonhidratı ve süt proteini içeren hızlı antrenman sonrası ara öğünü.', ['Tüm malzemeleri blendera alın.', 'Pürüzsüz kıvama gelene kadar karıştırın.', 'Hemen servis edin veya aynı gün tüketmek üzere soğukta saklayın.']],
  ['Tam Tahıllı Muzlu Antrenman Pankeki', 'Orta yoğunluktaki antrenman günleri için yulaf, yumurta ve meyveli kahvaltı.', ['Muz, yumurta, yulaf, süt, kabartma tozu ve tarçını blenderdan geçirin.', 'Karışımı beş dakika dinlendirin.', 'Küçük pankekleri yapışmaz tavada iki yüzü kızarana kadar pişirin.', 'Süzme yoğurtla servis edin.']],
  ['Kajun Tavuklu Kinoa Kasesi', 'Renkli sebzeler, protein ve karbonhidrat içeren toparlanma kasesi.', ['Kinoayı pişirip iyice süzün.', 'Tavuğu Kajun baharatıyla çeşnilendirip tamamen pişirin.', 'Biber, kabak ve mısırı soteleyin.', 'Kinoa, sebze ve dilimlenmiş tavuğu kaselere paylaştırıp misket limonuyla tamamlayın.']],
  ['Somonlu Patates Toparlanma Tepsisi', 'Omega-3 içeren balık, patates ve yeşil sebzelerden oluşan tam toparlanma öğünü.', ['İkiye bölünmüş patatesleri zeytinyağıyla neredeyse yumuşayana kadar fırınlayın.', 'Hardal ve limon sürülmüş somonu tepsiye ekleyin.', 'Brokoliyi ekleyip somon ve sebzeler pişene kadar fırınlayın.', 'Dereotuyla tamamlayın.']],
  ['Domates Soslu Hindi Köfte ve Makarna', 'Takım sporu veya kuvvet antrenmanı günleri için hazırlaması kolay protein ve karbonhidrat öğünü.', ['Hindi, yumurta, galeta unu ve kekiği karıştırıp köfteler hazırlayın.', 'Köfteleri tavada renk aldırıp kenara alın.', 'Soğan ve sarımsağı yumuşatın, domates püresiyle köfteleri ekleyip pişirin.', 'Makarnayı haşlayıp köfte sosuyla servis edin.']],
  ['Etli Brokoli ve Pirinç Kasesi', 'Yüksek eforlu antrenman günleri için demirden zengin yağsız et, pirinç ve sebze öğünü.', ['Pirinci paket talimatına göre pişirin.', 'Et şeritlerini yüksek ateşte partiler halinde soteleyin.', 'Brokoli ve havucu sarımsak ve zencefille pişirin.', 'Eti geri ekleyip soya sosu ve susam yağıyla karıştırın; pirinç üzerinde servis edin.']],
  ['Yumurtalı Ispanaklı Kahvaltı Dürümü', 'Yoğun antrenman sabahları için taşınabilir protein, karbonhidrat ve sebze kahvaltısı.', ['Ispanak ve domatesi tavada yumuşatın.', 'Çırpılmış yumurtayı ekleyip nazikçe pişirin.', 'Dürümleri ısıtıp lor peyniri sürün.', 'Yumurtalı karışımı ekleyip sıkıca sarın.']],
  ['Süzme Yoğurtlu ve Meyveli Gecelik Yulaf', 'Önceden hazırlanabilen proteinli kahvaltı veya toparlanma ara öğünü.', ['Yulaf, yoğurt, süt, chia tohumu ve balı karıştırın.', 'Kaplara bölüp gece boyunca buzdolabında bekletin.', 'Servisten önce orman meyvelerini ekleyin.']],
  ['Tofu ve Edamame Noodle Kasesi', 'Antrenman sonrası öğün hazırlığına uygun bitkisel protein ve karbonhidrat kasesi.', ['Tofunun suyunu alıp küp doğrayın ve tavada kızartın.', 'Noodle’ı pişirip süzün.', 'Sebzeleri, edamame ve zencefille soteleyin.', 'Noodle, tofu, soya sosu ve susam yağını ekleyip karıştırın.']],
  ['Kırmızı Mercimekli Tatlı Patates Körisi', 'Antrenman günü enerjisi için bakliyat ve karbonhidrat içeren yüksek lifli vegan öğün.', ['Soğan ve sarımsağı yağda yumuşatın.', 'Köri, mercimek, tatlı patates, domates ve Hindistan cevizi sütünü ekleyin.', 'Gerektikçe su ekleyerek mercimek ve patates yumuşayana kadar pişirin.', 'Servisten hemen önce ıspanağı ekleyin.']],
  ['Pişmeyen Yulaflı Hurma Enerji Topları', 'Uzun antrenmanlar için taşınabilir karbonhidrat ağırlıklı ara öğün; porsiyonu antrenman yüküne göre ayarlayın.', ['Hurmaları macun kıvamına gelene kadar çekin.', 'Yulaf, fıstık ezmesi, kakao ve chia tohumunu ekleyip karıştırın.', 'Karışım toparlanana kadar azar azar su ekleyin.', 'On eşit top yapıp buzdolabında dinlendirin.']],
  ['Lor Peynirli Meyveli Toparlanma Kasesi', 'Meyve ve tahıl karbonhidratı içeren, pişirme gerektirmeyen yüksek proteinli ara öğün.', ['Lor peyniriyle yoğurdu karıştırın.', 'Meyveler ve granolayla katmanlar oluşturun.', 'Servisten hemen önce bal gezdirin.']],
  ['Karidesli Domatesli Tam Buğday Makarna', 'Glikojen yenilenmesine yardımcı karbonhidrat içeren yüksek proteinli deniz ürünlü makarna.', ['Makarnayı diri kalacak şekilde haşlayın ve biraz haşlama suyu ayırın.', 'Kabak, domates ve sarımsağı soteleyin.', 'Karidesleri ekleyip rengi dönene kadar pişirin.', 'Makarna, limon, maydanoz ve yeterli haşlama suyuyla karıştırın.']],
  ['Yoğurtlu Türk Yumurtası Kasesi', 'Yumurta, yoğurt ve tam tahıllı ekmek içeren proteinli tuzlu kahvaltı.', ['Yoğurdu rendelenmiş sarımsakla karıştırıp kaselere yayın.', 'Yumurtaları beyazı pişip sarısı yumuşak kalacak şekilde poşe edin.', 'Yumurtaları yoğurdun üzerine alıp zeytinyağı, biber ve dereotuyla tamamlayın.', 'Salatalık ve tam tahıllı ekmekle servis edin.']],
];

recipes.forEach((item, index) => {
  const [name, description, steps] = turkish[index];
  item.recipe.translations.push({
    language: 'TR',
    name,
    description,
    cookingSteps: steps.map(instruction => ({ instruction })),
  });
});

const payload = {
  batchId: 'grun-athlete-recipes-stage-20-20260925',
  createdAt: '2026-09-25',
  purpose: 'Curated athlete recipe candidates for GRun. Covers post-training recovery, high-fuel meals, portable snacks, meal prep, and plant-based options. Every candidate requires admin food-item mapping, nutrition verification, allergen review, and image curation before publication.',
  sourcePolicy: {
    sourceName: 'GRun athlete recipe editorial curation',
    sourceUrl: sources.ais,
    selectionBasis: 'Selected using athlete-specific guidance from the Australian Institute of Sport and strong community popularity/rating signals from Good Food. Recipes are original GRun formulations, not copied source text.',
    license: 'GRun original editorial content; source links retained as selection evidence',
    licenseUrl: null,
    attributionRequired: false,
    shareAlikeRequired: false,
    notes: 'No third-party images are imported. Ratings are research snapshots and must not be presented as GRun user ratings. This is not individualized medical or sports-dietitian advice.',
  },
  recipes,
};

fs.mkdirSync(path.dirname(output), { recursive: true });
fs.writeFileSync(output, `${JSON.stringify(payload, null, 2)}\n`, 'utf8');
console.log(`Wrote ${recipes.length} athlete recipe candidates to ${output}`);
