package com.grun.calorietracker.contract;

import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class AdminAuditConstraintMigrationContractTest {
    @Test void latestConstraintMigrationContainsEveryJavaAuditEnumValue() throws Exception {
        String resource="db/migration/V207__allow_admin_password_reset_audits.sql";
        try(var stream=getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource+" missing");
            String sql=new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            for(AdminAuditActionType value:AdminAuditActionType.values()) assertTrue(sql.contains("'"+value.name()+"'"),"Missing action: "+value);
            for(AdminAuditTargetType value:AdminAuditTargetType.values()) assertTrue(sql.contains("'"+value.name()+"'"),"Missing target: "+value);
        }
    }
}