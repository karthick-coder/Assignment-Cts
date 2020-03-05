package com.stackroute.userservice.service;

import com.stackroute.userservice.model.User;

public interface UserService {

    void saveUser(User user);

    User findByUserName(String username);
}
