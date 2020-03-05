package com.stackroute.userservice.controller;

import com.stackroute.userservice.model.User;
import com.stackroute.userservice.repository.UserRepository;
import com.stackroute.userservice.service.SecurityService;
import com.stackroute.userservice.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@AllArgsConstructor
@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    private final SecurityService securityService;

    @PostMapping("/registration")
    public ResponseEntity<User> createUser(@RequestBody User user){

        return Optional.ofNullable(userService.saveUser(user))
                .map(response->new ResponseEntity<User>((User) response, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
    }
    @PostMapping("/login")
    public String validateLogin(@RequestBody User user){
        securityService.autoLogin(user.getUsername(),user.getPassword());
        return "Logged in successfully";
    }
}
