package cc.ioctl.telebot.tdlib.tlrpc;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a field in a TLRPC object.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface TlRpcField {
    /**
     * The field name in JSON
     */
    @NotNull
    String value();

    boolean optional() default false;
}
