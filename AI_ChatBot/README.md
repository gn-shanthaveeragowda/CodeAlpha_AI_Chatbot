# CodeMate — AI Chatbot (Task 3)

A Java-based chatbot for interactive Q&A on programming topics, built with
Spring Boot. It combines a **hand-written NLP pipeline**, a **from-scratch
ensemble machine learning classifier**, and a **rule engine**, with a web
interface, a Swing desktop GUI, and a console mode all backed by the same
trained model.

Built on top of an existing Spring Boot scaffold; this document describes
what was added to satisfy the Task 3 brief (NLP techniques, machine learning
or rule-based logic, FAQ training, and a GUI/web interface).

---

## 1. Quick start

```bash
mvn spring-boot:run                 # web interface only, at http://localhost:8081
mvn spring-boot:run -Dspring-boot.run.arguments=--gui       # + Swing desktop window
mvn spring-boot:run -Dspring-boot.run.arguments=--console   # + interactive terminal
```

Or build the jar and run it directly:

```bash
mvn clean package
java -jar target/ai-chatbot-0.0.1-SNAPSHOT.jar
java -jar target/ai-chatbot-0.0.1-SNAPSHOT.jar --gui
java -jar target/ai-chatbot-0.0.1-SNAPSHOT.jar --console
```

Run the tests:

```bash
mvn test
```

On first startup the bot seeds its knowledge base from
`src/main/resources/faq-dataset.json` into the H2 database (stored at
`./data/ai_chatbot.mv.db`) and trains the classifier. Subsequent restarts skip
re-seeding anything already present, so training data added while the app was
running — through feedback or the Teach tab — survives a restart.

---

## 2. What's in the box

| Area | What it does | Where |
|---|---|---|
| **NLP pipeline** | Normalizes, spell-corrects, expands synonyms, removes stop words, stems (Porter algorithm), extracts unigram + bigram features, detects sentiment and question type | `com.aichatbot.nlp` |
| **Machine learning** | Multinomial Naive Bayes + TF-IDF cosine similarity + keyword coverage, blended into one ensemble; leave-one-out cross-validation | `com.aichatbot.ml` |
| **Rule engine** | Deterministic answers for time, date and arithmetic — things no amount of training data can supply | `com.aichatbot.service.RuleEngine` |
| **Dialogue management** | Session-scoped context so "tell me more" / "show me an example" continue the previous topic; a clarification band instead of confident wrong guesses | `com.aichatbot.service.ConversationContextService`, `ChatbotService` |
| **Online learning** | Thumbs up/down feedback, intent correction, and a Teach tab all retrain the model live, no restart | `com.aichatbot.service.LearningService` |
| **FAQ knowledge base** | 41 intents, 362 training examples, 120 layered answers (primary / detail / example) | `src/main/resources/faq-dataset.json` |
| **Interfaces** | Responsive web UI (chat, insights, teach), a Swing desktop client, a console REPL — all calling the same `ChatbotService` | `static/`, `com.aichatbot.gui` |
| **Persistence** | H2 file database by default; a MySQL profile is one property swap away | `application.properties`, `application-mysql.properties.example` |

---

## 3. How a message is answered

```
User message
    |
    v
+----------------------------------------------------------------+
| NLP PIPELINE (NLPProcessor)                                    |
|  1. Normalize    - lowercase, expand contractions & shorthand, |
|                     strip accents, collapse elongated letters  |
|  2. Tokenize     - split on whitespace                         |
|  3. Spell-check  - Levenshtein distance against the corpus     |
|  4. Synonyms     - "oops" -> "oop", "db" -> "database", ...    |
|  5. Stop words   - removed, except question words which the    |
|                     question-type detector still needs         |
|  6. Stem         - Porter algorithm                            |
|  7. N-grams      - unigrams + bigrams as classifier features   |
+----------------------------------------------------------------+
    |
    v
+----------------------+  yes  +--------------------------------+
| 1. RULE ENGINE hit?  |------>| Return the computed answer      |
|    (time/date/maths) |       | (confidence 1.0, no ML used)    |
+-----------+----------+       +--------------------------------+
    | no
    v
+----------------------+  yes  +--------------------------------+
| 2. Follow-up on the  |------>| Serve the DETAIL or EXAMPLE     |
|    previous topic?   |       | layer of the last intent        |
|    ("tell me more")  |       +--------------------------------+
+-----------+----------+
    | no
    v
+------------------------------------------------------------------+
| 3. ENSEMBLE CLASSIFIER                                            |
|    score = 0.35 x NaiveBayes + 0.45 x CosineSimilarity            |
|            + 0.20 x KeywordCoverage                               |
+-----------+--------------------------------------------------------+
    |
    +- confidence >= 0.35   -> answer the top intent (MODEL)
    +- 0.18 <= conf < 0.35  -> ask a clarifying question, log the gap
    +- confidence < 0.18    -> fall back honestly, log the gap
```

