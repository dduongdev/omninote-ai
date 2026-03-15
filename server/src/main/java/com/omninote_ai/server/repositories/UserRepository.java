package com.omninote_ai.server.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.omninote_ai.server.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUserName(String userName);
    boolean existsByUserName(String userName);
}