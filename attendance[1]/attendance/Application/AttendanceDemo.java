package com.attendance.Application;

import ai.djl.modality.cv.Image;
import com.attendance.facealignment.DJLStandardFaceAligner;
import com.attendance.facedetection.RetinaFaceDetection;
import com.attendance.facerecogntion.FaceNetRecognition;
import com.attendance.pipeline.DJLFaceProcessingPipeline;
import com.attendance.dao.CourseDAO;
import com.attendance.dao.impl.JdbcCourseDAO;
import com.attendance.model.db.Course;
import com.attendance.service.AttendanceService;
import com.attendance.video.VideoCaptureService;
import com.attendance.video.VideoProcessingService;
import com.attendance.video.tracking.TrackedFace;



import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.attendance.model.db.Student;
import com.attendance.dao.StudentDAO;
import com.attendance.dao.impl.JdbcStudentDAO;

/**
 * Demo application that integrates video processing with attendance tracking.
 * This application captures video from a webcam, detects and recognizes faces,
 * and marks attendance for recognized students.
 */
public class AttendanceDemo extends JFrame {

    private static final Logger logger = LoggerFactory.getLogger(AttendanceDemo.class);
    
    // UI components
    private final JPanel videoPanel;
    private final JLabel statusLabel;
    private final JButton startButton;
    private final JButton stopButton;
    private final JButton captureButton;
    private final JButton resetButton;
    private final JButton terminateButton;
    
    // Services
    private final VideoProcessingService videoProcessingService;
    private final AttendanceService attendanceService;
    private final CourseDAO courseDAO;
    private final StudentDAO studentDAO;
    
    // Current course for this session
    private Course currentCourse;
    
    // State
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    // Helper to update the video panel image
    private void updateVideoPanel(Image frame) {
        if (frame == null) return;
        BufferedImage bufferedImage = (BufferedImage) frame.getWrappedImage();
        final BufferedImage finalImage = bufferedImage;
        SwingUtilities.invokeLater(() -> {
            try {
                java.lang.reflect.Method setImage = videoPanel.getClass().getDeclaredMethod("setImage", BufferedImage.class);
                setImage.invoke(videoPanel, finalImage);
            } catch (Exception e) {
                videoPanel.repaint();
            }
        });
    }
    
    /**
     * Creates a new AttendanceDemo application.
     */
    public AttendanceDemo() {
        super("AutoAttend Attendance Demo");
        
        // Initialize DataSource
        DataSource dataSource = com.attendance.db.DbConfig.getDataSource();
        
        // Initialize services
        attendanceService = new AttendanceService();
        courseDAO = new JdbcCourseDAO(dataSource);
        studentDAO = new JdbcStudentDAO(dataSource);
        
        try {
            // Create video processing service with camera index 1 (for Camo)
            VideoCaptureService captureService = new VideoCaptureService(
    com.attendance.config.VideoConfig.getCameraIndex(),
    com.attendance.config.VideoConfig.getCameraWidth(),
    com.attendance.config.VideoConfig.getCameraHeight(),
    com.attendance.config.VideoConfig.getCameraFps()
);
            
            // Create the face processing pipeline components
            RetinaFaceDetection faceDetection = new RetinaFaceDetection();
            DJLStandardFaceAligner faceAligner = new DJLStandardFaceAligner();
            FaceNetRecognition faceRecognition = new FaceNetRecognition();
            
            // Create the pipeline
            DJLFaceProcessingPipeline pipeline = new DJLFaceProcessingPipeline(
                    faceDetection,
                    faceAligner,
                    faceRecognition);
            
            // Create the video processing service
            videoProcessingService = new VideoProcessingService(captureService, pipeline);
            
            logger.info("Video processing components initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize video processing components", e);
            throw new RuntimeException("Failed to initialize video processing components", e);
        }
        
        // Set up callbacks
        videoProcessingService.setProcessedFrameCallback(this::updateVideoPanel);
        videoProcessingService.setTrackedFacesCallback(this::onTrackedFaces);
        
        // Set up UI
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1024, 768);
        setLayout(new BorderLayout());
        
