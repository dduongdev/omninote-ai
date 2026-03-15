package com.omninote_ai.server.repositories;

import java.util.Optional;

import com.omninote_ai.server.entity.User;

public interface UserRepository {
    Optional<User> findByUserName(String userName);
    User save(User user);
    boolean existsByUserName(String userName);
}
