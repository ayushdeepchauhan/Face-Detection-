package com.attendance.facealignment;

import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.BoundingBox;
import ai.djl.modality.cv.output.Rectangle;
import ai.djl.modality.cv.output.Landmark;
import ai.djl.modality.cv.output.Point;
import com.attendance.exception.ModelException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.List;
import java.util.Arrays;

/**
 * DJL native face aligner: uses bounding box to generate synthetic eye landmarks
 * and delegates to StandardFaceAligner.
 */
public class DJLStandardFaceAligner implements DJLFaceAligner {

    private final StandardFaceAligner standardAligner;

    public DJLStandardFaceAligner() {
        this.standardAligner = new StandardFaceAligner();
    }

    @Override
    public Image alignFace(Image originalImage, DetectedObjects.DetectedObject detectedObject) throws ModelException {
        return alignFaceWithVisualization(originalImage, detectedObject, Optional.empty(), Optional.empty());
    }

    @Override
    public Image alignFaceWithVisualization(
        Image originalImage,
        DetectedObjects.DetectedObject detectedObject,
        Optional<Path> outputDir,
        Optional<String> filePrefix) throws ModelException {

        if (originalImage == null) {
            throw new IllegalArgumentException("Original image cannot be null");
        }
        if (detectedObject == null) {
            throw new IllegalArgumentException("Detected object cannot be null");
        }

        BoundingBox bb = detectedObject.getBoundingBox();

        if (bb instanceof Landmark) {
            return standardAligner.alignFaceWithVisualization(originalImage, (Landmark) bb, outputDir, filePrefix);
        } else if (bb instanceof Rectangle) {
            Rectangle rect = (Rectangle) bb;
            double x = rect.getX();
            double y = rect.getY();
            double width = rect.getWidth();
            double height = rect.getHeight();

            List<Point> pts = Arrays.asList(
                new Point(x + width * 0.3, y + height * 0.4),
                new Point(x + width * 0.7, y + height * 0.4)
            );
            Landmark landmark = new Landmark((float) x, (float) y, (float) width, (float) height, pts);
            return standardAligner.alignFaceWithVisualization(originalImage, landmark, outputDir, filePrefix);
        } else {
            throw new ModelException("Unsupported bounding box type for alignment");
        }
    }
}
