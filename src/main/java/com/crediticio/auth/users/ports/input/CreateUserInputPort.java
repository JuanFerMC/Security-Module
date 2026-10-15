package com.crediticio.auth.users.ports.input;

import com.crediticio.auth.users.application.dto.CreateUserRequest;
import com.crediticio.auth.users.application.dto.UserResponse;

public interface CreateUserInputPort {

    UserResponse createUser(CreateUserRequest request, String actor);
}
