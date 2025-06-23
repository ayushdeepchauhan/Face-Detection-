package com.attendance.debug;

import com.attendance.dao.FaceEmbeddingDAO;
import com.attendance.dao.impl.JdbcFaceEmbeddingDAO;
import com.attendance.model.db.FaceEmbedding;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.sql.SQLException;
import java.util.List;

public class EmbeddingDatabaseInspector {
    public static void main(String[] args) throws SQLException {
        javax.sql.DataSource dataSource = com.attendance.db.DbConfig.getDataSource();
        FaceEmbeddingDAO embeddingDAO = new JdbcFaceEmbeddingDAO(dataSource);

        List<FaceEmbedding> embeddings = embeddingDAO.findAll();
        System.out.println("\n=== Embedding Database Inspector ===");
        System.out.println("Embeddings found: " + embeddings.size());

        for (FaceEmbedding emb : embeddings) {
            System.out.printf("\nEmbedding ID: %d, Student ID: %d, Byte Length: %d\n", emb.getId(), emb.getStudentId(), 
                emb.getEmbeddingData() != null ? emb.getEmbeddingData().length : -1);
            byte[] bytes = emb.getEmbeddingData();
            if (bytes == null || bytes.length % 4 != 0) {
                System.out.println("  [!] Invalid or missing embedding data.");
                continue;
            }
            // Print first 16 bytes as hex
            System.out.print("  Raw bytes (first 16): ");
            for (int i = 0; i < Math.min(16, bytes.length); i++) {
                System.out.printf("%02X ", bytes[i]);
            }
            System.out.println();
            // Convert to float[] using BIG_ENDIAN
            FloatBuffer floatBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asFloatBuffer();
            float[] floats = new float[floatBuffer.capacity()];
            floatBuffer.get(floats);
            int previewCount = Math.min(5, floats.length);
            System.out.print("  Floats (first 5): [");
            for (int j = 0; j < previewCount; j++) {
                System.out.print(floats[j]);
                if (j < previewCount - 1) System.out.print(", ");
            }
            System.out.println("]");
        }
        
    }
}
