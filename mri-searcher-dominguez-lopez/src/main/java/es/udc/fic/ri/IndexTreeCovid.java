package es.udc.fic.ri;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KeywordField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

record Article(
        @JsonProperty("_id") String id,
        String title,
        String text,

        Metadata metadata
        ){
        record Metadata(String url, @JsonProperty("pubmed_id") String pubmedId) {}
}
public class IndexTreeCovid {
    public static void main(String[] args) {
        String usage =
                """
                        java es.udc.fic.ri.IndexTrecCovid [-openmode OPEN_MODE] [-index INDEX_PATH] [-docs DOCS_PATH] [-indexingmodel INDEXING_MODEL VALUE]

                        This index the docs in DOCS_PATH in INDEX_PATH
                        """;
        OpenMode openmode = OpenMode.CREATE_OR_APPEND;
        String indexPath = null;
        String docsPath = null;
        Similarity indexingmodel = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-openmode" -> openmode = setOpenMode(args[++i]);
                case "-index" -> indexPath = args[++i];
                case "-docs" -> docsPath = args[++i];
                case "-indexingmodel" -> indexingmodel = setIndexingModel(args[++i], Float.parseFloat(args[++i]));
                default -> throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }
        if (indexPath == null || docsPath == null || indexingmodel == null) {
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        try {
            Directory dir = FSDirectory.open(Paths.get(indexPath));
            IndexWriterConfig iwc = new IndexWriterConfig(new StandardAnalyzer());
            iwc.setSimilarity(indexingmodel);
            iwc.setOpenMode(openmode);
            try (IndexWriter writer = new IndexWriter(dir, iwc)) {
                var is = new FileInputStream(docsPath);
                ObjectReader reader = JsonMapper.builder().findAndAddModules().build()
                        .readerFor(Article.class);
                final List<Article> articles;
                try {
                    articles = reader.<Article>readValues(is).readAll();
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
                for(Article article : articles) {
                    indexArchive(writer, article);
                }
            }
        } catch (Exception e) {
            System.out.println("Error indexing files");
            e.printStackTrace();
        }
    }

    static OpenMode setOpenMode(String openmode) {
        return switch (openmode) {
            case "create" -> OpenMode.CREATE;
            case "append" -> OpenMode.APPEND;
            case "create_or_append" -> OpenMode.CREATE_OR_APPEND;
            default -> throw new IllegalArgumentException("Unknown openmode " + openmode);
        };
    }

    static Similarity setIndexingModel(String indexingmodel, float indexingmodelValue) {
        if (Objects.equals(indexingmodel, "bm25")) {
            return new BM25Similarity(indexingmodelValue, 0.75f);
        } else if (Objects.equals(indexingmodel, "jm")) {
            return new LMJelinekMercerSimilarity(indexingmodelValue);
        }
        throw new IllegalArgumentException("Unknown indexingmodel " + indexingmodel);
    }

    static void indexArchive(IndexWriter writer, Article article) {
        Document doc = new Document();
        doc.add(new KeywordField("id", article.id(), Field.Store.YES));
        doc.add(new TextField("title", article.title(), Field.Store.YES));
        doc.add(new TextField("text", article.text(), Field.Store.YES));
        doc.add(new KeywordField("url", article.metadata().url(), Field.Store.YES));
        doc.add(new KeywordField("pubmedId", article.metadata().pubmedId(), Field.Store.YES));
        if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
            try {
                writer.addDocument(doc);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (writer.getConfig().getOpenMode() == OpenMode.CREATE_OR_APPEND) {
            try {
                writer.updateDocument(new Term("id", article.id()), doc);
            }
            catch(IOException e){
            e.printStackTrace();
            }
        }
        else if(writer.getConfig().getOpenMode() == OpenMode.APPEND){
            try {
                writer.updateDocument(new Term("id", article.id()), doc);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}