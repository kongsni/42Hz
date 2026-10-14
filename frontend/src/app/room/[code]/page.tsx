
'use client';

import {use, useCallback, useEffect, useRef, useState} from 'react';
import type {FormEvent} from 'react';
import Link from 'next/link';
import {api, readToken, saveToken, RoomState} from '@/lib/api';

type Question = {
 title: string;
 prompt: string;
 options?: string[];
 type: 'choice' | 'text';
 hint?: string;
};

type AnswerState = {
 questionIndex: number;
 submitted: boolean;
 bothSubmitted: boolean;
 myAnswer: string | null;
 partnerAnswer: string | null;
 submittedCount: number;
};

type ResultsState = {
 roomCode: string;
 allComplete: boolean;
 questions: {
  questionIndex: number;
  myAnswer: string | null;
  partnerAnswer: string | null;
  complete: boolean;
 }[];
};

const QUESTIONS: Question[] = [
 {type:'choice',title:'갑자기 계획이 바뀌었다면',prompt:'여행 출발 3시간 전, 예약한 숙소가 취소됐습니다. 가장 먼저 할 행동은?',options:['바로 다른 숙소를 찾는다','상대방에게 먼저 상황을 알린다','주변에서 해결할 방법을 찾는다','잠깐 멍해진 뒤 생각한다']},
 {type:'choice',title:'쉬는 날의 연락',prompt:'오랜만에 아무 일정 없는 휴일입니다. 가장 마음이 편한 방식은?',options:['하루 종일 같이 보내기','각자 쉬다가 중간중간 연락하기','같은 공간에서 각자 할 일 하기','그날 기분에 따라 정하기']},
 {type:'choice',title:'작은 오해가 생겼을 때',prompt:'대화 중 상대방의 말이 조금 서운하게 느껴졌습니다. 어떻게 할까요?',options:['바로 어떤 부분이 서운했는지 말한다','조금 생각한 뒤 차분히 말한다','상대방의 의도를 먼저 물어본다','일단 넘기고 분위기를 본다']},
 {type:'choice',title:'갑자기 생긴 두 시간',prompt:'약속 장소에 예상보다 두 시간 일찍 도착했습니다. 무엇을 하고 싶나요?',options:['근처를 함께 구경한다','카페에서 오래 이야기한다','각자 하고 싶었던 일을 한다','즉흥적으로 새로운 곳을 찾아본다']},
 {type:'choice',title:'위로가 필요한 날',prompt:'오늘 하루가 유난히 힘들었다고 털어놓는다면 가장 바라는 반응은?',options:['해결 방법을 같이 찾아주기','말 끊지 않고 들어주기','기분 전환할 일을 제안하기','말없이 곁에 있어주기']},
 {type:'choice',title:'뜻밖의 선물',prompt:'상대방이 작은 선물을 준비했다면 가장 기쁜 건 무엇인가요?',options:['평소 갖고 싶다고 했던 물건','내 취향을 세심하게 기억한 것','함께한 추억이 담긴 것','예상하지 못한 깜짝 이벤트']},
 {type:'choice',title:'의견이 다를 때',prompt:'함께 고른 영화 취향이 완전히 다릅니다. 어떻게 정하고 싶나요?',options:['번갈아 가며 고른다','둘 다 좋아할 만한 걸 찾는다','이번엔 상대방 취향을 따른다','각자 하나씩 보고 감상을 나눈다']},
 {type:'choice',title:'답장이 늦어지는 날',prompt:'상대방이 바쁜 날이라 연락이 평소보다 뜸합니다. 가장 가까운 생각은?',options:['바쁜가 보다 하고 기다린다','짧게라도 괜찮은지 물어본다','나중에 편할 때 연락하라고 한다','상황을 알 수 있으면 마음이 편하다']},
 {type:'choice',title:'둘만의 여행',prompt:'둘이 처음으로 하루 여행을 간다면 어떤 여행이 가장 끌리나요?',options:['계획 없이 걷는 바닷가','맛집과 카페를 찾아다니기','자연 속에서 조용히 쉬기','새로운 체험을 여러 개 해보기']},
 {type:'choice',title:'음악 취향',prompt:'지금 둘이 함께 들을 노래를 고른다면 어떤 분위기가 좋나요?',options:['잔잔하고 따뜻한 노래','신나고 리듬감 있는 노래','몽환적이고 분위기 있는 노래','추억이 떠오르는 익숙한 노래']},
 {type:'choice',title:'기억에 남는 순간',prompt:'함께 보낸 하루가 오래 기억에 남는 이유는 무엇일까요?',options:['많이 웃고 즐거웠기 때문에','진솔한 이야기를 나눴기 때문에','평소와 다른 경험을 했기 때문에','별일 없었지만 편안했기 때문에']},
 {type:'choice',title:'서로에게 바라는 것',prompt:'관계가 더 편안해지기 위해 가장 중요하다고 느끼는 건?',options:['솔직하게 이야기하기','서로의 시간을 존중하기','작은 표현을 자주 하기','말하지 않아도 살피려 노력하기']},
 {type:'text',title:'마지막으로 하나만',prompt:'상대방과 함께한 순간 중, 나중에도 문득 떠올리고 싶은 장면은 무엇인가요?',hint:'짧은 문장으로 편하게 적어주세요.'}
];

