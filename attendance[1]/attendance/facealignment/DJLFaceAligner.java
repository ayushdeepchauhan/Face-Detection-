package com.attendance.facealignment;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.output.DetectedObjects;
import com.attendance.exception.ModelException;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Interface for face alignment operations that work directly with DJL detection outputs.
 * This interface is designed to work with the native output format from DJL's face detection
 * implementations like RetinaFace without requiring type conversion.
 */
public interface DJLFaceAligner {
    
    /**
     * Aligns a face detected by DJL-based detectors.
     *
     * @param originalImage The original image containing the face
     * @param detectedObject The detected object from DJL detector
     * @return Aligned face image ready for recognition
     * @throws ModelException if alignment fails
     */
    Image alignFace(Image originalImage, DetectedObjects.DetectedObject detectedObject) throws ModelException;
    
    /**
     * Aligns a face and optionally saves visualization files to the specified directory.
     *
     * @param originalImage The original image containing the face
     * @param detectedObject The detected object from DJL detector
     * @param outputDir Optional directory to save visualization images
     * @param filePrefix Optional prefix for saved image files
     * @return Aligned face image ready for recognition
     * @throws ModelException if alignment fails
     */
    Image alignFaceWithVisualization(
            Image originalImage,
            DetectedObjects.DetectedObject detectedObject,
            Optional<Path> outputDir,
            Optional<String> filePrefix) throws ModelException;
}
