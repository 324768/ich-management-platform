package com.hyang.ich.omnitrix.agent.impl;

import com.hyang.ich.omnitrix.agent.AgentContext;
import com.hyang.ich.omnitrix.agent.SubAgent;
import com.hyang.ich.omnitrix.dto.AgentQueryResult;
import org.springframework.stereotype.Component;

@Component
public class GeneralSubAgent implements SubAgent {

    @Override
    public String getCode() {
        return "general_assistant";
    }

    @Override
    public String getName() {
        return "通用问答助手";
    }

    @Override
    public String getDescription() {
        return "处理不属于任何特定领域的通用问题";
    }

    @Override
    public String getAgentPrompt() {
        return "";
    }

    @Override
    public AgentQueryResult execute(String userQuery, AgentContext context) {
        return AgentQueryResult.success(null, getCode());
    }
}
