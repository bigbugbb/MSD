package com.binbo.msd.train;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.WritableComparator;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.jobcontrol.ControlledJob;
import org.apache.hadoop.mapreduce.lib.jobcontrol.JobControl;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.log4j.Logger;

import com.google.gson.Gson;


public class MSDTrainNaiveBayes {
	
	// Logger
	private static Logger log = Logger.getLogger(MSDTrainNaiveBayes.class);
	
	// The feature count
	private final static int FEATURES_COUNT = 90;
	
	// The key to access the json object
	private static final String YEAR_COUNTS = "YEAR_COUNTS";
	private static final String FEATURE_RANGES = "FEATURE_RANGES";	
	
	public static class TrainingKey implements WritableComparable<TrainingKey> {

		// The year when the song was released
		public int mYear;
		
		// The feature index
		public int mIndex;

		public TrainingKey() {
		}

		public void set(int year, int index) {
			mYear  = year;
			mIndex = index;			
		}

		///////////////////////////////////////////// Implement Writable
		public void write(DataOutput out) throws IOException {
			out.writeInt(mYear);
			out.writeInt(mIndex);			
		}

		///////////////////////////////////////////// Implement Writable
		public void readFields(DataInput in) throws IOException {
			mYear  = in.readInt();
			mIndex = in.readInt();			
		}

		///////////////////////////////////////////// Implement Comparable
		public int compareTo(TrainingKey obj) {
			int result = mYear - obj.mYear;
			if (result == 0) {
				result = mIndex - obj.mIndex;				
			}
			return result;
		}
	}
	
	public static class TrainingValue implements WritableComparable<TrainingValue> {
		
		// The feature index
		public int mIndex;
		
		// The number of times each feature appears in its interval
		public int[] mTimes;

		public TrainingValue() {
		}

		public void set(int index, int[] times) {
			mIndex = index;
			mTimes = times;
		}

		///////////////////////////////////////////// Implement Writable
		public void write(DataOutput out) throws IOException {
			out.writeInt(mIndex);
			out.writeInt(mTimes.length);
			for (int time : mTimes) {
				out.writeInt(time);
			}
		}		

		///////////////////////////////////////////// Implement Writable
		public void readFields(DataInput in) throws IOException {
			mIndex = in.readInt();
			mTimes = new int[in.readInt()];
			for (int i = 0; i < mTimes.length; ++i) {
				mTimes[i] = in.readInt();
			}
		}

		///////////////////////////////////////////// Implement Comparable
		public int compareTo(TrainingValue obj) {
			return mIndex - obj.mIndex;
		}
	}
	
	public static class TrainNaiveBayesMapper extends Mapper<Object, Text, TrainingKey, TrainingValue> {
		
		// The count of commas before the first feature
		private final static int FEATURES_OFFSET = 9;
				
		// The map output key
		private TrainingKey mKey = new TrainingKey();
		
		// The map output value
		private TrainingValue mValue = new TrainingValue();
					
		// The ranges read from distributed cache
		private List<double[]> mRanges = new ArrayList<double[]>();
		
		// The map from year to this year's feature distribution
		private HashMap<Integer, int[][]> mMap = new HashMap<Integer, int[][]>();

