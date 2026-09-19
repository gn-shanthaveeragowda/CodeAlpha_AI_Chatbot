package com.aichatbot.repository;

import com.aichatbot.entity.TrainingExample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TrainingExampleRepository extends JpaRepository<TrainingExample, Long> {

    boolean existsByExampleTextIgnoreCaseAndIntent_Name(String exampleText, String intentName);

    boolean existsByExampleTextIgnoreCase(String exampleText);

    List<TrainingExample> findByIntentName(String intentName);

    long countByIntentName(String intentName);

    long countBySource(TrainingExample.Source source);

    /** Loads every example with its intent in one query, avoiding N+1 during training. */
    @Query("select t from TrainingExample t join fetch t.intent")
    List<TrainingExample> findAllWithIntent();
}
