// Lucian UX/UI Prompt #101 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#101
'use client';
import {useRef, useState, type PointerEvent, type KeyboardEvent} from 'react';
import styles from './SplitExperience.module.css';

export default function SplitExperience(){
  const [split,setSplit]=useState(42);
  const [theme,setTheme]=useState<'dark'|'light'>('light');
  const panes=useRef<HTMLDivElement>(null);
  const separator=useRef<HTMLButtonElement>(null);
  const clamp=(n:number)=>setSplit(Math.max(25,Math.min(65,Math.round(n))));
  function onKeyDown(e:KeyboardEvent<HTMLButtonElement>){
    if(!['ArrowLeft','ArrowRight','Home','End','Escape'].includes(e.key))return;
    e.preventDefault();
    clamp(e.key==='Escape'?42:e.key==='Home'?25:e.key==='End'?65:split+(e.key==='ArrowRight'?3:-3));
  }
  function onPointerDown(e:PointerEvent<HTMLButtonElement>){e.currentTarget.setPointerCapture(e.pointerId)}
  function onPointerMove(e:PointerEvent<HTMLButtonElement>){
    if(!e.currentTarget.hasPointerCapture(e.pointerId)||!panes.current)return;
    const rect=panes.current.getBoundingClientRect();clamp((e.clientX-rect.left)/rect.width*100);
  }
  return <section className={`${styles.u101} ${theme==='light'?styles.light:styles.dark}`} aria-labelledby="split-heading">
    <div className={styles.heading}><div><span className={styles.kicker}>42Hz · EXPERIENCE PREVIEW</span><h2 id="split-heading">서로를 발견하는 공간</h2><p>경계를 움직여 체험 소개와 결과 카드의 크기를 조절해 보세요.</p></div><button type="button" className={styles.themeButton} onClick={()=>setTheme(t=>t==='dark'?'light':'dark')} aria-label={`${theme==='dark'?'라이트':'다크'} 테마로 전환`}>{theme==='dark'?'☀ 라이트':'☾ 다크'}</button></div>
    <div className={styles.panes} ref={panes} style={{'--split':`${split}%`} as React.CSSProperties}>
      <section className={styles.doc} aria-label="체험 소개"><span className={styles.kicker}>ROOM 01</span><h3>우리의<br/>주파수</h3><p>같은 질문,<br/>서로 다른 마음.<br/>상대방의 선택을 예상하고<br/>새로운 이야기를 발견해요.</p><span className={styles.note}>2인 참여 · 약 25분</span></section>
      <button ref={separator} type="button" className={styles.sep} role="separator" aria-label="체험 소개 폭, 방향키로 조절" aria-orientation="vertical" aria-valuemin={25} aria-valuemax={65} aria-valuenow={split} aria-controls="split-preview" onKeyDown={onKeyDown} onPointerDown={onPointerDown} onPointerMove={onPointerMove}>⋮</button>
      <section id="split-preview" className={styles.preview} aria-label="결과 카드 미리보기"><div className={styles.poster} role="img" aria-label="푸른색과 분홍색의 두 주파수가 교차하는 42Hz 결과 카드"><span className={styles.posterBrand}>42Hz</span><svg viewBox="0 0 180 110" aria-hidden="true" preserveAspectRatio="none"><path d="M0 55 Q22 5 45 55 T90 55 T135 55 T180 55" stroke="#B6D7FF"/><path d="M0 65 Q22 115 45 65 T90 65 T135 65 T180 65" stroke="#FFD2E9"/></svg><span className={styles.posterCaption}>우리만의 주파수 기록</span></div></section>
    </div>
    <p className={styles.help}>가운데 경계를 드래그하거나 ← / →로 조절 · Home / End로 최소·최대 · Esc로 초기화</p>
  </section>
}
