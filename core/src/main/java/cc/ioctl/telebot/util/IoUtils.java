package cc.ioctl.telebot.util;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

public class IoUtils {

    private IoUtils() {
        throw new AssertionError("No instances");
    }

    /**
     * Reads all bytes from an input stream and returns them as a byte array.
     * <p>
     * If an exception occurs, the exception is rethrown and the input stream is closed.
     *
     * @param is the input stream to read from
     * @return a byte array containing all the bytes from the input stream
     * @throws IOException if an I/O error occurs
     */
    @NotNull
    public static byte[] readFully(@NotNull InputStream is) throws IOException {
        Objects.requireNonNull(is, "is == null");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        try (is) {
            int len;
            while ((len = is.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
        }
        return baos.toByteArray();
    }

    /**
     * Read exact @{code length} bytes from an input stream and stores them in a byte array.
     * The input stream is NOT closed after reading, or exception thrown.
     *
     * @param is     the input stream to read from
     * @param buffer the buffer to write to
     * @param offset the offset in the buffer to start writing to
     * @param length the number of bytes to read
     * @throws IOException if an I/O error occurs
     */
    public static void readExact(@NotNull InputStream is, @NotNull byte[] buffer, int offset, int length) throws IOException {
        Objects.requireNonNull(is, "is == null");
        Objects.requireNonNull(buffer, "buffer == null");
        if (offset < 0 || length < 0 || offset + length > buffer.length) {
            throw new IndexOutOfBoundsException("offset: " + offset + ", length: " + length + ", buffer.length: " + buffer.length);
        }
        int remaining = length;
        while (remaining > 0) {
            int read = is.read(buffer, offset, remaining);
            if (read < 0) {
                throw new EOFException("Unexpected end of stream, expected " + remaining + " more bytes");
            }
            remaining -= read;
            offset += read;
        }
    }

    /**
     * Read exact @{code length} bytes from RandomAccessFile at current position and stores them in a byte array.
     * The RandomAccessFile is NOT closed after reading, or exception thrown.
     *
     * @param in     the RandomAccessFile to read from
     * @param buffer the buffer to store the bytes in
     * @param offset the offset in the buffer to start writing to
     * @param length the number of bytes to read
     * @throws IOException if an I/O error occurs
     */
    public static void readExact(@NotNull RandomAccessFile in, @NotNull byte[] buffer, int offset, int length) throws IOException {
        Objects.requireNonNull(in, "in == null");
        Objects.requireNonNull(buffer, "buffer == null");
        if (offset < 0 || length < 0 || offset + length > buffer.length) {
            throw new IndexOutOfBoundsException("offset: " + offset + ", length: " + length + ", buffer.length: " + buffer.length);
        }
        int remaining = length;
        while (remaining > 0) {
            int read = in.read(buffer, offset, remaining);
            if (read < 0) {
                throw new EOFException("Unexpected end of RandomAccessFile, expected " + remaining + " more bytes");
            }
            remaining -= read;
            offset += read;
        }
    }

    public static void skipExact(@NotNull InputStream is, long offset) throws IOException {
        Objects.requireNonNull(is, "is == null");
        long ret = is.skip(offset);
        if (ret != offset) {
            throw new IOException("Failed to skip " + offset + " bytes, got " + ret);
        }
    }

    /**
     * Read all bytes from a file and returns them as a byte array.
     * <p>
     * WARNING: This method will NOT work for a file which is larger than 2GB.
     *
     * @param file the file to read from
     * @return a byte array containing all the bytes from the file
     * @throws IOException if an I/O error occurs
     */
    @NotNull
    public static byte[] readFile(@NotNull File file) throws IOException {
        Objects.requireNonNull(file, "file == null");
        if (!file.exists()) {
            throw new IOException("File not found: " + file.getAbsolutePath());
        }
        long lsize = file.length();
        if (lsize > Integer.MAX_VALUE) {
            throw new IOException("File too large: " + file.getAbsolutePath() + ", size: " + lsize);
        }
        int size = (int) lsize;
        byte[] buf = new byte[size];
        try (InputStream is = new FileInputStream(file)) {
            int len;
            int offset = 0;
            while (offset < size && (len = is.read(buf, offset, size - offset)) != -1) {
                offset += len;
            }
            if (offset != size) {
                throw new IOException("Could not completely read file: " + file.getAbsolutePath() + ", expected: " + size + ", got: " + offset);
            }
        }
        return buf;
    }

    /**
     * Used for create a parent directory if it does not exist.
     * <p>
     * This will throw an unsafe {@link IOException} if the parent directory cannot be created.
     * <p>
     * Only use this when you think mkdirs() must be successful.
     *
     * @param file the file whose parent directory will be created if it does not exist
     * @return the same file as the parameter
     */
    @NotNull
    public static File makeParentDirsOrThrow(@NotNull File file) {
        Objects.requireNonNull(file, "file == null");
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            mkdirsOrThrow(parent);
        }
        return file;
    }

    /**
     * Used to create a directory if it does not exist.
     * <p>
     * This will throw an unsafe {@link IOException} if the directory cannot be created.
     * <p>
     * Only use this when you think mkdirs() must be successful.
     *
     * @param dir the directory to create if it does not exist
     * @return the same directory as the parameter
     */
    @NotNull
    public static File mkdirsOrThrow(@NotNull File dir) {
        Objects.requireNonNull(dir, "dir == null");
        if (!dir.exists() && !dir.mkdirs()) {
            unsafeThrow(new IOException("Failed to create directory: " + dir.getAbsolutePath()));
        }
        return dir;
    }

    public static void unsafeThrow(@NotNull Throwable t) {
        Objects.requireNonNull(t, "t == null");
        unsafeThrowImpl(t);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void unsafeThrowImpl(@NotNull Throwable t) throws T {
        throw (T) t;
    }

    public static void writeFile(@NotNull File file, @NotNull byte[] data) throws IOException {
        Objects.requireNonNull(file, "file == null");
        Objects.requireNonNull(data, "data == null");
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                mkdirsOrThrow(parent);
            }
            if (!file.createNewFile()) {
                throw new IOException("Failed to create file: " + file.getAbsolutePath());
            }
        }
        try (OutputStream os = new FileOutputStream(file)) {
            os.write(data);
            os.flush();
        }
    }

    @NotNull
    public static byte[] calculateFileMd5(@NotNull InputStream is) throws IOException {
        Objects.requireNonNull(is, "is == null");
        try (is) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                md.update(buf, 0, len);
            }
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("No MD5 algorithm found", e);
        }
    }

    @NotNull
    public static byte[] calculateFileMd5(@NotNull File file) throws IOException {
        Objects.requireNonNull(file, "file == null");
        try (InputStream is = new FileInputStream(file)) {
            return calculateFileMd5(is);
        }
    }

    private static final char[] HEX_LOWER_DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    private static final char[] HEX_UPPER_DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    @NotNull
    public static String calculateFileMd5HexString(@NotNull File file, boolean upperCase) throws IOException {
        Objects.requireNonNull(file, "file == null");
        byte[] md5 = calculateFileMd5(file);
        char[] hexDigits = upperCase ? HEX_UPPER_DIGITS : HEX_LOWER_DIGITS;
        char[] hex = new char[md5.length * 2];
        for (int i = 0; i < md5.length; i++) {
            int b = md5[i] & 0xFF;
            hex[i * 2] = hexDigits[b >>> 4];
            hex[i * 2 + 1] = hexDigits[b & 0xF];
        }
        return new String(hex);
    }
}
