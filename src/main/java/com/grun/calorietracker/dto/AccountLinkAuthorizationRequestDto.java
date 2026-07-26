package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.AccountLinkPurpose;
import com.grun.calorietracker.enums.AccountReauthenticationMethod;
import com.grun.calorietracker.enums.AuthProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Recent-authentication request for a provider link or unlink operation.")
public class AccountLinkAuthorizationRequestDto {

    @NotNull
    private AccountLinkPurpose purpose;

    @NotNull
    private AuthProvider targetProvider;

    @NotNull
    private AccountReauthenticationMethod method;

    private String currentPassword;
    private String idToken;
    private String nonce;
}
