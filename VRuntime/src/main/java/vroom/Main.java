package vroom;

import fuel3d.Fuel3D;
import fuel3d.io.Debugger;
import fuel3d.io.Logger;
import fuel3d.io.Window;
import org.joml.Vector2i;

public class Main {
    public static void main(String[] args) {
        Fuel3D.init();

        Debugger debugger = new Debugger(new Debugger.Settings());
        Logger.Settings loggerSettings = new Logger.Settings();
        loggerSettings.messageFunction = (messageType, message) -> {
            String type = "";
            switch (messageType) {
                case VERBOSE -> {
                    type = " VINF: ";
                    return;
                }
                case INFO -> {
                    type = " INFO:";
                    //return;
                }
                case WARNING -> {
                    type = " WARNING:";
                }
                case ERROR -> {
                    type = " ERROR:";
                }
                case MISC -> {
                    type = "";
                }
            }
            System.err.format("[Fuel3D]%s %s", type, message);
            System.out.println();
        };
        Logger logger = new Logger(loggerSettings);
        Fuel3D.Settings f3dSettings = new Fuel3D.Settings();
        f3dSettings.enableDebug(debugger);
        f3dSettings.logger = logger;
        Fuel3D f3d = new Fuel3D(f3dSettings);

        Window window = new Window(new Vector2i(1920, 1080), "Hi", f3d);
        while (!window.windowShouldClose()) {
            window.pollEvents();
        }
        window.destroy();

        f3d.destroy();

        Fuel3D.cleanup();
    }
}
