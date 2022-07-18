package cc.ioctl.tdlib.tlrpc.api.auth;

import cc.ioctl.tdlib.tlrpc.TlRpcField;
import cc.ioctl.tdlib.tlrpc.TlRpcJsonObject;

public class SetTdlibParameters extends TlRpcJsonObject {

    @TlRpcField("@type")
    public static final String TYPE = "setTdlibParameters";

    @TlRpcField("parameters")
    public Parameter parameters;

    public SetTdlibParameters() {
        super();
    }

    public SetTdlibParameters(Parameter parameters) {
        super();
        this.parameters = parameters;
    }

    public static class Parameter extends TlRpcJsonObject {

        @TlRpcField("database_directory")
        public String databaseDirectory;

        @TlRpcField("use_message_database")
        public boolean useMessageDatabase;

        @TlRpcField("use_secret_chats")
        public boolean useSecretChats;

        @TlRpcField("api_id")
        public int apiId;

        @TlRpcField("api_hash")
        public String apiHash;

        @TlRpcField("system_language_code")
        public String systemLanguageCode;

        @TlRpcField("device_model")
        public String deviceModel;

        @TlRpcField("application_version")
        public String applicationVersion;

        @TlRpcField("enable_storage_optimizer")
        public boolean enableStorageOptimizer;

        @TlRpcField("use_test_dc")
        public boolean useTestDC;

        public Parameter() {
            super();
        }

        public Parameter(String databaseDirectory, boolean useMessageDatabase, boolean useSecretChats,
                         int apiId, String apiHash, String systemLanguageCode, String deviceModel,
                         String applicationVersion, boolean enableStorageOptimizer, boolean useTestDC) {
            super();
            this.databaseDirectory = databaseDirectory;
            this.useMessageDatabase = useMessageDatabase;
            this.useSecretChats = useSecretChats;
            this.apiId = apiId;
            this.apiHash = apiHash;
            this.systemLanguageCode = systemLanguageCode;
            this.deviceModel = deviceModel;
            this.applicationVersion = applicationVersion;
            this.enableStorageOptimizer = enableStorageOptimizer;
            this.useTestDC = useTestDC;
        }
    }
}
