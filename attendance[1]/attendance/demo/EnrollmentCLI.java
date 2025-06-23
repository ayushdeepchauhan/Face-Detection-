package com.attendance.demo;

import com.attendance.dao.CourseDAO;
import com.attendance.dao.FaceEmbeddingDAO;
import com.attendance.dao.StudentDAO;
import com.attendance.dao.impl.JdbcCourseDAO;
import com.attendance.dao.impl.JdbcFaceEmbeddingDAO;
import com.attendance.dao.impl.JdbcStudentDAO;
import com.attendance.model.db.Course;
import com.attendance.model.db.FaceEmbedding;
import com.attendance.model.db.Student;
import com.attendance.pipeline.DJLFaceProcessingPipeline;
import com.attendance.exception.ModelException;
import com.attendance.facedetection.FaceDetectionService;
import com.attendance.facedetection.RetinaFaceDetection;
import com.attendance.facealignment.DJLFaceAligner;
import com.attendance.facealignment.DJLStandardFaceAligner;
import com.attendance.facerecogntion.FaceRecognitionService;
import com.attendance.facerecogntion.FaceNetRecognition;
import com.attendance.model.recognition.DJLRecognizedFace;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;

import javax.sql.DataSource;

/**
 * Command-line interface for enrolling students and managing face embeddings.
 */
public class EnrollmentCLI {
    private final StudentDAO studentDAO;
    private final FaceEmbeddingDAO embeddingDAO;
    private final CourseDAO courseDAO;
    private final com.attendance.dao.AttendanceDAO attendanceDAO;
    private final Scanner scanner;
    private final DJLFaceProcessingPipeline facePipeline;
    private final FaceDetectionService detectionService;
    private final DJLFaceAligner faceAligner;
    private final FaceRecognitionService recognitionService;

    public EnrollmentCLI() {
        // Initialize DataSource
        DataSource dataSource = com.attendance.db.DbConfig.getDataSource();

        // Pass DataSource to DAO constructors
        this.studentDAO = new JdbcStudentDAO(dataSource);
        this.embeddingDAO = new JdbcFaceEmbeddingDAO(dataSource);
        this.courseDAO = new JdbcCourseDAO(dataSource);
        this.attendanceDAO = new com.attendance.dao.impl.JdbcAttendanceDAO(dataSource);
        
        this.scanner = new Scanner(System.in);
        
        try {
            this.detectionService = new RetinaFaceDetection();
            this.faceAligner = new DJLStandardFaceAligner();
            this.recognitionService = new FaceNetRecognition();
            
            this.facePipeline = new DJLFaceProcessingPipeline(detectionService, faceAligner, recognitionService);
        } catch (ModelException e) {
            System.err.println("FATAL: Failed to initialize face processing models: " + e.getMessage());
            throw new RuntimeException("Failed to initialize models", e);
        }
    }

    /**
     * Starts the enrollment CLI.
     */
    public void start() {
        boolean running = true;
        while (running) {
            displayMenu();
            int choice = scanner.nextInt();
            scanner.nextLine(); // Consume newline
            
            switch (choice) {
                case 1:
                    enrollNewStudent();
                    break;
                case 2:
                    listStudents();
                    break;
                case 3:
                    removeStudent();
                    break;
                case 4:
                    manageStudentCourses();
                    break;
                case 0:
                    running = false;
                    break;
                default:
                    System.out.println("Invalid choice. Please try again.");
            }
        }
        
        scanner.close();
    }
    
    private void displayMenu() {
        System.out.println("\n=== Student Enrollment Menu ===");
        System.out.println("1. Enroll New Student");
        System.out.println("2. List Students");
        System.out.println("3. Remove Student");
        System.out.println("4. Manage Student Course Enrollment");
        System.out.println("0. Exit");
        System.out.print("Enter your choice: ");
    }
    
