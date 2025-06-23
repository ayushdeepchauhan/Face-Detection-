package com.attendance.model.db;

import java.time.LocalDate;
import java.time.LocalTime;

public class AttendanceRecord {
    private Long id;
    private Long studentId;
    private Integer courseId;  // Added for course-based attendance
    private LocalDate classDate;
    private LocalTime entryTime;
    private String attendanceStatus;

    // Constructors
    public AttendanceRecord() {}

    public AttendanceRecord(Long studentId, LocalDate classDate, LocalTime entryTime, String attendanceStatus) {
        this.studentId = studentId;
        this.classDate = classDate;
        this.entryTime = entryTime;
        this.attendanceStatus = attendanceStatus;
    }
    
    public AttendanceRecord(Long studentId, Integer courseId, LocalDate classDate, LocalTime entryTime, String attendanceStatus) {
        this.studentId = studentId;
        this.courseId = courseId;
        this.classDate = classDate;
        this.entryTime = entryTime;
        this.attendanceStatus = attendanceStatus;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public Integer getCourseId() { return courseId; }
    public void setCourseId(Integer courseId) { this.courseId = courseId; }

    public LocalDate getClassDate() { return classDate; }
    public void setClassDate(LocalDate classDate) { this.classDate = classDate; }

    public LocalTime getEntryTime() { return entryTime; }
    public void setEntryTime(LocalTime entryTime) { this.entryTime = entryTime; }

    public String getAttendanceStatus() { return attendanceStatus; }
    public void setAttendanceStatus(String attendanceStatus) { this.attendanceStatus = attendanceStatus; }

    @Override
    public String toString() {
        return String.format("AttendanceRecord[id=%d, studentId=%d, courseId=%d, date=%s, time=%s, status=%s]",
            id, studentId, courseId, classDate, entryTime, attendanceStatus);
    }
}
