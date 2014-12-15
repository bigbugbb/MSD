package com.binbo.msd;

import java.io.IOException;
import java.text.DecimalFormat;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.correlation.Covariance;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
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

/**
 * This class creates the mapreduce job to extract useful features in MSD from AWS PDS.
 * The entire MSD was scanned. For every track, timbre average and timbre covariance are used as 
 * the features. 
 * 20 median machines were used for this on EMR and the total amount of time is about 21 minutes.
 * The output file is about 365MB on S3.
 *  
 * @author bigbug
 *
 */
public class MSDExtractFeatures {
	
	// Logger
	private static Logger log = Logger.getLogger(MSDExtractFeatures.class);
	
	public static class MSDExtractFeaturesMapper extends Mapper<LongWritable, Text, NullWritable, Text> {		
		
		DecimalFormat mFormat;
		
		private double[] mFeatures;
		
		private Text mOutput;
		
		private final static int DIMENSIONS = 12;
		
		private int getOutputSize() {
			int size = DIMENSIONS;
			for (int i = 1; i <= DIMENSIONS; ++i) {
				size += i;
			}
			return size;
		}
		
		/**
		 * Called once at the beginning of the task.
		 */
		protected void setup(Context context) throws IOException, InterruptedException {
			mFormat = new DecimalFormat("0.000000");
			mFeatures = new double[getOutputSize()];	
			mOutput = new Text();
		}

		protected RealMatrix createRealMatrix(double[] data, int rows, int cols) {
	        double[][] matrixData = new double[rows][cols];
	        int ptr = 0;
	        for (int i = 0; i < rows; i++) {
	            System.arraycopy(data, ptr, matrixData[i], 0, cols);
	            ptr += cols;
	        }
	        return new Array2DRowRealMatrix(matrixData);
	    }
		
		public void map(LongWritable key, Text value, Context context)
				throws IOException, InterruptedException {
			// Parse each track data
			MSD track = new MSD(value);
			
			// Skip year == 0
			int year = track.getYear();
			if (year > 0) {
				// Get timbre and skip bad possible bad data
				double[] timbre = track.getSegmentsTimbre();
				if (timbre.length < DIMENSIONS)
					return;				
				
				// Get dimensions
				int rows = timbre.length / DIMENSIONS;
				int cols = DIMENSIONS;
				
				// Compute the timbre average					
				for (int i = 0; i < cols; ++i) {
					for (int j = 0; j < rows; ++j) {
						mFeatures[i] += timbre[i * rows + j];
					}
					mFeatures[i] /= rows;
				}
				
				// Compute the timbre covariance
				RealMatrix matrix = createRealMatrix(timbre, rows, cols);
		        RealMatrix covarianceMatrix = new Covariance(matrix).getCovarianceMatrix();
		        
		        // Populate the features array with diagnal data
		        int ptr = DIMENSIONS;
		        for (int i = 0; i < covarianceMatrix.getRowDimension(); ++i) {
		        	for (int j = 0; j <= i; ++j) {
		        		mFeatures[ptr++] = covarianceMatrix.getEntry(i, j);
		        	}
		        }
		        
		        // Construct the output with the year, 8 ids and 90 features
		        StringBuilder builder = new StringBuilder();
		        builder.append(year);
		        builder.append(",");
		        builder.append(track.getTrackId());
		        builder.append(",");   
		        builder.append(track.getTrack7digitalid());
		        builder.append(",");
		        builder.append(track.getArtist7DigitalId());
		        builder.append(",");
		        builder.append(track.getArtistId());
		        builder.append(",");
		        builder.append(track.getArtistMbid());
		        builder.append(",");
		        builder.append(track.getArtistPlaymeid());
		        builder.append(",");
		        builder.append(track.getRelease7digitalid());
		        builder.append(",");
		        builder.append(track.getSongId());		        
		        for (double feature : mFeatures) {
		        	builder.append(",");
		        	builder.append(mFormat.format(feature));		        	
		        }
		        mOutput.set(builder.toString());
		        
		        // Write the result
		        context.write(NullWritable.get(), mOutput);
			}
		}		
	}

	public static Job getJob(Configuration conf, String inputPath, String outputPath) throws IOException {
		Job job = new Job(conf, "MSDExtractFeatures Job");
		job.setJarByClass(MSDExtractFeatures.class);

		// Configure input source
		FileInputFormat.addInputPath(job, new Path(inputPath));

		// Configure mapper and reducer
		job.setMapperClass(MSDExtractFeaturesMapper.class);
		job.setReducerClass(Reducer.class);

		// Configure output
		job.setOutputKeyClass(NullWritable.class);
	    job.setOutputValueClass(Text.class);
	    FileOutputFormat.setOutputPath(job, new Path(outputPath));		

		return job;
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
	
	public static void main(String[] args) throws Exception {		
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
		if (otherArgs.length != 2) {
			log.error("Usage: extract_features <in> <out>");
			System.exit(2);
		}
		
		log.info(String.format("Read from: %s", otherArgs[0]));
		log.info(String.format("Write to: %s", otherArgs[1]));
		
		// Delete the output if it does exist
		Path output = new Path(otherArgs[1]);
		boolean delete = output.getFileSystem(conf).delete(output, true);
		if (delete) {
			log.info("Deleted " + output + "? " + delete);
		}		
		
		// Create the job
		Job job = getJob(conf, otherArgs[0], otherArgs[1]);
		ControlledJob cJob = new ControlledJob(conf);
		cJob.setJob(job);

		// Create the job control
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
