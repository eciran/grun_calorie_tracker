package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductImportResultDto;
import com.grun.calorietracker.enums.FoodProductImportFormat;
import com.grun.calorietracker.enums.FoodProductImportMode;
import org.springframework.web.multipart.MultipartFile;

public interface FoodProductImportService {

    FoodProductImportResultDto importCsv(MultipartFile file, String importedBy);

    FoodProductImportResultDto importCsv(MultipartFile file, String importedBy, FoodProductImportMode importMode);

    FoodProductImportResultDto importCsv(MultipartFile file, String importedBy, FoodProductImportMode importMode, FoodProductImportFormat importFormat);
}
