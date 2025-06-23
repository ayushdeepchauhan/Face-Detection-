package com.attendance.model.recognition;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.output.Landmark;

public class RecognizedFace {
    private final Landmark landmark;
    private final float[] embedding;
    private final Image alignedFace;
    private double confidence;

    public RecognizedFace(Landmark landmark, float[] embedding, Image alignedFace) {
        this.landmark = landmark;
        this.embedding = embedding;
        this.alignedFace = alignedFace;
        this.confidence = 0.0;
    }

    public Landmark getLandmark() { return landmark; }
    public float[] getEmbedding() { return embedding; }
    public Image getAlignedFace() { return alignedFace; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
}
