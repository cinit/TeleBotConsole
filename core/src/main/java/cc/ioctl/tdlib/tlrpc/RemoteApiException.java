package cc.ioctl.tdlib.tlrpc;

public class RemoteApiException extends Exception {

    public RemoteApiException(String message, Throwable cause) {
        super(message, cause);
    }

    public RemoteApiException(String message) {
        super(message);
    }

    public RemoteApiException(Throwable cause) {
        super(cause);
    }

    public RemoteApiException() {
    }

}
