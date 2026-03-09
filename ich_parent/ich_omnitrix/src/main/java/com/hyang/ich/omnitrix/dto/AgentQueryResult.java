package com.hyang.ich.omnitrix.dto;

import lombok.Data;

@Data
public class AgentQueryResult {

    public enum Status {
        SUCCESS,
        EMPTY,
        ERROR,
        NO_AUTH,
        ACTION_PROPOSED
    }

    private Status status;
    private String data;
    private String agentCode;
    private PendingAction pendingAction;

    public static AgentQueryResult success(String data, String agentCode) {
        AgentQueryResult r = new AgentQueryResult();
        r.setStatus(Status.SUCCESS);
        r.setData(data);
        r.setAgentCode(agentCode);
        return r;
    }

    public static AgentQueryResult empty(String agentCode) {
        AgentQueryResult r = new AgentQueryResult();
        r.setStatus(Status.EMPTY);
        r.setAgentCode(agentCode);
        return r;
    }

    public static AgentQueryResult error(String agentCode, String errorMsg) {
        AgentQueryResult r = new AgentQueryResult();
        r.setStatus(Status.ERROR);
        r.setData(errorMsg);
        r.setAgentCode(agentCode);
        return r;
    }

    public static AgentQueryResult noAuth(String agentCode) {
        AgentQueryResult r = new AgentQueryResult();
        r.setStatus(Status.NO_AUTH);
        r.setAgentCode(agentCode);
        return r;
    }

    public static AgentQueryResult actionProposed(String data, String agentCode, PendingAction action) {
        AgentQueryResult r = new AgentQueryResult();
        r.setStatus(Status.ACTION_PROPOSED);
        r.setData(data);
        r.setAgentCode(agentCode);
        r.setPendingAction(action);
        return r;
    }

    /**
     * 生成注入到 Prompt 的文本段
     */
    public String toPromptInjection() {
        switch (status) {
            case SUCCESS:
                if (data != null && !data.isEmpty()) {
                    return "[查询结果]\n" + data;
                }
                return "";
            case ACTION_PROPOSED:
                if (data != null && !data.isEmpty()) {
                    return "[查询结果]\n" + data + "\n[系统提示: 用户希望执行操作，请根据查询结果向用户确认操作详情，" +
                           "列出关键信息（商品名称/价格/活动名称/时间等），然后询问用户是否确认执行。" +
                           "不要自行执行，等待用户确认。]";
                }
                return "[系统提示: 未找到可操作的目标，请告知用户并建议更明确的描述]";
            case EMPTY:
                return "[系统提示: 未在平台数据库中找到相关内容，请基于你的知识回答，并告知用户平台暂无收录]";
            case ERROR:
                return "[系统提示: 数据查询服务暂时不可用，请基于你的知识尽力回答，并提示用户稍后再试]";
            case NO_AUTH:
                return "[系统提示: 该功能仅限管理员使用，请礼貌告知用户]";
            default:
                return "";
        }
    }
}
