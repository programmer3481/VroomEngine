package fuel3d;



public class Debugger {
    private final String[][] validationLayerSets;

    public Debugger(Settings settings) {
        validationLayerSets = settings.validationLayerSets;
    }

    public String[][] getValidationLayerSets() {
        return validationLayerSets;
    }

    public static class Settings {
        public String[][] validationLayerSets = new String[][] {{ // An array containing names of sets of validation layers to use. The first set that is available will be used.
                "VK_LAYER_KHRONOS_validation"
        }, {
                "VK_LAYER_LUNARG_standard_validation" // Deprecated, alternative set
        }, {
                "VK_LAYER_GOOGLE_threading", //Deprecated, alternative set
                "VK_LAYER_LUNARG_parameter_validation",
                "VK_LAYER_LUNARG_object_tracker",
                "VK_LAYER_LUNARG_core_validation",
                "VK_LAYER_GOOGLE_unique_objects"
        }
        };
    }
}
