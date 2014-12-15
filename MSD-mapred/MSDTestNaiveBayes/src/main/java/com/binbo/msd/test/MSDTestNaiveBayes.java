package com.binbo.msd.test;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;


public class MSDTestNaiveBayes {
	
	public static class TestJob {
		
		class Count {
			int    year;
			double prob;
		}
		
		class Result {
			int year;
			int predicted;
			boolean correct;
		}				
		
		// All neccessary data used to predict the year
		private List<double[]> mRanges = new ArrayList<double[]>();
		private List<Count> mCounts = new ArrayList<Count>();
		private List<double[]> mModel = new ArrayList<double[]>();
		
		// The list where the results are put
		private List<Result> mResults = new ArrayList<Result>();
		
		// The count of commas before the first feature
		private final static int FEATURES_OFFSET = 9;
		
		public TestJob(String rangesPath, String countsPath, String modelPath) {
			// Load data from paths
			try {
				processData(rangesPath, new DataProcessListener() {
					@Override
					public void onDataProcess(String line) {
						// Parse each range	
						String[] splits = line.split("[,]");
						int index = Integer.parseInt(splits[0]);
						int steps = Integer.parseInt(splits[3]);
						double min = Double.parseDouble(splits[1]);
						double max = Double.parseDouble(splits[2]);
						double inc = (max - min) / steps;
						
						// Populate the range array for the current feature column
						mRanges.add(index, new double[steps + 1]);
						mRanges.get(index)[0] = min;						
						for (int i = 1; i < steps; ++i) {
							mRanges.get(index)[i] = mRanges.get(index)[i - 1] + inc;
						}	
						mRanges.get(index)[steps] = max;
					}
				});
				processData(countsPath, new DataProcessListener() {
					@Override
					public void onDataProcess(String line) {
						// Parse each count	
						String[] splits = line.split("[,]");
						Count count = new Count();
						count.year = Integer.parseInt(splits[0]);
						count.prob = Double.parseDouble(splits[2]);						
						// Populate the count array
						mCounts.add(count);	
					}					
				});
				processData(modelPath, new DataProcessListener() {
					@Override
					public void onDataProcess(String line) {
						// Parse each year model	
						String[] splits = line.substring(line.indexOf(',') + 1).split("[ ]");
						double[] probs = new double[splits.length];
						for (int i = 0; i < probs.length; ++i) {
							probs[i] = Double.parseDouble(splits[i]);
						}	
						mModel.add(probs);
					}
				});
			} catch (IOException e) {
				e.printStackTrace();
			}			
		}
				
