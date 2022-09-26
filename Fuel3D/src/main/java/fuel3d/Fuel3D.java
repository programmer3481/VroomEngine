package fuel3d;

import org.joml.Vector2i;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.KHRWin32Surface.VK_KHR_WIN32_SURFACE_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRWin32Surface.vkGetPhysicalDeviceWin32PresentationSupportKHR;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.glfw.GLFWVulkan.*;
import static fuel3d.Logger.MessageType;

public class Fuel3D {
    private final VkInstance instance;
    private VkPhysicalDevice physicalDevice = null;
    private VkDevice device = null;
    private AvailableQueueFamilyIndices queueIndices;
    private VkQueue graphicsQueue = null;
    private VkQueue presentQueue = null;
    private List<VkPhysicalDevice> physicalDevices;
    private long debugMessenger;
    private boolean windowsSurface = false;

    private List<String> deviceNameList; // TODO: support switching gpu
    private final Debugger debugger;
    private final Logger logger;

    private final boolean validate; // 'Debug mode'
    private final String appName, engineName;
    private final Version appVersion, engineVersion;
    private final Window initWindow; // ONLY get right after renderer initialization; CAN BE DESTROYED AT RUNTIME

    private final String[] instanceExtensionList = new String[] { // Empty for now
    };
    private final String[] deviceExtensionList = new String[] {
            VK_KHR_SWAPCHAIN_EXTENSION_NAME
    };

    public static final Version VERSION = new Version(1, 0, 0);
    public static final String NAME = "Fuel3D";

