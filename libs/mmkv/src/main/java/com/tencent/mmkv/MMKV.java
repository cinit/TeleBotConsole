/*
 * Tencent is pleased to support the open source community by making
 * MMKV available.
 *
 * Copyright (C) 2018 THL A29 Limited, a Tencent company.
 * All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *       https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.mmkv;

import cc.ioctl.util.Log;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * An highly efficient, reliable, multi-process key-value storage framework.
 * THE PERFECT drop-in replacement for SharedPreferences and MultiProcessSharedPreferences.
 */
public class MMKV {

    private static final EnumMap<MMKVRecoverStrategic, Integer> recoverIndex;
    private static final EnumMap<MMKVLogLevel, Integer> logLevel2Index;
    private static final MMKVLogLevel[] index2LogLevel;
    private static final Set<Long> checkedHandleSet;

    static {
        recoverIndex = new EnumMap<>(MMKVRecoverStrategic.class);
        recoverIndex.put(MMKVRecoverStrategic.OnErrorDiscard, 0);
        recoverIndex.put(MMKVRecoverStrategic.OnErrorRecover, 1);

        logLevel2Index = new EnumMap<>(MMKVLogLevel.class);
        logLevel2Index.put(MMKVLogLevel.LevelDebug, 0);
        logLevel2Index.put(MMKVLogLevel.LevelInfo, 1);
        logLevel2Index.put(MMKVLogLevel.LevelWarning, 2);
        logLevel2Index.put(MMKVLogLevel.LevelError, 3);
        logLevel2Index.put(MMKVLogLevel.LevelNone, 4);

        index2LogLevel = new MMKVLogLevel[] {MMKVLogLevel.LevelDebug, MMKVLogLevel.LevelInfo, MMKVLogLevel.LevelWarning,
                                             MMKVLogLevel.LevelError, MMKVLogLevel.LevelNone};

        checkedHandleSet = new HashSet<Long>();
    }

    /**
     * Initialize MMKV with customize settings.
     * You must call one of the initialize() methods on App startup process before using MMKV.
     * @param isDebuggable Whether the app is debuggable.
     * @param rootDir The root folder of MMKV, defaults to $(FilesDir)/mmkv.
     * @param logLevel The log level of MMKV, defaults to {@link MMKVLogLevel#LevelInfo}.
     * @return The root folder of MMKV.
     */
    public static String initialize(boolean isDebuggable, String rootDir, String cacheDir, MMKVLogLevel logLevel) {
        // disable process mode in release build
        if (!isDebuggable) {
            disableProcessModeChecker();
        } else {
            enableProcessModeChecker();
        }
        return doInitialize(rootDir, cacheDir, logLevel);
    }

    private static String doInitialize(String rootDir, String cacheDir, MMKVLogLevel logLevel) {
        jniInitialize(rootDir, cacheDir, logLevel2Int(logLevel));
        MMKV.rootDir = rootDir;
        return MMKV.rootDir;
    }

    static private String rootDir = null;

    /**
     * @return The root folder of MMKV, defaults to $(FilesDir)/mmkv.
     */
    public static String getRootDir() {
        return rootDir;
    }

    private static int logLevel2Int(MMKVLogLevel level) {
        int realLevel;
        switch (level) {
            case LevelDebug:
                realLevel = 0;
                break;
            case LevelWarning:
                realLevel = 2;
                break;
            case LevelError:
                realLevel = 3;
                break;
            case LevelNone:
                realLevel = 4;
                break;
            case LevelInfo:
            default:
                realLevel = 1;
                break;
        }
        return realLevel;
    }

    /**
     * Set the log level of MMKV.
     * @param level Defaults to {@link MMKVLogLevel#LevelInfo}.
     */
    public static void setLogLevel(MMKVLogLevel level) {
        int realLevel = logLevel2Int(level);
        setLogLevel(realLevel);
    }

    /**
     * Notify MMKV that App is about to exit. It's totally fine not calling it at all.
     */
    public static native void onExit();

    /**
     * Single-process mode. The default mode on an MMKV instance.
     */
    static public final int SINGLE_PROCESS_MODE = 1 << 0;

