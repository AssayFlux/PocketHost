package com.assayflux.pockethost.build;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Generates Pocket Host launcher resources from the canonical Pocket Host 7 SVG.
 * Legacy launcher PNGs are square, opaque white-backed 48dp assets. Adaptive
 * foreground artwork is centered in Android's 66dp safe zone on a 108dp canvas.
 */
public abstract class GeneratePocketHostLauncherIcons extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getSourceSvg();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() {
        try {
            final int masterSize = 432; // 108dp at xxxhdpi
            PNGTranscoder transcoder = new PNGTranscoder();
            transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, (float) masterSize);
            transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, (float) masterSize);
            ByteArrayOutputStream raster = new ByteArrayOutputStream();
            transcoder.transcode(
                new TranscoderInput(getSourceSvg().get().getAsFile().toURI().toString()),
                new TranscoderOutput(raster)
            );
            BufferedImage master = ImageIO.read(new ByteArrayInputStream(raster.toByteArray()));
            if (master == null || master.getWidth() != masterSize || master.getHeight() != masterSize) {
                throw new GradleException("Pocket Host SVG rasterizer returned an unexpected image size.");
            }

            Map<String, Double> densities = new LinkedHashMap<>();
            densities.put("mdpi", 1);
            densities.put("hdpi", 1.5);
            densities.put("xhdpi", 2);
            densities.put("xxhdpi", 3);
            densities.put("xxxhdpi", 4);

            File root = getOutputDirectory().get().getAsFile();
            for (Map.Entry<String, Double> density : densities.entrySet()) {
                File folder = new File(root, "mipmap-" + density.getKey());
                Files.createDirectories(folder.toPath());
                double factor = density.getValue();
                writeWhiteBacked(master, (int) Math.round(48 * factor), new File(folder, "ic_launcher.png"));
                writeWhiteBacked(master, (int) Math.round(72 * factor), new File(folder, "ic_launcher_artwork.png"));
                writeAdaptiveForeground(master, (int) Math.round(108 * factor), factor,
                    new File(folder, "ic_launcher_foreground.png"));
            }

            File adaptive = new File(root, "mipmap-anydpi-v26");
            Files.createDirectories(adaptive.toPath());
            Files.writeString(new File(adaptive, "ic_launcher.xml").toPath(),
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "    <background android:drawable=\"@android:color/white\" />\n" +
                "    <foreground android:drawable=\"@mipmap/ic_launcher_foreground\" />\n" +
                "</adaptive-icon>\n");
            verifyOpaqueWhiteBackedPngs(root, densities);
        } catch (Exception error) {
            throw new GradleException("Could not generate verified white-background launcher icons from Assets/Pocket Host 7.svg: "
                + error.getClass().getSimpleName() + ": " + error.getMessage(), error);
        }
    }

    private static void configure(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private static void writeWhiteBacked(BufferedImage source, int size, File destination) throws IOException {
        BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            configure(graphics);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, size, size);
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.drawImage(source, 0, 0, size, size, null);
        } finally {
            graphics.dispose();
        }
        writePng(target, destination);
    }

    private static void writeAdaptiveForeground(BufferedImage source, int canvasSize, double density, File destination)
            throws IOException {
        BufferedImage target = new BufferedImage(canvasSize, canvasSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        try {
            configure(graphics);
            int safeArtworkSize = (int) Math.round(66 * density);
            int offset = (canvasSize - safeArtworkSize) / 2;
            graphics.drawImage(source, offset, offset, offset + safeArtworkSize, offset + safeArtworkSize,
                0, 0, source.getWidth(), source.getHeight(), null);
        } finally {
            graphics.dispose();
        }
        writePng(target, destination);
    }

    private static void writePng(BufferedImage image, File destination) throws IOException {
        if (!ImageIO.write(image, "png", destination)) {
            throw new IOException("No PNG ImageIO writer is available for " + destination);
        }
    }

    private static void verifyOpaqueWhiteBackedPngs(File root, Map<String, Double> densities) throws IOException {
        for (Map.Entry<String, Integer> density : densities.entrySet()) {
            int expected = (int) Math.round(48 * density.getValue());
            File png = new File(new File(root, "mipmap-" + density.getKey()), "ic_launcher.png");
            BufferedImage image = ImageIO.read(png);
            if (image == null || image.getWidth() != expected || image.getHeight() != expected
                || image.getColorModel().hasAlpha()) {
                throw new GradleException("Invalid launcher icon dimensions or alpha channel: " + png);
            }
            if ((image.getRGB(0, 0) & 0x00ffffff) != 0x00ffffff
                || (image.getRGB(expected - 1, expected - 1) & 0x00ffffff) != 0x00ffffff) {
                throw new GradleException("Launcher icon background is not solid white: " + png);
            }
        }
    }
}
