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
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.SwingUtilities;

public class FFmpegQuality {
	
	private final static int THREADS = Runtime.getRuntime().availableProcessors();
	private final static ExecutorService pool = Executors.newFixedThreadPool(THREADS);
	private final static ExecutorService iopool = Executors.newCachedThreadPool();

	private static String ffmpegdir = ".\\";
	private static File root = new File("");
	
	private static File logDir = new File(root, "logs");
	private static PrintWriter result;
	
	private static EncodeStatusFrame frame;
	
	public static void main(String[] args) throws IOException {

		if(!logDir.exists()) logDir.mkdirs();
		File resultFile = new File(root, "results.txt");
		resultFile.createNewFile();
		result = new PrintWriter(resultFile);
		
		System.out.println("Using " + THREADS + " threads...");
		SwingUtilities.invokeLater(() -> {
			frame = new EncodeStatusFrame();
			frame.setVisible(true);
		});
		
//		Arrays.stream(root.listFiles())
//		.filter(File::isFile).filter(f -> f.getName().endsWith(".mp4"))
//		.map(f -> pool.submit(() -> launch(f)))
//		.forEach(f -> {
//			try {
//				f.get();
//			} catch (InterruptedException | ExecutionException e) {
//				System.err.println("Failed to wait for future!");
//				e.printStackTrace();
//			}
//		});
		pool.shutdown();
		iopool.shutdown();
		System.out.println("done!");

	}

	public static void launch(QualityTask t) throws FileNotFoundException {
		List<String> cmd = new LinkedList<String>();
		cmd.addAll(List.of(ffmpegdir + "ffmpeg.exe", 
				"-hide_banner", // -i 2.lib.mp4 -i 2.mp4 -filter_complex "[0:v][1:v]ssim=stats_file=ssim_log.txt;[0:v][1:v]psnr;[0:v][1:v]libvmaf" -f null -
				"-i", t.distorted(), "-i", t.reference(), "-filter_complex", "\"[0:v][1:v]ssim=stats_file=ssim_log.txt;[0:v][1:v]psnr;[0:v][1:v]libvmaf\"", "-f", "null", "-"));

		ProcessBuilder pb = new ProcessBuilder(cmd);
		File logFile = new File(logDir, t.distorted() + "_log.txt");
		if(logFile.exists()) logFile.delete();
		try {
			if(!logFile.exists()) logFile.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
		PrintWriter pw = new PrintWriter(logFile);
		
		EncodeStatus stat = frame.addTable(new File(t.distorted()));
		
		//pb.redirectError(logFile);
		//pb.redirectOutput(logFile);
		
		try {
			Process p = pb.start();
			
			AtomicReference<String> psnr = new AtomicReference<String>();
			AtomicReference<String> vmaf = new AtomicReference<String>();
			AtomicReference<String> ssim = new AtomicReference<String>();
			
			Future<?> f1 = iopool.submit(() -> {
				Pattern statPattern = Pattern.compile("frame=\\s*(\\d+).*?fps=\\s*(\\d+).*?time=([\\d:.]+).*?speed=([\\d.]+)x");
				try(BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
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


						if(line.startsWith("[Parsed_ssim")) {
							System.out.println(line);
							ssim.set(line.replaceFirst("^\\[.*?\\]\\s*", ""));
						} else if(line.startsWith("[Parsed_libvmaf")) {
							System.out.println(line);
							vmaf.set(line.replaceFirst("^\\[.*?\\]\\s*", ""));
						} else if(line.startsWith("[Parsed_psnr")) {
							System.out.println(line);
							psnr.set(line.replaceFirst("^\\[.*?\\]\\s*", ""));
						}
					}
				} catch (IOException e) {
					e.printStackTrace(pw);
					e.printStackTrace();
				}
			});
			Future<?> f2 = iopool.submit(() -> {
				try(BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
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
			
			ProcessHandle.Info info = p.info();
			System.out.printf("[%s] started : %s, totalTime: %dms, exit code : %d\n",
					pb.command().stream().collect(Collectors.joining(" ")), info.startInstant().get().toString(), info.totalCpuDuration().get().toMillis(), p.exitValue());
			
			result.println(List.of(t.name(), String.format("%.2f", new File(t.distorted()).length() / (1024.0 * 1024)) + " MB", stat.getSpeed(), bitrate(t.distorted()), vmaf.get(), psnr.get(), ssim.get())
					.stream().collect(Collectors.joining("\t")));
		} catch (InterruptedException | IOException e) {
			System.err.println(t.distorted() + " failed!");
			e.printStackTrace();
		}
	}
	
	// https://superuser.com/questions/1106343/determine-video-bitrate-using-ffmpeg
	public static String bitrate(String file) {
		ProcessBuilder pb = new ProcessBuilder(
				List.of(ffmpegdir + "ffprobe.exe", "-v", "quiet", "-select_streams", "v:0", "-show_entries", "format=bit_rate", "-of", "default=noprint_wrappers=1:nokey=1", file));
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

}

record QualityTask(String name, String reference, String distorted) {
}
