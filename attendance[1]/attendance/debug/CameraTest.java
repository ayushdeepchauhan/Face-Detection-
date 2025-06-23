package com.attendance.debug;

import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JFrame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Utility class to test camera access and identify available cameras.
 */
public class CameraTest {
    private static final Logger logger = LoggerFactory.getLogger(CameraTest.class);

    public static void main(String[] args) {
        // Parse camera index from command line or use default
        int cameraIndex = 0;
        if (args.length > 0) {
            try {
                cameraIndex = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid camera index: " + args[0]);
                System.exit(1);
            }
        }
        
        logger.info("Testing camera at index: {}", cameraIndex);
        
        // Create a canvas frame for displaying the video
        CanvasFrame canvasFrame = new CanvasFrame("Camera Test", CanvasFrame.getDefaultGamma() / 2.2);
        canvasFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        // Add a window listener to handle closing
        canvasFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
        
        // Create a grabber for the camera
        try (OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(cameraIndex)) {
            // Set properties
            grabber.setImageWidth(640);
            grabber.setImageHeight(480);
            
            // Start the grabber
            grabber.start();
            logger.info("Camera started: {}x{} @ {} fps", 
                    grabber.getImageWidth(), 
                    grabber.getImageHeight(),
                    grabber.getFrameRate());
            
            // Main loop for grabbing and displaying frames
            while (canvasFrame.isVisible()) {
                // Grab a frame
                Frame frame = grabber.grab();
                
                if (frame != null) {
                    // Display the frame
                    canvasFrame.showImage(frame);
                    
                    // Log frame info occasionally
                    if (grabber.getFrameNumber() % 30 == 0) {
                        logger.info("Frame #{}: {}x{}", 
                                grabber.getFrameNumber(),
                                frame.imageWidth,
                                frame.imageHeight);
                    }
                }
            }
            
            // Stop the grabber
            grabber.stop();
        } catch (FrameGrabber.Exception e) {
            logger.error("Failed to grab from camera {}: {}", cameraIndex, e.getMessage());
            System.err.println("Error accessing camera " + cameraIndex + ": " + e.getMessage());
            System.err.println("Try a different camera index (0, 1, 2, etc.)");
        }
    }
}
