package com.attendance.service;

import ai.djl.modality.cv.Image;

import com.attendance.dao.FaceEmbeddingDAO;
import com.attendance.dao.StudentDAO;
import com.attendance.model.db.FaceEmbedding;
import com.attendance.model.db.Student;
import com.attendance.model.recognition.DJLRecognizedFace;
import ai.djl.modality.cv.output.BoundingBox;
import com.attendance.model.recognition.IdentifiedFace;
import com.attendance.exception.IdentificationException;
import com.attendance.pipeline.DJLFaceProcessingPipeline; 
import com.attendance.facerecogntion.FaceNetRecognition; // Corrected package name

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays; // Added for logging
import java.util.List;
import java.util.Optional; // Re-add Optional import
import org.slf4j.Logger; // Added for logging
import org.slf4j.LoggerFactory; // Added for logging

// Implementation using DJL for face detection and recognition
public class DJLFaceIdentifier implements FaceIdentifier {

    private static final Logger logger = LoggerFactory.getLogger(DJLFaceIdentifier.class); // Added logger

    private final DJLFaceProcessingPipeline facePipeline;
    private final FaceEmbeddingDAO embeddingDAO;
    private final StudentDAO studentDAO;
    private final FaceNetRecognition faceRecognition; // Use FaceNetRecognition type

    public DJLFaceIdentifier(DJLFaceProcessingPipeline facePipeline,
                             FaceEmbeddingDAO embeddingDAO, 
                             StudentDAO studentDAO,
                             FaceNetRecognition faceRecognition) { // Use FaceNetRecognition type
        this.facePipeline = facePipeline;
        this.embeddingDAO = embeddingDAO;
        this.studentDAO = studentDAO;
        this.faceRecognition = faceRecognition; // Store the instance
    }

    /**
     * Identifies the most likely student match for the most prominent face detected in the input image.
     * 
     * This method uses the configured face processing pipeline to detect faces and extract embeddings.
     * It then compares the primary face's embedding against stored student embeddings using
     * cosine similarity provided by the FaceNetRecognition service. If a match is found
     * with similarity above the configured threshold, an Optional containing the identified
     * student and the similarity score is returned.
     *
     * @param image The input image containing faces.
     * @return An Optional containing the IdentifiedFace (student and similarity score) if a match above 
     *         the threshold is found, otherwise an empty Optional.
     * @throws IdentificationException If an error occurs during face processing, embedding extraction,
     *                                 or if a database/runtime error prevents identification.
     */
    public Optional<IdentifiedFace> identifyFace(Image image) throws IdentificationException {
        List<IdentifiedFaceResult> results = identifyFacesMulti(image);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new IdentifiedFace(results.get(0).getStudent(), results.get(0).getSimilarity()));
    }

    /**
     * Identifies all faces in the image, matches each against the database, and returns a list of recognized faces.
     * Each recognized face includes student, similarity (confidence), and bounding box for visualization.
     *
     * @param image The input image containing faces.
     * @return List of IdentifiedFaceResult for all detected faces (matched or not).
     * @throws IdentificationException If an error occurs during face processing.
     */
    public List<IdentifiedFaceResult> identifyFacesMulti(Image image) throws IdentificationException {
        try {
            List<DJLRecognizedFace> detectedFaces = this.facePipeline.processImage(image);
            if (detectedFaces == null || detectedFaces.isEmpty()) {
                logger.info("No faces detected or processed in the image.");
                return List.of();
            }
            List<FaceEmbedding> storedEmbeddings = embeddingDAO.findAll();
            if (storedEmbeddings.isEmpty()) {
                logger.info("No student profiles enrolled in the database yet.");
                return List.of();
            }
            // Prepare result list
            List<IdentifiedFaceResult> results = new java.util.ArrayList<>();
            for (DJLRecognizedFace face : detectedFaces) {
                float[] newEmbedding = face.getEmbedding();
                if (newEmbedding == null) {
                    logger.warn("Face detected but embedding extraction failed.");
                    results.add(new IdentifiedFaceResult(null, 0.0, face.getBoundingBox(), face));
                    continue;
                }
                Student bestMatchStudent = null;
                double maxSimilarity = -1.0;
                for (FaceEmbedding storedEmbedding : storedEmbeddings) {
                    float[] storedEmbeddingFloats = bytesToFloats(storedEmbedding.getEmbeddingData());
                    if (storedEmbeddingFloats == null) {
                        logger.warn("Skipping corrupted or unreadable embedding for student ID: {}", storedEmbedding.getStudentId());
                        continue; // Skip corrupted/invalid embeddings
                    }
                    double similarity = faceRecognition.compareFaces(newEmbedding, storedEmbeddingFloats);
                    if (similarity > maxSimilarity) {
                        maxSimilarity = similarity;
                        bestMatchStudent = studentDAO.findById(storedEmbedding.getStudentId());
                    }
                }
                boolean isRecognized = bestMatchStudent != null && maxSimilarity >= faceRecognition.getSimilarityThreshold();
                if (isRecognized) {
                    logger.info("Face recognized: {} (similarity: {})", bestMatchStudent.getFirstName(), maxSimilarity);
                } else {
                    logger.info("Face not recognized (max similarity: {})", maxSimilarity);
                }
                results.add(new IdentifiedFaceResult(isRecognized ? bestMatchStudent : null, maxSimilarity, face.getBoundingBox(), face));
            }
            return results;
        } catch (Exception e) {
            logger.error("Error identifying faces: {}", e.getMessage());
            throw new IdentificationException(e.getMessage(), e);
        }
    }

    /**
     * Helper class to hold identification results for visualization.
     */
    public static class IdentifiedFaceResult {
        private final Student student;
        private final double similarity;
        private final ai.djl.modality.cv.output.BoundingBox boundingBox;
        private final DJLRecognizedFace recognizedFace;
        public IdentifiedFaceResult(Student student, double similarity, ai.djl.modality.cv.output.BoundingBox boundingBox, DJLRecognizedFace recognizedFace) {
            this.student = student;
            this.similarity = similarity;
            this.boundingBox = boundingBox;
            this.recognizedFace = recognizedFace;
        }
        public Student getStudent() { return student; }
        public double getSimilarity() { return similarity; }
        public BoundingBox getBoundingBox() { return boundingBox; }
        public DJLRecognizedFace getRecognizedFace() { return recognizedFace; }
    }

    // Keep bytesToFloats as it's needed to read from DB
    private float[] bytesToFloats(byte[] bytes) {
        if (bytes == null || bytes.length % 4 != 0) {
             logger.error("ERROR in bytesToFloats: Input is null or length is not multiple of 4. Len=" + (bytes != null ? bytes.length : -1)); 
            return null;
        }
        try {
            // Use native order consistent with how DJL might handle arrays internally
            // and assuming the same system saves and loads the data.
            // If data is transferred between systems with different endianness, this needs care.
            FloatBuffer floatBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asFloatBuffer();
            float[] floats = new float[floatBuffer.capacity()];
            floatBuffer.get(floats);
            return floats;
        } catch (Exception e) {
             logger.error("ERROR in bytesToFloats: Exception during conversion: " + e.getMessage(), e); 
             // Log the exception maybe? e.printStackTrace();
            return null;
        }
    }
}
