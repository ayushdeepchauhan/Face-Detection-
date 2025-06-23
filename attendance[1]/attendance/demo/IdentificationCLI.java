  package com.attendance.demo;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import com.attendance.dao.FaceEmbeddingDAO;
import com.attendance.dao.StudentDAO;
import com.attendance.dao.impl.JdbcFaceEmbeddingDAO;
import com.attendance.dao.impl.JdbcStudentDAO;
import com.attendance.exception.IdentificationException;
import com.attendance.exception.ModelException;
import com.attendance.facedetection.RetinaFaceDetection;
import com.attendance.facerecogntion.FaceNetRecognition;
import com.attendance.model.recognition.IdentifiedFace;
import com.attendance.model.db.Student;
import com.attendance.facealignment.DJLStandardFaceAligner;
import com.attendance.pipeline.DJLFaceProcessingPipeline;
import com.attendance.service.DJLFaceIdentifier;
import com.attendance.service.FaceIdentifier;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Scanner;

/**
 * A simple command-line interface for identifying a face from an image file.
 * Initializes its own dependencies (DataSource, DAOs, Services, Pipeline, Identifier).
 */
public class IdentificationCLI {

    private static final Logger logger = LoggerFactory.getLogger(IdentificationCLI.class);

    private final Scanner scanner;
    private final FaceIdentifier faceIdentifier;

    public IdentificationCLI(FaceIdentifier identifier) {
        this.faceIdentifier = identifier;
        this.scanner = new Scanner(System.in);
    }

    public void run() {
        System.out.println("\n=== Identify Face from Image ===");

        String imagePath = promptForImagePath();
        if (imagePath == null) {
            return;
        }

        try {
            Image img = ImageFactory.getInstance().fromFile(Paths.get(imagePath));
            logger.info("Attempting face identification...");

            Optional<IdentifiedFace> result = faceIdentifier.identifyFace(img);

            if (result.isPresent()) {
                IdentifiedFace identifiedFace = result.get();
                Student student = identifiedFace.getStudent();
                logger.info("Face identified: {} {} (ID: {}) with similarity: {:.4f}",
                        student.getFirstName(), student.getLastName(), student.getStudentId(), identifiedFace.getSimilarity());
                System.out.printf("Identification Result:%n -> Student: %s %s (ID: %s)%n -> Similarity: %.4f%n",
                        student.getFirstName(), student.getLastName(), student.getStudentId(), identifiedFace.getSimilarity());
            } else {
                logger.info("No matching face found in the database.");
                System.out.println("Identification Result: No match found.");
            }

        } catch (IdentificationException e) {
            logger.error("Identification failed: ", e);
            System.err.println("Error during identification: " + e.getMessage());
            if (e.getCause() != null) {
                logger.error("Caused by: ", e.getCause());
                System.err.println("  Cause: " + e.getCause().getMessage());
            }
        } catch (Exception e) {
            logger.error("An unexpected error occurred: ", e);
            System.err.println("An unexpected error occurred: " + e.getMessage());
        }
    }

    private String promptForImagePath() {
        System.out.print("Enter path to image file (or type 'cancel'): ");
        String input = scanner.nextLine().trim();

        if (input.equalsIgnoreCase("cancel")) {
            System.out.println("Operation cancelled.");
            return null;
        }

        if (input.isEmpty()) {
            System.out.println("Input cannot be empty.");
            return promptForImagePath();
        }

        if (Files.exists(Paths.get(input)) && Files.isRegularFile(Paths.get(input))) {
            return input;
        } else {
            System.out.println("Invalid file path or not a regular file. Please try again.");
            return promptForImagePath();
        }
    }

    public void closeMinimalResources() {
        scanner.close();
        logger.info("Minimal CLI resources released.");
    }

    public static void main(String[] args) {
        logger.info("IdentificationCLI main method started.");
        javax.sql.DataSource dataSource = null;
        FaceIdentifier faceIdentifier = null;
        IdentificationCLI cli = null;

        try {
            dataSource = com.attendance.db.DbConfig.getDataSource();
            System.out.println("Database connection pool initialized.");
            StudentDAO studentDAO = new JdbcStudentDAO(dataSource);
            FaceEmbeddingDAO embeddingDAO = new JdbcFaceEmbeddingDAO(dataSource);

            // ML Service Initialization (Use try-with-resources for AutoCloseable services)
            try (RetinaFaceDetection detectionServiceLocal = new RetinaFaceDetection();
                 FaceNetRecognition recognitionServiceLocal = new FaceNetRecognition()) {

                System.out.println("RetinaFaceDetection service initialized.");
                logger.info("RetinaFaceDetection service initialized successfully.");
                System.out.println("FaceNetRecognition service initialized.");
                logger.info("FaceNetRecognition service initialized successfully.");

                // Create Aligner and Pipeline (needed for DJLFaceIdentifier)
                DJLStandardFaceAligner faceAligner = new DJLStandardFaceAligner();
                try (DJLFaceProcessingPipeline pipeline = new DJLFaceProcessingPipeline(
                        detectionServiceLocal,
                        faceAligner,
                        recognitionServiceLocal, Optional.empty())) { // No visualization needed for CLI

                    // Create the identifier service using the pipeline
                    faceIdentifier = new DJLFaceIdentifier(pipeline, embeddingDAO, studentDAO, recognitionServiceLocal); // Use correct class and constructor
                    System.out.println("DJLFaceIdentifier service initialized.");
                    logger.info("DJLFaceIdentifier service initialized successfully.");

                    // Create and run the CLI
                    cli = new IdentificationCLI(faceIdentifier);

                    // Handle image path argument if provided
                    if (args.length > 0) {
                        String imagePath = args[0];
                        logger.info("Image path provided via command line: {}", imagePath);
                        if (Files.exists(Paths.get(imagePath)) && Files.isRegularFile(Paths.get(imagePath))) {
                            Path path = Paths.get(imagePath);
                            Image img = ImageFactory.getInstance().fromFile(path);
                            Optional<IdentifiedFace> result = faceIdentifier.identifyFace(img);
                            if (result.isPresent()) {
                                IdentifiedFace identifiedFace = result.get();
                                Student student = identifiedFace.getStudent();
                                System.out.printf("Identification Result:%n -> Student: %s %s (ID: %s)%n -> Similarity: %.4f%n",
                                        student.getFirstName(), student.getLastName(), student.getStudentId(), identifiedFace.getSimilarity());
                            } else {
                                System.out.println("Identification Result: No match found.");
                            }
                        } else {
                            logger.error("Invalid image path provided via command line: {}", imagePath);
                            System.err.println("Error: Invalid image file path provided.");
                        }
                    } else {
                        logger.warn("No image path provided via command line. Running interactive mode.");
                        cli.run(); // Call interactive mode
                    }

                }
            } catch (ModelException e) {
                logger.error("FATAL: Could not initialize RetinaFaceDetection service: {}", e.getMessage(), e);
                System.err.println("FATAL: Could not initialize RetinaFaceDetection service: " + e.getMessage());
                e.printStackTrace();
                
                return;
            }

        } catch (Exception e) {
            logger.error("FATAL: An unexpected error occurred: {}", e.getMessage(), e);
            System.err.println("FATAL: An unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
        } finally {
            logger.info("Entering finally block.");
            if (cli != null) {
                cli.closeMinimalResources();
            }
            logger.info("Exiting Identification CLI.");
            System.out.println("Exiting Identification CLI.");
        }
    }
}