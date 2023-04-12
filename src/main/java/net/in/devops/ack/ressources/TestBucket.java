package net.in.devops.ack.ressources;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.eks.Cluster;
import software.amazon.awscdk.services.eks.KubernetesManifest;

import java.util.Map;

public class TestBucket {

    public TestBucket(Stack stack, Cluster cluster, KubernetesManifest namespace) {

        /**
         * Create a test bucket
         * Warning the bucket name must be unique across all of Amazon S3
         */
        cluster.addManifest("test-bucket", Map.of(
                        "apiVersion", "s3.services.k8s.aws/v1alpha1",
                        "kind", "Bucket",
                        "metadata", Map.of(
                                "name", "devops-in-net-ack-test-bucket"
                        ),
                        "spec", Map.of(
                                "name", "devops-in-net-ack-test-bucket"
                        )
                )
        );
    }
}
