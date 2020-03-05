package com.stackroute.userservice.model;

import lombok.Data;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.Set;

@Data
@Entity
@Table(name = "role")
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message ="RoleName is mandatory")
    private String name;

    @ManyToMany(mappedBy = "roles")
    private Set<User> users;
}