    /**
     * Enrolls a new student with face embedding.
     */
    private void enrollNewStudent() {
        String studentId = promptForInput("Enter student ID (or type 'cancel'): ", false);
        if (studentId == null) return; // User cancelled

        String firstName = promptForInput("Enter first name (or type 'cancel'): ", false);
        if (firstName == null) return; // User cancelled

        String lastName = promptForInput("Enter last name (or type 'cancel'): ", false);
        if (lastName == null) return; // User cancelled

        String imagePath = promptForInput("Enter path to face image file (or type 'cancel'): ", true); // Validate path
        if (imagePath == null) return; // User cancelled or invalid path after retries

        Student student = new Student(studentId, firstName, lastName);
        Long dbStudentId = studentDAO.save(student);
        
        if (dbStudentId != null) {
            System.out.println("Student details saved successfully with DB ID: " + dbStudentId);
            
            try {
                Image img = ImageFactory.getInstance().fromFile(Paths.get(imagePath));
                
                List<DJLRecognizedFace> recognizedFaces = facePipeline.processImage(img);
                
                if (recognizedFaces != null && !recognizedFaces.isEmpty()) {
                    if (recognizedFaces.size() > 1) {
                        System.err.println("Warning: Multiple faces detected. Using the first one found.");
                    }
                    DJLRecognizedFace mainFace = recognizedFaces.get(0);
                    
                    float[] embedding = mainFace.getEmbedding();

                    if (embedding != null) {
                        // Print the first few values for debug/verification
                        int previewCount = Math.min(5, embedding.length);
                        System.out.print("Generated embedding (first " + previewCount + " values): [");
                        for (int j = 0; j < previewCount; j++) {
                            System.out.print(embedding[j]);
                            if (j < previewCount - 1) System.out.print(", ");
                        }
                        System.out.println("]");

                        byte[] embeddingBytes = floatsToBytes(embedding);
                        FaceEmbedding faceEmbedding = new FaceEmbedding(dbStudentId, embeddingBytes);
                        Long embeddingId = embeddingDAO.save(faceEmbedding);
                        
                        if (embeddingId != null) {
                            System.out.println("Face embedding saved successfully with ID: " + embeddingId);
                            
                            // Now prompt for course enrollment
                            enrollStudentInCourses(dbStudentId);
                        } else {
                            System.err.println("Failed to save face embedding.");
                            // Consider cleanup: maybe delete the student entry if embedding fails?
                        }
                    } else {
                        System.err.println("Could not extract face embedding from the image (embedding was null).");
                    }
                    
                } else {
                    System.err.println("No faces detected or recognized in the provided image.");
                }
                
            } catch (IOException e) {
                System.err.println("Error loading image file: " + e.getMessage());
            } catch (ModelException e) {
                System.err.println("Error processing face image: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("An unexpected error occurred during embedding processing: " + e.getMessage());
                e.printStackTrace();
            }

        } else {
            System.err.println("Failed to save student details.");
        }
    }

    /**
     * Lists all existing students in the database.
     */
    private void listStudents() {
        System.out.println("\n=== Student List ===");
        List<Student> students = studentDAO.findAll();
        
        if (students.isEmpty()) {
            System.out.println("No students found.");
            return;
        }
        
        for (Student student : students) {
            System.out.printf("ID: %d, Student ID: %s, Name: %s %s%n",
                student.getId(),
                student.getStudentId(),
                student.getFirstName(),
                student.getLastName()
            );
            
            List<FaceEmbedding> embeddings = embeddingDAO.findByStudentId(student.getId());
            System.out.printf("Number of face embeddings: %d%n", embeddings.size());
            for (int i = 0; i < embeddings.size(); i++) {
                FaceEmbedding embedding = embeddings.get(i);
                byte[] data = embedding.getEmbeddingData();
                if (data != null && data.length >= 20) { // at least 5 floats
                    FloatBuffer fb = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN).asFloatBuffer();
                    float[] floats = new float[fb.remaining()];
                    fb.get(floats);
                    int previewCount = Math.min(5, floats.length);
                    System.out.print("  Embedding " + (i+1) + " (first " + previewCount + " values): [");
                    for (int j = 0; j < previewCount; j++) {
                        System.out.print(floats[j]);
                        if (j < previewCount - 1) System.out.print(", ");
                    }
                    System.out.println("]");
                } else {
                    System.out.println("  Embedding " + (i+1) + ": (no data or too short)");
                }
            }
            System.out.println("----------------------------------------");
        }
    }
    
