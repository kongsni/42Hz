export const API = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';
export type RoomState = { roomCode:string; status:string; participants:{id:string;nickname:string;me:boolean}[] };
export function saveToken(code:string,token:string) { sessionStorage.setItem(`42hz:${code}`,token); }
export function readToken(code:string) { return sessionStorage.getItem(`42hz:${code}`); }

export class ApiError extends Error {
    constructor(message: string, public readonly status: number) {
        super(message);
        this.name = 'ApiError';
    }
    get retryable() { return this.status === 0 || this.status === 408 || this.status === 429 || this.status >= 500; }
}

export async function api<T>(path:string, init:RequestInit = {}):Promise<T> {
    const controller = new AbortController();
    const abort = () => controller.abort();
    init.signal?.addEventListener('abort', abort, {once: true});
    if (init.signal?.aborted) controller.abort();
    // Free instances can take more than 50 seconds to wake up.
    const timeout = setTimeout(abort, 90000);
    try {
        const headers = new Headers(init.headers);
        headers.set('Content-Type', 'application/json');
        const response = await fetch(`${API}${path}`, {
            ...init, headers, signal: controller.signal, cache:'no-store',
        });
        if (!response.ok) {
            let message = response.status === 410 ? '만료된 방입니다.'
                : response.status === 401 ? '참여자 인증이 만료되었거나 올바르지 않습니다.'
                : response.status === 404 ? '요청한 방 또는 결과를 찾을 수 없습니다.'
                : response.status >= 500 ? '일시적인 서버 오류입니다. 잠시 후 다시 시도해 주세요.'
                : `요청에 실패했습니다 (${response.status})`;
            try {
                const body = await response.json();
                if (typeof body.detail === 'string') message = body.detail;
            } catch { /* The proxy can return a non-JSON error response. */ }
            throw new ApiError(message, response.status);
        }
        try { return await response.json(); }
        catch { throw new ApiError('서버 응답을 읽지 못했습니다. 잠시 후 다시 시도해 주세요.', 502); }
    } catch (error) {
        if (error instanceof ApiError) throw error;
        if (init.signal?.aborted) throw error;
        throw new ApiError(controller.signal.aborted
            ? '서버 응답이 늦어지고 있습니다. 잠시 후 다시 시도해 주세요.'
            : '서버에 연결하지 못했습니다. 네트워크 상태를 확인해 주세요.', 0);
    } finally {
        clearTimeout(timeout);
        init.signal?.removeEventListener('abort', abort);
    }
}
