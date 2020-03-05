package com.stackroute.userservice.service;

import com.stackroute.userservice.model.Role;
import com.stackroute.userservice.model.User;
import com.stackroute.userservice.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.HashSet;
import java.util.Set;


@AllArgsConstructor
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

        private final UserRepository userRepository;

        @Override
        @Transactional
        public UserDetails loadUserByUsername(String username) {
                User user = userRepository.findByUsername(username);
                if (user == null) throw new UsernameNotFoundException(username);

                Set<GrantedAuthority> grantedAuthorities = new HashSet<>();
                for (Role role : user.getRoles()) {
                        grantedAuthorities.add(new SimpleGrantedAuthority(role.getName()));
                }

                return new org.springframework.security.core.userdetails.User(user.getUsername()
                        , user.getPassword(), grantedAuthorities);
        }
}