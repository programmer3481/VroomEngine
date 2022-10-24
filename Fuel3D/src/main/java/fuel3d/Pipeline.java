package fuel3d;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.VK10.*;

public class Pipeline {
    private final Fuel3D renderer;
    private final Shader vertexShader, fragmentShader;

    public Pipeline(Shader vertexShader, Shader fragmentShader, Fuel3D renderer) {
        this.renderer = renderer;
        this.vertexShader = vertexShader;
        this.fragmentShader = fragmentShader;
        renderer.addPipeline(this);

        create();
    }

    protected void create() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineShaderStageCreateInfo vertShaderStageInfo = VkPipelineShaderStageCreateInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .stage(VK_SHADER_STAGE_VERTEX_BIT)
                    .pName(stack.ASCII("main"))
                    .pSpecializationInfo(null);
            VkPipelineShaderStageCreateInfo fragShaderStageInfo = VkPipelineShaderStageCreateInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .pName(stack.ASCII("main"))
                    .pSpecializationInfo(null);

        }
    }

    public void destroy() {

    }

}
