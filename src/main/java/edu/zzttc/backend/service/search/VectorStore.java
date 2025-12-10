package edu.zzttc.backend.service.search;

public interface VectorStore {
    void upsert(String namespace, String id, String text);
    String[] search(String namespace, String query, int topK);
}

