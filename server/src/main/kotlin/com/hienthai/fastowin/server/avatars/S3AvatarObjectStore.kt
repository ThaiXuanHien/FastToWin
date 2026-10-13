package com.hienthai.fastowin.server.avatars

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3ClientBuilder
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import java.net.URI
import java.time.Duration

/** Official signing/transport implementation. Production endpoint is derived only from a validated account ID. */
class S3AvatarObjectStore internal constructor(private val client: S3Client, private val bucket: String) : AvatarObjectStore {
    override suspend fun put(key: String, bytes: ByteArray, contentType: String): Unit = withContext(Dispatchers.IO) {
        require(bytes.isNotEmpty() && bytes.size <= AvatarImageNormalizer.MAX_BYTES && contentType == "image/jpeg")
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(), RequestBody.fromBytes(bytes))
        Unit
    }

    override suspend fun read(key: String, maximumBytes: Int): ByteArray? = withContext(Dispatchers.IO) {
        require(maximumBytes in 1..AvatarImageNormalizer.MAX_BYTES)
        try {
            val stream = client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())
            try {
                val response = stream.response()
                check(response.contentType() == "image/jpeg") { "Invalid avatar object content type" }
                check((response.contentLength() ?: 0) <= maximumBytes) { "Avatar object exceeds read limit" }
                val bytes = stream.readNBytes(maximumBytes + 1)
                check(bytes.size <= maximumBytes) { "Avatar object exceeds read limit" }
                bytes
            } finally {
                // Abort avoids draining oversized/chunked responses when closing a URL-connection stream.
                stream.abort()
                stream.close()
            }
        } catch (failure: S3Exception) {
            if (failure.statusCode() == 404) null else throw failure
        }
    }

    override suspend fun delete(key: String): Unit = withContext(Dispatchers.IO) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build())
        Unit
    }
    override fun close() = client.close()

    companion object {
        fun open(configuration: R2AvatarConfiguration): S3AvatarObjectStore {
            return S3AvatarObjectStore(clientBuilder(configuration).build(), configuration.bucket)
        }

        internal fun clientBuilder(configuration: R2AvatarConfiguration): S3ClientBuilder = S3Client.builder()
                .endpointOverride(URI("https://${configuration.accountId}.r2.cloudflarestorage.com"))
                .region(Region.of("auto"))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                    configuration.accessKeyId, configuration.secretAccessKey)))
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                    .connectionTimeout(Duration.ofSeconds(2)).socketTimeout(Duration.ofSeconds(5)))
                .overrideConfiguration {
                    it.apiCallTimeout(Duration.ofSeconds(15)).apiCallAttemptTimeout(Duration.ofSeconds(8))
                }

    }
}
