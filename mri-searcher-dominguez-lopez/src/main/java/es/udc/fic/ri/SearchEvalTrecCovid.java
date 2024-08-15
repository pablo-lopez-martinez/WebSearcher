package es.udc.fic.ri;


import com.fasterxml.jackson.annotation.JsonProperty;
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
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;

import static java.lang.System.exit;


record Query(
        @JsonProperty("_id") int id,
        String text,
        Metadata metadata
){
    record Metadata(String query, String narrative) {}
}

class Metrics {
    private double RR = 0 ,APAN = 0,PAN = 0,RECALL = 0;
    Metrics(){
    }

    public double getRR() {
        return RR;
    }

    public void setRR(double RR) {
        this.RR = RR;
    }

    public double getAPAN() {
        return APAN;
    }

    public void setAPAN(double APAN) {
        this.APAN = APAN;
    }

    public double getPAN() {
        return PAN;
    }

    public void setPAN(double PAN) {
        this.PAN = PAN;
    }

    public double getRECALL() {
        return RECALL;
    }

    public void setRECALL(double RECALL) {
        this.RECALL = RECALL;
    }

    @Override
    public String toString() {
        return "Metrics = {" +
                "RR=" + RR +
                ", AP@N=" + APAN +
                ", P@N=" + PAN +
                ", Recall@N=" + RECALL +
                "}\n";
    }
}
public class SearchEvalTrecCovid {
    public static void main(String[] args) {
        String usage =
                """
                        java es.udc.fic.ri.SearchEvalTrecCovid [-search INDEXING_MODEL VALUE] [-index INDEX_PATH] [-cut N] [-top M] [-queries all | <int1> | <int1-int2>]

                        This searches in INDEX_PATH
                        """;
        String indexPath = null, strIndexModel = "", queriesStr = "all";
        Similarity indexingmodel = null;
        int cut = 0, top = 0;
        double mrrAcc = 0, mapAcc = 0, recallAcc = 0, panAcc = 0;
        float indexModelVal = 0;
        boolean all = true;
        int[] queryInterval = {0,0};
        Map<Map<Integer, String>, Integer> map = parseTsv();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-search" -> {
                    strIndexModel = args[++i];
                    indexModelVal = Float.parseFloat(args[++i]);
                    indexingmodel = setIndexingModel(strIndexModel,indexModelVal);
                }
                case "-index" -> indexPath = args[++i];
                case "-cut" -> cut = Integer.parseInt(args[++i]);
                case "-top" -> top = Integer.parseInt(args[++i]);
                case "-queries" -> {
                    queriesStr = args[++i];
                    if (queriesStr.equals("all")) {
                        break;
                    }
                    all = false;
                    String[] split = queriesStr.split("-");
                    if (split.length == 1) {
                        queryInterval[0] = Integer.parseInt(split[0]);
                    } else if (split.length == 2) {
                        queryInterval[0] = Integer.parseInt(split[0]);
                        queryInterval[1] = Integer.parseInt(split[1]);
                    }
                }
                default -> throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }
        if (indexPath == null || indexingmodel == null || cut == 0 || top == 0) {
            System.err.println("Usage: " + usage);
            exit(1);
        }
        String simModel = indexingmodel instanceof BM25Similarity ? "k1" : "lambda";
        String txtPath = "src/outputs/TREC-COVID." + strIndexModel + "." + top + "." + "hits" + "." + simModel + "." + indexModelVal + ".q" + queriesStr + ".txt";
        String csvPath = "src/outputs/TREC-COVID." + strIndexModel + "." + cut + "." + "cut" + "." + simModel + "." + indexModelVal + ".q" + queriesStr + ".csv";
        try(FileWriter csvWriter = new FileWriter(csvPath, true); FileWriter textWriter = new FileWriter(txtPath, true)) {
            csvWriter.write("Query,RR,AP@N,P@N,Recall@N,Cut\n");
            Directory dir = FSDirectory.open(Paths.get(indexPath));
            IndexReader reader = DirectoryReader.open(dir);
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(indexingmodel);
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
            if (!all) {
                if (queryInterval[1]!=0) {queryList.removeIf(query -> ((query.id()<queryInterval[0]) || (query.id()>queryInterval[1])));}
                else {queryList.removeIf(query -> (query.id()!=queryInterval[0]));}
            }
            int mrrCnt = 0, mapCnt = 0, recallCnt = 0, panCnt = 0;
                for(Query query : queryList) {
                    Metrics metrics = parseQuery(searcher, query, cut, top, map, textWriter);
                    if(metrics.getRR() > 0){
                        mrrAcc += metrics.getRR();
                        mrrCnt++;
                    }
                    if(metrics.getAPAN() > 0){
                        mapAcc += metrics.getAPAN();
                        mapCnt++;
                    }
                    if(metrics.getRECALL() > 0){
                        recallAcc += metrics.getRECALL();
                        recallCnt++;
                    }
                    if(metrics.getPAN() > 0){
                        panAcc += metrics.getPAN();
                        panCnt++;
                    }
                    csvWriter.write(query.id() + "," + metrics.getRR() + "," + metrics.getAPAN() + "," + metrics.getPAN() + "," + metrics.getRECALL() + "," + cut + "\n");
                }
            System.out.println("MRR: " + mrrAcc/mrrCnt);
            System.out.println("M@P: " + mapAcc/mapCnt);
            System.out.println("Recall@N: " + recallAcc/recallCnt);
            System.out.println("P@N: " + panAcc/panCnt);
            textWriter.write("MRR: " + mrrAcc/mrrCnt + "\n");
            textWriter.write("M@P: " + mapAcc/mapCnt + "\n");
            textWriter.write("Recall@N: " + recallAcc/recallCnt + "\n");
            textWriter.write("P@N: " + panAcc/panCnt + "\n");
            csvWriter.write("-,"+mrrAcc/mrrCnt+","+mapAcc/mapCnt+","+panAcc/panCnt+","+recallAcc/recallCnt+","+cut+"\n");
            reader.close();
        } catch (Exception e) {
            System.out.println("Error searching files");
            e.printStackTrace();
        }

    }
    static Similarity setIndexingModel(String indexingmodel, float indexingmodelValue) {
        if (Objects.equals(indexingmodel, "bm25")) {
            return new BM25Similarity(indexingmodelValue, 0.75f);
        } else if (Objects.equals(indexingmodel, "jm")) {
            return new LMJelinekMercerSimilarity(indexingmodelValue);
        }
        throw new IllegalArgumentException("Unknown indexingmodel " + indexingmodel);
    }


    static Metrics parseQuery(IndexSearcher searcher, Query query, int cut, int top, Map<Map<Integer, String>, Integer> map, FileWriter writer) {
        if(cut == 0){
            return new Metrics();
        }
        try {
            writer.write("-----------------------------------------------------------------------\n\n");
            System.out.println("Query: " + query.metadata().query());
            writer.write("Query: " + query.metadata().query() + "\n");
            QueryParser parser = new QueryParser("text", new StandardAnalyzer());
            org.apache.lucene.search.Query luceneQuery = parser.parse(query.metadata().query());
            System.out.println("Parsed query: " + luceneQuery.toString());
            writer.write("Parsed query: " + luceneQuery + "\n\n");
            TopDocs topDocs = searcher.search(luceneQuery, cut);
            Metrics metrics = new Metrics();
            int relevantCount = 0, position = 0;
            double averagePrecision = 0;
            boolean rr = false, isRelevant;
            for(ScoreDoc scoreDoc : topDocs.scoreDocs){
                position++;
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                String id = doc.get("id");

                if(map.get(Map.of(query.id(), id)) != null){
                    relevantCount++;
                    averagePrecision += (double) relevantCount/position;
                    if(!rr){
                        metrics.setRR((double)1/position);
                        rr = true;
                    }
                    isRelevant = true;
                }

                else{
                    isRelevant = false;
                }
                if(position <= top){
                    printOutputs(writer, doc, scoreDoc.score, isRelevant);
                }
            }
            metrics.setPAN((double) relevantCount/topDocs.scoreDocs.length);
            if(relevantCount != 0){
                metrics.setAPAN(averagePrecision/relevantCount);
            }
            metrics.setRECALL((double) relevantCount/findQueryIdOccurrences(map, query.id()));
            System.out.println(metrics + "\n");
            writer.write(metrics + "\n");
            return metrics;

        } catch (Exception e) {
            System.out.println("Error parsing query");
            e.printStackTrace();
        }
        return new Metrics();
    }

    static int findQueryIdOccurrences(Map<Map<Integer, String>, Integer> map, int queryId) {
        int cnt = 0;
        for (Map<Integer, String> key : map.keySet()) {
            for (Integer id : key.keySet()) {
                if (id == queryId) {
                    cnt++;
                }
            }
        }
        return cnt;
    }

    static Map<Map<Integer, String>, Integer> parseTsv() {
        String filePath = "src/main/resources/trec-covid/qrels/test.tsv";
        Map<Map<Integer, String>, Integer> map = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split("\t");
                if (values.length == 3) {
                    if (Integer.parseInt(values[2]) > 0) {
                        map.put(Map.of(Integer.parseInt(values[0]), values[1]), Integer.parseInt(values[2]));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return map;
    }

    static void printOutputs(FileWriter textWriter, Document doc, double score, boolean isRelevant) {
        try {
            System.out.println("Document with id: " + doc.get("id"));
            System.out.println("Title: " + doc.get("title"));
            System.out.println("Text: " + doc.get("text"));
            System.out.println("Url: " + doc.get("url"));
            System.out.println("PubmedId: " + doc.get("pubmedId"));
            System.out.println("Score: " + score);
            textWriter.write("Document with id: " + doc.get("id") + "\n");
            textWriter.write("Title: " + doc.get("title") + "\n");
            textWriter.write("Text: " + doc.get("text") + "\n");
            textWriter.write("Url: " + doc.get("url") + "\n");
            textWriter.write("PubmedId: " + doc.get("pubmedId") + "\n");
            textWriter.write("Score: " + score + "\n");
            if(isRelevant){
                System.out.println("Relevant: Yes");
                textWriter.write("Relevant: Yes\n\n");
            }
            else{
                System.out.println("Relevant: No");
                textWriter.write("Relevant: No\n\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
