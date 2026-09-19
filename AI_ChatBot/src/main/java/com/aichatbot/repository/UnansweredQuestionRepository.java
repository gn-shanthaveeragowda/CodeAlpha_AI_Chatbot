package com.aichatbot.repository;

import com.aichatbot.entity.UnansweredQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UnansweredQuestionRepository extends JpaRepository<UnansweredQuestion, Long> {

    Optional<UnansweredQuestion> findByNormalizedQuestion(String normalizedQuestion);

    List<UnansweredQuestion> findTop20ByResolvedFalseOrderByTimesAskedDescLastAskedAtDesc();

    long countByResolvedFalse();
}
