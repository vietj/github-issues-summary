import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class Generator implements AutoCloseable {

  Set<String> blackList = new HashSet<>();
  Vertx vertx = Vertx.vertx();
  WebClient webClient = WebClient.create(vertx, new WebClientOptions().setMaxPoolSize(1));
  JsonArray all = new JsonArray();

  public Generator blackList(String... labels) {
    blackList.addAll(Arrays.asList(labels));
    return this;
  }

  public Generator fetch(String url) throws Exception {
    CompletableFuture<Void> fut = new CompletableFuture<>();
    fetch(url, 1, ar -> {
      if (ar.succeeded()) {
        fut.complete(null);
      } else {
        fut.completeExceptionally(ar.cause());
      }
    });
    fut.get(20, TimeUnit.SECONDS);
    return this;
  }

  private void fetch(String url, int page, Handler<AsyncResult<Void>> handler) {
    String absoluteURI = url + "&page=" + page;
    System.out.println("Fetching " + absoluteURI);
    webClient.getAbs(absoluteURI)
        .as(BodyCodec.jsonObject())
        .send(ar -> {
        if (ar.succeeded()) {
          JsonObject json = ar.result().body();
          Boolean incomplete = json.getBoolean("incomplete_results", true);
          JsonArray items = json.getJsonArray("items");
          if (items != null) {
            all.addAll(items);
          }
          if (incomplete) {
            fetch(url, page + 1, handler);
          } else {
            handler.handle(Future.succeededFuture());
          }
        } else {
          handler.handle(ar.mapEmpty());
        }
    });
  }

  public String toString() {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (PrintStream out = new PrintStream(buffer)) {
      generate(out);
    }
    return buffer.toString();
  }

  public Generator generate(PrintStream out) {
    Set<String> allLabels = new HashSet<>();
    Map<String, List<JsonObject>> sorted = new HashMap<>();
    Set<String> prev = new HashSet<>();
    l:
    for (Object o : all) {
      JsonObject item = (JsonObject) o;
      JsonArray labels = item.getJsonArray("labels");
      for (Object l : labels) {
        String label = ((JsonObject) l).getString("name");
        if (blackList.contains(label)) {
          continue l;
        }
        allLabels.add(label);
      }
      String url = item.getString("url");
      if (prev.contains(url)) {
        continue;
      }
      prev.add(url);
      String repoURL = item.getString("repository_url").substring("https://api.github.com/repos/".length());
      int slash = repoURL.indexOf('/');
      String org = repoURL.substring(0, slash);
      String repo = repoURL.substring(slash + 1);
      List<JsonObject> l = sorted.computeIfAbsent(repo, b -> new ArrayList<>());
      l.add(item);
    }
    sorted.forEach((repo, items) -> {
      out.println("## " + repo);
      out.println();
      items.forEach(item -> {
        out.println("* [" + item.getString("title") + "](" + item.getString("html_url") + ")");
      });
      out.println();
    });
    return this;
  }

  public void close() {
    vertx.close();
  }
}
