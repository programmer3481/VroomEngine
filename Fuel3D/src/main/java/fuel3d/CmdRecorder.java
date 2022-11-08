package fuel3d;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.vkCmdEndRenderPass;

public class CmdRecorder implements AutoCloseable{ // 'dummy class', does not hold objects, useful in try-with resources pattern
    private VkCommandBuffer commandBuffer;
    private Framebuffer framebuffer;
    private Pipeline pipeline;

    protected CmdRecorder() { }

    protected void start(VkCommandBuffer commandBuffer, Framebuffer framebuffer, Pipeline pipeline) {
        this.commandBuffer = commandBuffer;
        this.framebuffer = framebuffer;
        this.pipeline = pipeline;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkClearValue.Buffer clearValues = VkClearValue.malloc(1, stack);
            clearValues.color()
                    .float32(0, pipeline.getClearColor()[0])
                    .float32(1, pipeline.getClearColor()[1])
                    .float32(2, pipeline.getClearColor()[2])
                    .float32(3, pipeline.getClearColor()[3]);

            VkRenderPassBeginInfo renderPassBeginInfo = VkRenderPassBeginInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .renderPass(pipeline.getRenderpass())
                    .framebuffer(framebuffer.getFramebuffer())
                    .renderArea(vkRect2D -> vkRect2D
                            .offset(vkOffset2D -> vkOffset2D.set(0, 0))
                            .extent(vkExtent2D -> vkExtent2D.set(
                                    framebuffer.getImage().getWidth(),
                                    framebuffer.getImage().getHeight())))
                    .clearValueCount(1)
                    .pClearValues(clearValues);
            vkCmdBeginRenderPass(commandBuffer, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE);
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getPipeline());

            VkViewport.Buffer viewport = VkViewport.malloc(1, stack)
                    .x(0.0f)
                    .y(0.0f)
                    .width(framebuffer.getImage().getWidth())
                    .height(framebuffer.getImage().getHeight())
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            vkCmdSetViewport(commandBuffer, 0, viewport);

            VkRect2D.Buffer scissor = VkRect2D.malloc(1, stack)
                    .offset(vkOffset2D -> vkOffset2D.set(0, 0))
                    .extent(vkExtent2D -> vkExtent2D.set(
                            framebuffer.getImage().getWidth(),
                            framebuffer.getImage().getHeight()));
            vkCmdSetScissor(commandBuffer, 0, scissor);
        }
    }

    public void drawVertices(int vertexCount) {
        vkCmdDraw(commandBuffer, vertexCount, 1, 0, 0);
    }

    @Override
    public void close() {
        vkCmdEndRenderPass(commandBuffer);
    }
}
