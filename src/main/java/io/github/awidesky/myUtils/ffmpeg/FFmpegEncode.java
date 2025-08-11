/*
 * Copyright (c) 2025 Eugene Hong
 *
 * This software is distributed under license. Use of this software
 * implies agreement with all terms and conditions of the accompanying
 * software license.
 * Please refer to LICENSE
 * */

package io.github.awidesky.myUtils.ffmpeg;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.SwingUtilities;

public class FFmpegEncode {
	
	private final static int THREADS = 8; //Runtime.getRuntime().availableProcessors();
	private final static ExecutorService pool = Executors.newFixedThreadPool(THREADS);
	private final static ExecutorService iopool = Executors.newCachedThreadPool();

	private static String ffmpegdir = ""; // TODO : preference
	private static File root = new File("");
	private static File dest = new File(root, "nvidia");
	
	private static File logDir = new File(root, "logs");
	
	private static EncodeStatusFrame frame;
	
	public static void main(String[] args) throws InvocationTargetException, InterruptedException {

		if(!logDir.exists()) logDir.mkdirs();
		if(!dest.exists()) dest.mkdirs();
		System.out.println("Using " + THREADS + " threads...");
		long start = System.currentTimeMillis();
		
		SwingUtilities.invokeAndWait(() -> {
			frame = new EncodeStatusFrame();
			frame.setVisible(true);
		});
		
		String input = new File(root, "14.mp4").getAbsolutePath();
		
		List.of(
				new EncodeTask("-i", input, "-c:v", "h264_nvenc", "h264_nvenc_default.mp4"),
				new EncodeTask("-i", input, "-c:v", "h264_nvenc", "-preset", "p6", "h264_nvenc_p6.mp4"),
				new EncodeTask("-i", input, "-c:v", "h264_nvenc", "-preset", "p7", "h264_nvenc_p7.mp4"),
				
				new EncodeTask("-i", input, "-c:v", "h264_nvenc", "-cq", "18", "h264_nvenc_cq18.mp4"),
				new EncodeTask("-i", input, "-c:v", "h264_nvenc", "-cq", "23", "h264_nvenc_cq23.mp4"),
				
				new EncodeTask("-i", input, "-c:v", "h264_nvenc", "-preset", "p6", "-cq", "18", "h264_nvenc_p6_cq18.mp4"),
				new EncodeTask("-i", input, "-c:v", "h264_nvenc", "-preset", "p6", "-cq", "23", "h264_nvenc_p6_cq23.mp4"),
				
				new EncodeTask("-i", input, "-c:v", "h264_nvenc", "-preset", "p7", "-cq", "18", "h264_nvenc_p7_cq18.mp4"),
				new EncodeTask("-i", input, "-c:v", "h264_nvenc", "-preset", "p7", "-cq", "23", "h264_nvenc_p7_cq23.mp4"),

				new EncodeTask("-i", input, "-c:v", "h264_nvenc", "-preset", "p2", "-cq", "18", "h264_nvenc_p2_cq18.mp4"),
				new EncodeTask("-i", input, "-c:v", "h264_nvenc", "-preset", "p2", "-cq", "23", "h264_nvenc_p2_cq23.mp4")
				)
		.stream()
		.map(t -> pool.submit(() -> launch(t)))
		.toList().stream()
		.forEach(f -> {
			try {
				f.get();
			} catch (InterruptedException | ExecutionException e) {
				System.err.println("Failed to wait for future!");
				e.printStackTrace();
			}
		});
		
		pool.shutdown();
		iopool.shutdown();
		
		Duration d = Duration.ofMillis(System.currentTimeMillis() - start);
		System.out.println("Done! Time : " + String.format("%02d:%02d:%02d.%03d", d.toHours(), d.toMinutesPart(),
				d.toSecondsPart(), d.toMillisPart()) + "ms");
	}

	public static void launch(EncodeTask t) {
		List<String> cmd = new LinkedList<String>();
		cmd.addAll(List.of(ffmpegdir + "ffmpeg.exe", 
				"-hide_banner" //, "-nostats"
				));
		cmd.addAll(t.options());
		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.directory(dest);
		File logFile = new File(logDir, t.output() + "_log.txt");
		if(logFile.exists()) logFile.delete();
		try {
			if(!logFile.exists()) logFile.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
		PrintWriter pw;
		try {
			pw = new PrintWriter(logFile);
		} catch (FileNotFoundException e) {
			System.out.println("logFile not exist! : " + logFile.getAbsolutePath());
			e.printStackTrace();
			return;
		}
		EncodeStatus stat = frame.addTable(new File(dest, t.output()));
		
		//pb.redirectError(logFile);
		//pb.redirectOutput(logFile);
		
		try {
			Process p = pb.start();
			Future<?> f1 = iopool.submit(() -> {
				Pattern statPattern = Pattern.compile("frame=\\s*(\\d+).*?fps=\\s*(\\d+).*?time=([\\d:.]+).*?speed=([\\d.]+)x");
				try(BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
					String line;
					while((line = br.readLine()) != null) {
						pw.println(line);
						
						Matcher matcher = statPattern.matcher(line);
						if (matcher.find()) {
							SwingUtilities.invokeLater(() -> {
								stat.set(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4));
								frame.updated(stat);
							});
						}
					}
				} catch (IOException e) {
					e.printStackTrace(pw);
					e.printStackTrace();
				}
			});
			Future<?> f2 = iopool.submit(() -> {
				try(BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
					String line;
					while((line = br.readLine()) != null) {
						pw.println(line);
					}
				} catch (IOException e) {
					e.printStackTrace(pw);
					e.printStackTrace();
				}
			});
			
			p.waitFor();
			iopool.execute(() -> {
				try {
					f1.get(5, TimeUnit.SECONDS);
					f2.get(5, TimeUnit.SECONDS);
				} catch (InterruptedException | ExecutionException | TimeoutException e) {
					e.printStackTrace();
				}
				pw.flush();
				pw.close();
			});
			SwingUtilities.invokeLater(() -> {
				stat.setStatus("Finished with " + p.exitValue());
				frame.updated(stat);
			});
			
			ProcessHandle.Info info = p.info();
			System.out.printf("[%s] started : %s, totalTime: %dms, exit code : %d\n",
					pb.command().stream().collect(Collectors.joining(" ")), info.startInstant().get().toString(), info.totalCpuDuration().get().toMillis(), p.exitValue());
			
			String bit = FFmpegQuality.bitrate(dest, t.output());
			System.out.println(List.of("\n==========" + t.output() + "==========",
							"Size : " + String.format("%.2f", new File(dest, t.output()).length() / (1024.0 * 1024)) + " MB",
							"Speed : " + stat.getSpeed() + "x", 
							"bitrate : " + bit + " kb/s",
							"==========" + t.output() + "==========\n")
					.stream().collect(Collectors.joining("\n")));
			
		} catch (InterruptedException | IOException e) {
			System.err.println(t.output() + " failed!");
			e.printStackTrace();
		}
	}


}

record EncodeTask(String output, List<String> options) { 
	public EncodeTask(String... options) {
		this(options[options.length - 1], Arrays.asList(options));
	}
}