    /**
     * Multi-process mode.
     * To enable multi-process accessing of an MMKV instance, you must set this mode whenever you getting that instance.
     */
    static public final int MULTI_PROCESS_MODE = 1 << 1;

    // in case someone mistakenly pass Context.MODE_MULTI_PROCESS
    static private final int CONTEXT_MODE_MULTI_PROCESS = 1 << 2;

    static private final int ASHMEM_MODE = 1 << 3;

    static private final int BACKUP_MODE = 1 << 4;

    /**
     * Create an MMKV instance with an unique ID (in single-process mode).
     * @param mmapID The unique ID of the MMKV instance.
     * @throws RuntimeException if there's an runtime error.
     */
    public static MMKV mmkvWithID(String mmapID) throws RuntimeException {
        if (rootDir == null) {
            throw new IllegalStateException("You should Call MMKV.initialize() first.");
        }

        long handle = getMMKVWithID(mmapID, SINGLE_PROCESS_MODE, null, null);
        return checkProcessMode(handle, mmapID, SINGLE_PROCESS_MODE);
    }

    /**
     * Create an MMKV instance in single-process or multi-process mode.
     * @param mmapID The unique ID of the MMKV instance.
     * @param mode The process mode of the MMKV instance, defaults to {@link #SINGLE_PROCESS_MODE}.
     * @throws RuntimeException if there's an runtime error.
     */
    public static MMKV mmkvWithID(String mmapID, int mode) throws RuntimeException {
        if (rootDir == null) {
            throw new IllegalStateException("You should Call MMKV.initialize() first.");
        }

        long handle = getMMKVWithID(mmapID, mode, null, null);
        return checkProcessMode(handle, mmapID, mode);
    }

    /**
     * Create an MMKV instance in customize process mode, with an encryption key.
     * @param mmapID The unique ID of the MMKV instance.
     * @param mode The process mode of the MMKV instance, defaults to {@link #SINGLE_PROCESS_MODE}.
     * @param cryptKey The encryption key of the MMKV instance (no more than 16 bytes).
     * @throws RuntimeException if there's an runtime error.
     */
    public static MMKV mmkvWithID(String mmapID, int mode, @Nullable String cryptKey) throws RuntimeException {
        if (rootDir == null) {
            throw new IllegalStateException("You should Call MMKV.initialize() first.");
        }

        long handle = getMMKVWithID(mmapID, mode, cryptKey, null);
        return checkProcessMode(handle, mmapID, mode);
    }

    /**
     * Create an MMKV instance in customize folder.
     * @param mmapID The unique ID of the MMKV instance.
     * @param rootPath The folder of the MMKV instance, defaults to $(FilesDir)/mmkv.
     * @throws RuntimeException if there's an runtime error.
     */
    public static MMKV mmkvWithID(String mmapID, String rootPath) throws RuntimeException {
        if (rootDir == null) {
            throw new IllegalStateException("You should Call MMKV.initialize() first.");
        }

        long handle = getMMKVWithID(mmapID, SINGLE_PROCESS_MODE, null, rootPath);
        return checkProcessMode(handle, mmapID, SINGLE_PROCESS_MODE);
    }

    /**
     * Create an MMKV instance with customize settings all in one.
     * @param mmapID The unique ID of the MMKV instance.
     * @param mode The process mode of the MMKV instance, defaults to {@link #SINGLE_PROCESS_MODE}.
     * @param cryptKey The encryption key of the MMKV instance (no more than 16 bytes).
     * @param rootPath The folder of the MMKV instance, defaults to $(FilesDir)/mmkv.
     * @throws RuntimeException if there's an runtime error.
     */
    public static MMKV mmkvWithID(String mmapID, int mode, @Nullable String cryptKey, String rootPath)
        throws RuntimeException {
        if (rootDir == null) {
            throw new IllegalStateException("You should Call MMKV.initialize() first.");
        }

        long handle = getMMKVWithID(mmapID, mode, cryptKey, rootPath);
        return checkProcessMode(handle, mmapID, mode);
    }

