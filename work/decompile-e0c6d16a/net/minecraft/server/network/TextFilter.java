package net.minecraft.server.network;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.SystemUtils;
import net.minecraft.util.ChatDeserializer;
import net.minecraft.util.thread.ThreadedMailbox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TextFilter implements AutoCloseable {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final AtomicInteger WORKER_COUNT = new AtomicInteger(1);
    private static final ThreadFactory THREAD_FACTORY = (runnable) -> {
        Thread thread = new Thread(runnable);

        thread.setName("Chat-Filter-Worker-" + TextFilter.WORKER_COUNT.getAndIncrement());
        return thread;
    };
    private final URL chatEndpoint;
    final URL joinEndpoint;
    final URL leaveEndpoint;
    private final String authKey;
    private final int ruleId;
    private final String serverId;
    private final String roomId;
    final TextFilter.a chatIgnoreStrategy;
    final ExecutorService workerPool;

    private TextFilter(URI uri, String s, int i, String s1, String s2, TextFilter.a textfilter_a, int j) throws MalformedURLException {
        this.authKey = s;
        this.ruleId = i;
        this.serverId = s1;
        this.roomId = s2;
        this.chatIgnoreStrategy = textfilter_a;
        this.chatEndpoint = uri.resolve("/v1/chat").toURL();
        this.joinEndpoint = uri.resolve("/v1/join").toURL();
        this.leaveEndpoint = uri.resolve("/v1/leave").toURL();
        this.workerPool = Executors.newFixedThreadPool(j, TextFilter.THREAD_FACTORY);
    }

    @Nullable
    public static TextFilter createFromConfig(String s) {
        if (Strings.isNullOrEmpty(s)) {
            return null;
        } else {
            try {
                JsonObject jsonobject = ChatDeserializer.parse(s);
                URI uri = new URI(ChatDeserializer.getAsString(jsonobject, "apiServer"));
                String s1 = ChatDeserializer.getAsString(jsonobject, "apiKey");

                if (s1.isEmpty()) {
                    throw new IllegalArgumentException("Missing API key");
                } else {
                    int i = ChatDeserializer.getAsInt(jsonobject, "ruleId", 1);
                    String s2 = ChatDeserializer.getAsString(jsonobject, "serverId", "");
                    String s3 = ChatDeserializer.getAsString(jsonobject, "roomId", "Java:Chat");
                    int j = ChatDeserializer.getAsInt(jsonobject, "hashesToDrop", -1);
                    int k = ChatDeserializer.getAsInt(jsonobject, "maxConcurrentRequests", 7);
                    TextFilter.a textfilter_a = TextFilter.a.select(j);

                    return new TextFilter(uri, Base64.getEncoder().encodeToString(s1.getBytes(StandardCharsets.US_ASCII)), i, s2, s3, textfilter_a, k);
                }
            } catch (Exception exception) {
                TextFilter.LOGGER.warn("Failed to parse chat filter config {}", s, exception);
                return null;
            }
        }
    }

    void processJoinOrLeave(GameProfile gameprofile, URL url, Executor executor) {
        JsonObject jsonobject = new JsonObject();

        jsonobject.addProperty("server", this.serverId);
        jsonobject.addProperty("room", this.roomId);
        jsonobject.addProperty("user_id", gameprofile.getId().toString());
        jsonobject.addProperty("user_display_name", gameprofile.getName());
        executor.execute(() -> {
            try {
                this.processRequest(jsonobject, url);
            } catch (Exception exception) {
                TextFilter.LOGGER.warn("Failed to send join/leave packet to {} for player {}", url, gameprofile, exception);
            }

        });
    }

    CompletableFuture<ITextFilter.a> requestMessageProcessing(GameProfile gameprofile, String s, TextFilter.a textfilter_a, Executor executor) {
        if (s.isEmpty()) {
            return CompletableFuture.completedFuture(ITextFilter.a.EMPTY);
        } else {
            JsonObject jsonobject = new JsonObject();

            jsonobject.addProperty("rule", this.ruleId);
            jsonobject.addProperty("server", this.serverId);
            jsonobject.addProperty("room", this.roomId);
            jsonobject.addProperty("player", gameprofile.getId().toString());
            jsonobject.addProperty("player_display_name", gameprofile.getName());
            jsonobject.addProperty("text", s);
            return CompletableFuture.supplyAsync(() -> {
                try {
                    JsonObject jsonobject1 = this.processRequestResponse(jsonobject, this.chatEndpoint);
                    boolean flag = ChatDeserializer.getAsBoolean(jsonobject1, "response", false);

                    if (flag) {
                        return ITextFilter.a.passThrough(s);
                    } else {
                        String s1 = ChatDeserializer.getAsString(jsonobject1, "hashed", (String) null);

                        if (s1 == null) {
                            return ITextFilter.a.fullyFiltered(s);
                        } else {
                            int i = ChatDeserializer.getAsJsonArray(jsonobject1, "hashes").size();

                            return textfilter_a.shouldIgnore(s1, i) ? ITextFilter.a.fullyFiltered(s) : new ITextFilter.a(s, s1);
                        }
                    }
                } catch (Exception exception) {
                    TextFilter.LOGGER.warn("Failed to validate message '{}'", s, exception);
                    return ITextFilter.a.fullyFiltered(s);
                }
            }, executor);
        }
    }

    public void close() {
        this.workerPool.shutdownNow();
    }

    private void drainStream(InputStream inputstream) throws IOException {
        byte[] abyte = new byte[1024];

        while (inputstream.read(abyte) != -1) {
            ;
        }

    }

    private JsonObject processRequestResponse(JsonObject jsonobject, URL url) throws IOException {
        HttpURLConnection httpurlconnection = this.makeRequest(jsonobject, url);
        InputStream inputstream = httpurlconnection.getInputStream();

        JsonObject jsonobject1;
        label91:
        {
            try {
                if (httpurlconnection.getResponseCode() != 204) {
                    try {
                        jsonobject1 = Streams.parse(new JsonReader(new InputStreamReader(inputstream))).getAsJsonObject();
                        break label91;
                    } finally {
                        this.drainStream(inputstream);
                    }
                }

                jsonobject1 = new JsonObject();
            } catch (Throwable throwable) {
                if (inputstream != null) {
                    try {
                        inputstream.close();
                    } catch (Throwable throwable1) {
                        throwable.addSuppressed(throwable1);
                    }
                }

                throw throwable;
            }

            if (inputstream != null) {
                inputstream.close();
            }

            return jsonobject1;
        }

        if (inputstream != null) {
            inputstream.close();
        }

        return jsonobject1;
    }

    private void processRequest(JsonObject jsonobject, URL url) throws IOException {
        HttpURLConnection httpurlconnection = this.makeRequest(jsonobject, url);
        InputStream inputstream = httpurlconnection.getInputStream();

        try {
            this.drainStream(inputstream);
        } catch (Throwable throwable) {
            if (inputstream != null) {
                try {
                    inputstream.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }
            }

            throw throwable;
        }

        if (inputstream != null) {
            inputstream.close();
        }

    }

    private HttpURLConnection makeRequest(JsonObject jsonobject, URL url) throws IOException {
        HttpURLConnection httpurlconnection = (HttpURLConnection) url.openConnection();

        httpurlconnection.setConnectTimeout(15000);
        httpurlconnection.setReadTimeout(2000);
        httpurlconnection.setUseCaches(false);
        httpurlconnection.setDoOutput(true);
        httpurlconnection.setDoInput(true);
        httpurlconnection.setRequestMethod("POST");
        httpurlconnection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        httpurlconnection.setRequestProperty("Accept", "application/json");
        httpurlconnection.setRequestProperty("Authorization", "Basic " + this.authKey);
        httpurlconnection.setRequestProperty("User-Agent", "Minecraft server" + SharedConstants.getCurrentVersion().getName());
        OutputStreamWriter outputstreamwriter = new OutputStreamWriter(httpurlconnection.getOutputStream(), StandardCharsets.UTF_8);

        try {
            JsonWriter jsonwriter = new JsonWriter(outputstreamwriter);

            try {
                Streams.write(jsonobject, jsonwriter);
            } catch (Throwable throwable) {
                try {
                    jsonwriter.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }

                throw throwable;
            }

            jsonwriter.close();
        } catch (Throwable throwable2) {
            try {
                outputstreamwriter.close();
            } catch (Throwable throwable3) {
                throwable2.addSuppressed(throwable3);
            }

            throw throwable2;
        }

        outputstreamwriter.close();
        int i = httpurlconnection.getResponseCode();

        if (i >= 200 && i < 300) {
            return httpurlconnection;
        } else {
            throw new TextFilter.c(i + " " + httpurlconnection.getResponseMessage());
        }
    }

    public ITextFilter createContext(GameProfile gameprofile) {
        return new TextFilter.b(gameprofile);
    }

    @FunctionalInterface
    public interface a {

        TextFilter.a NEVER_IGNORE = (s, i) -> {
            return false;
        };
        TextFilter.a IGNORE_FULLY_FILTERED = (s, i) -> {
            return s.length() == i;
        };

        static TextFilter.a ignoreOverThreshold(int i) {
            return (s, j) -> {
                return j >= i;
            };
        }

        static TextFilter.a select(int i) {
            switch (i) {
                case -1:
                    return TextFilter.a.NEVER_IGNORE;
                case 0:
                    return TextFilter.a.IGNORE_FULLY_FILTERED;
                default:
                    return ignoreOverThreshold(i);
            }
        }

        boolean shouldIgnore(String s, int i);
    }

    public static class c extends RuntimeException {

        c(String s) {
            super(s);
        }
    }

    private class b implements ITextFilter {

        private final GameProfile profile;
        private final Executor streamExecutor;

        b(GameProfile gameprofile) {
            this.profile = gameprofile;
            ThreadedMailbox<Runnable> threadedmailbox = ThreadedMailbox.create(TextFilter.this.workerPool, "chat stream for " + gameprofile.getName());

            Objects.requireNonNull(threadedmailbox);
            this.streamExecutor = threadedmailbox::tell;
        }

        @Override
        public void join() {
            TextFilter.this.processJoinOrLeave(this.profile, TextFilter.this.joinEndpoint, this.streamExecutor);
        }

        @Override
        public void leave() {
            TextFilter.this.processJoinOrLeave(this.profile, TextFilter.this.leaveEndpoint, this.streamExecutor);
        }

        @Override
        public CompletableFuture<List<ITextFilter.a>> processMessageBundle(List<String> list) {
            List<CompletableFuture<ITextFilter.a>> list1 = (List) list.stream().map((s) -> {
                return TextFilter.this.requestMessageProcessing(this.profile, s, TextFilter.this.chatIgnoreStrategy, this.streamExecutor);
            }).collect(ImmutableList.toImmutableList());

            return SystemUtils.sequenceFailFast(list1).exceptionally((throwable) -> {
                return ImmutableList.of();
            });
        }

        @Override
        public CompletableFuture<ITextFilter.a> processStreamMessage(String s) {
            return TextFilter.this.requestMessageProcessing(this.profile, s, TextFilter.this.chatIgnoreStrategy, this.streamExecutor);
        }
    }
}
