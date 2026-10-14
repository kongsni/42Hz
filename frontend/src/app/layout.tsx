
import type { ReactNode } from 'react';
import Script from 'next/script';
import './globals.css';

export const metadata = {
    title: '42Hz',
    description: '서로의 주파수를 발견하는 시간',
};

export default function RootLayout({
                                       children,
                                   }: {
    children: ReactNode;
}) {
    return (
        <html lang="ko">
        <body>
        {children}

        <Script
            src="https://t1.kakaocdn.net/kakao_js_sdk/2.7.9/kakao.min.js"
            strategy="afterInteractive"
            crossOrigin="anonymous"
        />
        </body>
        </html>
    );
}
