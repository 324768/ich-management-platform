package com.hyang.ich.omnitrix.config;

import com.hyang.ich.user.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务配置 —— 定期清理不活跃用户的在线状态
 */
@Slf4j
@Component
@EnableScheduling
public class ScheduledTaskConfig {

    private static final int INACTIVE_TIMEOUT_MINUTES = 30;

    private final UserService userService;

    public ScheduledTaskConfig(UserService userService) {
        this.userService = userService;
    }

    /**
     * 每 5 分钟清理超过 30 分钟无活跃的用户在线状态
     */
    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 60 * 1000)
    public void clearInactiveOnlineUsers() {
        try {
            int cleared = userService.clearInactiveUsers(INACTIVE_TIMEOUT_MINUTES);
            if (cleared > 0) {
                log.info("定时清理不活跃在线状态: {} 人", cleared);
            }
        } catch (Exception e) {
            log.debug("清理不活跃在线状态失败: {}", e.getMessage());
        }
    }
}
