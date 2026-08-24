"use client";

import { use, useCallback, useEffect, useState } from "react";
import { BookOpen, Bot, Save, ScanSearch } from "lucide-react";
import { toast } from "sonner";
import { novelApi, type AiTraceResult, type Novel, type NovelChapter } from "@/lib/api/novel";
import { toastApiError } from "@/lib/api/toast-api-error";

export default function NovelWorkspace({ params }: { params: Promise<{ id: string }> }) {
  const projectId = Number(use(params).id);
  const [novel, setNovel] = useState<Novel | null>(null);
  const [chapters, setChapters] = useState<NovelChapter[]>([]);
  const [selected, setSelected] = useState<NovelChapter | null>(null);
  const [busy, setBusy] = useState(false);
  const [instruction, setInstruction] = useState("");
  const [trace, setTrace] = useState<AiTraceResult | null>(null);

  const reload = useCallback(async () => {
    try {
      const current = await novelApi.get(projectId);
      setNovel(current);
      if (current) setChapters(await novelApi.chapters(projectId));
    } catch (error) { toastApiError(error, "加载小说失败"); }
  }, [projectId]);
  useEffect(() => { void reload(); }, [reload]);

  const createNovel = async () => {
    const title = window.prompt("小说名称");
    if (!title) return;
    try { setNovel(await novelApi.create(projectId, { title, genre: "", synopsis: "", worldSetting: "" })); }
    catch (error) { toastApiError(error, "创建小说失败"); }
  };
  const newChapter = () => setSelected({ id: 0, novelId: novel?.id ?? 0, chapterNo: chapters.length + 1, title: `第${chapters.length + 1}章`, content: "", sourceType: "HUMAN", revisionNo: 0, updateTime: "" });
  const save = async () => {
    if (!selected) return;
    setBusy(true);
    try {
      const saved = await novelApi.saveChapter(projectId, { ...selected, id: selected.id || undefined });
      setSelected(saved); await reload(); toast.success("章节已保存并生成版本、来源与审计记录");
    } catch (error) { toastApiError(error, "保存失败"); } finally { setBusy(false); }
  };
  const generate = async () => {
    const chapterNo = selected?.chapterNo ?? chapters.length + 1;
    setBusy(true);
    try {
      const result = await novelApi.generate(projectId, { chapterNo, title: selected?.title, instruction });
      setSelected(result.chapter); await reload(); toast.success("AI 草稿已生成并通过内容风控");
    } catch (error) { toastApiError(error, "生成失败，请先在 AI 配置中填写自己的模型 Key"); } finally { setBusy(false); }
  };
  const detect = async () => {
    if (!selected?.id) return;
    try { setTrace(await novelApi.aiTrace(projectId, selected.id)); } catch (error) { toastApiError(error, "检测失败"); }
  };

  if (!novel) return <main className="p-8"><div className="rounded-xl border border-dashed p-12 text-center"><BookOpen className="mx-auto mb-4"/><h1 className="text-xl font-semibold">从小说开始你的 AIGC 流程</h1><p className="my-4 text-sm text-muted-foreground">章节保存自动形成修订、来源哈希、风险审核和审计链。</p><button className="rounded bg-primary px-4 py-2 text-primary-foreground" onClick={createNovel}>创建小说</button></div></main>;

  return <main className="flex h-[calc(100vh-4rem)] gap-4 p-4">
    <aside className="w-64 shrink-0 rounded-xl border p-3"><h1 className="mb-1 font-semibold">{novel.title}</h1><p className="mb-4 text-xs text-muted-foreground">{chapters.length} 章 · 全链路可追溯</p><button className="mb-3 w-full rounded border px-3 py-2 text-sm" onClick={newChapter}>+ 新建章节</button><div className="space-y-1">{chapters.map(c => <button key={c.id} onClick={() => { setSelected(c); setTrace(null); }} className="w-full rounded px-3 py-2 text-left text-sm hover:bg-muted">{c.chapterNo}. {c.title}</button>)}</div></aside>
    <section className="flex min-w-0 flex-1 flex-col rounded-xl border p-4">{selected ? <><div className="mb-3 flex gap-2"><input className="w-20 rounded border bg-transparent px-2" type="number" value={selected.chapterNo} onChange={e => setSelected({...selected, chapterNo: Number(e.target.value)})}/><input className="flex-1 rounded border bg-transparent px-3" value={selected.title} onChange={e => setSelected({...selected, title: e.target.value})}/><button aria-label="保存章节" disabled={busy} className="rounded border px-3" onClick={save}><Save className="h-4 w-4"/></button><button aria-label="检测 AI 痕迹" className="rounded border px-3" onClick={detect}><ScanSearch className="h-4 w-4"/></button></div><textarea className="min-h-0 flex-1 resize-none rounded border bg-transparent p-4 leading-7" value={selected.content ?? ""} onChange={e => setSelected({...selected, content: e.target.value})}/><div className="mt-3 flex gap-2"><input className="flex-1 rounded border bg-transparent px-3 py-2 text-sm" placeholder="描述下一章情节、文风与约束（使用你配置的模型 Key）" value={instruction} onChange={e => setInstruction(e.target.value)}/><button disabled={busy} className="flex items-center gap-2 rounded bg-primary px-4 text-primary-foreground" onClick={generate}><Bot className="h-4 w-4"/>AI 生成</button></div>{trace && <div className="mt-3 rounded border p-3 text-sm"><b>AI 痕迹风险：{trace.riskLevel}（{trace.riskScore}）</b><p className="mt-1 text-muted-foreground">{trace.disclaimer}</p></div>}</> : <div className="m-auto text-muted-foreground">选择或新建章节</div>}</section>
  </main>;
}
