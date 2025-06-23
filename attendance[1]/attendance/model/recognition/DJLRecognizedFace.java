package com.attendance.model.recognition;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.output.BoundingBox;
import ai.djl.modality.cv.output.Landmark;

public class DJLRecognizedFace {
    private final BoundingBox boundingBox;
    private final float[] embedding;
    private final Image alignedFace;
    private double confidence;

    public DJLRecognizedFace(BoundingBox boundingBox, float[] embedding, Image alignedFace) {
        this.boundingBox = boundingBox;
        this.embedding = embedding;
        this.alignedFace = alignedFace;
        this.confidence = 0.0;
    }

    public BoundingBox getBoundingBox() { return boundingBox; }
    public float[] getEmbedding() { return embedding; }
    public Image getAlignedFace() { return alignedFace; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public RecognizedFace toLegacyRecognizedFace() {
        if (boundingBox instanceof Landmark) {
            return new RecognizedFace(
                (Landmark) boundingBox,
                embedding,
                alignedFace
            );
        }
        return null;
    }
}
