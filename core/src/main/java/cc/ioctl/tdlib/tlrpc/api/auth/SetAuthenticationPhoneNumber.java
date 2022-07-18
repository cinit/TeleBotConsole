package cc.ioctl.tdlib.tlrpc.api.auth;

import cc.ioctl.tdlib.tlrpc.TlRpcField;
import cc.ioctl.tdlib.tlrpc.TlRpcJsonObject;

public class SetAuthenticationPhoneNumber extends TlRpcJsonObject {

    @TlRpcField("@type")
    public static final String TYPE = "setAuthenticationPhoneNumber";

    @TlRpcField("phone_number")
    public String phoneNumber;

    public SetAuthenticationPhoneNumber() {
        super();
    }

    public SetAuthenticationPhoneNumber(String phoneNumber) {
        super();
        this.phoneNumber = phoneNumber;
    }

}
