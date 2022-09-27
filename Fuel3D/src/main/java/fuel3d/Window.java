package fuel3d;

import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.VK10.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Window {
    protected long window;
    protected long surface;
    protected Vector2i size;
    protected String title;
    protected Logger logger;
    protected Fuel3D renderer;
    private boolean isVisible = false;

    public Window(int width, int height, String title, Fuel3D renderer) {
        this.size = new Vector2i(width, height);
        this.title = title;
        this.logger = renderer.getLogger();
        this.renderer = renderer;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer lb = stack.mallocLong(1);
            IntBuffer ib = stack.mallocInt(1);

            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
            glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE); //TODO: Window resize support
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);


            window = glfwCreateWindow(size.x, size.y, title, NULL, NULL);
            if (window == NULL)
                logger.error("[Fuel3D] ERROR: Window creation failed!");

            renderer.chErr(glfwCreateWindowSurface(renderer.getInstance(), window, null, lb));
            surface = lb.get(0);

            renderer.chErr(vkGetPhysicalDeviceSurfaceSupportKHR(renderer.getPhysicalDevice(), renderer.getQueueIndices().present(), surface, ib));
            if (ib.get(0) != VK_TRUE) {
                logger.error("Physical device does not support this window surface");
            }
        }
    }

    //needed for custom constructor in Fuel3D
    protected Window() {}

    public void destroy() {
        vkDestroySurfaceKHR(renderer.getInstance(), surface, null);
        glfwDestroyWindow(window);
    }

    public boolean windowShouldClose() {
        return glfwWindowShouldClose(window);
    }

    public void visible(boolean visible) {
        isVisible = visible;
        if (isVisible == true) {
            glfwShowWindow(window);
        }
        else {
            glfwHideWindow(window);
        }
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void pollEvents() {
        glfwPollEvents();
    }

    protected long getSurface() {
        return surface;
    }

    public Vector2ic getSize() {
        return size;
    }

    public String getTitle() {
        return title;
    }
}