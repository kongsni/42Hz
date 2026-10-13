export const API = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';
export type RoomState = { roomCode:string; status:string; participants:{id:string;nickname:string;me:boolean}[] };
export function saveToken(code:string,token:string) { sessionStorage.setItem(`42hz:${code}`,token); }
export function readToken(code:string) { return sessionStorage.getItem(`42hz:${code}`); }
export async function api<T>(path:string, init:RequestInit = {}):Promise<T> {
 const response=await fetch(`${API}${path}`,{...init,headers:{'Content-Type':'application/json',...init.headers},cache:'no-store'});
 if(!response.ok) {
   let message = `요청에 실패했습니다 (${response.status})`;
   try { const body = await response.json(); message = body.detail ?? body.message ?? message; } catch {}
   throw new Error(message);
 }
 return response.json();
}
