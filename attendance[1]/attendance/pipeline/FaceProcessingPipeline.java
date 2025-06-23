package com.attendance.pipeline;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.DetectedObjects.DetectedObject;
import ai.djl.modality.cv.output.BoundingBox;
import ai.djl.modality.cv.output.Landmark;
import com.attendance.exception.ModelException;
import com.attendance.facedetection.FaceDetectionService;
import com.attendance.facealignment.FaceAligner;
import com.attendance.facerecogntion.FaceRecognitionService;
import com.attendance.model.recognition.RecognizedFace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Coordinates the face processing pipeline from detection through alignment to recognition.
 * This class orchestrates the entire process of identifying faces in images.
 */
public class FaceProcessingPipeline implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(FaceProcessingPipeline.class);
    
    private final FaceDetectionService detectionService;
    private final FaceAligner faceAligner;
    private final FaceRecognitionService recognitionService;
    private final Optional<Path> visualizationDir;
    
    /**
     * Creates a new processing pipeline with the specified components.
     *
     * @param detectionService Face detection service
     * @param faceAligner Face alignment service
     * @param recognitionService Face recognition service
     */
    public FaceProcessingPipeline(
            FaceDetectionService detectionService,
            FaceAligner faceAligner,
            FaceRecognitionService recognitionService) {
        this(detectionService, faceAligner, recognitionService, Optional.empty());
    }
    
    /**
     * Creates a new processing pipeline with the specified components and visualization directory.
     *
     * @param detectionService Face detection service
     * @param faceAligner Face alignment service
     * @param recognitionService Face recognition service
     * @param visualizationDir Optional directory to save visualization images
     */
    public FaceProcessingPipeline(
            FaceDetectionService detectionService,
            FaceAligner faceAligner,
            FaceRecognitionService recognitionService,
            Optional<Path> visualizationDir) {
        this.detectionService = detectionService;
        this.faceAligner = faceAligner;
        this.recognitionService = recognitionService;
        this.visualizationDir = visualizationDir;
        
        // Create visualization directory if provided
        if (visualizationDir.isPresent()) {
            try {
                Files.createDirectories(visualizationDir.get());
            } catch (IOException e) {
                logger.warn("Failed to create visualization directory: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Process an image through the complete pipeline: detection, alignment, and recognition.
     *
     * @param image The input image to process
     * @return List of recognized faces with their embeddings
     * @throws ModelException if processing fails
     */
    public List<RecognizedFace> processImage(Image image) throws ModelException {
        return processImage(image, "face");
    }
    
    /**
     * Process an image through the complete pipeline with custom file prefix for visualizations.
     *
     * @param image The input image to process
     * @param filePrefix Prefix for visualization files
     * @return List of recognized faces with their embeddings
     * @throws ModelException if processing fails
     */
    public List<RecognizedFace> processImage(Image image, String filePrefix) throws ModelException {
        if (image == null) {
            throw new IllegalArgumentException("Input image cannot be null");
        }
        
        List<RecognizedFace> recognizedFaces = new ArrayList<>();
        
        // Step 1: Detect faces
        long startTime = System.currentTimeMillis();
        DetectedObjects detectedFaces = detectionService.detectFaces(image);
        long detectionTime = System.currentTimeMillis() - startTime;
        
        logger.info("Detected {} faces in {} ms", detectedFaces.getNumberOfObjects(), detectionTime);
        
        // Save detection visualization if directory is provided
        if (visualizationDir.isPresent()) {
            try {
                Image detectionImage = image.duplicate();
                detectionImage.drawBoundingBoxes(detectedFaces);
                Path detectionPath = visualizationDir.get().resolve(filePrefix + "_detection.png");
                detectionImage.save(Files.newOutputStream(detectionPath), "png");
                logger.info("Saved detection visualization to: {}", detectionPath);
            } catch (IOException e) {
                logger.warn("Failed to save detection visualization: {}", e.getMessage());
            }
        }
        
        // Step 2 & 3: Align and recognize each face
        for (int i = 0; i < detectedFaces.getNumberOfObjects(); i++) {
            // Extract BoundingBox from DetectedObject
            DetectedObject detectedObj = detectedFaces.item(i);
            BoundingBox bbox = detectedObj.getBoundingBox();
            // Skip if no landmarks available
            if (!(bbox instanceof Landmark)) {
                logger.warn("DetectedObject #{} has no landmarks, skipping", i);
                continue;
            }
            // Safe to cast to Landmark
            Landmark faceLandmark = (Landmark) bbox;
            String facePrefix = filePrefix + "_" + i;
            
            try {
                // Align face
                startTime = System.currentTimeMillis();
                
                Image alignedFace = faceAligner.alignFaceWithVisualization(
                        image, 
                        faceLandmark, 
                        visualizationDir,
                        Optional.of(facePrefix));
                
                long alignmentTime = System.currentTimeMillis() - startTime;
                logger.info("Face {} aligned in {} ms", i, alignmentTime);
                
                // Check if alignment was successful
                if (alignedFace == null) {
                    logger.error("Alignment failed for face {}", i);
                    continue;
                }
                
                // Generate embedding
                startTime = System.currentTimeMillis();
                
                float[] embedding = recognitionService.getFaceEmbedding(alignedFace);
                long recognitionTime = System.currentTimeMillis() - startTime;
                
                logger.info("Processed face {}: alignment={}ms, recognition={}ms", 
                            i, alignmentTime, recognitionTime);
                
                // Create recognized face object
                RecognizedFace recognizedFace = new RecognizedFace(faceLandmark, embedding, alignedFace);
                recognizedFaces.add(recognizedFace);
                
                // Save processed face
                if (visualizationDir.isPresent()) {
                    try {
                        Path processedPath = visualizationDir.get().resolve(facePrefix + "_processed.png");
                        alignedFace.save(Files.newOutputStream(processedPath), "png");
                        logger.info("Saved processed face to: {}", processedPath);
                    } catch (IOException e) {
                        logger.warn("Failed to save processed face: {}", e.getMessage());
                    }
                }
                
            } catch (Exception e) {
                logger.error("Failed to process face {}: {}", i, e.getMessage());
                // Continue with next face
            }
        }
        
        logger.info("Pipeline completed: detected={}, recognized={}", 
                  detectedFaces.getNumberOfObjects(), recognizedFaces.size());
        
        return recognizedFaces;
    }
    
    /**
     * Compare a face embedding against a reference embedding.
     *
     * @param faceEmbedding The face embedding to compare
     * @param referenceEmbedding The reference embedding to compare against
     * @return Similarity score between 0 and 1, where 1 is identical
     */
    public double compareFaces(float[] faceEmbedding, float[] referenceEmbedding) {
        return recognitionService.compareFaces(faceEmbedding, referenceEmbedding);
    }
    
    @Override
    public void close() {
        try {
            if (detectionService instanceof AutoCloseable) {
                ((AutoCloseable) detectionService).close();
            }
        } catch (Exception e) {
            logger.warn("Error closing detection service: {}", e.getMessage());
        }
        
        try {
            if (recognitionService instanceof AutoCloseable) {
                ((AutoCloseable) recognitionService).close();
            }
        } catch (Exception e) {
            logger.warn("Error closing recognition service: {}", e.getMessage());
        }
    }
}
