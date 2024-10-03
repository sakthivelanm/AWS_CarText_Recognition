
package cs643Project1;

import software.amazon.awssdk.regions.Region;
// S3
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
// Rekognition
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;
// SQS
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
// java
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class textRecognition {
    // bucket, queue, and output file name
    private static final String bucketName = "cs643-njit-project1";
    private static final String queueName = "carRecognitionQueue.fifo";
    private static final String outputFilePath = "output.txt";

    public static void main(String[] args) {
        // s3, rekognition, sqs clients
        S3Client s3 = S3Client.builder().region(Region.US_EAST_1).build();
        RekognitionClient rekognition = RekognitionClient.builder().region(Region.US_EAST_1).build();
        SqsClient sqs = SqsClient.builder().region(Region.US_EAST_1).build();

        // create sqs if it doesn't exist or find it
        String queueUrl = getOrCreateQueue(sqs, queueName);

        // boolean to read sqs
        boolean readQueue = true;

        // continuously poll sqs for messages
        while (readQueue) {
            // text recognition
            readQueue = receiveMessages(sqs, queueUrl, s3, rekognition);
        }

        System.out.println("Processed all images in queue");
        System.exit(0);
    }

    public static String getOrCreateQueue(SqsClient sqs, String queueName) {
        try {
            // try to fiend existing queue
            ListQueuesRequest getQueue = ListQueuesRequest.builder()
                    .queueNamePrefix(queueName)
                    .build();
            ListQueuesResponse queueRespone = sqs.listQueues(getQueue);

            if (queueRespone.queueUrls().size() == 0) {
                // queue doesn't exist
                Map<QueueAttributeName, String> attributes = new HashMap<>();

                // fifo queue that doesn't allow duplication (for 5 min)
                attributes.put(QueueAttributeName.FIFO_QUEUE, "true");
                attributes.put(QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "true");

                // create queue
                CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                        .attributes(attributes)
                        .queueName(queueName)
                        .build();

                // get new queue url
                String newQueueUrl = sqs.createQueue(createQueueRequest).queueUrl();
                System.out.println("Created new queue: " + newQueueUrl);
                return newQueueUrl;
            } else {
                // queue exists, get existing queue url
                String existingQueueUrl = queueRespone.queueUrls().get(0);
                System.out.println("Queue already exists: " + existingQueueUrl);
                return existingQueueUrl;
            }
        } catch (SqsException e) {
            System.err.println("Failed to create or retrieve SQS queue: " + e.awsErrorDetails().errorMessage());
        }

        return null;
    }

    public static boolean receiveMessages(SqsClient sqs, String queueUrl, S3Client s3, RekognitionClient rekognition) {
        // get image index from sqs
        ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)  // You can adjust this number as needed
                .waitTimeSeconds(20)  // Long polling
                .build();
        ReceiveMessageResponse response = sqs.receiveMessage(receiveMessageRequest);

        // process image by image
        for (Message message : response.messages()) {
            String imageKey = message.body();
            System.out.println("Received message: " + imageKey);

            // Check for end of processing signal
            if (imageKey.equals("-1")) {
                System.out.println("Received end-of-processing signal. No longer reading the queue.");
                // delete message
                deleteMessage(sqs, queueUrl, message);
                return false;
            }
            // perform text recognition
            performTextRecognition(s3, rekognition, imageKey);
            // delete the message from the queue after processing
            deleteMessage(sqs, queueUrl, message);
        }

        return true;
    }

    public static void performTextRecognition(S3Client s3, RekognitionClient rekognition, String imageKey) {
        try {
            // build image object from s3 file
            Image s3Image = Image.builder()
                    .s3Object(software.amazon.awssdk.services.rekognition.model.S3Object.builder()
                            .bucket(bucketName)
                            .name(imageKey)
                            .build())
                    .build();

            // detect text in the image using rekognition
            DetectTextRequest detectTextRequest = DetectTextRequest.builder()
                    .image(s3Image)
                    .build();
            DetectTextResponse detectTextResponse = rekognition.detectText(detectTextRequest);

            // add image index to output string
            StringBuilder output = new StringBuilder();
            output.append("Index: ").append(imageKey).append("\n");

            // collect rekognition text output and add to file
            for (TextDetection textDetection : detectTextResponse.textDetections()) {
                output.append("Detected text: ").append(textDetection.detectedText()).append("\n");
            }
            writeOutputToFile(output.toString());

        } catch (RekognitionException e) {
            System.err.println("Failed to perform text recognition on image " + imageKey + ": " + e.awsErrorDetails().errorMessage());
        }
    }

    public static void writeOutputToFile(String output) {
        // append rekognition output to file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath, true))) {
            writer.write(output);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Failed to write output to file: " + e.getMessage());
        }
    }

    public static void deleteMessage(SqsClient sqs, String queueUrl, Message message) {
        // delete message from sqs
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build();
        sqs.deleteMessage(deleteRequest);
        System.out.println("Deleted message from queue: " + message.body());
    }
}
