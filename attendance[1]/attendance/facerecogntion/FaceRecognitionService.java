package com.attendance.facerecogntion;

import ai.djl.modality.cv.Image;
import com.attendance.exception.ModelException;

/**
 * Interface defining the contract for face recognition services in the AutoAttend system.
 */
public interface FaceRecognitionService {
    
    /**
     * Generates a face embedding vector for the given face image.
     *
     * @param face The face image to process (should be aligned and cropped)
     * @return float array containing the face embedding vector
     * @throws ModelException if embedding generation fails
     * @throws IllegalArgumentException if the input image is invalid
     */
    float[] getFaceEmbedding(Image face) throws ModelException;
    
    /**
     * Compares two face embeddings and returns a similarity score.
     *
     * @param embedding1 First face embedding
     * @param embedding2 Second face embedding
     * @return Similarity score between 0 and 1, where 1 indicates identical faces
     * @throws IllegalArgumentException if embeddings are null or of different lengths
     */
    double compareFaces(float[] embedding1, float[] embedding2);
    
    /**
     * Sets the similarity threshold for face matching.
     *
     * @param threshold Similarity threshold value between 0 and 1
     * @throws IllegalArgumentException if threshold is not between 0 and 1
     */
    void setSimilarityThreshold(double threshold);
    
    /**
     * Gets the current similarity threshold value.
     *
     * @return Current similarity threshold
     */
    double getSimilarityThreshold();
    
    /**
     * Releases any resources held by the service.
     * Should be called when the service is no longer needed.
     */
    void close();
}
