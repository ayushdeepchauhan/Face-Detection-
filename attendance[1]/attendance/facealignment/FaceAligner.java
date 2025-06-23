package com.attendance.facealignment;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.output.Landmark;
import com.attendance.exception.ModelException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Interface for face alignment operations in the AutoAttend system.
 * Face alignment is a crucial preprocessing step between face detection and recognition
 * that normalizes facial orientation and size for improved recognition accuracy.
 */
public interface FaceAligner {
    
    /**
     * Aligns a face detected in an image based on facial landmarks.
     *
     * @param originalImage The original image containing the face
     * @param faceLandmark The landmark containing facial points from detection
     * @return Aligned face image ready for recognition
     * @throws ModelException if alignment fails
     */
    Image alignFace(Image originalImage, Landmark faceLandmark) throws ModelException;
    
    /**
     * Aligns a face and optionally saves visualization files to the specified directory.
     *
     * @param originalImage The original image containing the face
     * @param faceLandmark The landmark containing facial points from detection
     * @param outputDir Optional directory to save visualization images
     * @param filePrefix Optional prefix for saved image files
     * @return Aligned face image ready for recognition
     * @throws ModelException if alignment fails
     */
    Image alignFaceWithVisualization(
            Image originalImage,
            Landmark faceLandmark,
            Optional<Path> outputDir,
            Optional<String> filePrefix) throws ModelException;
}
