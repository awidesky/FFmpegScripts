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
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import javax.swing.SwingUtilities;

public class FFmpegEncode {
	
	private final static int THREADS = 1; //Runtime.getRuntime().availableProcessors();
	private final static ExecutorService pool = Executors.newFixedThreadPool(THREADS);
	private final static ExecutorService iopool = Executors.newCachedThreadPool();

	private static String ffmpegdir = FFmpegProperties.ffmpegDir();
	private static File root = FFmpegProperties.workingDir();
	private static File dest = FFmpegProperties.destDir();
	
	private static File logDir = new File(root, "logs");
	private static Properties encodeSpeeds = new Properties();
	
	private static EncodeStatusFrame frame;
	
	public static void main(String[] args) throws InvocationTargetException, InterruptedException, FileNotFoundException, IOException {
		File input = new File(root, "14.mp4");
		
		if(!logDir.exists()) logDir.mkdirs();
		if(!dest.exists()) dest.mkdirs();
		
		System.out.println("Destination : " + dest.getAbsolutePath());
		System.out.println("Encode task using " + THREADS + " threads...");
		long start = System.currentTimeMillis();
		
		SwingUtilities.invokeAndWait(() -> {
			frame = new EncodeStatusFrame();
			frame.setVisible(true);
		});
		
		List<EncodeTask> taskList = FFmpegProperties.getEncodeTasks(input.getAbsolutePath());
		taskList.stream()
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
		
		SwingUtilities.invokeLater(() -> frame.setTitle("ffmpeg process Finished!"));
		
		File speedData = new File(".", //TODO : access denied, use app folder? + write frequently
				"EncodeSpeeds_" + input.getName() + "_" + THREADS
				+ new SimpleDateFormat("_yyyy-MM-dd-kk-mm-ss-SSSS").format(new Date()) + ".txt");
		encodeSpeeds.store(new FileWriter(speedData, StandardCharsets.UTF_8),
				"Input : " + input.getAbsolutePath() + ", threads : " + THREADS);
		System.out.println("Encode speed data saved : " + speedData.getAbsolutePath());
		
		File qualityTest = new File(logDir, "qualityTaskSuite.txt");
		Files.write(qualityTest.toPath(), taskList.stream().map(t -> input.getName() + " " + t.output()).sorted().toList(), StandardOpenOption.CREATE);
		System.out.println("Quality test suite saved : " + qualityTest.getAbsolutePath());
	}

	public static void launch(EncodeTask t) {
		List<String> cmd = new LinkedList<String>();
		cmd.addAll(List.of(new File(ffmpegdir, "ffmpeg").getAbsolutePath(),
				"-hide_banner", "-y" //, "-nostats"
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
				try(BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
					String line;
					while((line = br.readLine()) != null) {
						pw.println("[stderr] " + line);
						
						Matcher matcher = FFmpegProperties.statPattern().matcher(line);
						if (matcher.find()) {
							SwingUtilities.invokeLater(() -> {
								stat.set(matcher.group(1), matcher.group(2), matcher.group(4), matcher.group(3));
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
						pw.println("[stdout] " + line);
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
				encodeSpeeds.put(t.output(), stat.getSpeed());
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

	public static record EncodeTask(String output, List<String> options) { 
		public EncodeTask(String... options) {
			this(options[options.length - 1], Arrays.asList(options));
		}
	}

}

