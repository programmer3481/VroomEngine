package fuel3d;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Window {
    private long window;
    private long surface;
    private long swapchain;
    private int swapchainImageFormat;
    private int width, height;
    private ImageView[] swapchainImageViews;
    private String title;
    private Fuel3D renderer;
    private final boolean vSync;
    private boolean isVisible = false;

    public Window(int width, int height, String title, Fuel3D renderer, boolean vSync) {
        this.width = width;
        this.height = height;
        this.title = title;
        this.vSync = vSync;

        if (renderer != null) {
            renderer.addWindow(this);
            initWindow(renderer);
            checkSupport();
            createSwapChain();
        }
    }

    public Window(int width, int height, String title, Fuel3D renderer) {
        this(width, height, title, renderer, true);
    }

    protected void initWindow(Fuel3D renderer) {
        this.renderer = renderer;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer lb = stack.mallocLong(1);

            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
            glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE); //TODO: Window resize support
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);


            window = glfwCreateWindow(width, height, title, NULL, NULL);
            if (window == NULL)
                renderer.getLogger().error("[Fuel3D] ERROR: Window creation failed!");

            glfwSetFramebufferSizeCallback(window, (window, width, height) -> {
                this.width = width;
                this.height = height;
            });

            renderer.chErr(glfwCreateWindowSurface(renderer.getInstance(), window, null, lb));
            surface = lb.get(0);
        }
    }

    protected void checkSupport() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer ib = stack.mallocInt(1);

            renderer.chErr(vkGetPhysicalDeviceSurfaceSupportKHR(renderer.getPhysicalDevice(), renderer.getQueueIndices().present(), surface, ib));
            if (ib.get(0) != VK_TRUE) {
                renderer.getLogger().error("Physical device does not support this window surface");
            }
        }
    }

    protected void createSwapChain() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer lb = stack.mallocLong(1);
            IntBuffer ib = stack.mallocInt(1);

            SurfaceInfo surfaceInfo = querySurfaceInfo(stack, renderer.getPhysicalDevice());
            VkSurfaceFormatKHR surfaceFormat = chooseSwapchainSurfaceFormat(surfaceInfo);

            VkSwapchainCreateInfoKHR swapchainInfo = VkSwapchainCreateInfoKHR.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .surface(surface)
                    .minImageCount(chooseSwapchainImageCount(surfaceInfo))
                    .imageFormat(swapchainImageFormat = surfaceFormat.format())
                    .imageColorSpace(surfaceFormat.colorSpace())
                    .imageExtent(chooseSwapchainExtent(surfaceInfo, stack))
                    .imageArrayLayers(1)
                    .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                    .preTransform(surfaceInfo.capabilities.currentTransform())
                    .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                    .presentMode(chooseSwapchainPresentMode(surfaceInfo))
                    .clipped(true)
                    .oldSwapchain(VK_NULL_HANDLE);
            if (renderer.getQueueIndices().present() != renderer.getQueueIndices().graphics()) {
                swapchainInfo
                        .imageSharingMode(VK_SHARING_MODE_CONCURRENT)
                        .queueFamilyIndexCount(2)
                        .pQueueFamilyIndices(stack.ints(renderer.getQueueIndices().graphics(), renderer.getQueueIndices().present()));
            }
            else {
                swapchainInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
            }
            renderer.chErr(vkCreateSwapchainKHR(renderer.getDevice(), swapchainInfo, null, lb));
            swapchain = lb.get(0);

            renderer.chErr(vkGetSwapchainImagesKHR(renderer.getDevice(), swapchain, ib, null));
            LongBuffer swapchainImages = stack.mallocLong(ib.get(0));
            renderer.chErr(vkGetSwapchainImagesKHR(renderer.getDevice(), swapchain, ib, swapchainImages));

            swapchainImageViews = createImageViews(swapchainImages, stack);
        }
    }

    private ImageView[] createImageViews(LongBuffer images, MemoryStack stack) {
        LongBuffer lb = stack.mallocLong(1);

        ImageView[] result = new ImageView[images.capacity()];
        for (int i = 0; i < images.capacity(); i++) {
            VkImageViewCreateInfo imageViewInfo = VkImageViewCreateInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .image(images.get(i))
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(swapchainImageFormat)
                    .components(vkComponentMapping -> vkComponentMapping
                            .r(VK_COMPONENT_SWIZZLE_IDENTITY)
                            .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                            .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                            .a(VK_COMPONENT_SWIZZLE_IDENTITY))
                    .subresourceRange(vkImageSubresourceRange -> vkImageSubresourceRange
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .baseMipLevel(0)
                            .levelCount(1)
                            .baseArrayLayer(0)
                            .layerCount(1));
            renderer.chErr(vkCreateImageView(renderer.getDevice(), imageViewInfo, null, lb));
            result[i] = new ImageView(images.get(i), lb.get(0));
        }
        return result;
    }

    private VkSurfaceFormatKHR chooseSwapchainSurfaceFormat(SurfaceInfo info) {
        for (VkSurfaceFormatKHR format : info.formats) {
            if (format.format() == VK_FORMAT_B8G8R8A8_SRGB && format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return format;
            }
        }
        return info.formats.get(0);
    }

    private int chooseSwapchainPresentMode(SurfaceInfo info) {
        if (!vSync) {
            for (int i = 0; i < info.presentModes.capacity(); i++) {
                if (info.presentModes.get(i) == VK_PRESENT_MODE_IMMEDIATE_KHR) {
                    return VK_PRESENT_MODE_IMMEDIATE_KHR;
                }
            }
        }
        for (int i = 0; i < info.presentModes.capacity(); i++) {
            if (info.presentModes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR) {
                return VK_PRESENT_MODE_MAILBOX_KHR;
            }
        }
        return VK_PRESENT_MODE_FIFO_KHR;
    }

    private VkExtent2D chooseSwapchainExtent(SurfaceInfo info, MemoryStack stack) {
        if (info.capabilities.currentExtent().width() != 0xFFFFFFFF) {
            width = info.capabilities.currentExtent().width();
            height = info.capabilities.currentExtent().height();
            return info.capabilities.currentExtent();
        }
        else {
            VkExtent2D extent = VkExtent2D.malloc(stack)
                    .width(width)
                    .height(height);
            extent.width(Math.max(
                    info.capabilities.minImageExtent().width(),
                    Math.min(info.capabilities.maxImageExtent().width(), extent.width())));
            extent.height(Math.max(
                    info.capabilities.minImageExtent().height(),
                    Math.min(info.capabilities.maxImageExtent().height(), extent.height())));
            return extent;
        }
    }

    private int chooseSwapchainImageCount(SurfaceInfo info) {
        if (info.capabilities.maxImageCount() == 0) {
            return info.capabilities.minImageCount() + 1;
        }
        else {
            return Math.min(info.capabilities.minImageCount() + 1, info.capabilities.maxImageCount());
        }
    }

    protected SurfaceInfo querySurfaceInfo(MemoryStack stack, VkPhysicalDevice physicalDevice) {
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

    protected void destroySwapchain() {
        for (ImageView swapchainImageView : swapchainImageViews) {
            vkDestroyImageView(renderer.getDevice(), swapchainImageView.imageview, null);
        }
        vkDestroySwapchainKHR(renderer.getDevice(), swapchain, null);
    }

    public void destroy() {
        destroyObjects();
        renderer.removeWindow(this);
    }

    protected void destroyObjects() {
        destroySwapchain();
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

    public void setTitle(String title) {
        glfwSetWindowTitle(window, title);
        this.title = title;
    }

    public boolean vSync() {
        return vSync;
    }

    private record ImageView(long image, long imageview) {}

    protected record SurfaceInfo(VkSurfaceCapabilitiesKHR capabilities, VkSurfaceFormatKHR.Buffer formats, IntBuffer presentModes) {
        public boolean available() {
            return formats.capacity() != 0 && presentModes.capacity() != 0;
        }
    }
}