package com.aichatbot.repository;

import com.aichatbot.entity.Intent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IntentRepository extends JpaRepository<Intent, Long> {

    Optional<Intent> findByName(String name);

    boolean existsByName(String name);

    List<Intent> findAllByOrderByCategoryAscNameAsc();
}
