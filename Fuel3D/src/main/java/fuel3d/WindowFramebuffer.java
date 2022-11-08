package fuel3d;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.KHRSwapchain.vkAcquireNextImageKHR;
import static org.lwjgl.vulkan.VK10.*;

public class WindowFramebuffer {
    private final Fuel3D renderer;
    private final Framebuffer[] framebuffers;
    private final Window window;
    private boolean nextImageRequested = false;
    private long imageAcquisitionSemaphore;
    private int imageIndex;

    public WindowFramebuffer(Window window, Pipeline targetPipeline, Fuel3D renderer) {
        this.renderer = renderer;
        this.window = window;
        framebuffers = new Framebuffer[window.getSwapchainImages().length];
        for (int i = 0; i < window.getSwapchainImages().length; i++) {
            framebuffers[i] = new Framebuffer(window.getSwapchainImages()[i], targetPipeline, renderer);
        }
        renderer.addWindowFramebuffer(this);

        create();
    }

    protected void create() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer lb = stack.mallocLong(1);

            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0);
            renderer.chErr(vkCreateSemaphore(renderer.getDevice(), semaphoreInfo, null, lb));
            this.imageAcquisitionSemaphore = lb.get(0);
        }
    }

    protected int requestNextImage() {
        if (!nextImageRequested) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer ib = stack.mallocInt(1);

                vkAcquireNextImageKHR(renderer.getDevice(), window.getSwapchain(), Long.MAX_VALUE, imageAcquisitionSemaphore, VK_NULL_HANDLE, ib);
                imageIndex = ib.get(0);
                nextImageRequested = true;
            }
        }
        return imageIndex;
    }

    protected int getImageIndex() {
        return imageIndex;
    }

    public void destroy() {
        destroyObjects();
        for (Framebuffer framebuffer : framebuffers) {
            framebuffer.destroy();
        }
        renderer.removeWindowFramebuffer(this);
    }

    protected void destroyObjects() {
        vkDestroySemaphore(renderer.getDevice(), imageAcquisitionSemaphore, null);
    }

    protected Framebuffer[] getFramebuffers() {
        return framebuffers;
    }

    protected boolean isNextImageRequested() {
        return nextImageRequested;
    }

    protected void nextImageUsed() {
        this.nextImageRequested = false;
    }

    protected long getImageAcquisitionSemaphore() {
        return imageAcquisitionSemaphore;
    }

    public Window getWindow() {
        return window;
    }
}
