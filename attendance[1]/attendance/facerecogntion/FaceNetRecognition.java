package com.attendance.facerecogntion;

import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.TranslateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;

import com.attendance.config.ModelConfig;
import com.attendance.exception.ModelException;

public class FaceNetRecognition implements FaceRecognitionService, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(FaceNetRecognition.class);
    private ZooModel<Image, float[]> model;
    private Predictor<Image, float[]> predictor;
    private double similarityThreshold;

    public FaceNetRecognition() throws ModelException {
        this.similarityThreshold = ModelConfig.getSimilarityThreshold();
        try {
            initializeModel();
        } catch (IOException | ai.djl.ModelException e) {
            throw new ModelException("Failed to initialize FaceNet model", e);
        }
    }

    private void initializeModel() throws IOException, ai.djl.ModelException {
        String modelPath = ModelConfig.getFaceNetModelPath();
        logger.info("Loading FaceNet model from: {}", modelPath);

        Criteria<Image, float[]> criteria = Criteria.builder()
                .setTypes(Image.class, float[].class)
                .optModelPath(Paths.get(modelPath))
                .optEngine("TensorFlow")
                .optTranslator(new FaceNetTranslator())
                .optProgress(new ProgressBar())
                .build();

        model = ModelZoo.loadModel(criteria);
        predictor = model.newPredictor();
        logger.info("FaceNet model initialized successfully");
    }

    @Override
    public float[] getFaceEmbedding(Image face) throws ModelException {
        if (face == null) {
            throw new IllegalArgumentException("Input face image cannot be null");
        }

        try {
            return predictor.predict(face);
        } catch (TranslateException e) {
            throw new ModelException("Failed to generate face embedding", e);
        }
    }

    @Override
    public double compareFaces(float[] embedding1, float[] embedding2) {
        if (embedding1 == null || embedding2 == null) {
            throw new IllegalArgumentException("Face embeddings cannot be null");
        }
        if (embedding1.length != embedding2.length) {
            throw new IllegalArgumentException("Face embeddings must have the same length");
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            norm1 += embedding1[i] * embedding1[i];
            norm2 += embedding2[i] * embedding2[i];
        }

        double similarity = dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
        return Math.max(0.0, Math.min(1.0, (similarity + 1.0) / 2.0));
    }

    @Override
    public void setSimilarityThreshold(double threshold) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("Similarity threshold must be between 0 and 1");
        }
        this.similarityThreshold = threshold;
    }

    @Override
    public double getSimilarityThreshold() {
        return this.similarityThreshold;
    }

    @Override
    public void close() {
        if (predictor != null) {
            predictor.close();
        }
        if (model != null) {
            model.close();
        }
        logger.info("FaceNet model resources released");
    }

    public static void main(String[] args) {
        try (FaceNetRecognition recognizer = new FaceNetRecognition()) {
            logger.info("FaceNet model initialized successfully.");
        } catch (Exception e) {
            logger.error("An error occurred while initializing the FaceNet model", e);
        }
    }
}