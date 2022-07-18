package cc.ioctl.intern;

import cc.ioctl.intern.NativeBridge;
import cc.ioctl.tdlib.RobotServer;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class TDLibPollThread extends Thread {

    private static final String TAG = "TDLibPollThread";

    @NotNull
    private final RobotServer mServer;

    public TDLibPollThread(RobotServer server) {
        mServer = Objects.requireNonNull(server);
    }

    @Override
    public void run() {
        while (!isInterrupted() && mServer.isRunning()) {
            String event = NativeBridge.nativeTDLibPollEvent(3000);
            if (event != null) {
                mServer.onReceiveTDLibEvent(event);
            }
        }
    }
}