function buildReport(result: ResultsState) {
 const choices = result.questions.filter(q =>
     q.questionIndex < 12 && q.complete && q.myAnswer && q.partnerAnswer
 );
 const matched = choices.filter(q => q.myAnswer === q.partnerAnswer).length;
 const different = choices.length - matched;
 const similarity = choices.length ? Math.round(matched / choices.length * 100) : 0;

 const title = similarity >= 75
     ? '말하지 않아도 닮은 두 사람'
     : similarity >= 40
         ? '서로 다른 리듬, 같은 방향'
         : '서로에게 새로운 세계가 되어주는 두 사람';

 const connection = similarity >= 75
     ? '일상에서 비슷한 선택을 하는 순간이 많았어요. 익숙한 편안함 속에서도 서로에게 새로운 질문을 건네 보세요.'
     : similarity >= 40
         ? '같은 선택도, 다른 선택도 있었어요. 닮은 부분은 편안함이 되고 다른 부분은 이야깃거리가 될 수 있겠네요.'
         : '선택이 달랐던 순간이 더 많았어요. 정답이 다른 게 아니라 서로를 더 알아갈 장면이 그만큼 많다는 뜻일지도 몰라요.';

 const care = result.questions.find(q => q.questionIndex === 4);

 const comfort = care?.myAnswer && care?.partnerAnswer
     ? care.myAnswer === care.partnerAnswer
         ? '힘든 날 바라는 위로의 방식이 같았어요. 다음에는 그 마음을 실제로 표현해 보세요.'
         : `힘든 날 바라는 위로가 서로 달랐어요. 한 사람은 “${care.myAnswer}”, 다른 사람은 “${care.partnerAnswer}”을(를) 골랐어요. 서로에게 필요한 위로를 물어보는 것부터 시작해 보세요.`
     : '서로에게 어떤 위로가 필요한지 이야기해 보세요.';

 const memory = result.questions.find(q => q.questionIndex === 12);

 return {matched, different, similarity, title, connection, comfort, memory};
}

