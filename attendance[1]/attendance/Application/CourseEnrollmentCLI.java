package com.attendance.Application;

import com.attendance.dao.CourseDAO;
import com.attendance.dao.StudentDAO;
import com.attendance.dao.impl.JdbcCourseDAO;
import com.attendance.dao.impl.JdbcStudentDAO;
import com.attendance.model.db.Course;
import com.attendance.model.db.Student;
import com.attendance.util.DatabaseSetup;





import java.util.List;
import java.util.Scanner;

/**
 * Command-line interface for managing courses and student enrollments.
 */
public class CourseEnrollmentCLI {
    private final CourseDAO courseDAO;
    private final StudentDAO studentDAO;
    private Scanner scanner;

    /**
     * Creates a new CourseEnrollmentCLI with the specified DAOs.
     */
    public CourseEnrollmentCLI() {
        // Initialize DataSource
        javax.sql.DataSource dataSource = com.attendance.db.DbConfig.getDataSource();

        // Initialize DAOs
        this.courseDAO = new JdbcCourseDAO(dataSource);
        this.studentDAO = new JdbcStudentDAO(dataSource);
        this.scanner = new Scanner(System.in);
    }

    /**
     * Starts the CLI.
     */
    public void start() {
        boolean running = true;
        while (running) {
            displayMenu();
            int choice;
            try {
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) {
                    System.out.println("Please enter a valid choice.");
                    continue;
                }
                choice = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
                continue;
            } catch (Exception e) {
                System.out.println("Error reading input: " + e.getMessage());
                System.out.println("Restarting input...");
                scanner = new Scanner(System.in);
                continue;
            }
            
            switch (choice) {
                case 1:
                    addCourse();
                    break;
                case 2:
                    listCourses();
                    break;
                case 3:
                    enrollStudentInCourse();
                    break;
                case 4:
                    listCoursesForStudent();
                    break;
                case 5:
                    listStudentsInCourse();
                    break;
                case 6:
                    removeCourse();
                    break;
                case 7:
                    unenrollStudentFromCourse();
                    break;
                case 0:
                    running = false;
                    break;
                default:
                    System.out.println("Invalid choice. Please try again.");
            }
        }

