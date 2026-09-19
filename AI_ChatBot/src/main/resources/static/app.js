/*
 * CodeMate web client.
 *
 * Talks to the Spring Boot API and renders three views: the conversation, the
 * analytics dashboard and the teaching forms. The "show reasoning" toggle
 * exposes the intent, confidence, engine and extracted NLP features for every
 * answer, so the classifier's decision can be inspected rather than trusted.
 */

const api = {
    chat: '/api/chat',
    feedback: '/api/feedback',
    analytics: '/api/analytics',
    model: '/api/model',
    intentNames: '/api/intents/names',
    teachExample: '/api/training/examples',
    teachIntent: '/api/training/intents'
};

// One session id per browser tab keeps follow-up questions ("tell me more")
// tied to the right conversation on the server.
const sessionId = 'web-' + Math.random().toString(36).slice(2, 11);

const form = document.querySelector('#chatForm');
const input = document.querySelector('#messageInput');
const messages = document.querySelector('#messages');
const sendButton = document.querySelector('#sendButton');
const clearButton = document.querySelector('#clearButton');
const debugToggle = document.querySelector('#debugToggle');
const teachResult = document.querySelector('#teachResult');

let showReasoning = false;
let intentNames = [];

/* ------------------------------------------------------------- utilities */

function timeNow() {
    return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
}

async function postJson(url, body) {
    const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.error || 'The server could not process that request.');
    return data;
}

async function getJson(url) {
    const response = await fetch(url);
    if (!response.ok) throw new Error('Request failed: ' + url);
    return response.json();
}

/* ------------------------------------------------------------------ chat */

function addUserMessage(text) {
    const article = element('article', 'message user-message');
    article.appendChild(element('div', 'avatar', 'YOU'));
    const wrap = element('div', 'bubble-wrap');
    wrap.appendChild(element('div', 'bubble', text));
    wrap.appendChild(element('time', null, timeNow()));
    article.appendChild(wrap);
    messages.appendChild(article);
    scrollToEnd();
}

function addBotMessage(data) {
    const article = element('article', 'message bot-message');
    article.appendChild(element('div', 'avatar', 'CM'));

    const wrap = element('div', 'bubble-wrap');
    const bubble = element('div', 'bubble', data.response);
    // Stored answers contain code samples with real line breaks, so the bubble
    // preserves whitespace rather than collapsing it.
    if (data.response && data.response.includes('\n')) bubble.classList.add('has-code');
    wrap.appendChild(bubble);

    wrap.appendChild(buildMeta(data));
    if (showReasoning) wrap.appendChild(buildReasoning(data));
    if (data.suggestions && data.suggestions.length) wrap.appendChild(buildSuggestions(data));
    wrap.appendChild(buildFeedback(data));
    wrap.appendChild(element('time', null, timeNow()));

    article.appendChild(wrap);
    messages.appendChild(article);
    scrollToEnd();
}

/** Intent chip plus a confidence bar, always visible under an answer. */
function buildMeta(data) {
    const meta = element('div', 'answer-meta');

    const chip = element('span', 'intent-chip' + (data.fallback ? ' is-fallback' : ''),
        data.fallback ? 'no confident match' : data.intent.replace(/_/g, ' '));
    meta.appendChild(chip);

    const percent = Math.round((data.confidence || 0) * 100);
    const gauge = element('span', 'confidence-gauge');
    gauge.title = 'Classifier confidence';
    const fill = element('span', 'confidence-fill');
    fill.style.width = percent + '%';
    if (percent < 35) fill.classList.add('is-low');
    else if (percent < 65) fill.classList.add('is-medium');
    gauge.appendChild(fill);
    meta.appendChild(gauge);

    meta.appendChild(element('span', 'meta-value', percent + '%'));
    meta.appendChild(element('span', 'meta-engine', data.strategy));
    return meta;
}

