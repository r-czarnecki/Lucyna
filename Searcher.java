package lucyna.searcher.searcher;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.pl.PolishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.complexPhrase.ComplexPhraseQueryParser;
import org.apache.lucene.queryparser.flexible.core.builders.QueryBuilder;
import org.apache.lucene.queryparser.simple.SimpleQueryParser;
import org.apache.lucene.queryparser.xml.builders.TermQueryBuilder;
import org.apache.lucene.queryparser.xml.builders.TermsQueryBuilder;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Formatter;
import org.apache.lucene.search.highlight.Fragmenter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleSpanFragmenter;
import org.apache.lucene.search.highlight.TokenSources;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import lucyna.searcher.formatter.MyFormatter;
import lucyna.searcher.searcher.SearcherConfig.SEARCH;

public class Searcher {
    private SearcherConfig config;
    private Path indexPath;
    private Directory dir;
    private TopDocs topDocs;
    private int nrOfResults;
    private Query[] queries;
    private PerFieldAnalyzerWrapper wrapper;

    public Searcher(SearcherConfig config) {
        try {
            this.config = config;
            nrOfResults = 0;
            queries = new Query[2];
            indexPath = Paths.get(System.getProperty("user.home"), ".index");
            dir = FSDirectory.open(indexPath);
            Map<String, Analyzer> analyzerMap = new HashMap<>();
            analyzerMap.put("title_en", new EnglishAnalyzer());
            analyzerMap.put("body_en", new EnglishAnalyzer());
            analyzerMap.put("title_pl", new PolishAnalyzer());
            analyzerMap.put("body_pl", new PolishAnalyzer());
            analyzerMap.put("type", new KeywordAnalyzer());
            analyzerMap.put("path", new KeywordAnalyzer());
            wrapper = new PerFieldAnalyzerWrapper(new StandardAnalyzer(), analyzerMap);
        } catch(IOException exit) {
            System.err.println("Unable to open index.");
            System.exit(1);
        }
    }

    public void search(String text) throws IOException, ParseException {
        String lang = config.getLanguage();
        int nrOfRes = config.getLimit();
        if(nrOfRes == 0)
            nrOfRes = Integer.MAX_VALUE;

        Term titleTerm = new Term("title_" + lang, text);
        Term bodyTerm = new Term("body_" + lang, text);
        Query titleQuery;
        Query bodyQuery;
        if(config.getSearchMode() == SEARCH.TERM) {
            titleQuery = new QueryParser("title_" + lang, wrapper).parse(text);
            bodyQuery = new QueryParser("body_" + lang, wrapper).parse(text);
        }
        else if(config.getSearchMode() == SEARCH.FUZZY) {
            titleQuery = new FuzzyQuery(titleTerm);
            bodyQuery = new FuzzyQuery(bodyTerm);
        }
        else {
            titleQuery = new org.apache.lucene.util.QueryBuilder(wrapper).createPhraseQuery("title_" + lang, text);
            bodyQuery = new org.apache.lucene.util.QueryBuilder(wrapper).createPhraseQuery("body_" + lang, text);
        }

        queries[0] = titleQuery;
        queries[1] = bodyQuery;
        BooleanQuery query = new BooleanQuery.Builder()
            .add(titleQuery, BooleanClause.Occur.SHOULD)
            .add(bodyQuery, BooleanClause.Occur.SHOULD)
            .build();
        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);

        topDocs = searcher.search(query, nrOfRes);
        nrOfResults = topDocs.scoreDocs.length;
        reader.close();
    } 

    public int nrOfResults() {
        return nrOfResults;
    }

    public void writeResults(Terminal terminal) {
        IndexReader reader = null;
        try {
            reader = DirectoryReader.open(dir);
        } catch(IOException exit) {
            System.err.println("Cannot open index.");
            System.exit(1);
        }
        IndexSearcher searcher = new IndexSearcher(reader);
        String lang = config.getLanguage();
        String[] fields = new String[] {"title_" + lang, "body_" + lang};
        Formatter formatter = new MyFormatter(config.getColor());
        QueryScorer[] scorer = new QueryScorer[2];
        Highlighter[] highlighter = new Highlighter[2];
        Fragmenter[] fragmenter = new Fragmenter[2];

        for(int i = 0; i < 2; i++) {
            scorer[i] = new QueryScorer(queries[i]);
            highlighter[i] = new Highlighter(formatter, scorer[i]);
            fragmenter[i] = new SimpleSpanFragmenter(scorer[i], 100);
            highlighter[i].setTextFragmenter(fragmenter[i]);
        }

        for(int i = 0; i < topDocs.scoreDocs.length; i++) {
            try {
                int docId = topDocs.scoreDocs[i].doc;
                Document doc = searcher.doc(docId);
                String path = doc.get("path");
                terminal.writer().println(new AttributedStringBuilder()
                    .style(AttributedStyle.DEFAULT.bold())
                    .append(path)
                    .toAnsi());

                if(!config.getDetails())
                    continue;

                boolean firstContext = true;
                for(int j = 0; j < 2; j++) {
                    try {
                        String text = doc.get(fields[j]);
                        if(text == null)
                            continue;

                        String[] frags = highlighter[j].getBestFragments(wrapper, fields[j], text, 10);
                        for(String frag : frags) {
                            if(!firstContext)
                                terminal.writer().print(" ... ");
                            firstContext = false;
                            terminal.writer().print(frag);
                        }
                    } catch(IOException | InvalidTokenOffsetsException ignore) {
                        System.err.println("Cannot write context.");
                    }
                }
                terminal.writer().println();
            } catch(IOException ignore) {
                System.err.println("Cannot write context.");
            }
        }
    }

    public IndexReader getIndexReader() throws IOException {
        return DirectoryReader.open(dir);
    }
}