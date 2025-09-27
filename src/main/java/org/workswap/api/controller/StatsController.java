package org.workswap.api.controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.workswap.common.enums.IntervalType;
import org.workswap.datasource.stats.model.ListingStatSnapshot;
import org.workswap.datasource.stats.repository.ListingStatRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final ListingStatRepository listingStatRepository;

    @GetMapping("/views")
    @PreAuthorize("hasAuthority('VIEW_LISTING_STATS')")
    public ResponseEntity<List<Map<String, Object>>> getViewsStats(
            @RequestParam Long listingId,
            @RequestParam IntervalType interval,
            @RequestParam(required = false, defaultValue = "7") int days
    ) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm dd.MM.yyyy");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fromTime = LocalDateTime.now().minusDays(days);

        List<ListingStatSnapshot> stats = listingStatRepository.findByListingIdAndIntervalTypeAndTimeAfter(
                listingId,
                interval,
                fromTime
        );

        Optional<ListingStatSnapshot> lastBefore = listingStatRepository.findTopByListingIdAndIntervalTypeAndTimeBeforeOrderByTimeDesc(
                listingId,
                interval,
                fromTime
        );

        // Печать, если не было вообще данных
        if (stats.isEmpty() && lastBefore.isEmpty()) {
            System.out.println("Вообще нет данных в базе даже до fromTime");
        }

        int lastKnownViews = lastBefore.map(ListingStatSnapshot::getViews).orElse(0);

        System.out.println("Найдено снапшотов в базе: " + stats.size());

        // Выбираем шаг в зависимости от типа интервала
        Duration step = switch (interval) {
            case FIVE_MINUTES -> Duration.ofMinutes(5);
            case HOURLY -> Duration.ofHours(1);
            case DAILY -> Duration.ofDays(1);
            case WEEKLY -> Duration.ofDays(7);
            // Добавь другие варианты, если нужно
        };

        Map<LocalDateTime, Integer> timeToViews = stats.stream()
            .collect(Collectors.toMap(
                s -> roundToStep(s.getTime(), step),
                ListingStatSnapshot::getViews,
                (a, b) -> b
            ));

        List<Map<String, Object>> chartData = new ArrayList<>();
        
        int fakePoints = 0;
        int realPoints = 0;
        int points = 0;

        for (LocalDateTime time = roundToStep(fromTime, step); !time.isAfter(now); time = time.plus(step)) {
            // Проверка, есть ли реальная точка в данных
            if (timeToViews.containsKey(time)) {
                lastKnownViews = timeToViews.get(time);
                realPoints++;
            } else {
                fakePoints++;            
            }
            points++;

            Map<String, Object> point = new HashMap<>();
            point.put("x", time.format(formatter)); // можно заменить на time.format(formatter) для читаемости
            point.put("y", lastKnownViews);
            chartData.add(point);
        }

        System.out.println("Всего точек на графике создано: " + points);
        System.out.println("Реальных точек найдено: " + realPoints);
        System.out.println("Фейковых точек создано: " + fakePoints);

        return ResponseEntity.ok(chartData);
    }

    private LocalDateTime roundToStep(LocalDateTime time, Duration step) {
        long seconds = step.getSeconds();
        long timestamp = time.toEpochSecond(ZoneOffset.UTC);
        long rounded = (timestamp / seconds) * seconds;
        return LocalDateTime.ofEpochSecond(rounded, 0, ZoneOffset.UTC);
    }
}