package vroom;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.Wincon;
import com.sun.jna.ptr.IntByReference;
import fuel3d.*;

import java.io.IOException;

import static com.sun.jna.platform.win32.Wincon.ENABLE_VIRTUAL_TERMINAL_PROCESSING;

public class Main {
    public static void main(String[] args) throws IOException {
        if (System.getProperty("os.name").startsWith("Windows")) {
            Wincon wincon = Native.load("kernel32", Kernel32.class);
            WinNT.HANDLE hOut = wincon.GetStdHandle(Wincon.STD_OUTPUT_HANDLE);
            IntByReference dwMode = new IntByReference();
            wincon.GetConsoleMode(hOut, dwMode);
            dwMode.setValue(dwMode.getValue() | ENABLE_VIRTUAL_TERMINAL_PROCESSING);
            wincon.SetConsoleMode(hOut, dwMode.getValue());
        }

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
                case WARNING -> type = " \033[33mWARNING:\033[1m";
                case ERROR -> type = " \033[31mERROR:\033[1m";
                case MISC -> type = "";
            }
            System.err.format("[\033[32m\033[1mFuel3D\033[0m]%s %s\033[0m", type, message);
            System.out.println();
        };
        loggerSettings.errorString =
                message -> String.format("\n[\033[32m\033[1mFuel3D\033[0m] \033[31mERROR:\033[1m %s\033[0m", message);

        Logger logger = new Logger(loggerSettings);

        Fuel3D.Settings f3dSettings = new Fuel3D.Settings();
        f3dSettings.enableDebug(debugger);
        f3dSettings.logger = logger;

        Window mainWindow = new Window(1920, 1080, "hi", null, true);
        Fuel3D f3d = new Fuel3D(f3dSettings, mainWindow);

        //Window extra = new Window(1280, 720, "Multiple windows go brrr", f3d);


        Shader vertShader = Shader.fromGLSLFile("C:/Users/gwch3/IdeaProjects/VroomEngine/VRuntime/src/main/resources/shaders/vert.glsl",
                Shader.ShaderType.VertexShader, f3d);
        Shader fragShader = Shader.fromGLSLFile("C:/Users/gwch3/IdeaProjects/VroomEngine/VRuntime/src/main/resources/shaders/frag.glsl",
                Shader.ShaderType.FragmentShader, f3d);

        Pipeline pipeline = new Pipeline(vertShader, fragShader, mainWindow, f3d);

        Framebuffer.WindowFramebuffer framebuffer = new Framebuffer.WindowFramebuffer(mainWindow, pipeline, f3d);

        mainWindow.visible(true);
        //extra.visible(true);

        while (!mainWindow.windowShouldClose()) {// && !extra.windowShouldClose()) {
            mainWindow.pollEvents();
            f3d.render(framebuffer, pipeline);
            //extra.pollEvents();
        }

        f3d.destroy();

        Fuel3D.cleanup();
    }
}
