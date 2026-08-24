import { http } from "./client";

export interface Novel { id: number; projectId: number; title: string; genre?: string; synopsis?: string; worldSetting?: string }
export interface NovelChapter { id: number; novelId: number; chapterNo: number; title: string; content?: string; summary?: string; sourceType: string; revisionNo: number; updateTime: string }
export interface AiTraceResult { riskScore: number; riskLevel: string; features: Record<string, unknown>; disclaimer: string }

const root = (projectId: number) => `/api/projects/${projectId}/novel`;

export const novelApi = {
  get: (projectId: number) => http.get<never, Novel>(root(projectId)),
  create: (projectId: number, body: Pick<Novel, "title" | "genre" | "synopsis" | "worldSetting">) =>
    http.post<never, Novel>(root(projectId), body),
  chapters: (projectId: number) => http.get<never, NovelChapter[]>(`${root(projectId)}/chapters`),
  saveChapter: (projectId: number, body: Partial<NovelChapter> & { chapterNo: number; title: string }) =>
    http.put<never, NovelChapter>(`${root(projectId)}/chapters`, body),
  generate: (projectId: number, body: { chapterNo: number; title?: string; instruction?: string; modelId?: number; skillId?: string }) =>
    http.post<never, { chapter: NovelChapter }>(`${root(projectId)}/chapters/generate`, body),
  aiTrace: (projectId: number, chapterId: number) =>
    http.post<never, AiTraceResult>(`${root(projectId)}/chapters/${chapterId}/ai-trace`),
  search: (projectId: number, query: string) => http.get<never, unknown[]>(`${root(projectId)}/search`, { params: { query } }),
};
