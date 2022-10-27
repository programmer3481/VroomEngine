package fuel3d;


public class Framebuffer {
    private final Fuel3D renderer;
    private final Image image;

    public Framebuffer(Image image, Fuel3D renderer) {
        this.image = image;
        this.renderer = renderer;
    }
}
