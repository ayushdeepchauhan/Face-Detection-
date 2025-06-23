package com.attendance.demo;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import com.attendance.facedetection.RetinaFaceDetection;
import com.attendance.facealignment.DJLStandardFaceAligner;
import com.attendance.facerecogntion.FaceNetRecognition;
import com.attendance.model.recognition.DJLRecognizedFace;
import com.attendance.pipeline.DJLFaceProcessingPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * Demo application for the DJL-native face processing pipeline.
 * This demonstrates using the pipeline with direct DJL object passing between stages.
 */
public class DJLPipelineDemo {
    private static final Logger logger = LoggerFactory.getLogger(DJLPipelineDemo.class);
    
    public static void main(String[] args) {
        // Default image path - can be overridden by command line argument
        String imagePath = "C:\\Projects\\AutoAttend2\\src\\main\\resources\\Images\\ayush_d.jpg";
        
        // Check if image path is provided as argument
        if (args.length > 0) {
            imagePath = args[0];
        }
        
        // Create visualization directory
        Path visualDir = Paths.get("output", "demo_djl");
        
        try {
            // Ensure directory exists
            Files.createDirectories(visualDir);
            
            logger.info("Loading image from: {}", imagePath);
            Image image = ImageFactory.getInstance().fromFile(Paths.get(imagePath));
            
            // Initialize all components 
            logger.info("Initializing pipeline components...");
            try (RetinaFaceDetection faceDetection = new RetinaFaceDetection();
                 FaceNetRecognition faceRecognition = new FaceNetRecognition()) {
                
                // Create DJL-specific aligner
                DJLStandardFaceAligner faceAligner = new DJLStandardFaceAligner();
                
                // Create pipeline with visualization output
                try (DJLFaceProcessingPipeline pipeline = new DJLFaceProcessingPipeline(
                        faceDetection,
                        faceAligner,
                        faceRecognition,
                        Optional.of(visualDir))) {
                
                    // Process image
                    logger.info("Processing image...");
                    List<DJLRecognizedFace> recognizedFaces = pipeline.processImage(image, "face");
                    
                    // Show results
                    logger.info("Face processing complete. Recognized {} faces.", recognizedFaces.size());
                    for (int i = 0; i < recognizedFaces.size(); i++) {
                        DJLRecognizedFace face = recognizedFaces.get(i);
                        logger.info("Face #{}: Bounding box: {}", i+1, face.getBoundingBox());
                        
                        // Compare each face with itself (just for demonstration)
                        double selfSimilarity = pipeline.compareFaces(face.getEmbedding(), face.getEmbedding());
                        logger.info("Face #{}: Self-similarity score: {}", i+1, selfSimilarity);
                        
                        // Compare with other faces if available
                        for (int j = 0; j < recognizedFaces.size(); j++) {
                            if (i != j) {
                                DJLRecognizedFace otherFace = recognizedFaces.get(j);
                                double similarity = pipeline.compareFaces(
                                        face.getEmbedding(), otherFace.getEmbedding());
                                logger.info("Face #{} vs Face #{}: Similarity score: {}", 
                                        i+1, j+1, similarity);
                            }
                        }
                    }
                }
                
                logger.info("Results have been saved to: {}", visualDir.toAbsolutePath());
            }
            
        } catch (Exception e) {
            logger.error("Error in pipeline demo", e);
            e.printStackTrace();
        }
    }
}
