package com.grun.calorietracker.contract;

import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class AdminAuditConstraintMigrationContractTest {
    @Test void latestConstraintMigrationContainsEveryJavaAuditEnumValue() throws Exception {
        StringBuilder sql=new StringBuilder();
        for(String resource : new String[]{
                "db/migration/V269__allow_owner_alert_action_audits.sql",
                "db/migration/V272__allow_owner_error_group_audits.sql",
                "db/migration/V275__allow_customer_notification_audits.sql"
        }) {
            try(var stream=getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(stream, resource+" missing");
                sql.append(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        for(AdminAuditActionType value:AdminAuditActionType.values()) assertTrue(sql.indexOf("'"+value.name()+"'")>=0,"Missing action: "+value);
        for(AdminAuditTargetType value:AdminAuditTargetType.values()) assertTrue(sql.indexOf("'"+value.name()+"'")>=0,"Missing target: "+value);
    }
}
