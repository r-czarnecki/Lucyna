package lucyna.searcher.main;

import java.io.IOException;

import org.apache.lucene.queryparser.classic.ParseException;
import org.jline.builtins.Completers;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lucyna.searcher.searcher.Searcher;
import lucyna.searcher.searcher.SearcherConfig;

public class Main {
    private static Logger logger = LoggerFactory.getLogger(Main.class);
    public static void main(String[] args) {
        try(Terminal terminal = TerminalBuilder.builder()
            .jna(false)
            .jansi(true)
            .build()) {
            SearcherConfig config = new SearcherConfig();
            Searcher searcher = new Searcher(config);
            LineReader lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new Completers.FileNameCompleter())
                .build();

            while(true) {
                String line = null;
                try {
                    line = lineReader.readLine("> ");
                    if(line.length() == 0)
                        continue;
                    
                    if(line.charAt(0) == '%') {
                        String[] arg = line.split(" ");
                        config.changeOption(arg);
                    }
                    else {
                        try {
                            searcher.search(line);

                            terminal.writer().println(new AttributedStringBuilder()
                                .append("Files count: ")
                                .style(AttributedStyle.DEFAULT.bold())
                                .append(Integer.toString(searcher.nrOfResults()))
                                .toAnsi());
                            searcher.writeResults(terminal);
                        } catch(IOException | ParseException e) {
                            System.err.println("Parsing error.");
                        }
                    }
                } catch (UserInterruptException e) {
					break;
				} catch (EndOfFileException e) {
					break;
				}
            }

        } catch(IOException exit) {
            System.err.println("An error in input function");
            System.exit(1);
        }
    }
}