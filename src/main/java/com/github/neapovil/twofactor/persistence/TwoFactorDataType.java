package com.github.neapovil.twofactor.persistence;

import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import com.github.neapovil.twofactor.gson.TwoFactorGson;
import com.google.gson.Gson;

public class TwoFactorDataType implements PersistentDataType<String, TwoFactorGson>
{
    private final Gson gson = new Gson();

    @Override
    public @NotNull Class<String> getPrimitiveType()
    {
        return String.class;
    }

    @Override
    public @NotNull Class<TwoFactorGson> getComplexType()
    {
        return TwoFactorGson.class;
    }

    @Override
    public @NotNull String toPrimitive(@NotNull TwoFactorGson complex, @NotNull PersistentDataAdapterContext context)
    {
        return this.gson.toJson(complex);
    }

    @Override
    public @NotNull TwoFactorGson fromPrimitive(@NotNull String primitive, @NotNull PersistentDataAdapterContext context)
    {
        return this.gson.fromJson(primitive, TwoFactorGson.class);
    }

}
