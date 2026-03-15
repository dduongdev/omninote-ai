package com.omninote_ai.server.services;

import com.omninote_ai.server.dto.AuthRequest;
import com.omninote_ai.server.dto.AuthResponse;
import com.omninote_ai.server.dto.RegisterRequest;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(AuthRequest request);
}
