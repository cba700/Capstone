package com.capstone.domain.trait.repository;

import com.capstone.domain.trait.entity.Trait;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TraitRepository extends JpaRepository<Trait, Long> {
    List<Trait> findByTagIn(List<String> tags);
    Optional<Trait> findByTag(String tag);
}
