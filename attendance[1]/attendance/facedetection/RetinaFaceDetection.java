  package com.attendance.facedetection;

import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.TranslateException;

import com.attendance.config.ModelConfig;
import com.attendance.exception.ModelException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Implementation of face detection service using RetinaFace model.
 * This class is responsible for detecting faces in images using the RetinaFace deep learning model.
 */
public class RetinaFaceDetection implements FaceDetectionService, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(RetinaFaceDetection.class);
    
    private ZooModel<Image, DetectedObjects> model;
    private Predictor<Image, DetectedObjects> predictor;
    private double confidenceThreshold;
    private final double[] variance = {0.1, 0.2};
    private final int topK = 5000;
    private final int[][] scales = {{16, 32}, {64, 128}, {256, 512}};
    private final int[] steps = {8, 16, 32};

    public RetinaFaceDetection() throws ModelException {
        this.confidenceThreshold = ModelConfig.getDetectionConfidenceThreshold();
        try {
            initializeModel();
        } catch (IOException | ai.djl.ModelException e) {
            throw new ModelException("Failed to initialize RetinaFace model", e);
        }
    }

    private void initializeModel() throws IOException, ai.djl.ModelException {
        FaceDetectionTranslator translator = new FaceDetectionTranslator(
            confidenceThreshold,
            ModelConfig.getNmsThreshold(),
            variance,
            topK,
            scales,
            steps
        );

        Criteria<Image, DetectedObjects> criteria = Criteria.builder()
            .setTypes(Image.class, DetectedObjects.class)
            .optModelUrls(ModelConfig.getRetinaFaceModelPath())
            .optModelName("retinaface")
            .optTranslator(translator)
            .optProgress(new ProgressBar())
            .optEngine("PyTorch")
            .build();

        model = criteria.loadModel();
        predictor = model.newPredictor();
        logger.info("RetinaFace model initialized successfully");
    }

    @Override
    public DetectedObjects detectFaces(Image image) throws ModelException {
        if (image == null) {
            throw new IllegalArgumentException("Input image cannot be null");
        }

        try {
            long startTime = System.currentTimeMillis();
            DetectedObjects detection = predictor.predict(image);
            long endTime = System.currentTimeMillis();
            
            logger.debug("Face detection completed in {} ms", endTime - startTime);
            return detection;
        } catch (TranslateException e) {
            throw new ModelException("Face detection failed", e);
        }
    }

    @Override
    public void setConfidenceThreshold(double threshold) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("Confidence threshold must be between 0 and 1");
        }
        this.confidenceThreshold = threshold;
    }

    @Override
    public double getConfidenceThreshold() {
        return this.confidenceThreshold;
    }

    @Override
    public void close() {
        if (predictor != null) {
            predictor.close();
        }
        if (model != null) {
            model.close();
        }
        logger.info("RetinaFace model resources released");
    }

    /**
     * Demo method to test face detection functionality.
     */
    public static void main(String[] args) throws ModelException {
        try (RetinaFaceDetection detector = new RetinaFaceDetection()) {
            Path facePath = Paths.get("C:\\Projects\\AutoAttend2\\src\\main\\resources\\Images\\emily.jpg");
            Image img = ImageFactory.getInstance().fromFile(facePath);
            DetectedObjects detectedFaces = detector.detectFaces(img);
            logger.info("Detected faces: {}", detectedFaces);
            saveBoundingBoxImage(img, detectedFaces);
        } catch (IOException e) {
            throw new ModelException("Failed to process image", e);
        }
    }

    /**
     * Saves an image with detected face bounding boxes drawn on it.
     */
    private static void saveBoundingBoxImage(Image img, DetectedObjects detection)
            throws IOException {
        Path outputDir = Paths.get("build/output");
        Files.createDirectories(outputDir);
        Path imagePath = outputDir.resolve("retinaface_detected.png");

        img.drawBoundingBoxes(detection);
        img.save(Files.newOutputStream(imagePath), "png");
        logger.info("Face detection result image saved at: {}", imagePath);
    }
}