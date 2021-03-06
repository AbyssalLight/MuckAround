package net.minecraft.resources;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.IRegistry;

public final class RegistryFileCodec<E> implements Codec<Supplier<E>> {

    private final ResourceKey<? extends IRegistry<E>> registryKey;
    private final Codec<E> elementCodec;
    private final boolean allowInline;

    public static <E> RegistryFileCodec<E> create(ResourceKey<? extends IRegistry<E>> resourcekey, Codec<E> codec) {
        return create(resourcekey, codec, true);
    }

    public static <E> Codec<List<Supplier<E>>> homogeneousList(ResourceKey<? extends IRegistry<E>> resourcekey, Codec<E> codec) {
        return Codec.either(create(resourcekey, codec, false).listOf(), codec.xmap((object) -> {
            return () -> {
                return object;
            };
        }, Supplier::get).listOf()).xmap((either) -> {
            return (List) either.map((list) -> {
                return list;
            }, (list) -> {
                return list;
            });
        }, Either::left);
    }

    private static <E> RegistryFileCodec<E> create(ResourceKey<? extends IRegistry<E>> resourcekey, Codec<E> codec, boolean flag) {
        return new RegistryFileCodec<>(resourcekey, codec, flag);
    }

    private RegistryFileCodec(ResourceKey<? extends IRegistry<E>> resourcekey, Codec<E> codec, boolean flag) {
        this.registryKey = resourcekey;
        this.elementCodec = codec;
        this.allowInline = flag;
    }

    public <T> DataResult<T> encode(Supplier<E> supplier, DynamicOps<T> dynamicops, T t0) {
        return dynamicops instanceof RegistryWriteOps ? ((RegistryWriteOps) dynamicops).encode(supplier.get(), t0, this.registryKey, this.elementCodec) : this.elementCodec.encode(supplier.get(), dynamicops, t0);
    }

    public <T> DataResult<Pair<Supplier<E>, T>> decode(DynamicOps<T> dynamicops, T t0) {
        return dynamicops instanceof RegistryReadOps ? ((RegistryReadOps) dynamicops).decodeElement(t0, this.registryKey, this.elementCodec, this.allowInline) : this.elementCodec.decode(dynamicops, t0).map((pair) -> {
            return pair.mapFirst((object) -> {
                return () -> {
                    return object;
                };
            });
        });
    }

    public String toString() {
        return "RegistryFileCodec[" + this.registryKey + " " + this.elementCodec + "]";
    }
}
