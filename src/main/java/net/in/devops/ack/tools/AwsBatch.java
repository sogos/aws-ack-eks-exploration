package net.in.devops.ack.tools;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.batch.CfnComputeEnvironment;
import software.amazon.awscdk.services.batch.CfnComputeEnvironmentProps;
import software.amazon.awscdk.services.batch.CfnJobQueue;
import software.amazon.awscdk.services.batch.CfnJobQueueProps;
import software.amazon.awscdk.services.eks.AwsAuthMapping;
import software.amazon.awscdk.services.eks.Cluster;
import software.amazon.awscdk.services.iam.CfnInstanceProfile;
import software.amazon.awscdk.services.iam.CfnInstanceProfileProps;
import software.amazon.awscdk.services.iam.Role;

import java.util.List;
import java.util.Map;

public class AwsBatch {

    public AwsBatch(Stack stack, Cluster cluster, Role instanceRole) {
        String batchNamespaceName = cluster.getClusterName() + "-batch-nodes";

        // AWS Batch Integration
        cluster.addManifest("aws-batch-namespace",
                Map.of(
                        "apiVersion", "v1",
                        "kind", "Namespace",
                        "metadata", Map.of("name", batchNamespaceName)
                )
        );

        cluster.addManifest("aws-batch-cluster-role",
                Map.of(
                        "apiVersion", "rbac.authorization.k8s.io/v1",
                        "kind", "ClusterRole",
                        "metadata", Map.of("name", "aws-batch-cluster-role"),
                        "rules", List.of(
                                Map.of(
                                        "apiGroups", List.of(""),
                                        "resources", List.of("namespaces"),
                                        "verbs", List.of("get")
                                ),
                                Map.of(
                                        "apiGroups", List.of(""),
                                        "resources", List.of("nodes"),
                                        "verbs", List.of("list", "watch")
                                ),
                                Map.of(
                                        "apiGroups", List.of(""),
                                        "resources", List.of("pods"),
                                        "verbs", List.of("list", "watch")
                                )

                        )
                )
        );

        cluster.addManifest("aws-batch-cluster-role-binding",
                Map.of(
                        "apiVersion", "rbac.authorization.k8s.io/v1",
                        "kind", "ClusterRoleBinding",
                        "metadata", Map.of("name", "aws-batch-cluster-role-binding"),
                        "subjects", List.of(
                                Map.of(
                                        "kind", "User",
                                        "name", "aws-batch",
                                        "apiGroup", "rbac.authorization.k8s.io"
                                )
                        ),
                        "roleRef", Map.of(
                                "kind", "ClusterRole",
                                "name", "aws-batch-cluster-role",
                                "apiGroup", "rbac.authorization.k8s.io"
                        )
                )
        );

        cluster.addManifest("aws-batch-compute-environment-role",
                Map.of(
                        "apiVersion", "rbac.authorization.k8s.io/v1",
                        "kind", "Role",
                        "metadata", Map.of(
                                "name", "aws-batch-compute-environment-role",
                                "namespace", batchNamespaceName
                        ),
                        "rules", List.of(
                                Map.of(
                                        "apiGroups", List.of(""),
                                        "resources", List.of("pods"),
                                        "verbs", List.of("create", "get", "list", "watch", "delete")
                                )
                        )
                )
        );

        cluster.addManifest("aws-batch-compute-environment-role-binding",
                Map.of(
                        "apiVersion", "rbac.authorization.k8s.io/v1",
                        "kind", "RoleBinding",
                        "metadata", Map.of(
                                "name", "aws-batch-compute-environment-role-binding",
                                "namespace", batchNamespaceName
                        ),
                        "subjects", List.of(
                                Map.of(
                                        "kind", "User",
                                        "name", "aws-batch",
                                        "apiGroup", "rbac.authorization.k8s.io"
                                )
                        ),
                        "roleRef", Map.of(
                                "kind", "Role",
                                "name", "aws-batch-compute-environment-role",
                                "apiGroup", "rbac.authorization.k8s.io"
                        )
                )
        );

        cluster.getAwsAuth().addRoleMapping(
                Role.fromRoleArn(stack,
                        "aws-batch-role",
                        "arn:aws:iam::" + stack.getAccount() + ":role/AWSServiceRoleForBatch"),
                AwsAuthMapping.builder()
                        .groups(List.of())
                        .username("aws-batch")
                        .build()
        );

        CfnInstanceProfile instanceProfile = new CfnInstanceProfile(stack, "BatchInstanceProfile", CfnInstanceProfileProps.builder()
                .instanceProfileName("BatchInstanceProfile")
                .roles(List.of(instanceRole.getRoleName()))
                .build());


        CfnComputeEnvironment batchComputeEnvironment = new CfnComputeEnvironment(stack, "BatchComputeEnvironment", CfnComputeEnvironmentProps.builder()
                .computeEnvironmentName("BatchComputeEnvironment-with-defined-instances-type")
                .computeResources(CfnComputeEnvironment.ComputeResourcesProperty.builder()
                        .type("SPOT")
                        .allocationStrategy("SPOT_CAPACITY_OPTIMIZED")
                        .updateToLatestImageVersion(true)
                        .minvCpus(0)
                        .maxvCpus(10)
                        .securityGroupIds(List.of(cluster.getClusterSecurityGroupId()))
                        .instanceTypes(List.of("m5.large", "m5.xlarge", "m5.2xlarge"))
                        .subnets(List.of(cluster.getVpc().getPrivateSubnets().get(0).getSubnetId()))
                        .instanceRole(instanceProfile.getAttrArn())
                        .build())
                .type("MANAGED")
                .eksConfiguration(CfnComputeEnvironment.EksConfigurationProperty.builder()
                        .eksClusterArn(cluster.getClusterArn())
                        .kubernetesNamespace(batchNamespaceName)
                        .build())
                .build());

        CfnJobQueue batchJobQueue = new CfnJobQueue(stack, "BatchJobQueue", CfnJobQueueProps.builder()
                .jobQueueName("BatchJobQueue-in-eks")
                .priority(1)
                .state("ENABLED")
                .computeEnvironmentOrder(List.of(CfnJobQueue.ComputeEnvironmentOrderProperty.builder()
                        .computeEnvironment(batchComputeEnvironment.getRef())
                        .order(1)
                        .build()))
                .build());
    }
}
