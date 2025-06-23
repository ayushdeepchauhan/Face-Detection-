package com.attendance.dao;

import com.attendance.model.db.AttendanceRecord;
import java.time.LocalDate;
import java.util.List;

public interface AttendanceDAO {
    Long save(AttendanceRecord record);
    List<AttendanceRecord> findByStudentId(Long studentId);
    List<AttendanceRecord> findByDate(LocalDate date);
    List<AttendanceRecord> findByCourseId(Integer courseId);
    List<AttendanceRecord> findByCourseIdAndStudentId(Integer courseId, Long studentId);
    List<AttendanceRecord> findAll();
    void delete(Long recordId);
    void deleteByStudentId(Long studentId);
}
