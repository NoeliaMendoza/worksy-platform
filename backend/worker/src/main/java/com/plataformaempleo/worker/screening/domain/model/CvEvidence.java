package com.plataformaempleo.worker.screening.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;

/** Coincidencias léxicas del texto extraído, independientes de lo que responda el modelo. */
public record CvEvidence(String method, String sourceTextSha256, Map<String, List<Snippet>> snippets) {
    public static final String METHOD = "label-terms-v1";
    public static final int MAX_SNIPPETS = 3;
    public static final int MAX_SNIPPET_CHARS = 480;
    private static final int CONTEXT = 140;
    private static final int MAX_MATCH_SPAN = 160;
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}\\p{M}]*[+#]*");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Set<String> GENERIC = Set.of(
            "de", "del", "el", "la", "los", "las", "en", "con", "sin", "y", "o", "un", "una", "para", "por",
            "experiencia", "profesional", "profesionales", "anos", "ano", "nivel", "dominio", "conocimiento",
            "conocimientos", "manejo", "habilidad", "habilidades", "capacidad", "competencia", "competencias",
            "requisito", "requisitos", "requerido", "requerida", "general", "compatibilidad",
            "the", "and", "or", "in", "with", "of", "experience", "professional", "years", "skills");

    public record Snippet(String text, int startOffset, int endOffset) {
        public Snippet {
            if (text == null || text.isBlank() || text.length() > MAX_SNIPPET_CHARS
                    || startOffset < 0 || endOffset - startOffset != text.length()) {
                throw new IllegalArgumentException("Fragmento inválido");
            }
        }

        @Override
        public String toString() {
            return "Snippet[startOffset=" + startOffset + ", endOffset=" + endOffset + "]";
        }
    }

    private record Token(String value, int start, int end) { }

    public CvEvidence {
        Map<String, List<Snippet>> copy = new HashMap<>();
        snippets.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        snippets = Map.copyOf(copy);
    }

    public static CvEvidence collect(String text, List<Criterion> criteria) {
        List<Token> tokens = tokens(text);
        Map<String, List<Snippet>> evidence = new HashMap<>();
        for (Criterion criterion : criteria) {
            Set<String> terms = new HashSet<>();
            if (criterion.label() != null) {
                for (Token token : tokens(criterion.label())) {
                    if (!GENERIC.contains(token.value()) && (token.value().length() >= 2 || token.value().equals("c") || token.value().equals("r"))) {
                        terms.add(token.value());
                    }
                }
            }
            evidence.put(criterion.key(), find(text, tokens, terms));
        }
        return new CvEvidence(METHOD, fingerprint(text), evidence);
    }

    public List<Snippet> forCriterion(String key) {
        return snippets.getOrDefault(key, List.of());
    }

    private static List<Snippet> find(String text, List<Token> tokens, Set<String> terms) {
        if (terms.isEmpty()) return List.of();
        List<Snippet> found = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            Token first = tokens.get(i);
            if (!terms.contains(first.value())) continue;
            Set<String> matched = new HashSet<>();
            for (int j = i; j < tokens.size(); j++) {
                Token last = tokens.get(j);
                if (last.end() - first.start() > MAX_MATCH_SPAN) break;
                if (terms.contains(last.value())) matched.add(last.value());
                if (!matched.containsAll(terms)) continue;
                int start = Math.max(0, first.start() - CONTEXT);
                int end = Math.min(text.length(), last.end() + CONTEXT);
                while (start < first.start() && start > 0 && !Character.isWhitespace(text.charAt(start - 1))) start++;
                while (start < first.start() && Character.isWhitespace(text.charAt(start))) start++;
                while (end > last.end() && end < text.length() && !Character.isWhitespace(text.charAt(end))) end--;
                while (end > last.end() && Character.isWhitespace(text.charAt(end - 1))) end--;
                if (!found.isEmpty()) {
                    Snippet previous = found.get(found.size() - 1);
                    if (start <= previous.endOffset() && Math.max(end, previous.endOffset()) - previous.startOffset() <= MAX_SNIPPET_CHARS) {
                        end = Math.max(end, previous.endOffset());
                        start = previous.startOffset();
                        found.remove(found.size() - 1);
                    }
                }
                found.add(new Snippet(text.substring(start, end), start, end));
                break;
            }
            if (found.size() == MAX_SNIPPETS) break;
        }
        return List.copyOf(found);
    }

    private static List<Token> tokens(String text) {
        List<Token> tokens = new ArrayList<>();
        var matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            String normalized = MARKS.matcher(Normalizer.normalize(matcher.group(), Normalizer.Form.NFD))
                    .replaceAll("").toLowerCase(Locale.ROOT);
            tokens.add(new Token(normalized, matcher.start(), matcher.end()));
        }
        return tokens;
    }

    private static String fingerprint(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible");
        }
    }

    @Override
    public String toString() {
        return "CvEvidence[method=" + method + ", criteria=" + snippets.size() + "]";
    }
}
