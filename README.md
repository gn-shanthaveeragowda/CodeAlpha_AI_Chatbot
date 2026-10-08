# CodeMate — AI Programming Chatbot

CodeMate is a Java-based chatbot built to help users learn and explore programming concepts through an interactive conversation.

Instead of relying only on predefined answers, CodeMate combines a custom **NLP pipeline**, **machine learning**, **rule-based logic**, and **conversation context** to understand questions and decide how they should be answered.

The project also includes a web interface, a Java Swing desktop application, and a console interface, all using the same chatbot engine.

---

## What CodeMate Can Do

### Understand User Questions

CodeMate processes user messages using a custom NLP pipeline. The processing includes:

- Text normalization
- Tokenization
- Spell correction
- Synonym expansion
- Stop-word removal
- Porter stemming
- Unigram and bigram features
- Sentiment detection
- Question-type detection

This allows the chatbot to work with different ways of asking the same type of question instead of depending only on exact phrases.

### Use Machine Learning

The chatbot uses three classification approaches:

- **Multinomial Naive Bayes**
- **TF-IDF cosine similarity**
- **Keyword coverage**

These are combined into an ensemble model to determine the most likely intent for a user's question.

The default weights are:

```text
Naive Bayes          0.35
Cosine Similarity    0.45
Keyword Coverage     0.20
```

### Handle Simple Tasks Directly

Not every question needs machine learning.

CodeMate has a rule engine for requests such as:

- Current time
- Current date
- Arithmetic calculations

These are handled directly by the rule engine.

### Remember the Conversation

CodeMate keeps track of the current conversation session.

For example, if a user first asks about a programming concept and then says:

```text
Tell me more
```

or:

```text
Show me an example
```

the chatbot can continue from the previous topic instead of treating the message as a completely new question.

If the model is not confident enough, it can also ask the user for clarification instead of giving a random answer.

### Learn From Feedback

The chatbot can be improved while it is running.

Users can:

- Give thumbs-up or thumbs-down feedback
- Correct an intent
- Add new training examples
- Create new intents
- Use the Teach interface to add knowledge

Training changes can be applied without restarting the application.

---

## Interfaces

CodeMate currently provides three ways to interact with the chatbot:

| Interface | Description |
|---|---|
| **Web** | Browser-based responsive chatbot interface |
| **Desktop** | Java Swing application |
| **Console** | Interactive terminal interface |

All three interfaces use the same underlying chatbot engine and trained model.

---

## Technology Stack

| Technology | Used For |
|---|---|
| Java | Main programming language |
| Spring Boot | Backend and application framework |
| Maven | Build and dependency management |
| HTML / CSS / JavaScript | Web interface |
| Java Swing | Desktop interface |
| H2 | Default database |
| MySQL | Optional database |
| REST API | Communication between the interface and chatbot services |
| Git / GitHub | Version control |

---

## How It Works

A user message goes through several stages before CodeMate generates a response.

```text
User Question
     |
     v
NLP Processing
     |
     v
Rule Engine
     |
     +------ Rule Match ------> Direct Answer
     |
     v
Conversation Context
     |
     +------ Follow-up -------> Contextual Answer
     |
     v
Ensemble Classifier
     |
     +-----------------------------+
     |             |               |
     v             v               v
Naive Bayes   TF-IDF Similarity   Keywords
     |             |               |
     +-------------+---------------+
                   |
                   v
             Confidence
                   |
          +--------+--------+
          |        |        |
        High    Medium      Low
          |        |        |
          v        v        v
       Answer   Clarify   Fallback
```

Each conversation turn is also stored with information such as:

- Intent
- Confidence
- Strategy
- Sentiment
- Response time

This information is later used by the application's analytics and Insights functionality.

---

## Machine Learning

### Multinomial Naive Bayes

The Naive Bayes classifier learns the probability of an intent and the probability of words occurring within that intent.

It uses Laplace smoothing and logarithmic probabilities.

The implementation also avoids blindly selecting an intent when the message contains no vocabulary known to the training data.

---

### TF-IDF and Cosine Similarity

Training questions are converted into TF-IDF vectors.

When a new question arrives, CodeMate compares it with the stored examples using cosine similarity.

This helps specific programming terms carry more weight when identifying an intent.

For example:

```text
polymorphism
inheritance
JDBC
```

can be more useful for classification than very common words such as:

```text
what
how
java
```

---

### Keyword Coverage

Keyword coverage checks how much of the user's message matches the features that the predicted intent was trained on.

This acts as another check before the chatbot commits to an answer.

---

## Confidence Handling

CodeMate uses confidence thresholds to decide how it should respond.

```text
Confidence >= 0.35
        |
        +--> Answer

0.18 <= Confidence < 0.35
        |
        +--> Ask for clarification

Confidence < 0.18
        |
        +--> Fallback response
```

This is important because the goal is not simply to produce an answer for every question, but to avoid confidently answering when there is not enough evidence.

---

# Knowledge Base

The chatbot's training data is stored in:

