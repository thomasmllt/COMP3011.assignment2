package comp3011;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.bytedeco.javacv.Frame;

/**
 * Processes complete frames in parallel and returns them in submission order.
 */
public class FrameProcessingPipeline implements AutoCloseable {
    private final ArrayBlockingQueue<FrameWorkItem> inputQueue;
    private final ArrayBlockingQueue<ProcessedFrame> completedQueue;
    private final Map<Long, ProcessedFrame> outOfOrderResults = new HashMap<>();
    private final List<ExecutorService> workers = new ArrayList<>();
    private long nextSequence;
    private long nextSequenceToTake;

    public FrameProcessingPipeline(
            List<FrameProcessor> processors,
            InfoVideo videoInfo,
            int queueCapacity) throws Exception {
        this(() -> processors, videoInfo, queueCapacity, 1);
    }

    public FrameProcessingPipeline(
            Supplier<List<FrameProcessor>> processorFactory,
            InfoVideo videoInfo,
            int queueCapacity,
            int workerCount) throws Exception {
        if (queueCapacity < 1 || workerCount < 1) {
            throw new IllegalArgumentException("Queue capacity and worker count must be positive");
        }

        inputQueue = new ArrayBlockingQueue<>(queueCapacity);
        completedQueue = new ArrayBlockingQueue<>(queueCapacity);

        for (int index = 0; index < workerCount; index++) {
            List<FrameProcessor> processors = processorFactory.get();
            for (FrameProcessor processor : processors) {
                processor.initialise(videoInfo);
            }

            int workerIndex = index;
            ExecutorService worker = Executors.newSingleThreadExecutor(
                    runnable -> Thread.ofPlatform()
                            .name("frame-worker-" + workerIndex)
                            .unstarted(runnable));
            workers.add(worker);
            worker.submit(() -> runWorker(processors));
        }
    }

    public void submit(Frame frame, InfoFrame info) throws InterruptedException {
        inputQueue.put(new FrameWorkItem(nextSequence++, frame, info));
    }

    public ProcessedFrame take() throws InterruptedException {
        ProcessedFrame result = outOfOrderResults.remove(nextSequenceToTake);
        while (result == null) {
            ProcessedFrame completed = completedQueue.take();
            if (completed.sequence() == nextSequenceToTake) {
                result = completed;
            } else {
                outOfOrderResults.put(completed.sequence(), completed);
            }
        }
        nextSequenceToTake++;
        return result;
    }

    private void runWorker(List<FrameProcessor> processors) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                FrameWorkItem item = inputQueue.take();
                try {
                    for (FrameProcessor processor : processors) {
                        processor.process(item.frame(), item.info());
                    }
                    completedQueue.put(new ProcessedFrame(
                            item.sequence(), item.frame(), item.info(), null));
                } catch (Exception exception) {
                    completedQueue.put(new ProcessedFrame(
                            item.sequence(), null, item.info(), exception));
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        for (ExecutorService worker : workers) {
            worker.shutdownNow();
        }
        for (ExecutorService worker : workers) {
            try {
                worker.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private record FrameWorkItem(long sequence, Frame frame, InfoFrame info) {
    }

    public record ProcessedFrame(
            long sequence,
            Frame frame,
            InfoFrame info,
            Exception error) {
        public boolean succeeded() {
            return error == null;
        }
    }
}
