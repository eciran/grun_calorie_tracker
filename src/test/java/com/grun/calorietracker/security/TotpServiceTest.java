package com.grun.calorietracker.security;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class TotpServiceTest {
 @Test void generatesKnownHotpVector() { assertThat(new TotpService().generateForCounter("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ",1)).isEqualTo("287082"); }
 @Test void rejectsMalformedCodes() { assertThat(new TotpService().verify("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ","12ab")).isFalse(); }
}