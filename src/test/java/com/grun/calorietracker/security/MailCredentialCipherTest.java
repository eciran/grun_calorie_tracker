package com.grun.calorietracker.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MailCredentialCipherTest {
    @Test void encryptsMailboxPasswordsWithRandomIv(){
        MailCredentialCipher cipher=new MailCredentialCipher("test-mail-key");
        String first=cipher.encrypt("mailbox-password");
        String second=cipher.encrypt("mailbox-password");
        assertThat(first).isNotEqualTo(second).doesNotContain("mailbox-password");
        assertThat(cipher.decrypt(first)).isEqualTo("mailbox-password");
        assertThat(cipher.decrypt(second)).isEqualTo("mailbox-password");
    }
}
