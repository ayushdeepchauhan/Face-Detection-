package com.attendance.service;

import com.attendance.model.recognition.IdentifiedFace;
import ai.djl.modality.cv.Image;
import com.attendance.exception.IdentificationException;

import java.util.Optional;

/**
 * Interface for services that identify a face from an image against a database.
 */
public interface FaceIdentifier {

    /**
     * Processes an image, extracts the most prominent face embedding, 
     * compares it against stored embeddings, and returns the best match 
     * if found above a certain confidence threshold.
     *
     * @param image The input image containing a face.
     * @return An Optional containing an IdentifiedFace (student + confidence) if a match is found, 
     *         otherwise an empty Optional.
     * @throws IdentificationException if an error occurs during processing or comparison. 
     *         This could be due to model issues, database errors, or no faces being detected.
     */
    Optional<IdentifiedFace> identifyFace(Image image) throws IdentificationException;
}
