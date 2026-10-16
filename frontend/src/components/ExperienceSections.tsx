'use client';
import {useRef, useState, type KeyboardEvent} from 'react';
import Link from 'next/link';
import styles from './ExperienceSections.module.css';
const episodes=[{num:'01',tag:'무료 체험',title:'마음의 주파수',desc:'각자 답하고 서로의 선택을 확인하며 새로운 이야기를 발견해요.',tone:'blue',price:'무료 맛보기',time:'13개의 질문'},{num:'02',tag:'준비 중',title:'서로의 온도',desc:'위로, 연락, 애정 표현. 같은 순간에 서로 다른 마음을 발견해요.',tone:'pink',price:'2,900원 · 2인',time:'각자의 속도로'},{num:'03',tag:'준비 중',title:'우리의 갈림길',desc:'선택의 순간, 우리는 같은 방향을 바라보고 있을까요?',tone:'purple',price:'2,900원 · 2인',time:'각자의 속도로'}] as const;
export default function ExperienceSections(){const [active,setActive]=useState(0);const episode=episodes[active];
const tabs=useRef<(HTMLButtonElement | null)[]>([]);
function moveTab(event:KeyboardEvent<HTMLButtonElement>, index:number){
  let next:number;
  if(event.key==='ArrowRight') next=(index+1)%episodes.length;
  else if(event.key==='ArrowLeft') next=(index+episodes.length-1)%episodes.length;
  else if(event.key==='Home') next=0;
  else if(event.key==='End') next=episodes.length-1;
  else return;
  event.preventDefault();setActive(next);tabs.current[next]?.focus();
}
return <div className={styles.u042}>
<section className={styles.intro} id="experience"><div className={styles.label}>함께하는 방법</div><h2>서로를 알아가는 순간도<br/><em>하나의 전시가 될 수 있으니까.</em></h2><p>같은 질문에 각자 답하고, 두 사람의 선택을 함께 확인해요.<br/>휴대폰 두 대만 있으면 어디서든 둘만의 전시가 시작됩니다.</p><div className={styles.steps}>{[{n:'01',t:'공간 만들기',d:'한 사람이 공간을 열고 초대장을 보내요.'},{n:'02',t:'선택 나누기',d:'각자 답하고 두 사람의 답변을 함께 열어봐요.'},{n:'03',t:'우리의 기록 남기기',d:'예상 밖의 차이를 발견하고 결과를 간직해요.'}].map(s=><div className={styles.step} key={s.n}><span>{s.n}</span><h3>{s.t}</h3><p>{s.d}</p></div>)}</div></section>
<section className={styles.episodes} id="episodes"><div className={styles.label}>체험 에피소드</div><h2>오늘, 어떤 마음을<br/>발견해 볼까요?</h2><div className={styles.deck} role="tablist" aria-label="에피소드 선택">{episodes.map((e,i)=><button key={e.num} type="button" role="tab" id={`episode-tab-${i}`} ref={node=>{tabs.current[i]=node;}} tabIndex={active===i?0:-1} onKeyDown={event=>moveTab(event,i)} aria-selected={active===i} aria-controls="episode-panel" className={`${styles.tab} ${active===i?styles.selected:''}`} onClick={()=>setActive(i)}>{e.num} <span>{e.title}</span></button>)}</div><div id="episode-panel" role="tabpanel" aria-labelledby={`episode-tab-${active}`} tabIndex={0} className={`${styles.episodeCard} ${styles[episode.tone]}`}><div className={styles.episodeInfo}><span className={styles.episodeTag}>{episode.tag}</span><span className={styles.episodeNum}>{episode.num}</span><h3>{episode.title}</h3><p>{episode.desc}</p><div className={styles.episodeMeta}><span>◷ {episode.time}</span><span>♧ {episode.price}</span></div><Link href="/rooms/create" className={styles.episodeAction}>{active===0?'무료 체험 시작하기':'체험방 미리 만들기'} <span aria-hidden="true">↗</span></Link>{active!==0&&<small>정식 에피소드는 준비 중이며 결제 기능은 아직 없습니다.</small>}</div><div className={styles.art} aria-hidden="true"><div className={styles.artCircle}/><svg viewBox="0 0 320 180" preserveAspectRatio="none"><path d="M0 88 C45 0 75 176 120 88 S200 0 240 88 S290 176 320 88"/><path d="M0 100 C45 188 75 12 120 100 S200 188 240 100 S290 12 320 100"/></svg><div className={styles.artBrand}>42Hz</div></div></div></section>
<section className={styles.pricing} id="pricing"><div><div className={styles.label}>앞으로 만날 이야기</div><h2>둘이 함께하는<br/>서로를 발견하는 시간.</h2><p>첫 만남의 설렘부터 오래된 연인의 새로운 발견까지.<br/>에피소드 하나로 둘만의 데이트를 완성해 보세요.</p></div><div className={styles.priceCard}><span>정식 에피소드 · 2인 이용</span><div className={styles.price}>2,900<small>원</small></div><p>정식 에피소드의 예정 가격입니다. 현재 체험은 무료예요.</p><div className={styles.priceFeatures}>✓ 2인 입장권 &nbsp; ✓ 전체 에피소드 &nbsp; ✓ 결과 카드</div><div className={styles.priceNote}>정식 에피소드 출시 예정 · 현재 결제 불가</div></div></section>
<section className={styles.faq}><div className={styles.label}>체험 안내</div><h2>체험 전에 궁금한 점</h2><details><summary>꼭 같은 장소에 있어야 하나요?</summary><p>아니요. 각자의 휴대폰에서 초대 링크로 같은 방에 참여할 수 있어요. 두 사람이 답변을 제출하면 함께 확인할 수 있습니다.</p></details><details><summary>로그인이 필요한가요?</summary><p>로그인 없이 닉네임으로 방을 만들고, 초대 링크를 보내면 시작할 수 있어요.</p></details><details><summary>결과가 실제 심리 진단인가요?</summary><p>아니요. 서로의 응답을 비교하는 엔터테인먼트 경험이며 심리 진단이나 관계의 미래 예측이 아닙니다.</p></details></section>
<footer className={styles.end}><span>42Hz</span><p>우리의 주파수는 얼마나 같을까?</p><Link href="/rooms/create">둘만의 공간 입장하기 ↗</Link></footer>
</div>}
