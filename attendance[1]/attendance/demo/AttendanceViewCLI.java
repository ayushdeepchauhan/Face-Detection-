package com.attendance.demo;

import com.attendance.dao.CourseDAO;
import com.attendance.dao.StudentDAO;
import com.attendance.model.db.Course;
import com.attendance.model.db.Student;
import com.attendance.service.AttendanceService;
import com.attendance.model.db.AttendanceRecord;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.util.*;
import java.io.InputStream;

public class AttendanceViewCLI {
    static {
        // Completely disable HikariCP logging through system properties
        System.setProperty("com.zaxxer.hikari.pool.HikariPool.level", "OFF");
        System.setProperty("com.zaxxer.hikari.HikariConfig.level", "OFF");
        System.setProperty("com.zaxxer.hikari.HikariDataSource.level", "OFF");
        System.setProperty("com.zaxxer.hikari.pool.level", "OFF");
        System.setProperty("com.zaxxer.hikari.level", "OFF");
    }

    private final CourseDAO courseDAO;
    private final StudentDAO studentDAO;
    private final AttendanceService attendanceService;
    private final Scanner scanner;
    

    public AttendanceViewCLI() {
        // Silence ALL logging for HikariCP
        java.util.logging.Logger.getLogger("com.zaxxer.hikari").setLevel(java.util.logging.Level.OFF);
        
        // Create a custom properties object with logging disabled
        Properties props = new Properties();
        try (InputStream is = getClass().getResourceAsStream("/application.properties")) {
            props.load(is);
        } catch (Exception e) {
            System.err.println("Failed to load properties: " + e.getMessage());
        }
        
        // Add logging-related properties
        props.setProperty("maximumPoolSize", "10"); // Match your existing setting
        
        HikariConfig config = new HikariConfig(props);
        // Disable ALL monitoring and metrics
        config.setMetricsTrackerFactory(null);
        config.setHealthCheckRegistry(null);
        config.setRegisterMbeans(false);
        config.setAllowPoolSuspension(false);
        config.setLeakDetectionThreshold(0);
        DataSource dataSource = new HikariDataSource(config);
        this.courseDAO = new com.attendance.dao.impl.JdbcCourseDAO(dataSource);
        this.studentDAO = new com.attendance.dao.impl.JdbcStudentDAO(dataSource);
        this.attendanceService = new AttendanceService();
        this.scanner = new Scanner(System.in);
    }