```text
src/main/resources/faq-dataset.json
```

The current dataset contains:

| Item | Count |
|---|---:|
| Intents | **41** |
| Training examples | **362** |
| Layered answers | **120** |

Each intent can contain different response levels:

```text
PRIMARY
DETAIL
EXAMPLE
```

For example:

```json
{
  "name": "enum_types",
  "description": "Enums in Java",
  "category": "Java Basics",
  "examples": [
    "what is an enum",
    "how do enums work",
    "enum in java"
  ],
  "responses": {
    "PRIMARY": [
      "An enum defines a fixed set of named constants..."
    ],
    "DETAIL": [
      "Every enum implicitly extends java.lang.Enum..."
    ],
    "EXAMPLE": [
      "enum Day { MONDAY, TUESDAY, ... }"
    ]
  }
}
```

---

# Model Evaluation

The project includes two main evaluation approaches:

1. Leave-one-out cross-validation
2. A held-out set of realistic paraphrases

### Results

| Evaluation | Result |
|---|---:|
| Leave-one-out accuracy | **61.6%** |
| Macro F1 | **0.64** |
| Held-out top-1 accuracy | **93.3%** |
| Held-out top-3 accuracy | **96.7%** |

The held-out dataset contains **60 questions** that were not used during training.

The complete training dataset contains **362 examples across 41 intents**.

### Classifier Comparison

| Classifier | Top-1 Accuracy |
|---|---:|
| Naive Bayes | 93.3% |
| Cosine Similarity | 91.7% |
| Keyword Coverage | 90.0% |
| **Ensemble** | **93.3%** |

The ensemble uses the default weights:

```text
0.35 / 0.45 / 0.20
```

---

# Results

The final application brings the NLP pipeline, machine learning model, rule engine, conversation handling, and user interface together into a working programming chatbot.

The screenshots below show the actual web application interface and conversation flow.

## Main Interface

The main page provides the starting point for interacting with CodeMate. Users can select or explore programming topics and begin a conversation with the chatbot.

<p align="center">
  <img src="screenshots/home.png" alt="CodeMate main interface" width="92%">
</p>

## Chat Interface

The conversation view shows the interaction between the user and CodeMate, allowing programming questions and follow-up questions to be handled within the same session.

<p align="center">
  <img src="screenshots/conversation.png" alt="CodeMate conversation interface" width="92%">
</p>

## Evaluation Results

The current implementation produced the following results on the available evaluation datasets:

```text
Training intents       : 41
Training examples      : 362
Held-out questions     : 60

Cross-validation       : 61.6% accuracy
Macro F1               : 0.64

Held-out Top-1         : 93.3%
Held-out Top-3         : 96.7%
```

These numbers describe the current implementation and should be considered in the context of the project's training dataset size.

---

# Improvements Found During Testing

Testing the chatbot revealed a couple of issues that were addressed during development.

### Question Words Affecting Classification

Initially, words such as:

```text
what
how
why
```

were retained as useful features.

Because these words occur frequently across different questions, they sometimes caused unrelated intents to look similar.

Moving them into the stop-word list while allowing the question-type detector to process them beforehand improved cross-validation accuracy:

```text
Before: 53.9%
After : 61.6%
```

### Naive Bayes Overconfidence

The original classifier could select an intent even when a question contained no vocabulary known to the model.

That could result in an answer that sounded confident but was not supported by the training data.

The implementation was changed so that the classifier can abstain when there is not enough evidence and reduce confidence when only part of the message is recognized.

---

# Adding New Knowledge

There are two ways to add new knowledge to CodeMate.

### Through the Application

The application provides APIs for adding training data:

```http
POST /api/training/examples
```

Create a new intent:

```http
POST /api/training/intents
```

The **Teach** tab provides a more convenient interface for these operations.

User feedback can also be used to correct an incorrect intent.

### Through the Dataset

The main dataset is:

```text
src/main/resources/faq-dataset.json
```

After making changes, reload it with:

```http
POST /api/training/reload-dataset
```

or restart the application.

---

# REST API

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/chat` | Send a question to the chatbot |
| `DELETE` | `/api/chat/session/{id}` | Clear session context |
| `GET` | `/api/chat/history` | Get recent chat history |
| `GET` | `/api/chat/history/{sessionId}` | Get a session transcript |
| `POST` | `/api/feedback` | Submit feedback or correct an intent |
| `POST` | `/api/training/examples` | Add a training example |
| `POST` | `/api/training/intents` | Create an intent |
| `POST` | `/api/training/reload-dataset` | Reload the FAQ dataset |
| `GET` | `/api/training/gaps` | View questions the bot could not answer confidently |
| `GET` | `/api/intents` | Browse intents |
| `GET` | `/api/intents/by-category` | Browse intents by category |
| `GET` | `/api/intents/{name}` | Get a specific intent |
| `GET` | `/api/intents/names` | Get intent names |
| `GET` | `/api/model` | View model status |
| `GET` | `/api/model/metrics` | View evaluation metrics |
| `POST` | `/api/model/retrain` | Retrain the model |
| `GET/POST` | `/api/model/analyze` | Inspect NLP and classifier processing |
| `GET` | `/api/analytics` | Get chatbot analytics |
| `GET` | `/api/health` | Check application health |

---

# Project Structure

```text
src/
├── main/
│   ├── java/
│   │   └── com/aichatbot/
│   │       ├── nlp/
│   │       ├── ml/
│   │       ├── service/
│   │       ├── entity/
│   │       ├── repository/
│   │       ├── dto/
│   │       ├── http/
│   │       ├── gui/
│   │       └── config/
│   │
│   └── resources/
│       ├── faq-dataset.json
│       ├── application.properties
│       ├── application-mysql.properties.example
│       └── static/
│           ├── index.html
│           ├── app.js
│           └── styles.css
│
└── test/
    ├── java/com/aichatbot/
    │   ├── nlp/
    │   ├── ml/
    │   └── service/
    │
    └── resources/
        └── test-questions.json
