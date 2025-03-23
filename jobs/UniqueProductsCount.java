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
import java.util.HashSet;

public class UniqueProductsCount {

    public static class ProductMapper extends Mapper<Object, Text, Text, IntWritable> {
        private Text asin = new Text();
        private final static IntWritable one = new IntWritable(1);

        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            try {
                // Parse JSON and extract ASIN
                JSONObject json = new JSONObject(value.toString());
                if (json.has("asin") && !json.getString("asin").isEmpty()) {
                    asin.set(json.getString("asin"));
                    context.write(asin, one);  // Emit (asin, 1)
                }
            } catch (Exception e) {
                // Ignore malformed JSON lines
            }
        }
    }

    public static class ProductReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
        private HashSet<String> uniqueProducts = new HashSet<>();

        public void reduce(Text key, Iterable<IntWritable> values, Context context) {
            // Add ASIN to HashSet to ensure uniqueness
            uniqueProducts.add(key.toString());
        }

        @Override
        protected void cleanup(Context context) throws IOException, InterruptedException {
            // Output total unique product count
            context.write(new Text("Total unique products"), new IntWritable(uniqueProducts.size()));
        }
    }

    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "Unique Products Count");

        job.setJarByClass(UniqueProductsCount.class);
        job.setMapperClass(ProductMapper.class);
        job.setReducerClass(ProductReducer.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);

        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));

        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
