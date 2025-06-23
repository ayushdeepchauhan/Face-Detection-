package com.attendance.demo;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.DetectedObjects;
import com.attendance.facedetection.FaceDetectionService;
import com.attendance.facedetection.RetinaFaceDetection;
import com.attendance.facealignment.DJLFaceAligner;
import com.attendance.facealignment.DJLStandardFaceAligner;
import com.attendance.facealignment.FaceAligner;
import com.attendance.facealignment.StandardFaceAligner;
import com.attendance.facerecogntion.FaceNetRecognition;
import com.attendance.facerecogntion.FaceRecognitionService;
import com.attendance.model.recognition.DJLRecognizedFace;
import com.attendance.model.recognition.RecognizedFace;
import com.attendance.pipeline.DJLFaceProcessingPipeline;
import com.attendance.pipeline.FaceProcessingPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Demonstration of the complete face processing pipeline.
 * This class shows how to use the detection-alignment-recognition pipeline
 * and visualizes the results at each stage.
 */
public class PipelineDemo {
    private static final Logger logger = LoggerFactory.getLogger(PipelineDemo.class);
    
    // Flag to control which implementation to use
    private static final boolean USE_DJL_NATIVE = true; // Set to false to use legacy implementation
    
    public static void main(String[] args) {
        String imagePath;
        
        // Use command-line argument if provided, otherwise use a default image
        if (args.length >= 1) {
            imagePath = args[0];
        } else {
            // Default image path
            imagePath = "src\\main\\resources\\Images\\ron.jpg";
            logger.info("No image path provided, using default: {}", imagePath);
        }
        
        try {
            // Create output directory for visualizations
            Path outputDir = Paths.get("build/output/pipeline_demo");
            Files.createDirectories(outputDir);
            
            // Load and save original image
            logger.info("Loading image: {}", imagePath);
            Image image = ImageFactory.getInstance().fromFile(Paths.get(imagePath));
            
            // Save original image
            Path originalPath = outputDir.resolve("original.png");
            image.save(Files.newOutputStream(originalPath), "png");
            
            if (USE_DJL_NATIVE) {
                // DJL-native pipeline (recommended)
                runDJLNativePipeline(image, outputDir);
            } else {
                // Legacy pipeline
                runLegacyPipeline(image, outputDir);
            }
            
            logger.info("Pipeline demo completed successfully");
            logger.info("Results saved to: {}", outputDir.toAbsolutePath());
            
        } catch (Exception e) {
            logger.error("Pipeline demo failed", e);
            e.printStackTrace();
        }
    }
    
    /**
     * Run the DJL-native pipeline implementation (recommended).
     */
    private static void runDJLNativePipeline(Image image, Path outputDir) throws Exception {
        logger.info("Using DJL-native pipeline (recommended)");
        
        // Initialize components
        logger.info("Initializing face detection model...");
        RetinaFaceDetection detectionService = new RetinaFaceDetection();
        
        logger.info("Initializing face aligner...");
        DJLFaceAligner faceAligner = new DJLStandardFaceAligner();
        
        logger.info("Initializing face recognition model...");
        FaceNetRecognition recognitionService = new FaceNetRecognition();
        
        // Create pipeline with visualization
        logger.info("Creating DJL-native processing pipeline...");
        try (DJLFaceProcessingPipeline pipeline = new DJLFaceProcessingPipeline(
                detectionService, faceAligner, recognitionService, Optional.of(outputDir))) {
            
            // Process image
            logger.info("Processing image through pipeline...");
            List<DJLRecognizedFace> recognizedFaces = pipeline.processImage(image, "face");
            
            logger.info("Recognized {} faces", recognizedFaces.size());
            
            // Save final result with all faces
            saveResultImageDJL(image, recognizedFaces, outputDir);
            
            // Save individual recognized faces with details
            for (int i = 0; i < recognizedFaces.size(); i++) {
                DJLRecognizedFace face = recognizedFaces.get(i);
                Path facePath = outputDir.resolve("face_" + i + "_processed.png");
                face.getAlignedFace().save(Files.newOutputStream(facePath), "png");
                
                logger.info("Face {}: Confidence={}", i, face.getConfidence());
            }
        }
    }
    
