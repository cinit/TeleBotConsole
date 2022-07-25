package cc.ioctl.telebot.tdlib.tlrpc;

import com.google.gson.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;

public abstract class TlRpcJsonObject implements Cloneable {

    @NotNull
    public JsonObject toJsonObject() {
        Class<?> clazz = getClass();
        JsonObject result = new JsonObject();
        for (Field f : clazz.getDeclaredFields()) {
            if (!Modifier.isPublic(f.getModifiers())) {
                continue;
            }
            // check annotation
            if (f.isAnnotationPresent(TlRpcField.class)) {
                TlRpcField annotation = f.getAnnotation(TlRpcField.class);
                String fieldName = annotation.value();
                Object value;
                try {
                    value = f.get(this);
                } catch (IllegalAccessException e) {
                    // should not happen
                    throw new AssertionError(e);
                }
                Class<?> type = f.getType();
                if (type.isPrimitive()) {
                    addJsonObjectPrimitive(result, fieldName, type, value);
                } else if (type == String.class) {
                    result.addProperty(fieldName, (String) value);
                } else if (TlRpcJsonObject.class.isAssignableFrom(type)) {
                    result.add(fieldName, ((TlRpcJsonObject) value).toJsonObject());
                } else if (JsonElement.class.isAssignableFrom(type)) {
                    result.add(fieldName, (JsonElement) value);
                } else if (type.isArray()) {
                    if (value == null) {
                        result.add(fieldName, null);
                    } else {
                        result.add(fieldName, serializeJsonArray(type, value));
                    }
                } else {
                    throw new AssertionError("Unknown type: " + type);
                }
            }
        }
        return result;
    }

    protected static void addJsonObjectPrimitive(@NotNull JsonObject owner, @NotNull String name, @NotNull Class<?> type, @NotNull Object value) {
        if (type == int.class) {
            owner.addProperty(name, (int) value);
        } else if (type == long.class) {
            owner.addProperty(name, (long) value);
        } else if (type == boolean.class) {
            owner.addProperty(name, (boolean) value);
        } else if (type == double.class) {
            owner.addProperty(name, (double) value);
        } else if (type == float.class) {
            owner.addProperty(name, (float) value);
        } else if (type == short.class) {
            owner.addProperty(name, (short) value);
        } else if (type == byte.class) {
            owner.addProperty(name, (byte) value);
        } else if (type == char.class) {
            owner.addProperty(name, (char) value);
        } else {
            throw new AssertionError("Unknown primitive type: " + type);
        }
    }

    protected static void addJsonArrayPrimitive(@NotNull JsonArray owner, @NotNull Class<?> type, @NotNull Object value) {
        if (type == int.class) {
            owner.add((int) value);
        } else if (type == long.class) {
            owner.add((long) value);
        } else if (type == boolean.class) {
            owner.add((boolean) value);
        } else if (type == double.class) {
            owner.add((double) value);
        } else if (type == float.class) {
            owner.add((float) value);
        } else if (type == short.class) {
            owner.add((short) value);
        } else if (type == byte.class) {
            owner.add((byte) value);
        } else if (type == char.class) {
            owner.add((char) value);
        } else {
            throw new AssertionError("Unknown primitive type: " + type);
        }
    }

