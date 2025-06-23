package com.attendance.dao.impl;

import com.attendance.dao.StudentDAO;
import com.attendance.model.db.Student;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class JdbcStudentDAO implements StudentDAO {
    private final DataSource ds;

    public JdbcStudentDAO(DataSource ds) {
        this.ds = ds;
    }

    @Override
    public Student findById(Long id) {
        String sql = "SELECT id, student_id, first_name, last_name FROM students WHERE id = ?";
        try (Connection conn = ds.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding student by ID: " + id, e);
        }
        return null;
    }

    @Override
    public List<Student> findAll() {
        String sql = "SELECT id, student_id, first_name, last_name FROM students";
        List<Student> list = new ArrayList<>();
        try (Connection conn = ds.getConnection(); 
             Statement st = conn.createStatement(); 
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error retrieving all students", e);
        }
        return list;
    }

    @Override
    public Long save(Student student) {
        String sql = "INSERT INTO students(student_id, first_name, last_name) VALUES(?,?,?) " +
                    "ON CONFLICT (student_id) DO UPDATE SET " +
                    "first_name = EXCLUDED.first_name, " +
                    "last_name = EXCLUDED.last_name " +
                    "RETURNING id";
        try (Connection conn = ds.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, student.getStudentId());
            ps.setString(2, student.getFirstName());
            ps.setString(3, student.getLastName());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error saving student: " + student.getStudentId(), e);
        }
        return null;
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM students WHERE id = ?";
        try (Connection conn = ds.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting student with ID: " + id, e);
        }
    }

    @Override
    public Student findByStudentIdString(String studentId) {
        String sql = "SELECT id, student_id, first_name, last_name FROM students WHERE student_id = ?";
        try (Connection conn = ds.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding student by student ID string: " + studentId, e);
        }
        return null;
    }

    private Student mapRow(ResultSet rs) throws SQLException {
        Student student = new Student();
        student.setId(rs.getLong("id"));
        student.setStudentId(rs.getString("student_id"));
        student.setFirstName(rs.getString("first_name"));
        student.setLastName(rs.getString("last_name"));
        return student;
    }
}
