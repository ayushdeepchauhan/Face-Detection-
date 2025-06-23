package com.attendance.service;

import com.attendance.dao.AttendanceDAO;
import com.attendance.dao.CourseDAO;
import com.attendance.dao.FaceEmbeddingDAO;
import com.attendance.dao.StudentDAO;
import com.attendance.dao.impl.JdbcAttendanceDAO;
import com.attendance.dao.impl.JdbcCourseDAO;
import com.attendance.dao.impl.JdbcFaceEmbeddingDAO;
import com.attendance.dao.impl.JdbcStudentDAO;
import com.attendance.model.db.AttendanceRecord;
import com.attendance.model.db.Course;
import com.attendance.model.db.FaceEmbedding;
import com.attendance.model.db.Student;
import com.attendance.model.recognition.DJLRecognizedFace;
import com.attendance.video.tracking.TrackedFace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



import javax.sql.DataSource;



import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDate;
import java.time.LocalTime;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service class that integrates face recognition with the attendance database.
 * This class handles matching recognized faces with student records and marking attendance.
 */
public class AttendanceService {
    /**
     * Returns all attendance records for a given course.
     * @param courseId the course ID
     * @return list of attendance records for the course
     */
    public List<AttendanceRecord> getAttendanceRecordsByCourseId(Integer courseId) {
        return attendanceDAO.findByCourseId(courseId);
    }

    /**
     * Returns all attendance records for a given course and student.
     * @param courseId the course ID
     * @param studentId the student ID
     * @return list of attendance records for the course and student
     */
    public List<AttendanceRecord> getAttendanceRecordsByCourseIdAndStudentId(Integer courseId, Long studentId) {
        return attendanceDAO.findByCourseIdAndStudentId(courseId, studentId);
    }
    private static final Logger logger = LoggerFactory.getLogger(AttendanceService.class);
    
    // Minimum confidence threshold for face recognition
    private static final double RECOGNITION_THRESHOLD = 0.7;
    
    // Minimum number of recognitions needed to mark attendance
    private static final int MIN_RECOGNITIONS_FOR_ATTENDANCE = 5;
    
    // DAOs for database access
    private final StudentDAO studentDAO;
    private final FaceEmbeddingDAO faceEmbeddingDAO;
    private final AttendanceDAO attendanceDAO;
    private final CourseDAO courseDAO;
    
    // Current course for this attendance session
    private Integer currentCourseId;
    
    // Cache of face embeddings for quick matching
    private final Map<Long, float[]> embeddingCache = new HashMap<>();
    
    // Track recognition counts for each student to avoid duplicate attendance records
    private final Map<Long, Integer> recognitionCounts = new ConcurrentHashMap<>();
    
    // Track students who have already been marked present today
    private final Map<Long, Boolean> attendanceMarked = new ConcurrentHashMap<>();
    
    /**
     * Creates a new AttendanceService with default DAO implementations.
     */
    public AttendanceService() {
        DataSource dataSource = com.attendance.db.DbConfig.getDataSource();

        // Pass DataSource to DAO constructors
        this.studentDAO = new JdbcStudentDAO(dataSource);
        this.faceEmbeddingDAO = new JdbcFaceEmbeddingDAO(dataSource);
        this.attendanceDAO = new JdbcAttendanceDAO(dataSource);
        this.courseDAO = new JdbcCourseDAO(dataSource);
        
        // Load embeddings into cache
        loadEmbeddings();
    }
    
    /**
     * Creates a new AttendanceService with custom DAO implementations.
     */
    public AttendanceService(StudentDAO studentDAO, FaceEmbeddingDAO faceEmbeddingDAO, 
                             AttendanceDAO attendanceDAO, CourseDAO courseDAO) {
        this.studentDAO = studentDAO;
        this.faceEmbeddingDAO = faceEmbeddingDAO;
        this.attendanceDAO = attendanceDAO;
        this.courseDAO = courseDAO;
        
        // Load embeddings into cache
        loadEmbeddings();
    }
    
