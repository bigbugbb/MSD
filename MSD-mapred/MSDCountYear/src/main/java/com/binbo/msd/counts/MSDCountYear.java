package com.binbo.msd.counts;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.jobcontrol.ControlledJob;
import org.apache.hadoop.mapreduce.lib.jobcontrol.JobControl;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.log4j.Logger;

public class MSDCountYear 
{
	// Logger
	private static Logger log = Logger.getLogger(MSDCountYear.class);
	
	public static class CountYearMapper extends
			Mapper<Object, Text, IntWritable, IntWritable> {

		// The map output key
		private IntWritable mKey = new IntWritable();
		
		// The map output value
		private IntWritable mValue = new IntWritable();
		
		// The map from year to its count
		private HashMap<Integer, Integer> mMap = new HashMap<Integer, Integer>();
		
		public void map(Object key, Text value, Context context)
				throws IOException, InterruptedException {
			final String line = value.toString(); 						
			int year = Integer.parseInt(line.substring(0, line.indexOf(',', 0)));
			mMap.put(year, mMap.get(year) == null ? 1 : mMap.get(year) + 1);
			mMap.put(0, mMap.get(0) == null ? 1 : mMap.get(0) + 1);  // The sum of counts of all years
		}
		
		/**
		 * Called once at the end of the task.
		 */
		protected void cleanup(Context context) throws IOException, InterruptedException {
			for (Entry<Integer, Integer> entry : mMap.entrySet()) {
				mKey.set(entry.getKey());
				mValue.set(entry.getValue());
				context.write(mKey, mValue);
			}
		}
	}

	public static class CountYearReducer extends
			Reducer<IntWritable, IntWritable, NullWritable, Text> {

		// The output value
		private Text mOutput = new Text();
		
		// The sum of counts of all years
		private int mSum = 0;

		public void reduce(IntWritable key, Iterable<IntWritable> values, Context context)
				throws IOException, InterruptedException {
			// Compute the sume of counts of all years
			if (key.get() == 0) {
				for (IntWritable value : values) {
					mSum += value.get();
				}			
				return;
			}
			
			// Accumulate the count of this year
			int count = 0;
			for (IntWritable value : values) {
				count += value.get();
			}
			
			// Construct the output
			mOutput.set(String.format("%d,%d,%f", key.get(), count, Math.log((double) count / mSum)));
			
			// Emit the count of this year		
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

	public static final String[] JOBNAMES = { "Compute year probability Job" };

	public static Job getJob(Configuration conf, String inputPath,
			String outputPath) throws IOException {
		Job job = new Job(conf, JOBNAMES[0]);
		job.setJarByClass(MSDCountYear.class);

		// Configure input source
		FileInputFormat.addInputPath(job, new Path(inputPath));

		// Configure mapper and reducer
		job.setMapperClass(CountYearMapper.class);
		job.setReducerClass(CountYearReducer.class);
		job.setNumReduceTasks(1);

		// Configure output
		FileOutputFormat.setOutputPath(job, new Path(outputPath));
		job.setMapOutputKeyClass(IntWritable.class);
		job.setMapOutputValueClass(IntWritable.class);
		job.setOutputKeyClass(NullWritable.class);
		job.setOutputValueClass(Text.class);

		return job;
	}

	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
		if (otherArgs.length != 2) {
			log.error("Usage: <in> <out>");
			System.exit(2);
		}

		// Delete the output if it does exist
		Path output = new Path(otherArgs[1]);
		boolean delete = output.getFileSystem(conf).delete(output, true);
		if (delete) {
			log.info("Deleted " + output + "? " + delete);
		}

		// Create job for get the count and probabilities of the years
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
