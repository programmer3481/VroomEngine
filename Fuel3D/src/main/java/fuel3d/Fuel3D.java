package fuel3d;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import fuel3d.Window.SurfaceInfo;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.KHRWin32Surface.VK_KHR_WIN32_SURFACE_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRWin32Surface.vkGetPhysicalDeviceWin32PresentationSupportKHR;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.glfw.GLFWVulkan.*;
import static fuel3d.Logger.MessageType;

public class Fuel3D { // Contains the instance, logical and physical device, and command buffers
    private final VkInstance instance;
    private VkPhysicalDevice physicalDevice = null;
    private List<String> deviceNameList;
    private VkDevice device = null;
    private AvailableQueueFamilyIndices queueIndices;
    private VkQueue graphicsQueue = null;
    private VkQueue presentQueue = null;
    private long commandPool;
    private VkCommandBuffer commandBuffer;
    private long imageAvailableSemaphore, renderFinishedSemaphore, inFlightFence;
    private List<VkPhysicalDevice> physicalDevices;
    private long debugMessenger;
    private String platform;

    private final Debugger debugger;
    private final Logger logger;

    private final List<Window> windows = new ArrayList<>();
    private final List<Shader> shaders = new ArrayList<>();
    private final List<Pipeline> pipelines = new ArrayList<>();
    private final List<Image> images = new ArrayList<>();
    private final List<Framebuffer> framebuffers = new ArrayList<>();

    private final boolean validate; // 'Debug mode'
    private final String appName, engineName;
    private final Version appVersion, engineVersion;

    private final String[] instanceExtensionList = new String[0]; // Empty for now;
    private final String[] deviceExtensionList = new String[] {
            VK_KHR_SWAPCHAIN_EXTENSION_NAME
    };

    public static final Version VERSION = new Version(1, 0, 0);
    public static final String NAME = "Fuel3D";

    public Fuel3D(Settings settings, Window initWindow) {
        validate = settings.validate;
        debugger = settings.debugger;
        logger = settings.logger;
        appName = settings.appName;
        appVersion = settings.appVersion;
        engineName = settings.engineName;
        engineVersion = settings.engineVersion;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer ib = stack.mallocInt(1);
            LongBuffer lb = stack.mallocLong(1);

            PointerBuffer layers = null, instanceExtensions = null;

            //region create VkInstance

            // Getting validation layers
            if (validate) {
                chErr(vkEnumerateInstanceLayerProperties(ib, null));
                if (ib.get(0) > 0) {
                    VkLayerProperties.Buffer availableLayers = VkLayerProperties.malloc(ib.get(0), stack);
                    chErr(vkEnumerateInstanceLayerProperties(ib, availableLayers));

                    for (String[] layerSet : debugger.getValidationLayerSets()) {
                        boolean allFound = true;

                        for (String layer : layerSet) {
                            boolean found = false;
                            for (VkLayerProperties availableLayer : availableLayers) {
                                if (layer.equals(availableLayer.layerNameString())) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                allFound = false;
                                logger.log(MessageType.WARNING, "Cannot find layer: " + layer);
                                break;
                            }
                        }

                        if (allFound) {
                            layers = stack.mallocPointer(layerSet.length);
                            for (String s : layerSet) {
                                layers.put(stack.ASCII(s));
                            }
                            layers.flip();
                            break;
                        }
                        logger.log(MessageType.INFO, "Trying alternative layer set...");
                    }
                }

                if (layers == null)
                    logger.error("Failed to find required validation layers");
            }

            // Getting instance extensions
            PointerBuffer reqInstanceExtensions = queryReqInstanceExtensions(stack);
            chErr(vkEnumerateInstanceExtensionProperties((String)null, ib, null));
            if (ib.get(0) > 0) {
                VkExtensionProperties.Buffer availableInstanceExtensions = VkExtensionProperties.malloc(ib.get(0), stack);
                chErr(vkEnumerateInstanceExtensionProperties((String)null, ib, availableInstanceExtensions));

                boolean allFound = true;
                for (int i = 0; i < reqInstanceExtensions.capacity(); i++) {
                    boolean found = false;
                    for (VkExtensionProperties availableInstanceExtension : availableInstanceExtensions) {
                        if (reqInstanceExtensions.getStringASCII(i).equals(availableInstanceExtension.extensionNameString())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        allFound = false;
                        logger.log(MessageType.ERROR,"Cannot find instance extension: " + reqInstanceExtensions.getStringASCII(i));
                        break;
                    }
                }
                if (allFound) {
                    instanceExtensions = stack.mallocPointer(reqInstanceExtensions.capacity());
                    instanceExtensions.put(reqInstanceExtensions);
                    instanceExtensions.flip();
                }
            }

            if (instanceExtensions == null)
                logger.error("failed to find required instance extensions");

            VkApplicationInfo appInfo = VkApplicationInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .pApplicationName(stack.UTF8(appName))
                    .applicationVersion(VK_MAKE_VERSION(appVersion.major, appVersion.minor, appVersion.patch))
                    .pEngineName(stack.UTF8(engineName))
                    .engineVersion(VK_MAKE_VERSION(engineVersion.major, engineVersion.minor, engineVersion.patch))
                    .apiVersion(VK.getInstanceVersionSupported())
                    .apiVersion(VK_API_VERSION_1_2);
            VkInstanceCreateInfo instanceInfo = VkInstanceCreateInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .pApplicationInfo(appInfo)
                    .ppEnabledLayerNames(layers)
                    .ppEnabledExtensionNames(instanceExtensions);

            VkDebugUtilsMessengerCreateInfoEXT debugInfo = null;
            if (validate) {
                debugInfo = VkDebugUtilsMessengerCreateInfoEXT.malloc(stack)
                        .sType$Default()
                        .pNext(NULL)
                        .flags(0)
                        .messageSeverity(
                                VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT |
                                VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT |
                                VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT |
                                VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT
                        )
                        .messageType(
                                VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
                                VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
                                VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT
                        )
                        .pfnUserCallback(VkDebugUtilsMessengerCallbackEXT.create(
                                (messageSeverity, messageTypes, pCallbackData, pUserData) -> {
                                    MessageType type;
                                    if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT) != 0)
                                        type = MessageType.VERBOSE;
                                    else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT) != 0)
                                        type = MessageType.INFO;
                                    else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0)
                                        type = MessageType.WARNING;
                                    else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0)
                                        type = MessageType.ERROR;
                                    else
                                        type = MessageType.MISC;

