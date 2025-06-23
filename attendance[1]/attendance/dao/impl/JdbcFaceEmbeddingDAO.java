package com.attendance.dao.impl;

import com.attendance.dao.FaceEmbeddingDAO;
import com.attendance.model.db.FaceEmbedding;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class JdbcFaceEmbeddingDAO implements FaceEmbeddingDAO {
    private final DataSource ds;

    public JdbcFaceEmbeddingDAO(DataSource ds) {
        this.ds = ds;
    }

    @Override
    public FaceEmbedding findById(Long id) {
        String sql = "SELECT id, student_id, embedding_data, created_at FROM face_embeddings WHERE id = ?";
        try (Connection conn = ds.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding face embedding by ID: " + id, e);
        }
        return null;
    }

    @Override
    public List<FaceEmbedding> findByStudentId(Long studentId) {
        String sql = "SELECT id, student_id, embedding_data, created_at FROM face_embeddings WHERE student_id = ?";
        List<FaceEmbedding> list = new ArrayList<>();
        try (Connection conn = ds.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding face embeddings for student: " + studentId, e);
        }
        return list;
    }

    @Override
    public Long save(FaceEmbedding embedding) {
        String sql = "INSERT INTO face_embeddings(student_id, embedding_data, created_at) VALUES(?,?,?) RETURNING id";
        try (Connection conn = ds.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, embedding.getStudentId());
            ps.setBytes(2, embedding.getEmbeddingData());
            ps.setTimestamp(3, Timestamp.valueOf(embedding.getCreatedAt() != null ? 
                embedding.getCreatedAt() : LocalDateTime.now()));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error saving face embedding for student: " + embedding.getStudentId(), e);
        }
        return null;
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM face_embeddings WHERE id = ?";
        try (Connection conn = ds.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting face embedding: " + id, e);
        }
    }

    @Override
    public void deleteByStudentId(Long studentId) {
        String sql = "DELETE FROM face_embeddings WHERE student_id = ?";
        try (Connection conn = ds.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            int rowsAffected = ps.executeUpdate();
            System.out.println("Deleted " + rowsAffected + " face embedding(s) for student ID: " + studentId); 
        } catch (SQLException e) {
            System.err.println("Error deleting face embeddings for student ID " + studentId + ": " + e.getMessage());
        }
    }

    @Override
    public List<FaceEmbedding> findAll() {
        List<FaceEmbedding> list = new ArrayList<>();
        String sql = "SELECT id, student_id, embedding_data, created_at FROM face_embeddings";
        try (Connection conn = ds.getConnection(); 
             Statement stmt = conn.createStatement(); 
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            // Consider logging the error instead of throwing RuntimeException in a real app
            throw new RuntimeException("Error finding all face embeddings", e); 
        }
        return list;
    }

    private FaceEmbedding mapRow(ResultSet rs) throws SQLException {
        FaceEmbedding embedding = new FaceEmbedding();
        embedding.setId(rs.getLong("id"));
        embedding.setStudentId(rs.getLong("student_id"));
        embedding.setEmbeddingData(rs.getBytes("embedding_data"));
        embedding.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return embedding;
    }
}
