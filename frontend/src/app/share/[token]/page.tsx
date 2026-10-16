
'use client';

import { use, useEffect, useState } from 'react';
import Link from 'next/link';
import { api } from '@/lib/api';

type SharedResult = {
    title: string;
    connection: string;
    matched: number;
    different: number;
    similarity: number;
};

export default function SharedResultPage({
                                             params,
                                         }: {
    params: Promise<{ token: string }>;
}) {
    const { token } = use(params);

    const [result, setResult] = useState<SharedResult | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        let active = true;

        async function loadResult() {
            try {
                const data = await api<SharedResult>(
                    `/api/v1/shared-results/${encodeURIComponent(token)}`
                );

                if (active) {
                    setResult(data);
                }
            } catch (e) {
                if (active) {
                    setError(
                        e instanceof Error
                            ? e.message
                            : '공유 결과를 불러오지 못했습니다.'
                    );
                }
            } finally {
                if (active) {
                    setLoading(false);
                }
            }
        }

        void loadResult();

        return () => {
            active = false;
        };
    }, [token]);

    return (
        <main className="panel-page">
            <Link className="logo" href="/">
                42Hz
            </Link>

            <div className="room-shell">
                <section className="room-card">
                    {loading ? (
                        <>
                            <h1>두 사람의 주파수를 불러오는 중…</h1>
                            <p className="muted">
                                잠시만 기다려 주세요.
                            </p>
                        </>
                    ) : error ? (
                        <>
                            <h1>결과를 불러올 수 없어요.</h1>
                            <p className="muted">{error}</p>
                            <Link className="action" href="/">
                                홈으로 돌아가기 →
                            </Link>
                        </>
                    ) : result ? (
                        <div className="frequency-report">
                            <h1>{result.title}</h1>

                            <div className="report-stats">
                                <div>
                                    <strong>{result.matched}</strong>
                                    <span>같은 선택</span>
                                </div>

                                <div>
                                    <strong>{result.different}</strong>
                                    <span>다른 선택</span>
                                </div>
                            </div>

                            <div className="report-insights">
                                <article>
                                    <span>우리의 리듬</span>
                                    <h2>두 사람의 주파수</h2>
                                    <p>{result.connection}</p>
                                </article>
                            </div>

                            <p className="report-disclaimer">
                                이 결과는 두 사람의 선택을 바탕으로 만든
                                가벼운 이야기입니다. 개별 답변은 공개되지 않습니다.
                            </p>

                            <Link className="action" href="/rooms/create">
                                우리도 주파수 맞춰보기 →
                            </Link>
                        </div>
                    ) : (
                        <p className="muted">
                            공유된 결과를 찾을 수 없습니다.
                        </p>
                    )}
                </section>
            </div>
        </main>
    );
}
