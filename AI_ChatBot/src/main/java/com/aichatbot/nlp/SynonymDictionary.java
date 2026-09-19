package com.aichatbot.nlp;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps domain vocabulary onto canonical terms before classification.
 *
 * <p>Without this, "explain oops concepts" and "what is object oriented
 * programming" share no tokens at all. Canonicalising "oops" to "oop" and
 * "object oriented" to "oop" makes them land on the same feature.</p>
 */
@Component
public class SynonymDictionary {

    private final Map<String, String> canonical = new HashMap<>();

    public SynonymDictionary() {
        // Object-oriented vocabulary
        register("oop", "oops", "oopm", "objectoriented", "opps");
        register("inheritance", "inherit", "inherits", "inheriting", "extends", "subclassing");
        register("polymorphism", "polymorphic", "polymorph", "overloading", "overriding");
        register("encapsulation", "encapsulate", "encapsulated", "datahiding");
        register("abstraction", "abstract", "abstracted");
        register("class", "classes", "blueprint");
        register("object", "objects", "instance", "instances");

        // Language and platform terms
        register("java", "jvm", "jdk", "jre", "javase");
        register("method", "methods", "function", "functions", "func", "procedure");
        register("variable", "variables", "var", "vars", "identifier");
        register("array", "arrays");
        register("collection", "collections", "collectionframework");
        register("exception", "exceptions", "error", "errors", "bug", "crash");
        register("stream", "streams", "streamapi");
        register("lambda", "lambdas", "closure", "arrowfunction");
        register("thread", "threads", "threading", "multithreading", "concurrency");
        register("interface", "interfaces");
        register("constructor", "constructors");
        register("recursion", "recursive", "recurse");

        // Data and web terms
        register("database", "db", "databases", "rdbms", "datastore");
        register("sql", "mysql", "query", "queries", "postgresql", "sqlquery");
        register("jdbc", "javadatabaseconnectivity");
        register("api", "apis", "endpoint", "endpoints", "webservice");
        register("rest", "restful", "restapi");
        register("spring", "springboot", "springframework");
        register("json", "jsondata");
        register("test", "testing", "tests", "junit", "unittest", "unittesting");
        register("git", "github", "versioncontrol");

        // Conversational vocabulary
        register("hello", "hi", "hey", "hii", "hiii", "yo", "greetings", "hola", "namaste");
        register("bye", "goodbye", "farewell", "exit", "quit", "cya");
        register("thanks", "thank", "thankyou", "thanx", "grateful", "appreciate");
        register("help", "assist", "assistance", "support", "guide");
        register("explain", "describe", "elaborate", "clarify", "define", "definition", "meaning");
        register("example", "examples", "sample", "samples", "demo", "illustration");
        register("difference", "differences", "compare", "comparison", "versus", "vs", "differ");
    }

    private void register(String canonicalTerm, String... variants) {
        canonical.put(canonicalTerm, canonicalTerm);
        for (String variant : variants) {
            canonical.put(variant, canonicalTerm);
        }
    }

    /** Returns the canonical form of a token, or the token itself if unknown. */
    public String canonicalize(String token) {
        return canonical.getOrDefault(token, token);
    }

    public boolean isKnown(String token) {
        return canonical.containsKey(token);
    }

    public int size() {
        return canonical.size();
    }
}
