package lucyna.searcher.searcher;

import org.apache.lucene.analysis.Analyzer;

public class SearcherConfig {
    public enum SEARCH {
        TERM, PHRASE, FUZZY;
    }

    private String language;
    private boolean details;
    private int limit;
    private boolean color;
    private SEARCH searchMode;

    public SearcherConfig() {
        language = "en";
        details = false;
        limit = 0;
        color = true;
        searchMode = SEARCH.TERM;
    }

    public void changeOption(String[] args) {
        for(int i = 0; i < args.length; i++) {
            if(args[i].isEmpty())
                continue;

            if(args[i].equals("%lang"))
                language = args[i+1];
            else if(args[i].equals("%details")) {
                if(args[i+1].equals("on"))
                    details = true;
                else if(args[i+1].equals("off"))
                    details = false;
            }
            else if(args[i].equals("%limit")) {
                try {
                    limit = Integer.parseInt(args[i+1]);
                    if(limit < 0)
                        throw new NumberFormatException();
                } catch(NumberFormatException e) {
                    System.err.println("Wrong number. Setting limit to 0.");
                    limit = 0;
                }
            }
            else if(args[i].equals("%color")) {
                if(args[i+1].equals("on"))
                    color = true;
                else if(args[i+1].equals("off"))
                    color = false;
            }
            else if(args[i].equals("%term"))
                searchMode = SEARCH.TERM;
            else if(args[i].equals("%phrase"))
                searchMode = SEARCH.PHRASE;
            else if(args[i].equals("%fuzzy"))
                searchMode = SEARCH.FUZZY;
        }
    }

    public String getLanguage() {
        return language;
    }

    public boolean getDetails() {
        return details;
    }

    public int getLimit() {
        return limit;
    }

    public boolean getColor() {
        return color;
    }

    public SEARCH getSearchMode() {
        return searchMode;
    }
}