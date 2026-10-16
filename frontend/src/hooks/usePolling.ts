'use client';

import {useEffect} from 'react';
import {ApiError} from '@/lib/api';

/** A false result completes polling. Only reads are retried; mutations are never replayed. */
export function usePolling(
    task: (signal: AbortSignal) => Promise<boolean>,
    enabled: boolean,
    interval: number,
    onError: (error: unknown) => void,
) {
    useEffect(() => {
        if (!enabled) return;
        let disposed = false;
        let completed = false;
        let running = false;
        let failures = 0;
        let timer: ReturnType<typeof setTimeout> | undefined;
        let request: AbortController | undefined;

        async function run() {
            if (disposed || completed || running || document.hidden) return;
            running = true;
            request = new AbortController();
            try {
                completed = !(await task(request.signal));
                failures = 0;
            } catch (error) {
                if (!disposed) {
                    onError(error);
                    failures++;
                    if (error instanceof ApiError && !error.retryable) completed = true;
                }
            } finally {
                running = false;
                if (!disposed && !completed && !document.hidden) {
                    timer = setTimeout(run, Math.min(interval * 2 ** Math.min(failures, 4), 30000));
                }
            }
        }

        function visible() {
            clearTimeout(timer);
            if (!document.hidden) void run();
        }
        document.addEventListener('visibilitychange', visible);
        void run();
        return () => {
            disposed = true;
            clearTimeout(timer);
            request?.abort();
            document.removeEventListener('visibilitychange', visible);
        };
    }, [task, enabled, interval, onError]);
}
