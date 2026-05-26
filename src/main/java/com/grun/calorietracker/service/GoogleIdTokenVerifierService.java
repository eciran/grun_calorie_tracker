package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.VerifiedGoogleIdentityDto;

public interface GoogleIdTokenVerifierService {
    VerifiedGoogleIdentityDto verify(String idToken);
}
