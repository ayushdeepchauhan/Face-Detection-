package com.attendance.dao;

import com.attendance.model.db.FaceEmbedding;
import java.util.List;

public interface FaceEmbeddingDAO {
    FaceEmbedding findById(Long id);
    List<FaceEmbedding> findByStudentId(Long studentId);
    List<FaceEmbedding> findAll();
    Long save(FaceEmbedding embedding);
    void delete(Long id);
    void deleteByStudentId(Long studentId);
}
