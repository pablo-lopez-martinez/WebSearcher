package es.udc.fic.ri;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import static es.udc.fic.ri.IndexTreeCovid.setIndexingModel;
import static es.udc.fic.ri.SearchEvalTrecCovid.*;
import static java.lang.System.exit;

public class TraningTestTrecCovid {
    public static void main(String[] args) {
        String usage =
                """
                        java es.udc.fic.ri.TrainingTestTrecCovid [-evaljm OPEN_MODE] [-evalbm25 INDEX_PATH] [-cut N] [-metrica METRIC] [-index INDEX_PATH]

                        This index the docs in DOCS_PATH in INDEX_PATH
                        """;

        String indexPath = null;
        String outputFileTraining;
        String outputFileTest;
        String[] interval1;
        String[] interval2;
        int[] queryInterval = {0, 0, 0, 0};
        String model = null;
        String metric = null;
        Metrics metrics;
        float maxLambda=0;
        float maxK1=0;
        double sumMetricValues;
        int cut = 0;
        Map<Map<Integer, String>, Integer> map = parseTsv();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-evaljm" -> {
                    model = "jm";
                    interval1 = args[++i].split("-");
                    interval2 = args[++i].split("-");
                    queryInterval[0] = Integer.parseInt(interval1[0]);
                    queryInterval[1] = Integer.parseInt(interval1[1]);
                    queryInterval[2] = Integer.parseInt(interval2[0]);
                    queryInterval[3] = Integer.parseInt(interval2[1]);
                }
                case "-evalbm25" -> {
                    model = "bm25";
                    interval1 = args[++i].split("-");
                    interval2 = args[++i].split("-");
                    queryInterval[0] = Integer.parseInt(interval1[0]);
                    queryInterval[1] = Integer.parseInt(interval1[1]);
                    queryInterval[2] = Integer.parseInt(interval2[0]);
                    queryInterval[3] = Integer.parseInt(interval2[1]);
                }
                case "-cut" -> cut = Integer.parseInt(args[++i]);
                case "-metrica" -> metric = args[++i];
                case "-index" -> indexPath = args[++i];
                default -> throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }
        if (indexPath == null || metric == null || cut == 0) {
            System.err.println("Usage: " + usage);
            exit(1);
        }

        try{
            Directory dir = FSDirectory.open(Paths.get(indexPath));
            IndexReader reader = DirectoryReader.open(dir);
            IndexSearcher searcher = new IndexSearcher(reader);
            var is = IndexTreeCovid.class.getResourceAsStream("/trec-covid/queries.jsonl");
            ObjectReader objectReader = JsonMapper.builder().findAndAddModules().build()
                    .readerFor(Query.class);
            final List<Query> queryList;
            try {
                queryList = objectReader.<Query>readValues(is).readAll();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            Map<Map<Integer, Float>, Double> metricValues = new HashMap<>();
            List<Double> values = new ArrayList<>();
            List<Query> queryList1 = queryList.stream().filter(query -> ((query.id() >= queryInterval[0]) && (query.id() <= queryInterval[1]))).toList();
            List<Query> queryList2 = queryList.stream().filter(query -> ((query.id() >= queryInterval[2]) && (query.id() <= queryInterval[3]))).toList();
            outputFileTraining = "src/outputs/TREC-COVID." + model + ".training." + queryInterval[0] + "-" + queryInterval[1] + ".test." + queryInterval[2] + "-" + queryInterval[3] + "." + metric.toLowerCase() + cut + ".training.csv";
            outputFileTest =  "src/outputs/TREC-COVID." + model + ".training." + queryInterval[0] + "-" + queryInterval[1] + ".test." + queryInterval[2] + "-" + queryInterval[3] + "." + metric.toLowerCase() + cut + ".test.csv";
            int relevantCount = 0;

            switch (model) {
                case "jm" -> {
                    System.out.print("Resultados entrenamiento:\n\n");
                    try (FileWriter writer = new FileWriter(outputFileTraining, true)) {
                        writer.write("Metrica,Query,Cut,0.001,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1\n");
                        System.out.println("Metrica,Query,Cut,0.001,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1");
                        double mean;
                        double maxMean = 0;

                        for (float lambda = 0.001f; lambda <= 1.02f; lambda += (lambda == 0.001f) ? 0.099f : 0.1f) {
                            sumMetricValues = 0;
                            relevantCount = 0;
                            if (lambda > 1.0) lambda = 1.0f;
                            searcher.setSimilarity(setIndexingModel(model, lambda));
                            for (Query query : queryList1) {
                                metrics = parseQuery(searcher, query, cut, map);
                                double val = calculateMetric(metric, metrics);
                                if (val > 0) {
                                    relevantCount++;
                                    sumMetricValues += val;
                                }
                                metricValues.put(Map.of(query.id(), lambda), val);
                            }
                            mean = sumMetricValues / (relevantCount);
                            values.add(mean);
                            if (mean > maxMean) {
                                maxLambda = lambda;
                                maxMean = mean;
                            }
                        }
                        for (Query query : queryList1) {
                            writer.write(metric + "," + query.id() + "," + cut);
                            System.out.print(metric + "," + query.id() + "," + cut);
                            for (float lambda = 0.001f; lambda <= 1.02f; lambda += (lambda == 0.001f) ? 0.099 : 0.1f) {
                                if (lambda > 1.0) lambda = 1.0f;
                                writer.write("," + metricValues.get(Map.of(query.id(), lambda)));
                                System.out.print("," + metricValues.get(Map.of(query.id(), lambda)));
                            }
                            writer.write("\n");
                            System.out.print("\n");
                        }
                        writer.write(metric + ",-," + cut);
                        System.out.print(metric + ",-," + cut);
                        for (double value : values) {
                            writer.write("," + value);
                            System.out.print("," + value);
                        }
                        System.out.println("\n");
                    } catch (Exception e) {
                        System.out.println("Error searching files");
                        e.printStackTrace();
                    }
                    relevantCount = 0;
                    System.out.print("Resultados test:\n\n");
                    try (FileWriter writer = new FileWriter(outputFileTest, true)) {
                        sumMetricValues = 0;
                        writer.write(maxLambda + "," + metric + "\n");
                        System.out.println(maxLambda + "," + metric);
                        searcher.setSimilarity(setIndexingModel(model, maxLambda));
                        for (Query query : queryList2) {
                            metrics = parseQuery(searcher, query, cut, map);
                            double val = calculateMetric(metric, metrics);
                            if (val > 0) {
                                relevantCount++;
                                sumMetricValues += val;
                            }
                            writer.write(query.id() + "," + val + "\n");
                            System.out.println(query.id() + "," + val);
                        }
                        writer.write("-," + (sumMetricValues / (relevantCount)));
                        System.out.println("-," + (sumMetricValues / (relevantCount)));
                    } catch (Exception e) {
                        System.out.println("Error searching files");
                        e.printStackTrace();
                    }
                }
                case "bm25" -> {
                    System.out.print("Resultados entrenamiento:\n\n");
                    try (FileWriter writer = new FileWriter(outputFileTraining, true)) {
                        writer.write("Metrica,Query,Cut,0.4,0.6,0.8,1.0,1.2,1.4,1.6,1.8,2.0\n");
                        System.out.print("Metrica,Query,Cut,0.4,0.6,0.8,1.0,1.2,1.4,1.6,1.8,2.0\n");
                        double mean;
                        double maxMean = 0;

                        for (float k1 = 0.4f; k1 <= 2.0f + 0.0001f; k1 += 0.2f) {
                            if(k1 > 2.0) k1 = 2.0f;
                            sumMetricValues = 0;
                            relevantCount = 0;
                            searcher.setSimilarity(setIndexingModel(model, k1));
                            for (Query query : queryList1) {
                                metrics = parseQuery(searcher, query, cut, map);
                                double val = calculateMetric(metric, metrics);
                                if (val > 0) {
                                    relevantCount++;
                                    sumMetricValues += val;
                                }
                                metricValues.put(Map.of(query.id(), k1), val);
                            }
                            mean = sumMetricValues / (relevantCount);
                            values.add(mean);
                            if (mean > maxMean) {
                                maxK1 = k1;
                                maxMean = mean;
                            }
                        }
                        for (Query query : queryList1) {
                            writer.write(metric + "," + query.id() + "," + cut);
                            System.out.printf(metric + "," + query.id() + "," + cut);
                            for (float k1 = 0.4f; k1 <= 2.0f + 0.0001f; k1 += 0.2f) {
                                if(k1 > 2.0) k1 = 2.0f;
                                writer.write("," + metricValues.get(Map.of(query.id(), k1)));
                                System.out.printf("," + metricValues.get(Map.of(query.id(), k1)));
                            }
                            writer.write("\n");
                            System.out.print("\n");
                        }
                        writer.write(metric + ",-," + cut);
                        System.out.printf(metric + ",-," + cut);
                        for (double value : values) {
                            writer.write("," + value);
                            System.out.printf("," + value);
                        }
                    } catch (Exception e) {
                        System.out.println("Error searching files");
                        e.printStackTrace();
                    }
                    System.out.print("Resultados test:\n\n");
                    relevantCount = 0;
                    try (FileWriter writer = new FileWriter(outputFileTest, true)) {
                        sumMetricValues = 0;
                        writer.write(maxK1 + "," + metric + "\n");
                        System.out.printf(maxK1 + "," + metric + "\n");
                        searcher.setSimilarity(setIndexingModel(model, maxK1));
                        for (Query query : queryList2) {
                            metrics = parseQuery(searcher, query, cut, map);
                            double val = calculateMetric(metric, metrics);
                            if (val > 0) {
                                relevantCount++;
                                sumMetricValues += val;
                            }
                            writer.write(query.id() + "," + val + "\n");
                            System.out.printf(query.id() + "," + val + "\n");
                        }
                        writer.write("-," + (sumMetricValues / (relevantCount)));
                        System.out.print("-," + (sumMetricValues / (relevantCount)));
                    } catch (Exception e) {
                        System.out.println("Error searching files");
                        e.printStackTrace();
                    }
                }
                default -> throw new IllegalArgumentException("unknown model " + model);
            }


        } catch (Exception e) {
            System.out.println("Error searching files");
            e.printStackTrace();
        }
    }

    static Metrics parseQuery(IndexSearcher searcher, Query query, int cut, Map<Map<Integer, String>, Integer> map) {
        if(cut == 0){
            return new Metrics();
        }
        try {
//            System.out.println("Query: " + query.metadata().query());
            QueryParser parser = new QueryParser("text", new StandardAnalyzer());
            org.apache.lucene.search.Query luceneQuery = parser.parse(query.metadata().query());
//            System.out.println("Parsed query: " + luceneQuery.toString());
            TopDocs topDocs = searcher.search(luceneQuery, cut);
            Metrics metrics = new Metrics();
            int relevantCount = 0, position = 0;
            float averagePrecision = 0;
            boolean rr = false;
            for(ScoreDoc scoreDoc : topDocs.scoreDocs){
                position++;
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                String id = doc.get("id");

                if(map.get(Map.of(query.id(), id)) != null){
                    relevantCount++;
                    averagePrecision += (float)relevantCount/position;
                    if(!rr){
                        metrics.setRR((float)1/position);
                        rr = true;
                    }
                }
            }
            metrics.setPAN((float)relevantCount/topDocs.scoreDocs.length);
            if(relevantCount != 0){
                metrics.setAPAN(averagePrecision/relevantCount);
            }
            metrics.setRECALL((float)relevantCount/findQueryIdOccurrences(map, query.id()));
//            System.out.println(metrics + "\n");
            return metrics;

        } catch (Exception e) {
            System.out.println("Error parsing query");
            e.printStackTrace();
        }
        return new Metrics();
    }
    static double calculateMetric(String metric,Metrics metrics) {
        return switch (metric) {
            case "MRR" -> metrics.getRR();
            case "MAP" -> metrics.getAPAN();
            case "R" -> metrics.getRECALL();
            case "P" -> metrics.getPAN();
            default -> throw new IllegalArgumentException("unknown metric " + metric);
        };
    }
}