    /**
     * Removes a student from the database.
     */
    private void removeStudent() {
        System.out.println("\n=== Remove Student ===");
        System.out.print("Enter institutional student ID (e.g., 230111214) to remove: "); // Clarify prompt
        String studentIdString = scanner.nextLine(); // Read as String
        
        // Find the student using the String ID
        Student student = studentDAO.findByStudentIdString(studentIdString); 
        
        if (student == null) {
            System.out.println("Student with ID '" + studentIdString + "' not found."); // More informative message
            return;
        }
        
        // Confirm with user using fetched student details
        System.out.printf("\nYou are about to remove student: ID=%d, StudentID=%s, Name=%s %s%n",
            student.getId(), // Show both IDs for clarity
            student.getStudentId(),
            student.getFirstName(), 
            student.getLastName());
        System.out.print("Are you sure? (y/n): ");
        String confirm = scanner.nextLine();
        
        if (confirm.equalsIgnoreCase("y")) {
            try {
                // Delete face embeddings
                System.out.println("Attempting to delete face embeddings for student...");
                embeddingDAO.deleteByStudentId(student.getId());
                System.out.println("Deleted face embeddings for student ID: " + student.getId());

                // Delete attendance records
                System.out.println("Attempting to delete attendance records for student...");
                attendanceDAO.deleteByStudentId(student.getId());
                System.out.println("Deleted attendance records for student ID: " + student.getId());

                // Delete student record
                System.out.println("Attempting to delete student record...");
                studentDAO.delete(student.getId());
                System.out.println("Student record deleted successfully.");

                System.out.println("Student, associated attendance records, and embeddings removed successfully.");
            } catch (Exception e) {
                System.err.println("An error occurred during deletion: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("Operation cancelled.");
        }
    }

    /**
     * Helper method to prompt user for input with validation and cancellation.
     * @param prompt The message to display to the user.
     * @param checkFilePath If true, validates the input as an existing file path.
     * @return The valid user input, or null if the user cancels or provides invalid input after retries.
     */
    private String promptForInput(String prompt, boolean checkFilePath) {
        String input = null;
        boolean validInput = false;
        int retries = 3; // Allow a few retries for file path

        while (!validInput && retries > 0) {
            System.out.print(prompt);
            input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("cancel")) {
                System.out.println("Operation cancelled.");
                return null;
            }

            if (input.isEmpty()) {
                System.out.println("Input cannot be empty. Please try again or type 'cancel'.");
                continue; // Ask again
            }

            if (checkFilePath) {
                if (Files.exists(Paths.get(input)) && Files.isRegularFile(Paths.get(input))) {
                    validInput = true; // Path is valid
                } else {
                    System.out.println("Invalid file path or not a regular file. Please check the path and try again or type 'cancel'.");
                    retries--;
                    if (retries == 0) {
                         System.out.println("Maximum retries reached for file path. Cancelling enrollment.");
                         return null;
                    }
                }
            } else {
                validInput = true; // Non-path input is considered valid if not empty
            }
        }
        return input;
    }

    /**
     * Utility method to convert float[] to byte[] with explicit BIG_ENDIAN byte order
     */
    private byte[] floatsToBytes(float[] floats) {
        if (floats == null) return null;
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4);
        buffer.order(ByteOrder.BIG_ENDIAN); // Explicitly set byte order
        FloatBuffer floatBuffer = buffer.asFloatBuffer();
        floatBuffer.put(floats);
        return buffer.array();
    }

