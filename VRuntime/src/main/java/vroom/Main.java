package vroom;

import fuel3d.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException, URISyntaxException {
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

        Window mainWindow = new Window(1920, 1080, "hi", null);
        Fuel3D f3d = new Fuel3D(f3dSettings, mainWindow);

        Window extra = new Window(1280, 720, "Multiple windows go brrr", f3d);


        Shader vertShader = Shader.fromGLSLFile("C:/Users/gwch3/IdeaProjects/VroomEngine/VRuntime/src/main/resources/shaders/vert.glsl",
                Shader.ShaderType.VertexShader, f3d);
        Shader fragShader = Shader.fromGLSLFile("C:/Users/gwch3/IdeaProjects/VroomEngine/VRuntime/src/main/resources/shaders/frag.glsl",
                Shader.ShaderType.FragmentShader, f3d);

        mainWindow.visible(true);
        extra.visible(true);

        if (f3d.getDeviceNames().size() > 1) {
            System.out.println("---------------- CHANGING DEVICE ----------------");
            f3d.setDevice(1, mainWindow);
        }
        while (!mainWindow.windowShouldClose() && !extra.windowShouldClose()) {
            mainWindow.pollEvents();
            extra.pollEvents();
        }

        vertShader.destroy();
        fragShader.destroy();

        mainWindow.destroy();
        extra.destroy();

        f3d.destroy();

        Fuel3D.cleanup();
    }
}
