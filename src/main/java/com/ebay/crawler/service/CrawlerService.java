package com.ebay.crawler.service;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class CrawlerService {
    private Set<String> visited;
    private int maxDepth;
    private int limitPerLevel;
    private List<CompletableFuture> tasks;

    public CrawlerService() {
        visited = new HashSet<>();
        tasks = new ArrayList<>();
    }

    /**
     * Crawls the page and subpages
     * @param data contains the config for the crawling operation
     * @return a recursive list of pages with status codes
     */
    public Page crawl(CrawlerData data) {
        if (data.getDepth() < 1) throw new IllegalArgumentException("Depth should be a positive number");
        if (data.getLimitPerLevel() < 1) throw new IllegalArgumentException("Limit per level should be a positive number");
        if (data.getUrl() == null) throw new IllegalArgumentException("Null url");

        this.maxDepth = data.getDepth();
        this.limitPerLevel = data.getLimitPerLevel();
        visited.add(data.getUrl());
        Page page = new Page(data.getUrl(), 1);
        crawl(page);

        // wait for tasks to complete
        try{
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[tasks.size()])).join();
        } catch (Exception e){
            System.out.println(e);
        }

        return page;
    }

    private void crawl(Page page){
        try {
            System.out.println("Connecting to " + page.getUrl());
            Connection.Response response = Jsoup.connect(page.getUrl()).timeout(3000).execute();
            int code = response.statusCode();
            visited.add(page.getUrl());
            page.setCode(code);
            if (code != 200) return;

            Document document = response.parse();
            Elements pageUrls = document.select("a[href]");

            if (page.getDepth() >= maxDepth){
                System.out.println("Reached max depth: " + maxDepth);
                return;
            }

            int limit = 0;
            for (Element element : pageUrls) {
                String url = element.attr("abs:href");
                if (url == null || url.isEmpty()) continue;
                if (visited.contains(url)) {
                    System.out.println("Duplicate url: " + element.attr("abs:href"));
                    continue;
                }

                if (limit >= limitPerLevel){
                    System.out.println("Reached limit per level: " + limit);
                    break;
                }

                CompletableFuture task = CompletableFuture.runAsync(() -> {
                    Page subPage = new Page(url, page.getDepth() + 1);
                    page.addSubPage(subPage);
                    crawl(subPage);
                });

                tasks.add(task);

                limit++;
            }
        } catch (IOException e) {
            System.err.println("URL err '" + page.getUrl() + "': " + e.getMessage());
        }
    }
}


