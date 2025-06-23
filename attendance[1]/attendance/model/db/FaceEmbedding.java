package com.attendance.model.db;

import java.time.LocalDateTime;

public class FaceEmbedding {
    private Long id;
    private Long studentId;
    private byte[] embeddingData;
    private LocalDateTime createdAt;

    public FaceEmbedding() {}

    public FaceEmbedding(Long studentId, byte[] embeddingData) {
        this.studentId = studentId;
        this.embeddingData = embeddingData;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public byte[] getEmbeddingData() { return embeddingData; }
    public void setEmbeddingData(byte[] embeddingData) { this.embeddingData = embeddingData; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
