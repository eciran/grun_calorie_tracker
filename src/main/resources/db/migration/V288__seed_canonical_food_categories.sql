-- GRUN-owned bilingual navigation taxonomy. Source tags are mapped separately.
insert into food_categories(
    slug, name_en, name_tr, description_en, description_tr, icon_key,
    sort_order, active, created_by, updated_by
)
values
    ('produce', 'Fruit & Vegetables', 'Meyve ve Sebzeler', 'Fresh, frozen and preserved fruit and vegetables.', 'Taze, dondurulmus ve korunmus meyve ve sebzeler.', 'apple', 10, true, 'migration-v288', 'migration-v288'),
    ('meat-poultry', 'Meat & Poultry', 'Et ve Kus Urunleri', 'Meat, poultry and products made primarily from them.', 'Et, kus urunleri ve agirlikli olarak bunlardan yapilan urunler.', 'beef', 20, true, 'migration-v288', 'migration-v288'),
    ('fish-seafood', 'Fish & Seafood', 'Balik ve Deniz Urunleri', 'Fish, shellfish and seafood products.', 'Balik, kabuklu deniz urunleri ve deniz urunleri.', 'fish', 30, true, 'migration-v288', 'migration-v288'),
    ('dairy-eggs', 'Dairy & Eggs', 'Sut Urunleri ve Yumurta', 'Milk, cultured dairy, cheese and eggs.', 'Sut, fermente sut urunleri, peynir ve yumurta.', 'milk', 40, true, 'migration-v288', 'migration-v288'),
    ('grains-starches', 'Grains & Starches', 'Tahillar ve Nisastali Gidalar', 'Bread, rice, pasta, cereals and starchy staples.', 'Ekmek, pirinc, makarna, tahillar ve nisastali temel gidalar.', 'wheat', 50, true, 'migration-v288', 'migration-v288'),
    ('legumes-nuts-seeds', 'Legumes, Nuts & Seeds', 'Bakliyat, Kuruyemis ve Tohumlar', 'Pulses, legumes, nuts, seeds and their products.', 'Bakliyat, kurubaklagil, kuruyemis, tohum ve bunlardan yapilan urunler.', 'bean', 60, true, 'migration-v288', 'migration-v288'),
    ('prepared-meals', 'Prepared Meals', 'Hazir Yemekler', 'Prepared dishes and ready-to-eat meals.', 'Hazirlanmis yemekler ve tuketime hazir ogunler.', 'utensils', 70, true, 'migration-v288', 'migration-v288'),
    ('snacks-sweets', 'Snacks & Sweets', 'Atistirmaliklar ve Tatlilar', 'Sweet and savoury snacks, desserts and confectionery.', 'Tatli ve tuzlu atistirmaliklar, tatlilar ve sekerlemeler.', 'cookie', 80, true, 'migration-v288', 'migration-v288'),
    ('beverages', 'Beverages', 'Icecekler', 'Non-alcoholic drinks and beverage preparations.', 'Alkolsuz icecekler ve icecek hazirlama urunleri.', 'cup-soda', 90, true, 'migration-v288', 'migration-v288'),
    ('sauces-condiments-spreads', 'Sauces, Condiments & Spreads', 'Soslar, Cesniler ve Surulebilir Urunler', 'Sauces, condiments, dips and spreads.', 'Soslar, cesniler, dip soslar ve surulebilir urunler.', 'bottle', 100, true, 'migration-v288', 'migration-v288'),
    ('fats-oils', 'Fats & Oils', 'Yaglar', 'Cooking oils, fats and related products.', 'Yemeklik yaglar ve ilgili urunler.', 'droplets', 110, true, 'migration-v288', 'migration-v288'),
    ('supplements', 'Supplements', 'Takviye Edici Gidalar', 'Dietary and sports nutrition supplements.', 'Gida ve sporcu beslenmesi takviyeleri.', 'pill', 120, true, 'migration-v288', 'migration-v288')
on conflict (slug) do nothing;

insert into food_categories(
    parent_id, slug, name_en, name_tr, icon_key, sort_order, active, created_by, updated_by
)
select parent.id, child.slug, child.name_en, child.name_tr, child.icon_key, child.sort_order,
       true, 'migration-v288', 'migration-v288'