        scanner.close();
        System.out.println("Course Enrollment CLI exited.");
    }

    /**
     * Displays the main menu.
     */
    private void displayMenu() {
        System.out.println("\n=== Course Management System ===");
        System.out.println("1. Add New Course");
        System.out.println("2. List All Courses");
        System.out.println("3. Enroll Student in Course");
        System.out.println("4. List Courses for Student");
        System.out.println("5. List Students in Course");
        System.out.println("6. Remove Course");
        System.out.println("7. Unenroll Student from Course");
        System.out.println("0. Exit");
        System.out.print("Enter your choice: ");
    }

    /**
     * Adds a new course.
     */
    private void addCourse() {
        System.out.println("\n=== Add New Course ===");

        String courseCode = promptForInput("Enter course code (e.g., 'TMA 101'): ", false);
        if (courseCode == null) return;

        // Check if course code already exists
        Course existingCourse = courseDAO.findByCourseCode(courseCode);
        if (existingCourse != null) {
            System.out.println("A course with code '" + courseCode + "' already exists.");
            return;
        }

        String courseName = promptForInput("Enter course name: ", false);
        if (courseName == null) return;

        String description = promptForInput("Enter course description (optional): ", false);
        // Allow empty description

        Course course = new Course(courseCode, courseName, description);
        Integer courseId = courseDAO.save(course);

        if (courseId != null) {
            System.out.println("Course added successfully with ID: " + courseId);
        } else {
            System.out.println("Failed to add course.");
        }
    }

    /**
     * Lists all courses.
     */
    private void listCourses() {
        System.out.println("\n=== All Courses ===");
        List<Course> courses = courseDAO.findAll();

        if (courses.isEmpty()) {
            System.out.println("No courses found.");
            return;
        }

        for (Course course : courses) {
            System.out.printf("ID: %d, Code: %s, Name: %s%n",
                    course.getCourseId(),
                    course.getCourseCode(),
                    course.getCourseName());
            if (course.getDescription() != null && !course.getDescription().isEmpty()) {
                System.out.printf("  Description: %s%n", course.getDescription());
            }
            System.out.println("----------------------------------------");
        }
    }

    /**
     * Enrolls a student in a course.
     */
    private void enrollStudentInCourse() {
        System.out.println("\n=== Enroll Student in Course ===");

        // List all students
        List<Student> students = studentDAO.findAll();
        if (students.isEmpty()) {
            System.out.println("No students found. Please add students first.");
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

        System.out.print("Enter student ID (e.g., 230111214) or 0 to cancel: ");
        String studentIdStr = scanner.nextLine().trim();
        if (studentIdStr.equals("0")) return;
        
        Student student = findStudentByStudentId(studentIdStr);
        if (student == null) {
            System.out.println("Student with ID " + studentIdStr + " not found.");
            return;
        }
        
        // Get the database ID for further operations
        Long studentId = student.getId();
        String studentName = student.getFirstName() + " " + student.getLastName();

        // Get list of courses
        List<Course> allCourses = courseDAO.findAll();
        if (allCourses.isEmpty()) {
            System.out.println("No courses found. Please add courses first.");
            return;
        }

        // Start the enrollment loop
        boolean continueEnrolling = true;
        while (continueEnrolling) {
            // Get updated list of courses the student is already enrolled in
            List<Course> enrolledCourses = courseDAO.findCoursesByStudentId(studentId);
            
            // Display available courses
            System.out.println("\nAvailable Courses:");
            for (Course course : allCourses) {
                boolean enrolled = false;
                for (Course ec : enrolledCourses) {
                    if (ec.getCourseId().equals(course.getCourseId())) {
                        enrolled = true;
                        break;
                    }
                }
                System.out.printf("%d. %s - %s %s%n",
                        course.getCourseId(),
                        course.getCourseCode(),
                        course.getCourseName(),
                        enrolled ? "(ENROLLED)" : "");
            }
            
            // Prompt for course selection
            Integer courseId = promptForInt("\nEnter course ID to enroll in (or 0 to finish): ");
            if (courseId == 0) {
                System.out.println("Finished enrolling " + studentName + " in courses.");
                return;
            }
            
            // Find the selected course
            Course selectedCourse = null;
            for (Course c : allCourses) {
                if (c.getCourseId().equals(courseId)) {
                    selectedCourse = c;
                    break;
                }
            }
            
            if (selectedCourse == null) {
                System.out.println("Course with ID " + courseId + " not found.");
                continue;
            }
            
            // Check if already enrolled
            boolean alreadyEnrolled = false;
            for (Course ec : enrolledCourses) {
                if (ec.getCourseId().equals(courseId)) {
                    alreadyEnrolled = true;
                    break;
                }
            }
            
            if (alreadyEnrolled) {
                System.out.println("Student is already enrolled in this course.");
                continue;
            }
            
            // Enroll student in course
            try {
                courseDAO.enrollStudent(studentId, courseId);
                System.out.printf("Student %s enrolled in %s - %s successfully.%n",
                        studentName,
                        selectedCourse.getCourseCode(), selectedCourse.getCourseName());
            } catch (Exception e) {
                System.out.println("Failed to enroll student: " + e.getMessage());
            }
        }
    }

    /**
     * Lists all students enrolled in a course.
     */
    private void listStudentsInCourse() {
        System.out.println("\n=== List Students in Course ===");

        // List all courses
        List<Course> courses = courseDAO.findAll();
        if (courses.isEmpty()) {
            System.out.println("No courses found.");
            return;
        }

        System.out.println("Available Courses:");
        for (Course course : courses) {
            System.out.printf("%d. %s - %s%n",
                    course.getCourseId(),
                    course.getCourseCode(),
                    course.getCourseName());
        }

        Integer courseId = promptForInt("Enter course ID to list students (or 0 to cancel): ");
        if (courseId == 0) return;

        Course course = courseDAO.findById(courseId);
        if (course == null) {
            System.out.println("Course with ID " + courseId + " not found.");
            return;
        }

        List<Student> students = courseDAO.findStudentsByCourseId(courseId);
        if (students.isEmpty()) {
            System.out.printf("No students enrolled in %s - %s.%n",
                    course.getCourseCode(), course.getCourseName());
            return;
        }

        System.out.printf("Students enrolled in %s - %s:%n",
                course.getCourseCode(), course.getCourseName());
        for (Student student : students) {
            System.out.printf("ID: %d, Student ID: %s, Name: %s %s%n",
                    student.getId(),
                    student.getStudentId(),
                    student.getFirstName(),
                    student.getLastName());
        }
    }

    /**
     * Lists all courses a student is enrolled in.
     */
    private void listCoursesForStudent() {
        System.out.println("\n=== List Courses for Student ===");

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

        System.out.print("Enter student ID (e.g., 230111214) or 0 to cancel: ");
        String studentIdStr = scanner.nextLine().trim();
        if (studentIdStr.equals("0")) return;
        
        Student student = findStudentByStudentId(studentIdStr);
        if (student == null) {
            System.out.println("Student with ID " + studentIdStr + " not found.");
            return;
        }
        
        // Get the database ID for further operations
        Long studentId = student.getId();

        List<Course> courses = courseDAO.findCoursesByStudentId(studentId);
        if (courses.isEmpty()) {
            System.out.printf("Student %s %s is not enrolled in any courses.%n",
                    student.getFirstName(), student.getLastName());
            return;
        }

        System.out.printf("Courses for %s %s:%n", student.getFirstName(), student.getLastName());
        for (Course course : courses) {
            System.out.printf("ID: %d, Code: %s, Name: %s%n",
                    course.getCourseId(),
                    course.getCourseCode(),
                    course.getCourseName());
        }
    }

    /**
     * Removes a course.
     */
    private void removeCourse() {
        System.out.println("\n=== Remove Course ===");

        // List all courses
        List<Course> courses = courseDAO.findAll();
        if (courses.isEmpty()) {
            System.out.println("No courses found.");
            return;
        }

        System.out.println("Available Courses:");
        for (Course course : courses) {
            System.out.printf("%d. %s - %s%n",
                    course.getCourseId(),
                    course.getCourseCode(),
                    course.getCourseName());
        }

        Integer courseId = promptForInt("Enter course ID to remove (or 0 to cancel): ");
        if (courseId == 0) return;

        Course course = courseDAO.findById(courseId);
        if (course == null) {
            System.out.println("Course with ID " + courseId + " not found.");
            return;
        }

        System.out.printf("Are you sure you want to remove course '%s - %s'? (y/n): ",
                course.getCourseCode(), course.getCourseName());
        String confirm = scanner.nextLine().trim().toLowerCase();

        if (confirm.equals("y") || confirm.equals("yes")) {
            try {
                courseDAO.delete(courseId);
                System.out.println("Course removed successfully.");
            } catch (Exception e) {
                System.out.println("Failed to remove course: " + e.getMessage());
            }
        } else {
            System.out.println("Operation cancelled.");
        }
    }

    /**
     * Helper method to find a student by their student ID (not database ID)
     * 
     * @param studentIdStr The student ID string (e.g., 230111214)
     * @return The student object, or null if not found
     */
    private Student findStudentByStudentId(String studentIdStr) {
        List<Student> students = studentDAO.findAll();
        for (Student student : students) {
            if (student.getStudentId().equals(studentIdStr)) {
                return student;
            }
        }
        return null;
    }
    
    /**
     * Unenrolls a student from a course.
     */
    private void unenrollStudentFromCourse() {
        System.out.println("\n=== Unenroll Student from Course ===");

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

        System.out.print("Enter student ID (e.g., 230111214) or 0 to cancel: ");
        String studentIdStr = scanner.nextLine().trim();
        if (studentIdStr.equals("0")) return;
        
        Student student = findStudentByStudentId(studentIdStr);
        if (student == null) {
            System.out.println("Student with ID " + studentIdStr + " not found.");
            return;
        }
        
        // Get the database ID for further operations
        Long studentId = student.getId();

        // List courses for this student
        List<Course> courses = courseDAO.findCoursesByStudentId(studentId);
        if (courses.isEmpty()) {
            System.out.printf("Student %s %s is not enrolled in any courses.%n",
                    student.getFirstName(), student.getLastName());
            return;
        }

        System.out.printf("Courses for %s %s:%n", student.getFirstName(), student.getLastName());
        for (Course course : courses) {
            System.out.printf("%d. %s - %s%n",
                    course.getCourseId(),
                    course.getCourseCode(),
                    course.getCourseName());
        }

        Integer courseId = promptForInt("Enter course ID to unenroll from (or 0 to cancel): ");
        if (courseId == 0) return;

        // Verify the course exists and student is enrolled
        boolean enrolled = false;
        for (Course course : courses) {
            if (course.getCourseId().equals(courseId)) {
                enrolled = true;
                break;
            }
        }

        if (!enrolled) {
            System.out.println("Student is not enrolled in the selected course.");
            return;
        }

        // Unenroll student
        try {
            courseDAO.unenrollStudent(studentId, courseId);
            System.out.println("Student unenrolled from course successfully.");
        } catch (Exception e) {
            System.out.println("Failed to unenroll student: " + e.getMessage());
        }
    }

    /**
     * Prompts for user input with validation.
     *
     * @param message the prompt message
     * @param validatePath whether to validate as a file path
     * @return the user input, or null if cancelled
     */
    private String promptForInput(String message, boolean validatePath) {
        while (true) {
            System.out.print(message);
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("cancel")) {
                return null;
            }

            if (input.isEmpty()) {
                if (!message.contains("optional")) {
                    System.out.println("Input cannot be empty. Please try again or type 'cancel'.");
                    continue;
                }
            }

            return input;
        }
    }

    /**
     * Prompts for an integer input.
     *
     * @param message the prompt message
     * @return the integer input
     */
    private Integer promptForInt(String message) {
        while (true) {
            System.out.print(message);
            try {
                return Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
            }
        }
    }

    /**
            }
        }
    }

    /**
     * Main method to run the CLI.
     */
    public static void main(String[] args) {
        // Set logging levels first to reduce verbosity
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "WARN");
        System.setProperty("org.slf4j.simpleLogger.log.com.zaxxer.hikari", "WARN");
        System.setProperty("org.slf4j.simpleLogger.log.com.attendance", "INFO");
        
        try {
            // Initialize database schema if needed
            try {
                System.out.println("Checking and initializing database schema...");
                DatabaseSetup.setupDatabase();
                System.out.println("Database schema initialized successfully.");
            } catch (Exception e) {
                System.err.println("Warning: Could not initialize database schema: " + e.getMessage());
                System.err.println("You may need to manually run the schema_courses.sql script.");
            }

            CourseEnrollmentCLI cli = new CourseEnrollmentCLI();
            cli.start();
        } catch (Exception e) {
            System.err.println("Error running course enrollment CLI: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
