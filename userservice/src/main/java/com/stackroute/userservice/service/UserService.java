package com.stackroute.userservice.service;

import com.stackroute.userservice.model.User;

public interface UserService {

    User saveUser(User user);

    User findByUserName(String username);
}
