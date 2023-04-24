package lucyna.searcher.formatter;

import org.apache.lucene.search.highlight.Formatter;
import org.apache.lucene.search.highlight.TokenGroup;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

public class MyFormatter implements Formatter {

    private AttributedStyle preTag;
    private AttributedStyle postTag;
    private boolean formatting;

    public MyFormatter(boolean formatting) {
        int color = AttributedStyle.RED;
        preTag = AttributedStyle.DEFAULT.foreground(color);
        postTag = AttributedStyle.DEFAULT.foregroundOff();
        this.formatting = formatting;
    }

    @Override
    public String highlightTerm(String text, TokenGroup tokenGroup) {
        if(tokenGroup.getTotalScore() <= 0 || !formatting) {
            return text;
        }

        String returnText = new AttributedStringBuilder()
            .style(preTag)
            .append(text)
            .style(postTag)
            .toAnsi();
        
        return returnText;
    }
}