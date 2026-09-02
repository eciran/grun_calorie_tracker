package com.grun.calorietracker.service.reminder;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class MealReminderAdminSqlContractTest {
 @Test void migrationSeedsOffPolicyProtectedCopyAndPrivacyLifecycle() throws Exception {
  try(var in=getClass().getResourceAsStream("/db/migration/V233__add_meal_reminder_admin_policy.sql")){
   assertNotNull(in);String sql=new String(in.readAllBytes(),StandardCharsets.UTF_8).toLowerCase();
   assertTrue(sql.contains("'meal-reminder-policy-v1','active','off'"));
   assertTrue(sql.contains("uq_meal_reminder_single_active_policy"));
   assertTrue(sql.contains("meal_reminder_dinner_kcal"));
   assertTrue(sql.contains("on delete cascade"));
   assertFalse(sql.contains("push_token"));
  }
 }
}
