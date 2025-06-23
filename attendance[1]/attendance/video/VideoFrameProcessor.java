package com.attendance.video;

import ai.djl.modality.cv.Image;
import com.attendance.exception.ModelException;
import com.attendance.model.recognition.DJLRecognizedFace;
import com.attendance.pipeline.DJLFaceProcessingPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
// import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for processing video frames through the face detection and recognition pipeline.
 * This service handles frame skipping, processing intervals, and visualization.
 */
public class VideoFrameProcessor implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(VideoFrameProcessor.class);
    
    private final DJLFaceProcessingPipeline pipeline;
    private final Path visualizationDir;
    private final AtomicBoolean isRunning;
    private final AtomicInteger frameCounter;
    
    // Configuration parameters
    private final int skipFrames;
    private final long detectionIntervalMs;
    private final long recognitionIntervalMs;
    
    // Timestamps for interval-based processing
    private long lastDetectionTime;
    private long lastRecognitionTime;
    
    /**
     * Creates a new video frame processor with the specified pipeline and default settings.
     * 
     * @param pipeline the face processing pipeline to use
     * @throws IOException if the visualization directory cannot be created
     */
    public VideoFrameProcessor(DJLFaceProcessingPipeline pipeline) throws IOException {
        this(pipeline, com.attendance.config.VideoConfig.getRawProperties());
    }
    
    /**
     * Creates a new video frame processor with the specified pipeline and properties.
     * 
     * @param pipeline the face processing pipeline to use
     * @param props the properties containing processing settings
     * @throws IOException if the visualization directory cannot be created
     */
    public VideoFrameProcessor(DJLFaceProcessingPipeline pipeline, Properties props) throws IOException {
        this.pipeline = pipeline;
        this.isRunning = new AtomicBoolean(false);
        this.frameCounter = new AtomicInteger(0);
        
        // Load configuration from properties
        this.skipFrames = Integer.parseInt(props.getProperty("video.processing.skip.frames", "2"));
        this.detectionIntervalMs = Long.parseLong(props.getProperty("video.processing.detection.interval", "500"));
        this.recognitionIntervalMs = Long.parseLong(props.getProperty("video.processing.recognition.interval", "1000"));
        
        // Initialize timestamps
        this.lastDetectionTime = 0;
        this.lastRecognitionTime = 0;
        
        // Create visualization directory
        this.visualizationDir = Paths.get("output", "video_processing");
        Files.createDirectories(visualizationDir);
        
        logger.info("Created video frame processor: skipFrames={}, detectionInterval={}ms, recognitionInterval={}ms",
                skipFrames, detectionIntervalMs, recognitionIntervalMs);
    }
    
    /**
     * Starts the video frame processor.
     */
    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            logger.info("Starting video frame processor");
            frameCounter.set(0);
            lastDetectionTime = 0;
            lastRecognitionTime = 0;
        } else {
            logger.warn("Video frame processor already running");
        }
    }
    
    /**
     * Stops the video frame processor.
     */
    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            logger.info("Stopping video frame processor");
        }
    }
    
    /**
     * Processes a video frame through the face detection and recognition pipeline.
     * This method implements frame skipping and interval-based processing for performance.
     * 
     * @param frame the video frame to process
     * @return list of recognized faces, or empty list if no processing was performed
     * @throws ModelException if an error occurs during processing
     */
    public List<DJLRecognizedFace> processFrame(Image frame) throws ModelException {
        if (!isRunning.get() || frame == null) {
            return List.of();
        }
        
        // Increment frame counter
        int currentFrame = frameCounter.incrementAndGet();
        
        // Skip frames for performance
        if (currentFrame % (skipFrames + 1) != 0) {
            return List.of();
        }
        
        // Get current time
        long currentTime = System.currentTimeMillis();
        
        // Check if we should perform detection (based on interval)
        if (currentTime - lastDetectionTime < detectionIntervalMs) {
            return List.of();
        }
        
        // Update detection timestamp
        lastDetectionTime = currentTime;
        
        // Check if we should perform recognition (based on interval)
        boolean performRecognition = currentTime - lastRecognitionTime >= recognitionIntervalMs;
        if (performRecognition) {
            lastRecognitionTime = currentTime;
        }
        
        // Generate a timestamp for visualization
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        
        try {
            // Process the frame through the pipeline
            List<DJLRecognizedFace> recognizedFaces = pipeline.processImage(frame, "frame_" + timestamp);
            
            // Log processing results
            logger.debug("Processed frame {}: detected {} faces", currentFrame, recognizedFaces.size());
            
            // Save the processed frame with bounding boxes
            if (!recognizedFaces.isEmpty()) {
                try {
                    Image processedFrame = frame.duplicate();
                    // Draw bounding boxes with labels for each recognized face
java.awt.image.BufferedImage buffered = (java.awt.image.BufferedImage) processedFrame.getWrappedImage();
java.awt.Graphics2D g2d = buffered.createGraphics();
g2d.setStroke(new java.awt.BasicStroke(2.0f));
g2d.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 16));
for (com.attendance.model.recognition.DJLRecognizedFace face : recognizedFaces) {
    ai.djl.modality.cv.output.BoundingBox bbox = face.getBoundingBox();
    if (bbox != null && bbox.getBounds() != null) {
        ai.djl.modality.cv.output.Rectangle rect = bbox.getBounds();
        int x = (int) (rect.getX() * buffered.getWidth());
        int y = (int) (rect.getY() * buffered.getHeight());
        int w = (int) (rect.getWidth() * buffered.getWidth());
        int h = (int) (rect.getHeight() * buffered.getHeight());
        g2d.setColor(java.awt.Color.GREEN);
        g2d.drawRect(x, y, w, h);
        // Prepare label: name (if available) and confidence
        String label = String.format("Conf: %.2f", face.getConfidence());
        // If you have access to the name, append it here (for now, just confidence)
        g2d.setColor(java.awt.Color.YELLOW);
        g2d.drawString(label, x, y - 5);
    }
}
g2d.dispose();
                    
                    Path processedPath = visualizationDir.resolve("processed_" + timestamp + ".png");
                    processedFrame.save(Files.newOutputStream(processedPath), "png");
                    logger.debug("Saved processed frame to: {}", processedPath);
                } catch (IOException e) {
                    logger.warn("Failed to save processed frame: {}", e.getMessage());
                }
            }
            
            return recognizedFaces;
            
        } catch (Exception e) {
            logger.error("Error processing frame {}: {}", currentFrame, e.getMessage());
            return List.of();
        }
    }
    
    @Override
    public void close() throws Exception {
        stop();
    }
    
    /**
     * @return true if the video frame processor is running
     */
    public boolean isRunning() {
        return isRunning.get();
    }
    
    /**
     * @return the current frame counter
     */
    public int getFrameCounter() {
        return frameCounter.get();
    }
    
    // Configuration now handled by VideoConfig. Deprecated: loadProperties() removed.
}
