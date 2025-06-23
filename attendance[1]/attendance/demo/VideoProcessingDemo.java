package com.attendance.demo;

import ai.djl.modality.cv.Image;
// import com.attendance.exception.ModelException;
import com.attendance.facealignment.DJLStandardFaceAligner;
import com.attendance.facedetection.RetinaFaceDetection;
import com.attendance.facerecogntion.FaceNetRecognition;
import com.attendance.pipeline.DJLFaceProcessingPipeline;
import com.attendance.video.VideoCaptureService;
import com.attendance.video.VideoProcessingService;
import com.attendance.video.tracking.TrackedFace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Demo application for the video processing service.
 * This demonstrates capturing and processing video frames with face detection, recognition, and tracking.
 */
public class VideoProcessingDemo {
    private static final Logger logger = LoggerFactory.getLogger(VideoProcessingDemo.class);
    
    // UI components
    private JFrame frame;
    private JPanel videoPanel;
    // Helper to set image on videoPanel
    private void setPanelImage(BufferedImage image) {
        if (videoPanel != null) {
            try {
                java.lang.reflect.Method setImageMethod = videoPanel.getClass().getDeclaredMethod("setImage", BufferedImage.class);
                setImageMethod.invoke(videoPanel, image);
            } catch (Exception e) {
                logger.error("Failed to set image on video panel: {}", e.getMessage());
            }
        }
    }
    private JLabel statusLabel;
    private JButton startButton;
    private JButton stopButton;
    private JButton captureButton;
    
    // Processing components
    private VideoCaptureService captureService;
    private VideoProcessingService processingService;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    
    // Output directory for captured frames
    private final Path outputDir = Paths.get("output", "video_processing");
    
    /**
     * Creates and shows the demo UI.
     */
    public void createAndShowGUI() {
        // Create the main frame
        frame = new JFrame("AutoAttend Video Processing Demo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLayout(new BorderLayout());
        
        // Create the video panel
        videoPanel = new JPanel() {
            private BufferedImage currentFrame;
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (currentFrame != null) {
                    g.drawImage(currentFrame, 0, 0, getWidth(), getHeight(), null);
                }
            }
            public void setImage(BufferedImage image) {
                this.currentFrame = image;
                repaint();
            }
        };
        videoPanel.setBackground(Color.BLACK);
        frame.add(videoPanel, BorderLayout.CENTER);
        
        // Create the control panel
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new FlowLayout());
        
        startButton = new JButton("Start");
        startButton.addActionListener(e -> startProcessing());
        
        stopButton = new JButton("Stop");
        stopButton.addActionListener(e -> stopProcessing());
        stopButton.setEnabled(false);
        
        captureButton = new JButton("Capture Frame");
        captureButton.addActionListener(e -> captureCurrentFrame());
        captureButton.setEnabled(false);
        
        statusLabel = new JLabel("Status: Ready");
        
        controlPanel.add(startButton);
        controlPanel.add(stopButton);
        controlPanel.add(captureButton);
        controlPanel.add(statusLabel);
        
        frame.add(controlPanel, BorderLayout.SOUTH);
        
        // Center the frame on the screen
        frame.setLocationRelativeTo(null);
        
