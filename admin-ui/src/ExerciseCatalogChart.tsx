import { useCallback } from "react";
import type { EChartsCoreOption } from "echarts/core";
import { AdminEChart, adminChartPalette } from "./AdminEChart";

export type ExerciseCategoryDatum={label:string;value:number};

export function ExerciseCategoryChart({items,tr,selected,onSelect}:{items:ExerciseCategoryDatum[];tr:boolean;selected?:string;onSelect:(value:string)=>void}){
  const total=items.reduce((sum,item)=>sum+item.value,0);
  const visible=[...items].slice(0,10).reverse();
  const buildOption=useCallback(():EChartsCoreOption=>{const p=adminChartPalette();return {aria:{enabled:true},grid:{top:4,right:88,bottom:4,left:118},tooltip:{trigger:"axis",backgroundColor:p.surface,borderColor:p.line,padding:[10,12],textStyle:{color:p.text,fontFamily:p.fontFamily},axisPointer:{type:"shadow",shadowStyle:{color:"transparent"}},formatter:(params:unknown)=>{const point=(params as Array<{name:string;value:number}>)[0];const ratio=total?Math.round(point.value/total*100):0;return `<strong>${point.name}</strong><br/>${tr?"Hareket":"Exercises"}: ${point.value} · ${ratio}%`;}},xAxis:{type:"value",show:false,max:(value:{max:number})=>Math.max(1,value.max*1.2)},yAxis:{type:"category",data:visible.map(item=>item.label),axisLine:{show:false},axisTick:{show:false},axisLabel:{color:p.text,fontFamily:p.fontFamily,fontSize:12,width:104,overflow:"truncate",margin:14}},series:[{name:tr?"Hareket":"Exercises",type:"bar",barWidth:13,showBackground:true,backgroundStyle:{color:p.line,borderRadius:8,opacity:.45},data:visible.map(item=>({name:item.label,value:item.value,itemStyle:{color:selected===item.label?p.accent:p.primary,borderRadius:8},label:{show:true,position:"right",distance:7,color:p.text,fontFamily:p.fontFamily,fontSize:11,fontWeight:600,formatter:`{c} · ${total?Math.round(item.value/total*100):0}%`}})),emphasis:{focus:"self",itemStyle:{color:p.accent}}}]};},[visible,total,tr,selected]);
  return <AdminEChart ariaLabel={tr?"Ana kas grubuna göre egzersiz dağılımı":"Exercise distribution by primary muscle group"} buildOption={buildOption} className="exercise-category-chart" onSeriesDataClick={(_,name)=>onSelect(name)}/>;
}
