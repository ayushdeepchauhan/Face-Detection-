package com.attendance.debug;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.BoundingBox;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.Landmark;
import ai.djl.modality.cv.output.Point;
import com.attendance.facedetection.RetinaFaceDetection;
import com.attendance.facealignment.FaceAligner;
import com.attendance.facealignment.StandardFaceAligner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.BasicStroke;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * Test class for extracting and visualizing facial landmarks.
 * This class demonstrates the landmark extraction process and visualizes the results.
 * It can run in two modes: simple test mode or detailed diagnostic mode.
 */
public class LandmarkExtractionTest {
    private static final Logger logger = LoggerFactory.getLogger(LandmarkExtractionTest.class);
    private static Path outputDir;
    private static PrintWriter diagnosticLog;
    private static boolean diagnosticMode = false;

    public static void main(String[] args) {
        // Check if diagnostic mode is enabled
        if (args.length > 0 && args[0].equalsIgnoreCase("diagnostic")) {
            diagnosticMode = true;
        }
        
        RetinaFaceDetection faceDetection = null;
        StandardFaceAligner standardAligner = null;
        
        try {
            // Create output directory
            outputDir = Paths.get("landmark_extraction_test");
            Files.createDirectories(outputDir);
            
            // Setup diagnostic logging if enabled
            if (diagnosticMode) {
                Path logPath = outputDir.resolve("landmark_extraction_diagnostic.txt");
                diagnosticLog = new PrintWriter(new BufferedWriter(new FileWriter(logPath.toFile())));
                logMessage("LANDMARK EXTRACTION DIAGNOSTIC LOG");
                logMessage("=====================================");
                logMessage("Started at: " + java.time.LocalDateTime.now());
                logMessage("");
            }
            
            // Write a marker file to confirm execution
            Path markerFile = outputDir.resolve("test_started.txt");
            Files.write(markerFile, "Test started\n".getBytes());
            
            // Log where output files will be
            logger.info("Output files will be saved to: {}", outputDir.toAbsolutePath());
            logMessage("Output directory: " + outputDir.toAbsolutePath());
            
            // Load test image
            Path imagePath = findTestImage();
            if (imagePath == null) {
                logError("No suitable test image found");
                return;
            }
            
            // Load the image
            logger.info("Loading image from: {}", imagePath);
            logMessage("Test image: " + imagePath);
            Image image = ImageFactory.getInstance().fromFile(imagePath);
            logMessage("Image loaded: " + image.getWidth() + "x" + image.getHeight());
            
            // Save original image
            Path originalPath = outputDir.resolve("original.png");
            image.save(Files.newOutputStream(originalPath), "png");
            logger.info("Original image saved to: {}", originalPath);
            
            // Initialize face detection and alignment
            logger.info("Initializing face detection and alignment...");
            logMessage("\nFACE DETECTION");
            logMessage("--------------");
            faceDetection = new RetinaFaceDetection();
            standardAligner = new StandardFaceAligner();
            
            // Detect faces
            logger.info("Detecting faces...");
            DetectedObjects detectedFaces = faceDetection.detectFaces(image);
            int faceCount = detectedFaces.getNumberOfObjects();
            logger.info("Detected {} faces", faceCount);
            logMessage("Detected " + faceCount + " faces");
            
            if (faceCount == 0) {
                logError("No faces detected in the image");
                return;
            }
            
            // Save image with detection boxes
            Path detectionPath = outputDir.resolve("detection.png");
            Image detectionImage = image.duplicate();
            detectionImage.drawBoundingBoxes(detectedFaces);
            detectionImage.save(Files.newOutputStream(detectionPath), "png");
            logger.info("Detection results saved to: {}", detectionPath);
            
            // Extract and visualize landmarks for each face
            // Convert DJL Image to BufferedImage for drawing
            BufferedImage originalBI = ImageIO.read(Files.newInputStream(originalPath));
            BufferedImage landmarkBI = new BufferedImage(
                    originalBI.getWidth(), originalBI.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = landmarkBI.createGraphics();
            g.drawImage(originalBI, 0, 0, null);
            
            // Draw landmarks
            g.setColor(Color.RED);
            g.setStroke(new BasicStroke(3));
            
            int facesWithLandmarks = 0;
            
            for (int i = 0; i < faceCount; i++) {
                if (diagnosticMode) {
                    logMessage("\nFACE #" + i);
                    logMessage("-------");
                }
                
                DetectedObjects.DetectedObject detected = detectedFaces.item(i);
                BoundingBox bbox = detected.getBoundingBox();
                
                if (diagnosticMode) {
                    logMessage("Class: " + detected.getClassName());
                    logMessage("Confidence: " + detected.getProbability());
                    logMessage("Bounding Box Type: " + bbox.getClass().getSimpleName());
                }
                
                if (!(bbox instanceof Landmark)) {
                    logError("Face #" + i + " does not have landmarks");
                    continue;
                }
                
                facesWithLandmarks++;
                Landmark landmark = (Landmark) bbox;
                
                if (diagnosticMode) {
                    logMessage("\nLANDMARK EXTRACTION");
                    logMessage("-------------------");
                }
                
                // Use StandardFaceAligner's method to get keypoints
                List<Point> points = standardAligner.getKeypointsFromLandmark(landmark);
                
                if (diagnosticMode) {
                    logMessage("Extracted " + points.size() + " points from landmark");
                    for (int j = 0; j < points.size(); j++) {
                        Point p = points.get(j);
                        logMessage(String.format("  point %d: x=%.3f, y=%.3f", j, p.getX(), p.getY()));
                    }
                }
                
                // Draw each landmark point
                for (Point p : points) {
                    int x = (int) p.getX();
                    int y = (int) p.getY();
                    g.fillOval(x - 3, y - 3, 6, 6);
                }
                
                // Test alignment with StandardFaceAligner
                // (which internally uses AffineFaceAligner for the transformation)
                if (diagnosticMode) {
                    logMessage("\nFACE ALIGNMENT TEST");
                    logMessage("------------------");
                }
                testFaceAlignment(standardAligner, image, landmark, i);
            }
            
            g.dispose();
            
            // Save landmark visualization
            Path landmarkPath = outputDir.resolve("landmarks.png");
            ImageIO.write(landmarkBI, "PNG", landmarkPath.toFile());
            logger.info("Landmark visualization saved to: {}", landmarkPath);
            
            if (facesWithLandmarks == 0) {
                logError("No faces with landmarks were found");
            } else {
                logger.info("Landmark extraction test completed successfully with {} faces", facesWithLandmarks);
            }
            
            if (diagnosticMode) {
                logMessage("\nDiagnostic completed at: " + java.time.LocalDateTime.now());
            }
            
        } catch (Exception e) {
            logger.error("Error in landmark extraction test", e);
            if (diagnosticMode && diagnosticLog != null) {
                logMessage("\nFATAL ERROR: " + e.getMessage());
                e.printStackTrace(diagnosticLog);
            } else {
                e.printStackTrace();
            }
        } finally {
            // Clean up resources
            if (faceDetection != null) {
                try {
                    faceDetection.close();
                } catch (Exception e) {
                    logger.warn("Error closing face detection service: {}", e.getMessage());
                }
            }
            if (diagnosticLog != null) {
                diagnosticLog.close();
            }
        }
    }
    
    /**
     * Find a suitable test image in the resources directory.
     */
    private static Path findTestImage() {
        // First try the specific test image
        Path imagePath = Paths.get("src\\main\\resources\\Images\\Aman.jpg");
        if (Files.exists(imagePath)) {
            logMessage("Using specified test image: " + imagePath);
            return imagePath;
        }
        
        try {
            // Look for any image files in the resources directory
            Path resourcesDir = Paths.get("src/main/resources");
            if (Files.exists(resourcesDir) && Files.isDirectory(resourcesDir)) {
                // Find first image file
                Optional<Path> firstImage = Files.list(resourcesDir)
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase();
                            return name.endsWith(".jpg") || name.endsWith(".jpeg") || 
                                   name.endsWith(".png") || name.endsWith(".bmp");
                        })
                        .findFirst();
                
                if (firstImage.isPresent()) {
                    Path imagePath2 = firstImage.get();
                    logMessage("Using alternative image: " + imagePath2);
                    return imagePath2;
                }
            }
        } catch (Exception e) {
            logError("Error finding test image: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Test face alignment using StandardFaceAligner, which internally uses AffineFaceAligner
     * for the affine transformation part of the alignment process.
     */
    private static void testFaceAlignment(FaceAligner aligner, 
                                   Image image, Landmark landmark, int faceIndex) {
        try {
            // Align face using the FaceAligner interface
            logger.info("Aligning face #{} with StandardFaceAligner...", faceIndex);
            
            long startTime = System.currentTimeMillis();
            Image alignedFace = aligner.alignFace(image, landmark);
            long endTime = System.currentTimeMillis();
            
            if (diagnosticMode) {
                logMessage("Alignment completed in " + (endTime - startTime) + "ms");
                logMessage("Aligned face dimensions: " + alignedFace.getWidth() + "x" + alignedFace.getHeight());
            }
            
            // Save aligned face
            Path alignedPath = outputDir.resolve("aligned_face_" + faceIndex + ".png");
            alignedFace.save(Files.newOutputStream(alignedPath), "png");
            logger.info("Aligned face saved to: {}", alignedPath);
            
        } catch (Exception e) {
            logError("Error aligning face: " + e.getMessage());
            if (diagnosticMode && diagnosticLog != null) {
                e.printStackTrace(diagnosticLog);
            }
        }
    }
    
    /**
     * Log a message to both the logger and the diagnostic log file if in diagnostic mode
     */
    private static void logMessage(String message) {
        if (diagnosticMode && diagnosticLog != null) {
            diagnosticLog.println(message);
            diagnosticLog.flush();
        }
    }
    
    /**
     * Log an error message to both the logger and the diagnostic log file
     */
    private static void logError(String message) {
        logger.error(message);
        if (diagnosticMode && diagnosticLog != null) {
            diagnosticLog.println("ERROR: " + message);
            diagnosticLog.flush();
        }
    }
}
