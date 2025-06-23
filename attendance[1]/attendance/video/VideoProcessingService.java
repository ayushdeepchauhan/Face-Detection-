package com.attendance.video;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.output.BoundingBox;
import ai.djl.modality.cv.output.DetectedObjects;
import com.attendance.model.recognition.DJLRecognizedFace;
import com.attendance.pipeline.DJLFaceProcessingPipeline;
import com.attendance.video.tracking.FaceTracker;
import com.attendance.video.tracking.TrackedFace;
import org.bytedeco.javacv.FrameGrabber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Main service for video processing that integrates video capture, frame processing, and face tracking.
 * This service handles the continuous processing of video frames and provides callbacks for processing results.
 */
public class VideoProcessingService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(VideoProcessingService.class);
    
    private final VideoCaptureService captureService;
    private final VideoFrameProcessor frameProcessor;
    private final FaceTracker faceTracker;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean isRunning;
    
    private Consumer<List<TrackedFace>> trackedFacesCallback;
    private Consumer<Image> processedFrameCallback;
    private Consumer<Exception> errorCallback;
    
    /**
     * Creates a new video processing service with the specified components.
     * 
     * @param captureService the video capture service
     * @param pipeline the face processing pipeline
     * @throws IOException if an error occurs during initialization
     */
    public VideoProcessingService(VideoCaptureService captureService, DJLFaceProcessingPipeline pipeline) 
            throws IOException {
        this.captureService = captureService;
        this.frameProcessor = new VideoFrameProcessor(pipeline);
        this.faceTracker = new FaceTracker();
        this.executor = Executors.newSingleThreadScheduledExecutor();
        this.isRunning = new AtomicBoolean(false);
        
        logger.info("Created video processing service");
    }
    
    /**
     * Starts the video processing service.
     * This method starts the video capture, frame processor, and schedules the processing task.
     * 
     * @param frameIntervalMs the interval between frame processing in milliseconds
     * @throws FrameGrabber.Exception if the video capture cannot be started
     */
    public void start(long frameIntervalMs) throws FrameGrabber.Exception {
        if (isRunning.compareAndSet(false, true)) {
            logger.info("Starting video processing service with frame interval of {}ms", frameIntervalMs);
            
            // Start the capture service and frame processor
            captureService.start();
            frameProcessor.start();
            
            // Schedule the processing task
            executor.scheduleAtFixedRate(this::processNextFrame, 0, frameIntervalMs, TimeUnit.MILLISECONDS);
        } else {
            logger.warn("Video processing service already running");
        }
    }
    
    /**
     * Stops the video processing service.
     * This method stops the video capture, frame processor, and cancels the processing task.
     * 
     * @throws FrameGrabber.Exception if an error occurs while stopping the video capture
     */
    public void stop() throws FrameGrabber.Exception {
        if (isRunning.compareAndSet(true, false)) {
            logger.info("Stopping video processing service");
            
            // Stop the executor
            executor.shutdown();
            try {
                if (!executor.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            // Stop the capture service and frame processor
            frameProcessor.stop();
            captureService.stop();
        }
    }
    
    /**
     * Processes the next frame from the video capture.
     * This method is called by the scheduled executor at the specified interval.
     */
    private void processNextFrame() {
        if (!isRunning.get()) {
            return;
        }
        
        try {
            // Capture a frame
            Image frame = captureService.captureFrame();
            if (frame == null) {
                return;
            }
            
            // Process the frame
            List<DJLRecognizedFace> recognizedFaces = frameProcessor.processFrame(frame);
            
            // Update the face tracker
            List<TrackedFace> trackedFaces = faceTracker.update(recognizedFaces);
            
            // Call the callback with the tracked faces
            if (trackedFacesCallback != null) {
                trackedFacesCallback.accept(trackedFaces);
            }
            
            // Call the callback with the processed frame
            if (processedFrameCallback != null) {
                // Create a copy of the frame with bounding boxes for tracked faces
                Image processedFrame = frame.duplicate();
                
                // Draw bounding boxes with labels for tracked faces
                drawBoundingBoxesForTrackedFaces(processedFrame, trackedFaces);
                
                processedFrameCallback.accept(processedFrame);
            }
            
        } catch (Exception e) {
            logger.error("Error processing frame: {}", e.getMessage());
            
            // Call the error callback
            if (errorCallback != null) {
                errorCallback.accept(e);
            }
        }
    }
    
    /**
     * Draws bounding boxes and labels for tracked faces on the image.
     *
     * @param image the image to draw on
     * @param trackedFaces the list of tracked faces to draw
     */
    private void drawBoundingBoxesForTrackedFaces(Image image, List<TrackedFace> trackedFaces) {
        if (trackedFaces.isEmpty()) {
            return;
        }
        
        // Create lists for the DetectedObjects constructor
        List<String> names = new ArrayList<>();
        List<Double> probabilities = new ArrayList<>();
        List<BoundingBox> boundingBoxes = new ArrayList<>();
        
        // Add each tracked face to the lists
        for (TrackedFace face : trackedFaces) {
            // Only draw confirmed tracks (with enough hits)
            if (face.getTotalHits() >= 3) {
                String label = face.isRecognized() 
                    ? face.getStudentName() + " (ID: " + face.getStudentId() + ")"
                    : "Face #" + face.getTrackId();
                
                names.add(label);
                probabilities.add(1.0); // Use 1.0 as the confidence for tracked faces
                boundingBoxes.add(face.getBoundingBox());
            }
        }
        
        // Create a DetectedObjects object and draw it on the image
        if (!names.isEmpty()) {
            DetectedObjects objects = 
                new DetectedObjects(names, probabilities, boundingBoxes);
            image.drawBoundingBoxes(objects);
        }
    }
    
    /**
     * Sets the callback for tracked faces.
     * This callback is called with the list of tracked faces after each frame is processed.
     * 
     * @param callback the callback to set
     */
    public void setTrackedFacesCallback(Consumer<List<TrackedFace>> callback) {
        this.trackedFacesCallback = callback;
    }
    
    /**
     * Sets the callback for processed frames.
     * This callback is called with the processed frame after each frame is processed.
     * 
     * @param callback the callback to set
     */
    public void setProcessedFrameCallback(Consumer<Image> callback) {
        this.processedFrameCallback = callback;
    }
    
    /**
     * Sets the callback for errors.
     * This callback is called when an error occurs during frame processing.
     * 
     * @param callback the callback to set
     */
    public void setErrorCallback(Consumer<Exception> callback) {
        this.errorCallback = callback;
    }
    
    /**
     * @return true if the video processing service is running
     */
    public boolean isRunning() {
        return isRunning.get();
    }
    
    /**
     * @return the face tracker used by this service
     */
    public FaceTracker getFaceTracker() {
        return faceTracker;
    }
    
    @Override
    public void close() throws Exception {
        if (isRunning.get()) {
            stop();
        }
        
        captureService.close();
        frameProcessor.close();
    }
}
