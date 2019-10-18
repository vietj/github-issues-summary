
public class Main {
  public static void main(String[] args) throws Exception {
    new Generator()
        .blackList("wontfix", "question", "duplicate", "incomplete", "invalid", "Duplicate")
        .fetch("https://api.github.com/search/issues?q=is:closed+is:issue+user:vert-x3+milestone:3.8.3")
        .fetch("https://api.github.com/search/issues?q=is:closed+is:issue+user:eclipse-vertx+milestone:3.8.3")
        .generate(System.out)
        .close();
    }
}