from (values
    ('produce', 'fruit', 'Fruit', 'Meyveler', 'apple', 10),
    ('produce', 'vegetables', 'Vegetables', 'Sebzeler', 'carrot', 20),
    ('meat-poultry', 'poultry', 'Poultry', 'Kumes Hayvanlari', 'drumstick', 10),
    ('meat-poultry', 'red-meat', 'Red Meat', 'Kirmizi Et', 'beef', 20),
    ('meat-poultry', 'processed-meat', 'Processed Meat', 'Islenmis Et', 'ham', 30),
    ('fish-seafood', 'fish', 'Fish', 'Balik', 'fish', 10),
    ('fish-seafood', 'shellfish', 'Shellfish', 'Kabuklu Deniz Urunleri', 'shell', 20),
    ('dairy-eggs', 'milk', 'Milk', 'Sut', 'milk', 10),
    ('dairy-eggs', 'cheese', 'Cheese', 'Peynir', 'circle', 20),
    ('dairy-eggs', 'yogurt-cultured-dairy', 'Yogurt & Cultured Dairy', 'Yogurt ve Fermente Sut Urunleri', 'cup-soda', 30),
    ('dairy-eggs', 'eggs', 'Eggs', 'Yumurta', 'egg', 40),
    ('grains-starches', 'bread-bakery-staples', 'Bread & Bakery Staples', 'Ekmek ve Unlu Temel Gidalar', 'sandwich', 10),
    ('grains-starches', 'rice-grains', 'Rice & Grains', 'Pirinc ve Tahillar', 'wheat', 20),
    ('grains-starches', 'pasta-noodles', 'Pasta & Noodles', 'Makarna ve Noodle', 'utensils', 30),
    ('grains-starches', 'breakfast-cereals', 'Breakfast Cereals', 'Kahvaltilik Gevrekler', 'wheat', 40),
    ('grains-starches', 'potatoes-starchy-vegetables', 'Potatoes & Starchy Vegetables', 'Patates ve Nisastali Sebzeler', 'sprout', 50),
    ('legumes-nuts-seeds', 'legumes-pulses', 'Legumes & Pulses', 'Bakliyat ve Kurubaklagiller', 'bean', 10),
    ('legumes-nuts-seeds', 'nuts-seeds', 'Nuts & Seeds', 'Kuruyemis ve Tohumlar', 'nut', 20),
    ('prepared-meals', 'soups', 'Soups', 'Corbalar', 'soup', 10),
    ('prepared-meals', 'pizza-pies', 'Pizza, Pies & Quiches', 'Pizza, Turta ve Kisler', 'pizza', 20),
    ('prepared-meals', 'sandwiches-wraps', 'Sandwiches & Wraps', 'Sandvic ve Durumler', 'sandwich', 30),
    ('prepared-meals', 'ready-meals', 'Ready Meals', 'Hazir Ogunler', 'utensils', 40),
    ('snacks-sweets', 'savoury-snacks', 'Savoury Snacks', 'Tuzlu Atistirmaliklar', 'popcorn', 10),
    ('snacks-sweets', 'biscuits-cakes', 'Biscuits & Cakes', 'Biskuvi ve Kekler', 'cookie', 20),
    ('snacks-sweets', 'chocolate-confectionery', 'Chocolate & Confectionery', 'Cikolata ve Sekerlemeler', 'candy', 30),
    ('snacks-sweets', 'desserts-ice-cream', 'Desserts & Ice Cream', 'Tatlilar ve Dondurma', 'ice-cream-bowl', 40),
    ('beverages', 'water', 'Water', 'Su', 'glass-water', 10),
    ('beverages', 'soft-drinks', 'Soft Drinks', 'Gazli ve Aromali Icecekler', 'cup-soda', 20),
    ('beverages', 'juice', 'Juice & Nectar', 'Meyve Suyu ve Nektar', 'glass-water', 30),
    ('beverages', 'hot-drinks', 'Tea, Coffee & Hot Drinks', 'Cay, Kahve ve Sicak Icecekler', 'coffee', 40),
    ('beverages', 'plant-based-drinks', 'Plant-Based Drinks', 'Bitkisel Icecekler', 'vegan', 50),
    ('sauces-condiments-spreads', 'sauces-condiments', 'Sauces & Condiments', 'Soslar ve Cesniler', 'bottle', 10),
    ('sauces-condiments-spreads', 'spreads-dips', 'Spreads & Dips', 'Surulebilir Urunler ve Dip Soslar', 'circle-dot', 20),
    ('fats-oils', 'cooking-oils', 'Cooking Oils', 'Yemeklik Siviyaglar', 'droplets', 10),
    ('fats-oils', 'solid-fats', 'Butter & Solid Fats', 'Tereyagi ve Kati Yaglar', 'square', 20),
    ('supplements', 'dietary-supplements', 'Dietary Supplements', 'Gida Takviyeleri', 'pill', 10),
    ('supplements', 'sports-nutrition', 'Sports Nutrition', 'Sporcu Beslenmesi', 'dumbbell', 20)
) as child(parent_slug, slug, name_en, name_tr, icon_key, sort_order)
join food_categories parent on parent.slug = child.parent_slug
on conflict (slug) do nothing;
