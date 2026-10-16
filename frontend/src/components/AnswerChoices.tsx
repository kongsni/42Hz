// Lucian UX/UI Prompt #015 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#015
'use client';

import {useLayoutEffect, useRef, type KeyboardEvent} from 'react';
import './AnswerChoices.css';

type Props = {options: string[]; value: string; onChange: (value: string) => void; disabled?: boolean};

export function AnswerChoices({options, value, onChange, disabled = false}: Props) {
    const group = useRef<HTMLDivElement>(null);
    const frame = useRef<HTMLSpanElement>(null);
    const selected = options.indexOf(value);
    useLayoutEffect(() => {
        const container = group.current;
        const highlight = frame.current;
        if (!container || !highlight) return;
        function place() {
            const option = container!.querySelector<HTMLElement>('[aria-checked="true"]');
            highlight!.style.opacity = option ? '1' : '0';
            if (option) {
                highlight!.style.top = `${option.offsetTop}px`;
                highlight!.style.height = `${option.offsetHeight}px`;
            }
        }
        place();
        const observer = new ResizeObserver(place);
        observer.observe(container);
        container.querySelectorAll('button').forEach(option => observer.observe(option));
        return () => observer.disconnect();
    }, [value, options]);

    function move(event: KeyboardEvent<HTMLButtonElement>, index: number) {
        const direction = ['ArrowDown', 'ArrowRight'].includes(event.key) ? 1
            : ['ArrowUp', 'ArrowLeft'].includes(event.key) ? -1 : 0;
        if (!direction || disabled) return;
        event.preventDefault();
        const next = (index + direction + options.length) % options.length;
        onChange(options[next]);
        group.current?.querySelectorAll<HTMLButtonElement>('button')[next]?.focus();
    }

    return <div className="u015">
        <div className="u015-grp" role="radiogroup" aria-label="답변 선택" ref={group}>
            <span className="u015-frame" ref={frame} style={{opacity: selected < 0 ? 0 : 1}} aria-hidden="true" />
            {options.map((option, index) => <button key={option} type="button" className="u015-opt"
                role="radio" aria-checked={value === option} disabled={disabled}
                tabIndex={index === (selected < 0 ? 0 : selected) ? 0 : -1}
                onClick={() => onChange(option)} onKeyDown={event => move(event, index)}>
                <span className="u015-dot" aria-hidden="true" />
                <span className="u015-txt"><b>{option}</b></span>
            </button>)}
        </div>
    </div>;
}