/** The full NLP and classifier trace, shown when reasoning is toggled on. */
function buildReasoning(data) {
    const panel = element('div', 'reasoning');

    const row = (label, value) => {
        const line = element('div', 'reasoning-row');
        line.appendChild(element('span', 'reasoning-label', label));
        line.appendChild(element('span', 'reasoning-value', value));
        panel.appendChild(line);
    };

    row('features', (data.tokens || []).join('  '));
    row('question type', data.questionType);
    row('sentiment', data.sentiment + ' (' + data.sentimentScore + ')');
    if (data.matchedExample) row('closest example', '"' + data.matchedExample + '"');
    if (data.alternatives && data.alternatives.length) {
        row('runners up', data.alternatives
            .map(a => a.intent.replace(/_/g, ' ') + ' ' + Math.round(a.confidence * 100) + '%')
            .join(',  '));
    }
    row('time', data.responseTimeMs + ' ms');
    return panel;
}

function buildSuggestions(data) {
    const strip = element('div', 'suggestion-strip');
    data.suggestions.forEach(text => {
        const chip = element('button', 'suggestion', text);
        chip.type = 'button';
        chip.addEventListener('click', () => sendMessage(text));
        strip.appendChild(chip);
    });
    return strip;
}

/** Thumbs up and down. A thumbs down opens the intent correction dropdown. */
function buildFeedback(data) {
    const bar = element('div', 'feedback-bar');
    if (!data.messageId) return bar;

    const status = element('span', 'feedback-status');

    const up = element('button', 'feedback-button', '\u{1F44D}');
    up.type = 'button';
    up.title = 'This answered my question';

    const down = element('button', 'feedback-button', '\u{1F44E}');
    down.type = 'button';
    down.title = 'This was not what I meant';

    up.addEventListener('click', async () => {
        bar.querySelectorAll('button').forEach(b => b.disabled = true);
        try {
            const result = await postJson(api.feedback, { messageId: data.messageId, helpful: true });
            status.textContent = result.detail;
            if (result.retrained) refreshModelStatus();
        } catch (error) {
            status.textContent = error.message;
        }
    });

    down.addEventListener('click', () => {
        bar.querySelectorAll('button').forEach(b => b.disabled = true);
        bar.appendChild(buildCorrection(data.messageId, status));
        status.textContent = 'Which topic did you actually mean?';
    });

    bar.appendChild(element('span', 'feedback-label', 'Did this help?'));
    bar.appendChild(up);
    bar.appendChild(down);
    bar.appendChild(status);
    return bar;
}

/** Correction dropdown: picking the right intent teaches the model live. */
function buildCorrection(messageId, status) {
    const wrapper = element('span', 'correction');
    const select = document.createElement('select');
    select.appendChild(new Option('choose a topic...', ''));
    intentNames.forEach(name => select.appendChild(new Option(name.replace(/_/g, ' '), name)));

    const apply = element('button', 'suggestion', 'Teach me');
    apply.type = 'button';
    apply.addEventListener('click', async () => {
        apply.disabled = true;
        try {
            const result = await postJson(api.feedback, {
                messageId: messageId,
                helpful: false,
                correctedIntent: select.value || null
            });
            status.textContent = result.detail;
            wrapper.remove();
            if (result.retrained) refreshModelStatus();
        } catch (error) {
            status.textContent = error.message;
            apply.disabled = false;
        }
    });

    wrapper.appendChild(select);
    wrapper.appendChild(apply);
    return wrapper;
}

function addTyping() {
    const typing = element('article', 'message bot-message');
    typing.id = 'typing';
    typing.appendChild(element('div', 'avatar', 'CM'));
    const wrap = element('div', 'bubble-wrap');
    wrap.appendChild(element('div', 'bubble typing', 'Thinking'));
    typing.appendChild(wrap);
    messages.appendChild(typing);
    scrollToEnd();
}

function scrollToEnd() {
    messages.scrollTop = messages.scrollHeight;
}

