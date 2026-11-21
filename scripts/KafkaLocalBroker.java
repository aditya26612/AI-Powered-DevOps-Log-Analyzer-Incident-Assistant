import java.nio.file.Files;
import java.nio.file.Path;

class KafkaLocalBroker {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Broker configuration and owned stop signal required");
        Path stop = Path.of(args[1]);
        Thread.ofPlatform().daemon().name("devinsight-kafka-stop").start(() -> {
            try {
                while (!Files.exists(stop)) Thread.sleep(250);
                System.exit(0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Class.forName("kafka.Kafka").getMethod("main", String[].class).invoke(null, (Object) new String[]{args[0]});
    }
}
