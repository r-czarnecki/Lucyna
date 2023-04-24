package lucyna.indexer.indexer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.tika.exception.TikaException;
import org.xml.sax.SAXException;

import lucyna.indexer.extractor.Extractor;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.pl.PolishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

public class Index {
    private Map<String, Analyzer> analyzerMap;
    private Directory dir;
    private PerFieldAnalyzerWrapper wrapper;
    private Extractor extractor;

    public Index(Path indexPath) {
        try {
            extractor = new Extractor();
            dir = FSDirectory.open(indexPath);
            analyzerMap = new HashMap<>();
            analyzerMap.put("title_en", new EnglishAnalyzer());
            analyzerMap.put("body_en", new EnglishAnalyzer());
            analyzerMap.put("title_pl", new PolishAnalyzer());
            analyzerMap.put("body_pl", new PolishAnalyzer());
            analyzerMap.put("type", new KeywordAnalyzer());
            analyzerMap.put("path", new KeywordAnalyzer());
            wrapper = new PerFieldAnalyzerWrapper(new StandardAnalyzer(), analyzerMap);
        } catch(IOException exit) {
            System.err.println("Unable to create IndexWriter");
            System.exit(1);
        }
    }

    public void indexDocument(Path path) throws IOException {
        Document document = new Document();
        File file = path.toFile();
        String strPath = file.getCanonicalPath();

        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(wrapper);
        IndexWriter writer = new IndexWriter(dir, indexWriterConfig);

        if(file.isFile()) {
            try {
                String fileName = path.getFileName().toString();
                String text = extractor.parse(strPath);
                String textLanguage = extractor.getLanguage(text);
                String titleLanguage = extractor.getLanguage(fileName);
                
                document.add(new TextField("title_" + titleLanguage, fileName, Field.Store.YES));
                document.add(new TextField("body_" + textLanguage, text, Field.Store.YES));
                document.add(new TextField("everything", fileName + text, Field.Store.YES));
                document.add(new TextField("path", strPath, Field.Store.YES));
                document.add(new TextField("type", "file", Field.Store.YES));
                writer.addDocument(document);
            } catch(SAXException|TikaException|IOException ignore) {
                System.err.println("Unable to index " + strPath); 
            }
        }
        if(file.isDirectory()) {
            document.add(new TextField("path", strPath, Field.Store.YES));
            document.add(new TextField("type", "directory", Field.Store.YES));
            writer.addDocument(document);
        }

        writer.close();
    }

    public void unindexDocument(Path path) throws IOException {
        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(wrapper);
        IndexWriter writer = new IndexWriter(dir, indexWriterConfig);
        String strPath = path.toFile().getCanonicalPath();
        Term term = new Term("path", strPath);
        
        writer.deleteDocuments(term);
        writer.close();
    }

    public void unindexAll() {
        try {
            IndexWriterConfig indexWriterConfig = new IndexWriterConfig(wrapper);
            IndexWriter writer = new IndexWriter(dir, indexWriterConfig);
            
            writer.deleteAll();
            writer.close();
        } catch(IOException exit) {
            System.err.println("Cannot modify index.");
            System.exit(1);
        }
    }

    public List<String> getDirs() {
        List<String> dirs = null;

        try {
            dirs = getAllDirs();
        } catch(IOException exit) {
            System.err.println("Cannot read index.");
            System.exit(1);
        }

        return dirs;
    }

    public void removeAllSubdirs(String parent) {
        try {
            String str = Paths.get(parent).toFile().getCanonicalPath();
            Term term = new Term("path", str);
            Query query = new PrefixQuery(term);
            IndexWriterConfig indexWriterConfig = new IndexWriterConfig(wrapper);
            IndexWriter writer = new IndexWriter(dir, indexWriterConfig);
            
            writer.deleteDocuments(query);
            writer.close();

        } catch(IOException exit) {
            System.err.println("Cannot unindex all subdirectories");
            System.exit(1);
        }
    }

    private List<String> getAllDirs() throws IOException {
        Term term = new Term("type", "directory");
        Query query = new TermQuery(term);
        if(!DirectoryReader.indexExists(dir))
            return null;
        IndexReader indexReader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(indexReader);
        TopDocs topDocs = searcher.search(query, Integer.MAX_VALUE);
        List<String> result = new ArrayList<>();
        for(ScoreDoc scoreDoc : topDocs.scoreDocs) {
            Document doc = searcher.doc(scoreDoc.doc);
            result.add(doc.get("path"));
        }

        indexReader.close();
        return result;
    }
}