    /**
     * Loads all face embeddings from the database into memory for faster matching.
     */
    private void loadEmbeddings() {
        logger.info("Loading face embeddings into cache...");
        List<Student> students;
        
        // If a course is selected, only load students enrolled in that course
        if (currentCourseId != null) {
            students = courseDAO.findStudentsByCourseId(currentCourseId);
            logger.info("Loading embeddings for {} students enrolled in course ID: {}", students.size(), currentCourseId);
        } else {
            students = studentDAO.findAll();
            logger.info("Loading embeddings for all {} students (no course filter)", students.size());
        }
        
        for (Student student : students) {
            List<FaceEmbedding> embeddings = faceEmbeddingDAO.findByStudentId(student.getId());
            
            for (FaceEmbedding embedding : embeddings) {
                embeddingCache.put(student.getId(), byteArrayToFloatArray(embedding.getEmbeddingData()));
            }
        }
        
        logger.info("Loaded {} face embeddings", embeddingCache.size());
    }
    
    /**
     * Processes a tracked face for attendance marking.
     * If the face is recognized with high confidence, increments the recognition count.
     * Once the recognition count reaches the threshold, marks the student as present.
     *
     * @param trackedFace The tracked face to process
     * @return true if attendance was marked, false otherwise
     */
    public boolean processTrackedFace(TrackedFace trackedFace) {
        // Skip if the face doesn't have an embedding
        if (trackedFace.getEmbedding() == null) {
            return false;
        }
        
        // Find the best matching student
        MatchResult match = findBestMatch(trackedFace.getEmbedding());
        
        // If no match or confidence is too low, skip
        if (match == null || match.confidence < RECOGNITION_THRESHOLD) {
            return false;
        }
        
        // Update the tracked face with student information
        Student student = studentDAO.findById(match.studentId);
        if (student != null) {
            trackedFace.setStudentInfo(match.studentId, 
                String.format("%s %s", student.getFirstName(), student.getLastName()));
        }
        
        // Increment recognition count for this student
        int count = recognitionCounts.getOrDefault(match.studentId, 0) + 1;
        recognitionCounts.put(match.studentId, count);
        
        // Check if we should mark attendance
        if (count >= MIN_RECOGNITIONS_FOR_ATTENDANCE && !attendanceMarked.getOrDefault(match.studentId, false)) {
            markAttendance(match.studentId);
            attendanceMarked.put(match.studentId, true);
            return true;
        }
        
        return false;
    }
    
    /**
     * Processes a recognized face from the DJL pipeline.
     * This is an alternative to using tracked faces.
     *
     * @param recognizedFace The recognized face to process
     * @return true if attendance was marked, false otherwise
     */
    public boolean processRecognizedFace(DJLRecognizedFace recognizedFace) {
        // Skip if the face doesn't have an embedding
        if (recognizedFace.getEmbedding() == null) {
            return false;
        }
        
        // Find the best matching student
        MatchResult match = findBestMatch(recognizedFace.getEmbedding());
        
        // If no match or confidence is too low, skip
        if (match == null || match.confidence < RECOGNITION_THRESHOLD) {
            return false;
        }
        
        // Increment recognition count for this student
        int count = recognitionCounts.getOrDefault(match.studentId, 0) + 1;
        recognitionCounts.put(match.studentId, count);
        
        // Check if we should mark attendance
        if (count >= MIN_RECOGNITIONS_FOR_ATTENDANCE && !attendanceMarked.getOrDefault(match.studentId, false)) {
            markAttendance(match.studentId);
            attendanceMarked.put(match.studentId, true);
            return true;
        }
        
        return false;
    }
    
    /**
     * Marks a student as present in the attendance database.
     *
     * @param studentId The ID of the student to mark as present
     */
    private void markAttendance(Long studentId) {
        logger.info("Marking attendance for student ID: {}", studentId);
        
        AttendanceRecord record;
        
        // Create the record with or without course ID based on current session
        if (currentCourseId != null) {
            record = new AttendanceRecord(
                studentId,
                currentCourseId,
                LocalDate.now(),
                LocalTime.now(),
                "Present"
            );
            logger.info("Marking attendance for course ID: {}", currentCourseId);
        } else {
            record = new AttendanceRecord(
                studentId,
                LocalDate.now(),
                LocalTime.now(),
                "Present"
            );
        }
        
        attendanceDAO.save(record);
        
        logger.info("Attendance marked for student ID: {}", studentId);
    }
    
