package fuel3d;


import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.VK10.*;

public class Framebuffer {
    private long framebuffer;
    private final Fuel3D renderer;
    private final Pipeline targetPipeline;
    private final Image image;

    public Framebuffer(Image image, Pipeline targetPipeline, Fuel3D renderer) {
        this.image = image; // TODO: multiple image attachments
        this.targetPipeline = targetPipeline;
        this.renderer = renderer;

        create();
    }

    protected void create() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer lb = stack.mallocLong(1);

            VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .renderPass(targetPipeline.getRenderpass())
                    .attachmentCount(1)
                    .pAttachments(stack.longs(image.getImageView()))
                    .width(image.getWidth())
                    .height(image.getHeight())
                    .layers(1);
            renderer.chErr(vkCreateFramebuffer(renderer.getDevice(), framebufferInfo, null, lb));
            framebuffer = lb.get(0);
        }
    }

    protected void destroyObjects() {
        vkDestroyFramebuffer(renderer.getDevice(), framebuffer, null);
    }

    private void destroy() {
        destroyObjects();
    }

    protected int getImageFormat() {
        return image.getImageFormat();
    }

    public static Framebuffer[] getFromWindow(Window window, Pipeline targetPipeline, Fuel3D renderer) {
        Framebuffer[] result = new Framebuffer[window.getSwapchainImages().length];
        for (int i = 0; i < window.getSwapchainImages().length; i++) {
            result[i] = new Framebuffer(window.getSwapchainImages()[i], targetPipeline, renderer);
        }
        return result;
    }
}
