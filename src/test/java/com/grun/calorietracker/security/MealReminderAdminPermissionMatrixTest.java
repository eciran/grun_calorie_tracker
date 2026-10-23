package com.grun.calorietracker.security;
import com.grun.calorietracker.enums.AdminPermission;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
class MealReminderAdminPermissionMatrixTest {
 @Test void mealReminderAdminPathNeverFallsBackToDashboardPermission(){
  String path="/api/v1/admin/meal-reminder-automation/config";
  assertEquals(AdminPermission.NOTIFICATIONS_READ,AdminPermissionMatrix.requiredPermission("GET",path));
  assertEquals(AdminPermission.NOTIFICATIONS_MANAGE,AdminPermissionMatrix.requiredPermission("POST",path));
 }
}