		public void test(String inputPath) {
			try {
				// Pre-allocate
				final int[] indices = new int[mRanges.size()];
				final double[] probs = new double[mModel.size()];
				final double[] features = new double[mRanges.size()];								
				
				// Read each piece of data from test dataset, make the year prediction and save the result.
				processData(inputPath, new DataProcessListener() {
					@Override
					public void onDataProcess(String line) {
						// Extract all features from this line
						final String[] splits = line.split("[,]");
						for (int i = 0; i < features.length; ++i) {
							features[i] = Double.parseDouble(splits[i + FEATURES_OFFSET]);
						}
						
						// Get indices for each feature
						for (int i = 0; i < mRanges.size(); ++i) { // i is the index of the feature column
							double[] range = mRanges.get(i);
							double feature = Math.max(features[i], range[0]);
							if (feature >= range[range.length - 1]) feature = range[range.length - 2];
							for (int j = 0; j < range.length - 1; ++j) {
								if (range[j] <= feature && feature < range[j + 1]) {
									indices[i] = j;
								}
							}
						}
		
						// For each year's model, compute the sum of ln(prob)
						for (int i = 0; i < mModel.size(); ++i) {
							int offset = 0;
							double[] yearModel = mModel.get(i);
							for (int j = 0; j < indices.length; ++j) {
								probs[i] += yearModel[offset + indices[j]];
								offset += mRanges.get(j).length - 1;
							}
							probs[i] += mCounts.get(i).prob;
						}												
						
						// Get the majority vote to find the maximum probability and its year
						int index = 0;
						double max = probs[0];
						for (int i = 1; i < probs.length; ++i) {
							if (probs[i] > max) {
								max = probs[i];
								index = i;
							}
						}
						
						// Get the result
						Result result = new Result();
						result.year = Integer.parseInt(splits[0]);
						result.predicted = mCounts.get(index).year;
						result.correct = (result.year - 3 <= result.predicted) && (result.predicted <= result.year + 3);
						mResults.add(result);
						
						// Clear the probs list for next usage
						Arrays.fill(probs, 0d);
					}					
				});
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		private void write(String outputPath) {
			BufferedOutputStream output = null;
			try {
				output = new BufferedOutputStream(new FileOutputStream(outputPath));
				for (Result result : mResults) {
					String line = String.format("%d,%d,%b\n", result.year, result.predicted, result.correct);
					output.write(line.getBytes());
				}
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (output != null) {
					try {
						output.flush();
						output.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		
		private void writeSummary(String summaryPath) {			
			BufferedOutputStream output = null;
			try {
				output = new BufferedOutputStream(new FileOutputStream(summaryPath));
				int sumOfYears = 0, sumOfCorrect = 0;
				// The map from a year to the count of this year 
				HashMap<Integer, Integer> map1 = new HashMap<Integer, Integer>(mModel.size());
				// The map from a year to the count of the correct prediction of this year
				HashMap<Integer, Integer> map2 = new HashMap<Integer, Integer>(mModel.size());
				// The set used to save all occurance year without repeatation
				HashSet<Integer> set = new HashSet<Integer>();
				for (Result result : mResults) {
					++sumOfYears;
					map1.put(result.year, map1.get(result.year) == null ? 1 : map1.get(result.year) + 1);
					if (result.correct) {
						++sumOfCorrect;
						map2.put(result.year, map2.get(result.year) == null ? 1 : map2.get(result.year) + 1);
					} else {
						map2.put(result.year, map2.get(result.year) == null ? 0 : map2.get(result.year));
					}
					set.add(result.year);										
				}
				// Write the summary
				output.write(String.format("Year,Correct,Total,Ratio\n").getBytes());
				for (Integer year : set) {
					int total = map1.get(year), correct = map2.get(year);
					output.write(String.format("%d,%d,%d,%3f\n", year, correct, total, correct / (double) total).getBytes());
				}				
				output.write(String.format("Summary: %d, %d, %3f", 
						sumOfYears, sumOfCorrect, sumOfCorrect / (double) sumOfYears).getBytes());
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (output != null) {
					try {
						output.flush();
						output.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		
		private void processData(String dataPath, DataProcessListener listener) throws IOException {
			// List all data files
			File dir = new File(dataPath);
			File[] files = null;
			if (dir.isDirectory()) {
				files = dir.listFiles(new FilenameFilter() {
					@Override
					public boolean accept(File dir, String name) {
						if (name.contains(".DS_Store") || name.contains(".crc"))
							return false;
						return true;
					}						
				});					
			} else {
				files = new File[] { dir };
			}
			
			for (File file : files) {
				BufferedReader reader = null;
				try {
					reader = new BufferedReader(
							new InputStreamReader(new BufferedInputStream(new FileInputStream(file))));				
					String line;
					while ((line = reader.readLine()) != null) {
						listener.onDataProcess(line);
					}	
				} finally {
					if (reader != null) {
						reader.close();
					}
				}
			}
		}
		
		public interface DataProcessListener {
			void onDataProcess(final String line);
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 6) {
			System.err.println("Usage: test <ranges> <counts> <model> <in> <out> <summary>");
			System.exit(2);
		}
		
		TestJob job = new TestJob(args[0], args[1], args[2]);
		
		System.out.println("Start testing...");
		job.test(args[3]);
		job.write(args[4]);
		job.writeSummary(args[5]);
		System.out.println("Testing is done!");
		
		System.exit(0);
	}
}
