package com.stackroute.userservice.service;

import com.stackroute.userservice.model.User;
import com.stackroute.userservice.repository.RoleRepository;
import com.stackroute.userservice.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;

@AllArgsConstructor
@Service
public class UserServiceImpl implements UserService {

    private final RoleRepository roleRepository;

    private final UserRepository userRepository;

    private final PasswordEncoder bCryptPasswordEncoder;


    @Override
    public User saveUser(User user) {
        user.setPassword(bCryptPasswordEncoder.encode(user.getPassword()));
        user.setRoles(new HashSet<>(roleRepository.findAll()));
        return userRepository.save(user);
    }

    @Override
    public User findByUserName(String username) {

        return userRepository.findByUsername(username);
    }
}
