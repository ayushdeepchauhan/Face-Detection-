package com.attendance.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

import java.util.Properties;

/**
 * Centralized configuration management for the AutoAttend system.
 * Handles loading and providing access to model and system configurations.
 */
public class ModelConfig {
    private static final Logger logger = LoggerFactory.getLogger(ModelConfig.class);
    private static final Properties props = new Properties();

    
    private ModelConfig() {} // Prevent instantiation
    
    /**
     * Loads configuration from the specified file path.
     * Falls back to default values if file not found or loading fails.
     *
     * @param configPath Path to the configuration file
     */
    public static void loadConfig(String configPath) {
        // Always load all relevant domain-specific config files
        loadDomainConfig("config/face.properties");
        loadDomainConfig("config/recognition.properties");
        loadDomainConfig("config/image.properties");
        // Optionally, load a custom config file if provided (for overrides)
        if (configPath != null) {
            loadDomainConfig(configPath);
        }
    }

    private static void loadDomainConfig(String resourcePath) {
        try (InputStream input = ModelConfig.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input != null) {
                props.load(input);
                logger.info("Loaded config: {}", resourcePath);
            } else {
                logger.warn("Config file not found: {}", resourcePath);
            }
        } catch (IOException e) {
            logger.warn("Could not load config file: {}. Using defaults where necessary.", resourcePath, e);
        }
    }
    

    
    // Face Detection Configuration
    public static double getDetectionConfidenceThreshold() {
        return Double.parseDouble(props.getProperty("face.detection.confidence", "0.85"));
    }
    
    public static double getNmsThreshold() {
        return Double.parseDouble(props.getProperty("face.detection.nms", "0.45"));
    }
    
    public static String getRetinaFaceModelPath() {
        return props.getProperty("face.detection.model.path", "models/retinaface/retinaface.zip");
    }
    
    // Face Recognition Configuration
    public static String getFaceNetModelPath() {
        // Always return the directory path that works with SavedModel format
        return props.getProperty("face.recognition.model.path", "models/face_net");
    }
    
    public static double getSimilarityThreshold() {
        return Double.parseDouble(props.getProperty("face.recognition.similarity.threshold", "0.7"));
    }
    
    // Image Processing Configuration
    public static int getImageWidth() {
        return Integer.parseInt(props.getProperty("image.size.width", "160"));
    }
    
    public static int getImageHeight() {
        return Integer.parseInt(props.getProperty("image.size.height", "160"));
    }
}
