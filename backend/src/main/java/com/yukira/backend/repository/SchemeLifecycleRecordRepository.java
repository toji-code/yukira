package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeLifecycleRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SchemeLifecycleRecordRepository extends JpaRepository<SchemeLifecycleRecord, Long> {

    List<SchemeLifecycleRecord> findBySchemeOrderByEffectiveDateDesc(Scheme scheme);

    Optional<SchemeLifecycleRecord> findFirstBySchemeOrderByEffectiveDateDesc(Scheme scheme);
}

