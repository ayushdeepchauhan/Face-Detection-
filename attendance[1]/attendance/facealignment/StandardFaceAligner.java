package com.attendance.facealignment;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.Landmark;
import ai.djl.modality.cv.output.Point;
import ai.djl.modality.cv.output.Rectangle;
import com.attendance.exception.ModelException;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standard implementation of face alignment based on facial landmarks.
 * This implementation:
 * 1. Detects eye positions from landmarks
 * 2. Calculates rotation angle to align eyes horizontally
 * 3. Scales the face to a standard size
 * 4. Crops and centers the face for recognition
 */
public class StandardFaceAligner implements FaceAligner {
    private static final Logger logger = LoggerFactory.getLogger(StandardFaceAligner.class);
    
    private final int targetWidth;
    private final int targetHeight;
    private final double leftEyePositionX;
    private final double rightEyePositionX;
    private final double eyePositionY;
    private final double scaleFactor;
    
    /**
     * Creates a StandardFaceAligner with default parameters suitable for FaceNet.
     */
    public StandardFaceAligner() {
        // Default values: include padding (1.5x) to avoid black corners
        this(160, 160, 0.35, 0.65, 0.4, 1.5);
    }
    
    /**
     * Creates a StandardFaceAligner with custom parameters.
     *
     * @param targetWidth Width of the aligned output face
     * @param targetHeight Height of the aligned output face
     * @param leftEyePositionX Desired left eye position in the output (0-1)
     * @param rightEyePositionX Desired right eye position in the output (0-1)
     * @param eyePositionY Desired eye vertical position in the output (0-1)
     * @param scaleFactor Scale factor for the face region (>1 adds margins)
     */
    public StandardFaceAligner(int targetWidth, int targetHeight, 
                              double leftEyePositionX, double rightEyePositionX,
                              double eyePositionY, double scaleFactor) {
        if (targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("Target dimensions must be positive");
        }
        if (leftEyePositionX >= rightEyePositionX) {
            throw new IllegalArgumentException("Left eye position must be less than right eye position");
        }
        if (leftEyePositionX < 0 || rightEyePositionX > 1 || eyePositionY < 0 || eyePositionY > 1) {
            throw new IllegalArgumentException("Eye positions must be between 0 and 1");
        }
        if (scaleFactor <= 0) {
            throw new IllegalArgumentException("Scale factor must be positive");
        }
        
        this.targetWidth = targetWidth;
        this.targetHeight = targetHeight;
        this.leftEyePositionX = leftEyePositionX;
        this.rightEyePositionX = rightEyePositionX;
        this.eyePositionY = eyePositionY;
        this.scaleFactor = scaleFactor;
    }
    