        // Show the frame
        frame.setVisible(true);
    }
    
    /**
     * Starts the video processing.
     */
    private void startProcessing() {
        if (isRunning.get()) {
            return;
        }
        
        try {
            // Create output directory
            Files.createDirectories(outputDir);

            // Always use camera index 0
            logger.info("Initializing video processing components with camera index 0...");
            statusLabel.setText("Status: Initializing (Camera 0)...");

            // Initialize components
            captureService = new VideoCaptureService(
    com.attendance.config.VideoConfig.getCameraIndex(),
    com.attendance.config.VideoConfig.getCameraWidth(),
    com.attendance.config.VideoConfig.getCameraHeight(),
    com.attendance.config.VideoConfig.getCameraFps()
);

            // Test the camera connection first
            captureService.start();
            Image testFrame = captureService.captureFrame();
            if (testFrame == null) {
                throw new Exception("Failed to capture test frame from camera. Please check if your camera is available and not in use by another application.");
            }
            logger.info("Successfully captured test frame from camera: {}x{}", testFrame.getWidth(), testFrame.getHeight());

            // Show the test frame in the UI
            updateVideoPanel(testFrame);

            // Create the face processing pipeline
            logger.info("Initializing face processing pipeline...");
            RetinaFaceDetection faceDetection = new RetinaFaceDetection();
            DJLStandardFaceAligner faceAligner = new DJLStandardFaceAligner();
            FaceNetRecognition faceRecognition = new FaceNetRecognition();

            
            // Create the pipeline with visualization output
            DJLFaceProcessingPipeline pipeline = new DJLFaceProcessingPipeline(
                    faceDetection,
                    faceAligner,
                    faceRecognition,
                    Optional.of(outputDir));
            
            // Create the video processing service
            processingService = new VideoProcessingService(captureService, pipeline);
            
            // Set callbacks
            processingService.setProcessedFrameCallback(this::updateVideoPanel);
            processingService.setTrackedFacesCallback(this::handleTrackedFaces);
            processingService.setErrorCallback(this::handleError);
            
            // Start the processing service
            processingService.start(100); // Process frames every 100ms
            
            // Update UI
            isRunning.set(true);
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            captureButton.setEnabled(true);
            statusLabel.setText("Status: Running");
            
            logger.info("Video processing started");
        } catch (Exception e) {
            logger.error("Failed to start video processing", e);
            statusLabel.setText("Status: Error - " + e.getMessage());
            JOptionPane.showMessageDialog(frame, 
                    "Failed to start video processing: " + e.getMessage(), 
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Stops the video processing.
     */
    private void stopProcessing() {
        if (!isRunning.get()) {
            return;
        }
        
        try {
            // Stop the processing service
            if (processingService != null) {
                processingService.stop();
                processingService.close();
            }
            
            // Update UI
            isRunning.set(false);
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            captureButton.setEnabled(false);
            statusLabel.setText("Status: Stopped");
            
            logger.info("Video processing stopped");
        } catch (Exception e) {
            logger.error("Failed to stop video processing", e);
            statusLabel.setText("Status: Error - " + e.getMessage());
        }
    }
    
    /**
     * Captures the current frame and saves it to disk.
     */
    private void captureCurrentFrame() {
        if (!isRunning.get()) {
            return;
        }
        
        try {
            // Capture a frame
            Image frame = captureService.captureFrame();
            if (frame == null) {
                return;
            }
            
            // Generate a timestamp for the filename
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
            Path framePath = outputDir.resolve("capture_" + timestamp + ".png");
            
            // Save the frame to disk
            frame.save(Files.newOutputStream(framePath), "png");
            
            logger.info("Captured frame: {}", framePath);
            JOptionPane.showMessageDialog(this.frame, 
                    "Frame captured and saved to: " + framePath, 
                    "Frame Captured", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            logger.error("Failed to capture frame", e);
            JOptionPane.showMessageDialog(frame, 
                    "Failed to capture frame: " + e.getMessage(), 
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Updates the video panel with a new frame.
     * 
     * @param image the new frame to display
     */
    private void updateVideoPanel(Image image) {
        if (image == null) {
            logger.warn("Attempted to update video panel with null image");
            return;
        }
        
        try {
            // Convert DJL Image to AWT BufferedImage
            BufferedImage bufferedImage = (BufferedImage) image.getWrappedImage();
            if (bufferedImage == null) {
                logger.warn("Failed to get BufferedImage from DJL Image");
                return;
            }
            
            logger.debug("Updating video panel with image: {}x{}", bufferedImage.getWidth(), bufferedImage.getHeight());
            
            // Update the video panel on the EDT
            final BufferedImage finalImage = bufferedImage;
            SwingUtilities.invokeLater(() -> setPanelImage(finalImage));
        } catch (Exception e) {
            logger.error("Failed to update video panel: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Handles tracked faces from the video processing service.
     * 
     * @param trackedFaces the list of tracked faces
     */
    private void handleTrackedFaces(List<TrackedFace> trackedFaces) {
        // Update the status label with the number of tracked faces
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(String.format("Status: Running - %d faces tracked", trackedFaces.size()));
        });
        
        // Log the tracked faces
        logger.debug("Tracked faces: {}", trackedFaces.size());
    }
    
    /**
     * Handles errors from the video processing service.
     * 
     * @param e the error that occurred
     */
    private void handleError(Exception e) {
        logger.error("Video processing error", e);
        
        // Update the status label with the error
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Status: Error - " + e.getMessage());
        });
    }
    
    // We're using an anonymous inner class instead of this static inner class
    
    public static void main(String[] args) {
        // Set up the look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            logger.warn("Failed to set system look and feel", e);
        }
        
        // Create and show the GUI on the EDT
        SwingUtilities.invokeLater(() -> {
            VideoProcessingDemo demo = new VideoProcessingDemo();
            demo.createAndShowGUI();
        });
    }
}