		/**
		 * Called once at the beginning of the task.
		 */
		protected void setup(Context context) throws IOException, InterruptedException {
			// Reconstruct the feature ranges from json
			String json = context.getConfiguration().get(FEATURE_RANGES);
			final String[] ranges = new Gson().fromJson(json, String[].class);
					
			for (String range : ranges) {
				// Parse each range	
				String[] splits = range.split("[,]");
				int index = Integer.parseInt(splits[0]); // The index of the feature column
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
		}

		public void map(Object key, Text value, Context context)
				throws IOException, InterruptedException {
			final String line = value.toString();
			int start = line.indexOf(',') + 1;
			int year  = Integer.parseInt(line.substring(0, start - 1));

			// Create the feature distribution map for this year
			int[][] dist = mMap.get(year);
			if (dist == null) {
				dist = new int[FEATURES_COUNT][];
				for (int i = 0; i < FEATURES_COUNT; ++i) {
					dist[i] = new int[mRanges.get(i).length - 1];
				}
				mMap.put(year, dist);
			}

			// Skip the year and all kinds of ids						
			for (int i = 1; i < FEATURES_OFFSET; ++i, ++start) {
				start = line.indexOf(',', start);
			}
			
			double feature;
			for (int i = 0; start < line.length(); ++i) { // i is the index of each feature column
				// Parse out each feature				
				int stop = line.indexOf(',', start);
				if (stop != -1) {
					feature = Double.parseDouble(line.substring(start, stop));
					start = stop + 1;
				} else {
					feature = Double.parseDouble(line.substring(start));
					start = line.length();
				}
				
				// Get the index of interval in which this feature lies and accumulate the appearance count
				double[] range = mRanges.get(i);
				if (feature == range[range.length - 1]) feature = range[range.length - 2];
				for (int j = 0; j < range.length - 1; ++j) {
					if (feature >= range[j] && feature < range[j + 1]) {						
						dist[i][j] += 1;						
						break;
					}
				}				
			}
		}
		
		/**
	     * Called once at the end of the task.
	     */
		protected void cleanup(Context context) throws IOException, InterruptedException {
			for (Entry<Integer, int[][]> entry : mMap.entrySet()) {
				final int year = entry.getKey();
				final int[][] dist = entry.getValue();
				for (int i = 0; i < FEATURES_COUNT; ++i) {
					mKey.set(year, i);
					mValue.set(i, dist[i]);
					// Emit each feature				
					context.write(mKey, mValue);
				}
			}
	    }
	}
	
	/**
	 * The partitioner uses year to split the data to the reducer. 
	 * Since the distribution data of each year has been combined by the map per task tally,
	 * so the load balance should be fine.
	 */	
	public static class TrainNaiveBayesPartitioner extends Partitioner<TrainingKey, TrainingValue> {

		@Override
		public int getPartition(TrainingKey key, TrainingValue value, int numPartitions) {			
			return (key.mYear & Integer.MAX_VALUE) % numPartitions;
		}
	}

	// The key grouping comparator "groups" values together according to the
	// airline id.
	public static class TrainNaiveBayesGroupingComparator extends WritableComparator {

		public TrainNaiveBayesGroupingComparator() {
			super(TrainingKey.class, true);
		}

		@SuppressWarnings("rawtypes")
		@Override
		public int compare(WritableComparable w1, WritableComparable w2) {
			TrainingKey k1 = (TrainingKey) w1;
			TrainingKey k2 = (TrainingKey) w2;
			return k1.mYear - k2.mYear;
		}
	}
	
	public static class TrainNaiveBayesReducer extends Reducer<TrainingKey, TrainingValue, NullWritable, Text> {
	
		// The output value
		private Text mOutput = new Text();
		
		// The space for holding this year's feature distribution
		private int[][] mDistribution = new int[FEATURES_COUNT][];
		
		// The array contains the total count of each year in the dataset
		private HashMap<Integer, Integer> mCountMap = new HashMap<Integer, Integer>();
		
		/**
		 * Called once at the beginning of the task.
		 */
		protected void setup(Context context) throws IOException, InterruptedException {
			// Reconstruct the feature ranges from json
			String json = context.getConfiguration().get(YEAR_COUNTS);
			final String[] yearsAndCounts = new Gson().fromJson(json, String[].class);
			
			// Populate the map from year to its count
			for (String yearAndCount : yearsAndCounts) {
				String[] splits = yearAndCount.split("[,]");
				int year  = Integer.parseInt(splits[0]);
				int count = Integer.parseInt(splits[1]);
				mCountMap.put(year, count);
			}
		}
		
