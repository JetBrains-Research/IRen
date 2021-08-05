package tools.graphVarMiner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.diagnostic.Logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

public class ChunkWriter<T> {
    private final String pathPrefix;
    private int currentChunkIdx = 0;
    private final int maxChunkSize;
    private final List<T> unwrittenElements = new ArrayList<>();
    private final Logger log = Logger.getInstance(ChunkWriter.class);

    public ChunkWriter(String pathPrefix, int maxChunkSize) {
        this.pathPrefix = pathPrefix;
        this.maxChunkSize = maxChunkSize;
    }

    public void add(T element) {
        unwrittenElements.add(element);
        if (unwrittenElements.size() >= maxChunkSize) {
            try {
                writeChunk();
            } catch (IOException e) {
                throw new Error("Cannot write to output chunk file: " + e);
            }
        }
    }

    private void writeChunk() throws IOException {
        log.info(String.format("Writing chunk number %d...", currentChunkIdx));
        FileOutputStream output = new FileOutputStream(pathPrefix + '.' + currentChunkIdx + ".json.gz");
        Gson gson = new GsonBuilder().create();
        try {
            Writer writer = new OutputStreamWriter(new GZIPOutputStream(output), "UTF-8");
            writer.write(gson.toJson(unwrittenElements));
            writer.close();
        } finally {
            output.close();
        }

        currentChunkIdx++;
        unwrittenElements.clear();
    }

    public void close() {
        try {
            writeChunk();
        } catch (IOException e) {
            throw new Error("Cannot write to output chunk file: " + e);
        }
    }
}