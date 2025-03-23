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
import java.util.*;

public class TopProductsHelpfulReviews {

    // Mapper Class: Reads input JSON, extracts product ID (ASIN) and helpful votes, and emits (ASIN, helpful_votes)
    public static class HelpfulVotesMapper extends Mapper<Object, Text, Text, IntWritable> {
        private Text asin = new Text();
        private IntWritable helpfulVotes = new IntWritable();

        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            try {
                // Parse JSON input
                JSONObject json = new JSONObject(value.toString());
                
                // Check if JSON contains 'asin' (product ID) and 'helpful_vote'
                if (json.has("asin") && json.has("helpful_vote")) {
                    asin.set(json.getString("asin"));
                    
                    // Extract helpful votes as an integer
                    int votes = json.getInt("helpful_vote"); 
                    
                    // Only consider products with helpful votes > 0
                    if (votes > 0) {
                        helpfulVotes.set(votes);
                        context.write(asin, helpfulVotes);
                    }
                }
            } catch (Exception e) {
                // Ignore malformed JSON lines
            }
        }
    }

    // Reducer Class: Aggregates total helpful votes per product and finds the top 20
    public static class HelpfulVotesReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
        private TreeMap<Integer, String> topProducts = new TreeMap<>(Collections.reverseOrder());

        public void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
            int totalVotes = 0;
            
            // Sum up the total helpful votes for each product
            for (IntWritable val : values) {
                totalVotes += val.get();
            }
            
            // Store in a TreeMap for sorting by vote count (descending order)
            topProducts.put(totalVotes, key.toString());
        }

        @Override
        protected void cleanup(Context context) throws IOException, InterruptedException {
            int count = 0;
            
            // Output the top 20 products by helpful votes
            for (Map.Entry<Integer, String> entry : topProducts.entrySet()) {
                if (count++ >= 20) break; // Limit to top 20 products
                context.write(new Text(entry.getValue()), new IntWritable(entry.getKey()));
            }
        }
    }

    // Driver Code: Sets up and runs the Hadoop job
    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "Top Products by Helpful Reviews");

        job.setJarByClass(TopProductsHelpfulReviews.class);
        job.setMapperClass(HelpfulVotesMapper.class);
        job.setReducerClass(HelpfulVotesReducer.class);

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
