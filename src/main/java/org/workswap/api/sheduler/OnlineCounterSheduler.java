package org.workswap.api.sheduler;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.workswap.common.dto.stat.OnlineStatSnapshotDTO;
import org.workswap.core.services.analytic.OnlineCounter;
import org.workswap.core.services.producers.OnlineStatProducer;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class OnlineCounterSheduler {
    
    private final OnlineCounter onlineCounter;
    private final OnlineStatProducer onlineStatProducer;

    @Value("${isTest}")
    private boolean isTest;

    @Scheduled(fixedRate = 15000)
    public void saveOnlineAnalytic() {

        if (isTest == false) {
            OnlineStatSnapshotDTO dto = new OnlineStatSnapshotDTO();
            dto.setOnline(onlineCounter.getCurrent());
            dto.setTimestamp(LocalDateTime.now());

            onlineStatProducer.sendOnlineStat(dto);
        }
    }
}
