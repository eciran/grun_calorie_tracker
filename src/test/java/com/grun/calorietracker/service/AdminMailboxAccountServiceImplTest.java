package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminMailboxAccountRequestDto;
import com.grun.calorietracker.entity.AdminMailboxAccountEntity;
import com.grun.calorietracker.repository.AdminMailboxAccountRepository;
import com.grun.calorietracker.security.MailCredentialCipher;
import com.grun.calorietracker.service.impl.AdminMailboxAccountServiceImpl;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminMailboxAccountServiceImplTest {
    @Test void createEncryptsPasswordAndNeverReturnsIt(){
        AdminMailboxAccountRepository repository=mock(AdminMailboxAccountRepository.class);
        when(repository.findByEmailAddressIgnoreCase("support@gruncalorietracker.com")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation->{AdminMailboxAccountEntity value=invocation.getArgument(0);value.setId(1L);return value;});
        AdminMailboxAccountServiceImpl service=new AdminMailboxAccountServiceImpl(repository,new MailCredentialCipher("test-mail-key"));
        var result=service.create(request("support@gruncalorietracker.com","secret-value",true));
        assertThat(result.passwordConfigured()).isTrue();
        assertThat(result.toString()).doesNotContain("secret-value");
        verify(repository).save(argThat(value->value.getPasswordEncrypted()!=null&&!value.getPasswordEncrypted().contains("secret-value")));
    }

    @Test void cannotEnableMailboxWithoutPassword(){
        AdminMailboxAccountRepository repository=mock(AdminMailboxAccountRepository.class);
        when(repository.findByEmailAddressIgnoreCase(anyString())).thenReturn(Optional.empty());
        AdminMailboxAccountServiceImpl service=new AdminMailboxAccountServiceImpl(repository,new MailCredentialCipher("test-mail-key"));
        assertThatThrownBy(()->service.create(request("info@gruncalorietracker.com","",true)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("password");
        verify(repository,never()).save(any());
    }

    private AdminMailboxAccountRequestDto request(String email,String password,boolean enabled){return new AdminMailboxAccountRequestDto(email,"Support",email,password,"ni-kyrenia.guzelhosting.com",993,true,"ni-kyrenia.guzelhosting.com",465,true,enabled);}
}
