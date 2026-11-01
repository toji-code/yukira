package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.SourcePublicationCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SourcePublicationCalendarRepository extends JpaRepository<SourcePublicationCalendar, Long> {
    Optional<SourcePublicationCalendar> findBySourceNameAndIsActiveTrue(String sourceName);
    List<SourcePublicationCalendar> findByIsActiveTrue();
    List<SourcePublicationCalendar> findAllByOrderBySourceNameAsc();
}

