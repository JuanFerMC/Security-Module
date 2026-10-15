package com.crediticio.auth.authentication.ports.input;

import com.crediticio.auth.authentication.application.dto.LoginRequest;
import com.crediticio.auth.authentication.application.dto.LoginResponse;
import com.crediticio.auth.authentication.application.dto.TokenValidationResponse;

public interface AuthInputPort {

    LoginResponse login(LoginRequest request);

    LoginResponse refresh(String refreshToken);

    void logout(String refreshToken);

    TokenValidationResponse validate(String accessToken);
}
