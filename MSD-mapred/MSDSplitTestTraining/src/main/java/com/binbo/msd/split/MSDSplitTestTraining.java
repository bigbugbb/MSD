package com.binbo.msd.split;

import java.io.IOException;
import java.util.Random;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.jobcontrol.JobControl;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.log4j.Logger;


public class MSDSplitTestTraining {

	// Logger
	private static Logger log = Logger.getLogger(MSDSplitTestTraining.class);
	
	private static final String MIN_YEAR = "MIN_YEAR";
	private static final String MAX_YEAR = "MAX_YEAR";
	
	/**
	 * Randomly add the flag at the starting position of each row,
	 * the flag indicates whether the piece of data should be considered as training or test data.
	 */
	public static class SplitDataMapper extends Mapper<Object, Text, NullWritable, Text> {
		
		private Random mRand = new Random();
		
		private Text mValue = new Text();
		
		private int mMinYear, mMaxYear;
		
		protected void setup(Context context) throws IOException, InterruptedException {
			mMinYear = context.getConfiguration().getInt(MIN_YEAR, 1922);
			mMaxYear = context.getConfiguration().getInt(MAX_YEAR, 2011);
		}

		public void map(Object key, Text value, Context context)
				throws IOException, InterruptedException {
			final String line = value.toString();
			int start = line.indexOf(',') + 1;
			int year  = Integer.parseInt(line.substring(0, start - 1));
			
			if (year < mMinYear || year > mMaxYear)
				return; // Filter this data out	

			// Test data starts with "1" while Training data starts with "0"
			mValue.set((mRand.nextInt(10) == 0 ? "1," : "0,") + value);
			context.write(NullWritable.get(), mValue);
		}

	}
	
	/**
	 * Extract all training data from the output of SplitDataMapper.
	 */
	public static class ExtractTrainingDataMapper extends Mapper<Object, Text, NullWritable, Text> {
		
		private Text mValue = new Text();
		
		public void map(Object key, Text value, Context context)
				throws IOException, InterruptedException {
			if (value.charAt(0) == '0') {  // '0' indicates training data
				mValue.set(value.toString().substring(2));  // Jump across the comma
				context.write(NullWritable.get(), mValue);
			}				
		}
	}
	
	/**
	 * Extract all test data from the output of SplitDataMapper.
	 */
	public static class ExtractTestDataMapper extends Mapper<Object, Text, NullWritable, Text> {
		
		private Text mValue = new Text();
		
		public void map(Object key, Text value, Context context)
				throws IOException, InterruptedException {
			if (value.charAt(0) == '1') {  // '1' indicates test data
				mValue.set(value.toString().substring(2));  // Jump across the comma
				context.write(NullWritable.get(), mValue);
			}				
		}
	}

	static class JobRunner implements Runnable {

		private JobControl mControl;

		public JobRunner(JobControl control) {
			mControl = control;
		}

		public void run() {
			mControl.run();
		}
	}

	public static final String[] JOBNAMES = { "SplitData Job", "ExtractTrainingData Job", "ExtractTestData Job" };

	public static Job getSplitDataJob(Configuration conf, String inputPath,
			String outputPath) throws IOException {
		Job job = new Job(conf, JOBNAMES[0]);
		job.setJarByClass(MSDSplitTestTraining.class);

		// Configure input source
		FileInputFormat.addInputPath(job, new Path(inputPath));

		// Configure mapper and reducer
		job.setMapperClass(SplitDataMapper.class);
		job.setReducerClass(Reducer.class);

		// Configure output
		FileOutputFormat.setOutputPath(job, new Path(outputPath));
		job.setOutputKeyClass(NullWritable.class);
		job.setOutputValueClass(Text.class);

		return job;
	}
	
	public static Job getExtractTrainingDataJob(Configuration conf, String inputPath,
			String outputPath) throws IOException {
		Job job = new Job(conf, JOBNAMES[1]);
		job.setJarByClass(MSDSplitTestTraining.class);

		// Configure input source
		FileInputFormat.addInputPath(job, new Path(inputPath));

		// Configure mapper and reducer
		job.setMapperClass(ExtractTrainingDataMapper.class);
		job.setReducerClass(Reducer.class);
		job.setNumReduceTasks(1);

		// Configure output
		FileOutputFormat.setOutputPath(job, new Path(outputPath));
		job.setOutputKeyClass(NullWritable.class);
		job.setOutputValueClass(Text.class);

		return job;
	}
	
	public static Job getExtractTestDataJob(Configuration conf, String inputPath,
			String outputPath) throws IOException {
		Job job = new Job(conf, JOBNAMES[2]);
		job.setJarByClass(MSDSplitTestTraining.class);

		// Configure input source
		FileInputFormat.addInputPath(job, new Path(inputPath));

		// Configure mapper and reducer
		job.setMapperClass(ExtractTestDataMapper.class);
		job.setReducerClass(Reducer.class);
		job.setNumReduceTasks(1);

		// Configure output
		FileOutputFormat.setOutputPath(job, new Path(outputPath));
		job.setOutputKeyClass(NullWritable.class);
		job.setOutputValueClass(Text.class);

		return job;
	}

	private static void deleteExistingDirs(Configuration conf, String[] args) throws IOException {
		Path temp = new Path(args[1]);
		boolean delete = temp.getFileSystem(conf).delete(temp, true);
		if (delete) {
			log.info("Deleted " + temp + "? " + delete);
		}
		
		Path training = new Path(args[2]);
		delete = training.getFileSystem(conf).delete(training, true);
		if (delete) {
			log.info("Deleted " + training + "? " + delete);
		}
		
		Path test = new Path(args[3]);
		delete = test.getFileSystem(conf).delete(test, true);
		if (delete) {
			log.info("Deleted " + test + "? " + delete);
		}
	}
	
	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();		
		if (otherArgs.length != 6) {
			log.error("Usage: split <in> <tmp> <training_out> <test_out> <min_year> <max_year>");
			for (int i = 0; i < otherArgs.length; ++i) {
				log.error(otherArgs[i]);
			}
			System.exit(2);
		}

		// Delete the output if it does exist
		deleteExistingDirs(conf, otherArgs);
		
		// Set the min and the max year we are interesting to deal with
		int min = Integer.parseInt(otherArgs[4]);
		int max = Integer.parseInt(otherArgs[5]);
		conf.setInt(MIN_YEAR, Math.max(min, 1922));
		conf.setInt(MAX_YEAR, Math.min(max, 2011));

		// Create the split data job, this job randomly splits the entire 
		// extracted features into 90% training data and 10% test data.
		Job job1 = getSplitDataJob(conf, otherArgs[0], otherArgs[1]);
		if (!job1.waitForCompletion(true)) {
			System.exit(1);
		}
		
		// Extract all training data from the split data job's output.
		Job job2 = getExtractTrainingDataJob(conf, otherArgs[1] + "/part*", otherArgs[2]);
		if (!job2.waitForCompletion(true)) {
			System.exit(1);
		}
		
		// Extract all test data from the split data job's output.
		Job job3 = getExtractTestDataJob(conf, otherArgs[1] + "/part*", otherArgs[3]);
		if (!job3.waitForCompletion(true)) {
			System.exit(1);
		}
		
		log.info("Done!");
		System.exit(0);
	}
}
