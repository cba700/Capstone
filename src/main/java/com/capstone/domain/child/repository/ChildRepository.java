package com.capstone.domain.child.repository;

import com.capstone.domain.child.entity.Child;
import com.capstone.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChildRepository extends JpaRepository<Child, Long> {
    List<Child> findByUser(User user);
}
