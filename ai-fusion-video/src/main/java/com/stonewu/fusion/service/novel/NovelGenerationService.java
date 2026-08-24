package com.stonewu.fusion.service.novel;

import cn.hutool.core.util.StrUtil;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.entity.novel.Novel;
import com.stonewu.fusion.entity.novel.NovelChapter;
import com.stonewu.fusion.service.ai.AiModelService;
import com.stonewu.fusion.service.ai.ChatModelFactory;
import com.stonewu.fusion.service.risk.ContentRiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.List;

/** 使用用户配置的文本模型生成小说章节草稿。 */
@Service
@RequiredArgsConstructor
public class NovelGenerationService {

    private static final int TEXT_MODEL_TYPE = 1;

    private final NovelService novelService;
    private final AiModelService aiModelService;
    private final ChatModelFactory chatModelFactory;
    private final ContentRiskService riskService;

    @CacheEvict(value = "novelChapter", allEntries = true)
    public GenerationResult generateChapter(Long userId, Long projectId, Novel novel,
                                            Integer chapterNo, String chapterTitle,
                                            String instruction, Long modelId,
                                            String skillId, String skillVersion) {
        if (novel == null || !projectId.equals(novel.getProjectId())) {
            throw new BusinessException(404, "当前项目尚未创建小说");
        }
        String userInstruction = StrUtil.blankToDefault(instruction, "延续既有剧情，推进主要冲突");
        riskService.requireGeneratable(userId, projectId, "CHAPTER_PROMPT", "INPUT", userInstruction);

        AiModel model = modelId == null
                ? aiModelService.getDefaultByType(TEXT_MODEL_TYPE)
                : aiModelService.getById(modelId);
        if (model == null || !Integer.valueOf(TEXT_MODEL_TYPE).equals(model.getModelType())) {
            throw new BusinessException(400, "请先配置一个可用的默认文本模型");
        }

        List<NovelChapter> chapters = novelService.listChapters(novel.getId(), userId);
        String recentContext = chapters.stream()
                .skip(Math.max(0, chapters.size() - 3L))
                .map(chapter -> "第" + chapter.getChapterNo() + "章 " + chapter.getTitle()
                        + "\n摘要：" + StrUtil.blankToDefault(chapter.getSummary(), "暂无")
                        + "\n正文末尾：" + tail(chapter.getContent(), 1200))
                .reduce("", (left, right) -> left + "\n\n" + right);
        String promptText = buildPrompt(novel, chapterNo, chapterTitle, userInstruction, recentContext);

        ChatResponse response = chatModelFactory.getOrCreate(model).call(new Prompt(promptText));
        String generated = extractText(response);
        if (StrUtil.isBlank(generated)) {
            throw new BusinessException("模型已响应，但没有返回章节正文");
        }
        riskService.requireGeneratable(userId, projectId, "CHAPTER", "OUTPUT", generated);
        NovelChapter chapter = novelService.saveChapter(novel.getId(), null, userId, chapterNo,
                StrUtil.blankToDefault(chapterTitle, "第" + chapterNo + "章"), generated,
                summarize(generated), "AI_GENERATED", model.getId(), skillId, skillVersion, promptText);
        return new GenerationResult(chapter, model.getId(), model.getName());
    }

    private String buildPrompt(Novel novel, Integer chapterNo, String chapterTitle,
                               String instruction, String recentContext) {
        return """
                你是一名专业中文网络小说作者。请直接输出章节正文，不要解释创作过程，也不要使用 Markdown 代码块。

                # 小说
                书名：%s
                题材：%s
                简介：%s
                世界观：%s

                # 本次任务
                章节：第%d章 %s
                写作要求：%s

                # 最近上下文
                %s

                保持人物、时间线和世界规则一致；以具体动作、对白和场景推动剧情，避免模板化总结。
                """.formatted(novel.getTitle(), safe(novel.getGenre()), safe(novel.getSynopsis()),
                safe(novel.getWorldSetting()), chapterNo,
                StrUtil.blankToDefault(chapterTitle, "未命名"), instruction,
                StrUtil.blankToDefault(recentContext, "这是第一章，暂无前文。"));
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null) return "";
        AssistantMessage message = response.getResult().getOutput();
        return message == null ? "" : StrUtil.trim(message.getText());
    }

    private String tail(String content, int limit) {
        if (content == null || content.length() <= limit) return safe(content);
        return content.substring(content.length() - limit);
    }

    private String summarize(String content) {
        String compact = content.replaceAll("\\s+", " ").trim();
        return compact.length() <= 240 ? compact : compact.substring(0, 240) + "…";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record GenerationResult(NovelChapter chapter, Long modelId, String modelName) {
    }
}
