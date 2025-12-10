package edu.zzttc.backend.service.search.impl;

import edu.zzttc.backend.service.search.VectorStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class InMemoryVectorStore implements VectorStore {
    private final Map<String, Map<String, String>> db = new HashMap<>();

    @Override
    public void upsert(String namespace, String id, String text) {
        db.computeIfAbsent(namespace, k -> new HashMap<>()).put(id, text == null ? "" : text);
    }

    @Override
    public String[] search(String namespace, String query, int topK) {
        Map<String, String> m = db.getOrDefault(namespace, Collections.emptyMap());
        if (m.isEmpty()) return new String[0];
        String[] qs = tokenize(query);
        List<Map.Entry<String, String>> list = new ArrayList<>(m.entrySet());
        list.sort((a, b) -> score(b.getValue(), qs) - score(a.getValue(), qs));
        return list.stream().limit(topK).map(Map.Entry::getValue).toArray(String[]::new);
    }

    private int score(String text, String[] qs) {
        int s = 0;
        for (String q : qs) if (text.contains(q)) s++;
        return s;
    }

    private String[] tokenize(String x) {
        if (x == null) return new String[0];
        return x.replaceAll("\\s+", " ").trim().split(" ");
    }
}

