package comp3011;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.bytedeco.javacv.Frame;

/**
 * Runs the configured frame effects as a bounded processing pipeline.
 *
 * <p>Each effect has one worker, so a stateful {@link FrameProcessor} is never
 * called concurrently. Frames can be in different effects at the same time,
 * while each queue limits memory use and applies back-pressure to the reader.</p>
 */
public class FrameProcessingPipeline implements AutoCloseable {
    private final List<ArrayBlockingQueue<FrameWorkItem>> stageQueues;
    private final ArrayBlockingQueue<ProcessedFrame> completedFrames;
    private final List<ExecutorService> stageExecutors = new ArrayList<>();

    public FrameProcessingPipeline(
            List<FrameProcessor> processors,
            InfoVideo videoInfo,
            int queueCapacity) throws Exception {
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("Queue capacity must be positive");
        }

        completedFrames = new ArrayBlockingQueue<>(queueCapacity);
        stageQueues = new ArrayList<>();
        for (int index = 0; index < processors.size(); index++) {
            stageQueues.add(new ArrayBlockingQueue<>(queueCapacity));
        }

        for (int index = 0; index < processors.size(); index++) {
            FrameProcessor processor = processors.get(index);
            processor.initialise(videoInfo);
            ArrayBlockingQueue<FrameWorkItem> inputQueue = stageQueues.get(index);
            ArrayBlockingQueue<FrameWorkItem> outputQueue = index + 1 < stageQueues.size()
                    ? stageQueues.get(index + 1)
                    : null;

            int workerIndex = index;
            ExecutorService executor = Executors.newSingleThreadExecutor(
                    runnable -> Thread.ofPlatform()
                            .name("frame-effect-" + workerIndex)
                            .unstarted(runnable));
            stageExecutors.add(executor);
            executor.submit(() -> runStage(processor, inputQueue, outputQueue));
        }
    }

    /**
     * Adds work to the first stage. This may wait while the bounded queue is full.
     */
    public void submit(Frame frame, InfoFrame info) throws InterruptedException {
        if (stageQueues.isEmpty()) {
            completedFrames.put(new ProcessedFrame(frame, info));
        } else {
            stageQueues.get(0).put(new FrameWorkItem(frame, info));
        }
    }

    public ProcessedFrame take() throws InterruptedException {
        return completedFrames.take();
    }

    private void runStage(
            FrameProcessor processor,
            ArrayBlockingQueue<FrameWorkItem> inputQueue,
            ArrayBlockingQueue<FrameWorkItem> outputQueue) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                FrameWorkItem item = inputQueue.take();
                processor.process(item.frame(), item.info());

                if (outputQueue == null) {
                    completedFrames.put(new ProcessedFrame(item.frame(), item.info()));
                } else {
                    outputQueue.put(item);
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            completedFrames.offer(new ProcessedFrame(exception));
        }
    }

    @Override
    public void close() {
        for (ExecutorService executor : stageExecutors) {
            executor.shutdownNow();
        }
        for (ExecutorService executor : stageExecutors) {
            try {
                executor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private record FrameWorkItem(Frame frame, InfoFrame info) {
    }

    public record ProcessedFrame(Frame frame, InfoFrame info, Exception error) {
        private ProcessedFrame(Frame frame, InfoFrame info) {
            this(frame, info, null);
        }

        private ProcessedFrame(Exception error) {
            this(null, null, error);
        }

        public boolean succeeded() {
            return error == null;
        }
    }
}