    /**
     * Get an backed-up MMKV instance with customize settings all in one.
     * @param mmapID The unique ID of the MMKV instance.
     * @param mode The process mode of the MMKV instance, defaults to {@link #SINGLE_PROCESS_MODE}.
     * @param cryptKey The encryption key of the MMKV instance (no more than 16 bytes).
     * @param rootPath The backup folder of the MMKV instance.
     * @throws RuntimeException if there's an runtime error.
     */
    public static MMKV backedUpMMKVWithID(String mmapID, int mode, @Nullable String cryptKey, String rootPath)
            throws RuntimeException {
        if (rootDir == null) {
            throw new IllegalStateException("You should Call MMKV.initialize() first.");
        }

        mode |= BACKUP_MODE;
        long handle = getMMKVWithID(mmapID, mode, cryptKey, rootPath);
        return checkProcessMode(handle, mmapID, mode);
    }

    /**
     * Create the default MMKV instance in single-process mode.
     * @throws RuntimeException if there's an runtime error.
     */
    public static MMKV defaultMMKV() throws RuntimeException {
        if (rootDir == null) {
            throw new IllegalStateException("You should Call MMKV.initialize() first.");
        }

        long handle = getDefaultMMKV(SINGLE_PROCESS_MODE, null);
        return checkProcessMode(handle, "DefaultMMKV", SINGLE_PROCESS_MODE);
    }

    /**
     * Create the default MMKV instance in customize process mode, with an encryption key.
     * @param mode The process mode of the MMKV instance, defaults to {@link #SINGLE_PROCESS_MODE}.
     * @param cryptKey The encryption key of the MMKV instance (no more than 16 bytes).
     * @throws RuntimeException if there's an runtime error.
     */
    public static MMKV defaultMMKV(int mode, @Nullable String cryptKey) throws RuntimeException {
        if (rootDir == null) {
            throw new IllegalStateException("You should Call MMKV.initialize() first.");
        }

        long handle = getDefaultMMKV(mode, cryptKey);
        return checkProcessMode(handle, "DefaultMMKV", mode);
    }

    private static MMKV checkProcessMode(long handle, String mmapID, int mode) throws RuntimeException {
        if (handle == 0) {
            throw new RuntimeException("Fail to create an MMKV instance [" + mmapID + "] in JNI");
        }
        if (!isProcessModeCheckerEnabled) {
            return new MMKV(handle);
        }
        synchronized (checkedHandleSet) {
            if (!checkedHandleSet.contains(handle)) {
                if (!checkProcessMode(handle)) {
                    String message;
                    if (mode == SINGLE_PROCESS_MODE) {
                        message = "Opening a multi-process MMKV instance [" + mmapID + "] with SINGLE_PROCESS_MODE!";
                    } else {
                        message = "Opening an MMKV instance [" + mmapID + "] with MULTI_PROCESS_MODE, ";
                        message += "while it's already been opened with SINGLE_PROCESS_MODE by someone somewhere else!";
                    }
                    throw new IllegalArgumentException(message);
                }
                checkedHandleSet.add(handle);
            }
        }
        return new MMKV(handle);
    }

    // Enable checkProcessMode() when initializing an MMKV instance, it's automatically enabled on debug build.
    private static boolean isProcessModeCheckerEnabled = true;

    /**
     * Manually enable the process mode checker.
     * By default, it's automatically enabled in DEBUG build, and disabled in RELEASE build.
     * If it's enabled, MMKV will throw exceptions when an MMKV instance is created with mismatch process mode.
     */
    public static void enableProcessModeChecker() {
        synchronized (checkedHandleSet) {
            isProcessModeCheckerEnabled = true;
        }
        Log.i("MMKV", "Enable checkProcessMode()");
    }

    /**
     * Manually disable the process mode checker.
     * By default, it's automatically enabled in DEBUG build, and disabled in RELEASE build.
     * If it's enabled, MMKV will throw exceptions when an MMKV instance is created with mismatch process mode.
     */
    public static void disableProcessModeChecker() {
        synchronized (checkedHandleSet) {
            isProcessModeCheckerEnabled = false;
        }
        Log.i("MMKV", "Disable checkProcessMode()");
    }

    /**
     * @return The encryption key (no more than 16 bytes).
     */
    @Nullable
    public native String cryptKey();

