package com.hyang.ich.omnitrix.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 待确认操作 —— AI 提议的写操作，需用户确认后执行
 */
@Data
public class PendingAction implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 操作类型 */
    private String actionType;
    /** 操作描述（用于 LLM Prompt 注入） */
    private String description;
    /** 操作参数 */
    private Map<String, String> params;
    /** 创建时间戳 */
    private long createdAt;

    public static PendingAction of(String actionType, String description) {
        PendingAction action = new PendingAction();
        action.setActionType(actionType);
        action.setDescription(description);
        action.setParams(new HashMap<>());
        action.setCreatedAt(System.currentTimeMillis());
        return action;
    }

    public PendingAction param(String key, String value) {
        if (this.params == null) this.params = new HashMap<>();
        this.params.put(key, value);
        return this;
    }

    public String getParam(String key) {
        return params != null ? params.get(key) : null;
    }

    public Long getParamAsLong(String key) {
        String v = getParam(key);
        return v != null ? Long.parseLong(v) : null;
    }

    public Integer getParamAsInt(String key) {
        String v = getParam(key);
        return v != null ? Integer.parseInt(v) : null;
    }

    /** 是否在 5 分钟内有效 */
    @JsonIgnore
    public boolean isExpired() {
        return System.currentTimeMillis() - createdAt > 5 * 60 * 1000;
    }
}
