package com.attendance.dao;

import com.attendance.model.db.Course;
import com.attendance.model.db.Student;
import java.util.List;

/**
 * Data Access Object interface for Course entities.
 */
public interface CourseDAO {
    /**
     * Find a course by its ID.
     * 
     * @param id the course ID
     * @return the course, or null if not found
     */
    Course findById(Integer id);
    
    /**
     * Find a course by its code.
     * 
     * @param courseCode the course code (e.g., "TMA 101")
     * @return the course, or null if not found
     */
    Course findByCourseCode(String courseCode);
    
    /**
     * Get all courses.
     * 
     * @return list of all courses
     */
    List<Course> findAll();
    
    /**
     * Save a course.
     * 
     * @param course the course to save
     * @return the ID of the saved course
     */
    Integer save(Course course);
    
    /**
     * Delete a course.
     * 
     * @param id the ID of the course to delete
     */
    void delete(Integer id);
    
    /**
     * Find all students enrolled in a course.
     * 
     * @param courseId the course ID
     * @return list of students enrolled in the course
     */
    List<Student> findStudentsByCourseId(Integer courseId);
    
    /**
     * Enroll a student in a course.
     * 
     * @param studentId the student ID
     * @param courseId the course ID
     */
    void enrollStudent(Long studentId, Integer courseId);
    
    /**
     * Remove a student from a course.
     * 
     * @param studentId the student ID
     * @param courseId the course ID
     */
    void unenrollStudent(Long studentId, Integer courseId);
    
    /**
     * Find all courses a student is enrolled in.
     * 
     * @param studentId the student ID
     * @return list of courses the student is enrolled in
     */
    List<Course> findCoursesByStudentId(Long studentId);
}
