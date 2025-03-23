import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.json.JSONObject;
import java.io.IOException;
import java.util.*;

public class ProductPopularityEngagement {

    // Mapper Class
    public static class PopularityMapper extends Mapper<LongWritable, Text, Text, Text> {
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            try {
                // Parse JSON
                JSONObject review = new JSONObject(value.toString());

                // Extract necessary fields
                String asin = review.optString("asin", "UNKNOWN");
                double rating = review.optDouble("rating", 0.0);
                int helpfulVotes = review.optInt("helpful_vote", 0);

                // Emit Key-Value pair: (asin → [rating, helpful_votes, 1])
                context.write(new Text(asin), new Text(rating + "," + helpfulVotes + ",1"));
            } catch (Exception e) {
                System.err.println("Error parsing JSON: " + e.getMessage());
            }
        }
    }

    // Reducer Class
    public static class PopularityReducer extends Reducer<Text, Text, NullWritable, Text> {
        private PriorityQueue<Map.Entry<String, Double>> topProducts = 
            new PriorityQueue<>(Comparator.comparing(Map.Entry::getValue));
        
        public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            int totalReviews = 0;
            double totalRating = 0.0;
            int totalHelpfulVotes = 0;

            for (Text val : values) {
                String[] parts = val.toString().split(",");
                totalRating += Double.parseDouble(parts[0]);
                totalHelpfulVotes += Integer.parseInt(parts[1]);
                totalReviews += Integer.parseInt(parts[2]);
            }

            double avgRating = totalReviews == 0 ? 0 : totalRating / totalReviews;
            double engagementScore = totalReviews + totalHelpfulVotes; // Simple engagement metric
            
            String productData = key.toString() + "," + totalReviews + "," + avgRating + "," + totalHelpfulVotes;
            topProducts.add(new AbstractMap.SimpleEntry<>(productData, engagementScore));
            
            if (topProducts.size() > 20) {
                topProducts.poll(); // Keep only top 20 products
            }
        }

        @Override
        protected void cleanup(Context context) throws IOException, InterruptedException {
            List<Map.Entry<String, Double>> sortedProducts = new ArrayList<>(topProducts);
            sortedProducts.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            
            for (Map.Entry<String, Double> product : sortedProducts) {
                context.write(NullWritable.get(), new Text(product.getKey()));
            }
        }
    }

    // Driver Code
    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "Product Popularity and Engagement");
        job.setJarByClass(ProductPopularityEngagement.class);
        
        job.setMapperClass(PopularityMapper.class);
        job.setReducerClass(PopularityReducer.class);

        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(Text.class);
        
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