    /**
     * Transform plain text into encrypted text, or vice versa by passing a null encryption key.
     * You can also change existing crypt key with a different cryptKey.
     * @param cryptKey The new encryption key (no more than 16 bytes).
     * @return True if success, otherwise False.
     */
    public native boolean reKey(@Nullable String cryptKey);

    /**
     * Just reset the encryption key (will not encrypt or decrypt anything).
     * Usually you should call this method after another process has {@link #reKey(String)} the multi-process MMKV instance.
     * @param cryptKey The new encryption key (no more than 16 bytes).
     */
    public native void checkReSetCryptKey(@Nullable String cryptKey);

    /**
     * @return The device's memory page size.
     */
    public static native int pageSize();

    /**
     * @return The version of MMKV.
     */
    public static native String version();

    /**
     * @return The unique ID of the MMKV instance.
     */
    public native String mmapID();

    /**
     * Exclusively inter-process lock the MMKV instance.
     * It will block and wait until it successfully locks the file.
     * It will make no effect if the MMKV instance is created with {@link #SINGLE_PROCESS_MODE}.
     */
    public native void lock();

    /**
     * Exclusively inter-process unlock the MMKV instance.
     * It will make no effect if the MMKV instance is created with {@link #SINGLE_PROCESS_MODE}.
     */
    public native void unlock();

    /**
     * Try exclusively inter-process lock the MMKV instance.
     * It will not block if the file has already been locked by another process.
     * It will make no effect if the MMKV instance is created with {@link #SINGLE_PROCESS_MODE}.
     * @return True if successfully locked, otherwise return immediately with False.
     */
    public native boolean tryLock();

    public boolean encode(String key, boolean value) {
        return encodeBool(nativeHandle, key, value);
    }

    public boolean decodeBool(String key) {
        return decodeBool(nativeHandle, key, false);
    }

    public boolean decodeBool(String key, boolean defaultValue) {
        return decodeBool(nativeHandle, key, defaultValue);
    }

    public boolean encode(String key, int value) {
        return encodeInt(nativeHandle, key, value);
    }

    public int decodeInt(String key) {
        return decodeInt(nativeHandle, key, 0);
    }

    public int decodeInt(String key, int defaultValue) {
        return decodeInt(nativeHandle, key, defaultValue);
    }

    public boolean encode(String key, long value) {
        return encodeLong(nativeHandle, key, value);
    }

    public long decodeLong(String key) {
        return decodeLong(nativeHandle, key, 0);
    }

    public long decodeLong(String key, long defaultValue) {
        return decodeLong(nativeHandle, key, defaultValue);
    }

    public boolean encode(String key, float value) {
        return encodeFloat(nativeHandle, key, value);
    }

    public float decodeFloat(String key) {
        return decodeFloat(nativeHandle, key, 0);
    }

    public float decodeFloat(String key, float defaultValue) {
        return decodeFloat(nativeHandle, key, defaultValue);
    }

    public boolean encode(String key, double value) {
        return encodeDouble(nativeHandle, key, value);
    }

    public double decodeDouble(String key) {
        return decodeDouble(nativeHandle, key, 0);
    }

    public double decodeDouble(String key, double defaultValue) {
        return decodeDouble(nativeHandle, key, defaultValue);
    }

    public boolean encode(String key, @Nullable String value) {
        return encodeString(nativeHandle, key, value);
    }

    @Nullable
    public String decodeString(String key) {
        return decodeString(nativeHandle, key, null);
    }

    @Nullable
    public String decodeString(String key, @Nullable String defaultValue) {
        return decodeString(nativeHandle, key, defaultValue);
    }

    public boolean encode(String key, @Nullable Set<String> value) {
        return encodeSet(nativeHandle, key, (value == null) ? null : value.toArray(new String[0]));
    }

    @Nullable
    public Set<String> decodeStringSet(String key) {
        return decodeStringSet(key, null);
    }

