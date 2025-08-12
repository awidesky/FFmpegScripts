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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.github.awidesky.myUtils.ffmpeg.FFmpegEncode.EncodeTask;
import io.github.awidesky.myUtils.ffmpeg.FFmpegQuality.QualityTask;

public class FFmpegProperties {
	private static final Path propertyFile = Paths.get("ffmpeg.properties");
	private static final Path encodeJobFile = Paths.get("ffmpegEncodeJobs.txt");
	private static final Path qualityJobFile = Paths.get("ffmpegQualityJobs.txt");
	
	private static final Map<String, String> properties = new HashMap<>();
	
	static {		
		if(!Files.exists(propertyFile)) {
			try {
				Files.createFile(propertyFile);
				Files.write(propertyFile, List.of("ffmpegdir=.", "workingdir=."), StandardOpenOption.CREATE);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if(!Files.exists(encodeJobFile)) {
			try {
				Files.createFile(encodeJobFile);
				Files.write(encodeJobFile, List.of("# ffmpeg encode jobs", "# \"?input?\" replaces to input file path in code"), StandardOpenOption.CREATE);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if(!Files.exists(qualityJobFile)) {
			try {
				Files.createFile(qualityJobFile);
				Files.write(qualityJobFile, List.of("# ffmpeg quality check jobs", "# \"reference_video\" \"distorted_video\""), StandardOpenOption.CREATE);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		try (BufferedReader br = Files.newBufferedReader(propertyFile)) {
			br.lines().forEach(s -> properties.put(s.substring(0, s.indexOf("=")), s.substring(s.indexOf("=") + 1, s.length())));
			properties.entrySet().forEach(e -> System.out.println(e.getKey() + " : " + e.getValue()));
			System.out.println();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static String ffmpegDir() {
		return properties.getOrDefault("ffmpegdir", ".");
	}

	public static File workingDir() {
		return new File(properties.getOrDefault("workingdir", "."));
	}
	
	
	private static final Pattern TOKEN_PATTERN = Pattern.compile("\"([^\"]*)\"|(\\S+)");
	public static List<EncodeTask> getEncodeTasks(String input) {
		boolean ignore = false;
		List<EncodeTask> result = new LinkedList<>();

		try (BufferedReader br = Files.newBufferedReader(encodeJobFile)) {
			String line;
			while ((line = br.readLine()) != null) {
				if(line.startsWith("###")) ignore = !ignore;
				if(ignore) continue;
				if(line.startsWith("#") || line.isBlank()) continue;
				
				List<String> tokens = new LinkedList<>();
				Matcher matcher = TOKEN_PATTERN.matcher(line);
				while (matcher.find()) {
					String token = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
					if("?input?".equals(token) && input != null) token = input;
					tokens.add(token);
				}

				result.add(new EncodeTask(tokens.toArray(String[]::new)));
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return result;
	}
	
	public static List<QualityTask> getQualityTasks() {
		boolean ignore = false;
		List<QualityTask> result = new LinkedList<>();
		
		try (BufferedReader br = Files.newBufferedReader(encodeJobFile)) {
			String line;
			while ((line = br.readLine()) != null) {
				if(line.startsWith("###")) ignore = !ignore;
				if(ignore) continue;
				if(line.startsWith("#") || line.isBlank()) continue;
				
				List<String> tokens = new LinkedList<>();
				Matcher matcher = TOKEN_PATTERN.matcher(line);
				while (matcher.find()) {
					String token = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
					tokens.add(token);
				}
				
				if (tokens.size() == 2) {
					result.add(new QualityTask(tokens.get(0), tokens.get(1)));
				} else if (tokens.size() == 3) {
					result.add(new QualityTask(tokens.get(0), tokens.get(1), tokens.get(3)));
				} else {
                    System.err.println("Invalid line (not 2 or 3 tokens): " + line);
                    System.err.println(tokens.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", ")));
                }
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return result;
	}
}
