package com.capstone.domain.dto;

import com.capstone.domain.entity.User;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRegisterRequestDto {
    private String parentName;
    private String childName;
    private String password;
    private String email;

    public User toEntity() {
        User user = new User();
        user.setParentName(parentName);
        user.setChildName(childName);
        user.setPassword(password);
        user.setEmail(email);
        return user;
    }
}