package com.hyang.ich.omnitrix.service;

import com.hyang.ich.omnitrix.mapper.AiUserBehaviorMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RecommendService {

    private final AiUserBehaviorMapper behaviorMapper;

    public RecommendService(AiUserBehaviorMapper behaviorMapper) {
        this.behaviorMapper = behaviorMapper;
    }

    /**
     * 获取用户兴趣标签（近30天行为数据）
     */
    public List<Map<String, Object>> getUserInterests(Long userId) {
        return behaviorMapper.selectUserInterests(userId, 30, 10);
    }
}
