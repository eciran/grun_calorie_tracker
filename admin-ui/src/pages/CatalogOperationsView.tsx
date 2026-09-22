import { FormEvent, lazy, Suspense, useMemo, useState } from "react";

import { formatRequestError, PageResponse, request } from "../api";

import { AdminCatalogImportJob, AdminCatalogSummary, ExerciseCatalogItem, ExerciseCatalogPage } from "../types";

import { CollapsiblePanel, DataTable, EmptyState, LoadState, MetricCard, PaginationControls, Panel, SectionToolbar } from "../AdminPrimitives";

import { Badge, combineStates, formatDate, formatValue, humanizeFeature, useEndpoint } from "./../admin/shared";
import { useAdminLocale } from "../admin/locale";

const ExerciseCategoryChart=lazy(()=>import("../ExerciseCatalogChart").then(module=>({default:module.ExerciseCategoryChart})));

export type CatalogOperationsMode = "exercises" | "sources";

export type UnmatchedAiExercise = {
  id: number; displayName: string; normalizedName: string; language?: string; equipment?: string;
  targetMuscleGroup?: string; occurrenceCount: number; firstSeenAt: string; lastSeenAt: string;
  status: string; resolvedExerciseItemId?: number;
};

export type ExerciseResolutionSummary = {
  openNames: number; openOccurrences: number; resolvedNames: number; dismissedNames: number;
  reviewedNames: number; resolutionRatePercent: number;
};
type ExerciseOverview={muscleGroups:{category:string;total:number}[];bodyScopes:{category:string;total:number}[];total:number;pendingReview:number;missingMedia:number;missingMeasurement:number;filtered:boolean};
type ExerciseFacets={primaryMuscleGroups:string[];secondaryMuscleGroups:string[];equipment:string[]};

export const EMPTY_EXERCISE: ExerciseCatalogItem = {
  name: "", metCode: "", caloriesPerMinute: 1, description: "", primaryMuscleGroup: "",
  secondaryMuscleGroups: "", equipment: "", difficulty: "BEGINNER", instructions: "", safetyNotes: "",
  thumbnailUrl: "", videoUrl: "", animationUrl: "", defaultMeasurementType: "DURATION",
  allowedMeasurementTypes: ["DURATION"], aiEligible: false, active: true, sourceName: "", sourceUrl: "",
  licenseName: "", licenseUrl: ""
};

const EXERCISE_MEASUREMENTS=[
  {value:"DURATION",labelTr:"Süre",labelEn:"Duration",fields:["durationMinutes"]},
  {value:"REPS",labelTr:"Tekrar",labelEn:"Repetitions",fields:["reps"]},
  {value:"SETS_REPS",labelTr:"Set + tekrar",labelEn:"Sets + reps",fields:["setCount","reps"]},
  {value:"WEIGHT_REPS",labelTr:"Ağırlık + tekrar",labelEn:"Weight + reps",fields:["weightKg","reps","setCount"]},
  {value:"DISTANCE",labelTr:"Mesafe",labelEn:"Distance",fields:["distanceKm","durationMinutes"]},
  {value:"MIXED",labelTr:"Süre + mesafe",labelEn:"Duration + distance",fields:["durationMinutes","distanceKm"]}
] as const;
type ExerciseEditorTab="identity"|"technique"|"measurement"|"evidence"|"governance";
function measurementFieldLabel(field:string,tr:boolean){
  const labels:Record<string,[string,string]>={durationMinutes:["Süre (dk)","Duration (min)"],setCount:["Set","Sets"],reps:["Tekrar","Reps"],weightKg:["Ağırlık (kg)","Weight (kg)"],distanceKm:["Mesafe (km)","Distance (km)"]};
  return labels[field]?.[tr?0:1]??humanizeFeature(field);
}
function csvValues(value?:string){return (value??"").split(",").map(item=>item.trim()).filter(Boolean);}

