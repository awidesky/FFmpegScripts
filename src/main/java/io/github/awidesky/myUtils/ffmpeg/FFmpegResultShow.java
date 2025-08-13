/*
 * Copyright (c) 2025 Eugene Hong
 *
 * This software is distributed under license. Use of this software
 * implies agreement with all terms and conditions of the accompanying
 * software license.
 * Please refer to LICENSE
 * */

package io.github.awidesky.myUtils.ffmpeg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Scanner;
import java.util.stream.Collectors;

public class FFmpegResultShow {

	private static final int showLines = 5;
	public static void main(String[] args) {
		Scanner sc = new Scanner(System.in);
		while(sc.hasNext()) {
			//System.out.println("results.txt file >");
			System.out.flush();
			String line = sc.nextLine();
			try {
				List<TestResult> list = Files.lines(Paths.get(line)).map(TestResult::parse)
						//.filter(t -> t.name().startsWith("libx264"))
						.toList();
				
				System.out.println("VMAF top :");
				System.out.println(topN(list, Comparator.comparing(TestResult::vmaf_d).thenComparing(TestResult::ssim).thenComparing(TestResult::psnr)
						.thenComparing(Comparator.comparing(TestResult::size_d).reversed()).thenComparing(TestResult::speed_d)));
				System.out.println();
				
				System.out.println("Size top :");
				System.out.println(topN(list, Comparator.comparing(TestResult::size_d).reversed().thenComparing(TestResult::vmaf_d).thenComparing(TestResult::ssim)
						.thenComparing(TestResult::psnr).thenComparing(TestResult::speed_d)));
				System.out.println();
				
				System.out.println("Speed top :");
				System.out.println(topN(list, Comparator.comparing(TestResult::speed_d).thenComparing(TestResult::vmaf_d).thenComparing(TestResult::ssim)
						.thenComparing(TestResult::psnr).thenComparing(Comparator.comparing(TestResult::size_d).reversed())));
				System.out.println();
				
				System.out.println("psnr top :");
				System.out.println(topN(list, Comparator.comparing(TestResult::psnr).thenComparing(TestResult::vmaf_d).thenComparing(TestResult::ssim)
						.thenComparing(Comparator.comparing(TestResult::size_d).reversed()).thenComparing(TestResult::speed_d)));
				System.out.println();
				
				System.out.println("ssim top :");
				System.out.println(topN(list, Comparator.comparing(TestResult::vmaf_d).thenComparing(TestResult::ssim).thenComparing(TestResult::psnr)
						.thenComparing(Comparator.comparing(TestResult::size_d).reversed()).thenComparing(TestResult::speed_d)));
				System.out.println();
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		sc.close();
	}

	public static String topN(List<TestResult> list, Comparator<TestResult> comparator) {
	    PriorityQueue<TestResult> heap = new PriorityQueue<>(showLines, comparator);

	    for (TestResult item : list) {
	        if (heap.size() < showLines) {
	            heap.offer(item);
	        } else if (comparator.compare(item, heap.peek()) > 0) {
	            heap.poll();
	            heap.offer(item);
	        }
	    }

	    List<TestResult> result = new ArrayList<>(heap);
	    result.sort(comparator.reversed());
	    return result.stream().map(TestResult::toString).collect(Collectors.joining("\n"));
	}
}
