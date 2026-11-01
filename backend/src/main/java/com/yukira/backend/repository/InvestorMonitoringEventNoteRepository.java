package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.InvestorMonitoringEvent;
import com.yukira.backend.domain.entity.InvestorMonitoringEventNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvestorMonitoringEventNoteRepository extends JpaRepository<InvestorMonitoringEventNote, Long> {

    List<InvestorMonitoringEventNote> findByEventOrderByCreatedAtDesc(InvestorMonitoringEvent event);
}

