package com.hospital.v2.service;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts simple appointment preferences from the patient's own words and ranks real slots. */
@Service
public class BookingPreferenceService {
    private static final Pattern WEEKDAY = Pattern.compile("(下周|本周|这周|周|星期)([一二三四五六日天])");

    public List<Map<String, Object>> rank(List<Map<String, Object>> slots, String conversation) {
        return rank(slots, conversation, LocalDate.now());
    }

    List<Map<String, Object>> rank(List<Map<String, Object>> slots, String conversation, LocalDate today) {
        Preference preference = parse(conversation == null ? "" : conversation, today);
        List<Map<String, Object>> ranked = new ArrayList<>();
        for (Map<String, Object> slot : slots) ranked.add(new LinkedHashMap<>(slot));
        ranked.sort(Comparator
                .comparingInt((Map<String, Object> slot) -> score(slot, preference))
                .thenComparing(slot -> LocalDate.parse(String.valueOf(slot.get("work_date"))))
                .thenComparing(slot -> String.valueOf(slot.get("period"))));
        for (Map<String, Object> slot : ranked) {
            boolean dateMatch = preference.date() == null || preference.date().equals(LocalDate.parse(String.valueOf(slot.get("work_date"))));
            boolean periodMatch = preference.period() == null || preference.period().equals(String.valueOf(slot.get("period")));
            slot.put("recommendation", dateMatch && periodMatch && (preference.date() != null || preference.period() != null)
                    ? "符合你说的就诊时间" : "按可预约时间排序");
        }
        return ranked;
    }

    Preference parse(String text, LocalDate today) {
        LocalDate date = null;
        if (text.contains("后天")) date = today.plusDays(2);
        else if (text.contains("明天")) date = today.plusDays(1);
        else if (text.contains("今天")) date = today;
        else {
            Matcher matcher = WEEKDAY.matcher(text);
            if (matcher.find()) {
                DayOfWeek target = weekday(matcher.group(2));
                date = today.with(TemporalAdjusters.nextOrSame(target));
                if ("下周".equals(matcher.group(1))) {
                    LocalDate nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
                    date = nextMonday.with(TemporalAdjusters.nextOrSame(target));
                }
            }
        }
        String period = null;
        if (text.matches("(?s).*(下午|午后|晚上|下班后).*$")) period = "PM";
        else if (text.matches("(?s).*(上午|早上|清晨).*$")) period = "AM";
        return new Preference(date, period);
    }

    private int score(Map<String, Object> slot, Preference preference) {
        LocalDate slotDate = LocalDate.parse(String.valueOf(slot.get("work_date")));
        String slotPeriod = String.valueOf(slot.get("period"));
        int score = preference.date() == null || preference.date().equals(slotDate) ? 0 : 4;
        if (preference.period() != null && !preference.period().equals(slotPeriod)) score += 2;
        return score;
    }

    private DayOfWeek weekday(String chinese) {
        return switch (chinese) {
            case "一" -> DayOfWeek.MONDAY;
            case "二" -> DayOfWeek.TUESDAY;
            case "三" -> DayOfWeek.WEDNESDAY;
            case "四" -> DayOfWeek.THURSDAY;
            case "五" -> DayOfWeek.FRIDAY;
            case "六" -> DayOfWeek.SATURDAY;
            default -> DayOfWeek.SUNDAY;
        };
    }

    record Preference(LocalDate date, String period) {}
}
