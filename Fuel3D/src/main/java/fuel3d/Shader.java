package fuel3d;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.util.shaderc.Shaderc.*;

public class Shader {
    private final Fuel3D renderer;
    private final ByteBuffer code;
    private long shader;

    public Shader(ByteBuffer code, Fuel3D renderer) {
        this.renderer = renderer;
        this.code = code;

        createShader();
    }

    public Shader(byte[] code, Fuel3D renderer) {
        this(ByteBuffer.wrap(code), renderer);
    }

    protected void createShader() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer lb = stack.mallocLong(1);

            ByteBuffer codeByteBuf = memAlloc(code.capacity()).put(code);
            codeByteBuf.flip();

            VkShaderModuleCreateInfo shaderModuleInfo = VkShaderModuleCreateInfo.malloc(stack)
                    .sType$Default()
                    .pNext(NULL)
                    .flags(0)
                    .pCode(codeByteBuf);

            renderer.chErr(vkCreateShaderModule(renderer.getDevice(), shaderModuleInfo, null, lb));
            shader = lb.get(0);
        }
    }

    public void destroy() {
        vkDestroyShaderModule(renderer.getDevice(), shader, null);
    }

    protected long getShader() {
        return shader;
    }

    public static Shader fromSPVFile(String path, Fuel3D renderer) throws IOException {
        return fromSPVFile(Paths.get(path), renderer);
    }

    public static Shader fromSPVFile(Path path, Fuel3D renderer) throws IOException {
        return new Shader(Files.readAllBytes(path), renderer);
    }

    public static Shader fromGLSLCode(String code, Fuel3D renderer) {
        long compiler = shaderc_compiler_initialize();
        long options = shaderc_compile_options_initialize();

        long result = shaderc_compile_into_spv(
                compiler,
                code,
                shaderc_glsl_vertex_shader,
                "",
                "main",
                options
        );

        if (shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success
                || shaderc_result_get_length(result) == 0
                || shaderc_result_get_bytes(result) == null) {
            renderer.getLogger().error("Shader compilation error: " + shaderc_result_get_error_message(result));
        }
        ByteBuffer resultByteBuf = shaderc_result_get_bytes(result);

        shaderc_result_release(result);
        shaderc_compile_options_release(options);
        shaderc_compiler_release(compiler);

        return new Shader(resultByteBuf, renderer);
    }

    public static Shader fromGLSLFile(Path path, Fuel3D renderer) throws IOException {
        long compiler = shaderc_compiler_initialize();
        long options = shaderc_compile_options_initialize();

        long result = shaderc_compile_into_spv(
                compiler,
                Files.readString(path),
                shaderc_glsl_vertex_shader,
                path.getFileName().toString(),
                "main",
                options
        );

        if (shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success
                || shaderc_result_get_length(result) == 0
                || shaderc_result_get_bytes(result) == null) {
            renderer.getLogger().error("Shader compilation error: " + shaderc_result_get_error_message(result));
        }
        ByteBuffer resultByteBuf = shaderc_result_get_bytes(result);

        shaderc_result_release(result);
        shaderc_compile_options_release(options);
        shaderc_compiler_release(compiler);

        return new Shader(resultByteBuf, renderer);
    }

    public static Shader fromGLSLFile(String path, Fuel3D renderer) throws IOException {
        return fromGLSLFile(Paths.get(path), renderer);
    }
}