    public void start() {
        while (true) {
            System.out.println("\n=== Attendance View CLI ===");
            System.out.println("1. View attendance by course (subject-wise)");
            System.out.println("2. View attendance by student (student-wise)");
            System.out.println("0. Exit");
            System.out.print("Enter your choice: ");
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1":
                    viewAttendanceByCourse();
                    break;
                case "2":
                    viewAttendanceByStudent();
                    break;
                case "0":
                    System.out.println("Exiting Attendance View CLI.");
                    return;
                default:
                    System.out.println("Invalid choice. Please try again.");
            }
        }
    }

    private void viewAttendanceByCourse() {
        List<Course> courses = courseDAO.findAll();
        if (courses.isEmpty()) {
            System.out.println("No courses found.");
            return;
        }
        System.out.println("\nAvailable Courses:");
        for (int i = 0; i < courses.size(); i++) {
            Course c = courses.get(i);
            System.out.printf("%d. %s - %s\n", i + 1, c.getCourseCode(), c.getCourseName());
        }
        int idx = promptForInt("Select a course by number (or 0 to cancel): ");
        if (idx <= 0 || idx > courses.size()) return;
        Course selectedCourse = courses.get(idx - 1);

        // Fetch all enrolled students
        List<Student> enrolled = courseDAO.findStudentsByCourseId(selectedCourse.getCourseId());
        if (enrolled.isEmpty()) {
            System.out.println("No students enrolled in this course.");
            return;
        }

        // Fetch all attendance records for this course
        // TODO: Replace with correct DAO/service method if not present
        List<AttendanceRecord> records = attendanceService.getAttendanceRecordsByCourseId(selectedCourse.getCourseId());
        if (records == null || records.isEmpty()) {
            System.out.println("No attendance records found for this course.");
            return;
        }

        // Group attendance records by session (classDate + entryTime)
        Map<String, Map<Long, String>> sessionAttendance = new TreeMap<>();
        for (AttendanceRecord record : records) {
            String sessionKey = record.getClassDate() + " " + (record.getEntryTime() != null ? record.getEntryTime().toString() : "");
            sessionAttendance.putIfAbsent(sessionKey, new HashMap<>());
            sessionAttendance.get(sessionKey).put(record.getStudentId(), record.getAttendanceStatus());
        }

        // Display table for each session
        for (Map.Entry<String, Map<Long, String>> entry : sessionAttendance.entrySet()) {
            System.out.println("\nSession: " + entry.getKey());
            List<String[]> table = new ArrayList<>();
            table.add(new String[]{"Student ID", "Name", "Status"});
            for (Student s : enrolled) {
                String statusRaw = entry.getValue().get(s.getId());
                String status = (statusRaw != null && statusRaw.equalsIgnoreCase("PRESENT")) ? "Present" : "Absent";
                table.add(new String[]{s.getStudentId(), s.getFirstName() + " " + s.getLastName(), status});
            }
            printTable(table);
        }
    }

    private void viewAttendanceByStudent() {
        List<Student> students = studentDAO.findAll();
        if (students.isEmpty()) {
            System.out.println("No students found.");
            return;
        }
        System.out.println("\nAvailable Students:");
        for (int i = 0; i < students.size(); i++) {
            Student s = students.get(i);
            System.out.printf("%d. %s %s (ID: %s)\n", i + 1, s.getFirstName(), s.getLastName(), s.getStudentId());
        }
        int idx = promptForInt("Select a student by number (or 0 to cancel): ");
        if (idx <= 0 || idx > students.size()) return;
        Student selectedStudent = students.get(idx - 1);

        // Fetch all enrolled courses
        List<Course> enrolledCourses = courseDAO.findCoursesByStudentId(selectedStudent.getId());
        if (enrolledCourses.isEmpty()) {
            System.out.println("Student is not enrolled in any courses.");
            return;
        }

        for (Course course : enrolledCourses) {
            System.out.println("\nCourse: " + course.getCourseCode() + " - " + course.getCourseName());
            // TODO: Replace with correct DAO/service method if not present
            List<AttendanceRecord> records = attendanceService.getAttendanceRecordsByCourseIdAndStudentId(course.getCourseId(), selectedStudent.getId());
            if (records == null || records.isEmpty()) {
                System.out.println("  No attendance records for this course.");
                continue;
            }
            List<String[]> table = new ArrayList<>();
            table.add(new String[]{"Session Date", "Session Time", "Status"});
            for (AttendanceRecord rec : records) {
                String date = rec.getClassDate() != null ? rec.getClassDate().toString() : "";
                String time = rec.getEntryTime() != null ? rec.getEntryTime().toString() : "";
                String status = (rec.getAttendanceStatus() != null && rec.getAttendanceStatus().equalsIgnoreCase("PRESENT")) ? "Present" : "Absent";
                table.add(new String[]{date, time, status});
            }
            printTable(table);
        }
    }

    private int promptForInt(String message) {
        while (true) {
            System.out.print(message);
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) return 0;
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
            }
        }
    }

    // Simple ASCII table printer
    private void printTable(List<String[]> rows) {
        if (rows.isEmpty()) return;
        int[] widths = new int[rows.get(0).length];
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                widths[i] = Math.max(widths[i], row[i] == null ? 0 : row[i].length());
            }
        }
        StringBuilder fmt = new StringBuilder();
        for (int w : widths) fmt.append("| %-" + w + "s ");
        fmt.append("|\n");
        String sep = "";
        for (int w : widths) sep += "+" + "-".repeat(w + 2);
        sep += "+\n";
        System.out.print(sep);
        for (int r = 0; r < rows.size(); r++) {
            System.out.printf(fmt.toString(), (Object[]) rows.get(r));
            if (r == 0) System.out.print(sep);
        }
        System.out.print(sep);
    }

    public static void main(String[] args) {
        new AttendanceViewCLI().start();
    }
}
