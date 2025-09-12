package com.example.codesys.repository;

import com.example.codesys.model.AiDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiDefinitionRepository extends JpaRepository<AiDefinitionEntity, Long> {
}
