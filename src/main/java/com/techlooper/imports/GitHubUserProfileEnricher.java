package com.techlooper.imports;

import com.techlooper.utils.PropertyManager;
import com.techlooper.utils.Utils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.search.SearchHits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by phuonghqh on 1/30/15.
 */
public class GitHubUserProfileEnricher {

  private static Logger LOGGER = LoggerFactory.getLogger(GitHubUserProfileEnricher.class);

  private static List<String> refineImportIOFields = Arrays.asList("organizations", "popular_repos", "contributed_repos");

  public static void main(String[] args) throws IOException {
//    Files.lines(Paths.get(inputFile), StandardCharsets.UTF_8).parallel().forEach(username -> {
//      try {
//        enrichUserProfile(username);
//      }
//      catch (Exception e) {
//        LOGGER.error("ERROR:", e);
//      }
//    });
    queryES();
  }

  private static void queryES() throws IOException {
    Client client = Utils.esClient();
    String outputDirectory = PropertyManager.properties.getProperty("githubUserProfileEnricher.outputDirectory");

    SearchRequestBuilder searchRequestBuilder = client.prepareSearch(PropertyManager.properties.getProperty("githubUserProfileEnricher.es.index"));
    SearchResponse response = searchRequestBuilder.setSearchType(SearchType.COUNT).execute().actionGet();

    long totalUsers = response.getHits().getTotalHits();
    long maxPageNumber = (totalUsers % 100 == 0) ? totalUsers / 100 : totalUsers / 100 + 1;

    for (int pageNumber = 0; pageNumber < maxPageNumber; pageNumber++) {
      response = searchRequestBuilder.addField("profiles.GITHUB.username")
        .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
        .setFrom(pageNumber * 100).setSize(100).execute().actionGet();
      List<String> failedUsernames = new ArrayList<>();

      List<String> usernames = new ArrayList<>();
      response.getHits().forEach(hit -> usernames.add(hit.field("profiles.GITHUB.username").getValue()));
      usernames.parallelStream().forEach(username -> {
        try {
          String failedUsername = enrichUserProfile(username);
          if (failedUsername != null) {
            failedUsernames.add(failedUsername);
          }
        }
        catch (IOException e) {
          LOGGER.error("ERROR", e);
        }
      });

      if (failedUsernames.size() > 0) {
        final StringBuilder usnBuilder = new StringBuilder();
        failedUsernames.forEach(usn -> usnBuilder.append(",").append("\"").append(usn).append("\""));
        Utils.writeToFile(usnBuilder.deleteCharAt(0).insert(0, "[").append("]").toString(),
          String.format("%sgithubUserProfileEnricher.%d.json", outputDirectory, pageNumber));
      }
    }
    client.close();
  }

  private static String enrichUserProfile(String username) throws IOException {
    String enrichUserApi = PropertyManager.properties.getProperty("githubUserProfileEnricher.techlooper.api.enrichUser");
    String userId = PropertyManager.properties.getProperty("githubUserProfileEnricher.userId");
    String apiKey = PropertyManager.properties.getProperty("githubUserProfileEnricher.apiKey");
    String connectorId = PropertyManager.properties.getProperty("githubUserProfileEnricher.githubProfile");
    String queryUrlTemplate = PropertyManager.properties.getProperty("githubUserProfileEnricher.queryUrlTemplate");

    boolean tryAgain = true;

    while (tryAgain) {
      HttpClient httpClient = HttpClients.createDefault();
      HttpPost post = new HttpPost(String.format("https://api.import.io/store/data/%s/_query?_user=%s&_apikey=%s",
        connectorId, userId, URLEncoder.encode(apiKey, "UTF-8")));

      String queryUrl = String.format(queryUrlTemplate, username);
      String json = String.format("{ \"input\": {\"webpage/url\": \"%s\"} }", queryUrl);
      post.setEntity(new StringEntity(json, ContentType.create("application/json", "UTF-8")));

      String content = null;
      try {
        HttpResponse response = httpClient.execute(post);
        content = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
      }
      catch (Exception err) {
        LOGGER.error("ERROR", err);
        continue;
      }

      json = Utils.getImportIOResult(content);
      if (json == null) {
        LOGGER.debug("Error result => query: {}", queryUrl);
        break;
      }

      for (String field : refineImportIOFields) {
        String fieldPattern = "\"" + field + "\":\"";
        int indexOfField = json.indexOf(fieldPattern);
        if (indexOfField == -1) {
          continue;
        }
        String endFieldPattern = "\",\"";

        String extractJson = json.substring(indexOfField, json.indexOf(endFieldPattern, indexOfField) + endFieldPattern.length());
        if (extractJson != null && !extractJson.contains("[")) {
          LOGGER.debug("Extract json: {} => Refine data", extractJson);
          json = json.replaceAll(extractJson, extractJson.replaceAll(":\"", ":[\"").replaceAll("\",", "\"],"));
        }
      }

      LOGGER.debug("OK => Post to api {} ", enrichUserApi);
      if (Utils.postJsonString(enrichUserApi, json) != 204) {
        LOGGER.error("Error when posting json {} to api {}", json, enrichUserApi);
        return username;
      }
      tryAgain = false;
    }
    return null;
  }
}