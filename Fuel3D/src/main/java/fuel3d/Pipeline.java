package fuel3d;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public class Pipeline {
    private final Fuel3D renderer;
    private final Shader vertexShader, fragmentShader;
    private long graphicsPipeline, pipelineLayout, renderpass;
    private final int targetImageFormat;

    public Pipeline(Shader vertexShader, Shader fragmentShader, Window target, Fuel3D renderer) {
        this.renderer = renderer;
        this.vertexShader = vertexShader;
        this.fragmentShader = fragmentShader;
        this.targetImageFormat = target.getImageFormat();
        renderer.addPipeline(this);

        create();
    }

    protected void create() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            createRenderPass();

            LongBuffer lb = stack.mallocLong(1);

            VkPipelineShaderStageCreateInfo.Buffer shaderStageInfo = VkPipelineShaderStageCreateInfo.malloc(2, stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .stage(VK_SHADER_STAGE_VERTEX_BIT)
                    .module(vertexShader.getShader())
                    .pName(stack.ASCII("main"))
                    .pSpecializationInfo(null);
            (shaderStageInfo.get(1))
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(fragmentShader.getShader())
                    .pName(stack.ASCII("main"))
                    .pSpecializationInfo(null);
            VkPipelineVertexInputStateCreateInfo vertexInputStateInfo = VkPipelineVertexInputStateCreateInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .pVertexBindingDescriptions(null)
                    .pVertexAttributeDescriptions(null); // TODO: Vertex array
            VkPipelineInputAssemblyStateCreateInfo inputAssemblyStateInfo = VkPipelineInputAssemblyStateCreateInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST) // TODO: support drawing lines (?)
                    .primitiveRestartEnable(false);
            VkPipelineViewportStateCreateInfo viewportStateInfo = VkPipelineViewportStateCreateInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .pViewports(null)
                    .viewportCount(1)
                    .pScissors(null)
                    .scissorCount(1);
            VkPipelineRasterizationStateCreateInfo rasterizationStateInfo = VkPipelineRasterizationStateCreateInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .depthClampEnable(false)
                    .rasterizerDiscardEnable(false)
                    .polygonMode(VK_POLYGON_MODE_FILL)
                    .cullMode(VK_CULL_MODE_BACK_BIT)
                    .frontFace(VK_FRONT_FACE_CLOCKWISE)
                    .depthBiasEnable(false)
                    .depthBiasConstantFactor(0.0f)
                    .depthBiasClamp(0.0f)
                    .depthBiasSlopeFactor(0.0f)
                    .lineWidth(1.0f); // TODO: support drawing lines (?)
            VkPipelineMultisampleStateCreateInfo multisampleStateInfo = VkPipelineMultisampleStateCreateInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
                    .sampleShadingEnable(false) // TODO: Anti aliasing support via multisampling
                    .minSampleShading(1.0f)
                    .pSampleMask(null)
                    .alphaToCoverageEnable(false)
                    .alphaToOneEnable(false);
            // TODO: Depth testing
            VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachmentState = VkPipelineColorBlendAttachmentState.malloc(1, stack)
                    .blendEnable(true) // TODO: Proper customizable color blending
                    .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                    .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                    .colorBlendOp(VK_BLEND_OP_ADD)
                    .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                    .dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA)
                    .alphaBlendOp(VK_BLEND_OP_ADD)
                    .colorWriteMask(
                            VK_COLOR_COMPONENT_A_BIT |
                            VK_COLOR_COMPONENT_G_BIT |
                            VK_COLOR_COMPONENT_B_BIT |
                            VK_COLOR_COMPONENT_A_BIT);
            VkPipelineColorBlendStateCreateInfo colorBlendStateInfo = VkPipelineColorBlendStateCreateInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .logicOpEnable(false)
                    .logicOp(VK_LOGIC_OP_COPY)
                    .pAttachments(colorBlendAttachmentState)
                    .blendConstants(0, 0.0f)
                    .blendConstants(1, 0.0f)
                    .blendConstants(2, 0.0f)
                    .blendConstants(3, 0.0f);

            VkPipelineDynamicStateCreateInfo dynamicStateInfo = VkPipelineDynamicStateCreateInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .pDynamicStates(stack.ints(
                            VK_DYNAMIC_STATE_VIEWPORT,
                            VK_DYNAMIC_STATE_SCISSOR
                    ));
            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .pSetLayouts(null)
                    .pPushConstantRanges(null);
            renderer.chErr(vkCreatePipelineLayout(renderer.getDevice(), pipelineLayoutInfo, null, lb));
            pipelineLayout = lb.get(0);

            VkGraphicsPipelineCreateInfo.Buffer graphicsPipelineInfo = VkGraphicsPipelineCreateInfo.malloc(1, stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .pStages(shaderStageInfo)
                    .pVertexInputState(vertexInputStateInfo)
                    .pInputAssemblyState(inputAssemblyStateInfo)
                    .pTessellationState(null)
                    .pViewportState(viewportStateInfo)
                    .pRasterizationState(rasterizationStateInfo)
                    .pMultisampleState(multisampleStateInfo)
                    .pDepthStencilState(null)
                    .pColorBlendState(colorBlendStateInfo)
                    .pDynamicState(dynamicStateInfo)
                    .layout(pipelineLayout)
                    .renderPass(renderpass)
                    .subpass(0)
                    .basePipelineHandle(VK_NULL_HANDLE)
                    .basePipelineIndex(-1); // TODO: make renderer create main pipeline and derive (?)
            renderer.chErr(vkCreateGraphicsPipelines(renderer.getDevice(), VK_NULL_HANDLE, graphicsPipelineInfo, null, lb));
            graphicsPipeline = lb.get(0);
        }
    }

    private void createRenderPass() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer lb = stack.mallocLong(1);

            VkAttachmentDescription.Buffer attachmentDescription = VkAttachmentDescription.malloc(1, stack)
                    .flags(0) // TODO: Configurable number of framebuffer attachments (render targets)
                    .format(targetImageFormat)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR); // TODO: Configurable render destination (swapchain optimized for now)
            VkAttachmentReference.Buffer colorAttachmentReference = VkAttachmentReference.malloc(1, stack)
                    .attachment(0)
                    .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            VkSubpassDescription.Buffer subpassDescription = VkSubpassDescription.calloc(1, stack)
                    .flags(0) // TODO: Configurable number of subpasses (ex for deferred shading)
                    .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1)
                    .pColorAttachments(colorAttachmentReference);
            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .pAttachments(attachmentDescription)
                    .pSubpasses(subpassDescription)
                    .pDependencies(null);
            renderer.chErr(vkCreateRenderPass(renderer.getDevice(), renderPassInfo, null, lb));
            renderpass = lb.get(0);
        }
    }

    public boolean isWindowCompatible(Window window) {
        return window.getImageFormat() == targetImageFormat;
    }

    protected void destroyObjects() {
        vkDestroyPipeline(renderer.getDevice(), graphicsPipeline, null);
        vkDestroyPipelineLayout(renderer.getDevice(), pipelineLayout, null);
        vkDestroyRenderPass(renderer.getDevice(), renderpass, null);
    }

    public void destroy() {
        destroyObjects();
        renderer.removePipeline(this);
    }

}