function Wave({connected = false}: {connected?: boolean}) {
 return (
     <div className="wave-stage" aria-label="서로 다른 리듬으로 흐르는 두 주파수">
      <svg viewBox="0 0 320 130" role="img">
       <defs>
        <linearGradient id="blueFlow">
         <stop offset="0%" stopColor="#b8d2ff"/>
         <stop offset="50%" stopColor="#83b2ff"/>
         <stop offset="100%" stopColor="#b8d2ff">
          <animate attributeName="offset" values="100%;0%;100%" dur="7s" repeatCount="indefinite"/>
         </stop>
        </linearGradient>
        <linearGradient id="pinkFlow">
         <stop offset="0%" stopColor="#f0c7e8"/>
         <stop offset="50%" stopColor="#d59bd3"/>
         <stop offset="100%" stopColor="#f0c7e8">
          <animate attributeName="offset" values="0%;100%;0%" dur="9s" repeatCount="indefinite"/>
         </stop>
        </linearGradient>
       </defs>
       <path d="M0 32 C18 32 18 10 36 10 S54 54 72 54 S90 10 108 10 S126 54 144 54 S162 10 180 10 S198 54 216 54 S234 10 252 10 S270 54 288 32 S306 32 320 32" stroke="url(#blueFlow)" className="wave-blue"/>
       <path d="M0 98 C14 98 14 118 28 118 S42 78 56 78 S70 118 84 118 S98 78 112 78 S126 118 140 118 S154 78 168 78 S182 118 196 118 S210 78 224 78 S238 118 252 118 S266 78 280 78 S294 98 320 98" stroke="url(#pinkFlow)" className="wave-pink"/>
       {connected && <circle cx="160" cy="65" r="3" className="connection-spark"/>}
      </svg>
     </div>
 );
}

