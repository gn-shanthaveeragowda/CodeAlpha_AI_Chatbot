package com.aichatbot.repository;

import com.aichatbot.entity.Response;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResponseRepository extends JpaRepository<Response, Long> {

    List<Response> findByIntentName(String intentName);

    List<Response> findByIntentNameAndResponseType(String intentName, Response.ResponseType responseType);

    boolean existsByIntentNameAndResponseTypeAndResponseText(String intentName,
                                                            Response.ResponseType responseType,
                                                            String responseText);

    long countByIntentName(String intentName);
}
