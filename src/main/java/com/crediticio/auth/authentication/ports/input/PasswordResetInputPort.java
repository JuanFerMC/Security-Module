package com.crediticio.auth.authentication.ports.input;

import com.crediticio.auth.authentication.application.dto.ForgotPasswordRequest;
import com.crediticio.auth.authentication.application.dto.ResetPasswordRequest;

public interface PasswordResetInputPort {

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);
}