		public void reduce(TrainingKey key, Iterable<TrainingValue> values, Context context)
				throws IOException, InterruptedException {
			
			int year  = key.mYear;
			int count = mCountMap.get(year);
			
			// Merge the distribution columns with the same index
			int index = -1;						
			
			// Populate the distribution space
			int[] times = null;
			for (TrainingValue value : values) {				
				if (index != value.mIndex) {					
					times = Arrays.copyOf(value.mTimes, value.mTimes.length);
					mDistribution[value.mIndex] = times;
					index = value.mIndex;
				} else {
					for (int i = 0; i < times.length; ++i) {
						times[i] += value.mTimes[i];
					}
				}
			}
			
			StringBuilder builder = new StringBuilder();
			builder.append(key.mYear);
			builder.append(",");
			for (int i = 0; i < mDistribution.length; ++i) {
				for (int j = 0; j < mDistribution[i].length; ++j) {						
					if (i != 0 || j != 0) {
						// Use ' ' as the seperator so we can import all features to a single postgres column.
						builder.append(" ");
					}
					// Add 1 to avoid zero probability, use log to fix the precision problem.
					builder.append(String.format("%f",
						Math.log((double) (mDistribution[i][j] + 1) / (count + mDistribution[i].length))));						
				}
			}
			mOutput.set(builder.toString());
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

	public static final String[] JOBNAMES = { "Naive Bayes Training Job" };

	public static Job getJob(Configuration conf, String rangesPath, String countsPath,
			String inputPath, String outputPath) throws IOException, URISyntaxException {
		Job job = new Job(conf, JOBNAMES[0]);
		job.setJarByClass(MSDTrainNaiveBayes.class);

		// Configure input source
		FileInputFormat.addInputPath(job, new Path(inputPath));

		// Configure mapper and reducer
		job.setMapperClass(TrainNaiveBayesMapper.class);
		job.setPartitionerClass(TrainNaiveBayesPartitioner.class);
		job.setGroupingComparatorClass(TrainNaiveBayesGroupingComparator.class);
		job.setReducerClass(TrainNaiveBayesReducer.class);
		job.setNumReduceTasks(1);

		// Configure output
		FileOutputFormat.setOutputPath(job, new Path(outputPath));
		job.setMapOutputKeyClass(TrainingKey.class);
		job.setMapOutputValueClass(TrainingValue.class);
		job.setOutputKeyClass(NullWritable.class);
		job.setOutputValueClass(Text.class);
		
		// Add the year counts and feature ranges
		job.getConfiguration().set(YEAR_COUNTS, new Gson().toJson(loadDataByLine(job.getConfiguration(), new Path(countsPath))));
		job.getConfiguration().set(FEATURE_RANGES, new Gson().toJson(loadDataByLine(job.getConfiguration(), new Path(rangesPath))));
		
		return job;
	}
	
	private static String[] loadDataByLine(Configuration conf, Path path) {
		List<String> list = new ArrayList<String>();
		
		// List all data files
		FileSystem fs = null;
		FileStatus[] statuses = null;
		try {
			fs = path.getFileSystem(conf);
			if (fs.getFileStatus(path).isDir()) {
				statuses = fs.listStatus(path, new PathFilter() {
					@Override
					public boolean accept(Path path) {
						if (path.getName().contains(".DS_Store")) {
							return false;
						}						
						return true;
					}					
				});		
			} else {
				statuses = new FileStatus[] { fs.getFileStatus(path) };
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// Read data from all files in the input path
		for (FileStatus status : statuses) {	
			BufferedReader reader = null;
			try {
				log.trace(status);
				reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(fs.open(status.getPath()))));
				String line;
				while ((line = reader.readLine()) != null) {
					list.add(line);
				}
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				closeQuietly(reader);
			}
		}		
		
		if (list.size() > 0) {
			return list.toArray(new String[list.size()]);
		}		
		return null;
	}
	
	private static void closeQuietly(BufferedReader reader) {
		try {
			if (reader != null) {
				reader.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
		if (otherArgs.length != 4) {
			log.error("Usage: train <ranges> <counts> <in> <out>");
			System.exit(2);
		}

		// Delete the output if it does exist
		Path output = new Path(otherArgs[3]);
		boolean delete = output.getFileSystem(conf).delete(output, true);
		if (delete) {
			log.info("Deleted " + output + "? " + delete);
		}
		
		// Create job to train the model by Naive Bayes
		Job job = getJob(conf, otherArgs[0], otherArgs[1], otherArgs[2], otherArgs[3]);
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
