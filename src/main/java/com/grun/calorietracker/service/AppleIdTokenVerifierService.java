package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.VerifiedAppleIdentityDto;

public interface AppleIdTokenVerifierService {
    VerifiedAppleIdentityDto verify(String idToken, String nonce);
}
