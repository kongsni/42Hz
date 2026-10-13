'use client';
import {useState} from 'react';
import {useRouter} from 'next/navigation';
import Link from 'next/link';
import {api,saveToken} from '@/lib/api';
export default function CreateRoom(){
 const [name,setName]=useState('');const [error,setError]=useState('');const [busy,setBusy]=useState(false);const router=useRouter();
 async function create(e:React.FormEvent){e.preventDefault();if(!name.trim())return;setBusy(true);setError('');try{const data=await api<{roomCode:string;participantToken:string}>('/api/v1/rooms',{method:'POST',body:JSON.stringify({nickname:name.trim()})});saveToken(data.roomCode,data.participantToken);router.push(`/room/${data.roomCode}`)}catch(err){setError(err instanceof Error?err.message:'공간을 만들지 못했습니다. 백엔드가 실행 중인지 확인해 주세요.')}finally{setBusy(false)}}
 return <main className="panel-page"><Link className="logo" href="/">42Hz</Link><section className="panel"><div className="eyebrow">THE BEGINNING</div><h1>우리만의 공간 만들기</h1><p className="muted">서로의 주파수를 맞춰볼 준비가 되셨나요?<br/>먼저 이 공간에서 사용할 이름을 정해주세요.</p><form onSubmit={create}><label htmlFor="nickname">어떻게 불러드릴까요?</label><input id="nickname" className="field" placeholder="예: 42Hz" maxLength={16} required value={name} onChange={e=>setName(e.target.value)} autoComplete="nickname"/><button className="action" disabled={busy||!name.trim()}>{busy?'공간 만드는 중…':'둘만의 공간 만들기 →'}</button>{error&&<p role="alert" className="error-text">{error}</p>}</form><p className="muted" style={{fontSize:12,textAlign:'center',marginTop:22}}>두 사람, 하나의 경험.</p></section></main>
}
