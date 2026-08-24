package com.stonewu.fusion.controller.novel;

import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.entity.audit.AuditEvent;
import com.stonewu.fusion.entity.novel.Novel;
import com.stonewu.fusion.entity.novel.NovelChapter;
import com.stonewu.fusion.entity.novel.NovelChapterRevision;
import com.stonewu.fusion.entity.risk.RiskAssessment;
import com.stonewu.fusion.security.SecurityUtils;
import com.stonewu.fusion.service.audit.AuditEventService;
import com.stonewu.fusion.service.novel.NovelGenerationService;
import com.stonewu.fusion.service.novel.NovelService;
import com.stonewu.fusion.service.project.ProjectService;
import com.stonewu.fusion.service.risk.AiTraceAnalysisService;
import com.stonewu.fusion.service.risk.ContentRiskService;
import com.stonewu.fusion.service.search.ElasticsearchNovelSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 小说创作工作台 API。所有写操作同时形成版本、来源、风控与审计记录。 */
@Tag(name = "小说创作")
@RestController
@RequestMapping("/api/projects/{projectId}/novel")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasAuthority('novel:read')")
public class NovelController {

    private final NovelService novelService;
    private final NovelGenerationService generationService;
    private final ContentRiskService riskService;
    private final AiTraceAnalysisService aiTraceAnalysisService;
    private final AuditEventService auditEventService;
    private final ProjectService projectService;
    private final ElasticsearchNovelSearchService searchService;

    @GetMapping
    @Operation(summary = "获取项目小说")
    public CommonResult<Novel> get(@PathVariable Long projectId) {
        return CommonResult.success(novelService.getByProject(projectId, currentUserId()));
    }

    @PostMapping
    @Operation(summary = "创建项目小说")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('novel:write')")
    public CommonResult<Novel> create(@PathVariable Long projectId,
                                      @Valid @RequestBody NovelCreateRequest request) {
        return CommonResult.success(novelService.createNovel(projectId, currentUserId(), request.title(),
                request.genre(), request.synopsis(), request.worldSetting()));
    }

    @GetMapping("/chapters")
    @Operation(summary = "获取章节目录")
    public CommonResult<List<NovelChapter>> chapters(@PathVariable Long projectId) {
        Novel novel = requireNovel(projectId);
        return CommonResult.success(novelService.listChapters(novel.getId(), currentUserId()));
    }

    @GetMapping("/chapters/{chapterId}")
    @Operation(summary = "获取章节正文")
    public CommonResult<NovelChapter> chapter(@PathVariable Long projectId,
                                              @PathVariable Long chapterId) {
        requireNovel(projectId);
        return CommonResult.success(novelService.getChapter(chapterId, currentUserId()));
    }

    @PutMapping("/chapters")
    @Operation(summary = "新建或保存章节修订")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('novel:write')")
    public CommonResult<NovelChapter> saveChapter(@PathVariable Long projectId,
                                                  @Valid @RequestBody ChapterSaveRequest request) {
        Novel novel = requireNovel(projectId);
        return CommonResult.success(novelService.saveChapter(novel.getId(), request.id(), currentUserId(),
                request.chapterNo(), request.title(), request.content(), request.summary(),
                "HUMAN", null, null, null, null));
    }

    @PostMapping("/chapters/generate")
    @Operation(summary = "使用用户配置的文本模型生成章节草稿")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('novel:generate')")
    public CommonResult<NovelGenerationService.GenerationResult> generate(
            @PathVariable Long projectId,
            @Valid @RequestBody ChapterGenerateRequest request) {
        Novel novel = requireNovel(projectId);
        return CommonResult.success(generationService.generateChapter(currentUserId(), projectId, novel,
                request.chapterNo(), request.title(), request.instruction(), request.modelId(),
                request.skillId(), request.skillVersion()));
    }

    @GetMapping("/chapters/{chapterId}/revisions")
    @Operation(summary = "获取章节不可变修订历史")
    public CommonResult<List<NovelChapterRevision>> revisions(@PathVariable Long projectId,
                                                              @PathVariable Long chapterId) {
        requireNovel(projectId);
        return CommonResult.success(novelService.listRevisions(chapterId, currentUserId()));
    }

    @PostMapping("/chapters/{chapterId}/ai-trace")
    @Operation(summary = "分析章节 AI 创作痕迹风险")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('risk:read')")
    public CommonResult<AiTraceAnalysisService.AnalysisResult> analyzeAiTrace(
            @PathVariable Long projectId,
            @PathVariable Long chapterId) {
        NovelChapter chapter = novelService.getChapter(chapterId, currentUserId());
        return CommonResult.success(aiTraceAnalysisService.analyze(currentUserId(), projectId,
                "NOVEL", chapterId, chapter.getContent()));
    }

    @GetMapping("/risk-assessments")
    @Operation(summary = "获取项目内容安全审核记录")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('risk:read')")
    public CommonResult<List<RiskAssessment>> riskAssessments(@PathVariable Long projectId) {
        requireProjectAccess(projectId);
        return CommonResult.success(riskService.listByProject(projectId));
    }

    @GetMapping("/search")
    @Operation(summary = "使用 Elasticsearch 检索当前项目小说")
    public CommonResult<List<ElasticsearchNovelSearchService.SearchHit>> search(
            @PathVariable Long projectId,
            @org.springframework.web.bind.annotation.RequestParam String query,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int limit) {
        requireProjectAccess(projectId);
        if (query == null || query.isBlank()) {
            throw new BusinessException(400, "检索内容不能为空");
        }
        return CommonResult.success(searchService.search(projectId, query.trim(), limit));
    }

    @GetMapping("/chapters/{chapterId}/audit-events")
    @Operation(summary = "获取章节操作审计链")
    public CommonResult<List<AuditEvent>> auditEvents(@PathVariable Long projectId,
                                                      @PathVariable Long chapterId) {
        novelService.getChapter(chapterId, currentUserId());
        return CommonResult.success(auditEventService.listByResource("CHAPTER", String.valueOf(chapterId)));
    }

    private Novel requireNovel(Long projectId) {
        Novel novel = novelService.getByProject(projectId, currentUserId());
        if (novel == null) {
            throw new BusinessException(404, "当前项目尚未创建小说");
        }
        return novel;
    }

    private void requireProjectAccess(Long projectId) {
        if (!projectService.canAccessProject(projectId, currentUserId())) {
            throw new BusinessException(403, "无权访问该项目");
        }
    }

    private long currentUserId() {
        return SecurityUtils.requireCurrentUserId();
    }

    public record NovelCreateRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 64) String genre,
            @Size(max = 10000) String synopsis,
            @Size(max = 50000) String worldSetting) {
    }

    public record ChapterSaveRequest(
            Long id,
            @NotNull @Min(1) Integer chapterNo,
            @NotBlank @Size(max = 255) String title,
            @Size(max = 500000) String content,
            @Size(max = 10000) String summary) {
    }

    public record ChapterGenerateRequest(
            @NotNull @Min(1) Integer chapterNo,
            @Size(max = 255) String title,
            @Size(max = 10000) String instruction,
            Long modelId,
            @Size(max = 128) String skillId,
            @Size(max = 32) String skillVersion) {
    }
}