async function sendMessage(message) {
    addUserMessage(message);
    addTyping();
    sendButton.disabled = true;
    input.disabled = true;

    try {
        const data = await postJson(api.chat, { message: message, sessionId: sessionId });
        document.querySelector('#typing')?.remove();
        addBotMessage(data);
    } catch (error) {
        document.querySelector('#typing')?.remove();
        const offline = error.message.includes('Failed to fetch') || error.message.includes('NetworkError');
        addBotMessage({
            response: offline ? 'I cannot reach the chatbot server. Is it still running?' : error.message,
            intent: 'error', confidence: 0, fallback: true, strategy: 'ERROR',
            sentiment: 'NEUTRAL', sentimentScore: 0, questionType: 'STATEMENT',
            tokens: [], alternatives: [], suggestions: [], responseTimeMs: 0
        });
    } finally {
        sendButton.disabled = false;
        input.disabled = false;
        input.focus();
    }
}

/* -------------------------------------------------------------- insights */

async function refreshModelStatus() {
    try {
        const status = await getJson(api.model);
        const accuracy = status.evaluation ? status.evaluation.accuracy : null;
        const label = document.querySelector('#modelAccuracy');
        const bar = document.querySelector('#progressBar');

        if (accuracy === null || accuracy === undefined) {
            label.textContent = status.evaluating ? 'evaluating...' : 'not evaluated';
            bar.style.width = '0%';
        } else {
            label.textContent = Math.round(accuracy * 100) + '% cross-validated';
            bar.style.width = Math.round(accuracy * 100) + '%';
        }
        document.querySelector('#modelSummary').textContent =
            status.trainingExamples + ' examples, ' + status.intents + ' topics';
        document.querySelector('#railStatus').textContent =
            status.trainingExamples > 0 ? 'Model trained' : 'Model empty';
    } catch (error) {
        document.querySelector('#modelSummary').textContent = 'Model status unavailable';
    }
}

function statCard(label, value, hint) {
    const card = element('div', 'stat-card');
    card.appendChild(element('span', 'stat-label', label));
    card.appendChild(element('strong', 'stat-value', value));
    if (hint) card.appendChild(element('small', 'stat-hint', hint));
    return card;
}

function renderBars(container, counts) {
    container.textContent = '';
    const entries = Object.entries(counts || {});
    if (!entries.length) {
        container.appendChild(element('p', 'section-note', 'Nothing recorded yet. Ask a few questions first.'));
        return;
    }
    const max = Math.max(...entries.map(entry => entry[1]));
    entries.forEach(([name, count]) => {
        const row = element('div', 'bar-row');
        row.appendChild(element('span', 'bar-name', name.replace(/_/g, ' ')));
        const track = element('span', 'bar-track');
        const fill = element('span', 'bar-fill');
        fill.style.width = Math.max(4, (count / max) * 100) + '%';
        track.appendChild(fill);
        row.appendChild(track);
        row.appendChild(element('span', 'bar-count', count));
        container.appendChild(row);
    });
}

async function refreshInsights() {
    try {
        const [stats, model] = await Promise.all([getJson(api.analytics), getJson(api.model)]);

        const grid = document.querySelector('#statGrid');
        grid.textContent = '';
        grid.appendChild(statCard('Messages', stats.totalMessages, 'total handled'));
        grid.appendChild(statCard('Answered', Math.round(stats.answerRate * 100) + '%',
            stats.fallbackMessages + ' fell back'));
        grid.appendChild(statCard('Avg confidence', Math.round(stats.averageConfidence * 100) + '%',
            'when answering'));
        grid.appendChild(statCard('Response time', Math.round(stats.averageResponseTimeMs) + ' ms', 'average'));
        grid.appendChild(statCard('Satisfaction', stats.positiveFeedback + stats.negativeFeedback > 0
            ? Math.round(stats.satisfactionRate * 100) + '%' : '-',
            stats.positiveFeedback + ' up, ' + stats.negativeFeedback + ' down'));
        grid.appendChild(statCard('Topics known', stats.knownIntents, stats.storedAnswers + ' answers'));
        grid.appendChild(statCard('Training data', stats.trainingExamples,
            stats.learnedExamples + ' learned from chats'));
        grid.appendChild(statCard('Training gaps', stats.openTrainingGaps, 'awaiting an answer'));

        renderModelPanel(model);
        renderBars(document.querySelector('#topIntents'), stats.topIntents);
        renderBars(document.querySelector('#strategyList'), stats.strategyBreakdown);
        renderGaps(stats.trainingQueue);
        refreshModelStatus();
    } catch (error) {
        document.querySelector('#modelPanel').textContent = 'Could not load analytics: ' + error.message;
    }
}

