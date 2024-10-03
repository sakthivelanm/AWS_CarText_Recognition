package cs643Project1;

import software.amazon.awssdk.regions.Region;
// s3
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.model.S3Object;
// rekognition
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;
// sqs
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
// java utils
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.HashMap;

public class carRecognition {
    // bucket and queue name
    private static final String bucketName = "cs643-njit-project1";
    private static final String queueName = "carRecognitionQueue.fifo";

    public static void main(String[] args) {
        // s3, rekognition, sqs clients
        S3Client s3 = S3Client.builder().region(Region.US_EAST_1).build();
        RekognitionClient rekognition = RekognitionClient.builder().region(Region.US_EAST_1).build();
        SqsClient sqs = SqsClient.builder().region(Region.US_EAST_1).build();

        // create sqs if it doesn't exist or find it
        String queueUrl = getOrCreateQueue(sqs, queueName);
        // car recognition
        processImages(s3, rekognition, sqs, queueUrl);
    }

    public static String getOrCreateQueue(SqsClient sqs, String queueName) {
        try {
            // try to find existing queue
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

                // get queue url
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

    public static void processImages(S3Client s3, RekognitionClient rekognition, SqsClient sqs, String queueUrl) {
        // pull images from s3 bucket
        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build();
        ListObjectsV2Response listResponse = s3.listObjectsV2(listRequest);

        // process image by image
        for (S3Object object : listResponse.contents()) {
            String key = object.key();
            System.out.println("Processing image: " + key);

            try {
                // build image object from s3
                Image s3Image = Image.builder()
                        .s3Object(software.amazon.awssdk.services.rekognition.model.S3Object.builder()
                                .bucket(bucketName)
                                .name(key)
                                .build())
                        .build();

                // send s3 image to rekognition to detect labels
                DetectLabelsRequest detectLabelsRequest = DetectLabelsRequest.builder()
                        .image(s3Image)
                        .minConfidence(80F)  // Confidence threshold
                        .build();
                DetectLabelsResponse detectLabelsResult = rekognition.detectLabels(detectLabelsRequest);
                List<Label> labels = detectLabelsResult.labels();

                // process labels for each image
                for (Label label : labels) {
                    // check for 'Car' label greater than 0.8
                    if (label.name().equalsIgnoreCase("Car") && label.confidence() > 80) {
                        // send index of image to sqs
                        System.out.println("Car detected with high confidence.");
                        sendMessageToSQS(sqs, queueUrl, key);
                    }
                }
                System.out.println();
            } catch (RekognitionException e) {
                System.err.println("Failed to process image " + key + ": " + e.awsErrorDetails().errorMessage());
            }
        }
        // after processing all images, send end-signal to sqs indicating that processing is complete
        sendEndOfProcessingSignal(sqs, queueUrl);
    }

    public static void sendMessageToSQS(SqsClient sqs, String queueUrl, String imageKey) {
        // send message to sqs
        SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(imageKey)
                .messageGroupId("cars")
                .messageDeduplicationId(imageKey)
                .build();
        sqs.sendMessage(sendMsgRequest);
        System.out.println("Message sent to SQS: " + imageKey);
    }

    public static void sendEndOfProcessingSignal(SqsClient sqs, String queueUrl) {
        // send end-signal to sqs
        SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody("-1") // send -1 to indicate no more images will be processed
                .messageGroupId("cars")
                .messageDeduplicationId("end-signal")
                .build();
        sqs.sendMessage(sendMsgRequest);
        System.out.println("Sent end-of-processing signal to SQS: -1");
    }

}
