package com.attendance.video.tracking;

import ai.djl.modality.cv.output.BoundingBox;
import ai.djl.modality.cv.output.Rectangle;
import com.attendance.model.recognition.DJLRecognizedFace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks faces across video frames to maintain identity consistency.
 * This class implements a simple tracking algorithm based on IoU (Intersection over Union)
 * to associate detections with existing tracks.
 */
public class FaceTracker {
    private static final Logger logger = LoggerFactory.getLogger(FaceTracker.class);
    
    private final Map<Integer, TrackedFace> tracks;
    private final AtomicInteger nextTrackId;
    
    // Configuration parameters
    private final double maxDistance;
    private final int maxAge;
    private final int minHits;
    private final boolean enabled;
    
    /**
     * Creates a new face tracker with default settings.
     */
    public FaceTracker() {
        this(com.attendance.config.VideoConfig.getRawProperties());
    }
    
    /**
     * Creates a new face tracker with settings from properties.
     * 
     * @param props the properties containing tracker settings
     */
    public FaceTracker(Properties props) {
        this.tracks = new ConcurrentHashMap<>();
        this.nextTrackId = new AtomicInteger(0);
        
        // Load configuration from properties
        this.enabled = Boolean.parseBoolean(props.getProperty("video.tracking.enabled", "true"));
        this.maxDistance = Double.parseDouble(props.getProperty("video.tracking.max.distance", "0.3"));
        this.maxAge = Integer.parseInt(props.getProperty("video.tracking.max.age", "10"));
        this.minHits = Integer.parseInt(props.getProperty("video.tracking.min.hits", "3"));
        
        logger.info("Created face tracker: enabled={}, maxDistance={}, maxAge={}, minHits={}",
                enabled, maxDistance, maxAge, minHits);
    }
    
    /**
     * Updates the tracker with new face detections.
     * This method associates new detections with existing tracks and creates new tracks as needed.
     * 
     * @param recognizedFaces the list of recognized faces from the current frame
     * @return the list of tracked faces after updating
     */
    public List<TrackedFace> update(List<DJLRecognizedFace> recognizedFaces) {
        if (!enabled) {
            // If tracking is disabled, convert all recognized faces to new tracked faces
            List<TrackedFace> result = new ArrayList<>();
            for (DJLRecognizedFace face : recognizedFaces) {
                TrackedFace trackedFace = new TrackedFace(nextTrackId.getAndIncrement(), face);
                result.add(trackedFace);
            }
            return result;
        }
        
        // Increment age of all existing tracks
        tracks.values().forEach(TrackedFace::incrementAge);
        
        // Remove old tracks
        tracks.entrySet().removeIf(entry -> entry.getValue().getAge() > maxAge);
        
        // If no detections, return current tracks
        if (recognizedFaces.isEmpty()) {
            return new ArrayList<>(tracks.values());
        }
        
        // Calculate distance matrix between detections and existing tracks
        double[][] distanceMatrix = calculateDistanceMatrix(recognizedFaces);
        
        // Match detections to existing tracks
        Set<Integer> unmatchedDetections = new HashSet<>();
        for (int i = 0; i < recognizedFaces.size(); i++) {
            unmatchedDetections.add(i);
        }
        
        Set<Integer> unmatchedTracks = new HashSet<>(tracks.keySet());
        Map<Integer, Integer> matches = new HashMap<>();
        
        // Find best matches using greedy algorithm
        while (!unmatchedDetections.isEmpty() && !unmatchedTracks.isEmpty()) {
            double bestDistance = maxDistance;
            int bestDetectionIdx = -1;
            int bestTrackId = -1;
            
            for (int detectionIdx : unmatchedDetections) {
                for (int trackId : unmatchedTracks) {
                    double distance = distanceMatrix[detectionIdx][getTrackIndex(trackId)];
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestDetectionIdx = detectionIdx;
                        bestTrackId = trackId;
                    }
                }
            }
            
            if (bestDetectionIdx >= 0) {
                matches.put(bestDetectionIdx, bestTrackId);
                unmatchedDetections.remove(bestDetectionIdx);
                unmatchedTracks.remove(bestTrackId);
            } else {
                break; // No more matches below threshold
            }
        }
        