function renderModelPanel(model) {
    const panel = document.querySelector('#modelPanel');
    panel.textContent = '';

    const line = (label, value) => {
        const row = element('div', 'info-row');
        row.appendChild(element('span', 'info-label', label));
        row.appendChild(element('span', 'info-value', value));
        panel.appendChild(row);
    };

    line('Algorithm', model.algorithm);
    line('Weights', Object.entries(model.weights || {})
        .map(([k, v]) => k + ' ' + v).join(',  '));
    line('Training examples', model.trainingExamples + ' across ' + model.intents + ' topics');
    line('Feature vocabulary', model.featureVocabulary + ' stems and bigrams');
    line('Spell dictionary', model.spellVocabulary + ' words');
    line('Training time', model.trainingTimeMs + ' ms, run ' + model.trainingRuns + ' times');

    const evaluation = model.evaluation;
    if (!evaluation) {
        line('Evaluation', model.evaluating ? 'running in the background...' : 'not available yet');
        return;
    }
    line('Validation', evaluation.method + ', ' + evaluation.sampleCount + ' samples');
    line('Accuracy', Math.round(evaluation.accuracy * 1000) / 10 + '%');
    line('Macro F1', evaluation.macroF1);
    line('Macro precision / recall', evaluation.macroPrecision + ' / ' + evaluation.macroRecall);

    if (evaluation.topConfusions && evaluation.topConfusions.length) {
        line('Most confused', evaluation.topConfusions.slice(0, 3)
            .map(c => c.expected.replace(/_/g, ' ') + ' as ' + c.predicted.replace(/_/g, ' '))
            .join(',  '));
    }
}

function renderGaps(queue) {
    const list = document.querySelector('#gapList');
    list.textContent = '';
    if (!queue || !queue.length) {
        list.appendChild(element('p', 'section-note', 'No gaps recorded. Everything asked so far was answered.'));
        return;
    }
    queue.forEach(gap => {
        const row = element('div', 'gap-row');
        row.appendChild(element('span', 'gap-question', gap.question));
        row.appendChild(element('span', 'gap-count', 'asked ' + gap.timesAsked + 'x'));
        const teach = element('button', 'suggestion', 'Teach this');
        teach.type = 'button';
        teach.addEventListener('click', () => {
            switchTab('teach');
            document.querySelector('#teachExample').value = gap.question;
            if (gap.closestIntent) document.querySelector('#teachIntent').value = gap.closestIntent;
        });
        row.appendChild(teach);
        list.appendChild(row);
    });
}

/* ----------------------------------------------------------------- teach */

async function loadIntentNames() {
    try {
        intentNames = await getJson(api.intentNames);
        const select = document.querySelector('#teachIntent');
        select.textContent = '';
        intentNames.forEach(name => select.appendChild(new Option(name.replace(/_/g, ' '), name)));
    } catch (error) {
        intentNames = [];
    }
}

function showTeachResult(message, isError) {
    teachResult.textContent = message;
    teachResult.className = 'teach-result is-visible' + (isError ? ' is-error' : '');
}

async function teachExample() {
    const intent = document.querySelector('#teachIntent').value;
    const example = document.querySelector('#teachExample').value.trim();
    if (!example) return showTeachResult('Type the question wording first.', true);

    try {
        const result = await postJson(api.teachExample, { intent: intent, example: example });
        showTeachResult(result.detail, !result.accepted);
        if (result.learned) {
            document.querySelector('#teachExample').value = '';
            refreshModelStatus();
        }
    } catch (error) {
        showTeachResult(error.message, true);
    }
}

