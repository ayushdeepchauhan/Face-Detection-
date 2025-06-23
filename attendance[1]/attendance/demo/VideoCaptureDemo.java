package com.attendance.demo;

import ai.djl.modality.cv.Image;
import com.attendance.video.VideoCaptureService;
import org.bytedeco.javacv.FrameGrabber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Demo application for testing the video capture service.
 * This demonstrates capturing frames from a webcam and saving them to disk.
 */
public class VideoCaptureDemo {
    private static final Logger logger = LoggerFactory.getLogger(VideoCaptureDemo.class);
    
    public static void main(String[] args) {
        // Create output directory for captured frames
        Path outputDir = Paths.get("output", "video_capture");
        
        try {
            // Ensure output directory exists
            Files.createDirectories(outputDir);
            
            // Create and start video capture service
            logger.info("Initializing video capture service...");
            try (VideoCaptureService captureService = new VideoCaptureService(0, 640, 480, 30)) {
                // Start the capture service
                captureService.start();
                logger.info("Video capture started: camera={}, resolution={}x{}, fps={}", 
                        captureService.getCameraIndex(),
                        captureService.getWidth(),
                        captureService.getHeight(),
                        captureService.getFps());
                
                // Capture 10 frames with a 1-second interval
                logger.info("Capturing frames...");
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
                
                for (int i = 0; i < 10; i++) {
                    // Capture a frame
                    Image frame = captureService.captureFrame();
                    
                    if (frame != null) {
                        // Generate a timestamp for the filename
                        String timestamp = LocalDateTime.now().format(formatter);
                        Path framePath = outputDir.resolve("frame_" + timestamp + ".png");
                        
                        // Save the frame to disk
                        frame.save(Files.newOutputStream(framePath), "png");
                        logger.info("Captured frame {}: {}", i + 1, framePath);
                    } else {
                        logger.warn("Failed to capture frame {}", i + 1);
                    }
                    
                    // Wait for 1 second before capturing the next frame
                    Thread.sleep(1000);
                }
                
                logger.info("Frame capture complete. Frames saved to: {}", outputDir.toAbsolutePath());
            }
            
        } catch (FrameGrabber.Exception e) {
            logger.error("Error in video capture", e);
            System.err.println("Failed to initialize or use the camera: " + e.getMessage());
            System.err.println("If you're using a phone as webcam, make sure it's properly connected and recognized by the system.");
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            e.printStackTrace();
        }
    }
}
