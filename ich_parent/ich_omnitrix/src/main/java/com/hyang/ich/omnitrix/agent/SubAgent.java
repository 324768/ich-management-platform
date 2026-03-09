package com.hyang.ich.omnitrix.agent;

import com.hyang.ich.omnitrix.dto.AgentQueryResult;

public interface SubAgent {

    /** 子代理唯一编码 */
    String getCode();

    /** 子代理名称 */
    String getName();

    /** 子代理描述 */
    String getDescription();

    /** 角色切换 Prompt（注入到主脑 System Prompt 尾部） */
    String getAgentPrompt();

    /** 执行子代理逻辑 */
    AgentQueryResult execute(String userQuery, AgentContext context);
}
