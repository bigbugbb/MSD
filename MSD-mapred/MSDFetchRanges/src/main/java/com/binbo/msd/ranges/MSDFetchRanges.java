package com.binbo.msd.ranges;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.Mapper.Context;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.jobcontrol.ControlledJob;
import org.apache.hadoop.mapreduce.lib.jobcontrol.JobControl;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.log4j.Logger;


public class MSDFetchRanges {
	
	// Logger
	private static Logger log = Logger.getLogger(MSDFetchRanges.class);
	
	private static final String AVG_STEP = "AVG_STEP";
	private static final String COV_STEP = "COV_STEP";
	
	public static class FetchRangesMapper extends
			Mapper<Object, Text, IntWritable, DoubleWritable> {

		// The map output key
		private IntWritable mKey = new IntWritable();
		
		// The map output value
		private DoubleWritable mValue = new DoubleWritable();
		
		// The count of commas before the first feature
		private final static int FEATURES_OFFSET = 9;				

		public void map(Object key, Text value, Context context)
				throws IOException, InterruptedException {
			final String line = value.toString(); 						
			int start = 0;  // The current position in this line
						
			// Skip the year and all kinds of ids						
			for (int i = 0; i < FEATURES_OFFSET; ++i, ++start) {
				start = line.indexOf(',', start);
			}
			
			double feature;
			for (int i = 0; start < line.length(); ++i) {
				// Parse out each feature				
				int stop = line.indexOf(',', start);
				if (stop != -1) {
					feature = Double.parseDouble(line.substring(start, stop));
					start = stop + 1;
				} else {
					feature = Double.parseDouble(line.substring(start));
					start = line.length();
				}
				
				// Emit each feature
				mKey.set(i);
				mValue.set(feature);
				context.write(mKey, mValue);
			}
		}
	}

	public static class FetchRangesReducer extends
			Reducer<IntWritable, DoubleWritable, NullWritable, Text> {
		
		// The output value
		private Text mOutput = new Text();
		
		// The configurable step size
		private int mAvgStep, mCovStep;		
		
		protected void setup(Context context) throws IOException, InterruptedException {
			mAvgStep = context.getConfiguration().getInt(AVG_STEP, 10);
			mCovStep = context.getConfiguration().getInt(COV_STEP, 50);
		}

		public void reduce(IntWritable key, Iterable<DoubleWritable> values, Context context)
				throws IOException, InterruptedException {
			// Get the min and the max from the incoming values
			double min = Double.MAX_VALUE, max = Double.MIN_VALUE;
			for (DoubleWritable value : values) {
				if (value.get() < min) {
					min = value.get();
				}
				if (value.get() > max) {
					max = value.get();
				}
			}
			
			/*
			 * Construct the output with feature index, feature range and the step
			 */
			mOutput.set(String.format("%d,%f,%f,%d", key.get(), min, max, key.get() < 12 ? mAvgStep : mCovStep));			
			
			// Emit the range with the step
			context.write(NullWritable.get(), mOutput);
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

	public static final String[] JOBNAMES = { "Compute Features Ranges Job" };

	public static Job getJob(Configuration conf, String inputPath,
			String outputPath) throws IOException {
		Job job = new Job(conf, JOBNAMES[0]);
		job.setJarByClass(MSDFetchRanges.class);

		// Configure input source
		FileInputFormat.addInputPath(job, new Path(inputPath));

		// Configure mapper and reducer
		job.setMapperClass(FetchRangesMapper.class);
		job.setReducerClass(FetchRangesReducer.class);
		job.setNumReduceTasks(1);

		// Configure output
		FileOutputFormat.setOutputPath(job, new Path(outputPath));
		job.setMapOutputKeyClass(IntWritable.class);
		job.setMapOutputValueClass(DoubleWritable.class);
		job.setOutputKeyClass(NullWritable.class);
		job.setOutputValueClass(Text.class);

		return job;
	}

	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
		if (otherArgs.length != 4) {
			log.error("Usage: ranges <in> <out> <avg_step> <cov_step>");
			System.exit(2);
		}

		// Delete the output if it does exist
		Path output = new Path(otherArgs[1]);
		boolean delete = output.getFileSystem(conf).delete(output, true);
		if (delete) {
			log.info("Deleted " + output + "? " + delete);
		}
		
		// Set the configurable step size for average and covariance features
		conf.setInt(AVG_STEP, Integer.parseInt(otherArgs[2]));
		conf.setInt(COV_STEP, Integer.parseInt(otherArgs[3]));

		// Create job for fetching the ranges of every column of features 
		Job job = getJob(conf, otherArgs[0], otherArgs[1]);
		ControlledJob cJob = new ControlledJob(conf);
		cJob.setJob(job);

		// Create the job control.
		JobControl jobCtrl = new JobControl("jobctrl");
		jobCtrl.addJob(cJob);

		// Create a thread to run the job in the background.
		Thread jobRunnerThread = new Thread(new JobRunner(jobCtrl));
		jobRunnerThread.setDaemon(true);
		jobRunnerThread.start();

		while (!jobCtrl.allFinished()) {
			log.info("Still running...");
			Thread.sleep(5000);
		}
		log.info("Done");
		jobCtrl.stop();

		if (jobCtrl.getFailedJobList().size() > 0) {
			log.error(jobCtrl.getFailedJobList().size() + " jobs failed!");
			for (ControlledJob aJob : jobCtrl.getFailedJobList()) {
				log.error(aJob.getJobName() + " failed");
			}
			System.exit(1);
		} else {
			log.info("Success!! Workflow completed ["
					+ jobCtrl.getSuccessfulJobList().size() + "] jobs.");
			System.exit(0);
		}
	}
}
