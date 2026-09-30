-- Correct the original ASCII-only Turkish taxonomy labels without rewriting V288.
with localized(slug, name_tr) as (values
    ('produce', 'Meyve ve Sebzeler'),
    ('meat-poultry', 'Et ve Tavuk'),
    ('fish-seafood', 'Balık ve Deniz Ürünleri'),
    ('dairy-eggs', 'Süt Ürünleri ve Yumurta'),
    ('grains-starches', 'Tahıllar ve Nişastalı Gıdalar'),
    ('legumes-nuts-seeds', 'Bakliyat, Kuruyemiş ve Tohumlar'),
    ('prepared-meals', 'Hazır Yemekler'),
    ('snacks-sweets', 'Atıştırmalıklar ve Tatlılar'),
    ('beverages', 'İçecekler'),
    ('sauces-condiments-spreads', 'Soslar, Çeşniler ve Sürülebilir Ürünler'),
    ('fats-oils', 'Yağlar'),
    ('supplements', 'Takviye Edici Gıdalar'),
    ('fruit', 'Meyveler'),
    ('vegetables', 'Sebzeler'),
    ('poultry', 'Tavuk ve Kümes Hayvanları'),
    ('red-meat', 'Kırmızı Et'),
    ('processed-meat', 'İşlenmiş Et'),
    ('fish', 'Balık'),
    ('shellfish', 'Kabuklu Deniz Ürünleri'),
    ('milk', 'Süt'),
    ('cheese', 'Peynir'),
    ('yogurt-cultured-dairy', 'Yoğurt ve Fermente Süt Ürünleri'),
    ('eggs', 'Yumurta'),
    ('bread-bakery-staples', 'Ekmek ve Unlu Temel Gıdalar'),
    ('rice-grains', 'Pirinç ve Tahıllar'),
    ('pasta-noodles', 'Makarna ve Noodle'),
    ('breakfast-cereals', 'Kahvaltılık Gevrekler'),
    ('potatoes-starchy-vegetables', 'Patates ve Nişastalı Sebzeler'),
    ('legumes-pulses', 'Bakliyat ve Kuru Baklagiller'),
    ('nuts-seeds', 'Kuruyemiş ve Tohumlar'),
    ('soups', 'Çorbalar'),
    ('pizza-pies', 'Pizza, Turta ve Kişler'),
    ('sandwiches-wraps', 'Sandviç ve Dürümler'),
    ('ready-meals', 'Hazır Öğünler'),
    ('savoury-snacks', 'Tuzlu Atıştırmalıklar'),
    ('biscuits-cakes', 'Bisküvi ve Kekler'),
    ('chocolate-confectionery', 'Çikolata ve Şekerlemeler'),
    ('desserts-ice-cream', 'Tatlılar ve Dondurma'),
    ('water', 'Su'),
    ('soft-drinks', 'Gazlı ve Aromalı İçecekler'),
    ('juice', 'Meyve Suyu ve Nektar'),
    ('hot-drinks', 'Çay, Kahve ve Sıcak İçecekler'),
    ('plant-based-drinks', 'Bitkisel İçecekler'),
    ('sauces-condiments', 'Soslar ve Çeşniler'),
    ('spreads-dips', 'Sürülebilir Ürünler ve Dip Soslar'),
    ('cooking-oils', 'Yemeklik Sıvı Yağlar'),
    ('solid-fats', 'Tereyağı ve Katı Yağlar'),
    ('dietary-supplements', 'Gıda Takviyeleri'),
    ('sports-nutrition', 'Sporcu Beslenmesi')
)
update food_categories category
set name_tr = localized.name_tr,
    updated_by = 'migration-v295',
    updated_at = current_timestamp
from localized
where category.slug = localized.slug;

with localized(slug, description_tr) as (values
    ('produce', 'Taze, dondurulmuş ve korunmuş meyve ve sebzeler.'),
    ('meat-poultry', 'Et, tavuk, diğer kümes hayvanları ve ağırlıklı olarak bunlardan yapılan ürünler.'),
    ('fish-seafood', 'Balık, kabuklu deniz ürünleri ve diğer deniz ürünleri.'),
    ('dairy-eggs', 'Süt, fermente süt ürünleri, peynir ve yumurta.'),
    ('grains-starches', 'Ekmek, pirinç, makarna, tahıllar ve nişastalı temel gıdalar.'),
    ('legumes-nuts-seeds', 'Bakliyat, kuru baklagiller, kuruyemişler, tohumlar ve bunlardan yapılan ürünler.'),
    ('prepared-meals', 'Hazırlanmış yemekler ve tüketime hazır öğünler.'),
    ('snacks-sweets', 'Tatlı ve tuzlu atıştırmalıklar, tatlılar ve şekerlemeler.'),
    ('beverages', 'Alkolsüz içecekler ve içecek hazırlama ürünleri.'),
    ('sauces-condiments-spreads', 'Soslar, çeşniler, dip soslar ve sürülebilir ürünler.'),
    ('fats-oils', 'Yemeklik yağlar ve ilgili ürünler.'),
    ('supplements', 'Gıda ve sporcu beslenmesi takviyeleri.')
)
update food_categories category
set description_tr = localized.description_tr,
    updated_by = 'migration-v295',
    updated_at = current_timestamp
from localized
where category.slug = localized.slug;
