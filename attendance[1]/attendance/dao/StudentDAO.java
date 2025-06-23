package com.attendance.dao;

import com.attendance.model.db.Student;
import java.util.List;

public interface StudentDAO {
    Student findById(Long id);
    List<Student> findAll();
    Long save(Student student);
    void delete(Long id);
    Student findByStudentIdString(String studentId);
}
