package com.attendance.model.db;

public class Student {
    private Long id;
    private String studentId;
    private String firstName;
    private String lastName;

    // Constructors
    public Student() {}

    public Student(String studentId, String firstName, String lastName) {
        this.studentId = studentId;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    @Override
    public String toString() {
        return String.format("Student[id=%d, studentId='%s', name='%s %s']",
            id, studentId, firstName, lastName);
    }
}