    @Nullable
    public Set<String> decodeStringSet(String key, @Nullable Set<String> defaultValue) {
        return decodeStringSet(key, defaultValue, HashSet.class);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public Set<String> decodeStringSet(String key, @Nullable Set<String> defaultValue, Class<? extends Set> cls) {
        String[] result = decodeStringSet(nativeHandle, key);
        if (result == null) {
            return defaultValue;
        }
        Set<String> a;
        try {
            a = cls.newInstance();
        } catch (IllegalAccessException e) {
            return defaultValue;
        } catch (InstantiationException e) {
            return defaultValue;
        }
        a.addAll(Arrays.asList(result));
        return a;
    }

    public boolean encode(String key, @Nullable byte[] value) {
        return encodeBytes(nativeHandle, key, value);
    }

    @Nullable
    public byte[] decodeBytes(String key) {
        return decodeBytes(key, null);
    }

    @Nullable
    public byte[] decodeBytes(String key, @Nullable byte[] defaultValue) {
        byte[] ret = decodeBytes(nativeHandle, key);
        return (ret != null) ? ret : defaultValue;
    }

    /**
     * Get the actual size consumption of the key's value.
     * Note: might be a little bigger than value's length.
     * @param key The key of the value.
     */
    public int getValueSize(String key) {
        return valueSize(nativeHandle, key, false);
    }

    /**
     * Get the actual size of the key's value. String's length or byte[]'s length, etc.
     * @param key The key of the value.
     */
    public int getValueActualSize(String key) {
        return valueSize(nativeHandle, key, true);
    }

    /**
     * Check whether or not MMKV contains the key.
     * @param key The key of the value.
     */
    public boolean containsKey(String key) {
        return containsKey(nativeHandle, key);
    }

    /**
     * @return All the keys.
     */
    @Nullable
    public native String[] allKeys();

    /**
     * @return The total count of all the keys.
     */
    public long count() {
        return count(nativeHandle);
    }

    /**
     * Get the size of the underlying file. Align to the disk block size, typically 4K for an Android device.
     */
    public long totalSize() {
        return totalSize(nativeHandle);
    }

    /**
     * Get the actual used size of the MMKV instance.
     * This size might increase and decrease as MMKV doing insertion and full write back.
     */
    public long actualSize() {
        return actualSize(nativeHandle);
    }

    public void removeValueForKey(String key) {
        removeValueForKey(nativeHandle, key);
    }

    /**
     * Batch remove some keys from the MMKV instance.
     * @param arrKeys The keys to be removed.
     */
    public native void removeValuesForKeys(String[] arrKeys);

    /**
     * Clear all the key-values inside the MMKV instance.
     */
    public native void clearAll();

    /**
     * The {@link #totalSize()} of an MMKV instance won't reduce after deleting key-values,
     * call this method after lots of deleting if you care about disk usage.
     * Note that {@link #clearAll()}  has a similar effect.
     */
    public native void trim();

    /**
     * Call this method if the MMKV instance is no longer needed in the near future.
     * Any subsequent call to any MMKV instances with the same ID is undefined behavior.
     */
    public native void close();

    /**
     * Clear memory cache of the MMKV instance.
     * You can call it on memory warning.
     * Any subsequent call to the MMKV instance will trigger all key-values loading from the file again.
     */
    public native void clearMemoryCache();

    /**
     * Save all mmap memory to file synchronously.
     * You don't need to call this, really, I mean it.
     * Unless you worry about the device running out of battery.
     */
    public void sync() {
        sync(true);
    }

    /**
     * Save all mmap memory to file asynchronously.
     * No need to call this unless you worry about the device running out of battery.
     */
    public void async() {
        sync(false);
    }

    private native void sync(boolean sync);

    /**
     * Check whether the MMKV file is valid or not.
     * Note: Don't use this to check the existence of the instance, the result is undefined on nonexistent files.
     */
    public static boolean isFileValid(String mmapID) {
        return isFileValid(mmapID, null);
    }

    /**
     * Check whether the MMKV file is valid or not on customize folder.
     * @param mmapID The unique ID of the MMKV instance.
     * @param rootPath The folder of the MMKV instance, defaults to $(FilesDir)/mmkv.
     */
    public static native boolean isFileValid(String mmapID, @Nullable String rootPath);

    /**
     * backup one MMKV instance to dstDir
     * @param mmapID the MMKV ID to backup
     * @param rootPath the customize root path of the MMKV, if null then backup from the root dir of MMKV
     * @param dstDir the backup destination directory
     */
    public static native boolean backupOneToDirectory(String mmapID, String dstDir, @Nullable String rootPath);

    /**
     * restore one MMKV instance from srcDir
     * @param mmapID the MMKV ID to restore
     * @param srcDir the restore source directory
     * @param rootPath the customize root path of the MMKV, if null then restore to the root dir of MMKV
     */
    public static native boolean restoreOneMMKVFromDirectory(String mmapID, String srcDir, @Nullable String rootPath);

    /**
     * backup all MMKV instance to dstDir
     * @param dstDir the backup destination directory
     * @return count of MMKV successfully backuped
     */
    public static native long backupAllToDirectory(String dstDir);

    /**
     * restore all MMKV instance from srcDir
     * @param srcDir the restore source directory
     * @return count of MMKV successfully restored
     */
    public static native long restoreAllFromDirectory(String srcDir);

    /**
     * Intentionally Not Supported. Because MMKV does type-eraser inside to get better performance.
     */
    public Map<String, ?> getAll() {
        throw new UnsupportedOperationException(
            "Intentionally Not Supported. Use allKeys() instead, getAll() not implement because type-erasure inside mmkv");
    }

    @Nullable
    public String getString(String key, @Nullable String defValue) {
        return decodeString(nativeHandle, key, defValue);
    }

    public MMKV putString(String key, @Nullable String value) {
        encodeString(nativeHandle, key, value);
        return this;
    }

    @Nullable
    public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
        return decodeStringSet(key, defValues);
    }

