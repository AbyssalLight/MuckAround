package net.minecraft.commands;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.IRegistryCustom;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.World;

public interface ICompletionProvider {

    Collection<String> getOnlinePlayerNames();

    default Collection<String> getSelectedEntities() {
        return Collections.emptyList();
    }

    Collection<String> getAllTeams();

    Collection<MinecraftKey> getAvailableSoundEvents();

    Stream<MinecraftKey> getRecipeNames();

    CompletableFuture<Suggestions> customSuggestion(CommandContext<ICompletionProvider> commandcontext, SuggestionsBuilder suggestionsbuilder);

    default Collection<ICompletionProvider.a> getRelevantCoordinates() {
        return Collections.singleton(ICompletionProvider.a.DEFAULT_GLOBAL);
    }

    default Collection<ICompletionProvider.a> getAbsoluteCoordinates() {
        return Collections.singleton(ICompletionProvider.a.DEFAULT_GLOBAL);
    }

    Set<ResourceKey<World>> levels();

    IRegistryCustom registryAccess();

    boolean hasPermission(int i);

    static <T> void filterResources(Iterable<T> iterable, String s, Function<T, MinecraftKey> function, Consumer<T> consumer) {
        boolean flag = s.indexOf(58) > -1;
        Iterator iterator = iterable.iterator();

        while (iterator.hasNext()) {
            T t0 = iterator.next();
            MinecraftKey minecraftkey = (MinecraftKey) function.apply(t0);

            if (flag) {
                String s1 = minecraftkey.toString();

                if (matchesSubStr(s, s1)) {
                    consumer.accept(t0);
                }
            } else if (matchesSubStr(s, minecraftkey.getNamespace()) || minecraftkey.getNamespace().equals("minecraft") && matchesSubStr(s, minecraftkey.getPath())) {
                consumer.accept(t0);
            }
        }

    }

    static <T> void filterResources(Iterable<T> iterable, String s, String s1, Function<T, MinecraftKey> function, Consumer<T> consumer) {
        if (s.isEmpty()) {
            iterable.forEach(consumer);
        } else {
            String s2 = Strings.commonPrefix(s, s1);

            if (!s2.isEmpty()) {
                String s3 = s.substring(s2.length());

                filterResources(iterable, s3, function, consumer);
            }
        }

    }

