package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.SchemeManagerHist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SchemeManagerHistRepository extends JpaRepository<SchemeManagerHist, Long> {

    List<SchemeManagerHist> findBySchemeIdOrderByStartDateDesc(Long schemeId);

    List<SchemeManagerHist> findBySchemeIdAndEndDateIsNullOrderByStartDateDesc(Long schemeId);
}
