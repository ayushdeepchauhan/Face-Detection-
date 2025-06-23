package com.attendance.model.recognition;

import com.attendance.model.db.Student;
import java.util.Objects;

/**
 * Represents a recognized student along with the confidence (similarity) of the match.
 */
public class IdentifiedFace {
    private final Student student;
    private final double similarity; // Higher similarity means higher confidence/match

    public IdentifiedFace(Student student, double similarity) {
        this.student = Objects.requireNonNull(student, "Student cannot be null");
        if (similarity < 0.0 || similarity > 1.0) { 
            System.err.printf("Warning: IdentifiedFace created with similarity outside expected 0-1 range: %.4f%n", similarity);
        }
        this.similarity = similarity;
    }

    public Student getStudent() {
        return student;
    }

    public double getSimilarity() { 
        return similarity;
    }

    @Override
    public String toString() {
        return "IdentifiedFace{" +
               "student=" + (student != null ? student.getStudentId() + " - " + student.getFirstName() : "null") +
               ", similarity=" + String.format("%.4f", similarity) +
               '}';
    }
}
