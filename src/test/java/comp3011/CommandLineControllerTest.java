package comp3011;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.Test;

public class CommandLineControllerTest {

    @Test
    public void parsesLongOptionsAndVideoFile() {
        CommandLineController controller = new CommandLineController(new String[] {
                "--audio", "--maximise", "pom.xml"
        });

        assertTrue(controller.isAudioRequested());
        assertTrue(controller.isMaximiseRequested());
        assertEquals(new File("pom.xml"), controller.getVideoFile());
        assertTrue(controller.shouldLaunchApplication());
    }

    @Test
    public void parsesStackedOptionsInOrderAndKeepsRepetitions() {
        CommandLineController controller = new CommandLineController(new String[] {
                "-nssnf", "pom.xml"
        });

        assertEquals(List.of("n", "s", "s", "n", "f"),
                controller.getFrameProcessorOptions());
    }

    @Test
    public void createsProcessorsInCommandLineOrder() {
        CommandLineController controller = new CommandLineController(new String[] {
                "-s", "-f", "-w", "pom.xml"
        });

        List<FrameProcessor> processors = controller.createFrameProcessors();

        assertEquals(3, processors.size());
        assertEquals(FrameScratcher.class, processors.get(0).getClass());
        assertEquals(FrameFlickerer.class, processors.get(1).getClass());
        assertEquals(FrameBlackAndWhiter.class, processors.get(2).getClass());
    }

    @Test
    public void rejectsUnknownOption() {
        CommandLineController controller = new CommandLineController(new String[] {
                "--unknown", "pom.xml"
        });

        assertFalse(controller.shouldLaunchApplication());
        assertEquals("Unknown option: --unknown", controller.getErrorMessage());
    }

    @Test
    public void rejectsMissingVideoFile() {
        CommandLineController controller = new CommandLineController(new String[] {
                "--audio"
        });

        assertFalse(controller.shouldLaunchApplication());
        assertEquals("No video file specified.", controller.getErrorMessage());
    }
}
