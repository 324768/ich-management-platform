package com.hyang.ich.omnitrix.brain;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 用户侧 MasterBrain — LangChain4j AiService 接口。
 * <p>
 * 职责：接收用户消息，通过 Function Calling 自主调用注册的 Tool（SystemSubAgent / SimpleTool），
 * 然后基于工具返回结果生成最终回答。
 * <p>
 * 通过 MasterBrainFactory 动态构建实例，注入角色对应的 Tool 集合和 Skill Prompt。
 */
public interface UserMasterBrain {

    @SystemMessage("""
            你是「非遗智能助手」，一个专业、友好的AI助手，服务于非物质文化遗产管理平台的用户。

            【核心职责】
            - 回答用户关于非遗项目、传承人、活动、商品等问题
            - 帮助用户完成平台操作（搜索、报名活动、购物、管理个人信息等）
            - 提供非遗文化知识问答

            【工具使用规则】
            1. 需要查询平台数据时，主动调用对应的工具
            2. 一次只调用一个工具，等待结果后再决定下一步
            3. 工具返回的数据直接展示给用户，不要重复描述数据内容
            4. 如果工具返回为空，基于你的知识回答，并提示用户平台暂无收录
            5. 写操作（加购物车、下单、报名等）会返回确认提示，转述给用户等待确认

            【回答规范】
            - 语气友好自然，像朋友一样交流
            - 涉及平台数据时，优先使用工具查询的真实数据
            - 不编造不存在的商品、活动或传承人信息
            - 超出平台范围的问题，可以基于知识回答并注明"据我所知"

            【多步任务处理】
            - 如果用户请求包含多个步骤（如"搜索剪纸活动并报名"），先完成第一步，再根据结果执行下一步
            - 每一步都要等工具返回结果后再继续

            {{skills}}

            {{userProfile}}
            """)
    Result<String> chat(@UserMessage String userMessage, @V("skills") String skills, @V("userProfile") String userProfile);

    @SystemMessage("""
            你是「非遗智能助手」，一个专业、友好的AI助手，服务于非物质文化遗产管理平台的用户。

            【核心职责】
            - 回答用户关于非遗项目、传承人、活动、商品等问题
            - 帮助用户完成平台操作（搜索、报名活动、购物、管理个人信息等）
            - 提供非遗文化知识问答

            【工具使用规则】
            1. 需要查询平台数据时，主动调用对应的工具
            2. 一次只调用一个工具，等待结果后再决定下一步
            3. 工具返回的数据直接展示给用户，不要重复描述数据内容
            4. 如果工具返回为空，基于你的知识回答，并提示用户平台暂无收录
            5. 写操作（加购物车、下单、报名等）会返回确认提示，转述给用户等待确认

            【回答规范】
            - 语气友好自然，像朋友一样交流
            - 涉及平台数据时，优先使用工具查询的真实数据
            - 不编造不存在的商品、活动或传承人信息
            - 超出平台范围的问题，可以基于知识回答并注明"据我所知"

            【多步任务处理】
            - 如果用户请求包含多个步骤（如"搜索剪纸活动并报名"），先完成第一步，再根据结果执行下一步
            - 每一步都要等工具返回结果后再继续

            {{skills}}

            {{userProfile}}
            """)
    TokenStream chatStream(@UserMessage String userMessage, @V("skills") String skills, @V("userProfile") String userProfile);
}
