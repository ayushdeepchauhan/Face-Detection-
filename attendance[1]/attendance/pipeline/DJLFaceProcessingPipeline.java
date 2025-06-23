package com.attendance.pipeline;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.output.DetectedObjects;
import com.attendance.exception.ModelException;
import com.attendance.facedetection.FaceDetectionService;
import com.attendance.facealignment.DJLFaceAligner;
import com.attendance.facerecogntion.FaceRecognitionService;
import com.attendance.model.recognition.DJLRecognizedFace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A version of the face processing pipeline that works directly with DJL's native detection types.
 * This pipeline eliminates the need for type conversion between detection and alignment stages.
 */
public class DJLFaceProcessingPipeline implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(DJLFaceProcessingPipeline.class);
    
    private final FaceDetectionService detectionService;
    private final DJLFaceAligner faceAligner;
    private final FaceRecognitionService recognitionService;
    private final Optional<Path> visualizationDir;
    
    /**
     * Creates a new processing pipeline with the specified components.
     *
     * @param detectionService Face detection service
     * @param faceAligner Face alignment service that works with DJL detection outputs
     * @param recognitionService Face recognition service
     */
    public DJLFaceProcessingPipeline(
            FaceDetectionService detectionService,
            DJLFaceAligner faceAligner,
            FaceRecognitionService recognitionService) {
        this(detectionService, faceAligner, recognitionService, Optional.empty());
    }
    
    /**
     * Creates a new processing pipeline with the specified components and visualization directory.
     *
     * @param detectionService Face detection service
     * @param faceAligner Face alignment service that works with DJL detection outputs
     * @param recognitionService Face recognition service
     * @param visualizationDir Optional directory to save visualization images
     */
    public DJLFaceProcessingPipeline(
            FaceDetectionService detectionService,
            DJLFaceAligner faceAligner,
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
    public List<DJLRecognizedFace> processImage(Image image) throws ModelException {
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
    public List<DJLRecognizedFace> processImage(Image image, String filePrefix) throws ModelException {
        if (image == null) {
            throw new IllegalArgumentException("Input image cannot be null");
        }
        
        List<DJLRecognizedFace> recognizedFaces = new ArrayList<>();
        
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
            // Get the detected object directly
            DetectedObjects.DetectedObject detectedObj = detectedFaces.item(i);
            String facePrefix = filePrefix + "_" + i;
            
            try {
                // Align face - no need for type checking or casting
                startTime = System.currentTimeMillis();
                
                Image alignedFace = faceAligner.alignFaceWithVisualization(
                        image, 
                        detectedObj, 
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
                
                // Create recognized face object - using original bounding box for reference
                DJLRecognizedFace recognizedFace = new DJLRecognizedFace(detectedObj.getBoundingBox(), embedding, alignedFace);
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
                logger.error("Exception details:", e);
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
     * @return Similarity score (higher means more similar)
     */
    public double compareFaces(float[] faceEmbedding, float[] referenceEmbedding) {
        return recognitionService.compareFaces(faceEmbedding, referenceEmbedding);
    }
    
    @Override
    public void close() throws Exception {
        // Close all services if they are AutoCloseable
        if (detectionService instanceof AutoCloseable) {
            ((AutoCloseable) detectionService).close();
        }
        
        if (recognitionService instanceof AutoCloseable) {
            ((AutoCloseable) recognitionService).close();
        }
    }
}
