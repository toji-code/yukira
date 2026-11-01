package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.SchemeIdentifierAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SchemeIdentifierAliasRepository extends JpaRepository<SchemeIdentifierAlias, Long> {
    Optional<SchemeIdentifierAlias> findByIdentifierTypeAndIdentifierValueAndIsActiveTrue(String identifierType, String identifierValue);
    List<SchemeIdentifierAlias> findBySchemeOptionIdAndIsActiveTrue(Long schemeOptionId);
    List<SchemeIdentifierAlias> findByIdentifierValue(String identifierValue);

    @Query("SELECT a FROM SchemeIdentifierAlias a WHERE a.identifierValue = :val OR UPPER(a.identifierValue) = UPPER(:val)")
    List<SchemeIdentifierAlias> resolveByValue(@Param("val") String val);
}

