package com.capstone.domain.child.service;

import com.capstone.domain.child.dto.ChildCreateRequestDto;
import com.capstone.domain.child.entity.Child;
import com.capstone.domain.child.repository.ChildRepository;
import com.capstone.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChildService {

    private final ChildRepository childRepository;

    public Child createChild(ChildCreateRequestDto requestDto, User user) {
        Child child = Child.builder()
                .name(requestDto.getName())
                .age(requestDto.getAge())
                .gender(requestDto.getGender())
                .user(user)
                .build();
        return childRepository.save(child);
    }

    @Transactional(readOnly = true)
    public List<Child> findMyChildren(User user) {
        return childRepository.findByUser(user);
    }

    @Transactional(readOnly = true)
    public Optional<Child> findChildById(Long id) {
        return childRepository.findById(id);
    }

    public void deleteChild(Long id) {
        childRepository.deleteById(id);
    }
}