    @Override
    public Image alignFace(Image originalImage, Landmark faceLandmark) throws ModelException {
        if (originalImage == null) {
            throw new IllegalArgumentException("Original image cannot be null");
        }
        if (faceLandmark == null) {
            throw new IllegalArgumentException("Face landmark cannot be null");
        }
        
        try {
            // Get face position from the landmark (convert normalized coords to pixel values)
            Rectangle rect = (Rectangle) faceLandmark;
            double imgWidth = originalImage.getWidth();
            double imgHeight = originalImage.getHeight();
            double x = rect.getX() * imgWidth;
            double y = rect.getY() * imgHeight;
            double faceWidth = rect.getWidth() * imgWidth;
            double faceHeight = rect.getHeight() * imgHeight;
            
            // Extract points from the landmark
            List<Point> keypoints = getKeypointsFromLandmark(faceLandmark);
            
            if (keypoints.size() < 2) {
                throw new ModelException("Need at least 2 keypoints (eyes) for alignment");
            }
            
            // RetinaFace returns 5 points in this order:
            // 0: left eye, 1: right eye, 2: nose, 3: left mouth corner, 4: right mouth corner
            Point leftEye, rightEye;
            
            if (keypoints.size() >= 5) {
                // Full 5-point facial landmarks from RetinaFace
                leftEye = keypoints.get(0);
                rightEye = keypoints.get(1);
                logger.info("Using 5-point landmarks for alignment: left eye ({}, {}), right eye ({}, {})", 
                        leftEye.getX(), leftEye.getY(), rightEye.getX(), rightEye.getY());
            } else {
                // Fallback to the first two points (our synthetic eyes)
                leftEye = keypoints.get(0);
                rightEye = keypoints.get(1);
                logger.info("Using fallback 2-point landmarks for alignment");
            }
            
            // Ensure left eye is actually to the left of right eye
            if (leftEye.getX() > rightEye.getX()) {
                logger.info("Swapping eye points - left eye was to the right of right eye");
                Point temp = leftEye;
                leftEye = rightEye;
                rightEye = temp;
            }
            
            // Calculate margins to add around the face
            double margin = Math.max(faceWidth, faceHeight) * (scaleFactor - 1.0) / 2.0;
            double extractX = Math.max(0, x - margin);
            double extractY = Math.max(0, y - margin);
            double extractWidth = Math.min(originalImage.getWidth() - extractX, faceWidth + 2 * margin);
            double extractHeight = Math.min(originalImage.getHeight() - extractY, faceHeight + 2 * margin);
            
            // Extract face region from original image
            BufferedImage originalBuffered = (BufferedImage) originalImage.getWrappedImage();
            BufferedImage faceRegion = originalBuffered.getSubimage(
                    (int) extractX, (int) extractY, (int) extractWidth, (int) extractHeight);
            
            // Adjust eye coordinates relative to the extracted region
            Point leftEyeRelative = new Point(
                    leftEye.getX() - extractX,
                    leftEye.getY() - extractY
            );
            Point rightEyeRelative = new Point(
                    rightEye.getX() - extractX,
                    rightEye.getY() - extractY
            );
            
            // Delegate affine warp to AffineFaceAligner//

            AffineFaceAligner warpAligner = new AffineFaceAligner(
                    targetWidth, targetHeight,
                    leftEyePositionX, rightEyePositionX,
                    eyePositionY);
            BufferedImage warped = warpAligner.warp(
                    faceRegion,
                    new Point(leftEyeRelative.getX(), leftEyeRelative.getY()),
                    new Point(rightEyeRelative.getX(), rightEyeRelative.getY()));
            return ImageFactory.getInstance().fromImage(warped);
            
        } catch (Exception e) {
            throw new ModelException("Failed to align face: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Image alignFaceWithVisualization(
            Image originalImage, 
            Landmark faceLandmark,
            Optional<Path> outputDir,
            Optional<String> filePrefix) throws ModelException {
        
        // First, align the face normally
        Image alignedFace = alignFace(originalImage, faceLandmark);
        
        // Save visualization images if output directory is provided
        if (outputDir.isPresent() && alignedFace != null) {
            try {
                String prefix = filePrefix.orElse("face");
                Path dir = outputDir.get();
                Files.createDirectories(dir);
                
                // Save aligned face
                Path alignedPath = dir.resolve(prefix + "_aligned.png");
                alignedFace.save(Files.newOutputStream(alignedPath), "png");
                logger.info("Saved aligned face to: {}", alignedPath);
            } catch (Exception e) {
                logger.warn("Failed to save visualization: {}", e.getMessage());
            }
        }
        
        return alignedFace;
    }
    
    /**
     * Extract keypoints from a Landmark using reflection to handle different implementations.
     */
    public List<Point> getKeypointsFromLandmark(Landmark landmark) throws ModelException {
        List<Point> keypoints = new ArrayList<>();
        String[] methodNames = {"getPoints", "getJoints", "getKeyPoints", "getLandmarks"};
        // Try each method name to retrieve list of points
        for (String methodName : methodNames) {
            try {
                Method method = landmark.getClass().getDeclaredMethod(methodName);
                method.setAccessible(true);
                Object result = method.invoke(landmark);
                if (result instanceof List) {
                    for (Object item : (List<?>) result) {
                        if (item instanceof Point) {
                            keypoints.add((Point) item);
                        }
                    }
                    if (!keypoints.isEmpty()) {
                        return keypoints;
                    }
                }
            } catch (Exception ignored) {
                // method not present or invocation failed; try next
            }
        }
        // Fallback: reflect on private 'points' field
        try {
            java.lang.reflect.Field fld = landmark.getClass().getDeclaredField("points");
            fld.setAccessible(true);
            Object result = fld.get(landmark);
            if (result instanceof List) {
                for (Object item : (List<?>) result) {
                    if (item instanceof Point) {
                        keypoints.add((Point) item);
                    }
                }
                if (!keypoints.isEmpty()) {
                    return keypoints;
                }
            }
        } catch (Exception ignored) {
            // no 'points' field or inaccessible
        }
        // Final fallback: synthetic eyes from bounding box
        if (landmark instanceof Rectangle) {
            Rectangle rect = (Rectangle) landmark;
            double x = rect.getX();
            double y = rect.getY();
            double width = rect.getWidth();
            double height = rect.getHeight();
            keypoints.add(new Point(x + width * 0.33, y + height * 0.4));
            keypoints.add(new Point(x + width * 0.67, y + height * 0.4));
            logger.info("Using synthetic eye positions from bounding box");
            return keypoints;
        }
        throw new ModelException("Failed to extract or synthesize keypoints from landmark");
    }
}
