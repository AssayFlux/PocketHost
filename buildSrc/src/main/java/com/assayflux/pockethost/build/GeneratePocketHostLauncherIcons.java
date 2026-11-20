package com.assayflux.pockethost.build;

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

/** Rasterizes the canonical SVG into density-correct legacy and adaptive launcher assets. */
public abstract class GeneratePocketHostLauncherIcons extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getSourceSvg();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() {
        try {
            final int masterSize = 288;
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

            Map<String, Integer> densities = new LinkedHashMap<>();
            densities.put("mdpi", 1);
            densities.put("hdpi", 1);
            densities.put("xhdpi", 2);
            densities.put("xxhdpi", 3);
            densities.put("xxxhdpi", 4);

            File root = getOutputDirectory().get().getAsFile();
            for (Map.Entry<String, Integer> density : densities.entrySet()) {
                File folder = new File(root, "mipmap-" + density.getKey());
                Files.createDirectories(folder.toPath());
                int factor = density.getValue();
                writeScaled(master, 48 * factor, new File(folder, "ic_launcher.png"));
                writeScaled(master, 72 * factor, new File(folder, "ic_launcher_artwork.png"));
            }
        } catch (Exception error) {
            throw new GradleException("Could not rasterize Assets/Pocket Host 7.svg into Android launcher icons.", error);
        }
    }

    private static void writeScaled(BufferedImage source, int size, File destination) throws IOException {
        BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, size, size, null);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(target, "png", destination);
    }
}
