package com.capstone.domain.trait.repository;

import com.capstone.domain.trait.entity.Trait;
import com.capstone.domain.trait.entity.TraitJob;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TraitJobRepository extends JpaRepository<TraitJob, Long> {
    List<TraitJob> findByTraitIn(List<Trait> traits);
}
