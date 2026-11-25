
package utb.fai;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.LongAdder;

public class App {

    public static ConcurrentHashMap<URI, Boolean> visitedURIs =
            new ConcurrentHashMap<>();

    public static ConcurrentHashMap<String, LongAdder> wordCounts =
            new ConcurrentHashMap<>();

    public static ExecutorService executor;

    public static Phaser phaser;

    public static int maxDepth = 2;
    public static int debugLevel = 0;

    public static void main(String[] args) {

        if (args.length < 1) {
            System.err.println("Missing parameter - start URL");
            return;
        }

        try {
            URI startURI = new URI(args[0]).normalize();

            if (args.length >= 2) {
                maxDepth = Integer.parseInt(args[1]);
            }

            if (args.length >= 3) {
                debugLevel = Integer.parseInt(args[2]);
            }

            if (maxDepth < 0) {
                System.err.println("Depth must be non-negative");
                return;
            }

            if (!startURI.getScheme().equalsIgnoreCase("http")
                    && !startURI.getScheme().equalsIgnoreCase("https")) {
                System.err.println("Only HTTP and HTTPS URLs are supported");
                return;
            }

            executor = Executors.newFixedThreadPool(
                    Runtime.getRuntime().availableProcessors()
            );

            phaser = new Phaser(1);

            visitedURIs.put(startURI, true);

            URIinfo startInfo = new URIinfo(startURI, 0);

            submitPage(startInfo);

            phaser.arriveAndAwaitAdvance();

            executor.shutdown();

            printResults();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());

            if (executor != null) {
                executor.shutdownNow();
            }
        }
    }

    public static void submitPage(URIinfo info) {

        phaser.register();

        executor.submit(() -> {

            try {
                ParserCallback callback = new ParserCallback(
                        visitedURIs,
                        wordCounts,
                        info,
                        maxDepth
                );

                callback.analyzePage();

            } finally {
                phaser.arriveAndDeregister();
            }
        });
    }

    private static void printResults() {

        System.out.println();
        System.out.println("========================================");
        System.out.println("         WEB CRAWLER RESULTS");
        System.out.println("========================================");

        System.out.println("Visited pages: " + visitedURIs.size());

        System.out.println();
        System.out.printf("%-25s %s%n", "WORD", "COUNT");
        System.out.println("----------------------------------------");

        wordCounts.entrySet()
                .stream()
                .sorted((a, b) -> {
                    int result = Long.compare(
                            b.getValue().sum(),
                            a.getValue().sum()
                    );

                    if (result == 0) {
                        return a.getKey().compareTo(b.getKey());
                    }

                    return result;
                })
                .limit(20)
                .forEach(entry ->
                        System.out.printf(
                                "%-25s %d%n",
                                entry.getKey(),
                                entry.getValue().sum()
                        )
                );

        System.out.println("========================================");
    }
}
