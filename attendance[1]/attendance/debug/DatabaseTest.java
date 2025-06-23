package com.attendance.debug;

import com.attendance.dao.AttendanceDAO;
import com.attendance.dao.FaceEmbeddingDAO;
import com.attendance.dao.StudentDAO;
import com.attendance.dao.impl.JdbcAttendanceDAO;
import com.attendance.dao.impl.JdbcFaceEmbeddingDAO;
import com.attendance.dao.impl.JdbcStudentDAO;
import com.attendance.model.db.AttendanceRecord;
import com.attendance.model.db.FaceEmbedding;
import com.attendance.model.db.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Random;

/**
 * Utility class to test database connectivity and operations.
 * This allows testing the database components without requiring the webcam.
 */
public class DatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseTest.class);
    private static final DataSource dataSource = createDataSource();
    
    private static final StudentDAO studentDAO = new JdbcStudentDAO(dataSource);
    private static final AttendanceDAO attendanceDAO = new JdbcAttendanceDAO(dataSource);
    private static final FaceEmbeddingDAO faceEmbeddingDAO = new JdbcFaceEmbeddingDAO(dataSource);
    
    public static void main(String[] args) {
        try {
            logger.info("Starting Database Test...\n");

            // Test Student Operations
            logger.info("Testing Student Operations:");
            testStudentOperations();

            // Test Face Embedding Operations
            logger.info("\nTesting Face Embedding Operations:");
            testFaceEmbeddingOperations();

            // Test Attendance Operations
            logger.info("\nTesting Attendance Operations:");
            testAttendanceOperations();

            logger.info("\nAll tests completed successfully!");

        } catch (Exception e) {
            logger.error("Test failed: " + e.getMessage(), e);
        }
    }

    private static void testStudentOperations() {
        // Create a test student
        Student student = new Student("ST001", "John", "Doe");
        Long studentId = studentDAO.save(student);
        logger.info("Created student with ID: {}", studentId);

        // Retrieve and verify
        Student retrieved = studentDAO.findById(studentId);
        logger.info("Retrieved student: {}", retrieved);

        // List all students
        logger.info("All students: {}", studentDAO.findAll());
    }

    private static void testFaceEmbeddingOperations() {
        // Get first student
        Student student = studentDAO.findAll().get(0);
        
        // Create test embedding
        byte[] dummyEmbedding = generateRandomEmbedding();
        
        FaceEmbedding embedding = new FaceEmbedding(student.getId(), dummyEmbedding);
        Long embeddingId = faceEmbeddingDAO.save(embedding);
        logger.info("Created face embedding with ID: {}", embeddingId);

        // Retrieve and verify
        FaceEmbedding retrieved = faceEmbeddingDAO.findById(embeddingId);
        logger.info("Retrieved face embedding for student: {}", retrieved.getStudentId());

        // List all embeddings for student
        logger.info("Number of embeddings for student: {}", 
            faceEmbeddingDAO.findByStudentId(student.getId()).size());
    }

    private static void testAttendanceOperations() {
        // Get first student
        Student student = studentDAO.findAll().get(0);
        
        // Create test attendance record
        AttendanceRecord record = new AttendanceRecord(
            student.getId(),
            LocalDate.now(),
            LocalTime.now(),
            "Present"
        );

        Long recordId = attendanceDAO.save(record);
        logger.info("Created attendance record with ID: {}", recordId);

        // Retrieve by student
        logger.info("Number of attendance records for student: {}", 
            attendanceDAO.findByStudentId(student.getId()).size());

        // Retrieve by date
        logger.info("Number of attendance records for today: {}", 
            attendanceDAO.findByDate(LocalDate.now()).size());
    }
    
    /**
     * Generate a random face embedding for testing.
     */
    private static byte[] generateRandomEmbedding() {
        // Generate 128-dimensional embedding vector
        float[] embedding = new float[128];
        Random random = new Random();
        
        // Generate random values
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = random.nextFloat() * 2 - 1; // Random value between -1 and 1
        }
        
        // Normalize the embedding
        float sum = 0;
        for (float v : embedding) {
            sum += v * v;
        }
        
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] /= norm;
        }
        
        // Convert to bytes for storage
        ByteBuffer buffer = ByteBuffer.allocate(embedding.length * 4); // 4 bytes per float
        for (float f : embedding) {
            buffer.putFloat(f);
        }
        
        return buffer.array();
    }

    private static DataSource createDataSource() {
        return com.attendance.db.DbConfig.getDataSource();
    }
}
