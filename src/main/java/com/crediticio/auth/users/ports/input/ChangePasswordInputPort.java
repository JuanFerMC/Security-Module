package com.crediticio.auth.users.ports.input;

import com.crediticio.auth.users.application.dto.ChangePasswordRequest;

public interface ChangePasswordInputPort {

    void changePassword(Long userId, ChangePasswordRequest request, String authenticatedEmail, boolean isAdmin);
}
