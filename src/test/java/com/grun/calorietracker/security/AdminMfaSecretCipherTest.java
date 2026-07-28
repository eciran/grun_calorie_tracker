package com.grun.calorietracker.security;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AdminMfaSecretCipherTest {
 @Test void encryptsWithRandomIvAndDecrypts() { AdminMfaSecretCipher cipher=new AdminMfaSecretCipher("test-key-material"); String a=cipher.encrypt("SECRET"); String b=cipher.encrypt("SECRET"); assertThat(a).isNotEqualTo(b); assertThat(cipher.decrypt(a)).isEqualTo("SECRET"); }
}