    protected static JsonArray serializeJsonArray(@NotNull Class<?> type, @Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (!type.isArray()) {
            throw new IllegalArgumentException("Not an array: " + type);
        }
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("Not an array of type: " + type);
        }
        Class<?> componentType = type.getComponentType();
        if (componentType.isArray()) {
            JsonArray result = new JsonArray();
            for (Object o : (Object[]) value) {
                result.add(serializeJsonArray(componentType, o));
            }
            return result;
        } else if (componentType.isPrimitive()) {
            JsonArray result = new JsonArray();
            for (Object o : (Object[]) value) {
                addJsonArrayPrimitive(result, componentType, o);
            }
            return result;
        } else if (componentType == String.class) {
            JsonArray result = new JsonArray();
            for (Object o : (Object[]) value) {
                result.add((String) o);
            }
            return result;
        } else if (JsonElement.class.isAssignableFrom(componentType)) {
            JsonArray result = new JsonArray();
            for (Object o : (Object[]) value) {
                result.add((JsonElement) o);
            }
            return result;
        } else if (TlRpcJsonObject.class.isAssignableFrom(componentType)) {
            JsonArray result = new JsonArray();
            for (Object o : (Object[]) value) {
                result.add(((TlRpcJsonObject) o).toJsonObject());
            }
            return result;
        } else {
            throw new AssertionError("Unknown type: " + componentType);
        }
    }

    @NotNull
    public String toJsonString(@Nullable String extra) {
        JsonObject result = toJsonObject();
        if (extra != null) {
            result.addProperty("@extra", extra);
        }
        return result.toString();
    }

    public static class TlRpcObjDecodeResult {
        TlRpcJsonObject object;
        int clientId;
        @Nullable
        String extra;
        @Nullable
        String type;
    }

    public static <T extends TlRpcJsonObject> T fromJsonObject(@NotNull Class<T> clazz, @NotNull JsonObject jsonObject) throws ReflectiveOperationException {
        T result = clazz.getConstructor().newInstance();
        for (Field f : clazz.getDeclaredFields()) {
            if (!Modifier.isPublic(f.getModifiers()) || Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            // check annotation
            if (f.isAnnotationPresent(TlRpcField.class)) {
                TlRpcField annotation = f.getAnnotation(TlRpcField.class);
                String fieldName = annotation.value();
                if (jsonObject.has(fieldName)) {
                    JsonElement value = jsonObject.get(fieldName);
                    Class<?> type = f.getType();
                    try {
                        if (type.isPrimitive()) {
                            if (type == int.class) {
                                f.setInt(result, value.getAsInt());
                            } else if (type == long.class) {
                                f.setLong(result, value.getAsLong());
                            } else if (type == boolean.class) {
                                f.setBoolean(result, value.getAsBoolean());
                            } else if (type == double.class) {
                                f.setDouble(result, value.getAsDouble());
                            } else if (type == float.class) {
                                f.setFloat(result, value.getAsFloat());
                            } else if (type == short.class) {
                                f.setShort(result, value.getAsShort());
                            } else if (type == byte.class) {
                                f.setByte(result, value.getAsByte());
                            } else if (type == char.class) {
                                f.setChar(result, (char) value.getAsInt());
                            } else {
                                throw new AssertionError("Unknown primitive type: " + type);
                            }
                        } else if (type == String.class) {
                            f.set(result, value.getAsString());
                        } else if (type == JsonObject.class) {
                            f.set(result, value);
                        } else if (TlRpcJsonObject.class.isAssignableFrom(type)) {
                            f.set(result, fromJsonObject((Class<? extends TlRpcJsonObject>) type, (JsonObject) value));
                        } else {
                            throw new AssertionError("Unknown type: " + type);
                        }
                    } catch (IllegalAccessException e) {
                        // should not happen
                        throw new AssertionError(e);
                    }
                } else {
                    if (!annotation.optional()) {
                        throw new IllegalArgumentException("Missing required field: " + fieldName);
                    }
                }
            }
        }
        return result;
    }

    public static TlRpcObjDecodeResult fromJsonObjectEx(@NotNull Class<? extends TlRpcJsonObject> clazz, @NotNull JsonObject json) throws ReflectiveOperationException {
        TlRpcJsonObject obj = fromJsonObject(clazz, json);
        TlRpcObjDecodeResult result = new TlRpcObjDecodeResult();
        result.object = obj;
        result.clientId = getClientId(json);
        result.extra = getExtra(json);
        result.type = getType(json);
        return result;
    }

    public static int getClientId(@NotNull String json) {
        JsonObject obj = (JsonObject) JsonParser.parseString(json);
        if (obj.has("@client_id")) {
            return obj.get("@client_id").getAsInt();
        } else {
            return -1;
        }
    }

    @Nullable
    public static String getExtra(@NotNull String json) {
        JsonObject obj = (JsonObject) JsonParser.parseString(json);
        if (obj.has("@extra")) {
            return obj.get("@extra").getAsString();
        } else {
            return null;
        }
    }

    @Nullable
    public static String getType(@NotNull String json) {
        JsonObject obj = (JsonObject) JsonParser.parseString(json);
        if (obj.has("@type")) {
            return obj.get("@type").getAsString();
        } else {
            return null;
        }
    }

    public static int getClientId(@NotNull JsonObject obj) {
        if (obj.has("@client_id")) {
            return obj.get("@client_id").getAsInt();
        } else {
            return -1;
        }
    }

    @Nullable
    public static String getExtra(@NotNull JsonObject obj) {
        if (obj.has("@extra")) {
            return obj.get("@extra").getAsString();
        } else {
            return null;
        }
    }

    @Nullable
    public static String getType(@NotNull JsonObject obj) {
        if (obj.has("@type")) {
            return obj.get("@type").getAsString();
        } else {
            return null;
        }
    }

    public static void checkTypeNullable(@Nullable JsonObject obj, @NotNull String type) {
        if (obj == null) {
            return;
        }
        checkTypeNonNull(obj, type);
    }

    public static void checkTypeNonNull(@Nullable JsonObject obj, @NotNull String type) {
        if (obj == null) {
            throw new IllegalArgumentException("Null object, expected: " + type);
        }
        if (!obj.has("@type")) {
            throw new IllegalArgumentException("No @type field in object: " + obj);
        }
        if (!obj.get("@type").getAsString().equals(type)) {
            throw new AssertionError("Wrong @type field in object: " + obj + ", expected: " + type);
        }
    }

    public static void throwRemoteApiExceptionIfError(@Nullable String result) throws RemoteApiException {
        Objects.requireNonNull(result, "result == null");
        throwRemoteApiExceptionIfError(JsonParser.parseString(result).getAsJsonObject());
    }

    public static void throwRemoteApiExceptionIfError(@Nullable JsonObject obj) throws RemoteApiException {
        Objects.requireNonNull(obj, "obj == null");
        if (!obj.has("@type")) {
            throw new IllegalArgumentException("No @type field in object: " + obj);
        }
        String type = obj.get("@type").getAsString();
        if ("error".equals(type)) {
            throw new RemoteApiException(obj.toString());
        }
    }
}
