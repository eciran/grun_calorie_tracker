INSERT INTO exercise_item_aliases(exercise_item_id, alias, normalized_alias, language, alias_type, active)
SELECT e.id, e.name,
       trim(regexp_replace(lower(translate(e.name, 'ÇĞİÖŞÜçğıöşü', 'CGIOSUcgiosu')), '[^a-z0-9]+', ' ', 'g')),
       'und', 'CANONICAL', TRUE
FROM exercise_items e
WHERE e.name IS NOT NULL
  AND trim(e.name) <> ''
ON CONFLICT (normalized_alias, language) DO NOTHING;
