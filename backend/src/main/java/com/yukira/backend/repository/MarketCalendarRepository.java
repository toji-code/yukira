package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.MarketCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MarketCalendarRepository extends JpaRepository<MarketCalendar, LocalDate> {
    List<MarketCalendar> findByCalendarDateBetweenOrderByCalendarDateAsc(LocalDate startDate, LocalDate endDate);
}
