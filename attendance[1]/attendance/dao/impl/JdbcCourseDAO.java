package com.attendance.dao.impl;

import com.attendance.dao.CourseDAO;
import com.attendance.model.db.Course;
import com.attendance.model.db.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC implementation of the CourseDAO interface.
 */
public class JdbcCourseDAO implements CourseDAO {
    private static final Logger logger = LoggerFactory.getLogger(JdbcCourseDAO.class);
    private final DataSource ds;

    public JdbcCourseDAO(DataSource ds) {
        this.ds = ds;
    }

    @Override
    public Course findById(Integer id) {
        String sql = "SELECT course_id, course_code, course_name, description FROM courses WHERE course_id = ?";
        try (Connection conn = ds.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding course by ID: " + id, e);
        }
        return null;
    }

    @Override
    public Course findByCourseCode(String courseCode) {
        String sql = "SELECT course_id, course_code, course_name, description FROM courses WHERE course_code = ?";
        try (Connection conn = ds.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, courseCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding course by code: " + courseCode, e);
        }
        return null;
    }

    @Override
    public List<Course> findAll() {
        List<Course> list = new ArrayList<>();
        String sql = "SELECT course_id, course_code, course_name, description FROM courses";
        
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement(); 
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            // Check if the error is related to the table not existing
            if (e.getMessage().contains("relation \"courses\" does not exist")) {
                // Table doesn't exist yet - return empty list instead of throwing exception
                logger.warn("Courses table does not exist yet. Please run the schema_courses.sql script.");
                return list;  // Return empty list
            }
            throw new RuntimeException("Error retrieving all courses", e);
        }
        return list;
    }

    @Override
    public Integer save(Course course) {
        String sql = "INSERT INTO courses(course_code, course_name, description) VALUES(?,?,?) " +
                "ON CONFLICT (course_code) DO UPDATE SET " +
                "course_name = EXCLUDED.course_name, " +
                "description = EXCLUDED.description " +
                "RETURNING course_id";
        try (Connection conn = ds.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, course.getCourseCode());
            ps.setString(2, course.getCourseName());
            ps.setString(3, course.getDescription());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error saving course: " + course.getCourseCode(), e);
        }
        return null;
    }

    @Override
    public void delete(Integer id) {
        String sql = "DELETE FROM courses WHERE course_id = ?";
        try (Connection conn = ds.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting course with ID: " + id, e);
        }
    }

    @Override
    public List<Student> findStudentsByCourseId(Integer courseId) {
        String sql = "SELECT s.id, s.student_id, s.first_name, s.last_name " +
                "FROM students s " +
                "JOIN student_courses sc ON s.id = sc.student_id " +
                "WHERE sc.course_id = ?";
        List<Student> students = new ArrayList<>();
        try (Connection conn = ds.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Student student = new Student();
                    student.setId(rs.getLong("id"));
                    student.setStudentId(rs.getString("student_id"));
                    student.setFirstName(rs.getString("first_name"));
                    student.setLastName(rs.getString("last_name"));
                    students.add(student);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding students for course ID: " + courseId, e);
        }
        return students;
    }

    @Override
    public void enrollStudent(Long studentId, Integer courseId) {
        String sql = "INSERT INTO student_courses(student_id, course_id) VALUES(?,?) " +
                "ON CONFLICT (student_id, course_id) DO NOTHING";
        try (Connection conn = ds.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setInt(2, courseId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error enrolling student " + studentId + " in course " + courseId, e);
        }
    }

    @Override
    public void unenrollStudent(Long studentId, Integer courseId) {
        String sql = "DELETE FROM student_courses WHERE student_id = ? AND course_id = ?";
        try (Connection conn = ds.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setInt(2, courseId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error unenrolling student " + studentId + " from course " + courseId, e);
        }
    }

    @Override
    public List<Course> findCoursesByStudentId(Long studentId) {
        String sql = "SELECT c.course_id, c.course_code, c.course_name, c.description " +
                "FROM courses c " +
                "JOIN student_courses sc ON c.course_id = sc.course_id " +
                "WHERE sc.student_id = ?";
        List<Course> courses = new ArrayList<>();
        try (Connection conn = ds.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    courses.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding courses for student ID: " + studentId, e);
        }
        return courses;
    }

    private Course mapRow(ResultSet rs) throws SQLException {
        Course course = new Course();
        course.setCourseId(rs.getInt("course_id"));
        course.setCourseCode(rs.getString("course_code"));
        course.setCourseName(rs.getString("course_name"));
        course.setDescription(rs.getString("description"));
        return course;
    }
}