    public Fuel3D(Settings settings) { // initWindow should be null
        validate = settings.validate;
        debugger = settings.debugger;
        logger = settings.logger;
        appName = settings.appName;
        appVersion = settings.appVersion;
        engineName = settings.engineName;
        engineVersion = settings.engineVersion;
        initWindow = new InitWindow(settings.initWindowWidth, settings.initWindowHeight, settings.initWindowTitle, this);

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
                            for (int i = 0; i  < availableLayers.capacity(); i++) {
                                availableLayers.position(i);
                                if (layer.equals(availableLayers.layerNameString())) {
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
            PointerBuffer reqInstanceExtensions = getReqInstanceExtensions(stack);
            chErr(vkEnumerateInstanceExtensionProperties((String)null, ib, null));
            if (ib.get(0) > 0) {
                VkExtensionProperties.Buffer availableInstanceExtensions = VkExtensionProperties.malloc(ib.get(0), stack);
                chErr(vkEnumerateInstanceExtensionProperties((String)null, ib, availableInstanceExtensions));

                boolean allFound = true;
                for (int i = 0; i < reqInstanceExtensions.capacity(); i++) {
                    boolean found = false;
                    for (int j = 0; j < availableInstanceExtensions.capacity(); j++) {
                        availableInstanceExtensions.position(j);
                        if (reqInstanceExtensions.getStringASCII(i).equals(availableInstanceExtensions.extensionNameString())) {
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
                    .apiVersion(VK.getInstanceVersionSupported());
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
            for (int i = 0; i < glfwReqExtensions.capacity(); i++) {
                if (glfwReqExtensions.getStringASCII(i).equals(VK_KHR_WIN32_SURFACE_EXTENSION_NAME)) {
                    windowsSurface = true;
                    logger.log(MessageType.INFO, "Windows platform detected");
                    break;
                }
            }

            //create initWindow surface
            chErr(glfwCreateWindowSurface(instance, initWindow.window, null, lb));
            initWindow.surface = lb.get(0);

            pickPhysicalDevice(initWindow.surface);
            createLogicalDevice(initWindow.surface);
        }
    }

    public void destroy() {
        vkDestroyDevice(device, null);
        if (validate) {
            vkDestroyDebugUtilsMessengerEXT(instance, debugMessenger, null);
        }
        vkDestroyInstance(instance, null);
    }

    private void createLogicalDevice(long testSurface) { // Also sets graphicsQueue
        try (MemoryStack stack = MemoryStack.stackPush()) {
            queueIndices = getQueueFamilyIndices(stack, physicalDevice, testSurface);

            VkDeviceQueueCreateInfo.Buffer deviceQueueInfo = VkDeviceQueueCreateInfo.malloc(queueIndices.graphics == queueIndices.present ? 1 : 2, stack);
            deviceQueueInfo.get(0)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .queueFamilyIndex(queueIndices.graphics)
                    .pQueuePriorities(stack.floats(1.0f));
            if (queueIndices.graphics != queueIndices.present) {
                deviceQueueInfo.get(1)
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

    private void pickPhysicalDevice(long testSurface) {
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

                    if (isDeviceSupported(testDevice, availablePhysicalDevicesProperties.get(i), testSurface)) {
                        physicalDevices.add(testDevice);
                        deviceNameList.add(availablePhysicalDevicesProperties.get(i).deviceNameString());
                    }
                }
                if (physicalDevices.size() > 0){
                    physicalDevice = physicalDevices.get(0); // Use the first one TODO: support switching gpus
                    logger.log(MessageType.INFO, "Using GPU: " + availablePhysicalDevicesProperties.get(0).deviceNameString());
                }
            }
            if (physicalDevice == null) {
                logger.error("Failed to find GPU with vulkan support");
            }
        }
    }

    private boolean isDeviceSupported(VkPhysicalDevice physicalDevice, VkPhysicalDeviceProperties properties, long testSurface) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer ib = stack.mallocInt(1);

            // Check extension support
            boolean extensionsAllFound = true;
            chErr(vkEnumerateDeviceExtensionProperties(physicalDevice, (String)null, ib, null));
            if (ib.get(0) > 0) {
                VkExtensionProperties.Buffer availableDeviceExtensions = VkExtensionProperties.malloc(ib.get(0), stack);
                chErr(vkEnumerateDeviceExtensionProperties(physicalDevice, (String)null, ib, availableDeviceExtensions));

                for (String extension : deviceExtensionList) {
                    boolean found = false;
                    for (int i = 0; i < availableDeviceExtensions.capacity(); i++) {
                        availableDeviceExtensions.position(i);
                        if (extension.equals(availableDeviceExtensions.extensionNameString())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        extensionsAllFound = false;
                        logger.log(MessageType.INFO,
                                "Cannot find instance extension " + extension +
                                " on device " + properties.deviceNameString() + ", skipping");
                        break;
                    }
                }
            }

            AvailableQueueFamilyIndices indices = getQueueFamilyIndices(stack, physicalDevice, testSurface);

            return indices.allAvailable() && extensionsAllFound;
        }
    }

    private AvailableQueueFamilyIndices getQueueFamilyIndices(MemoryStack stack, VkPhysicalDevice physicalDevice, long testSurface) {
        IntBuffer ib = stack.mallocInt(1);

        int graphicsQueue = -1;
        int presentQueue = -1;

        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, ib, null);
        if (ib.get(0) > 0) {
            VkQueueFamilyProperties.Buffer availableQueueFamilies = VkQueueFamilyProperties.malloc(ib.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, ib, availableQueueFamilies);

            for (int i = 0; i < availableQueueFamilies.capacity(); i++) {
                if ((availableQueueFamilies.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                    graphicsQueue = i;
                }

                chErr(vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice, i, testSurface, ib));
                if (ib.get(0) == VK_TRUE) {
                    if (!windowsSurface || vkGetPhysicalDeviceWin32PresentationSupportKHR(physicalDevice, i)) {
                        presentQueue = i;
                    }
                }

                if (graphicsQueue >= 0 && presentQueue >= 0) {
                    break;
                }
            }
        }

        if (graphicsQueue < 0 || presentQueue < 0) {
            logger.error("Could not find suitable QueueFamily indices");
        }

        return new AvailableQueueFamilyIndices(graphicsQueue, presentQueue);
    }

    private PointerBuffer getReqInstanceExtensions(MemoryStack stack) {
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


    //region getters and setters
    public List<String> getDeviceNames() {
        return deviceNameList;
    }

    protected Logger getLogger() {
        return logger;
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

    protected VkInstance getInstance() {
        return instance;
    }

    protected VkPhysicalDevice getPhysicalDevice() {
        return physicalDevice;
    }

    protected AvailableQueueFamilyIndices getQueueIndices() {
        return queueIndices;
    }

    public Window getInitWindow() { // ONLY get right after renderer initialization; CAN BE DESTROYED AT RUNTIME
        return initWindow;
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

    private static class InitWindow extends Window { // First window to be created('main' window), surface not initialized, used for surface support checking.
        public InitWindow(int width, int height, String title, Fuel3D renderer) {
            this.size = new Vector2i(width, height);
            this.title = title;
            this.logger = renderer.getLogger();
            this.renderer = renderer;

            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
            glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE); //TODO: Window resize support

            window = glfwCreateWindow(size.x, size.y, title, NULL, NULL);
            if (window == NULL)
                logger.error("[Fuel3D] ERROR: Window creation failed!");
        }
    }

    // If index is -1, it is not available
    protected record AvailableQueueFamilyIndices(int graphics, int present) {
        public boolean allAvailable() {
            return graphics >= 0;
        }
    }

    public record Version(int major, int minor, int patch) { }

    public static class Settings {
        public String appName = "App", engineName = "Fuel3D";
        public Version appVersion = new Version(1, 0 ,0), engineVersion = VERSION;
        public Logger logger = new Logger(new Logger.Settings());
        private boolean validate = false;
        private Debugger debugger = null;
        public int initWindowWidth = 720, initWindowHeight = 480;
        public String initWindowTitle = "App";

        public void enableDebug(Debugger debugger) {
            validate = true;
            this.debugger = debugger;
        }
    }
}
