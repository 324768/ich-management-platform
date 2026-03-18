package com.hyang.ich.omnitrix.infrastructure.skill;

import com.hyang.ich.omnitrix.infrastructure.prompt.HeritageSkillPrompt;
import com.hyang.ich.omnitrix.service.SkillConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skill配置中心 - 管理所有可用的Skills
 * 支持数据库动态配置 + 代码默认配置
 * 管理员可在后台开启/关闭Skills
 */
@Slf4j
@Component
public class SkillPromptConfig {

    /**
     * 所有可用的Skills集合
     * key: skillId, value: Skill对象
     */
    private final Map<String, Skill> skills = new LinkedHashMap<>();

    /**
     * 版本缓存，用于检测配置变化
     */
    private final Map<String, Integer> skillVersions = new ConcurrentHashMap<>();

    private final SkillConfigService skillConfigService;

    public SkillPromptConfig(SkillConfigService skillConfigService) {
        this.skillConfigService = skillConfigService;
    }

    @PostConstruct
    public void init() {
        loadFromDatabase();
    }

    /**
     * 从数据库加载Skill配置，与代码默认配置合并
     */
    public void loadFromDatabase() {
        try {
            List<com.hyang.ich.omnitrix.entity.AiSkillConfig> dbSkills = skillConfigService.getAllSkills();
            if (dbSkills != null && !dbSkills.isEmpty()) {
                log.info("从数据库加载 {} 个Skill配置", dbSkills.size());
                for (com.hyang.ich.omnitrix.entity.AiSkillConfig dbSkill : dbSkills) {
                    Skill skill = convertToSkill(dbSkill);
                    skills.put(skill.getId(), skill);
                    skillVersions.put(skill.getId(), dbSkill.getVersion());
                }
            } else {
                // 数据库为空，使用代码默认配置
                log.info("数据库无Skill配置，使用代码默认配置");
                initDefaultSkills();
            }
        } catch (Exception e) {
            log.warn("从数据库加载Skill配置失败，使用代码默认配置: {}", e.getMessage());
            initDefaultSkills();
        }
    }

    /**
     * 刷新配置（当数据库配置更新时调用）
     */
    public void refresh() {
        log.info("刷新Skill配置...");
        loadFromDatabase();
    }