    /**
     * Finds the best matching student for a given face embedding.
     *
     * @param embedding The face embedding to match
     * @return The best match result, or null if no match found
     */
    private MatchResult findBestMatch(float[] embedding) {
        MatchResult bestMatch = null;
        double bestSimilarity = -1.0;
        
        for (Map.Entry<Long, float[]> entry : embeddingCache.entrySet()) {
            double similarity = calculateCosineSimilarity(embedding, entry.getValue());
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestMatch = new MatchResult(entry.getKey(), similarity);
            }
        }
        
        return bestMatch;
    }
    
    /**
     * Calculates the cosine similarity between two face embeddings.
     * Higher value indicates more similar faces (1.0 = identical).
     *
     * @param embedding1 The first embedding
     * @param embedding2 The second embedding
     * @return The cosine similarity (between -1.0 and 1.0)
     */
    private double calculateCosineSimilarity(float[] embedding1, float[] embedding2) {
        if (embedding1.length != embedding2.length) {
            return -1.0;
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            norm1 += embedding1[i] * embedding1[i];
            norm2 += embedding2[i] * embedding2[i];
        }
        
        norm1 = Math.sqrt(norm1);
        norm2 = Math.sqrt(norm2);
        
        return dotProduct / (norm1 * norm2);
    }
    
    /**
     * Converts a byte array to a float array with explicit BIG_ENDIAN byte order.
     * This is needed because face embeddings are stored as bytes in the database.
     *
     * @param bytes The byte array to convert
     * @return The converted float array
     */
    private float[] byteArrayToFloatArray(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.BIG_ENDIAN); // Use same byte order as when saving
        float[] floats = new float[bytes.length / 4]; // 4 bytes per float
        
        for (int i = 0; i < floats.length; i++) {
            floats[i] = buffer.getFloat();
        }
        
        return floats;
    }
    
    /**
     * Resets the attendance tracking for a new session.
     * This should be called at the start of each class session.
     * 
     * @param courseId The ID of the course for this session, or null for all students
     */
    public void resetAttendanceTracking(Integer courseId) {
        recognitionCounts.clear();
        attendanceMarked.clear();
        this.currentCourseId = courseId;
        
        // Reload embeddings based on the selected course
        embeddingCache.clear();
        loadEmbeddings();
        
        if (courseId != null) {
            Course course = courseDAO.findById(courseId);
            if (course != null) {
                logger.info("Attendance tracking reset for new session of course: {} ({})", 
                        course.getCourseName(), course.getCourseCode());
            } else {
                logger.warn("Attendance tracking reset with unknown course ID: {}", courseId);
            }
        } else {
            logger.info("Attendance tracking reset for new session (no course filter)");
        }
    }
    
    /**
     * Resets the attendance tracking for a new session with no course filter.
     * This should be called at the start of each class session.
     */
    public void resetAttendanceTracking() {
        resetAttendanceTracking(null);
    }
    
    /**
     * Gets the current course ID for this attendance session.
     * 
     * @return The current course ID, or null if no course is selected
     */
    public Integer getCurrentCourseId() {
        return currentCourseId;
    }
    
    /**
     * Checks if a student was marked present during the current session.
     * 
     * @param studentId The ID of the student to check
     * @return True if the student was marked present, false otherwise
     */
    public boolean wasStudentMarkedPresent(Long studentId) {
        Boolean marked = attendanceMarked.get(studentId);
        return marked != null && marked;
    }
    
    /**
     * Gets a student by ID.
     * 
     * @param studentId The ID of the student to retrieve
     * @return The student, or null if not found
     */
    public Student getStudentById(Long studentId) {
        return studentDAO.findById(studentId);
    }
    
    /**
     * Gets the current course for this attendance session.
     * 
     * @return The current course, or null if no course is selected
     */
    public Course getCurrentCourse() {
        if (currentCourseId == null) {
            return null;
        }
        return courseDAO.findById(currentCourseId);
    }
    
    /**
     * Inner class to hold a match result.
     */
    private static class MatchResult {
        final Long studentId;
        final double confidence;
        
        MatchResult(Long studentId, double confidence) {
            this.studentId = studentId;
            this.confidence = confidence;
        }
    }
}