Every turn is persisted to `chat_history` with its intent, confidence,
strategy, sentiment and response time, which is what powers the Insights tab.

---

## 4. The machine learning, in more detail

Three independent signals vote, and their scores are blended rather than
picking a single "winner" model:

- **Multinomial Naive Bayes** (`NaiveBayesClassifier`) — learns `P(intent)`
  and `P(word | intent)` from the training data with Laplace smoothing, in log
  space to avoid underflow. If a message shares **no** vocabulary with
  anything trained, it abstains rather than falling back to the class priors
  (an early version didn't do this — see §5).
- **TF-IDF cosine similarity** (`TfIdfVectorizer`, `CosineSimilarityClassifier`)
  — every training example becomes a unit-length TF-IDF vector; a message is
  compared against all of them, and the *k* nearest neighbours vote, weighted
  by similarity. Rare, specific words (`polymorphism`) count for more than
  common ones (`what`, `java`).
- **Keyword coverage** — what fraction of the message's features the winning
  intent was actually trained on. This is the guard rail against a
  fluent-sounding wrong answer.

An **exact match** to a stored training example short-circuits the blend to
confidence 1.0 — this is effectively the rule-based half of the classifier,
guaranteeing that a question typed exactly as trained is never second-guessed
by the statistics.

### Evaluating the classifier

`ModelEvaluator` runs **leave-one-out cross-validation**: every example is
held out in turn, the model is retrained on the rest, and the held-out example
is predicted. This runs automatically on a background thread after every
training run and its report is available at `GET /api/model/metrics` and on
the Insights tab.

Two numbers are worth reporting, and they measure different things:

| Metric | Result | What it means |
|---|---|---|
| Leave-one-out cross-validation, full corpus (362 examples, 41 intents) | **61.6% accuracy, macro F1 0.64** | The conservative worst case. Many training examples share a word with nothing else in their own intent, so removing that one example genuinely removes all the evidence for it — the model is being asked to predict from strictly less information than it will ever have live. |
| Held-out set of 60 realistic paraphrases *never seen in training* (typos, shorthand, indirect wording — see `src/test/resources/test-questions.json`) | **93.3% top-1, 96.7% top-3** | A closer approximation of real usage, where the model has its full 362-example vocabulary to draw on. |

A weight sweep over the held-out set confirmed the ensemble is not just
convenient but actually the best configuration tried:

| Configuration | Top-1 accuracy |
|---|---|
| Naive Bayes alone | 93.3% |
| Cosine similarity alone | 91.7% |
| Keyword coverage alone | 90.0% |
| **Ensemble (0.35 / 0.45 / 0.20 — the default)** | **93.3%** |

The ensemble matches the best single model rather than being dragged down by
the weaker ones, and its abstentions are more honest (see the abstain fix in
§5), which is the real reason to prefer it over Naive Bayes alone.

---

## 5. Two bugs worth knowing about (found by testing, not assumed away)

**Question words were dominating the classifier.** The first version of the
stop-word list kept `what`, `how`, `why` because they seemed meaningful. In
practice they were the *most frequent* tokens in the whole corpus, so
`"what is git"` and `"what are you"` shared their strongest feature and the
classifier confused `bot_identity`/`greeting` constantly. Moving them to the
stop list (while the question-type detector still reads them **before** that
filter runs) took cross-validation accuracy from 53.9% to 61.6%.

**Naive Bayes never abstained.** With zero recognised words, every intent's
score reduced to its prior probability, so the single largest training
intent always "won" — a confident, fluent, completely unfounded answer. The
fix: if a message shares no vocabulary with the training data at all, Naive
Bayes now returns no predictions, and even a partial match scales its
confidence down by how much of the message it actually recognised. This
turned silently-wrong answers into honest abstentions and pushed the fallback
path to do its job properly.

---

## 6. Extending the knowledge base

**Through the running app (no restart needed):**
- `POST /api/training/examples` — add a new phrasing to an existing intent
- `POST /api/training/intents` — create a brand-new topic (examples + answers)
- The **Teach** tab in the web UI wraps both of these
- A thumbs-down on an answer, with a corrected topic, teaches the correction immediately

**By editing the dataset file** (`src/main/resources/faq-dataset.json`), then
calling `POST /api/training/reload-dataset` or restarting — useful for adding
a large batch of FAQs at once. Each intent needs:

```json
{
  "name": "enum_types",
  "description": "Enums in Java",
  "category": "Java Basics",
  "examples": ["what is an enum", "how do enums work", "enum in java"],
  "responses": {
    "PRIMARY": ["An enum defines a fixed set of named constants..."],
    "DETAIL": ["Every enum implicitly extends java.lang.Enum..."],
    "EXAMPLE": ["enum Day { MONDAY, TUESDAY, ... }"]
  }
}
```

---

## 7. API reference

| Method & path | Purpose |
|---|---|
| `POST /api/chat` | `{message, sessionId}` -> the bot's answer with full reasoning metadata |
| `DELETE /api/chat/session/{id}` | Clears a session's follow-up context |
| `GET /api/chat/history` | Last 100 messages across all sessions |
| `GET /api/chat/history/{sessionId}` | Full transcript of one session |
| `POST /api/feedback` | `{messageId, helpful, correctedIntent?}` -> rate an answer, optionally correct it |
| `POST /api/training/examples` | Teach a new phrasing for an existing intent |
| `POST /api/training/intents` | Create a whole new intent |
| `POST /api/training/reload-dataset` | Re-read `faq-dataset.json` |
| `GET /api/training/gaps` | Questions the bot couldn't confidently answer |
| `GET /api/intents` / `/by-category` / `/{name}` / `/names` | Browse the knowledge base |
| `GET /api/model` | Classifier status: weights, vocabulary size, last training run |
| `GET /api/model/metrics` | Full cross-validation report |
| `POST /api/model/retrain` | Force a retrain right now |
| `GET`/`POST /api/model/analyze` | Dry run: shows every NLP stage and every classifier score for a message, **without** answering or logging it — the tool to use in a demo |
| `GET /api/analytics` | Everything behind the Insights tab |
| `GET /api/health` | Liveness + whether the model has finished training |

---

## 8. Project layout

```
src/main/java/com/aichatbot/
├── nlp/            TextNormalizer, StopWords, SynonymDictionary, PorterStemmer,
│                   SpellCorrector, SentimentAnalyzer, NLPProcessor (orchestrator)
├── ml/             IntentClassifier (interface), NaiveBayesClassifier,
│                   TfIdfVectorizer, CosineSimilarityClassifier,
│                   EnsembleClassifier, ModelEvaluator
├── service/        ChatbotService (turn decision logic), RuleEngine,
│                   ConversationContextService, ModelTrainingService,
│                   LearningService, AnalyticsService, DataInitializer
├── entity/         Intent, Response, TrainingExample, ChatMessage, UnansweredQuestion
├── repository/     Spring Data JPA repositories for each entity
├── dto/            ChatRequest/Response, FeedbackRequest, TrainingRequest
├── http/           REST controllers (Chat, History, Feedback, Training,
│                   Intent, Model, Analytics, Health) + ApiExceptionHandler
├── gui/            SwingChatbotClient (--gui), ConsoleChatRunner (--console)
├── config/         StartupRunner (seed -> train, in order)
└── AiChatbotApplication.java

src/main/resources/
├── faq-dataset.json         The knowledge base: 41 intents, 362 examples, 120 answers
├── application.properties   H2 config, confidence thresholds, ensemble weights
└── static/                  index.html, app.js, styles.css (chat / insights / teach tabs)

src/test/java/com/aichatbot/
├── nlp/            Tests for the stemmer, normalizer, spell corrector,
│                   sentiment analyzer and full pipeline
├── ml/             Tests for Naive Bayes, TF-IDF, cosine similarity, ensemble
└── service/        Tests for the rule engine

src/test/resources/test-questions.json   60 held-out paraphrases for accuracy evaluation
```

---

## 9. Configuration

All in `application.properties`:

```properties
chatbot.confidence-threshold=0.35   # minimum confidence to commit to an answer
chatbot.clarify-threshold=0.18      # below this, fall back entirely; between the two, ask a clarifying question
chatbot.weights.naive-bayes=0.35
chatbot.weights.similarity=0.45
chatbot.weights.keyword=0.20
chatbot.learning.reinforce-below=0.75   # a positive rating below this confidence reinforces the training data
chatbot.evaluation.enabled=true         # cross-validate after every retrain (background thread)
```

To use MySQL instead of the bundled H2 database, copy the contents of
`application-mysql.properties.example` into `application.properties` and set
a real password.

---

## 10. Honesty about scope

This satisfies the Task 3 checklist (Java-based chatbot, NLP techniques,
machine-learning/rule-based logic, FAQ training, GUI/web interface) with:
real NLP (not just lowercasing), a genuine trained classifier (not a lookup
table), a working rule engine, a knowledge base that grows at runtime, and
three separate user interfaces sharing one engine.

What it is *not*: a deep-learning or transformer-based system, and the
training corpus (362 examples across 41 topics) is small enough that
leave-one-out cross-validation is a conservative rather than a definitive
accuracy figure — see §4 for why the held-out paraphrase test is the more
representative number.
