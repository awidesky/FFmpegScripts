package io.github.awidesky.myUtils.ffmpeg;

import java.io.File;

public class EncodeStatus {
    private File file;
    private String frame;
    private String fps;
    private String time;
    private String speed;
    private String status;

    public EncodeStatus(File fileName, String frame, String fps, String time, String speed, String status) {
        this.file = fileName;
        this.frame = frame;
        this.fps = fps;
        this.time = time;
        this.speed = speed;
        this.status = status;
    }

    // Getters
    public File getFile() { return file; }
    public String getFrame() { return frame; }
    public String getFps() { return fps; }
    public String getTime() { return time; }
    public String getSpeed() { return speed; }
    public String getStatus() { return status; }

    public void set(String frame, String fps, String time, String speed) {
        this.frame = frame;
        this.fps = fps;
        this.time = time;
        this.speed = speed;
    }
    public void setStatus(String status) {
    	this.status = status;
    }
}