    /**
     * Enrolls a student in courses.
     * 
     * @param studentId the student ID
     */
    private void enrollStudentInCourses(Long studentId) {
        List<Course> courses = courseDAO.findAll();
        if (courses.isEmpty()) {
            System.out.println("No courses available for enrollment. Please add courses first using the Course Management CLI.");
            return;
        }
        
        System.out.println("\n=== Course Enrollment ===");
        System.out.println("Available courses:");
        for (Course course : courses) {
            System.out.printf("%d. %s - %s%n", course.getCourseId(), course.getCourseCode(), course.getCourseName());
        }
        
        System.out.println("\nEnter course IDs to enroll the student in (comma-separated, e.g., '1,3,5'), or 'all' for all courses, or 'none' to skip:");
        String input = scanner.nextLine().trim();
        
        if (input.equalsIgnoreCase("none")) {
            System.out.println("Student not enrolled in any courses.");
            return;
        }
        
        if (input.equalsIgnoreCase("all")) {
            for (Course course : courses) {
                try {
                    courseDAO.enrollStudent(studentId, course.getCourseId());
                    System.out.printf("Enrolled in: %s - %s%n", course.getCourseCode(), course.getCourseName());
                } catch (Exception e) {
                    System.err.println("Failed to enroll in " + course.getCourseCode() + ": " + e.getMessage());
                }
            }
            return;
        }
        
        String[] courseIds = input.split(",");
        for (String courseIdStr : courseIds) {
            try {
                Integer courseId = Integer.parseInt(courseIdStr.trim());
                Course course = courseDAO.findById(courseId);
                if (course != null) {
                    courseDAO.enrollStudent(studentId, courseId);
                    System.out.printf("Enrolled in: %s - %s%n", course.getCourseCode(), course.getCourseName());
                } else {
                    System.out.println("Course with ID " + courseId + " not found.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid course ID: " + courseIdStr);
            } catch (Exception e) {
                System.err.println("Error enrolling in course: " + e.getMessage());
            }
        }
    }
    
    /**
     * Manages a student's course enrollments.
     */
    private void manageStudentCourses() {
        System.out.println("\n=== Manage Student Course Enrollment ===");
        
        // List all students
        List<Student> students = studentDAO.findAll();
        if (students.isEmpty()) {
            System.out.println("No students found.");
            return;
        }
        
        System.out.println("Available Students:");
        for (Student student : students) {
            System.out.printf("%d. %s %s (ID: %s)%n",
                    student.getId(),
                    student.getFirstName(),
                    student.getLastName(),
                    student.getStudentId());
        }
        
        System.out.print("\nEnter student ID (e.g., 230111214) to manage courses (or 0 to cancel): ");
        String studentIdStr = scanner.nextLine().trim();
        if (studentIdStr.equals("0")) return;
        
        // Find student by student ID (not database ID)
        Student student = null;
        for (Student s : students) {
            if (s.getStudentId().equals(studentIdStr)) {
                student = s;
                break;
            }
        }
        
        if (student == null) {
            System.out.println("Student with ID " + studentIdStr + " not found.");
            return;
        }
        
        // Get the database ID for further operations
        Long studentId = student.getId();
        
        System.out.printf("Managing courses for: %s %s (ID: %s)%n", 
                student.getFirstName(), student.getLastName(), student.getStudentId());
        
        // Show current enrollments
        List<Course> enrolledCourses = courseDAO.findCoursesByStudentId(studentId);
        System.out.println("\nCurrently enrolled in:");
        if (enrolledCourses.isEmpty()) {
            System.out.println("(No courses)");
        } else {
            for (Course course : enrolledCourses) {
                System.out.printf("%d. %s - %s%n", 
                        course.getCourseId(), course.getCourseCode(), course.getCourseName());
            }
        }
        
        // Show enrollment options
        System.out.println("\nOptions:");
        System.out.println("1. Add course enrollments");
        System.out.println("2. Remove course enrollments");
        System.out.println("0. Back to main menu");
        System.out.print("Enter choice: ");
        
        int choice;
        try {
            choice = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Returning to main menu.");
            return;
        }
        
        switch (choice) {
            case 1:
                enrollStudentInCourses(studentId);
                break;
            case 2:
                unenrollStudentFromCourses(studentId, enrolledCourses);
                break;
            case 0:
            default:
                return;
        }
    }
    
    /**
     * Unenrolls a student from selected courses.
     * 
     * @param studentId the student ID
     * @param enrolledCourses the courses the student is currently enrolled in
     */
    private void unenrollStudentFromCourses(Long studentId, List<Course> enrolledCourses) {
        if (enrolledCourses.isEmpty()) {
            System.out.println("Student is not enrolled in any courses.");
            return;
        }
        
        System.out.println("\nEnter course IDs to unenroll from (comma-separated, e.g., '1,3,5'), or 'all' to unenroll from all courses, or 'none' to cancel:");
        String input = scanner.nextLine().trim();
        
        if (input.equalsIgnoreCase("none")) {
            System.out.println("No changes made.");
            return;
        }
        
        if (input.equalsIgnoreCase("all")) {
            for (Course course : enrolledCourses) {
                try {
                    courseDAO.unenrollStudent(studentId, course.getCourseId());
                    System.out.printf("Unenrolled from: %s - %s%n", course.getCourseCode(), course.getCourseName());
                } catch (Exception e) {
                    System.err.println("Failed to unenroll from " + course.getCourseCode() + ": " + e.getMessage());
                }
            }
            return;
        }
        
        String[] courseIds = input.split(",");
        for (String courseIdStr : courseIds) {
            try {
                Integer courseId = Integer.parseInt(courseIdStr.trim());
                boolean found = false;
                
                for (Course course : enrolledCourses) {
                    if (course.getCourseId().equals(courseId)) {
                        found = true;
                        courseDAO.unenrollStudent(studentId, courseId);
                        System.out.printf("Unenrolled from: %s - %s%n", course.getCourseCode(), course.getCourseName());
                        break;
                    }
                }
                
                if (!found) {
                    System.out.println("Student is not enrolled in course with ID " + courseId);
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid course ID: " + courseIdStr);
            } catch (Exception e) {
                System.err.println("Error unenrolling from course: " + e.getMessage());
            }
        }
    }
    
    /**
     * Main method to run the enrollment CLI.
     */
    public static void main(String[] args) {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "INFO");
        System.setProperty("org.slf4j.simpleLogger.log.com.zaxxer.hikari", "WARN");
        
        try {
            EnrollmentCLI cli = new EnrollmentCLI();
            cli.start();
        } catch (Exception e) {
            System.err.println("Error running enrollment CLI: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