    public MMKV putStringSet(String key, @Nullable Set<String> values) {
        encode(key, values);
        return this;
    }

    public MMKV putBytes(String key, @Nullable byte[] bytes) {
        encode(key, bytes);
        return this;
    }

    public byte[] getBytes(String key, @Nullable byte[] defValue) {
        return decodeBytes(key, defValue);
    }

    public int getInt(String key, int defValue) {
        return decodeInt(nativeHandle, key, defValue);
    }

    public MMKV putInt(String key, int value) {
        encodeInt(nativeHandle, key, value);
        return this;
    }

    public long getLong(String key, long defValue) {
        return decodeLong(nativeHandle, key, defValue);
    }

    public MMKV putLong(String key, long value) {
        encodeLong(nativeHandle, key, value);
        return this;
    }

    public float getFloat(String key, float defValue) {
        return decodeFloat(nativeHandle, key, defValue);
    }

    public MMKV putFloat(String key, float value) {
        encodeFloat(nativeHandle, key, value);
        return this;
    }

    public boolean getBoolean(String key, boolean defValue) {
        return decodeBool(nativeHandle, key, defValue);
    }

    public MMKV putBoolean(String key, boolean value) {
        encodeBool(nativeHandle, key, value);
        return this;
    }

    public MMKV remove(String key) {
        removeValueForKey(key);
        return this;
    }

    /**
     * {@link #clearAll()}
     */
    public MMKV clear() {
        clearAll();
        return this;
    }

    /**
     * @deprecated This method is only for compatibility purpose. You should remove all the calls after migration to MMKV.
     * MMKV doesn't rely on commit() to save data to file.
     * If you really worry about losing battery and data corruption, call {@link #async()} or {@link #sync()} instead.
     */
    @Deprecated
    public boolean commit() {
        sync(true);
        return true;
    }

    /**
     * @deprecated This method is only for compatibility purpose. You should remove all the calls after migration to MMKV.
     * MMKV doesn't rely on apply() to save data to file.
     * If you really worry about losing battery and data corruption, call {@link #async()} instead.
     */
    @Deprecated
    public void apply() {
        sync(false);
    }

    public boolean contains(String key) {
        return containsKey(key);
    }

