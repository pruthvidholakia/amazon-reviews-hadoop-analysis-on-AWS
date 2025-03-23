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

public class RatingDistributionAnalysis {

    // Mapper Class: Reads input JSON, extracts rating, and emits (rating, 1)
    public static class RatingMapper extends Mapper<Object, Text, IntWritable, IntWritable> {
        private final static IntWritable one = new IntWritable(1);
        private IntWritable rating = new IntWritable();

        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            try {
                // Parse JSON input
                JSONObject json = new JSONObject(value.toString());
                
                // Check if the JSON contains a 'rating' field
                if (json.has("rating")) {
                    int rate = json.getInt("rating");
                    
                    // Ensure the rating value is within the valid range (1-5 stars)
                    if (rate >= 1 && rate <= 5) {
                        rating.set(rate);
                        context.write(rating, one); // Emit (rating, 1)
                    }
                }
            } catch (Exception e) {
                // Ignore malformed JSON lines
            }
        }
    }

    // Reducer Class: Aggregates counts for each rating value
    public static class RatingReducer extends Reducer<IntWritable, IntWritable, IntWritable, IntWritable> {
        public void reduce(IntWritable key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
            int sum = 0;
            
            // Sum up the counts for each rating category
            for (IntWritable val : values) {
                sum += val.get();
            }
            
            // Write the output as (rating, total count)
            context.write(key, new IntWritable(sum));
        }
    }

    // Driver Code: Sets up and runs the Hadoop job
    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "Rating Distribution Analysis");

        job.setJarByClass(RatingDistributionAnalysis.class);
        job.setMapperClass(RatingMapper.class);
        job.setReducerClass(RatingReducer.class);

        // Define output key and value types
        job.setOutputKeyClass(IntWritable.class);
        job.setOutputValueClass(IntWritable.class);

        // Set input and output paths from command line arguments
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));

        // Run the job and exit based on success or failure
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
