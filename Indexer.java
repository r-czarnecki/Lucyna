package lucyna.indexer.indexer;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

public class Indexer {
    private Index index;

    public Indexer(Path indexPath) {
        this.index = new Index(indexPath);
    }

    public void addDir(String dir, boolean indexDirectory) {
        Path path = Paths.get(dir);
        try {
            if(indexDirectory) {
                index.indexDocument(path);
            }
        } catch(IOException exit) {
            System.err.println("Cannot index a directory.");
            System.exit(1);
        }
        
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    try {
                        index.indexDocument(file);
                    } catch(IOException ignore) {}

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch(IOException exit) {
            System.err.println("IOException while walking the file tree.");
            System.exit(1);
        }
    }

    public void removeDir(String dir, boolean indexDirectory) {
        index.removeAllSubdirs(dir);
    }

    public void indexDocument(Path path) {
        try {
            index.indexDocument(path);
        } catch(IOException ignore) {
            System.err.println("Cannot index the document.");
        }
    }

    public void unindexDocument(Path path) {
        try {
            index.unindexDocument(path);
        } catch(IOException ignore) {
            System.err.println("Cannot unindex the document.");
        }
    }

    public void reindex() {
        List<String> dirs = index.getDirs();
        if(dirs == null)
            return;
        index.unindexAll();
        for(String str : dirs) {
            addDir(str, true);
        }
    }

    public void list() {
        List<String> dirs = index.getDirs();
        if(dirs == null)
            return;
            
        for(String str : dirs) {
            System.out.println(str);
        }
    }

    public void removeAll() {
        index.unindexAll();
    }

    public Index index() {
        return index;
    }
}