export default function RoomPage({params}: {params: Promise<{code: string}>}) {
 const {code} = use(params);

 const activeQuestionRef = useRef(0);
 const requestVersionRef = useRef(0);

 const [token, setToken] = useState<string | null>(null);
 const [room, setRoom] = useState<RoomState | null>(null);
 const [nickname, setNickname] = useState('');
 const [invitationOpened, setInvitationOpened] = useState(false);
 const [error, setError] = useState('');
 const [busy, setBusy] = useState(false);
 const [phase, setPhase] = useState<'room' | 'countdown' | 'question' | 'results'>('room');
 const [countdown, setCountdown] = useState(3);
 const [index, setIndex] = useState(0);
 const [answer, setAnswer] = useState('');
 const [answerState, setAnswerState] = useState<AnswerState | null>(null);
 const [result, setResult] = useState<ResultsState | null>(null);
 const [copyMessage, setCopyMessage] = useState('');

 useEffect(() => {
  setToken(readToken(code));
 }, [code]);

 const refreshRoom = useCallback(async () => {
  if (!token) return;
  try {
   const data = await api<RoomState>(`/api/v1/rooms/${code}`, {
    headers: {Authorization: `Bearer ${token}`}
   });
   setRoom(data);
   setError('');
  } catch (e) {
   setError(e instanceof Error ? e.message : '방 상태를 확인하지 못했습니다.');
  }
 }, [code, token]);

 useEffect(() => {
  if (!token) return;
  void refreshRoom();
  const timer = setInterval(() => void refreshRoom(), 2200);
  return () => clearInterval(timer);
 }, [token, refreshRoom]);

 useEffect(() => {
  if (room?.status === 'READY' && phase === 'room') {
   setPhase('countdown');
   setCountdown(3);
  }
 }, [room?.status, phase]);

 useEffect(() => {
  if (phase !== 'countdown') return;
  if (countdown <= 0) {
   setPhase('question');
   return;
  }
  const timer = setTimeout(() => setCountdown(v => v - 1), 1000);
  return () => clearTimeout(timer);
 }, [phase, countdown]);

 // 이전 질문의 API 응답이 다음 질문 화면에 반영되지 않도록 합니다.
 const refreshAnswer = useCallback(async () => {
  if (!token || phase !== 'question') return;

  const requestedIndex = index;
  const version = ++requestVersionRef.current;

  try {
   const data = await api<AnswerState>(
       `/api/v1/rooms/${code}/answers/${requestedIndex}`,
       {headers: {Authorization: `Bearer ${token}`}}
   );

   if (
       version !== requestVersionRef.current ||
       activeQuestionRef.current !== requestedIndex ||
       data.questionIndex !== requestedIndex
   ) {
    return;
   }

   setAnswerState(data);

   if (data.submitted && data.myAnswer) {
    setAnswer(data.myAnswer);
   }
  } catch (e) {
   if (
       version === requestVersionRef.current &&
       activeQuestionRef.current === requestedIndex
   ) {
    setError(e instanceof Error ? e.message : '답변 상태를 불러오지 못했습니다.');
   }
  }
 }, [token, phase, code, index]);

 useEffect(() => {
  activeQuestionRef.current = index;
  ++requestVersionRef.current;
  setAnswerState(null);
  setAnswer('');

  if (phase !== 'question') return;

  void refreshAnswer();
  const timer = setInterval(() => void refreshAnswer(), 1800);

  return () => {
   clearInterval(timer);
   ++requestVersionRef.current;
  };
 }, [phase, index, refreshAnswer]);

 async function join(e: FormEvent) {
  e.preventDefault();
  setBusy(true);
  setError('');

  try {
   const data = await api<{participantToken: string}>(
       `/api/v1/rooms/${code}/join`,
       {
        method: 'POST',
        body: JSON.stringify({nickname: nickname.trim()})
       }
   );

   saveToken(code, data.participantToken);
   setToken(data.participantToken);
  } catch (e) {
   setError(e instanceof Error ? e.message : '입장에 실패했습니다.');
  } finally {
   setBusy(false);
  }
 }

 async function copy(value: string, label: string) {
  try {
   await navigator.clipboard.writeText(value);
   setCopyMessage(`${label} 복사 완료`);
   setTimeout(() => setCopyMessage(''), 1800);
  } catch {
   setCopyMessage('복사하지 못했습니다. 주소창을 이용해 주세요.');
  }
 }

 async function submitAnswer(e: FormEvent) {
  e.preventDefault();

  if (!token || busy || !answer.trim() || answerState?.submitted) return;

  const submittedIndex = index;
  const submittedAnswer = answer.trim();

  setBusy(true);
  setError('');
  ++requestVersionRef.current;

  try {
   const data = await api<AnswerState>(
       `/api/v1/rooms/${code}/answers`,
       {
        method: 'POST',
        headers: {Authorization: `Bearer ${token}`},
        body: JSON.stringify({
         questionIndex: submittedIndex,
         answer: submittedAnswer
        })
       }
   );

   if (
       activeQuestionRef.current === submittedIndex &&
       data.questionIndex === submittedIndex
   ) {
    ++requestVersionRef.current;
    setAnswerState(data);
   }
  } catch (e) {
   if (activeQuestionRef.current === submittedIndex) {
    setError(e instanceof Error ? e.message : '답변을 제출하지 못했습니다.');
   }
  } finally {
   setBusy(false);
  }
 }

 async function nextQuestion() {
  if (!answerState?.bothSubmitted || answerState.questionIndex !== index) return;

  ++requestVersionRef.current;

  if (index < 12) {
   activeQuestionRef.current = index + 1;
   setAnswerState(null);
   setAnswer('');
   setIndex(v => v + 1);
  } else {
   setPhase('results');

   try {
    const data = await api<ResultsState>(
        `/api/v1/rooms/${code}/results`,
        {headers: {Authorization: `Bearer ${token}`}}
    );
    setResult(data);
   } catch (e) {
    setError(e instanceof Error ? e.message : '결과를 불러오지 못했습니다.');
   }
  }
 }

 useEffect(() => {
  if (phase !== 'results' || !token) return;

  let live = true;

  async function load() {
   try {
    const data = await api<ResultsState>(
        `/api/v1/rooms/${code}/results`,
        {headers: {Authorization: `Bearer ${token}`}}
    );
    if (live) setResult(data);
   } catch {}
  }

  void load();
  const timer = setInterval(() => void load(), 2000);

  return () => {
   live = false;
   clearInterval(timer);
  };
 }, [phase, token, code]);

 async function shareReport() {
  if (!result) return;

  const report = buildReport(result);
  const text = `42Hz · ${report.title}\n같은 선택 ${report.matched}개 · 다른 선택 ${report.different}개\n${report.connection}`;

  try {
   if (navigator.share) {
    await navigator.share({
     title: '42Hz · 우리의 주파수',
     text
    });
   } else {
    await copy(text, '결과 요약');
   }
  } catch (e) {
   if (e instanceof Error && e.name !== 'AbortError') {
    setCopyMessage('공유할 수 없습니다. 요약 복사를 이용해 주세요.');
   }
  }
 }

 const question = QUESTIONS[index];
 const visibleAnswerState =
     answerState?.questionIndex === index ? answerState : null;

 return (
     <main className="panel-page">
      <Link className="logo" href="/">42Hz</Link>
      <div className="room-shell">
       <section className="room-card">

        {phase === 'room' && (
            <>
             <div className="eyebrow">YOUR PRIVATE SPACE</div>
             {token ? (
                 <>
                  <h1>우리의 주파수를 맞추는 중</h1>
                  <p className="muted">상대방에게 초대 코드를 보내주세요. 두 사람이 입장하면 함께 시작합니다.</p>
                  <div className="code-label" style={{textAlign:'center',marginTop:18}}>INVITATION CODE</div>
                  <div className="code-box" style={{borderStyle:'solid'}}>{code}</div>

                  <div className="member-list">
                   {[0,1].map(i => {
                    const p = room?.participants[i];
                    return (
                        <div className="member" key={i}>
                         <span className={`member-dot ${i === 1 ? 'pink' : ''}`}/>
                         <div>{p?.nickname ?? (i === 0 ? '나' : '상대방')}</div>
                         <small className="muted">{p ? '참여 완료' : '입장 대기 중'}</small>
                        </div>
                    );
                   })}
                  </div>

                  <Wave/>
                  <button className="action" onClick={() => copy(code, '초대 코드')}>초대 코드 복사</button>
                  <button className="action secondary" onClick={() => copy(window.location.origin + `/room/${code}`, '초대 링크')}>초대 링크 복사</button>
                  {copyMessage && <p className="status-line" role="status">{copyMessage}</p>}
                  <p className="status-line">
                   <span className="status-dot"/>
                   {room?.status === 'READY' ? '연결을 준비하고 있어요…' : '상대방이 입장하면 주파수가 연결됩니다.'}
                  </p>
                 </>
             ) : (
                 <div className="invite-experience">
                  <div className="invite-heading">
                   <div className="eyebrow">A LITTLE NOTE FOR YOU</div>
                   <h1>{invitationOpened ? '두 사람의 주파수를 연결해 볼까요?' : '당신에게 초대장이 도착했어요.'}</h1>
                   <p className="muted">{invitationOpened ? '이름을 남기면 둘만의 이야기가 시작돼요.' : '누군가 당신과 조금 더 가까워지고 싶어 해요.'}</p>
                  </div>

                  <button
                      type="button"
                      className={`envelope-button ${invitationOpened ? 'is-open' : ''}`}
                      onClick={() => setInvitationOpened(true)}
                      disabled={invitationOpened}
                      aria-label="초대장 열기"
                  >
                    <span className="envelope-scene" aria-hidden="true">
                      <span className="envelope-letter">
                        <span className="letter-mark">42Hz</span>
                        <span className="letter-line"/>
                        <span className="letter-line short"/>
                      </span>
                      <span className="envelope-back"/>
                      <span className="envelope-flap"/>
                      <span className="envelope-front"/>
                      <span className="envelope-seal">♡</span>
                    </span>
                  </button>

                  {!invitationOpened ? (
                      <button type="button" className="action invite-open-action" onClick={() => setInvitationOpened(true)}>
                       초대장 열어보기 <span aria-hidden="true">→</span>
                      </button>
                  ) : (
                      <form className="invite-join-form" onSubmit={join}>
                       <div className="invite-message">서로 다른 두 개의 리듬이<br/>하나의 이야기를 만들어 갈 거예요.</div>
                       <label htmlFor="nickname">어떻게 불러드릴까요?</label>
                       <input
                           id="nickname"
                           className="field"
                           placeholder="예: 42Hz"
                           maxLength={16}
                           required
                           value={nickname}
                           onChange={e => setNickname(e.target.value)}
                       />
                       <button className="action" disabled={busy || !nickname.trim()}>
                        {busy ? '입장 중…' : '주파수 연결하기 →'}
                       </button>
                      </form>
                  )}
                 </div>
             )}
            </>
        )}

        {phase === 'countdown' && (
            <div className="experience-center">
             <div className="eyebrow">CONNECTED</div>
             <Wave connected/>
             <h1>두 개의 주파수가 만났어요.</h1>
             <p className="muted">이제 서로를 조금 더 알아볼 시간이에요.</p>
             <div className="countdown-number">{countdown || '시작'}</div>
             <p className="muted">{countdown ? '함께 시작할 준비를 해주세요.' : '첫 번째 질문으로 이동합니다.'}</p>
            </div>
        )}

        {phase === 'question' && (
            <>
             <div className="question-progress">
              <span>QUESTION {String(index + 1).padStart(2, '0')} / 13</span>
              <span>{Math.round(((index + 1) / 13) * 100)}%</span>
             </div>
             <div className="progress-track">
              <div style={{width: `${((index + 1) / 13) * 100}%`}}/>
             </div>
             <div className="eyebrow" style={{marginTop:28}}>42HZ · OUR EXPERIENCE</div>
             <h1>{question.title}</h1>
             <p className="question-prompt">{question.prompt}</p>

             {visibleAnswerState?.bothSubmitted ? (
                 <div className="answer-reveal">
                  <p className="eyebrow">BOTH ANSWERS SUBMITTED</p>
                  <h2>서로의 선택을 확인해 보세요.</h2>
                  <div className="answer-pair">
                   <div>
                    <small>나의 답변</small>
                    <p>{visibleAnswerState.myAnswer}</p>
                   </div>
                   <div>
                    <small>상대방의 답변</small>
                    <p>{visibleAnswerState.partnerAnswer}</p>
                   </div>
                  </div>
                  <button className="action" onClick={nextQuestion}>
                   {index === 12 ? '결과 확인하기 →' : '다음 질문으로 →'}
                  </button>
                 </div>
             ) : visibleAnswerState?.submitted ? (
                 <div className="waiting-answer">
                  <Wave/>
                  <h2>답변을 보관했어요.</h2>
                  <p className="muted">상대방이 답변을 제출하면 함께 공개됩니다.</p>
                  <p className="status-line">
                   <span className="status-dot"/>
                   {visibleAnswerState.submittedCount}/2명 제출 완료
                  </p>
                 </div>
             ) : (
                 <form key={index} onSubmit={submitAnswer} className="answer-form">
                  {question.type === 'choice' ? (
                      question.options?.map((option, i) => (
                          <label className={`answer-option ${answer === option ? 'selected' : ''}`} key={option}>
                           <input
                               type="radio"
                               name={`q-${index}`}
                               value={option}
                               checked={answer === option}
                               onChange={() => setAnswer(option)}
                           />
                           <span className="option-letter">{String.fromCharCode(65 + i)}</span>
                           <span>{option}</span>
                          </label>
                      ))
                  ) : (
                      <>
                      <textarea
                          className="field text-answer"
                          maxLength={1000}
                          rows={4}
                          value={answer}
                          onChange={e => setAnswer(e.target.value)}
                          placeholder={question.hint}
                      />
                       <p className="muted char-count">{answer.length}/1000</p>
                      </>
                  )}
                  <button className="action" disabled={busy || !answer.trim()}>
                   {busy ? '제출 중…' : '답변 제출하기 →'}
                  </button>
                  <p className="muted small-note">상대방이 제출하기 전까지 답변은 공개되지 않습니다.</p>
                 </form>
             )}
            </>
        )}

        {phase === 'results' && (
            <>
             <div className="eyebrow">YOUR FREQUENCY REPORT</div>
             <h1>서로의 답을 확인해 보세요.</h1>
             <p className="muted">두 사람의 답변을 나란히 모았어요. 서로 다른 선택도 새로운 발견이 될 수 있습니다.</p>

             {!result?.allComplete ? (
                 <div className="waiting-answer">
                  <Wave/>
                  <p>상대방의 마지막 답변을 기다리고 있어요.</p>
                 </div>
             ) : (
                 <div className="results-list">
                  {(() => {
                   const report = buildReport(result);
                   return (
                       <section className="frequency-report">
                        <div className="eyebrow">42Hz · OUR FREQUENCY</div>
                        <h2>{report.title}</h2>
                        <p className="muted">두 사람의 답변으로 발견한 작은 이야기</p>
                        <Wave connected/>
                        <div className="report-stats">
                         <div><strong>{report.matched}</strong><span>같은 선택</span></div>
                         <div><strong>{report.different}</strong><span>다른 선택</span></div>
                        </div>
                        <div className="report-insights">
                         <article>
                          <span>01 · 우리의 리듬</span>
                          <h3>{report.similarity >= 75 ? '생각보다 닮은 마음' : report.similarity >= 40 ? '닮음과 다름 사이' : '서로 다른 매력'}</h3>
                          <p>{report.connection}</p>
                         </article>
                         <article>
                          <span>02 · 서로를 위한 힌트</span>
                          <h3>힘든 날의 위로</h3>
                          <p>{report.comfort}</p>
                         </article>
                         <article>
                          <span>03 · 오래 남을 장면</span>
                          <h3>각자의 마음에 남은 기억</h3>
                          <p>서로가 고른 장면을 천천히 읽어 보세요. 같은 하루를 다르게 기억하고 있을 수도 있어요.</p>
                          {report.memory && (
                              <div className="report-memories">
                               <p><b>나</b> {report.memory.myAnswer || '—'}</p>
                               <p><b>상대방</b> {report.memory.partnerAnswer || '—'}</p>
                              </div>
                          )}
                         </article>
                        </div>
                        <p className="report-disclaimer">이 분석은 답변을 바탕으로 만든 가벼운 이야기예요. 궁합이나 성격을 진단하지 않습니다.</p>
                        <button
                            className="action secondary"
                            onClick={() => {
                             const lines = [
                              `42Hz · ${report.title}`,
                              `같은 선택 ${report.matched}개 · 다른 선택 ${report.different}개`,
                              report.connection,
                              report.comfort
                             ];
                             void copy(lines.join('\n'), '결과 요약');
                            }}
                        >
                         우리의 결과 요약 복사하기
                        </button>
                       </section>
                   );
                  })()}

                  {QUESTIONS.map((q, i) => {
                   const item = result.questions.find(r => r.questionIndex === i);
                   return (
                       <article className="result-item" key={i}>
                        <div className="eyebrow">QUESTION {String(i + 1).padStart(2, '0')}</div>
                        <h2>{q.title}</h2>
                        <p className="muted">{q.prompt}</p>
                        <div className="answer-pair">
                         <div>
                          <small>나의 답변</small>
                          <p>{item?.myAnswer || '답변 없음'}</p>
                         </div>
                         <div>
                          <small>상대방의 답변</small>
                          <p>{item?.partnerAnswer || '답변 없음'}</p>
                         </div>
                        </div>
                       </article>
                   );
                  })}

                  <div className="result-actions">
                   <h2>오늘, 우리의 주파수가 닿았어요.</h2>
                   <p className="muted">오늘 나눈 대화가 내일 서로를 이해하는 작은 힌트가 되기를.</p>
                   <button className="action" onClick={shareReport}>결과 공유하기 ↗</button>
                   <button
                       className="action secondary"
                       onClick={() => {
                        ++requestVersionRef.current;
                        activeQuestionRef.current = 0;
                        setIndex(0);
                        setPhase('question');
                        setAnswerState(null);
                        setAnswer('');
                       }}
                   >
                    답변 다시 살펴보기
                   </button>
                   <Link className="action secondary" href="/rooms/create">새로운 체험 시작하기 →</Link>
                   <Link className="result-home" href="/">⌂ 홈으로 돌아가기</Link>
                   {copyMessage && <p className="status-line" role="status">{copyMessage}</p>}
                  </div>
                 </div>
             )}
            </>
        )}

        {error && <p role="alert" className="error-text">{error}</p>}
       </section>
      </div>
     </main>
 );
}
