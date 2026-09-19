package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;

@Entity
@Table(name = "market_calendar")
public class MarketCalendar implements Serializable {

    @Id
    @Column(name = "calendar_date", nullable = false)
    private LocalDate calendarDate;

    @Column(name = "is_trading_day", nullable = false)
    private boolean isTradingDay = true;

    @Column(name = "exchange", nullable = false, length = 20)
    private String exchange = "NSE";

    @Column(name = "holiday_name", length = 100)
    private String holidayName;

    public MarketCalendar() {}

    public MarketCalendar(LocalDate calendarDate, boolean isTradingDay, String exchange, String holidayName) {
        this.calendarDate = calendarDate;
        this.isTradingDay = isTradingDay;
        this.exchange = exchange;
        this.holidayName = holidayName;
    }

    public LocalDate getCalendarDate() { return calendarDate; }
    public void setCalendarDate(LocalDate calendarDate) { this.calendarDate = calendarDate; }

    public boolean isTradingDay() { return isTradingDay; }
    public void setTradingDay(boolean tradingDay) { isTradingDay = tradingDay; }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public String getHolidayName() { return holidayName; }
    public void setHolidayName(String holidayName) { this.holidayName = holidayName; }
}
