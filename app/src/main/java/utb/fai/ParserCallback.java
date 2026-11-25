
package utb.fai;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ParserCallback {

    URI pageURI;

    int depth;
    int maxDepth;

    ConcurrentHashMap<URI, Boolean> visitedURIs;

    ConcurrentHashMap<String, LongAdder> wordCounts;

    private static final Pattern WORD_PATTERN =
            Pattern.compile("[\\p{L}\\p{N}]+");

    ParserCallback(
            ConcurrentHashMap<URI, Boolean> visitedURIs,
            ConcurrentHashMap<String, LongAdder> wordCounts,
            URIinfo info,
            int maxDepth
    ) {
        this.visitedURIs = visitedURIs;
        this.wordCounts = wordCounts;
        this.pageURI = info.uri;
        this.depth = info.depth;
        this.maxDepth = maxDepth;
    }

    public void analyzePage() {

        try {
            if (App.debugLevel > 0) {
                System.err.println("Analyzing " + pageURI);
            }

            Document document = Jsoup.connect(pageURI.toString())
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .maxBodySize(2 * 1024 * 1024)
                    .followRedirects(true)
                    .get();

            document.select(
                    "script, style, noscript, iframe, svg, canvas, " +
                    "template, head"
            ).remove();

            String text = document.body() != null
                    ? document.body().text()
                    : document.text();

            handleText(text);

            if (depth < maxDepth) {
                findLinks(document);
            }

        } catch (Exception e) {
            if (App.debugLevel > 0) {
                System.err.println(
                        "Error loading page: " + pageURI +
                        " - " + e.getMessage()
                );
            }
        }
    }

    private void findLinks(Document document) {

        for (Element element : document.select("a[href], frame[src], iframe[src]")) {

            String link;

            if (element.hasAttr("href")) {
                link = element.absUrl("href");
            } else {
                link = element.absUrl("src");
            }

            if (link.isEmpty()) {
                continue;
            }

            try {
                URI uri = pageURI.resolve(link).normalize();

                String scheme = uri.getScheme();

                if (scheme == null ||
                        (!scheme.equalsIgnoreCase("http")
                                && !scheme.equalsIgnoreCase("https"))) {
                    continue;
                }

                uri = new URI(
                        scheme.toLowerCase(),
                        uri.getUserInfo(),
                        uri.getHost() != null
                                ? uri.getHost().toLowerCase()
                                : null,
                        uri.getPort(),
                        uri.getPath(),
                        uri.getQuery(),
                        null
                );

                if (visitedURIs.putIfAbsent(uri, true) == null) {

                    URIinfo newInfo = new URIinfo(uri, depth + 1);

                    App.submitPage(newInfo);

                    if (App.debugLevel > 0) {
                        System.err.println("Adding URI: " + uri);
                    }
                }

            } catch (Exception e) {
                if (App.debugLevel > 1) {
                    System.err.println("Invalid URI: " + link);
                }
            }
        }
    }

    public void handleText(String text) {

        Matcher matcher = WORD_PATTERN.matcher(text.toLowerCase());

        while (matcher.find()) {

            String word = matcher.group();

            wordCounts.computeIfAbsent(
                    word,
                    key -> new LongAdder()
            ).increment();
        }
    }
}