    /**
     * Run the legacy pipeline implementation.
     */
    private static void runLegacyPipeline(Image image, Path outputDir) throws Exception {
        logger.info("Using legacy pipeline");
        
        // Initialize components
        logger.info("Initializing face detection model...");
        FaceDetectionService detectionService = new RetinaFaceDetection();
        
        logger.info("Initializing face aligner...");
        FaceAligner faceAligner = new StandardFaceAligner();
        
        logger.info("Initializing face recognition model...");
        FaceRecognitionService recognitionService = new FaceNetRecognition();
        
        // Create pipeline with visualization
        logger.info("Creating legacy processing pipeline...");
        try (FaceProcessingPipeline pipeline = new FaceProcessingPipeline(
                detectionService, faceAligner, recognitionService, Optional.of(outputDir))) {
            
            // Process image
            logger.info("Processing image through pipeline...");
            List<RecognizedFace> recognizedFaces = pipeline.processImage(image, "face");
            
            logger.info("Recognized {} faces", recognizedFaces.size());
            
            // Save final result with all faces
            saveResultImageLegacy(image, recognizedFaces, outputDir);
            
            // Save individual recognized faces with details
            for (int i = 0; i < recognizedFaces.size(); i++) {
                RecognizedFace face = recognizedFaces.get(i);
                Path facePath = outputDir.resolve("face_" + i + "_processed.png");
                face.getAlignedFace().save(Files.newOutputStream(facePath), "png");
                
                logger.info("Face {}: Confidence={}", i, face.getConfidence());
            }
        }
    }
    
    /**
     * Save a result image with DJL recognized faces highlighted.
     */
    private static void saveResultImageDJL(Image image, List<DJLRecognizedFace> recognizedFaces, Path outputDir) 
            throws IOException {
        Image resultImage = image.duplicate();
        
        // Create a DetectedObjects from the recognized faces for visualization
        List<String> classNames = new ArrayList<>();
        List<Double> probabilities = new ArrayList<>();
        List<ai.djl.modality.cv.output.BoundingBox> boundingBoxes = new ArrayList<>();
        
        for (DJLRecognizedFace face : recognizedFaces) {
            classNames.add("Face");
            probabilities.add(face.getConfidence());
            boundingBoxes.add(face.getBoundingBox());
        }
        
        DetectedObjects detectedObjects = new DetectedObjects(classNames, probabilities, boundingBoxes);
        resultImage.drawBoundingBoxes(detectedObjects);
        
        Path resultPath = outputDir.resolve("result.png");
        resultImage.save(Files.newOutputStream(resultPath), "png");
        logger.info("Saved result image to: {}", resultPath);
    }
    
    /**
     * Save a result image with legacy recognized faces highlighted.
     */
    private static void saveResultImageLegacy(Image image, List<RecognizedFace> recognizedFaces, Path outputDir) 
            throws IOException {
        Image resultImage = image.duplicate();
        
        // Create a DetectedObjects from the recognized faces for visualization
        List<String> classNames = new ArrayList<>();
        List<Double> probabilities = new ArrayList<>();
        List<ai.djl.modality.cv.output.BoundingBox> boundingBoxes = new ArrayList<>();
        
        for (RecognizedFace face : recognizedFaces) {
            classNames.add("Face");
            probabilities.add(face.getConfidence());
            boundingBoxes.add(face.getLandmark());
        }
        
        DetectedObjects detectedObjects = new DetectedObjects(classNames, probabilities, boundingBoxes);
        resultImage.drawBoundingBoxes(detectedObjects);
        
        Path resultPath = outputDir.resolve("result.png");
        resultImage.save(Files.newOutputStream(resultPath), "png");
        logger.info("Saved result image to: {}", resultPath);
    }
}
