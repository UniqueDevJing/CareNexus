package com.hospital.v2.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BookingPreferenceServiceTest {
    private final BookingPreferenceService service = new BookingPreferenceService();

    @Test
    void parsesRelativeDateAndPeriod() {
        var preference = service.parse("我后天下午有时间", LocalDate.of(2026, 11, 25));
        assertEquals(LocalDate.of(2026, 11, 27), preference.date());
        assertEquals("PM", preference.period());
    }

    @Test
    void parsesNextWeekday() {
        var preference = service.parse("改到下周二上午", LocalDate.of(2026, 11, 25));
        assertEquals(LocalDate.of(2026, 12, 1), preference.date());
        assertEquals("AM", preference.period());
    }

    @Test
    void ranksMatchingSlotFirstWithoutMutatingInput() {
        List<Map<String, Object>> slots = List.of(
                Map.of("id", 1L, "work_date", "2026-11-26", "period", "AM", "remain_num", 2),
                Map.of("id", 2L, "work_date", "2026-11-27", "period", "PM", "remain_num", 3));
        var ranked = service.rank(slots, "后天下午", LocalDate.of(2026, 11, 25));
        assertEquals("PM", ranked.get(0).get("period"));
        assertEquals(2L, ranked.get(0).get("id"));
        assertEquals(4, slots.get(0).size());
    }
}
