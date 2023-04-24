package lucyna.indexer.watcher;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import static java.nio.file.StandardWatchEventKinds.*;
import static java.nio.file.LinkOption.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lucyna.indexer.indexer.Indexer;

public class Watcher {
    private Indexer indexer;
    private WatchService watcher;
    private Map<WatchKey, Path> keys;

    public Watcher(Indexer indexer) {
        this.indexer = indexer;
        this.keys = new HashMap<>();
        try {
            watcher = FileSystems.getDefault().newWatchService();
        } catch(IOException exit) {
            System.err.println("Cannot create a WatchService");
            System.exit(1);
        }


    }

    public void startLoop() {
        registerAll();
        while(true) {

            WatchKey key;
            try {
                key = watcher.take();
            } catch(InterruptedException exit) {
                return;
            }

            Path dir = keys.get(key);
            for(WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);

                if(kind == ENTRY_CREATE) {
                    if(Files.isRegularFile(child, NOFOLLOW_LINKS)) {
                        indexer.indexDocument(child);
                    }

                    if(Files.isDirectory(child, NOFOLLOW_LINKS)) {
                        indexer.addDir(child.toString(), false);
                        registerDir(child.toString());
                    }
                }

                if(kind == ENTRY_DELETE) {
                    indexer.removeDir(child.toString(), false);
                }

                if(kind == ENTRY_MODIFY) {
                    if(Files.isRegularFile(child, NOFOLLOW_LINKS)) {
                        indexer.unindexDocument(child);
                        indexer.indexDocument(child);
                    }

                    if(Files.isDirectory(child, NOFOLLOW_LINKS)) {
                        indexer.removeDir(child.toString(), false);
                        indexer.addDir(child.toString(), false);
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    keys.remove(key);
    
                    if (keys.isEmpty()) {
                        break;
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }

    private void registerDir(String str) {
        try {
            Path path = Paths.get(str);
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    register(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch(IOException ignore) {
            System.err.println("Cannot watch over the " + str + " directory.");
        }
    }

    private void registerAll() {
        List<String> dirs = indexer.index().getDirs();
        if(dirs == null) {
            System.err.println("No directories to watch.");
            System.exit(0);
        }

        for(String str : dirs) {
            registerDir(str);
        }
    }

    private void register(Path path) throws IOException {
        WatchKey key = path.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        keys.put(key, path);
    }
}