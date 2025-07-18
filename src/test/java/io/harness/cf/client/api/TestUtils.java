package io.harness.cf.client.api;

import com.google.gson.Gson;
import io.harness.cf.model.FeatureConfig;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

public class TestUtils {

  public static String makeSegmentsJson() throws IOException, URISyntaxException {
    return getJsonResource("local-test-cases/segments.json");
  }

  public static String makeFeatureJson() throws IOException, URISyntaxException {
    return getJsonResource("local-test-cases/percentage-rollout-with-zero-weights.json");
  }

  public static String makeBasicFeatureJson() throws IOException, URISyntaxException {
    return getJsonResource("local-test-cases/basic_bool_string_number_json_variations.json");
  }

  public static String makeBasicFeatureJsonForRepoTest() throws IOException, URISyntaxException {
    return getJsonResource("local-test-cases/basic_bool_string_for_repository.json");
  }

  public static String getJsonResource(String location) throws IOException, URISyntaxException {
    final Path path = Paths.get(EvaluatorTest.class.getClassLoader().getResource(location).toURI());
    return new String(Files.readAllBytes(path));
  }

  private final Gson gson = new Gson();

  public List<FeatureConfig> CreateBenchmarkData(int size, int version) throws Exception {
    FeatureConfig fg = GetFeatureConfigFromFile();
    List<FeatureConfig> list = new LinkedList<FeatureConfig>();
    for (int i = 1; i <= size; i++) {
      FeatureConfig f = fg;
      f.setFeature("simpleBool" + i);
      f.setVersion((long) version);
      // we are copying objects
      FeatureConfig df = gson.fromJson(gson.toJson(f), FeatureConfig.class);
      list.add(df);
    }
    //    System.out.println(list);
    return list;
  }

  public FeatureConfig GetFeatureConfigFromFile() throws Exception {
    try {
      String relativePath =
          "./src/test/resources/local-test-cases/basic_bool_string_for_repository.json";
      // Resolve the absolute path
      String filePath = new File(relativePath).getCanonicalPath();
      // Read the content of the file into a String
      String jsonString = new String(Files.readAllBytes(Paths.get(filePath)));

      final FeatureConfig featureConfig = gson.fromJson(jsonString, FeatureConfig.class);
      return featureConfig;

    } catch (IOException e) {
      // Handle exceptions like file not found or read errors
      e.printStackTrace();
    }
    return null;
  }
}
