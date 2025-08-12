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
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import javax.swing.SwingUtilities;

public class FFmpegQuality {
	
	private final static int THREADS = 16;
	private final static ExecutorService pool = Executors.newFixedThreadPool(THREADS);
	private final static ExecutorService iopool = Executors.newCachedThreadPool();

	private static String ffmpegdir = FFmpegProperties.ffmpegDir();
	private static File root = FFmpegProperties.workingDir();
	
	private static File logDir = new File(root, "logs");
	private static Vector<String> resultList;
	private static Properties encodeSpeeds;
	
	private static final boolean filterlog = false;
	
	private static EncodeStatusFrame frame;
	
	public static void main(String[] args) throws IOException, InvocationTargetException, InterruptedException {

		encodeSpeeds = FFmpegProperties.encodeSpeeds();
		if(!logDir.exists()) logDir.mkdirs();
		
		System.out.println("Quality task using " + THREADS + " threads...");
		long start = System.currentTimeMillis();
		SwingUtilities.invokeAndWait(() -> {
			frame = new EncodeStatusFrame();
			frame.setVisible(true);
		});
		
		List<QualityTask> taskList = FFmpegProperties.getQualityTasks("24.mp4");
		resultList = new Vector<String>(taskList.size());
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
		
		File resultFile = new File(root, "results_" + new SimpleDateFormat("yyyy-MM-dd-kk-mm-ss-SSSS").format(new Date()) + ".txt");
		Files.write(resultFile.toPath(), resultList.stream().sorted().toList(), StandardOpenOption.CREATE);
		System.out.println("Result file saved : " + resultFile.getAbsolutePath());
	}

	public static void launch(QualityTask t) {
		List<String> cmd = new LinkedList<String>();
		cmd.addAll(List.of(new File(ffmpegdir, "ffmpeg").getAbsolutePath(), 
				"-hide_banner",
				"-i", t.distorted(),
				"-i", t.reference(),
				"-filter_complex",
				filterlog 
						? "\"[0:v][1:v]ssim=stats_file=" + t.name() + "_ssim_log.txt;[0:v][1:v]psnr;[0:v][1:v]libvmaf=log_fmt=xml:log_path=" + t.name() + "_vmaf_log.xml\""
						: "\"[0:v][1:v]ssim;[0:v][1:v]psnr;[0:v][1:v]libvmaf\"",
				"-f", "null", "-"));

		ProcessBuilder pb = new ProcessBuilder(cmd);
		File logFile = new File(logDir, t.name() + "_log.txt");
		try {
			if(logFile.exists()) logFile.delete();
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
		
		EncodeStatus stat = frame.addTable(new File(t.distorted()));
		
		//pb.redirectError(logFile);
		//pb.redirectOutput(logFile);
		
		try {
			Process p = pb.start();
			
			AtomicReference<String> psnr = new AtomicReference<String>();
			AtomicReference<String> vmaf = new AtomicReference<String>();
			AtomicReference<String> ssim = new AtomicReference<String>();
			
			Future<?> f1 = iopool.submit(() -> {
				try(BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
					String line;
					while((line = br.readLine()) != null) {
						pw.println(line);
						//System.out.println(line);
						
						Matcher matcher = FFmpegProperties.statPattern().matcher(line);
						if (matcher.find()) {
							SwingUtilities.invokeLater(() -> {
								stat.set(matcher.group(1), matcher.group(2), matcher.group(4), matcher.group(3));
								frame.updated(stat);
							});
						}


						if(line.startsWith("[Parsed_ssim")) {
							ssim.set(line.replaceFirst("^\\[.*?\\]\\s*", ""));
						} else if(line.startsWith("[Parsed_libvmaf")) {
							vmaf.set(line.replaceFirst("^\\[.*?\\]\\s*", ""));
						} else if(line.startsWith("[Parsed_psnr")) {
							psnr.set(line.replaceFirst("^\\[.*?\\]\\s*", ""));
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
						//System.err.println(line);
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
			
			String bit = bitrate(t.distorted());
			String distortedName = new File(t.distorted()).getName();
			System.out.println(List.of("\n==========" + distortedName + "==========",
							"Name : " + t.name,
							"Size : " + String.format("%.2f", new File(t.distorted()).length() / (1024.0 * 1024)) + " MB",
							"Speed : " + encodeSpeeds.getOrDefault(distortedName, "N/A") + "x", 
							"bitrate : " + bit + " kb/s", vmaf.get(), psnr.get(), ssim.get(),
							"==========" + distortedName + "==========\n")
					.stream().collect(Collectors.joining("\n")));
			
			String resultStr = List.of(t.name(), String.format("%.2f", new File(t.distorted()).length() / (1024.0 * 1024)),  encodeSpeeds.getOrDefault(distortedName, "N/A").toString(),
					bit, vmaf.get().substring(12), psnr.get().substring(5), ssim.get().substring(5))
					.stream().collect(Collectors.joining("\t"));
			resultList.add(resultStr);
		} catch (InterruptedException | IOException e) {
			System.err.println(t.distorted() + " failed!");
			e.printStackTrace();
		}
	}
	public static String bitrate(String file) {
		return bitrate(null, file);
	}
	
	// https://superuser.com/questions/1106343/determine-video-bitrate-using-ffmpeg
	public static String bitrate(File dir, String file) {
		File f= new File(ffmpegdir, "ffprobe.exe");
		if(!f.exists()) return "File Not Exist";
		
		ProcessBuilder pb = new ProcessBuilder(
				List.of(f.getAbsolutePath(), "-v", "quiet", "-select_streams", "v:0",
						"-show_entries", "format=bit_rate", "-of", "default=noprint_wrappers=1:nokey=1", file));
		pb.directory(dir);
		try {
			Process p = pb.start();
			Scanner sc = new Scanner(p.getInputStream());
			String ret = String.valueOf(sc.nextLong() / 1024);
			sc.close();
			return ret;
		} catch (IOException e) {
			e.printStackTrace();
			return "N/A";
		}
	}

	public static record QualityTask(String name, String reference, String distorted) {
		public QualityTask(String reference, String distorted) {
			this(new File(distorted).getName(), reference, distorted);
		}
	}
}


