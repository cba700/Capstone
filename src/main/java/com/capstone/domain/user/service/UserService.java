package com.capstone.domain.user.service;

import com.capstone.domain.user.dto.UserRegisterRequestDto;
import com.capstone.domain.user.entity.User;
import com.capstone.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User signUp(UserRegisterRequestDto requestDto) {
        if (userRepository.findByEmail(requestDto.getEmail()).isPresent()) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        User user = new User(
                requestDto.getEmail(),
                passwordEncoder.encode(requestDto.getPassword())
        );

        return userRepository.save(user);
    }
}
