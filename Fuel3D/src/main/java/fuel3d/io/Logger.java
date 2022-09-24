package fuel3d.io;

public class Logger {
    private final MessageFunction messageFunction;
    private final ErrorString errorString;

    public Logger(Settings settings) {
        messageFunction = settings.messageFunction;
        errorString = settings.errorString;
    }

    public void log(MessageType type, String message) {
        messageFunction.run(type, message);
    }

    public void error(String message) {
        throw new IllegalStateException(errorString.get(message));
    }

    public interface MessageFunction {
        void run(MessageType messageType, String message);
    }
    public interface ErrorString {
        String get(String message);
    }
    public enum MessageType {
        VERBOSE, INFO, WARNING, ERROR, MISC
    }

    public static class Settings {
        public MessageFunction messageFunction = (messageType, message) -> {
            System.err.format("[Fuel3D] %s: %s", messageType.toString(), message);
            System.out.println();
        };

        public ErrorString errorString = message -> String.format("[Fuel3D] ERROR: %s", message);
    }
}
