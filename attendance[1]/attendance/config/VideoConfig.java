package com.attendance.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Utility class for loading video-related configuration properties.
 * Looks for config/video.properties in the classpath.
 */
public class VideoConfig {
    private static final Logger logger = LoggerFactory.getLogger(VideoConfig.class);
    private static final String CONFIG_PATH = "config/video.properties";
    private static final Properties props = new Properties();

    static {
        try (InputStream is = VideoConfig.class.getClassLoader().getResourceAsStream(CONFIG_PATH)) {
            if (is != null) {
                props.load(is);
                logger.info("Loaded video configuration from {}", CONFIG_PATH);
            } else {
                logger.warn("{} not found in classpath, using defaults", CONFIG_PATH);
            }
        } catch (IOException e) {
            logger.warn("Failed to load video configuration, using defaults", e);
        }
    }

    private VideoConfig() {}

    public static int getCameraIndex() {
        return Integer.parseInt(props.getProperty("video.camera.index", "0"));
    }

    public static int getWidth() {
        return Integer.parseInt(props.getProperty("video.width", "640"));
    }

    public static int getCameraWidth() {
        return getWidth();
    }

    public static int getHeight() {
        return Integer.parseInt(props.getProperty("video.height", "480"));
    }

    public static int getCameraHeight() {
        return getHeight();
    }

    public static int getFps() {
        return Integer.parseInt(props.getProperty("video.fps", "30"));
    }

    public static int getCameraFps() {
        return getFps();
    }

    // Add other getters for additional video properties as needed
    public static Properties getRawProperties() {
        return props;
    }
}