    /**
     * Get an ashmem MMKV instance that has been initiated by another process.
     * Normally you should just call {@link #mmkvWithAshmemID(Context, String, int, int, String)} instead.
     * @param mmapID The unique ID of the MMKV instance.
     * @param fd The file descriptor of the ashmem of the MMKV file, transferred from another process by binder.
     * @param metaFD The file descriptor of the ashmem of the MMKV crc file, transferred from another process by binder.
     * @param cryptKey The encryption key of the MMKV instance (no more than 16 bytes).
     * @throws RuntimeException If any failure in JNI or runtime.
     */
    // Parcelable
    public static MMKV mmkvWithAshmemFD(String mmapID, int fd, int metaFD, String cryptKey) throws RuntimeException {
        long handle = getMMKVWithAshmemFD(mmapID, fd, metaFD, cryptKey);
        if (handle == 0) {
            throw new RuntimeException("Fail to create an ashmem MMKV instance [" + mmapID + "] in JNI");
        }
        return new MMKV(handle);
    }

    /**
     * @return The file descriptor of the ashmem of the MMKV file.
     */
    public native int ashmemFD();

    /**
     * @return The file descriptor of the ashmem of the MMKV crc file.
     */
    public native int ashmemMetaFD();

    /**
     * Create an native buffer, whose underlying memory can be directly transferred to another JNI method.
     * Avoiding unnecessary JNI boxing and unboxing.
     * An NativeBuffer must be manually {@link #destroyNativeBuffer} to avoid memory leak.
     * @param size The size of the underlying memory.
     */
    @Nullable
    public static NativeBuffer createNativeBuffer(int size) {
        long pointer = createNB(size);
        if (pointer <= 0) {
            return null;
        }
        return new NativeBuffer(pointer, size);
    }

    /**
     * Destroy the native buffer. An NativeBuffer must be manually destroy to avoid memory leak.
     */
    public static void destroyNativeBuffer(NativeBuffer buffer) {
        destroyNB(buffer.pointer, buffer.size);
    }

    /**
     * Write the value of the key to the native buffer.
     * @return The size written. Return -1 on any error.
     */
    public int writeValueToNativeBuffer(String key, NativeBuffer buffer) {
        return writeValueToNB(nativeHandle, key, buffer.pointer, buffer.size);
    }

    // callback handler
    private static MMKVHandler gCallbackHandler;
    private static boolean gWantLogReDirecting = false;

    /**
     * Register a handler for MMKV log redirecting, and error handling.
     */
    public static void registerHandler(MMKVHandler handler) {
        gCallbackHandler = handler;

        if (gCallbackHandler.wantLogRedirecting()) {
            setCallbackHandler(true, true);
            gWantLogReDirecting = true;
        } else {
            setCallbackHandler(false, true);
            gWantLogReDirecting = false;
        }
    }

    /**
     * Unregister the handler for MMKV.
     */
    public static void unregisterHandler() {
        gCallbackHandler = null;

        setCallbackHandler(false, false);
        gWantLogReDirecting = false;
    }

    private static int onMMKVCRCCheckFail(String mmapID) {
        MMKVRecoverStrategic strategic = MMKVRecoverStrategic.OnErrorDiscard;
        if (gCallbackHandler != null) {
            strategic = gCallbackHandler.onMMKVCRCCheckFail(mmapID);
        }
        simpleLog(MMKVLogLevel.LevelInfo, "Recover strategic for " + mmapID + " is " + strategic);
        Integer value = recoverIndex.get(strategic);
        return (value == null) ? 0 : value;
    }

    private static int onMMKVFileLengthError(String mmapID) {
        MMKVRecoverStrategic strategic = MMKVRecoverStrategic.OnErrorDiscard;
        if (gCallbackHandler != null) {
            strategic = gCallbackHandler.onMMKVFileLengthError(mmapID);
        }
        simpleLog(MMKVLogLevel.LevelInfo, "Recover strategic for " + mmapID + " is " + strategic);
        Integer value = recoverIndex.get(strategic);
        return (value == null) ? 0 : value;
    }

