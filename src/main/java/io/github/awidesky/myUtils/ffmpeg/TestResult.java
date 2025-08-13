/*
 * Copyright (c) 2025 Eugene Hong
 *
 * This software is distributed under license. Use of this software
 * implies agreement with all terms and conditions of the accompanying
 * software license.
 * Please refer to LICENSE
 * */

package io.github.awidesky.myUtils.ffmpeg;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record TestResult(String name, String size, String speed, String bitrate, String vmaf, String psnr, String ssim) {
	private static final Pattern ssimPattern = Pattern.compile("All:[^A-Z]+");
	private static final Pattern psnrPattern = Pattern.compile("average:.*");
	
	public static TestResult parse(String line) {
		String[] strs = line.split("\t");
		if(strs.length != 7) {
			System.err.println("Invalid strings : " + Arrays.stream(strs).collect(Collectors.joining(" ")));
			return null;
		}
		
        Matcher matcher = ssimPattern.matcher(strs[6]);
        if (matcher.find()) {
        	String found = matcher.group();
            strs[6] = found + " " + strs[6].replace(found, "").trim();
        }
        matcher = psnrPattern.matcher(strs[5]);
        if (matcher.find()) {
        	String found = matcher.group();
        	strs[5] = found + " " + strs[5].replace(found, "").trim();
        }
		return new TestResult(strs[0], strs[1], strs[2], strs[3], strs[4], strs[5], strs[6]);
	}

	@Override
	public String toString() {
		return String.join("\t", name, size, speed, bitrate, vmaf, psnr, ssim);
	}
	
    public double size_d() {
        return parseDouble(size);
    }

    public double speed_d() {
        return parseDouble(speed);
    }

    public double vmaf_d() {
        return parseDouble(vmaf);
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }
	
}
