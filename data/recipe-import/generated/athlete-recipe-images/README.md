# GRUN Athlete Recipe Image Staging

This directory contains the reviewed image candidates for the 20-recipe athlete import batch.

- Images are generated from each recipe's declared ingredients and cooking method.
- File names match the recipe `sourceKey` suffix and canonical English recipe name.
- These are staging assets. Recipe `imageUrl` values remain `null` until the approved files are uploaded to S3.
- Upload must preserve the source-key mapping so generated S3 URLs can be applied deterministically.

The first energy-bite attempt was rejected because it contained nine pieces instead of the recipe's ten servings. Only the corrected ten-piece image belongs in this directory.