    static CompletableFuture<Suggestions> suggestResource(Iterable<MinecraftKey> iterable, SuggestionsBuilder suggestionsbuilder, String s) {
        String s1 = suggestionsbuilder.getRemaining().toLowerCase(Locale.ROOT);

        filterResources(iterable, s1, s, (minecraftkey) -> {
            return minecraftkey;
        }, (minecraftkey) -> {
            suggestionsbuilder.suggest(s + minecraftkey);
        });
        return suggestionsbuilder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggestResource(Iterable<MinecraftKey> iterable, SuggestionsBuilder suggestionsbuilder) {
        String s = suggestionsbuilder.getRemaining().toLowerCase(Locale.ROOT);

        filterResources(iterable, s, (minecraftkey) -> {
            return minecraftkey;
        }, (minecraftkey) -> {
            suggestionsbuilder.suggest(minecraftkey.toString());
        });
        return suggestionsbuilder.buildFuture();
    }

    static <T> CompletableFuture<Suggestions> suggestResource(Iterable<T> iterable, SuggestionsBuilder suggestionsbuilder, Function<T, MinecraftKey> function, Function<T, Message> function1) {
        String s = suggestionsbuilder.getRemaining().toLowerCase(Locale.ROOT);

        filterResources(iterable, s, function, (object) -> {
            suggestionsbuilder.suggest(((MinecraftKey) function.apply(object)).toString(), (Message) function1.apply(object));
        });
        return suggestionsbuilder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggestResource(Stream<MinecraftKey> stream, SuggestionsBuilder suggestionsbuilder) {
        Objects.requireNonNull(stream);
        return suggestResource(stream::iterator, suggestionsbuilder);
    }

    static <T> CompletableFuture<Suggestions> suggestResource(Stream<T> stream, SuggestionsBuilder suggestionsbuilder, Function<T, MinecraftKey> function, Function<T, Message> function1) {
        Objects.requireNonNull(stream);
        return suggestResource(stream::iterator, suggestionsbuilder, function, function1);
    }

    static CompletableFuture<Suggestions> suggestCoordinates(String s, Collection<ICompletionProvider.a> collection, SuggestionsBuilder suggestionsbuilder, Predicate<String> predicate) {
        List<String> list = Lists.newArrayList();

        if (Strings.isNullOrEmpty(s)) {
            Iterator iterator = collection.iterator();

            while (iterator.hasNext()) {
                ICompletionProvider.a icompletionprovider_a = (ICompletionProvider.a) iterator.next();
                String s1 = icompletionprovider_a.x + " " + icompletionprovider_a.y + " " + icompletionprovider_a.z;

                if (predicate.test(s1)) {
                    list.add(icompletionprovider_a.x);
                    list.add(icompletionprovider_a.x + " " + icompletionprovider_a.y);
                    list.add(s1);
                }
            }
        } else {
            String[] astring = s.split(" ");
            Iterator iterator1;
            ICompletionProvider.a icompletionprovider_a1;
            String s2;

            if (astring.length == 1) {
                iterator1 = collection.iterator();

                while (iterator1.hasNext()) {
                    icompletionprovider_a1 = (ICompletionProvider.a) iterator1.next();
                    s2 = astring[0] + " " + icompletionprovider_a1.y + " " + icompletionprovider_a1.z;
                    if (predicate.test(s2)) {
                        list.add(astring[0] + " " + icompletionprovider_a1.y);
                        list.add(s2);
                    }
                }
            } else if (astring.length == 2) {
                iterator1 = collection.iterator();

                while (iterator1.hasNext()) {
                    icompletionprovider_a1 = (ICompletionProvider.a) iterator1.next();
                    s2 = astring[0] + " " + astring[1] + " " + icompletionprovider_a1.z;
                    if (predicate.test(s2)) {
                        list.add(s2);
                    }
                }
            }
        }

        return suggest((Iterable) list, suggestionsbuilder);
    }

    static CompletableFuture<Suggestions> suggest2DCoordinates(String s, Collection<ICompletionProvider.a> collection, SuggestionsBuilder suggestionsbuilder, Predicate<String> predicate) {
        List<String> list = Lists.newArrayList();

        if (Strings.isNullOrEmpty(s)) {
            Iterator iterator = collection.iterator();

            while (iterator.hasNext()) {
                ICompletionProvider.a icompletionprovider_a = (ICompletionProvider.a) iterator.next();
                String s1 = icompletionprovider_a.x + " " + icompletionprovider_a.z;

                if (predicate.test(s1)) {
                    list.add(icompletionprovider_a.x);
                    list.add(s1);
                }
            }
        } else {
            String[] astring = s.split(" ");

            if (astring.length == 1) {
                Iterator iterator1 = collection.iterator();

                while (iterator1.hasNext()) {
                    ICompletionProvider.a icompletionprovider_a1 = (ICompletionProvider.a) iterator1.next();
                    String s2 = astring[0] + " " + icompletionprovider_a1.z;

                    if (predicate.test(s2)) {
                        list.add(s2);
                    }
                }
            }
        }

        return suggest((Iterable) list, suggestionsbuilder);
    }

    static CompletableFuture<Suggestions> suggest(Iterable<String> iterable, SuggestionsBuilder suggestionsbuilder) {
        String s = suggestionsbuilder.getRemaining().toLowerCase(Locale.ROOT);
        Iterator iterator = iterable.iterator();

        while (iterator.hasNext()) {
            String s1 = (String) iterator.next();

            if (matchesSubStr(s, s1.toLowerCase(Locale.ROOT))) {
                suggestionsbuilder.suggest(s1);
            }
        }

        return suggestionsbuilder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggest(Stream<String> stream, SuggestionsBuilder suggestionsbuilder) {
        String s = suggestionsbuilder.getRemaining().toLowerCase(Locale.ROOT);
        Stream stream1 = stream.filter((s1) -> {
            return matchesSubStr(s, s1.toLowerCase(Locale.ROOT));
        });

        Objects.requireNonNull(suggestionsbuilder);
        stream1.forEach(suggestionsbuilder::suggest);
        return suggestionsbuilder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggest(String[] astring, SuggestionsBuilder suggestionsbuilder) {
        String s = suggestionsbuilder.getRemaining().toLowerCase(Locale.ROOT);
        String[] astring1 = astring;
        int i = astring.length;

        for (int j = 0; j < i; ++j) {
            String s1 = astring1[j];

            if (matchesSubStr(s, s1.toLowerCase(Locale.ROOT))) {
                suggestionsbuilder.suggest(s1);
            }
        }

        return suggestionsbuilder.buildFuture();
    }

    static <T> CompletableFuture<Suggestions> suggest(Iterable<T> iterable, SuggestionsBuilder suggestionsbuilder, Function<T, String> function, Function<T, Message> function1) {
        String s = suggestionsbuilder.getRemaining().toLowerCase(Locale.ROOT);
        Iterator iterator = iterable.iterator();

        while (iterator.hasNext()) {
            T t0 = iterator.next();
            String s1 = (String) function.apply(t0);

            if (matchesSubStr(s, s1.toLowerCase(Locale.ROOT))) {
                suggestionsbuilder.suggest(s1, (Message) function1.apply(t0));
            }
        }

        return suggestionsbuilder.buildFuture();
    }

    static boolean matchesSubStr(String s, String s1) {
        for (int i = 0; !s1.startsWith(s, i); ++i) {
            i = s1.indexOf(95, i);
            if (i < 0) {
                return false;
            }
        }

        return true;
    }

    public static class a {

        public static final ICompletionProvider.a DEFAULT_LOCAL = new ICompletionProvider.a("^", "^", "^");
        public static final ICompletionProvider.a DEFAULT_GLOBAL = new ICompletionProvider.a("~", "~", "~");
        public final String x;
        public final String y;
        public final String z;

        public a(String s, String s1, String s2) {
            this.x = s;
            this.y = s1;
            this.z = s2;
        }
    }
}
