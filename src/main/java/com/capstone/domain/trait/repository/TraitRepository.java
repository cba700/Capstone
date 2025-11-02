package com.capstone.domain.trait.repository;

import com.capstone.domain.trait.entity.Trait;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TraitRepository extends JpaRepository<Trait, Long> {
    List<Trait> findByTagIn(List<String> tags);
}
