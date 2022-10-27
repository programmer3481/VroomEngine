package fuel3d;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkImageViewCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.VK10.*;

public class Image {
    private final Fuel3D renderer;
    private final long image;
    private long imageView;
    private final int imageFormat;
    private final boolean userCreated;
    // data = null if not userCreated

    private Image(long image, int imageFormat, boolean userCreated, Fuel3D renderer) {
        this.image = image;
        this.imageFormat = imageFormat;
        this.userCreated = userCreated;
        this.renderer = renderer;
        if (userCreated) renderer.addImage(this);

        create();
    }

    protected void create() {
        // TODO: If userCreated then create the image here
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer lb = stack.mallocLong(1);
            VkImageViewCreateInfo imageViewInfo = VkImageViewCreateInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .image(image)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(imageFormat)
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
            imageView = lb.get(0);
        }
    }

    public void destroy() {
        destroyObjects();
        renderer.removeImage(this);
    }

    public void destroyObjects() {
        vkDestroyImageView(renderer.getDevice(), imageView, null);
        if (userCreated) {
            vkDestroyImage(renderer.getDevice(), image, null);
        }
    }

    protected static Image[] fromVkImages(LongBuffer images, int format, Fuel3D renderer) {
        Image[] result = new Image[images.capacity()];
        for (int i = 0; i < images.capacity(); i++) {
            result[i] = new Image(images.get(i), format, false, renderer);
        }
        return result;
    }
}
