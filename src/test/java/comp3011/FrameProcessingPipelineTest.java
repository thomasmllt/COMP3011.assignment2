package comp3011;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.bytedeco.javacv.Frame;
import org.junit.Test;

public class FrameProcessingPipelineTest {
    @Test
    public void sendsEachFrameThroughEveryStage() throws Exception {
        List<Integer> firstStageFrames = new ArrayList<>();
        List<Integer> secondStageFrames = new ArrayList<>();
        FrameProcessor first = recordingProcessor(firstStageFrames);
        FrameProcessor second = recordingProcessor(secondStageFrames);

        try (FrameProcessingPipeline pipeline = new FrameProcessingPipeline(
                List.of(first, second), testVideoInfo(), 2)) {
            pipeline.submit(new Frame(), new InfoFrame(7, 100));
            FrameProcessingPipeline.ProcessedFrame result = pipeline.take();

            assertTrue(result.succeeded());
            assertEquals(7, result.info().frameNumber());
            assertEquals(List.of(7), firstStageFrames);
            assertEquals(List.of(7), secondStageFrames);
        }
    }

    @Test
    public void supportsAnEmptyProcessorList() throws Exception {
        try (FrameProcessingPipeline pipeline = new FrameProcessingPipeline(
                List.of(), testVideoInfo(), 1)) {
            pipeline.submit(new Frame(), new InfoFrame(3, 50));
            assertEquals(3, pipeline.take().info().frameNumber());
        }
    }

    private FrameProcessor recordingProcessor(List<Integer> frameNumbers) {
        return new FrameProcessor() {
            @Override
            public void process(Frame frame, InfoFrame info) {
                frameNumbers.add(info.frameNumber());
            }
        };
    }

    private InfoVideo testVideoInfo() {
        return new InfoVideo("test", 10, 1, 1, Frame.DEPTH_UBYTE, 1, 1, 24, 24, 41667, 0);
    }
}
