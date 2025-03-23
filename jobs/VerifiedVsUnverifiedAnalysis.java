import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.json.JSONObject;
import java.io.IOException;

public class VerifiedVsUnverifiedAnalysis {

    // Mapper Class: Reads input JSON, extracts verified purchase status, and emits ("Verified" or "Unverified", 1)
    public static class VerifiedMapper extends Mapper<Object, Text, Text, IntWritable> {
        private Text verifiedStatus = new Text();
        private final static IntWritable one = new IntWritable(1);

        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            try {
                // Parse JSON input
                JSONObject json = new JSONObject(value.toString());
                
                // Check if JSON contains 'verified_purchase'
                if (json.has("verified_purchase")) {
                    boolean isVerified = json.getBoolean("verified_purchase");
                    verifiedStatus.set(isVerified ? "Verified" : "Unverified");
                    
                    // Emit ("Verified" or "Unverified", 1)
                    context.write(verifiedStatus, one);
                }
            } catch (Exception e) {
                // Ignore malformed JSON lines
            }
        }
    }

    // Reducer Class: Aggregates total verified and unverified reviews
    public static class VerifiedReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
        public void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
            int sum = 0;
            
            // Sum up the total count for each category (Verified or Unverified)
            for (IntWritable val : values) {
                sum += val.get();
            }
            
            // Write the final count output
            context.write(key, new IntWritable(sum));
        }
    }

    // Driver Code: Sets up and runs the Hadoop job
    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "Verified vs Unverified Purchase Analysis");

        job.setJarByClass(VerifiedVsUnverifiedAnalysis.class);
        job.setMapperClass(VerifiedMapper.class);
        job.setReducerClass(VerifiedReducer.class);

        // Define output key and value types
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);

        // Set input and output paths from command line arguments
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));

        // Run the job and exit based on success or failure
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