async function createIntent() {
    const name = document.querySelector('#newIntentName').value.trim();
    const examples = document.querySelector('#newIntentExamples').value
        .split('\n').map(line => line.trim()).filter(Boolean);
    const answer = document.querySelector('#newIntentAnswer').value.trim();
    const detail = document.querySelector('#newIntentDetail').value.trim();

    if (!name) return showTeachResult('Give the topic a name.', true);
    if (!answer) return showTeachResult('The topic needs an answer.', true);

    try {
        const result = await postJson(api.teachIntent, {
            name: name, description: name.replace(/_/g, ' '), category: 'Custom',
            examples: examples, primaryAnswer: answer,
            detailAnswer: detail || null, exampleAnswer: null
        });
        showTeachResult(result.detail, !result.accepted);
        if (result.learned) {
            ['#newIntentName', '#newIntentExamples', '#newIntentAnswer', '#newIntentDetail']
                .forEach(id => document.querySelector(id).value = '');
            loadIntentNames();
            refreshModelStatus();
        }
    } catch (error) {
        showTeachResult(error.message, true);
    }
}

/* ------------------------------------------------------------------ tabs */

function switchTab(name) {
    document.querySelectorAll('.rail-tab').forEach(tab =>
        tab.classList.toggle('is-active', tab.dataset.tab === name));
    document.querySelectorAll('.tab-panel').forEach(panel =>
        panel.classList.toggle('is-active', panel.dataset.panel === name));
    if (name === 'insights') refreshInsights();
    if (name === 'teach') loadIntentNames();
    if (name === 'chat') input.focus();
}

/* ------------------------------------------------------------- listeners */

form.addEventListener('submit', event => {
    event.preventDefault();
    const message = input.value.trim();
    if (!message) return;
    input.value = '';
    sendMessage(message);
});

document.querySelectorAll('[data-question]').forEach(button =>
    button.addEventListener('click', () => {
        switchTab('chat');
        sendMessage(button.dataset.question);
    }));

document.querySelectorAll('.rail-tab').forEach(tab =>
    tab.addEventListener('click', () => switchTab(tab.dataset.tab)));

debugToggle.addEventListener('click', () => {
    showReasoning = !showReasoning;
    debugToggle.setAttribute('aria-pressed', String(showReasoning));
    debugToggle.classList.toggle('is-on', showReasoning);
    document.querySelectorAll('.reasoning').forEach(node => node.remove());
});

clearButton.addEventListener('click', async () => {
    try {
        await fetch('/api/chat/session/' + sessionId, { method: 'DELETE' });
    } catch (error) {
        // A failed reset only affects follow-up context, so the chat still clears.
    }
    messages.textContent = '';
    const note = element('div', 'welcome-note');
    note.appendChild(element('span', null, 'NEW SESSION'));
    note.appendChild(element('p', null, 'Fresh page, fresh questions. What are you curious about?'));
    messages.appendChild(note);
    input.focus();
});

document.querySelector('#refreshInsights').addEventListener('click', refreshInsights);
document.querySelector('#teachExampleButton').addEventListener('click', teachExample);
document.querySelector('#createIntentButton').addEventListener('click', createIntent);

/* ----------------------------------------------------------------- start */

addBotMessage({
    messageId: null,
    response: 'Hello! I am CodeMate. Ask me about Java, OOP, collections, exceptions, JDBC, SQL, '
        + 'Spring Boot or testing. Say "tell me more" to go deeper on any answer.',
    intent: 'greeting', intentDescription: 'A friendly greeting', confidence: 1,
    fallback: false, strategy: 'MODEL', sentiment: 'POSITIVE', sentimentScore: 0,
    questionType: 'STATEMENT', tokens: [], alternatives: [],
    suggestions: ['What is OOP?', 'How do you work?'], responseTimeMs: 0
});

loadIntentNames();
refreshModelStatus();
// The model evaluates on a background thread, so poll briefly until it lands.
setTimeout(refreshModelStatus, 4000);
input.focus();
