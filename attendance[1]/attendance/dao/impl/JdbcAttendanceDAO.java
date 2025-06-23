package com.attendance.dao.impl;

import com.attendance.dao.AttendanceDAO;
import com.attendance.model.db.AttendanceRecord;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class JdbcAttendanceDAO implements AttendanceDAO {
    private final DataSource ds;

    public JdbcAttendanceDAO(DataSource ds) {
        this.ds = ds;
    }

    @Override
    public Long save(AttendanceRecord record) {
        String sql;
        if (record.getCourseId() != null) {
            sql = "INSERT INTO attendance_records(student_id, course_id, class_date, entry_time, attendance_status) " +
                  "VALUES(?,?,?,?,?) RETURNING id";
        } else {
            sql = "INSERT INTO attendance_records(student_id, class_date, entry_time, attendance_status) " +
                  "VALUES(?,?,?,?) RETURNING id";
        }
        
        try (Connection conn = ds.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, record.getStudentId());
            
            if (record.getCourseId() != null) {
                ps.setInt(2, record.getCourseId());
                ps.setDate(3, Date.valueOf(record.getClassDate()));
                ps.setTime(4, Time.valueOf(record.getEntryTime()));
                ps.setString(5, record.getAttendanceStatus());
            } else {
                ps.setDate(2, Date.valueOf(record.getClassDate()));
                ps.setTime(3, Time.valueOf(record.getEntryTime()));
                ps.setString(4, record.getAttendanceStatus());
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error saving attendance record", e);
        }
        return null;
    }

    @Override
    public List<AttendanceRecord> findByStudentId(Long studentId) {
        String sql = "SELECT id, student_id, course_id, class_date, entry_time, attendance_status " +
                    "FROM attendance_records WHERE student_id = ?";
        List<AttendanceRecord> list = new ArrayList<>();
        try (Connection conn = ds.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding attendance records for student: " + studentId, e);
        }
        return list;
    }

    @Override
    public List<AttendanceRecord> findByDate(LocalDate date) {
        String sql = "SELECT id, student_id, course_id, class_date, entry_time, attendance_status " +
                    "FROM attendance_records WHERE class_date = ?";
        List<AttendanceRecord> list = new ArrayList<>();
        try (Connection conn = ds.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding attendance records for date: " + date, e);
        }
        return list;
    }

    @Override
    public List<AttendanceRecord> findByCourseId(Integer courseId) {
        String sql = "SELECT id, student_id, course_id, class_date, entry_time, attendance_status " +
                    "FROM attendance_records WHERE course_id = ?";
        List<AttendanceRecord> list = new ArrayList<>();
        try (Connection conn = ds.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding attendance records for course: " + courseId, e);
        }
        return list;
    }

    @Override
    public List<AttendanceRecord> findByCourseIdAndStudentId(Integer courseId, Long studentId) {
        String sql = "SELECT id, student_id, course_id, class_date, entry_time, attendance_status " +
                    "FROM attendance_records WHERE course_id = ? AND student_id = ?";
        List<AttendanceRecord> list = new ArrayList<>();
        try (Connection conn = ds.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, courseId);
            ps.setLong(2, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding attendance records for course: " + courseId + " and student: " + studentId, e);
        }
        return list;
    }

    @Override
    public List<AttendanceRecord> findAll() {
        String sql = "SELECT id, student_id, course_id, class_date, entry_time, attendance_status FROM attendance_records ORDER BY class_date DESC, entry_time DESC";
        List<AttendanceRecord> list = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching attendance history", e);
        }
        return list;
    }

    @Override
    public void delete(Long recordId) {
        String sql = "DELETE FROM attendance_records WHERE id = ?";
        try (Connection conn = ds.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, recordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting attendance record: " + recordId, e);
        }
    }

    @Override
    public void deleteByStudentId(Long studentId) {
        String sql = "DELETE FROM attendance_records WHERE student_id = ?";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting attendance records for student: " + studentId, e);
        }
    }

    private AttendanceRecord mapRow(ResultSet rs) throws SQLException {
        AttendanceRecord record = new AttendanceRecord();
        record.setId(rs.getLong("id"));
        record.setStudentId(rs.getLong("student_id"));
        
        // Handle course_id which may be null in existing records
        try {
            Integer courseId = rs.getInt("course_id");
            if (!rs.wasNull()) {
                record.setCourseId(courseId);
            }
        } catch (SQLException e) {
            // Column might not exist in older records, ignore
        }
        
        record.setClassDate(rs.getDate("class_date").toLocalDate());
        record.setEntryTime(rs.getTime("entry_time").toLocalTime());
        record.setAttendanceStatus(rs.getString("attendance_status"));
        return record;
    }
}
