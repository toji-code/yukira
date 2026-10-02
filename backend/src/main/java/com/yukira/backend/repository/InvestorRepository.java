package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.Investor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvestorRepository extends JpaRepository<Investor, Long> {
    Optional<Investor> findByAuth0Subject(String auth0Subject);
}
