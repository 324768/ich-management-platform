package com.hyang.ich.omnitrix.service;

import com.hyang.ich.omnitrix.entity.AiConversation;
import com.hyang.ich.omnitrix.entity.AiMessage;
import com.hyang.ich.omnitrix.mapper.AiConversationMapper;
import com.hyang.ich.omnitrix.mapper.AiMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ConversationService {

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;

    public ConversationService(AiConversationMapper conversationMapper,
                               AiMessageMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    /**
     * 根据 sessionId 查找对话，不存在则自动创建
     */
    public AiConversation findOrCreate(String sessionId, Long userId) {
        AiConversation conv = conversationMapper.selectBySessionId(sessionId);
        if (conv != null) {
            return conv;
        }

        conv = new AiConversation();
        conv.setUserId(userId);
        conv.setSessionId(sessionId);
        conv.setTitle("新对话");
        conv.setMessageCount(0);
        conv.setStatus(1);
        conversationMapper.insert(conv);

        log.info("创建新对话: sessionId={}, userId={}, id={}", sessionId, userId, conv.getId());
        return conv;
    }

    /**
     * 创建新对话（用户主动创建）
     */
    public AiConversation create(Long userId, String title) {
        AiConversation conv = new AiConversation();
        conv.setUserId(userId);
        conv.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        conv.setTitle(title != null ? title : "新对话");
        conv.setMessageCount(0);
        conv.setStatus(1);
        conversationMapper.insert(conv);
        return conv;
    }

    /**
     * 获取用户对话列表
     */
    public List<AiConversation> listByUserId(Long userId) {
        return conversationMapper.selectByUserId(userId);
    }

    /**
     * 获取对话详情（含消息列表）
     */
    public AiConversation getById(Long id) {
        return conversationMapper.selectById(id);
    }

    /**
     * 获取对话的消息列表
     */
    public List<AiMessage> listMessages(Long conversationId) {
        return messageMapper.selectByConversationId(conversationId);
    }

    /**
     * 获取对话的消息列表（按 sessionId）
     */
    public List<AiMessage> listMessagesBySessionId(String sessionId) {
        return messageMapper.selectBySessionId(sessionId);
    }

    /**
     * 保存消息
     */
    @Transactional(rollbackFor = Exception.class)
    public AiMessage saveMessage(Long conversationId, String sessionId, String role,
                                  String content, Integer tokens, String model,
                                  String subAgent, Integer latencyMs) {
        AiMessage msg = new AiMessage();
        msg.setConversationId(conversationId);
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setTokens(tokens);
        msg.setModel(model);
        msg.setSubAgent(subAgent);
        msg.setLatencyMs(latencyMs);
        messageMapper.insert(msg);

        // 原子自增消息计数（替代 COUNT 全表查询）
        conversationMapper.incrementMessageCount(conversationId);

        return msg;
    }

    /**
     * 软删除对话
     */
    public void deleteConversation(Long id, Long userId) {
        AiConversation conv = conversationMapper.selectById(id);
        if (conv != null && conv.getUserId().equals(userId)) {
            conversationMapper.softDelete(id);
        }
    }

    /**
     * 更新对话标题
     */
    public void updateTitle(Long id, String title) {
        conversationMapper.updateTitle(id, title);
    }

    /**
     * 获取 session 中最后一条指定角色的消息
     */
    public AiMessage getLastMessage(String sessionId, String role) {
        return messageMapper.selectLastBySessionIdAndRole(sessionId, role);
    }

    /**
     * 删除指定消息
     */
    public void deleteMessage(Long messageId) {
        messageMapper.deleteById(messageId);
    }
}