    private static void mmkvLogImp(int level, String file, int line, String function, String message) {
        if (gCallbackHandler != null && gWantLogReDirecting) {
            gCallbackHandler.mmkvLog(index2LogLevel[level], file, line, function, message);
        } else {
            switch (index2LogLevel[level]) {
                case LevelDebug:
                    Log.d("MMKV", message);
                    break;
                case LevelInfo:
                    Log.i("MMKV", message);
                    break;
                case LevelWarning:
                    Log.w("MMKV", message);
                    break;
                case LevelError:
                    Log.e("MMKV", message);
                    break;
                case LevelNone:
                    break;
            }
        }
    }

    private static void simpleLog(MMKVLogLevel level, String message) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[stacktrace.length - 1];
        Integer i = logLevel2Index.get(level);
        int intLevel = (i == null) ? 0 : i;
        mmkvLogImp(intLevel, e.getFileName(), e.getLineNumber(), e.getMethodName(), message);
    }

    // content change notification of other process
    // trigger by getXXX() or setXXX() or checkContentChangedByOuterProcess()
    private static MMKVContentChangeNotification gContentChangeNotify;

    /**
     * Register for MMKV inter-process content change notification.
     * The notification will trigger only when any method is manually called on the MMKV instance.
     * For example {@link #checkContentChangedByOuterProcess()}.
     * @param notify The notification handler.
     */
    public static void registerContentChangeNotify(MMKVContentChangeNotification notify) {
        gContentChangeNotify = notify;
        setWantsContentChangeNotify(gContentChangeNotify != null);
    }

    /**
     * Unregister for MMKV inter-process content change notification.
     */
    public static void unregisterContentChangeNotify() {
        gContentChangeNotify = null;
        setWantsContentChangeNotify(false);
    }

    private static void onContentChangedByOuterProcess(String mmapID) {
        if (gContentChangeNotify != null) {
            gContentChangeNotify.onContentChangedByOuterProcess(mmapID);
        }
    }

    private static native void setWantsContentChangeNotify(boolean needsNotify);

    /**
     * Check inter-process content change manually.
     */
    public native void checkContentChangedByOuterProcess();

    // jni
    private final long nativeHandle;

    private MMKV(long handle) {
        nativeHandle = handle;
    }

    private static native void jniInitialize(String rootDir, String cacheDir, int level);

    private native static long
    getMMKVWithID(String mmapID, int mode, @Nullable String cryptKey, @Nullable String rootPath);

    private native static long getMMKVWithIDAndSize(String mmapID, int size, int mode, @Nullable String cryptKey);

    private native static long getDefaultMMKV(int mode, @Nullable String cryptKey);

    private native static long getMMKVWithAshmemFD(String mmapID, int fd, int metaFD, @Nullable String cryptKey);

    private native boolean encodeBool(long handle, String key, boolean value);

    private native boolean decodeBool(long handle, String key, boolean defaultValue);

    private native boolean encodeInt(long handle, String key, int value);

    private native int decodeInt(long handle, String key, int defaultValue);

    private native boolean encodeLong(long handle, String key, long value);

    private native long decodeLong(long handle, String key, long defaultValue);

    private native boolean encodeFloat(long handle, String key, float value);

    private native float decodeFloat(long handle, String key, float defaultValue);

    private native boolean encodeDouble(long handle, String key, double value);

    private native double decodeDouble(long handle, String key, double defaultValue);

    private native boolean encodeString(long handle, String key, @Nullable String value);

    @Nullable
    private native String decodeString(long handle, String key, @Nullable String defaultValue);

    private native boolean encodeSet(long handle, String key, @Nullable String[] value);

    @Nullable
    private native String[] decodeStringSet(long handle, String key);

    private native boolean encodeBytes(long handle, String key, @Nullable byte[] value);

    @Nullable
    private native byte[] decodeBytes(long handle, String key);

    private native boolean containsKey(long handle, String key);

    private native long count(long handle);

    private native long totalSize(long handle);

    private native long actualSize(long handle);

    private native void removeValueForKey(long handle, String key);

    private native int valueSize(long handle, String key, boolean actualSize);

    private static native void setLogLevel(int level);

    private static native void setCallbackHandler(boolean logReDirecting, boolean hasCallback);

    private static native long createNB(int size);

    private static native void destroyNB(long pointer, int size);

    private native int writeValueToNB(long handle, String key, long pointer, int size);

    private static native boolean checkProcessMode(long handle);
}