        // Video panel - use anonymous JPanel for perfect overlay and sizing
        videoPanel = new JPanel() {
            private BufferedImage image;
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (image != null) {
                    int x = (getWidth() - image.getWidth()) / 2;
                    int y = (getHeight() - image.getHeight()) / 2;
                    g.drawImage(image, x, y, this);
                }
            }
            public void setImage(BufferedImage image) {
                this.image = image;
                repaint();
            }

        };
        videoPanel.setBackground(Color.BLACK);
        videoPanel.setPreferredSize(new Dimension(800, 600));
        add(videoPanel, BorderLayout.CENTER);
        
        // Control panel
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new FlowLayout());
        
        startButton = new JButton("Start");
        startButton.addActionListener(e -> startProcessing());
        
        stopButton = new JButton("Stop");
        stopButton.addActionListener(e -> stopProcessing());
        stopButton.setEnabled(false);
        
        captureButton = new JButton("Capture Frame");
        captureButton.addActionListener(e -> captureFrame());
        captureButton.setEnabled(false);
        
        resetButton = new JButton("Reset Attendance");
        resetButton.addActionListener(e -> resetAttendance());
        
        terminateButton = new JButton("Terminate");
        terminateButton.addActionListener(e -> terminateApplication());
        
        statusLabel = new JLabel("Status: Ready");
        
        controlPanel.add(startButton);
        controlPanel.add(stopButton);
        controlPanel.add(captureButton);
        controlPanel.add(resetButton);
        controlPanel.add(terminateButton);
        controlPanel.add(statusLabel);
        
        add(controlPanel, BorderLayout.SOUTH);
        
        // Handle window close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (isRunning.get()) {
                    stopProcessing();
                }
            }
        });
        
        // Show the window
        setLocationRelativeTo(null);
        setVisible(true);
    }
    
    /**
     * Starts the video processing.
     */
    private void startProcessing() {
        if (!isRunning.get()) {
            try {
                // Prompt for course selection before starting
                selectCourseForSession();
                
                // Set the current course ID in the attendance service
                if (currentCourse != null) {
                    attendanceService.resetAttendanceTracking(currentCourse.getCourseId());
                    logger.info("Set current course ID for attendance tracking: {}", currentCourse.getCourseId());
                } else {
                    attendanceService.resetAttendanceTracking();
                    logger.info("No course filter set for attendance tracking");
                }
                
                videoProcessingService.start(1000); // Process a frame every 1000ms
                isRunning.set(true);
                
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                captureButton.setEnabled(true);
                
                // Update status with course info if available
                if (currentCourse != null) {
                    statusLabel.setText(String.format("Status: Running - Course: %s (%s)", 
                            currentCourse.getCourseName(), currentCourse.getCourseCode()));
                } else {
                    statusLabel.setText("Status: Running - No course selected");
                }
                
                logger.info("Video processing started");
            } catch (Exception e) {
                logger.error("Failed to start video processing", e);
                JOptionPane.showMessageDialog(this, 
                        "Failed to start video processing: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Stops the video processing.
     */
    private void stopProcessing() {
        if (isRunning.get()) {
            try {
                videoProcessingService.stop();
                isRunning.set(false);
                
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                captureButton.setEnabled(false);
                
                statusLabel.setText("Status: Stopped");
                logger.info("Video processing stopped");
                
                // Display attendance summary
                displayAttendanceSummary();
            } catch (Exception e) {
                logger.error("Failed to stop video processing", e);
                JOptionPane.showMessageDialog(this, 
                        "Failed to stop video processing: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Captures a single frame from the video feed.
     */
    private void captureFrame() {
        if (isRunning.get()) {
            try {
                // Force processing of the next frame by triggering the scheduled task
                logger.info("Manual frame capture requested");
                // This is a workaround since we can't directly call processNextFrame
                videoProcessingService.stop();
                videoProcessingService.start(1000);
                logger.info("Frame capture triggered");
            } catch (Exception e) {
                logger.error("Failed to capture frame", e);
                JOptionPane.showMessageDialog(this, 
                        "Failed to capture frame: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Resets the attendance tracking.
     */
    private void resetAttendance() {
        // Prompt for course selection
        selectCourseForSession();
        
        // Reset attendance tracking with selected course
        if (currentCourse != null) {
            attendanceService.resetAttendanceTracking(currentCourse.getCourseId());
            statusLabel.setText(String.format("Status: Attendance reset for %s (%s)", 
                    currentCourse.getCourseName(), currentCourse.getCourseCode()));
            logger.info("Attendance tracking reset for course: {} ({})", 
                    currentCourse.getCourseName(), currentCourse.getCourseCode());
        } else {
            attendanceService.resetAttendanceTracking();
            statusLabel.setText("Status: Attendance reset (no course filter)");
            logger.info("Attendance tracking reset with no course filter");
        }
    }
    
    /**
     * Displays a summary of students whose attendance was marked during this session.
     */
    private void displayAttendanceSummary() {
        // Get list of all students for the current course
        List<Student> students;
        if (currentCourse != null) {
            students = courseDAO.findStudentsByCourseId(currentCourse.getCourseId());
        } else {
            students = studentDAO.findAll();
        }
        
        // Build the summary message
        StringBuilder message = new StringBuilder();
        message.append("<html><body>");
        message.append("<h2>Attendance Summary</h2>");
        
        if (currentCourse != null) {
            message.append(String.format("<h3>Course: %s (%s)</h3>", 
                    currentCourse.getCourseName(), currentCourse.getCourseCode()));
        }
        
        // Filter to only show students who were marked present in this session
        List<Student> presentStudents = new ArrayList<>();
        for (Student student : students) {
            // Get the database ID (Long type) from the student object
            Long studentId = student.getId();
            if (studentId != null && attendanceService.wasStudentMarkedPresent(studentId)) {
                presentStudents.add(student);
            }
        }
        
        if (presentStudents.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No students were marked present during this session.",
                    "Attendance Summary", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        message.append("<p>The following students were marked present:</p>");
        message.append("<ul>");
        
        // Add each student to the message
        for (Student student : presentStudents) {
            message.append(String.format("<li>%s %s (ID: %s)</li>",
                    student.getFirstName(),
                    student.getLastName(),
                    student.getStudentId()));
        }
        
        message.append("</ul>");
        message.append("</body></html>");
        
        // Show the summary dialog
        JOptionPane.showMessageDialog(this,
                message.toString(),
                "Attendance Summary",
                JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Terminates the application.
     */
    private void terminateApplication() {
        // Stop processing if running
        if (isRunning.get()) {
            stopProcessing();
        } else {
            // If not running, still display the attendance summary
            displayAttendanceSummary();
        }
        
        // Close the application
        logger.info("Application terminated by user");
        dispose();
        System.exit(0);
    }
    
    private void selectCourseForSession() {
        List<Course> courses = courseDAO.findAll();
        
        if (courses.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No courses found in the database. Please add courses using the Course Management CLI.",
                    "No Courses Available", JOptionPane.WARNING_MESSAGE);
            currentCourse = null;
            return;
        }
        
        // Create course selection dialog
        Object[] courseOptions = new Object[courses.size() + 1];
        courseOptions[0] = "All Students (No Course Filter)";
        
        for (int i = 0; i < courses.size(); i++) {
            Course course = courses.get(i);
            courseOptions[i + 1] = String.format("%s - %s", course.getCourseCode(), course.getCourseName());
        }
        
        String selectedOption = (String) JOptionPane.showInputDialog(
                this,
                "Select a course for this attendance session:",
                "Course Selection",
                JOptionPane.QUESTION_MESSAGE,
                null,
                courseOptions,
                courseOptions[0]);
        
        if (selectedOption == null) {
            // User cancelled, keep current course
            return;
        }
        
        if (selectedOption.equals("All Students (No Course Filter)")) {
            currentCourse = null;
            logger.info("No course filter selected for attendance session");
        } else {
            // Find the selected course
            for (Course course : courses) {
                String courseOption = String.format("%s - %s", course.getCourseCode(), course.getCourseName());
                if (selectedOption.equals(courseOption)) {
                    currentCourse = course;
                    logger.info("Selected course for attendance session: {} ({})", 
                            course.getCourseName(), course.getCourseCode());
                    break;
                }
            }
        }
    }
    
    /**
     * @param trackedFaces The list of tracked faces
     */
    private void onTrackedFaces(List<TrackedFace> trackedFaces) {
        int recognizedCount = 0;
        
        for (TrackedFace face : trackedFaces) {
            // Only process faces with enough hits
            if (face.getTotalHits() >= 3) {
                boolean marked = attendanceService.processTrackedFace(face);
                if (face.isRecognized()) {
                    recognizedCount++;
                }
                if (marked) {
                    logger.info("Attendance marked for: {}", face.getStudentName());
                }
            }
        }
        
        // Update status label with course info if available
        if (currentCourse != null) {
            statusLabel.setText(String.format("Status: Running - Course: %s - %d faces tracked, %d recognized", 
                    currentCourse.getCourseCode(), trackedFaces.size(), recognizedCount));
        } else {
            statusLabel.setText(String.format("Status: Running - %d faces tracked, %d recognized", 
                    trackedFaces.size(), recognizedCount));
        }
    }
    
    /**
     * Main method to start the application.
     */
    public static void main(String[] args) {
        // Set up the look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            logger.warn("Failed to set system look and feel", e);
        }
        
        // Start the application on the EDT
        SwingUtilities.invokeLater(AttendanceDemo::new);
    }
}
