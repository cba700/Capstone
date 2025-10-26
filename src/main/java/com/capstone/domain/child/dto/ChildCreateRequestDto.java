package com.capstone.domain.child.dto;

import com.capstone.domain.child.entity.Child;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChildCreateRequestDto {
    private String name;
    private int age;
    private Child.Gender gender;
}
