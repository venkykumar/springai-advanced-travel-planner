package com.example.travelplanner.init;

import com.example.travelplanner.data.DestinationDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads destination knowledge into PgVectorStore on startup.
 * Unlike SimpleVectorStore (which re-ingests every boot), PgVectorStore persists
 * embeddings in Postgres permanently — set travel.agent.ingest-knowledge-base=false
 * after the first run to skip re-ingestion.
 */
@Component
public class KnowledgeBaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseInitializer.class);

    private final VectorStore vectorStore;
    private final DestinationDataRepository destinationRepo;

    @Value("${travel.agent.ingest-knowledge-base:true}")
    private boolean shouldIngest;

    public KnowledgeBaseInitializer(VectorStore vectorStore, DestinationDataRepository destinationRepo) {
        this.vectorStore = vectorStore;
        this.destinationRepo = destinationRepo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ingest() {
        if (!shouldIngest) {
            log.info("Knowledge base ingestion skipped (travel.agent.ingest-knowledge-base=false)");
            return;
        }

        log.info("Starting knowledge base ingestion into PgVectorStore...");
        List<Document> documents = new ArrayList<>();

        for (String destination : destinationRepo.supportedDestinations()) {
            String text = destinationRepo.getFullTextDescription(destination);
            if (text == null || text.isBlank()) continue;

            // Split into chunks of ~500 chars for better retrieval granularity
            List<String> chunks = chunkText(text, 500);
            for (int i = 0; i < chunks.size(); i++) {
                documents.add(new Document(
                        chunks.get(i),
                        Map.of("destination", destination, "chunk", i)
                ));
            }
            log.info("Prepared {} chunks for destination: {}", chunks.size(), destination);
        }

        if (!documents.isEmpty()) {
            vectorStore.add(documents);
            log.info("Ingested {} document chunks into PgVectorStore", documents.size());
        }
    }

    private List<String> chunkText(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        String[] lines = text.split("\n");
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            if (current.length() + line.length() > chunkSize && !current.isEmpty()) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(line).append("\n");
        }
        if (!current.isEmpty()) chunks.add(current.toString().trim());
        return chunks;
    }
}
