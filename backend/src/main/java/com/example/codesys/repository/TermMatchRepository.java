package com.example.codesys.repository;

import com.example.codesys.model.TermMatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TermMatchRepository extends JpaRepository<TermMatchEntity, Long> {
}
