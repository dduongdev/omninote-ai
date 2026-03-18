package com.omninote_ai.server.services;

import com.omninote_ai.server.dto.AuthRequest;
import com.omninote_ai.server.dto.AuthResponse;
import com.omninote_ai.server.dto.RegisterRequest;
import com.omninote_ai.server.entity.User;
import com.omninote_ai.server.repositories.UserRepository;
import com.omninote_ai.server.utility.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUserName(request.getUserName())) {
            throw new RuntimeException("Username already exists");
        }

        User user = new User();
        user.setUserName(request.getUserName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setStatus("ACTIVE");

        userRepository.save(user);

        return new AuthResponse(null, "User registered successfully");
    }

    @Override
    public AuthResponse login(AuthRequest request) {
        Optional<User> optionalUser = userRepository.findByUserName(request.getUserName());

        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                String token = jwtUtil.generateToken(user);
                return new AuthResponse(token, "Login successful");
            }
        }
        
        throw new RuntimeException("Invalid username or password");
    }
}
