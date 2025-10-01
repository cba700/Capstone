package com.capstone.domain.service;

import com.capstone.domain.dto.UserRegisterRequestDto;
import com.capstone.domain.entity.User;
import com.capstone.domain.repository.UserRepository;
import com.capstone.global.exception.EmailDuplicateException;
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
    public void registerUser(UserRegisterRequestDto requestDto) {
        if (userRepository.findByEmail(requestDto.getEmail()).isPresent()) {
            throw new EmailDuplicateException("이미 사용 중인 이메일입니다.");
        }
        User user = requestDto.toEntity();
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);
    }
}