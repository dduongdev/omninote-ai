package com.omninote_ai.server.repositories.inmemmory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.omninote_ai.server.entity.User;
import com.omninote_ai.server.repositories.UserRepository;

@Repository
public class InMemoryUserRepositoryImpl implements UserRepository {

    private final List<User> users = new ArrayList<>();
    private long currentId = 1L;

    @Override
    public Optional<User> findByUserName(String userName) {
        return users.stream()
                .filter(u -> u.getUserName().equals(userName))
                .findFirst();
    }

    @Override
    public User save(User user) {
        if (user.getId() == null) {
            user.setId(currentId++);
            users.add(user);
        } else {
            users.removeIf(u -> u.getId().equals(user.getId()));
            users.add(user);
        }
        return user;
    }

    @Override
    public boolean existsByUserName(String userName) {
        return users.stream().anyMatch(u -> u.getUserName().equals(userName));
    }
}