```

---

# Configuration

The main configuration file is:

```text
src/main/resources/application.properties
```

Important settings include:

```properties
chatbot.confidence-threshold=0.35
chatbot.clarify-threshold=0.18

chatbot.weights.naive-bayes=0.35
chatbot.weights.similarity=0.45
chatbot.weights.keyword=0.20

chatbot.learning.reinforce-below=0.75
chatbot.evaluation.enabled=true
```

---

# Database

CodeMate uses an H2 file database by default:

```text
./data/ai_chatbot.mv.db
```

On the first startup, the application:

1. Loads the FAQ dataset.
2. Seeds the database.
3. Trains the classifier.
4. Starts the application.

Training data added while using the application is preserved across restarts.

MySQL configuration is also provided through:

```text
application-mysql.properties.example
```

---

# Running the Project

## Requirements

Before running CodeMate, make sure you have:

- Java JDK
- Maven
- Git
- A modern web browser

Check Java:

```bash
java -version
```

Check Maven:

```bash
mvn -version
```

## Clone the Repository

```bash
git clone <your-repository-url>
cd <project-directory>
```

## Start the Web Application

```bash
mvn spring-boot:run
```

Open:

```text
http://localhost:8081
```

## Start the Desktop Version

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--gui
```

## Start the Console Version

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--console
```

---

# Build

To create the JAR:

```bash
mvn clean package
```

Run the web version:

```bash
java -jar target/ai-chatbot-0.0.1-SNAPSHOT.jar
```

Run the desktop version:

```bash
java -jar target/ai-chatbot-0.0.1-SNAPSHOT.jar --gui
```

Run the console version:

```bash
java -jar target/ai-chatbot-0.0.1-SNAPSHOT.jar --console
```

---

# Testing

Run the test suite with:

```bash
mvn test
```

The project includes tests for areas such as:

- NLP processing
- Text normalization
- Porter stemming
- Spell correction
- Sentiment analysis
- Naive Bayes
- TF-IDF
- Cosine similarity
- Ensemble classification
- Rule engine

---

# Model Analysis

CodeMate also provides an analysis endpoint for inspecting how a message moves through the NLP and classification pipeline.

```http
GET /api/model/analyze
```

or:

```http
POST /api/model/analyze
```

This is useful when testing or demonstrating how the chatbot processes a particular input.

---

# Analytics

Conversation information such as the following is stored for analytics:

- Intent
- Confidence
- Strategy
- Sentiment
- Response time

The analytics API is:

```http
GET /api/analytics
```

---

# Limitations

CodeMate currently has a knowledge base of:

```text
41 intents
362 training examples
```

The training dataset is relatively small, so the evaluation results should be viewed within the context of the current dataset.

The leave-one-out result is also conservative because removing an individual example can remove important vocabulary for that intent.

CodeMate is currently a traditional NLP and machine-learning system rather than a deep-learning or transformer-based chatbot.

---

# Future Improvements

Some areas that could be explored in future versions include:

- Expanding the training dataset
- Adding more programming topics
- Adding more paraphrased questions
- Improving classification robustness
- Expanding analytics
- Adding more rule-based capabilities
- Adding additional interfaces
- Improving multilingual support
- Exploring more advanced machine-learning approaches

---

# Contributing

If you want to contribute:

1. Fork the repository.
2. Create a new branch.
3. Make your changes.
4. Test the changes.
5. Commit your changes.
6. Push the branch.
7. Open a Pull Request.

---

# License

This project is intended for **educational and learning purposes**.

---

# Author

**G N Shanthaveeragowda**

Student / Software Developer

GitHub: `https://github.com/gn-shanthaveeragowda`

---

## Repository Structure for Screenshots

Make sure the screenshots are stored inside the repository like this:

```text
project-root/
│
├── src/
-----
│   ├── home.png
│   └── conversation.png
│
├── pom.xml
└── README.md
```

The Results section references:

```text
home.png
conversation.png
```

Once those two files are uploaded to GitHub, they will automatically appear in the **Results** section of the README.
