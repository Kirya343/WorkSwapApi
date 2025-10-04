package org.workswap.api.sheduler;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.workswap.common.dto.stat.UsersStatSnapshotDTO;
import org.workswap.common.enums.UserType;
import org.workswap.core.services.producers.UsersStatProducer;
import org.workswap.core.services.query.UserQueryService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class UsersCounterSheduler {
    
    private final UsersStatProducer usersStatProducer;
    private final UserQueryService userQueryService;

    @Value("${isTest}")
    private boolean isTest;

    @Scheduled(fixedRate = 15000)
    public void saveOnlineAnalytic() {

        if (isTest == false) {
            UsersStatSnapshotDTO dto = new UsersStatSnapshotDTO(
                userQueryService.countByType(UserType.STANDART),
                userQueryService.countByType(UserType.TEMP),
                LocalDateTime.now()
            );

            usersStatProducer.sendUsersStat(dto);
        }
    }
}