export function CatalogOperationsView({ mode, onError }: { mode: CatalogOperationsMode; onError: (message: string | null) => void }) {
  const {locale}=useAdminLocale(); const tr=locale==="tr";
  const [query, setQuery] = useState("");
  const [appliedQuery, setAppliedQuery] = useState("");
  const [reviewStatus, setReviewStatus] = useState("");
  const [activeFilter, setActiveFilter] = useState("");
  const [categoryFilter,setCategoryFilter]=useState("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(25);
  const [selected, setSelected] = useState<ExerciseCatalogItem | null>(null);
  const [draft, setDraft] = useState<ExerciseCatalogItem>(EMPTY_EXERCISE);
  const [reviewNote, setReviewNote] = useState("");
  const [assignment, setAssignment] = useState({ assignee: "", dueAt: "", reason: "" });
  const [actionState, setActionState] = useState<LoadState>("ready");
  const [formError,setFormError]=useState("");
  const [exerciseEditorTab,setExerciseEditorTab]=useState<ExerciseEditorTab>("identity");
  const [measurementTouched,setMeasurementTouched]=useState(false);
  const [primaryCustom,setPrimaryCustom]=useState(false);
  const [equipmentCustom,setEquipmentCustom]=useState(false);
  const [resolutionIds, setResolutionIds] = useState<Record<number, string>>({});
  const [filtersOpen,setFiltersOpen]=useState(false);
  const exercisePath = useMemo(() => {
    const params = new URLSearchParams({ page: String(page), size: String(pageSize) });
    if (appliedQuery) params.set("q", appliedQuery);
    if (reviewStatus) params.set("reviewStatus", reviewStatus);
    if (activeFilter) params.set("active", activeFilter);
    if(categoryFilter) params.set("category",categoryFilter);
    return `/api/v1/admin/catalog/exercises?${params}`;
  }, [page, pageSize, appliedQuery, reviewStatus, activeFilter,categoryFilter]);
  const overviewPath=useMemo(()=>{const params=new URLSearchParams();if(appliedQuery)params.set("q",appliedQuery);if(reviewStatus)params.set("reviewStatus",reviewStatus);if(activeFilter)params.set("active",activeFilter);if(categoryFilter)params.set("category",categoryFilter);return `/api/v1/admin/catalog/exercises/overview${params.size?`?${params}`:""}`;},[appliedQuery,reviewStatus,activeFilter,categoryFilter]);
  const { data: summary, state: summaryState, reload: reloadSummary } = useEndpoint<AdminCatalogSummary>("/api/v1/admin/catalog/summary", onError);
  const { data: importJobs, state: importState, reload: reloadImports } = useEndpoint<AdminCatalogImportJob[]>("/api/v1/admin/catalog/import-jobs", onError);
  const { data: exercises, state: exerciseState, reload: reloadExercises } = useEndpoint<ExerciseCatalogPage>(exercisePath, onError);
  const {data:exerciseOverview,state:overviewState,reload:reloadOverview}=useEndpoint<ExerciseOverview>(overviewPath,onError);
  const {data:exerciseFacets,state:facetState,reload:reloadFacets}=useEndpoint<ExerciseFacets>("/api/v1/admin/catalog/exercises/facets",onError);
  const { data: unmatchedExercises, state: unmatchedState, reload: reloadUnmatched } = useEndpoint<PageResponse<UnmatchedAiExercise>>("/api/v1/admin/exercise-resolution/unmatched?status=OPEN&page=0&size=50", onError);
  const { data: resolutionSummary, state: resolutionSummaryState, reload: reloadResolutionSummary } = useEndpoint<ExerciseResolutionSummary>("/api/v1/admin/exercise-resolution/summary", onError);
  const exerciseRows = exercises?.content ?? [];
  const categoryItems=(exerciseOverview?.muscleGroups??[]).map(item=>({label:item.category==="UNSPECIFIED"?(tr?"Belirtilmemiş":"Unspecified"):item.category,value:item.total}));
  const muscleTotal=categoryItems.reduce((sum,item)=>sum+item.value,0);
  const scopeTotal=(exerciseOverview?.bodyScopes??[]).reduce((sum,item)=>sum+item.total,0);

  function openExercise(item?: ExerciseCatalogItem) {
    const next = item ? { ...item } : { ...EMPTY_EXERCISE };
    setSelected(item ?? {});
    setDraft(next);
    setReviewNote(item?.techniqueReviewNote ?? "");
    setAssignment({ assignee: item?.reviewAssignee ?? "", dueAt: item?.reviewDueAt?.slice(0, 16) ?? "", reason: "" });
    setFormError("");
    setExerciseEditorTab("identity");
    setMeasurementTouched(Boolean(item?.id&&item.defaultMeasurementType));
    setPrimaryCustom(Boolean(item?.primaryMuscleGroup&&!(exerciseFacets?.primaryMuscleGroups??[]).includes(item.primaryMuscleGroup)));
    setEquipmentCustom(Boolean(item?.equipment&&!(exerciseFacets?.equipment??[]).includes(item.equipment)));
  }

  async function saveExercise(event: FormEvent) {
    event.preventDefault();
    const measurements=Array.from(new Set([...(draft.allowedMeasurementTypes??[]),draft.defaultMeasurementType??"DURATION"]));
    if(!draft.name?.trim()||!draft.metCode?.trim()||!Number.isFinite(Number(draft.caloriesPerMinute))||Number(draft.caloriesPerMinute)<=0){
      setFormError(tr?"Ad, MET kodu ve sıfırdan büyük kalori değeri zorunludur.":"Name, MET code and a calorie value greater than zero are required.");
      return;
    }
    setFormError("");
    setActionState("loading");
    try {
      const payload = {
        ...draft,name:draft.name.trim(),metCode:draft.metCode.trim(),
        caloriesPerMinute: Number(draft.caloriesPerMinute),
        allowedMeasurementTypes: measurements,
        sourceLastRefreshedAt: draft.sourceName ? new Date().toISOString().slice(0, 19) : null
      };
      await request<ExerciseCatalogItem>(draft.id ? `/api/v1/admin/catalog/exercises/${draft.id}` : "/api/v1/admin/catalog/exercises", {
        method: draft.id ? "PUT" : "POST",
        body: payload
      });
      setSelected(null);
      await Promise.all([reloadExercises(), reloadSummary(),reloadOverview()]);
      setActionState("ready");
    } catch (error) {
      setActionState("error");
      const message=formatRequestError(error); setFormError(message); onError(message);
    }
  }

  async function reviewExercise(status: string) {
    if (!draft.id || !reviewNote.trim()) return;
    setActionState("loading");
    try {
      await request(`/api/v1/admin/catalog/exercises/${draft.id}/review`, { method: "PATCH", body: { status, note: reviewNote.trim() } });
      setSelected(null);
      await Promise.all([reloadExercises(), reloadSummary()]);
      setActionState("ready");
    } catch (error) {
      setActionState("error");
      onError(formatRequestError(error));
    }
  }

  async function assignExerciseReview() {
    if (!draft.id || !assignment.dueAt || !assignment.reason.trim()) return;
    setActionState("loading");
    try {
      await request(`/api/v1/admin/catalog/review-items/EXERCISE/${draft.id}/assignment`, {
        method: "PATCH",
        body: { assignee: assignment.assignee.trim() || null, dueAt: `${assignment.dueAt}:00`, reason: assignment.reason.trim() }
      });
      setSelected(null);
      await Promise.all([reloadExercises(), reloadSummary()]);
      setActionState("ready");
    } catch (error) {
      setActionState("error");
      onError(formatRequestError(error));
    }
  }

  async function resolveAiExercise(item: UnmatchedAiExercise) {
    const exerciseItemId = Number(resolutionIds[item.id]);
    if (!Number.isInteger(exerciseItemId) || exerciseItemId <= 0) return;
    setActionState("loading");
    try {
      await request(`/api/v1/admin/exercise-resolution/unmatched/${item.id}/resolve`, {
        method: "POST", body: { exerciseItemId, createAlias: true }
      });
      setResolutionIds((current) => ({ ...current, [item.id]: "" }));
      await Promise.all([reloadUnmatched(), reloadResolutionSummary()]);
      setActionState("ready");
    } catch (error) {
      setActionState("error"); onError(formatRequestError(error));
    }
  }

  async function dismissAiExercise(item: UnmatchedAiExercise) {
    setActionState("loading");
    try {
      await request(`/api/v1/admin/exercise-resolution/unmatched/${item.id}/dismiss`, { method: "POST" });
      await Promise.all([reloadUnmatched(), reloadResolutionSummary()]); setActionState("ready");
    } catch (error) { setActionState("error"); onError(formatRequestError(error)); }
  }

  function toggleSecondaryMuscle(value:string){
    const current=csvValues(draft.secondaryMuscleGroups);
    const next=current.some(item=>item.toLowerCase()===value.toLowerCase())
      ? current.filter(item=>item.toLowerCase()!==value.toLowerCase())
      : [...current,value];
    setDraft({...draft,secondaryMuscleGroups:next.join(", ")});
  }

  const secondaryValues=csvValues(draft.secondaryMuscleGroups);
  const customSecondaryValues=secondaryValues.filter(value=>!(exerciseFacets?.secondaryMuscleGroups??[]).some(standard=>standard.toLowerCase()===value.toLowerCase()));

  const combinedState = combineStates([summaryState, importState, exerciseState, overviewState, facetState, unmatchedState, resolutionSummaryState, actionState]);
  return <div className="stack catalog-operations-view">
    <SectionToolbar
      title={mode === "exercises" ? "Exercise library operations" : "Catalog sources and jobs"}
      description={mode === "exercises" ? "Moderate technique, safety, media, source evidence, ownership, and active state." : "Cross-catalog coverage, source freshness, licensing evidence, and recent pipeline runs."}
      state={combinedState}
      onReload={() => { void reloadSummary(); void reloadImports(); void reloadExercises(); void reloadOverview(); void reloadFacets(); void reloadUnmatched(); void reloadResolutionSummary(); }}
    />
    {mode === "sources" && <>
      <div className="catalog-domain-grid">
        {(["food", "recipes", "exercises"] as const).map((key) => {
          const item = summary?.[key];
          return <article key={key} className="catalog-domain-card">
            <header><strong>{humanizeFeature(key)}</strong><Badge value={`${formatValue(item?.approved)} approved`} tone="good" /></header>
            <div><span>Total</span><strong>{formatValue(item?.total)}</strong></div>
            <div><span>Pending review</span><strong>{formatValue(item?.pendingReview)}</strong></div>
            <div><span>Missing media</span><strong>{formatValue(item?.missingMedia)}</strong></div>
            <div><span>Stale source</span><strong>{formatValue(item?.staleSource)}</strong></div>
            <div><span>Overdue SLA</span><strong>{formatValue(item?.overdueReview)}</strong></div>
          </article>;
        })}
      </div>
      <Panel title="Food source coverage" description="Server-side aggregate; no product payload is loaded into the browser.">
        <DataTable columns={["Source", "Items", "Stale", "License gaps"]} rows={(summary?.sources ?? []).map((item) => [<strong>{humanizeFeature(item.source)}</strong>, formatValue(item.itemCount), formatValue(item.staleCount), formatValue(item.missingLicenseCount)])} empty="No catalog source metrics returned." />
      </Panel>
      <Panel title="Recent catalog pipeline jobs" description="Recipe import batches and food quality runs share one operational ledger.">
        <DataTable columns={["Job", "Catalog", "Source", "Trigger", "Region", "Processed", "Issues", "Evidence", "Status", "Window"]} rows={(importJobs ?? []).map((item) => [
          <div className="entity-cell"><strong>{item.jobKey ?? "-"}</strong><small>{item.failureDetail ?? "No failure detail"}</small></div>,
          <Badge value={item.catalogType} />,
          item.source ?? "-", item.triggerType ?? "-", item.region ?? "All", formatValue(item.processedItems), formatValue(item.issueItems),
          item.licenseEvidence ?? "Missing", <Badge value={item.status} tone={item.status === "FAILED" ? "danger" : item.status === "PENDING" ? "warn" : "good"} />,
          <div className="entity-cell"><strong>{formatDate(item.startedAt)}</strong><small>{formatDate(item.completedAt)}</small></div>
        ])} empty="No catalog import or validation jobs returned." />
      </Panel>
    </>}
    {mode === "exercises" && <>
      <div className="exercise-operational-metrics"><MetricCard label={tr?"Toplam":"Total"} value={formatValue(exerciseOverview?.total)} hint={exerciseOverview?.filtered?(tr?"Filtrelenen hareketler":"Filtered exercises"):(tr?"Tüm katalog":"Full catalog")}/><MetricCard label={tr?"Onay bekleyen":"Awaiting approval"} value={formatValue(exerciseOverview?.pendingReview)} hint={tr?"Teknik kararı tamamlanmamış":"Technique decision incomplete"}/><MetricCard label={tr?"Medyası eksik":"Missing media"} value={formatValue(exerciseOverview?.missingMedia)} hint={tr?"Görsel, video veya animasyon yok":"No image, video or animation"}/><MetricCard label={tr?"Ölçüm türü eksik":"Missing measurement"} value={formatValue(exerciseOverview?.missingMeasurement)} hint={tr?"Kayıt biçimi tamamlanmalı":"Log format needs completion"}/></div>
      <Panel className="exercise-distribution-panel" title={exerciseOverview?.filtered?(tr?"Filtrelenen hareketler":"Filtered exercises"):(tr?"Kas grubu dağılımı":"Muscle group distribution")} description={tr?"Bir kas grubunu seçerek kataloğu doğrudan daraltın.":"Select a muscle group to narrow the catalog directly."}>
        <div className="exercise-distribution-toolbar"><span>{tr?`${formatValue(muscleTotal)} kas grubu kaydı`:`${formatValue(muscleTotal)} muscle-classified records`}</span>{categoryFilter&&<button className="ghost-button" type="button" onClick={()=>{setCategoryFilter("");setPage(0);}}>{tr?"Seçimi temizle":"Clear selection"} · {categoryFilter==="UNSPECIFIED"?(tr?"Belirtilmemiş":"Unspecified"):categoryFilter}</button>}</div>
        <div className="exercise-distribution-layout">
          <div className="exercise-chart-column">{categoryItems.length?<Suspense fallback={<div className="admin-chart-loading">Loading…</div>}><ExerciseCategoryChart items={categoryItems} tr={tr} selected={categoryFilter==="UNSPECIFIED"?(tr?"Belirtilmemiş":"Unspecified"):categoryFilter} onSelect={value=>{const raw=value===(tr?"Belirtilmemiş":"Unspecified")?"UNSPECIFIED":value;setCategoryFilter(current=>current===raw?"":raw);setPage(0);}}/></Suspense>:<EmptyState title={tr?"Kas grubu verisi yok":"No muscle-group data"} message={tr?"Bu filtre kapsamında egzersiz bulunamadı.":"No exercise was returned for this scope."}/>}</div>
          <aside className="exercise-scope-card"><div><span>{tr?"VÜCUT KAPSAMI":"BODY SCOPE"}</span><strong>{formatValue(scopeTotal)}</strong><small>{tr?"Kas gruplarından ayrı sınıflandırılır":"Classified separately from muscle groups"}</small></div><div className="exercise-scope-filters">{(exerciseOverview?.bodyScopes??[]).map(item=><button className={categoryFilter===item.category?"active":""} key={item.category} type="button" onClick={()=>{setCategoryFilter(current=>current===item.category?"":item.category);setPage(0);}}><span>{item.category}</span><strong>{item.total}</strong></button>)}</div></aside>
        </div>
      </Panel>
      <div className="exercise-queue-heading"><div><h3>{tr?"Egzersiz moderasyon kuyruğu":"Exercise moderation queue"}</h3><p>{tr?"Teknik, kaynak ve yayın durumunu tek listeden yönetin.":"Manage technique, evidence and publishing state in one list."}</p></div><button className="primary-button" type="button" onClick={()=>openExercise()}>{tr?"Yeni egzersiz":"New exercise"}</button></div>
      <CollapsiblePanel className="exercise-filter-panel" title={tr?"Egzersiz filtreleri":"Exercise filters"} description={tr?"Ad, MET kodu, kas grubu ve inceleme durumuna göre listeyi daraltın.":"Narrow the list by name, MET code, muscle group and review state."} open={filtersOpen} onToggle={()=>setFiltersOpen(value=>!value)}>
        <form className="catalog-exercise-filter" onSubmit={(event) => { event.preventDefault(); setPage(0); setAppliedQuery(query.trim()); }}>
          <label>Search<input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Name, MET code, muscle" /></label>
          <label>Review status<select value={reviewStatus} onChange={(event) => { setReviewStatus(event.target.value); setPage(0); }}><option value="">All statuses</option>{["PENDING", "IN_REVIEW", "APPROVED", "REJECTED"].map((item) => <option key={item}>{item}</option>)}</select></label>
          <label>Active state<select value={activeFilter} onChange={(event) => { setActiveFilter(event.target.value); setPage(0); }}><option value="">All items</option><option value="true">Active</option><option value="false">Inactive</option></select></label>
          <div className="exercise-filter-actions"><button className="ghost-button" type="button" onClick={()=>{setQuery("");setAppliedQuery("");setReviewStatus("");setActiveFilter("");setCategoryFilter("");setPage(0);}}>{tr?"Temizle":"Clear"}</button><button className="primary-button" type="submit">{tr?"Uygula":"Apply"}</button></div>
        </form>
      </CollapsiblePanel>
      <Panel title={tr?"Egzersiz listesi":"Exercise list"} description={`${formatValue(exercises?.totalElements)} ${tr?"kayıt":"records"}`}>
        <DataTable
          columns={["Exercise", "Technique", "Muscles", "Media", "Source", "Owner / SLA", "State"]}
          rows={exerciseRows.map((item) => [
            <div className="entity-cell"><strong>{item.name ?? "-"}</strong><small>{item.metCode ?? "-"} | {formatValue(item.caloriesPerMinute)} kcal/min</small></div>,
            <Badge value={item.techniqueReviewStatus ?? "PENDING"} tone={item.techniqueReviewStatus === "APPROVED" ? "good" : item.techniqueReviewStatus === "REJECTED" ? "danger" : "warn"} />,
            <div className="entity-cell"><strong>{item.primaryMuscleGroup ?? "-"}</strong><small>{item.equipment ?? "No equipment"}</small></div>,
            <Badge value={item.videoUrl || item.animationUrl || item.thumbnailUrl ? "Available" : "Missing"} tone={item.videoUrl || item.animationUrl || item.thumbnailUrl ? "good" : "warn"} />,
            <div className="entity-cell"><strong>{item.sourceName ?? "Missing"}</strong><small>{item.licenseName ?? "No license evidence"}</small></div>,
            <div className="entity-cell"><strong>{item.reviewAssignee ?? "Unassigned"}</strong><small>{formatDate(item.reviewDueAt)}</small></div>,
            <div className="badge-stack"><Badge value={item.active === false ? "Inactive" : "Active"} tone={item.active === false ? "neutral" : "good"} /><Badge value={item.aiEligible ? "AI eligible" : "AI blocked"} /></div>
          ])}
          rowData={exerciseRows}
          onRowClick={openExercise}
          empty="No exercise catalog items match the filters."
        />
        <PaginationControls page={exercises?.page ?? page} pageSize={exercises?.size ?? pageSize} totalElements={exercises?.totalElements ?? 0} totalPages={Math.max(1, exercises?.totalPages ?? 1)} first={exercises?.first ?? page === 0} last={exercises?.last ?? true} onPageChange={setPage} onPageSizeChange={(size) => { setPageSize(size); setPage(0); }} />
      </Panel>
      <Panel title="Unmatched AI exercises" description="Names produced by workout generation that could not be linked safely to an approved canonical exercise. Resolving also creates a reusable alias.">
        <div className="runtime-metric-grid">
          <MetricCard label="Open names" value={formatValue(resolutionSummary?.openNames)} hint={`${formatValue(resolutionSummary?.openOccurrences)} generated occurrences`} />
          <MetricCard label="Resolved" value={formatValue(resolutionSummary?.resolvedNames)} hint="Linked to canonical exercises" />
          <MetricCard label="Dismissed" value={formatValue(resolutionSummary?.dismissedNames)} hint="Reviewed as non-catalog output" />
          <MetricCard label="Resolution rate" value={`${formatValue(resolutionSummary?.resolutionRatePercent)}%`} hint={`${formatValue(resolutionSummary?.reviewedNames)} names reviewed`} />
        </div>
        <DataTable
          columns={["AI exercise", "Context", "Occurrences", "Last seen", "Resolve to exercise ID"]}
          rows={(unmatchedExercises?.content ?? []).map((item) => [
            <div className="entity-cell"><strong>{item.displayName}</strong><small>{item.normalizedName} · {item.language ?? "und"}</small></div>,
            <div className="entity-cell"><strong>{item.targetMuscleGroup ?? "Unknown muscle"}</strong><small>{item.equipment ?? "Unknown equipment"}</small></div>,
            formatValue(item.occurrenceCount), formatDate(item.lastSeenAt),
            <div className="inline-actions"><input aria-label={`Exercise ID for ${item.displayName}`} type="number" min="1" value={resolutionIds[item.id] ?? ""} onChange={(event) => setResolutionIds((current) => ({ ...current, [item.id]: event.target.value }))} /><button className="ghost-button" type="button" disabled={!resolutionIds[item.id] || actionState === "loading"} onClick={() => void resolveAiExercise(item)}>Link + alias</button><button className="ghost-button danger-text" type="button" disabled={actionState === "loading"} onClick={() => void dismissAiExercise(item)}>Dismiss</button></div>
          ])}
          empty="No unmatched AI exercises. Generated plans are resolving cleanly."
        />
      </Panel>
      {selected && <div className="modal-backdrop" role="presentation" onClick={() => setSelected(null)}>
        <form className="modal-card catalog-exercise-modal" role="dialog" aria-modal="true" aria-labelledby="exercise-modal-title" onSubmit={saveExercise} onClick={(event) => event.stopPropagation()}>
          <header className="modal-header exercise-modal-header"><div><span>{tr?"EGZERSİZ KATALOĞU":"EXERCISE CATALOG"}</span><h2 id="exercise-modal-title">{draft.id ? draft.name : (tr?"Yeni egzersiz":"New exercise")}</h2><p>{tr?"Kimlik, teknik, ölçüm ve kanıt bilgilerini tamamlayın. Yeni kayıt teknik onay bekler.":"Complete identity, technique, measurement and evidence data. New records remain pending review."}</p></div><button className="modal-icon-close" type="button" onClick={() => setSelected(null)} aria-label={tr?"Egzersiz formunu kapat":"Close exercise form"}>×</button></header>
          <div className="modal-body catalog-exercise-body">
            {formError&&<div className="exercise-form-error" role="alert">{formError}</div>}
            <div className="exercise-editor-layout">
              <nav className="exercise-editor-nav" aria-label={tr?"Egzersiz düzenleme bölümleri":"Exercise editor sections"}>{([
                ["identity",tr?"Kimlik ve sınıflandırma":"Identity & classification",Boolean(draft.name&&draft.metCode)],
                ["technique",tr?"Teknik ve güvenlik":"Technique & safety",Boolean(draft.instructions)],
                ["measurement",tr?"Kayıt modeli":"Logging model",measurementTouched],
                ["evidence",tr?"Medya ve kanıt":"Media & evidence",Boolean(draft.sourceName||draft.thumbnailUrl||draft.videoUrl||draft.animationUrl)],
                ...(draft.id?[["governance",tr?"İnceleme ve yayın":"Review & publishing",Boolean(draft.techniqueReviewStatus==="APPROVED")]]:[])
              ] as Array<[ExerciseEditorTab,string,boolean]>).map(([key,label,complete],index)=><button key={key} className={`${exerciseEditorTab===key?"active":""} ${complete?"complete":""}`} type="button" onClick={()=>setExerciseEditorTab(key)}><span>{index+1}</span><div><strong>{label}</strong><small>{complete?(tr?"Hazır":"Ready"):(tr?"Tamamlanmalı":"Needs input")}</small></div></button>)}</nav>
              <div className="exercise-editor-stage">
              {exerciseEditorTab==="identity"&&<section className="exercise-editor-section"><header><div><span>01</span><div><h3>{tr?"Kimlik ve sınıflandırma":"Identity and classification"}</h3><p>{tr?"Katalogda bulunabilirlik, enerji tahmini ve kas sınıflandırması.":"Catalog discovery, energy estimate and muscle classification."}</p></div></div><Badge value={draft.active===false?(tr?"Pasif":"Inactive"):(tr?"Aktif":"Active")} tone={draft.active===false?"neutral":"good"}/></header><div className="catalog-exercise-form-grid">
                <label>{tr?"Egzersiz adı":"Exercise name"}<input required value={draft.name ?? ""} onChange={(event) => setDraft({ ...draft, name: event.target.value })} /></label>
                <label>MET code<input required value={draft.metCode ?? ""} onChange={(event) => setDraft({ ...draft, metCode: event.target.value.toUpperCase() })} /></label>
                <label>{tr?"Dakikadaki kalori":"Calories / minute"}<input required min="0.01" step="0.01" type="number" value={draft.caloriesPerMinute ?? 1} onChange={(event) => setDraft({ ...draft, caloriesPerMinute: Number(event.target.value) })} /></label>
                <label>{tr?"Zorluk":"Difficulty"}<select value={draft.difficulty ?? "BEGINNER"} onChange={(event) => setDraft({ ...draft, difficulty: event.target.value })}>{["BEGINNER", "INTERMEDIATE", "ADVANCED"].map((item) => <option key={item} value={item}>{humanizeFeature(item)}</option>)}</select></label>
                <label>{tr?"Ana kas grubu":"Primary muscle"}<select value={primaryCustom?"__custom__":draft.primaryMuscleGroup??""} onChange={event=>{if(event.target.value==="__custom__"){setPrimaryCustom(true);setDraft({...draft,primaryMuscleGroup:""});}else{setPrimaryCustom(false);setDraft({...draft,primaryMuscleGroup:event.target.value});}}}><option value="">{tr?"Seçin":"Select"}</option>{(exerciseFacets?.primaryMuscleGroups??[]).map(value=><option key={value} value={value}>{value}</option>)}<option value="__custom__">{tr?"Yeni değer ekle…":"Add a new value…"}</option></select>{primaryCustom&&<input autoFocus value={draft.primaryMuscleGroup??""} onChange={event=>setDraft({...draft,primaryMuscleGroup:event.target.value})} placeholder={tr?"Yeni ana kas grubu":"New primary muscle group"}/>}</label>
                <label>{tr?"Ekipman":"Equipment"}<select value={equipmentCustom?"__custom__":draft.equipment??""} onChange={event=>{if(event.target.value==="__custom__"){setEquipmentCustom(true);setDraft({...draft,equipment:""});}else{setEquipmentCustom(false);setDraft({...draft,equipment:event.target.value});}}}><option value="">{tr?"Ekipman yok / seçin":"No equipment / select"}</option>{(exerciseFacets?.equipment??[]).map(value=><option key={value} value={value}>{value}</option>)}<option value="__custom__">{tr?"Yeni değer ekle…":"Add a new value…"}</option></select>{equipmentCustom&&<input autoFocus value={draft.equipment??""} onChange={event=>setDraft({...draft,equipment:event.target.value})} placeholder={tr?"Yeni ekipman standardı":"New equipment standard"}/>}</label>
                <div className="wide-field exercise-taxonomy-field"><div><strong>{tr?"İkincil kas grupları":"Secondary muscles"}</strong><small>{tr?"Birden fazla standart değer seçebilirsiniz.":"Select multiple standardized values."}</small></div><div className="exercise-taxonomy-options">{(exerciseFacets?.secondaryMuscleGroups??[]).map(value=>{const checked=secondaryValues.some(item=>item.toLowerCase()===value.toLowerCase());return <button key={value} className={checked?"active":""} type="button" onClick={()=>toggleSecondaryMuscle(value)}>{value}<span>{checked?"✓":"+"}</span></button>})}</div><label>{tr?"Ek özel değerler":"Additional custom values"}<input value={customSecondaryValues.join(", ")} onChange={event=>{const standards=secondaryValues.filter(value=>(exerciseFacets?.secondaryMuscleGroups??[]).some(standard=>standard.toLowerCase()===value.toLowerCase()));setDraft({...draft,secondaryMuscleGroups:[...standards,...csvValues(event.target.value)].join(", ")});}} placeholder={tr?"Virgülle ayırın":"Separate with commas"}/></label></div>
                <label className="wide-field">{tr?"Açıklama":"Description"}<textarea rows={2} value={draft.description ?? ""} onChange={(event) => setDraft({ ...draft, description: event.target.value })} /></label>
                <label className="toggle-field"><input type="checkbox" checked={draft.active !== false} onChange={(event) => setDraft({ ...draft, active: event.target.checked })} />{tr?"Katalogda aktif":"Active in catalog"}</label>
                <label className="toggle-field"><input type="checkbox" disabled={!draft.id||draft.techniqueReviewStatus!=="APPROVED"} checked={draft.aiEligible === true} onChange={(event) => setDraft({ ...draft, aiEligible: event.target.checked })} /><span>{tr?"AI antrenmanlarında kullanılabilir":"AI workout eligible"}<small>{!draft.id?(tr?"Teknik onaydan sonra etkinleştirilebilir":"Available after technique approval"):draft.techniqueReviewStatus!=="APPROVED"?(tr?"Önce teknik onay gerekir":"Technique approval required"):""}</small></span></label>
              </div></section>}
              {exerciseEditorTab==="technique"&&<section className="exercise-editor-section"><header><div><span>02</span><div><h3>{tr?"Teknik ve güvenlik":"Technique and safety"}</h3><p>{tr?"Kullanıcının hareketi doğru ve güvenli uygulaması için gösterilecek içerik.":"Content shown to help the user perform the movement safely and correctly."}</p></div></div></header><div className="exercise-technique-grid"><label>{tr?"Uygulama adımları":"Instructions"}<textarea rows={9} value={draft.instructions ?? ""} onChange={(event) => setDraft({ ...draft, instructions: event.target.value })} placeholder={tr?"Başlangıç pozisyonu, hareket sırası ve bitiş…":"Starting position, movement sequence and finish…"}/><small>{tr?"Kısa, sıralı ve uygulanabilir talimatlar yazın.":"Use concise, sequential and actionable instructions."}</small></label><label>{tr?"Güvenlik notları":"Safety notes"}<textarea rows={9} value={draft.safetyNotes ?? ""} onChange={(event) => setDraft({ ...draft, safetyNotes: event.target.value })} placeholder={tr?"Form uyarıları, kontrendikasyonlar ve durdurma işaretleri…":"Form warnings, contraindications and stop signals…"}/><small>{tr?"Riskleri ve kritik form hatalarını belirtin.":"Describe risks and critical form mistakes."}</small></label></div></section>}
              {exerciseEditorTab==="measurement"&&<section className="exercise-editor-section"><header><div><span>03</span><div><h3>{tr?"Kullanıcı kayıt modeli":"User logging model"}</h3><p>{tr?"Mobil uygulamada bu hareket için girilebilecek değerleri ve varsayılan deneyimi belirleyin.":"Choose which values users can record and define the default experience."}</p></div></div></header><div className="exercise-measurement-grid">{EXERCISE_MEASUREMENTS.map(option=>{const allowed=(draft.allowedMeasurementTypes??[]).includes(option.value);const isDefault=(draft.defaultMeasurementType??"DURATION")===option.value;return <article key={option.value} className={`${allowed||isDefault?"selected":""} ${isDefault?"default":""}`}><div><span className="exercise-measurement-icon">{option.value==="DURATION"?"◷":option.value==="DISTANCE"||option.value==="MIXED"?"↗":option.value.includes("WEIGHT")?"kg":"#"}</span><div><strong>{tr?option.labelTr:option.labelEn}</strong><small>{option.fields.map(field=>measurementFieldLabel(field,tr)).join(" · ")}</small></div></div><div><label><input type="checkbox" checked={allowed||isDefault} disabled={isDefault} onChange={event=>{setMeasurementTouched(true);setDraft({...draft,allowedMeasurementTypes:event.target.checked?Array.from(new Set([...(draft.allowedMeasurementTypes??[]),option.value])):(draft.allowedMeasurementTypes??[]).filter(value=>value!==option.value)})}}/>{tr?"İzin ver":"Allow"}</label><label><input type="radio" name="default-measurement" checked={isDefault} onChange={()=>{setMeasurementTouched(true);setDraft({...draft,defaultMeasurementType:option.value,allowedMeasurementTypes:Array.from(new Set([...(draft.allowedMeasurementTypes??[]),option.value]))})}}/>{tr?"Varsayılan":"Default"}</label></div></article>})}</div><div className="exercise-log-preview"><div><span>{tr?"MOBİL KAYIT ÖNİZLEMESİ":"MOBILE LOG PREVIEW"}</span><strong>{tr?EXERCISE_MEASUREMENTS.find(item=>item.value===(draft.defaultMeasurementType??"DURATION"))?.labelTr:EXERCISE_MEASUREMENTS.find(item=>item.value===(draft.defaultMeasurementType??"DURATION"))?.labelEn}</strong><small>{tr?"Kullanıcı hareketi kaydederken bu alanlar önce gösterilir.":"These fields are shown first when the user logs the exercise."}</small></div><div>{(EXERCISE_MEASUREMENTS.find(item=>item.value===(draft.defaultMeasurementType??"DURATION"))?.fields??[]).map(field=><span key={field}>{measurementFieldLabel(field,tr)}</span>)}</div></div></section>}
              {exerciseEditorTab==="evidence"&&<section className="exercise-editor-section"><header><div><span>04</span><div><h3>{tr?"Medya ve kaynak kanıtı":"Media and source evidence"}</h3><p>{tr?"Kullanıcı deneyimini ve editoryal güveni destekleyen varlıklar.":"Assets supporting the user experience and editorial confidence."}</p></div></div></header><div className="catalog-exercise-form-grid">
                <label>{tr?"İkon URL":"Icon URL"}<input type="url" value={draft.iconUrl ?? ""} onChange={(event) => setDraft({ ...draft, iconUrl: event.target.value })} /></label>
                <label>{tr?"Küçük görsel URL":"Thumbnail URL"}<input type="url" value={draft.thumbnailUrl ?? ""} onChange={(event) => setDraft({ ...draft, thumbnailUrl: event.target.value })} /></label>
                <label>{tr?"Video URL":"Video URL"}<input type="url" value={draft.videoUrl ?? ""} onChange={(event) => setDraft({ ...draft, videoUrl: event.target.value })} /></label>
                <label>{tr?"Animasyon URL":"Animation URL"}<input type="url" value={draft.animationUrl ?? ""} onChange={(event) => setDraft({ ...draft, animationUrl: event.target.value })} /></label>
                <label>{tr?"Kaynak adı":"Source name"}<input value={draft.sourceName ?? ""} onChange={(event) => setDraft({ ...draft, sourceName: event.target.value })} /></label>
                <label>{tr?"Kaynak URL":"Source URL"}<input type="url" value={draft.sourceUrl ?? ""} onChange={(event) => setDraft({ ...draft, sourceUrl: event.target.value })} /></label>
                <label>{tr?"Lisans adı":"License name"}<input value={draft.licenseName ?? ""} onChange={(event) => setDraft({ ...draft, licenseName: event.target.value })} /></label>
                <label className="wide-field">{tr?"Lisans URL":"License URL"}<input type="url" value={draft.licenseUrl ?? ""} onChange={(event) => setDraft({ ...draft, licenseUrl: event.target.value })} /></label>
              </div></section>}
            {draft.id&&exerciseEditorTab==="governance"&&<section className="exercise-editor-section"><header><div><span>05</span><div><h3>{tr?"İnceleme, sahiplik ve yayın":"Review, ownership and publishing"}</h3><p>{tr?"Sorumluyu, SLA tarihini, teknik kararı ve AI uygunluğunu yönetin.":"Manage ownership, SLA, technique decision and AI eligibility."}</p></div></div><Badge value={draft.techniqueReviewStatus??"PENDING"} tone={draft.techniqueReviewStatus==="APPROVED"?"good":"warn"}/></header><div className="exercise-governance-block"><h4>{tr?"İnceleme sahipliği":"Review ownership"}</h4><div className="catalog-review-grid">
                <label>Assignee email<input type="email" value={assignment.assignee} onChange={(event) => setAssignment({ ...assignment, assignee: event.target.value })} placeholder="catalog@grun.app" /></label>
                <label>Due at<input type="datetime-local" value={assignment.dueAt} onChange={(event) => setAssignment({ ...assignment, dueAt: event.target.value })} /></label>
                <label>Assignment reason<input value={assignment.reason} onChange={(event) => setAssignment({ ...assignment, reason: event.target.value })} /></label>
                <button className="ghost-button" type="button" disabled={!assignment.dueAt || !assignment.reason.trim() || actionState === "loading"} onClick={assignExerciseReview}>Assign review</button>
              </div></div><div className="exercise-governance-block"><h4>{tr?"Teknik karar":"Technique decision"}</h4><div className="catalog-technique-review">
                <div><Badge value={draft.techniqueReviewStatus ?? "PENDING"} tone={draft.techniqueReviewStatus === "APPROVED" ? "good" : "warn"} /><small>{draft.techniqueReviewedBy ? `Last reviewed by ${draft.techniqueReviewedBy}` : "No completed review"}</small></div>
                <label>Required review note<textarea rows={3} value={reviewNote} onChange={(event) => setReviewNote(event.target.value)} placeholder="Technique, safety, and media evidence checked." /></label>
                <div className="inline-actions"><button className="ghost-button danger-text" type="button" disabled={!reviewNote.trim()} onClick={() => void reviewExercise("REJECTED")}>Reject</button><button className="primary-button" type="button" disabled={!reviewNote.trim()} onClick={() => void reviewExercise("APPROVED")}>Approve technique</button></div>
              </div></div></section>}
              </div>
            </div>
          </div>
          <footer className="modal-actions padded-actions"><div className="exercise-submit-note"><strong>{draft.id?(tr?"Değişiklikleri kaydet":"Save changes"):(tr?"İncelemeye gönder":"Submit for review")}</strong><span>{tr?"Zorunlu alanlar: ad, MET kodu ve kalori":"Required: name, MET code and calories"}</span></div><button className="ghost-button" type="button" onClick={() => setSelected(null)}>{tr?"Vazgeç":"Cancel"}</button><button className="primary-button" disabled={actionState === "loading"} type="submit">{actionState==="loading"?(tr?"Kaydediliyor…":"Saving…"):draft.id?(tr?"Katalog kaydını güncelle":"Update catalog item"):(tr?"Onay bekleyen kayıt oluştur":"Create pending item")}</button></footer>
        </form>
      </div>}
    </>}
  </div>;
}
