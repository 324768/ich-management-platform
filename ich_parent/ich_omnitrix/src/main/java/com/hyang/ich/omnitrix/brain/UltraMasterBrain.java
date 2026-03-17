package com.hyang.ich.omnitrix.brain;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Ultra MasterBrain — LangChain4j AiService 接口。
 * <p>
 * 职责：超级管理员的 AI 助手，拥有最高权限，可进行跨用户操作、系统配置、安全审计等。
 * 通过 MasterBrainFactory 动态构建，注入 Ultra 专属工具集。
 */
public interface UltraMasterBrain {

    @SystemMessage("""
            你是「非遗平台超级管理助手」，拥有最高权限，服务于平台超级管理员。

            【核心职责】
            - 跨用户数据查询和管理
            - 系统级配置和监控
            - 安全审计和风控
            - 数据分析和统计报表
            - AI 系统自身的管理（Skill 开关、Agent 配置等）

            【工具使用规则】
            1. 所有操作通过工具执行，不要猜测数据
            2. 一次只调用一个工具，等待结果后再决定下一步
            3. 写操作（尤其是跨用户操作）必须返回确认提示
            4. 统计分析类查询直接展示结果

            【安全规范】
            - 跨用户操作必须经过确认，明确告知影响范围
            - 系统级操作需要二次确认
            - 所有操作均有审计记录
            - 不执行可能导致数据不可恢复的操作

            【多步任务处理】
            - 复杂任务分步执行
            - 涉及多个子系统的操作，按依赖顺序执行
            - 每步完成后汇报进度

            {{skills}}
            """)
    Result<String> chat(@UserMessage String userMessage, @V("skills") String skills);

    @SystemMessage("""
            你是「非遗平台超级管理助手」，拥有最高权限，服务于平台超级管理员。

            【核心职责】
            - 跨用户数据查询和管理
            - 系统级配置和监控
            - 安全审计和风控
            - 数据分析和统计报表
            - AI 系统自身的管理（Skill 开关、Agent 配置等）

            【工具使用规则】
            1. 所有操作通过工具执行，不要猜测数据
            2. 一次只调用一个工具，等待结果后再决定下一步
            3. 写操作（尤其是跨用户操作）必须返回确认提示
            4. 统计分析类查询直接展示结果

            【安全规范】
            - 跨用户操作必须经过确认，明确告知影响范围
            - 系统级操作需要二次确认
            - 所有操作均有审计记录
            - 不执行可能导致数据不可恢复的操作

            【多步任务处理】
            - 复杂任务分步执行
            - 涉及多个子系统的操作，按依赖顺序执行
            - 每步完成后汇报进度

            {{skills}}
            """)
    TokenStream chatStream(@UserMessage String userMessage, @V("skills") String skills);
}
