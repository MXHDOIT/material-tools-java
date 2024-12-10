package com.frank.material.tools;

import cn.hutool.core.exceptions.ExceptionUtil;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;
import org.springframework.core.io.ClassPathResource;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;

@Slf4j
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

    private static final String FONT_FILE_DIR_PATH = "/tmp/fonts";
    private static final Path FONT_FILE_DIR = Paths.get(FONT_FILE_DIR_PATH);
    private static final File FONT_FILE;

    static {
        try {
            Files.createDirectories(FONT_FILE_DIR);
            ClassPathResource resource = new ClassPathResource("fonts/MiSans-Medium.ttf");
            try (InputStream inputStream = resource.getInputStream()) {
                FONT_FILE = FONT_FILE_DIR.resolve("MiSans-Medium_font.ttf").toFile();
                Files.copy(inputStream, FONT_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize font file", e);
        }
    }

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
            // 加载原始图片
            BufferedImage originImage = ImageIO.read(new File(inputPath));
            // 获取图片的宽高
            int h = originImage.getHeight();
            int w = originImage.getWidth();

            // 创建水印图像
            BufferedImage textPic =
                    makeTextPicture(w, h, text, fontSize, WATERMARK_ANGLE, new Color(169, 169, 169, 51));

            // 将水印添加到原图
            BufferedImage watermarkedImage = addWatermarkToImage(originImage, textPic);

            // 保存带水印的图片到指定路径
            ImageIO.write(watermarkedImage, "PNG", new File(outputPath));
            return true;
        } catch (Exception e) {
            log.error("Fail to add watermark to pic! error :{}", ExceptionUtil.stacktraceToString(e));
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
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputPath)) {

            // 启动视频抓取器
            grabber.start();

            // 获取视频的基本信息
            int width = grabber.getImageWidth();
            int height = grabber.getImageHeight();
            double frameRate = grabber.getFrameRate();
            int audioChannels = grabber.getAudioChannels();
            int audioCodec = grabber.getAudioCodec();
            int sampleRate = grabber.getSampleRate();

            // 创建水印图片
            BufferedImage textPic = makeTextPicture(width, height, text, fontSize, WATERMARK_ANGLE, new Color(169, 169, 169, 51));

            // 创建视频录制器，注意这里需要在 grabber.start() 后获取宽度和高度
            try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, width, height)) {
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
                // 文件格式
                recorder.setFormat("mp4");
                // 帧率
                recorder.setFrameRate(frameRate);
                // 视频码率
                recorder.setVideoBitrate(VIDEO_BIT_RATE);
                // 音频通道数
                recorder.setAudioChannels(audioChannels);
                // 音频编码
                recorder.setAudioCodec(audioCodec);
                // 音频采样率
                recorder.setSampleRate(sampleRate);
                recorder.start();

                // 用于转换 Java 图像和 JavaCV Frame 之间的转换器
                Java2DFrameConverter java2DFrameConverter = new Java2DFrameConverter();
                Frame frame;

                // 处理每一帧并添加水印
                while ((frame = grabber.grabFrame()) != null) {
                    if (frame.image != null) {
                        BufferedImage frameImage = java2DFrameConverter.convert(frame);
                        // 添加水印到帧
                        addWatermarkToFrame(frameImage, textPic);
                        // 录制带水印的帧
                        recorder.record(java2DFrameConverter.convert(frameImage));
                    } else if (frame.samples != null) {
                        // 录制音频帧
                        recorder.record(frame);
                    }
                }

                return true;
            } catch (Exception e) {
                log.error("Failed to add watermark to video, error: {}", ExceptionUtil.stacktraceToString(e));
            }

        } catch (Exception e) {
            log.error("Error occurred while processing the video, error:{}", ExceptionUtil.stacktraceToString(e));
        }

        return false;
    }

    private static BufferedImage makeTextPicture(int w, int h, String text, int fontSize, int angle, Color color)
            throws FontFormatException, IOException {
        // 创建一个透明的图像作为水印
        BufferedImage textPic = new BufferedImage(4 * w, 4 * h, BufferedImage.TYPE_INT_ARGB);

        // 获取 Graphics2D 对象，用于绘制图形
        Graphics2D g = textPic.createGraphics();

        // 使用提供的字体文件创建字体对象，并设置字体大小
        Font font = Font.createFont(Font.TRUETYPE_FONT, FONT_FILE).deriveFont((float) fontSize);

        // 设置字体和绘制颜色
        g.setFont(font);
        g.setColor(color);

        // 启用抗锯齿效果，改善文本绘制质量
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 设置旋转角度，旋转中心为图像中心
        g.rotate(Math.toRadians(angle), textPic.getWidth() / 2, textPic.getHeight() / 2);

        // 在图像上重复绘制文本，形成网格状的水印效果
        for (int x = TEXT_DRAW_OFFSET; x < textPic.getWidth() - TEXT_DRAW_OFFSET; x += TEXT_X_REPEAT_FACTOR * fontSize * text.length()) {
            for (int y = TEXT_DRAW_OFFSET; y < textPic.getHeight() - TEXT_DRAW_OFFSET; y += TEXT_Y_REPEAT_FACTOR * fontSize) {
                // 在 (x, y) 位置绘制文本
                g.drawString(text, x, y);
            }
        }

        // 绘制完成后，释放 Graphics2D 对象的资源
        g.dispose();

        // 裁剪图像，将其缩小至原始的宽高（w 和 h），只保留中心区域
        return textPic.getSubimage(w, h, w, h);
    }

    private static BufferedImage addWatermarkToImage(BufferedImage originImage, BufferedImage textPic) {
        // 创建 Graphics2D 对象，用于在原图上绘制水印
        Graphics2D g = originImage.createGraphics();
        // 将水印图像绘制到原图的指定位置，这里是 (0, 0) 位置
        g.drawImage(textPic, 0, 0, null);
        // 绘制完成后，释放 Graphics2D 对象的资源
        g.dispose();
        // 返回添加了水印的原图
        return originImage;
    }

    private static void addWatermarkToFrame(BufferedImage frame, BufferedImage textPic) {
        // 获取 Graphics2D 对象，用于在视频帧上绘制水印
        Graphics2D g = frame.createGraphics();
        // 在视频帧的指定位置绘制水印图像，这里是将水印绘制在 (0, 0) 位置
        g.drawImage(textPic, 0, 0, null);
        // 绘制完成后，释放 Graphics2D 对象的资源
        g.dispose();
    }

    public static void main(String[] args) {
        String input_pic = "/Users/maxinhang/Code/material-tools-java/src/main/resources/input/input_image.jpg";
        String output_pic = "/Users/maxinhang/Code/material-tools-java/src/main/resources/output/output_image.jpg";
        addWatermarkToPic(input_pic, output_pic, "Watermark", 36);

        String input_video = "/Users/maxinhang/Code/material-tools-java/src/main/resources/input/input_video.mp4";
        String output_video = "/Users/maxinhang/Code/material-tools-java/src/main/resources/output/output_video.mp4";
        addWatermarkToVideo(input_video, output_video, "Watermark", 36);
    }
}
