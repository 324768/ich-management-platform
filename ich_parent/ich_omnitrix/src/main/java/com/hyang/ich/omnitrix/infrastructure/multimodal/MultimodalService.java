package com.hyang.ich.omnitrix.infrastructure.multimodal;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.input.Entities;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

/**
 * 多模态服务
 * 提供图片理解、图片问答等能力
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "omnitrix.multimodal", name = "enabled", havingValue = "true")
public class MultimodalService {

    private final ChatLanguageModel multimodalModel;

    public MultimodalService(ChatLanguageModel multimodalModel) {
        this.multimodalModel = multimodalModel;
    }

    /**
     * 图片理解（单图）
     *
     * @param imageFile 图片文件
     * @param question  关于图片的问题
     * @return AI对图片的分析回答
     */
    public String analyzeImage(MultipartFile imageFile, String question) {
        try {
            String base64Image = convertToBase64(imageFile);
            return analyzeImageBase64(base64Image, question);
        } catch (IOException e) {
            log.error("图片处理失败: {}", e.getMessage(), e);
            return "图片处理失败: " + e.getMessage();
        }
    }

    /**
     * 图片理解（Base64编码）
     *
     * @param base64Image Base64编码的图片
     * @param question    关于图片的问题
     * @return AI对图片的分析回答
     */
    public String analyzeImageBase64(String base64Image, String question) {
        if (question == null || question.trim().isEmpty()) {
            question = "请描述这张图片的内容";
        }

        String userMessage = String.format(
                "图片: data:image/jpeg;base64,%s\n\n请仔细观察上面的图片，然后回答以下问题：%s",
                base64Image, question
        );

        try {
            ChatResponse response = multimodalModel.chat(userMessage);
            return response.aiMessage().singleText();
        } catch (Exception e) {
            log.error("图片理解失败: {}", e.getMessage(), e);
            return "图片理解失败: " + e.getMessage();
        }
    }

    /**
     * 图片理解（多图）
     *
     * @param imageFiles 图片文件列表
     * @param question   关于多图的问题
     * @return AI对多图的分析回答
     */
    public String analyzeImages(List<MultipartFile> imageFiles, String question) {
        try {
            StringBuilder imagesPart = new StringBuilder();

            for (MultipartFile file : imageFiles) {
                String base64 = convertToBase64(file);
                imagesPart.append("图片: data:image/jpeg;base64,").append(base64).append("\n\n");
            }

            if (question == null || question.trim().isEmpty()) {
                question = "请描述这些图片的内容";
            }

            String userMessage = imagesPart.toString() +
                    "请仔细观察上面的所有图片，然后回答以下问题：" + question;

            ChatResponse response = multimodalModel.chat(userMessage);
            return response.aiMessage().singleText();

        } catch (Exception e) {
            log.error("多图理解失败: {}", e.getMessage(), e);
            return "多图理解失败: " + e.getMessage();
        }
    }

    /**
     * 图片内容提取（用于RAG知识库构建）
     *
     * @param imageFile 图片文件
     * @return 从图片中提取的文本描述
     */
    public String extractTextFromImage(MultipartFile imageFile) {
        try {
            String base64Image = convertToBase64(imageFile);

            String userMessage = String.format(
                    "图片: data:image/jpeg;base64,%s\n\n请提取图片中的所有文字内容，包括标题、正文、标签等。如果图片中没有文字，请描述图片的主要内容。",
                    base64Image
            );

            ChatResponse response = multimodalModel.chat(userMessage);
            return response.aiMessage().singleText();

        } catch (Exception e) {
            log.error("图片文字提取失败: {}", e.getMessage(), e);
            return "";
        }
    }

    /**
     * 图片分类/标签
     *
     * @param imageFile 图片文件
     * @param categories 可能的分类列表
     * @return 分类结果
     */
    public String categorizeImage(MultipartFile imageFile, List<String> categories) {
        try {
            String base64Image = convertToBase64(imageFile);
            String categoriesStr = String.join("、", categories);

            String userMessage = String.format(
                    "图片: data:image/jpeg;base64,%s\n\n请从以下分类中选择最合适的分类：%s。如果都不合适，请说明原因。",
                    base64Image, categoriesStr
            );

            ChatResponse response = multimodalModel.chat(userMessage);
            return response.aiMessage().singleText();

        } catch (Exception e) {
            log.error("图片分类失败: {}", e.getMessage(), e);
            return "图片分类失败: " + e.getMessage();
        }
    }

    /**
     * 比较两张图片的差异
     *
     * @param image1 第一张图片
     * @param image2 第二张图片
     * @return 差异分析结果
     */
    public String compareImages(MultipartFile image1, MultipartFile image2) {
        try {
            String base64Image1 = convertToBase64(image1);
            String base64Image2 = convertToBase64(image2);

            String userMessage = String.format(
                    "图片1: data:image/jpeg;base64,%s\n\n图片2: data:image/jpeg;base64,%s\n\n请比较这两张图片的差异，包括内容、布局、风格等方面的不同。",
                    base64Image1, base64Image2
            );

            ChatResponse response = multimodalModel.chat(userMessage);
            return response.aiMessage().singleText();

        } catch (Exception e) {
            log.error("图片比较失败: {}", e.getMessage(), e);
            return "图片比较失败: " + e.getMessage();
        }
    }

    /**
     * 将MultipartFile转换为Base64编码
     */
    private String convertToBase64(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        return Base64.getEncoder().encodeToString(bytes);
    }
}
