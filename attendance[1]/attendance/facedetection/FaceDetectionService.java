package com.attendance.facedetection;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.output.DetectedObjects;
import com.attendance.exception.ModelException;

/**
 * Interface defining the contract for face detection services in the AutoAttend system.
 */
public interface FaceDetectionService {
    
    /**
     * Detects faces in the provided image.
     *
     * @param image The input image to process
     * @return DetectedObjects containing the locations and confidence scores of detected faces
     * @throws ModelException if face detection fails
     */
    DetectedObjects detectFaces(Image image) throws ModelException;
    
    /**
     * Sets the confidence threshold for face detection.
     * Faces with confidence scores below this threshold will be filtered out.
     *
     * @param threshold Confidence threshold value between 0 and 1
     * @throws IllegalArgumentException if threshold is not between 0 and 1
     */
    void setConfidenceThreshold(double threshold);
    
    /**
     * Gets the current confidence threshold value.
     *
     * @return Current confidence threshold
     */
    double getConfidenceThreshold();
    
    /**
     * Releases any resources held by the service.
     * Should be called when the service is no longer needed.
     */
    void close();
}