        // Update matched tracks
        for (Map.Entry<Integer, Integer> match : matches.entrySet()) {
            int detectionIdx = match.getKey();
            int trackId = match.getValue();
            
            DJLRecognizedFace face = recognizedFaces.get(detectionIdx);
            TrackedFace track = tracks.get(trackId);
            track.update(face);
        }
        
        // Create new tracks for unmatched detections
        for (int detectionIdx : unmatchedDetections) {
            DJLRecognizedFace face = recognizedFaces.get(detectionIdx);
            int newTrackId = nextTrackId.getAndIncrement();
            TrackedFace newTrack = new TrackedFace(newTrackId, face);
            tracks.put(newTrackId, newTrack);
        }
        
        // Return all active tracks
        return new ArrayList<>(tracks.values());
    }
    
    /**
     * Calculates the distance matrix between detections and existing tracks.
     * The distance is based on IoU (Intersection over Union) of bounding boxes.
     * 
     * @param recognizedFaces the list of recognized faces from the current frame
     * @return the distance matrix
     */
    private double[][] calculateDistanceMatrix(List<DJLRecognizedFace> recognizedFaces) {
        double[][] distanceMatrix = new double[recognizedFaces.size()][tracks.size()];
        
        for (int i = 0; i < recognizedFaces.size(); i++) {
            BoundingBox detectionBox = recognizedFaces.get(i).getBoundingBox();
            
            int j = 0;
            for (TrackedFace track : tracks.values()) {
                BoundingBox trackBox = track.getBoundingBox();
                
                // Calculate IoU-based distance (1 - IoU)
                double iou = calculateIoU(detectionBox, trackBox);
                double distance = 1.0 - iou;
                
                distanceMatrix[i][j] = distance;
                j++;
            }
        }
        
        return distanceMatrix;
    }
    
    /**
     * Calculates the Intersection over Union (IoU) of two bounding boxes.
     * 
     * @param box1 the first bounding box
     * @param box2 the second bounding box
     * @return the IoU value (0 to 1)
     */
    private double calculateIoU(BoundingBox box1, BoundingBox box2) {
        if (!(box1 instanceof Rectangle) || !(box2 instanceof Rectangle)) {
            // For non-rectangle boxes, use a simpler distance metric
            return 0.0;
        }
        
        Rectangle rect1 = (Rectangle) box1;
        Rectangle rect2 = (Rectangle) box2;
        
        // Calculate coordinates of the intersection rectangle
        double x1 = Math.max(rect1.getX(), rect2.getX());
        double y1 = Math.max(rect1.getY(), rect2.getY());
        double x2 = Math.min(rect1.getX() + rect1.getWidth(), rect2.getX() + rect2.getWidth());
        double y2 = Math.min(rect1.getY() + rect1.getHeight(), rect2.getY() + rect2.getHeight());
        
        // Calculate area of intersection
        double intersectionArea = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        
        // Calculate areas of both rectangles
        double area1 = rect1.getWidth() * rect1.getHeight();
        double area2 = rect2.getWidth() * rect2.getHeight();
        
        // Calculate IoU
        double unionArea = area1 + area2 - intersectionArea;
        return intersectionArea / unionArea;
    }
    
    /**
     * Gets the index of a track in the internal array.
     * 
     * @param trackId the track ID
     * @return the index in the array
     */
    private int getTrackIndex(int trackId) {
        int index = 0;
        for (int id : tracks.keySet()) {
            if (id == trackId) {
                return index;
            }
            index++;
        }
        return -1;
    }
    
    /**
     * Clears all tracks from the tracker.
     */
    public void clearTracks() {
        tracks.clear();
    }
    
    /**
     * @return the number of active tracks
     */
    public int getTrackCount() {
        return tracks.size();
    }
    
    /**
     * @return a list of all active tracks
     */
    public List<TrackedFace> getTracks() {
        return new ArrayList<>(tracks.values());
    }
    
    /**
     * @return a list of confirmed tracks (tracks with enough hits)
     */
    public List<TrackedFace> getConfirmedTracks() {
        List<TrackedFace> confirmed = new ArrayList<>();
        for (TrackedFace track : tracks.values()) {
            if (track.getTotalHits() >= minHits) {
                confirmed.add(track);
            }
        }
        return confirmed;
    }
    
    // Configuration now handled by VideoConfig. Deprecated: loadProperties() removed.
}
