package com.frank.material.tools;

import cn.hutool.core.exceptions.ExceptionUtil;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author: maxinhang.
 */
public class WatermarkUtil {

    /**
     * 水印倾斜度.
     */
    private static final int WATERMARK_ANGLE = 45;

    /**
     * 文本绘制的偏移量.
     */
    private static final int TEXT_DRAW_OFFSET = 10;

    /**
     * 文本在 X 轴方向上的重复因子.
     */
    private static final int TEXT_X_REPEAT_FACTOR = 1;

    /**
     * 文本在 Y 轴方向上的重复因子.
     */
    private static final int TEXT_Y_REPEAT_FACTOR = 4;

    /**
     * 视频比特率.
     */
    private static final int VIDEO_BIT_RATE = 2000000;


    /**
     * 在图片上添加水印文字.
     *
     * @param inputPath  输入图片的路径
     * @param outputPath 输出图片的路径
     * @param text       要添加的水印文字
     * @param fontSize   水印文字的字体大小
     * @return 如果添加水印成功返回true，否则返回false
     */
    public static boolean addWatermarkToPic(String inputPath, String outputPath, String text, int fontSize) {
        try {
            // Load the image
            BufferedImage originImage = ImageIO.read(new File(inputPath));
            int h = originImage.getHeight();
            int w = originImage.getWidth();

            // Create watermark image
            BufferedImage textPic =
                    makeTextPicture(h, w, text, fontSize, WATERMARK_ANGLE, new Color(169, 169, 169, 51));

            // Add watermark
            BufferedImage watermarkedImage = addWatermarkToImage(originImage, textPic);

            // Save the result
            ImageIO.write(watermarkedImage, "PNG", new File(outputPath));
            return true;
        } catch (Exception e) {
            System.out.println("add watermark to pic fail! error :" + ExceptionUtil.stacktraceToString(e));
        }
        return false;
    }

    /**
     * 为视频添加水印.
     *
     * @param inputPath  视频输入路径
     * @param outputPath 视频输出路径
     * @param text       要添加的水印文本
     * @param fontSize   水印文本的字体大小
     * @return 如果成功添加水印返回true，否则返回false
     */
    public static boolean addWatermarkToVideo(String inputPath, String outputPath, String text, int fontSize) {
        try {
            // Use JavaCV to handle the video file
            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputPath);
            grabber.start();

            // Get video properties
            int width = grabber.getImageWidth();
            int height = grabber.getImageHeight();
            double frameRate = grabber.getFrameRate();
            int audioChannels = grabber.getAudioChannels();
            int audioCodec = grabber.getAudioCodec();
            int sampleRate = grabber.getSampleRate();

            // Set an appropriate video bitrate for better quality (adjust as needed)

            // Create watermark image
            BufferedImage textPic =
                    makeTextPicture(height, width, text, fontSize, WATERMARK_ANGLE, new Color(169, 169, 169, 51));

            // Prepare for video output
            FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, width, height);
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setFormat("mp4");
            recorder.setFrameRate(frameRate);
            recorder.setVideoBitrate(VIDEO_BIT_RATE);
            recorder.setAudioChannels(audioChannels);
            recorder.setAudioCodec(audioCodec);
            recorder.setSampleRate(sampleRate);
            recorder.start();

            // Process each frame and add watermark
            Frame frame;
            Java2DFrameConverter java2DFrameConverter = new Java2DFrameConverter();
            while ((frame = grabber.grabFrame()) != null) {
                if (frame.image != null) {
                    BufferedImage frameImage = java2DFrameConverter.convert(frame);
                    addWatermarkToFrame(frameImage, textPic);
                    recorder.record(java2DFrameConverter.convert(frameImage));
                } else if (frame.samples != null) {
                    // Record audio frame if available
                    recorder.record(frame);
                }
            }

            // Close resources
            grabber.stop();
            recorder.stop();
            return true;
        } catch (Exception e) {
            System.out.println("add watermark to video fail! error :" + ExceptionUtil.stacktraceToString(e));
        }
        return false;
    }

    private static BufferedImage makeTextPicture(int h, int w, String text, int fontSize, int angle, Color color)
            throws FontFormatException, IOException {
        // Create a transparent image for the watermark
        BufferedImage textPic = new BufferedImage(4 * h, 4 * w, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = textPic.createGraphics();

        // Load font (similar to loading a custom font in Python)
        Resource resource = new ClassPathResource("fonts/MiSans-Medium.ttf");
        InputStream inputStream = resource.getInputStream();
        Font font = Font.createFont(Font.TRUETYPE_FONT, inputStream).deriveFont((float) fontSize);
        g.setFont(font);
        g.setColor(color);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Set the rotation angle
        g.rotate(Math.toRadians(angle), textPic.getWidth() / 2, textPic.getHeight() / 2);

        // Draw the text repeatedly in a grid pattern
        for (int x = TEXT_DRAW_OFFSET; x < textPic.getWidth() - TEXT_DRAW_OFFSET; x += TEXT_X_REPEAT_FACTOR * fontSize * text.length()) {
            for (int y = TEXT_DRAW_OFFSET; y < textPic.getHeight() - TEXT_DRAW_OFFSET; y += TEXT_Y_REPEAT_FACTOR * fontSize) {
                g.drawString(text, x, y);
            }
        }

        // Dispose of the graphics object
        g.dispose();

        // Crop the image to match the original dimensions
        return textPic.getSubimage(h, w, 2 * h, 2 * w);
    }

    private static BufferedImage addWatermarkToImage(BufferedImage originImage, BufferedImage textPic) {
        // Create a new image to composite the watermark over the original image
        Graphics2D g = originImage.createGraphics();
        g.drawImage(textPic, 0, 0, null);
        g.dispose();
        return originImage;
    }

    private static void addWatermarkToFrame(BufferedImage frame, BufferedImage textPic) {
        // Add the watermark to a video frame (image)
        Graphics2D g = frame.createGraphics();
        g.drawImage(textPic, 0, 0, null);
        g.dispose();
    }

    public static void main(String[] args) {
        String text = "Watermark";
        int fontSize = 25;
        String input_picture_path = "/Users/maxinhang/Code/material-tools-java/src/main/resources/input/input_image.jpg";
        String output_picture_path = "/Users/maxinhang/Code/material-tools-java/src/main/resources/ouput/output_image.png";
        addWatermarkToPic(input_picture_path, output_picture_path, text, fontSize);
        String input_video_path = "/Users/maxinhang/Code/material-tools-java/src/main/resources/input/input_video.mp4";
        String output_video_path = "/Users/maxinhang/Code/material-tools-java/src/main/resources/ouput/output_video.mp4";
        addWatermarkToVideo(input_video_path, output_video_path, text, fontSize);
    }
}
