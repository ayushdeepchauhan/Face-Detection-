package com.attendance.video;

import com.attendance.config.VideoConfig;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for capturing video frames from a webcam using JavaCV.
 * This service supports capturing from a single camera source.
 */
public class VideoCaptureService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(VideoCaptureService.class);
    
    private final FrameGrabber grabber;
    private final Java2DFrameConverter converter;
    private final AtomicBoolean isRunning;
    private final int cameraIndex;
    private final int width;
    private final int height;
    private final int fps;
    
    /**
     * Creates a new video capture service with default settings.
     * 
     * @throws FrameGrabber.Exception if the camera cannot be initialized
     */
    public VideoCaptureService() throws FrameGrabber.Exception {
        this(
            VideoConfig.getCameraIndex(),
            VideoConfig.getWidth(),
            VideoConfig.getHeight(),
            VideoConfig.getFps()
        );
        // List available camera devices to help with debugging
        listCameraDevices();
    }
    
    /**
     * Lists all available camera devices to help with debugging.
     */
    public static void listCameraDevices() {
        try {
            logger.info("Listing available camera devices:");
            
            // Try to get device information using OpenCVFrameGrabber
            for (int i = 0; i < 10; i++) { // Check first 10 possible camera indices
                try {
                    OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(i);
                    grabber.start();
                    Frame frame = grabber.grab();
                    if (frame != null) {
                        logger.info("Camera device #{}: Available ({}x{})", 
                                i, frame.imageWidth, frame.imageHeight);
                    } else {
                        logger.info("Camera device #{}: Available but returned null frame", i);
                    }
                    grabber.stop();
                    grabber.close();
                } catch (Exception e) {
                    // This is expected for non-existent cameras
                    logger.debug("Camera device #{}: Not available ({})", i, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to list camera devices: {}", e.getMessage());
        }
    }
    
    /**
     * Creates a new video capture service with custom settings.
     * 
     * @param cameraIndex the index of the camera to use (usually 0 for the first camera)
     * @param width the width of the captured frames
     * @param height the height of the captured frames
     * @param fps the target frames per second
     * @throws FrameGrabber.Exception if the camera cannot be initialized
     */
    public VideoCaptureService(int cameraIndex, int width, int height, int fps) throws FrameGrabber.Exception {
        this.cameraIndex = cameraIndex;
        this.width = width;
        this.height = height;
        this.fps = fps;
        
        this.grabber = new OpenCVFrameGrabber(cameraIndex);
        this.grabber.setImageWidth(width);
        this.grabber.setImageHeight(height);
        this.grabber.setFrameRate(fps);
        
        this.converter = new Java2DFrameConverter();
        this.isRunning = new AtomicBoolean(false);
        
        logger.info("Created video capture service: camera={}, resolution={}x{}, fps={}", 
                cameraIndex, width, height, fps);
    }
    
    
    /**
     * Starts the video capture service.
     * 
     * @throws FrameGrabber.Exception if the camera cannot be started
     */
    public void start() throws FrameGrabber.Exception {
        if (isRunning.compareAndSet(false, true)) {
            logger.info("Starting video capture from camera {} ({}x{} @ {} fps)", 
                    cameraIndex, width, height, fps);
            try {
                grabber.start();
                // Verify that the grabber is working by grabbing a test frame
                Frame testFrame = grabber.grab();
                if (testFrame != null && testFrame.image != null) {
                    logger.info("Camera initialized successfully. Frame size: {}x{}", 
                            testFrame.imageWidth, testFrame.imageHeight);
                } else {
                    logger.warn("Camera initialized but returned null test frame");
                }
            } catch (Exception e) {
                isRunning.set(false);
                logger.error("Failed to start camera: {}", e.getMessage());
                throw e;
            }
        } else {
            logger.warn("Video capture already running");
        }
    }
    
    /**
     * Captures a single frame from the camera.
     * 
     * @return the captured frame as a DJL Image, or null if no frame could be captured
     * @throws FrameGrabber.Exception if an error occurs during frame capture
     */
    public Image captureFrame() throws FrameGrabber.Exception {
        if (!isRunning.get()) {
            logger.warn("Cannot capture frame: video capture not running");
            return null;
        }
        
        Frame frame = grabber.grab();
        if (frame == null || frame.image == null) {
            logger.warn("Captured null frame");
            return null;
        }
        
        BufferedImage bufferedImage = converter.convert(frame);
        if (bufferedImage == null) {
            logger.warn("Failed to convert frame to BufferedImage");
            return null;
        }
        
        logger.debug("Captured frame: {}x{}", bufferedImage.getWidth(), bufferedImage.getHeight());
        return ImageFactory.getInstance().fromImage(bufferedImage);
    }
    
    /**
     * Stops the video capture service.
     * 
     * @throws FrameGrabber.Exception if an error occurs while stopping the camera
     */
    public void stop() throws FrameGrabber.Exception {
        if (isRunning.compareAndSet(true, false)) {
            logger.info("Stopping video capture");
            grabber.stop();
        }
    }
    
    @Override
    public void close() throws Exception {
        if (isRunning.get()) {
            stop();
        }
        grabber.close();
        logger.info("Video capture service closed");
    }
    
    /**
     * @return the current camera index
     */
    public int getCameraIndex() {
        return cameraIndex;
    }
    
    /**
     * @return the current frame width
     */
    public int getWidth() {
        return width;
    }
    
    /**
     * @return the current frame height
     */
    public int getHeight() {
        return height;
    }
    
    /**
     * @return the current target FPS
     */
    public int getFps() {
        return fps;
    }
    
    /**
     * @return true if the video capture service is running
     */
    public boolean isRunning() {
        return isRunning.get();
    }
    
    // Configuration now handled by VideoConfig. Deprecated: loadProperties() removed.
}
