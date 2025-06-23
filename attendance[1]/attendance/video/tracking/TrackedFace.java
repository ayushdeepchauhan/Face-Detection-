package com.attendance.video.tracking;

import ai.djl.modality.cv.output.BoundingBox;
import com.attendance.model.recognition.DJLRecognizedFace;

/**
 * Represents a face being tracked across multiple frames.
 * This class maintains the identity and state of a face as it moves through the video.
 */
public class TrackedFace {
    private final int trackId;
    private BoundingBox boundingBox;
    private float[] embedding;
    private long lastSeen;
    private int age;
    private int totalHits;
    private boolean recognized;
    private Long studentId;
    private String studentName;
    
    /**
     * Creates a new tracked face with the specified ID and recognized face.
     * 
     * @param trackId the unique tracking ID for this face
     * @param recognizedFace the recognized face to track
     */
    public TrackedFace(int trackId, DJLRecognizedFace recognizedFace) {
        this.trackId = trackId;
        this.boundingBox = recognizedFace.getBoundingBox();
        this.embedding = recognizedFace.getEmbedding();
        this.lastSeen = System.currentTimeMillis();
        this.age = 0;
        this.totalHits = 1;
        this.recognized = false;
        this.studentId = null;
        this.studentName = null;
    }
    
    /**
     * Updates this tracked face with a new detection.
     * 
     * @param recognizedFace the new recognized face
     */
    public void update(DJLRecognizedFace recognizedFace) {
        this.boundingBox = recognizedFace.getBoundingBox();
        this.lastSeen = System.currentTimeMillis();
        this.age = 0;
        this.totalHits++;
        
        // Only update the embedding if it's not null
        if (recognizedFace.getEmbedding() != null) {
            this.embedding = recognizedFace.getEmbedding();
        }
    }
    
    /**
     * Increments the age of this tracked face.
     * The age represents how many frames have passed since this face was last detected.
     */
    public void incrementAge() {
        this.age++;
    }
    
    /**
     * Sets the student information for this tracked face.
     * 
     * @param studentId the student ID
     * @param studentName the student name
     */
    public void setStudentInfo(Long studentId, String studentName) {
        this.studentId = studentId;
        this.studentName = studentName;
        this.recognized = true;
    }
    
    /**
     * @return the unique tracking ID for this face
     */
    public int getTrackId() {
        return trackId;
    }
    
    /**
     * @return the current bounding box for this face
     */
    public BoundingBox getBoundingBox() {
        return boundingBox;
    }
    
    /**
     * @return the face embedding for this face
     */
    public float[] getEmbedding() {
        return embedding;
    }
    
    /**
     * @return the timestamp when this face was last seen
     */
    public long getLastSeen() {
        return lastSeen;
    }
    
    /**
     * @return the age of this tracked face (frames since last detection)
     */
    public int getAge() {
        return age;
    }
    
    /**
     * @return the total number of times this face has been detected
     */
    public int getTotalHits() {
        return totalHits;
    }
    
    /**
     * @return true if this face has been recognized as a known student
     */
    public boolean isRecognized() {
        return recognized;
    }
    
    /**
     * @return the student ID for this face, or null if not recognized
     */
    public Long getStudentId() {
        return studentId;
    }
    
    /**
     * @return the student name for this face, or null if not recognized
     */
    public String getStudentName() {
        return studentName;
    }
}