    /**
     * 检查是否需要刷新（基于版本号）
     */
    public boolean needsRefresh() {
        try {
            for (String skillId : skills.keySet()) {
                Integer dbVersion = skillConfigService.getSkillVersion(skillId);
                Integer cachedVersion = skillVersions.get(skillId);
                if (dbVersion != null && !dbVersion.equals(cachedVersion)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("检查Skill版本失败: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 将数据库实体转换为Skill对象
     */
    private Skill convertToSkill(com.hyang.ich.omnitrix.entity.AiSkillConfig dbSkill) {
        String prompt = dbSkill.getSystemPrompt();
        if (prompt == null || prompt.isEmpty()) {
            // 如果数据库没有Prompt，使用代码默认的
            prompt = getDefaultPrompt(dbSkill.getSkillId());
        }

        String[] keywords = null;
        if (dbSkill.getKeywords() != null && !dbSkill.getKeywords().isEmpty()) {
            keywords = dbSkill.getKeywords().split(",");
        }

        // 构建触发条件（基于description生成）
        String triggerCondition = "当用户询问" + dbSkill.getDescription() + "相关问题时激活";

        // 从数据库字段构建完整Skill（支持扩展）
        return new Skill(
            dbSkill.getSkillId(),
            dbSkill.getSkillName(),
            dbSkill.getDescription(),
            prompt,
            triggerCondition,  // triggerCondition
            null,              // examples
            null,              // resourcePaths
            keywords,
            dbSkill.getEnabled() != null && dbSkill.getEnabled() == 1,
            0,                 // priority
            "general"          // category
        );
    }

    /**
     * 获取默认Prompt（代码内置）
     */
    private String getDefaultPrompt(String skillId) {
        switch (skillId) {
            case "heritage_master":
                return HeritageSkillPrompt.getHeritageMasterPrompt();
            case "shopping_advisor":
                return getShoppingAdvisorPrompt();
            case "customer_service":
                return getCustomerServicePrompt();
            case "knowledge_expert":
                return getKnowledgeExpertPrompt();
            case "recommend_expert":
                return getRecommendExpertPrompt();
            case "security_audit":
                return getSecurityAuditPrompt();
            case "quality_evaluator":
                return getQualityEvaluatorPrompt();
            default:
                return "";
        }
    }

    /**
     * 初始化代码默认Skills（作为兜底）
     * 参考Claude Skill设计：每个Skill包含触发条件、使用示例等
     */
    private void initDefaultSkills() {
        // 1. 非遗传统文化大师
        Skill heritageSkill = new Skill(
            "heritage_master",
            "非遗文化大师",
            "回答非遗项目、传承人、传统文化、历史典故等问题，兼具学术性与趣味性",
            HeritageSkillPrompt.getHeritageMasterPrompt(),
            "当用户询问关于非遗项目、传承人、传统文化、历史典故、民间艺术等问题时激活",
            new String[]{
                "给我讲讲京剧的历史",
                "什么是剪纸艺术？",
                "有哪些非遗传承人？"
            },
            null,
            new String[]{"非遗", "传承人", "传统文化", "昆曲", "京剧", "剪纸", "陶瓷", "刺绣", "武术", "中医", "节日", "民俗", "历史", "文化", "传统技艺", "手工"},
            true,
            10,
            "文化"
        );
        skills.put(heritageSkill.getId(), heritageSkill);

        // 2. 购物顾问
        Skill shoppingSkill = new Skill(
            "shopping_advisor",
            "购物顾问",
            "回答商品咨询、推荐、选购建议等问题，了解文创产品特点",
            getShoppingAdvisorPrompt(),
            "当用户询问商品价格、推荐、购买、材质、性价比等问题时激活",
            new String[]{
                "这件商品怎么样？",
                "有什么适合送人的礼物吗？",
                "这件衣服是什么材质的？"
            },
            null,
            new String[]{"商品", "购买", "推荐", "选购", "价格", "材质", "做工", "文创", "商城", "礼物", "送人", "性价比"},
            true,
            10,
            "商业"
        );
        skills.put(shoppingSkill.getId(), shoppingSkill);

        // 3. 客服话术师
        Skill customerServiceSkill = new Skill(
            "customer_service",
            "客服话术师",
            "处理用户投诉、售后问题、退换货等，态度耐心亲切",
            getCustomerServicePrompt(),
            "当用户表达不满、投诉、询问退换货、售后问题时报活",
            new String[]{
                "我要投诉",
                "商品坏了怎么退货？",
                "物流太慢了"
            },
            null,
            new String[]{"投诉", "售后", "退货", "换货", "退款", "质量问题", "态度", "客服", "物流", "订单"},
            true,
            10,
            "服务"
        );
        skills.put(customerServiceSkill.getId(), customerServiceSkill);

        // 4. 知识百科达人
        Skill knowledgeSkill = new Skill(
            "knowledge_expert",
            "知识百科达人",
            "回答平台知识库相关问题，基于知识库内容准确回答",
            getKnowledgeExpertPrompt(),
            "当用户询问平台功能使用方法、会员问题、积分规则等问题时激活",
            new String[]{
                "怎么成为会员？",
                "积分有什么用？",
                "如何参加活动？"
            },
            null,
            new String[]{"知识库", "FAQ", "常见问题", "帮助", "如何使用", "怎么操作", "功能", "会员", "积分", "规则"},
            true,
            10,
            "知识"
        );
        skills.put(knowledgeSkill.getId(), knowledgeSkill);

        // 5. 智能推荐解读者
        Skill recommendSkill = new Skill(
            "recommend_expert",
            "推荐解读者",
            "解读个性化推荐逻辑，分析用户兴趣偏好",
            getRecommendExpertPrompt(),
            "当用户询问为什么推荐某个商品/内容，或想了解推荐原因时激活",
            new String[]{
                "为什么给我推荐这个？",
                "推荐理由是什么？",
                "你喜欢什么类型的商品？"
            },
            null,
            new String[]{"推荐", "为什么推荐", "猜你喜欢", "兴趣", "偏好", "个性化", "推荐理由"},
            true,
            5,
            "分析"
        );
        skills.put(recommendSkill.getId(), recommendSkill);

        // 6. 安全审核员
        Skill securitySkill = new Skill(
            "security_audit",
            "安全审核员",
            "内容安全审核，过滤敏感信息，确保合规",
            getSecurityAuditPrompt(),
            "当需要审核内容合规性、检查敏感词、进行安全检查时激活（通常由系统自动触发）",
            new String[]{
                "检查这段内容是否合规",
                "审核用户发布的文章"
            },
            null,
            new String[]{"审核", "违规", "敏感", "安全", "内容审查", "合规"},
            true,
            8,
            "安全"
        );
        skills.put(securitySkill.getId(), securitySkill);

        // 7. 质量评估师
        Skill qualitySkill = new Skill(
            "quality_evaluator",
            "质量评估师",
            "评估AI回答质量，给出改进建议",
            getQualityEvaluatorPrompt(),
            "当用户或系统要求评估AI回答质量、要求改进回答时激活",
            new String[]{
                "评估一下你的回答",
                "能不能说得更好？",
                "回答可以改进吗？"
            },
            null,
            new String[]{"评估", "质量", "回答", "改进", "优化", "评分"},
            true,
            5,
            "评估"
        );
        skills.put(qualitySkill.getId(), qualitySkill);

        log.info("已加载 {} 个默认Skills", skills.size());
    }

    /**
     * 获取购物顾问Prompt
     */
    private String getShoppingAdvisorPrompt() {
        return "## ═══════════════════════════════════════════════════════\n" +
                "## 【购物顾问 Skill 已激活】\n" +
                "## ═══════════════════════════════════════════════════════\n" +
                "\n" +
                "【角色定位】\n" +
                "你是专业的文创产品购物顾问，了解各类文创产品的特点、设计理念和价值。\n" +
                "\n" +
                "【核心能力】\n" +
                "- 熟悉平台商品分类：首饰配饰、家居摆件、文具印章、茶具香具、服饰包袋等\n" +
                "- 了解材质工艺：陶瓷、木作、织物、漆器、金属等\n" +
                "- 能根据用户需求推荐合适产品\n" +
                "\n" +
                "【回答规范】\n" +
                "- 推荐的商品必须是平台真实存在的\n" +
                "- 如实介绍产品特点和价格\n" +
                "- 不夸大其词，不过度推销\n" +
                "- 引导用户到商城查看详情\n";
    }

    /**
     * 获取客服话术师Prompt
     */
    private String getCustomerServicePrompt() {
        return "## ═══════════════════════════════════════════════════════\n" +
                "## 【客服话术师 Skill 已激活】\n" +
                "## ═══════════════════════════════════════════════════════\n" +
                "\n" +
                "【角色定位】\n" +
                "你是耐心的客服人员，帮助用户解决问题，记录用户反馈。\n" +
                "\n" +
                "【核心能力】\n" +
                "- 处理退换货咨询\n" +
                "- 解答物流配送问题\n" +
                "- 接受用户投诉和建议\n" +
                "- 引导用户联系人工客服\n" +
                "\n" +
                "【回答规范】\n" +
                "- 态度友好，耐心倾听\n" +
                "- 了解清楚用户问题的具体情况\n" +
                "- 属于自己职责范围内的给出解决方案\n" +
                "- 超出职责范围的引导用户联系人工客服\n" +
                "- 不做无法兑现的承诺\n";
    }

    /**
     * 获取知识百科达人Prompt
     */
    private String getKnowledgeExpertPrompt() {
        return "## ═══════════════════════════════════════════════════════\n" +
                "## 【知识百科达人 Skill 已激活】\n" +
                "## ═══════════════════════════════════════════════════════\n" +
                "\n" +
                "【角色定位】\n" +
                "你是平台知识库的管理员，熟悉各类常见问题及答案。\n" +
                "\n" +
                "【核心能力】\n" +
                "- 基于知识库内容准确回答用户问题\n" +
                "- 提供清晰的操作指导\n" +
                "- 不知道的问题如实告知用户\n" +
                "\n" +
                "【回答规范】\n" +
                "- 优先使用知识库内容回答\n" +
                "- 适当润色但不改变核心含义\n" +
                "- 如知识库无相关信息，告知用户\"未在知识库中找到答案\"\n" +
                "- 操作类问题要给出具体步骤\n";
    }

    /**
     * 获取推荐解读者Prompt
     */
    private String getRecommendExpertPrompt() {
        return "## ═══════════════════════════════════════════════════════\n" +
                "## 【推荐解读者 Skill 已激活】\n" +
                "## ═══════════════════════════════════════════════════════\n" +
                "\n" +
                "【角色定位】\n" +
                "你是智能推荐系统的解读专家，帮助用户理解个性化推荐逻辑。\n" +
                "\n" +
                "【核心能力】\n" +
                "- 解读推荐结果的产生原因\n" +
                "- 分析用户的兴趣偏好\n" +
                "- 提供优化推荐的建议\n" +
                "\n" +
                "【回答规范】\n" +
                "- 用通俗语言解释推荐算法\n" +
                "- 真诚可信，不过度夸大推荐效果\n" +
                "- 尊重用户隐私，不过度解读\n";
    }

    /**
     * 获取安全审核Prompt
     */
    private String getSecurityAuditPrompt() {
        return "## ═══════════════════════════════════════════════════════\n" +
                "## 【安全审核员 Skill 已激活】\n" +
                "## ═══════════════════════════════════════════════════════\n" +
                "\n" +
                "【角色定位】\n" +
                "你是平台内容安全的守护者，负责审核内容安全合规。\n" +
                "\n" +
                "【核心能力】\n" +
                "- 识别各类违规内容\n" +
                "- 判断违规程度\n" +
                "- 给出处理建议\n" +
                "\n" +
                "【审核标准】\n" +
                "- 政治敏感：零容忍\n" +
                "- 违法违规：坚决禁止\n" +
                "- 色情低俗：严格审核\n" +
                "- 虚假信息：核实处理\n" +
                "\n" +
                "【回答规范】\n" +
                "- 审核标准统一\n" +
                "- 处理结果有据可查\n" +
                "- 保护用户隐私信息\n";
    }

    /**
     * 获取质量评估Prompt
     */
    private String getQualityEvaluatorPrompt() {
        return "## ═══════════════════════════════════════════════════════\n" +
                "## 【质量评估师 Skill 已激活】\n" +
                "## ═══════════════════════════════════════════════════════\n" +
                "\n" +
                "【角色定位】\n" +
                "你是AI回答质量的评估专家，负责评估和改进AI回答质量。\n" +
                "\n" +
                "【核心能力】\n" +
                "- 对AI回答进行多维度评估\n" +
                "- 识别回答中的问题\n" +
                "- 给出具体的改进建议\n" +
                "\n" +
                "【评估维度】\n" +
                "- 准确性：事实是否正确\n" +
                "- 完整性：是否完整回答问题\n" +
                "- 相关性：是否切题\n" +
                "- 清晰度：表达是否清晰\n" +
                "- 安全性：是否合规\n" +
                "\n" +
                "【回答规范】\n" +
                "- 客观公正，评估标准统一\n" +
                "- 建议具体可操作\n" +
                "- 反馈建设性正面\n";
    }

    /**
     * 获取所有Skills的描述列表（用于LLM选择）
     */
    public String getSkillsDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 可用的Skills（根据问题选择需要启用的Skills）\n\n");

        for (Skill skill : skills.values()) {
            sb.append(String.format("- [%s] %s: %s\n",
                skill.getId(), skill.getName(), skill.getDescription()));
        }

        return sb.toString();
    }

    /**
     * 根据skillId获取Skill
     */
    public Optional<Skill> getSkill(String skillId) {
        return Optional.ofNullable(skills.get(skillId));
    }

    /**
     * 获取所有启用的Skills
     */
    public List<Skill> getAllEnabledSkills() {
        List<Skill> result = new ArrayList<>();
        for (Skill skill : skills.values()) {
            if (skill.isEnabled()) {
                result.add(skill);
            }
        }
        return result;
    }

    /**
     * 根据skillIds获取多个Skills
     */
    public List<Skill> getSkills(List<String> skillIds) {
        List<Skill> result = new ArrayList<>();
        for (String skillId : skillIds) {
            Skill skill = skills.get(skillId);
            if (skill != null && skill.isEnabled()) {
                result.add(skill);
            }
        }
        return result;
    }

    /**
     * 根据关键词匹配Skills — 从用户问题中匹配需要的Skills
     */
    public List<Skill> getSkillsByKeywords(String userQuery) {
        if (userQuery == null || userQuery.isEmpty()) {
            return Collections.emptyList();
        }

        String query = userQuery.toLowerCase();
        List<Skill> matchedSkills = new ArrayList<>();

        for (Skill skill : skills.values()) {
            if (!skill.isEnabled() || skill.getKeywords() == null) {
                continue;
            }
            for (String keyword : skill.getKeywords()) {
                if (query.contains(keyword.toLowerCase())) {
                    matchedSkills.add(skill);
                    break;
                }
            }
        }

        return matchedSkills;
    }
}
