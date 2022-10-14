package fuel3d;

import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;

import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.VK10.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Vector;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Window {
    private long window;
    private long surface;
    private int width, height;
    private String title;
    private Logger logger;
    private Fuel3D renderer;
    private boolean isVisible = false;

    public Window(int width, int height, String title, Fuel3D renderer) {
        this.width = width;
        this.height = height;
        this.title = title;

        if (renderer != null) {
            initWindow(renderer);
            checkSupport();
        }
    }

    protected void initWindow(Fuel3D renderer) {
        this.renderer = renderer;
        this.logger = renderer.getLogger();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer lb = stack.mallocLong(1);

            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
            glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE); //TODO: Window resize support
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);


            window = glfwCreateWindow(width, height, title, NULL, NULL);
            if (window == NULL)
                logger.error("[Fuel3D] ERROR: Window creation failed!");

            renderer.chErr(glfwCreateWindowSurface(renderer.getInstance(), window, null, lb));
            surface = lb.get(0);
        }
    }

    protected void checkSupport() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer ib = stack.mallocInt(1);

            renderer.chErr(vkGetPhysicalDeviceSurfaceSupportKHR(renderer.getPhysicalDevice(), renderer.getQueueIndices().present(), surface, ib));
            if (ib.get(0) != VK_TRUE) {
                logger.error("Physical device does not support this window surface");
            }
        }
    }

    protected SurfaceInfo getSurfaceInfo(MemoryStack stack, VkPhysicalDevice physicalDevice) {
        IntBuffer ib = stack.mallocInt(1);

        VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
        renderer.chErr(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, capabilities));

        renderer.chErr(vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, ib, null));
        VkSurfaceFormatKHR.Buffer surfaceFormats = VkSurfaceFormatKHR.malloc(ib.get(0), stack);
        if (ib.get(0) > 0) {
            vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, ib, surfaceFormats);
        }

        vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, ib, null);
        IntBuffer presentModes = stack.mallocInt(ib.get(0));
        if (ib.get(0) > 0) {
            vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, ib, presentModes);
        }

        return new SurfaceInfo(capabilities, surfaceFormats, presentModes);
    }

    public void destroy() {
        vkDestroySurfaceKHR(renderer.getInstance(), surface, null);
        glfwDestroyWindow(window);
    }

    public boolean windowShouldClose() {
        return glfwWindowShouldClose(window);
    }

    public void visible(boolean visible) {
        isVisible = visible;
        if (isVisible) {
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

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getTitle() {
        return title;
    }

    protected record SurfaceInfo(VkSurfaceCapabilitiesKHR capabilities, VkSurfaceFormatKHR.Buffer formats, IntBuffer presentModes) {
        public boolean available() {
            return formats.capacity() != 0 && presentModes.capacity() != 0;
        }
    }
}