package cc.ioctl.tdlib.tlrpc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;

public abstract class TlRpcJsonObject implements Serializable, Cloneable {

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
                    if (type == int.class) {
                        result.addProperty(fieldName, (int) value);
                    } else if (type == long.class) {
                        result.addProperty(fieldName, (long) value);
                    } else if (type == boolean.class) {
                        result.addProperty(fieldName, (boolean) value);
                    } else if (type == double.class) {
                        result.addProperty(fieldName, (double) value);
                    } else if (type == float.class) {
                        result.addProperty(fieldName, (float) value);
                    } else if (type == short.class) {
                        result.addProperty(fieldName, (short) value);
                    } else if (type == byte.class) {
                        result.addProperty(fieldName, (byte) value);
                    } else if (type == char.class) {
                        result.addProperty(fieldName, (char) value);
                    } else {
                        throw new AssertionError("Unknown primitive type: " + type);
                    }
                } else if (type == String.class) {
                    result.addProperty(fieldName, (String) value);
                } else if (TlRpcJsonObject.class.isAssignableFrom(type)) {
                    result.add(fieldName, ((TlRpcJsonObject) value).toJsonObject());
                } else {
                    throw new AssertionError("Unknown type: " + type);
                }
            }
        }
        return result;
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

    public static <T extends TlRpcJsonObject> T fromJsonString(@NotNull Class<T> clazz, @NotNull String json) throws ReflectiveOperationException {
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        T result = clazz.getConstructor().newInstance();
        for (Field f : clazz.getDeclaredFields()) {
            if (!Modifier.isPublic(f.getModifiers())) {
                continue;
            }
            // check annotation
            if (f.isAnnotationPresent(TlRpcField.class)) {
                TlRpcField annotation = f.getAnnotation(TlRpcField.class);
                String fieldName = annotation.value();
                if (jsonObject.has(fieldName)) {
                    Object value = jsonObject.get(fieldName);
                    Class<?> type = f.getType();
                    try {
                        if (type.isPrimitive()) {
                            if (type == int.class) {
                                f.setInt(result, (int) value);
                            } else if (type == long.class) {
                                f.setLong(result, (long) value);
                            } else if (type == boolean.class) {
                                f.setBoolean(result, (boolean) value);
                            } else if (type == double.class) {
                                f.setDouble(result, (double) value);
                            } else if (type == float.class) {
                                f.setFloat(result, (float) value);
                            } else if (type == short.class) {
                                f.setShort(result, (short) value);
                            } else if (type == byte.class) {
                                f.setByte(result, (byte) value);
                            } else if (type == char.class) {
                                f.setChar(result, (char) value);
                            } else {
                                throw new AssertionError("Unknown primitive type: " + type);
                            }
                        } else if (type == String.class) {
                            f.set(result, value);
                        } else if (TlRpcJsonObject.class.isAssignableFrom(type)) {
                            f.set(result, fromJsonString((Class<? extends TlRpcJsonObject>) type, value.toString()));
                        } else {
                            throw new AssertionError("Unknown type: " + type);
                        }
                    } catch (IllegalAccessException e) {
                        // should not happen
                        throw new AssertionError(e);
                    }
                }
            }
        }
        return result;
    }

    public static TlRpcObjDecodeResult fromJsonStringEx(@NotNull Class<? extends TlRpcJsonObject> clazz, @NotNull String json) throws ReflectiveOperationException {
        TlRpcJsonObject obj = fromJsonString(clazz, json);
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
