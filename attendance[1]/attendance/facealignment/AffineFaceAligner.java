package com.attendance.facealignment;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import ai.djl.modality.cv.output.Point;

public class AffineFaceAligner {
    private final int targetWidth;
    private final int targetHeight;
    private final double leftEyePositionX;
    private final double rightEyePositionX;
    private final double eyePositionY;

    public AffineFaceAligner() {
        this(160, 160, 0.35, 0.65, 0.4);
    }

    public AffineFaceAligner(
            int targetWidth,
            int targetHeight,
            double leftEyePositionX,
            double rightEyePositionX,
            double eyePositionY) {
        this.targetWidth = targetWidth;
        this.targetHeight = targetHeight;
        this.leftEyePositionX = leftEyePositionX;
        this.rightEyePositionX = rightEyePositionX;
        this.eyePositionY = eyePositionY;
    }

    /**
     * Apply affine warp, render on expanded canvas, then crop centered region to target size.
     */
    public BufferedImage warp(BufferedImage faceRegion, Point leftEyeRelative, Point rightEyeRelative) {
        // compute angle and scale
        double dX = rightEyeRelative.getX() - leftEyeRelative.getX();
        double dY = rightEyeRelative.getY() - leftEyeRelative.getY();
        double angleRad = Math.atan2(dY, dX);
        double desiredEyeDist = (rightEyePositionX - leftEyePositionX) * targetWidth;
        double actualEyeDist = Math.hypot(dX, dY);
        double scale = desiredEyeDist / actualEyeDist;

        // compute target eye offset in output
        double targetLeftX = leftEyePositionX * targetWidth;
        double targetLeftY = eyePositionY * targetHeight;

        // expanded canvas size to avoid black corners (diagonal of faceRegion)
        int w = faceRegion.getWidth();
        int h = faceRegion.getHeight();
        int diag = (int) Math.ceil(Math.hypot(w, h) * scale);
        int cx = diag / 2;
        int cy = diag / 2;
        BufferedImage big = new BufferedImage(diag, diag, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = big.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // affine transform: center, scale, rotate, shift by eye relative
        AffineTransform t = new AffineTransform();
        t.translate(cx, cy);
        t.scale(scale, scale);
        t.rotate(-angleRad);
        t.translate(-leftEyeRelative.getX(), -leftEyeRelative.getY());
        g.drawImage(faceRegion, t, null);
        g.dispose();

        // crop centered target rectangle
        int cropX = (int) Math.round(cx - targetLeftX);
        int cropY = (int) Math.round(cy - targetLeftY);
        return big.getSubimage(cropX, cropY, targetWidth, targetHeight);
    }
}
