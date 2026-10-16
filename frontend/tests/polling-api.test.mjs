import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';

function load(path, globals = {}, modules = {}) {
    const code = ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), {
        compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022},
    }).outputText;
    const exports = {};
    vm.runInNewContext(code, {
        exports, require: name => modules[name], process: {env: {}},
        Headers, AbortController, setTimeout, clearTimeout, ...globals,
    });
    return exports;
}
const flush = () => new Promise(resolve => setImmediate(resolve));

function pollHarness(taskFactory) {
    const timers = new Map();
    let sequence = 0;
    let cleanup;
    let visible;
    const errors = [];
    const document = {hidden: false,
        addEventListener: (_, cb) => { visible = cb; }, removeEventListener() {},
    };
    const api = load('../src/lib/api.ts');
    const hook = load('../src/hooks/usePolling.ts', {
        document,
        setTimeout: (cb, delay) => { timers.set(++sequence, {cb, delay}); return sequence; },
        clearTimeout: id => timers.delete(id),
    }, {react: {useEffect: effect => { cleanup = effect(); }}, '@/lib/api': api});
    hook.usePolling(taskFactory(api), true, 1800, e => errors.push(e));
    return {timers, errors, document, stop: () => cleanup(), visible: () => visible(),
        next: async () => {
            const [id, timer] = timers.entries().next().value;
            timers.delete(id); await timer.cb(); await flush();
        }};
}

test('API retains status/detail and classifies terminal failures', async () => {
    const {api, ApiError} = load('../src/lib/api.ts', {
        fetch: async () => new Response(JSON.stringify({detail: '만료된 방입니다.'}), {status: 410}),
    });
    await assert.rejects(api('/room'), e => e instanceof ApiError && e.status === 410 && !e.retryable && e.message === '만료된 방입니다.');
});
test('API handles proxy HTML and network failures without exposing response body', async () => {
    const html = load('../src/lib/api.ts', {fetch: async () => new Response('private upstream details', {status: 503})});
    await assert.rejects(html.api('/room'), e => e.status === 503 && e.retryable && !e.message.includes('private'));
    const network = load('../src/lib/api.ts', {fetch: async () => { throw new TypeError('failed'); }});
    await assert.rejects(network.api('/room'), e => e.status === 0 && e.retryable);
});
test('polling completes without scheduling another request', async () => {
    const h = pollHarness(() => async () => false);
    await flush(); assert.equal(h.timers.size, 0); h.stop();
});
test('polling never overlaps slow requests and aborts on cleanup', async () => {
    let signal;
    let finish;
    let calls = 0;
    const h = pollHarness(() => s => { calls++; signal = s; return new Promise(r => { finish = r; }); });
    h.visible(); h.visible();
    assert.equal(calls, 1); assert.equal(h.timers.size, 0);
    h.stop(); assert.equal(signal.aborted, true);
    finish(true); await flush(); assert.equal(h.timers.size, 0);
});
test('terminal authentication errors stop; transient failures back off then recover', async () => {
    const stopped = pollHarness(({ApiError}) => async () => { throw new ApiError('expired', 401); });
    await flush(); assert.equal(stopped.errors.length, 1); assert.equal(stopped.timers.size, 0); stopped.stop();
    let calls = 0;
    const h = pollHarness(({ApiError}) => async () => { if (++calls === 1) throw new ApiError('down', 503); return true; });
    await flush(); assert.equal([...h.timers.values()][0].delay, 3600);
    await h.next(); assert.equal([...h.timers.values()][0].delay, 1800); h.stop();
});
test('hidden tabs suspend requests and visible tabs resume once', async () => {
    let calls = 0;
    const h = pollHarness(() => async () => { calls++; return true; });
    await flush(); h.document.hidden = true; h.visible();
    assert.equal(h.timers.size, 0); assert.equal(calls, 1);
    h.document.hidden = false; h.visible(); await flush();
    assert.equal(calls, 2); h.stop();
});