                                    VkDebugUtilsMessengerCallbackDataEXT data = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
                                    logger.log(type, data.pMessageString());
                                    return VK_FALSE;
                                }
                        ))
                        .pUserData(NULL);
                instanceInfo.pNext(debugInfo);
            }

            PointerBuffer pb = stack.mallocPointer(1);
            int code = vkCreateInstance(instanceInfo, null, pb);
            switch (code) {
                case VK_SUCCESS:
                    break;
                case VK_ERROR_INCOMPATIBLE_DRIVER:
                    logger.error("Cannot find compatible vulkan driver");
                case VK_ERROR_EXTENSION_NOT_PRESENT:
                    logger.error("Cannot find specified extensions");
                default:
                    chErr(code);
            }

            instance = new VkInstance(pb.get(0), instanceInfo);

            if (validate) {
                code = vkCreateDebugUtilsMessengerEXT(instance, debugInfo, null, lb);
                switch (code) {
                    case VK_SUCCESS -> debugMessenger = lb.get(0);
                    case VK_ERROR_OUT_OF_HOST_MEMORY ->
                            logger.error("CreateDebugUtilsMessenger out of host memory");
                    default ->
                            chErr(code);
                }
            }
            //endregion

            // Find surface type (used for determining what function to use for checking physical device queue surface support)
            PointerBuffer glfwReqExtensions = glfwGetRequiredInstanceExtensions();
            if (glfwReqExtensions == null) {
                logger.error("glfwGetRequiredInstanceExtensions() failed to find the required instance extensions");
            }
            for (int i = 0; i < glfwReqExtensions.capacity(); i++) {
                if (glfwReqExtensions.getStringASCII(i).equals(VK_KHR_WIN32_SURFACE_EXTENSION_NAME)) {
                    platform = "Windows";
                    logger.log(MessageType.INFO, "Windows platform detected");
                    break;
                }
            }

            addWindow(initWindow);
            //create initWindow surface
            initWindow.initWindow(this);

            pickPhysicalDevice(initWindow);
            createLogicalDevice(initWindow);
            createCommandBuffer();

            initWindow.checkSupport();
            initWindow.createSwapChain();
        }
    }

    public void destroy() {
        vkWaitForFences(device, inFlightFence, true, Long.MAX_VALUE);
        vkDestroySemaphore(device, renderFinishedSemaphore, null);
        vkDestroySemaphore(device, imageAvailableSemaphore, null);
        vkDestroyFence(device, inFlightFence, null);

        vkDestroyCommandPool(device, commandPool, null);

        logger.log(MessageType.INFO, "Cleaning up");
        for (Framebuffer framebuffer: framebuffers) {
            framebuffer.destroyObjects();
        }
        framebuffers.clear();

        // destroy all images
        for (Image image : images) {
            image.destroyObjects();
        }
        images.clear();

        // destroy all pipelines
        for (Pipeline pipeline : pipelines) {
            pipeline.destroyObjects();
        }
        pipelines.clear();

        // destroy all shaders
        for (Shader shader : shaders) {
            shader.destroyObjects();
        }
        shaders.clear();

        // destroy all windows' swap chains
        for (Window window : windows) {
            window.destroyObjects();
        }
        windows.clear();

        vkDestroyDevice(device, null);
        if (validate) {
            vkDestroyDebugUtilsMessengerEXT(instance, debugMessenger, null);
        }
        vkDestroyInstance(instance, null);
    }

    private void createCommandBuffer() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer lb = stack.mallocLong(1);
            PointerBuffer pb = stack.mallocPointer(1);

            VkCommandPoolCreateInfo commandPoolInfo = VkCommandPoolCreateInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                    .queueFamilyIndex(queueIndices.graphics);
            chErr(vkCreateCommandPool(device, commandPoolInfo, null, lb));
            commandPool = lb.get(0);

            VkCommandBufferAllocateInfo commandBufferInfo = VkCommandBufferAllocateInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .commandPool(commandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);
            chErr(vkAllocateCommandBuffers(device, commandBufferInfo, pb));
            commandBuffer = new VkCommandBuffer(pb.get(0), device);

            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0);
            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(VK_FENCE_CREATE_SIGNALED_BIT);
            chErr(vkCreateSemaphore(device, semaphoreInfo,null, lb));
            imageAvailableSemaphore = lb.get(0);
            chErr(vkCreateSemaphore(device, semaphoreInfo,null, lb));
            renderFinishedSemaphore = lb.get(0);
            chErr(vkCreateFence(device, fenceInfo, null, lb));
            inFlightFence = lb.get(0);
        }
    }

    private void createLogicalDevice(Window testWindow) { // Also sets graphicsQueue
        try (MemoryStack stack = MemoryStack.stackPush()) {
            queueIndices = queryQueueFamilyIndices(stack, physicalDevice, testWindow);

            VkDeviceQueueCreateInfo.Buffer deviceQueueInfo = VkDeviceQueueCreateInfo.malloc(queueIndices.graphics == queueIndices.present ? 1 : 2, stack);
            (deviceQueueInfo.get(0))
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .queueFamilyIndex(queueIndices.graphics)
                    .pQueuePriorities(stack.floats(1.0f));
            if (queueIndices.graphics != queueIndices.present) {
                (deviceQueueInfo.get(1))
                        .sType$Default()
                        .pNext(NULL)
                        .flags(0)
                        .queueFamilyIndex(queueIndices.present)
                        .pQueuePriorities(stack.floats(1.0f));
            }

            VkPhysicalDeviceFeatures physicalDeviceFeatures = VkPhysicalDeviceFeatures.calloc(stack);

            PointerBuffer deviceExtensions = stack.mallocPointer(deviceExtensionList.length);
            for (int i = 0; i < deviceExtensions.capacity(); i++) {
                deviceExtensions.put(stack.ASCII(deviceExtensionList[i]));
            }
            deviceExtensions.flip();
            VkDeviceCreateInfo deviceInfo = VkDeviceCreateInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .pQueueCreateInfos(deviceQueueInfo)
                    .ppEnabledLayerNames(null)
                    .ppEnabledExtensionNames(deviceExtensions)
                    .pEnabledFeatures(physicalDeviceFeatures);

            PointerBuffer pb = stack.mallocPointer(1);
            chErr(vkCreateDevice(physicalDevice, deviceInfo, null, pb));
            device = new VkDevice(pb.get(0), physicalDevice, deviceInfo);

            vkGetDeviceQueue(device, queueIndices.graphics, 0, pb);
            graphicsQueue = new VkQueue(pb.get(0), device);
            vkGetDeviceQueue(device, queueIndices.present, 0, pb);
            presentQueue = new VkQueue(pb.get(0),device);

        }
    }

    private void pickPhysicalDevice(Window testWindow) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer ib = stack.mallocInt(1);

            chErr(vkEnumeratePhysicalDevices(instance, ib, null));
            if (ib.get(0) > 0) {
                PointerBuffer availablePhysicalDevices = stack.mallocPointer(ib.get(0));
                chErr(vkEnumeratePhysicalDevices(instance, ib, availablePhysicalDevices));

                VkPhysicalDeviceProperties.Buffer availablePhysicalDevicesProperties = VkPhysicalDeviceProperties.malloc(ib.get(0), stack);
                physicalDevices = new ArrayList<>(ib.get(0));
                deviceNameList = new ArrayList<>(ib.get(0));

                for (int i = 0; i < availablePhysicalDevices.capacity(); i++) {
                    VkPhysicalDevice testDevice = new VkPhysicalDevice(availablePhysicalDevices.get(i), instance);
                    vkGetPhysicalDeviceProperties(testDevice, availablePhysicalDevicesProperties.get(i));

                    if (isDeviceSupported(testDevice, availablePhysicalDevicesProperties.get(i), testWindow)) {
                        physicalDevices.add(testDevice);
                        deviceNameList.add(availablePhysicalDevicesProperties.get(i).deviceNameString());
                    }
                }
                if (physicalDevices.size() > 0){
                    physicalDevice = physicalDevices.get(0); // Use the first one
                    logger.log(MessageType.INFO, "Using GPU: " + availablePhysicalDevicesProperties.get(0).deviceNameString());
                }
            }
            if (physicalDevice == null) {
                logger.error("Failed to find GPU with vulkan support");
            }
        }
    }

    private boolean isDeviceSupported(VkPhysicalDevice physicalDevice, VkPhysicalDeviceProperties properties, Window testWindow) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer ib = stack.mallocInt(1);

            // Check extension support
            chErr(vkEnumerateDeviceExtensionProperties(physicalDevice, (String)null, ib, null));
            if (ib.get(0) > 0) {
                VkExtensionProperties.Buffer availableDeviceExtensions = VkExtensionProperties.malloc(ib.get(0), stack);
                chErr(vkEnumerateDeviceExtensionProperties(physicalDevice, (String)null, ib, availableDeviceExtensions));

                for (String extension : deviceExtensionList) {
                    boolean found = false;
                    for (VkExtensionProperties availableDeviceExtension : availableDeviceExtensions) {
                        if (extension.equals(availableDeviceExtension.extensionNameString())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        logger.log(MessageType.INFO,
                                "Cannot find instance extension " + extension +
                                " on device " + properties.deviceNameString() + ", skipping");
                        return false;
                    }
                }
            }

            SurfaceInfo sInfo = testWindow.querySurfaceInfo(stack, physicalDevice);
            if (!sInfo.available()) {
                return false;
            }

            AvailableQueueFamilyIndices indices = queryQueueFamilyIndices(stack, physicalDevice, testWindow);
            return indices.allAvailable();
        }
    }

    private AvailableQueueFamilyIndices queryQueueFamilyIndices(MemoryStack stack, VkPhysicalDevice physicalDevice, Window testWindow) {
        IntBuffer ib = stack.mallocInt(1);

        int graphicsQueue = -1;
        int presentQueue = -1;

        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, ib, null);
        if (ib.get(0) > 0) {
            VkQueueFamilyProperties.Buffer availableQueueFamilies = VkQueueFamilyProperties.malloc(ib.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, ib, availableQueueFamilies);

            for (int i = 0; i < availableQueueFamilies.capacity(); i++) {
                boolean bothAvailable = true;

                if ((availableQueueFamilies.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                    if (graphicsQueue < 0) {
                        graphicsQueue = i;
                    }
                }
                else {
                    bothAvailable = false;
                }


                chErr(vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice, i, testWindow.getSurface(), ib));
                if (ib.get(0) == VK_TRUE && (!platform.equals("Windows") || vkGetPhysicalDeviceWin32PresentationSupportKHR(physicalDevice, i))) {
                    if (presentQueue < 0) {
                        presentQueue = i;
                    }
                }
                else {
                    bothAvailable = false;
                }

                if (bothAvailable) {
                    graphicsQueue = i;
                    presentQueue = i;
                    break;
                }
            }
        }

        return new AvailableQueueFamilyIndices(graphicsQueue, presentQueue);
    }

    private PointerBuffer queryReqInstanceExtensions(MemoryStack stack) {
        PointerBuffer glfwReqExtensions = glfwGetRequiredInstanceExtensions();
        if (glfwReqExtensions == null)
            logger.error("glfwGetRequiredInstanceExtensions failed to find the platform surface extensions");
        int extraExtensionCount = instanceExtensionList.length;
        if (validate) extraExtensionCount++; // debug utils extension
        assert glfwReqExtensions != null;
        PointerBuffer reqExtensions = stack.mallocPointer(glfwReqExtensions.capacity() + extraExtensionCount);
        reqExtensions.put(glfwReqExtensions);
        for (String s : instanceExtensionList) {
            reqExtensions.put(stack.ASCII(s));
        }
        if (validate) reqExtensions.put(stack.ASCII(VK_EXT_DEBUG_UTILS_EXTENSION_NAME));
        reqExtensions.flip();
        return reqExtensions;
    }

    protected void chErr(int code) { // return code error checking function
        if (code != 0 && code != 5) logger.error(String.format("Vulkan error [0x%X]", code));
    }

    public void render(Framebuffer.WindowFramebuffer framebuffer, Pipeline pipeline) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer ib = stack.mallocInt(1);

            vkWaitForFences(device, inFlightFence, true, Long.MAX_VALUE);
            vkResetFences(device, inFlightFence);

            vkAcquireNextImageKHR(device, framebuffer.getWindow().getSwapchain(), Long.MAX_VALUE, imageAvailableSemaphore, VK_NULL_HANDLE, ib);
            int imageIndex = ib.get(0);

            // region record commandBuffer
            vkResetCommandBuffer(commandBuffer, 0);
            VkCommandBufferBeginInfo commandBufferBeginInfo = VkCommandBufferBeginInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .pInheritanceInfo(null);
            chErr(vkBeginCommandBuffer(commandBuffer, commandBufferBeginInfo));

            VkClearValue.Buffer clearValues = VkClearValue.malloc(1, stack);
            clearValues.color()
                    .float32(0, 0.0f)
                    .float32(1, 0.0f)
                    .float32(2, 0.0f)
                    .float32(3, 1.0f);

            VkRenderPassBeginInfo renderPassBeginInfo = VkRenderPassBeginInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .renderPass(pipeline.getRenderpass())
                    .framebuffer(framebuffer.getFramebuffers()[imageIndex].getFramebuffer())
                    .renderArea(vkRect2D -> vkRect2D
                            .offset(vkOffset2D -> vkOffset2D.set(0, 0))
                            .extent(vkExtent2D -> vkExtent2D.set(
                                    framebuffer.getFramebuffers()[imageIndex].getImage().getWidth(),
                                    framebuffer.getFramebuffers()[imageIndex].getImage().getHeight())))
                    .clearValueCount(1)
                    .pClearValues(clearValues);
            vkCmdBeginRenderPass(commandBuffer, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE);
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getPipeline());

            VkViewport.Buffer viewport = VkViewport.malloc(1, stack)
                    .x(0.0f)
                    .y(0.0f)
                    .width(framebuffer.getFramebuffers()[imageIndex].getImage().getWidth())
                    .height(framebuffer.getFramebuffers()[imageIndex].getImage().getHeight())
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            vkCmdSetViewport(commandBuffer, 0, viewport);

            VkRect2D.Buffer scissor = VkRect2D.malloc(1, stack)
                    .offset(vkOffset2D -> vkOffset2D.set(0, 0))
                    .extent(vkExtent2D -> vkExtent2D.set(
                            framebuffer.getFramebuffers()[imageIndex].getImage().getWidth(),
                            framebuffer.getFramebuffers()[imageIndex].getImage().getHeight()));
            vkCmdSetScissor(commandBuffer, 0, scissor);

            vkCmdDraw(commandBuffer, 3, 1, 0, 0); // TODO: VERTEX BUFFERS

            vkCmdEndRenderPass(commandBuffer);
            chErr(vkEndCommandBuffer(commandBuffer));
            //endregion

            VkSubmitInfo submitInfo = VkSubmitInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .waitSemaphoreCount(1)
                    .pWaitSemaphores(stack.longs(imageAvailableSemaphore))
                    .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)) // TODO: more attachments
                    .pCommandBuffers(stack.pointers(commandBuffer))
                    .pSignalSemaphores(stack.longs(renderFinishedSemaphore));
            chErr(vkQueueSubmit(graphicsQueue, submitInfo, inFlightFence));

            //region presentation
            VkPresentInfoKHR presentInfo = VkPresentInfoKHR.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .pWaitSemaphores(stack.longs(renderFinishedSemaphore))
                    .swapchainCount(1)
                    .pSwapchains(stack.longs(framebuffer.getWindow().getSwapchain()))
                    .pImageIndices(stack.ints(imageIndex))
                    .pResults(null);
            vkQueuePresentKHR(presentQueue, presentInfo);
            //endregion presentation
        }
    }


    //region getters and setters
    public List<String> getDeviceNames() {
        return deviceNameList;
    }

    /* // TODO: reimplement it properly!!
    public void setDevice(int index, Window testWindow) {
        physicalDevice = physicalDevices.get(index);
        logger.log(MessageType.INFO, "Using GPU: " + deviceNameList.get(index));

        // destroy all images
        for (Image image : images) {
            image.destroy();
        }

        // destroy all pipelines
        for (Pipeline pipeline : pipelines) {
            pipeline.destroyObjects();
        }

        // destroy all shaders
        for (Shader shader : shaders) {
            shader.destroyObjects();
        }

        // destroy all windows' swap chains
        for (Window window : windows) {
            window.checkSupport();
            window.destroySwapchain();
        }

        // recreate logical device
        vkDestroyDevice(device, null);
        createLogicalDevice(testWindow);

        // recreate all windows' swap chains
        for (Window window : windows) {
            window.createSwapChain();
        }

        //recompile all shaders
        for (Shader shader : shaders) {
            shader.create();
        }

        // recreate all pipelines
        for (Pipeline pipeline : pipelines) {
            pipeline.create();
        }

        // recreate all images
        for (Image image : images) {
            image.create();
        }
    }
    */

    public Logger getLogger() {
        return logger;
    }

    public Debugger getDebugger() {
        return debugger;
    }

    public Version getAppVersion() {
        return appVersion;
    }

    public boolean validationEnabled() {
        return validate;
    }

    public String getAppName() {
        return appName;
    }

    public String getEngineName() {
        return engineName;
    }

    public Version getEngineVersion() {
        return engineVersion;
    }

    protected VkInstance getInstance() { // used for surface creation/destruction
        return instance;
    }

    protected VkPhysicalDevice getPhysicalDevice() {
        return physicalDevice; // used for surface info query
    }

    protected VkDevice getDevice() {
        return device;
    }

    protected AvailableQueueFamilyIndices getQueueIndices() {
        return queueIndices;
    }

    protected void addWindow(Window window) {
        windows.add(window);
    }

    protected void removeWindow(Window window) {
        windows.remove(window);
    }

    protected void addShader(Shader shader) {
        shaders.add(shader);
    }

    protected void removeShader(Shader shader) {
        shaders.remove(shader);
    }

    protected void addPipeline(Pipeline pipeline) {
        pipelines.add(pipeline);
    }

    protected void removePipeline(Pipeline pipeline) {
        pipelines.remove(pipeline);
    }

    protected void addImage(Image image) {
        images.remove(image);
    }

    protected void removeImage(Image image) {
        images.remove(image);
    }

    protected void addFramebuffer(Framebuffer framebuffer) {
        framebuffers.add(framebuffer);
    }

    protected void removeFramebuffer(Framebuffer framebuffer) {
        framebuffers.remove(framebuffer);
    }

    //endregion

    //region init and cleanup
    public static void init() {
        if (!glfwInit())
            throw new IllegalStateException("[Fuel3D] ERROR: Unable to initialize GLFW");
        if (!glfwVulkanSupported())
            throw new IllegalStateException("[Fuel3D] ERROR: Cannot find a compatible Vulkan installable client driver (ICD)");
    }

    public static void cleanup() {
        glfwTerminate();
    }
    //endregion

    // If index is -1, it is not available
    protected record AvailableQueueFamilyIndices(int graphics, int present) {
        public boolean allAvailable() {
            return graphics >= 0 && present >= 0;
        }
    }

    public record Version(int major, int minor, int patch) { }

    public static class Settings {
        public String appName = "App", engineName = NAME;
        public Version appVersion = new Version(1, 0 ,0), engineVersion = VERSION;
        public Logger logger = new Logger(new Logger.Settings());
        private boolean validate = false;
        private Debugger debugger = null;

        public void enableDebug(Debugger debugger) {
            validate = true;
            this.debugger = debugger;
        }
    }
}
