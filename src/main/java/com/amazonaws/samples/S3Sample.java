/*
 * Copyright 2010-2013 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.amazonaws.samples;

import java.io.*;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;

@Slf4j
public class S3Sample {

    private static final Logger logger = LoggerFactory.getLogger(S3Sample.class.getName());

    public static void main(String[] args) throws IOException, ConfigurationException {
        /*
         * Create your credentials file at ~/.aws/credentials (C:\Users\USER_NAME\.aws\credentials for Windows users)
         * and save the following lines after replacing the underlined values with your own.
         *
         * [default]
         * aws_access_key_id = YOUR_ACCESS_KEY_ID
         * aws_secret_access_key = YOUR_SECRET_ACCESS_KEY
         */
        AWSCredentials credentials = getCredentials();
        PropertiesConfiguration config = new PropertiesConfiguration("config.properties");
        Regions region = Regions.fromName(config.getString("Region"));
        String bucketName = config.getString("bucketName") + UUID.randomUUID();
        String key = config.getString("key");

        logger.info("===========================================");
        logger.info("Getting Started with Amazon S3");
        logger.info("===========================================\n");

        AmazonS3 s3 = createS3bucket(bucketName, region, credentials);
        logger.info("Bucket name :{}", bucketName);

        /*
         * List the buckets in your account
         */
        logger.info("Listing buckets");
        for (Bucket bucket : s3.listBuckets()) {
            logger.info(" - " + bucket.getName());
        }

        /*
         * Upload an object to your bucket - You can easily upload a file to
         * S3, or upload directly an InputStream if you know the length of
         * the data in the stream. You can also specify your own metadata
         * when uploading to S3, which allows you set a variety of options
         * like content-type and content-encoding, plus additional metadata
         * specific to your applications.
         */
        logger.info("Uploading a new object to S3 from a file\n");
        addItemToBucket(s3, bucketName, key, createSampleFile());
        ObjectListing objectListing = s3.listObjects(new ListObjectsRequest()
                .withBucketName(bucketName));
        for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
            logger.info(" - " + objectSummary.getKey() + "  " +
                    "(size = " + objectSummary.getSize() + ")");
        }
        /*
         * Download an object - When you download an object, you get all of
         * the object's metadata and a stream from which to read the contents.
         * It's important to read the contents of the stream as quickly as
         * possibly since the data is streamed directly from Amazon S3 and your
         * network connection will remain open until you read all the data or
         * close the input stream.
         *
         * GetObjectRequest also supports several other options, including
         * conditional downloading of objects based on modification times,
         * ETags, and selectively downloading a range of an object.
         */
        logger.info("Downloading an object");
        S3Object object = getItemFromBucket(s3, bucketName, key);
        logger.info("Content-Type: " + object.getObjectMetadata().getContentType());
        displayTextInputStream(object.getObjectContent());

        /*
         * List objects in your bucket by prefix - There are many options for
         * listing the objects in your bucket.  Keep in mind that buckets with
         * many objects might truncate their results when listing their objects,
         * so be sure to check if the returned object listing is truncated, and
         * use the AmazonS3.listNextBatchOfObjects(...) operation to retrieve
         * additional results.
         */
        logger.info("Listing objects");
        ObjectListing objectListing1 = s3.listObjects(new ListObjectsRequest()
                .withBucketName(bucketName)
                .withPrefix("My"));
        for (S3ObjectSummary objectSummary : objectListing1.getObjectSummaries()) {
            logger.info(" - " + objectSummary.getKey() + "  " +
                    "(size = " + objectSummary.getSize() + ")");
        }

        logger.info("Deleting an object\n");
        deleteItemFromBucket(s3, bucketName, key);

        /*
         * Delete a bucket - A bucket must be completely empty before it can be
         * deleted, so remember to delete any objects from your buckets before
         * you try to delete them.
         */

        for (Bucket bucket : s3.listBuckets()) {
            logger.info("Deleting" + bucket.getName());
            deleteBucket(bucketName, s3);
        }
    }

    /**
     * Creates a temporary file with text data to demonstrate uploading a file
     * to Amazon S3
     *
     * @return A newly created temporary file with text data.
     * @throws IOException
     */
    private static File createSampleFile() throws IOException {
        File file = File.createTempFile("aws-java-sdk-", ".txt");
        file.deleteOnExit();

        Writer writer = new OutputStreamWriter(new FileOutputStream(file));
        writer.write("abcdefghijklmnopqrstuvwxyz\n");
        writer.write("01234567890112345678901234\n");
        writer.write("!@#$%^&*()-=[]{};':',.<>/?\n");
        writer.write("01234567890112345678901234\n");
        writer.write("abcdefghijklmnopqrstuvwxyz\n");
        writer.close();

        return file;
    }

    /**
     * Displays the contents of the specified input stream as text.
     *
     * @param input The input stream to display as text.
     * @throws IOException
     */
    private static void displayTextInputStream(InputStream input) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        while (true) {
            String line = reader.readLine();
            if (line == null) break;

            logger.info("    " + line);
        }
        logger.info(" ");
    }

    private static AmazonS3 createS3bucket(String bucketName, Regions region, AWSCredentials credentials) {
        AmazonS3 s3 = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(region.getName())
                .withForceGlobalBucketAccessEnabled(false)
                .build();
        try {
            logger.info("Creating bucket " + bucketName + "\n");
            s3.createBucket(bucketName);
        } catch (AmazonServiceException ase) {
            logger.error("Caught an AmazonServiceException, which means your request made it "
                    + "to Amazon S3, but was rejected with an error response for some reason.");
            logger.error("Error Message:    " + ase.getMessage());
            logger.error("HTTP Status Code: " + ase.getStatusCode());
            logger.error("AWS Error Code:   " + ase.getErrorCode());
            logger.error("Error Type:       " + ase.getErrorType());
            logger.error("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            logger.error("Caught an AmazonClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with S3, "
                    + "such as not being able to access the network.");
            logger.error("Error Message: " + ace.getMessage());
        }
        return s3;
    }

    private static boolean addItemToBucket(AmazonS3 s3, String bucketName, String itemName, File file) {
        PutObjectResult result = s3.putObject(new PutObjectRequest(bucketName, itemName, file));
        logger.info("result = {}", result);
        return true;
    }

    /*
     * Delete an object - Unless versioning has been turned on for your bucket,
     * there is no way to undelete an object, so use caution when deleting objects.
     */
    private static boolean deleteItemFromBucket(AmazonS3 s3, String bucketName, String itemName) {
        try {
            s3.deleteObject(bucketName, itemName);
        } catch (AmazonServiceException ase) {
            logger.error("Caught an AmazonServiceException, which means your request made it "
                    + "to Amazon S3, but was rejected with an error response for some reason.");
            logger.error("Error Message:    " + ase.getMessage());
            logger.error("HTTP Status Code: " + ase.getStatusCode());
            logger.error("AWS Error Code:   " + ase.getErrorCode());
            logger.error("Error Type:       " + ase.getErrorType());
            logger.error("Request ID:       " + ase.getRequestId());
        }
        return true;
    }

    private static S3Object getItemFromBucket(AmazonS3 s3, String bucketName, String itemName) {
        return s3.getObject(new GetObjectRequest(bucketName, itemName));
    }

    private static AWSCredentials getCredentials() {
        AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider().getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                            "Please make sure that your credentials file is at the correct " +
                            "location (~/.aws/credentials), and is in valid format.",
                    e);
        }
        return credentials;
    }

    private static void deleteBucket(String bucketName, AmazonS3 s3) throws AmazonClientException {
        logger.info("Deleting bucket " + bucketName + "\n");
        try {
            s3.deleteBucket(bucketName);
        } catch (AmazonClientException ace) {
            logger.error("Caught an AmazonClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with S3, "
                    + "such as not being able to access the network.");
            logger.error("Error Message: " + ace.getMessage());
        }
    }
}
