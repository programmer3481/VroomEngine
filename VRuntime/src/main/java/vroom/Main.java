package vroom;

import fuel3d.Fuel3D;
import fuel3d.Debugger;
import fuel3d.Logger;
import fuel3d.Window;

public class Main {
    public static void main(String[] args) {
        Fuel3D.init();

        Debugger debugger = new Debugger(new Debugger.Settings());
        Logger.Settings loggerSettings = new Logger.Settings();
        loggerSettings.messageFunction = (messageType, message) -> {
            String type = "";
            switch (messageType) {
                case VERBOSE -> {
                    //type = " VINF: ";
                    return;
                }
                case INFO -> type = " INFO:";
                case WARNING -> type = " WARNING:";
                case ERROR -> type = " ERROR:";
                case MISC -> type = "";
            }
            System.err.format("[Fuel3D]%s %s", type, message);
            System.out.println();
        };
        Logger logger = new Logger(loggerSettings);

        Fuel3D.Settings f3dSettings = new Fuel3D.Settings();
        f3dSettings.enableDebug(debugger);
        f3dSettings.logger = logger;
        f3dSettings.initWindowWidth = 1920;
        f3dSettings.initWindowHeight = 1080;
        f3dSettings.initWindowTitle = "Hi";

        Fuel3D f3d = new Fuel3D(f3dSettings);
        Window window = f3d.getInitWindow();

        Window extra = new Window(1280, 720, "Multiple windows go brrr", f3d);

        while (!window.windowShouldClose() && !extra.windowShouldClose()) {
            window.pollEvents();
            extra.pollEvents();
        }
        window.destroy();
        extra.destroy();

        f3d.destroy();

        Fuel3D.cleanup();
    }
}
