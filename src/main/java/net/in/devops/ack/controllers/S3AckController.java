package net.in.devops.ack.controllers;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.eks.*;
import software.amazon.awscdk.services.iam.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.in.devops.ack.EksAckStack.ACK_SYSTEM_NAMESPACE;

public class S3AckController extends Stack {

    public static final String CDK_ACK_S3_CONTROLLER_SA = "cdk-ack-s3-controller-sa";

    public S3AckController(Stack stack, Cluster cluster, KubernetesManifest ackNamespace) {


        CfnJson AssumePolicyConditions = new CfnJson(stack, "aws-ack-s3-policy-json", CfnJsonProps.builder()
                .value(Map.of(
                        "oidc.eks.%s.amazonaws.com/id/%s:sub" .formatted(stack.getRegion(), Fn.split("/", cluster.getClusterOpenIdConnectIssuerUrl(), 5).get(4)),
                        "system:serviceaccount:%s:%s" .formatted(ACK_SYSTEM_NAMESPACE, CDK_ACK_S3_CONTROLLER_SA),

                        "oidc.eks.%s.amazonaws.com/id/%s:aud" .formatted(stack.getRegion(), Fn.split("/", cluster.getClusterOpenIdConnectIssuerUrl(), 5).get(4)),
                        "sts.amazonaws.com"))
                .build());

        Role role = Role.Builder.create(stack, "aws-ack-s3-policy-role")
                .roleName("cdk-aws-ack-s3-controller-role")
                .assumedBy(
                        /**
                         * WebIdentityPrincipal imply sts:AssumeRoleWithWebIdentity
                         * (vs FederatedIdentity imply only sts:AssumeRole)
                         */
                        new WebIdentityPrincipal("arn:aws:iam::%s:oidc-provider/oidc.eks.%s.amazonaws.com/id/%s"
                                .formatted(stack.getAccount(), stack.getRegion(), Fn.split("/", cluster.getClusterOpenIdConnectIssuerUrl(), 5).get(4)))
                                .withConditions(
                                        Map.of(
                                                "StringEquals", AssumePolicyConditions
                                        )
                                )

                )
                .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("AmazonS3FullAccess")
                ))
                .inlinePolicies(Map.of(
                        "aws-ack-s3-policy", PolicyDocument.Builder.create()
                                .statements(List.of(
                                        PolicyStatement.Builder.create()
                                                .actions(List.of(
                                                        "sts:AssumeRole"
                                                ))
                                                .resources(List.of("arn:aws:iam::*:role/ACK-*")
                                                )
                                                .build()
                                ))
                                .build()
                ))
                .build();


        ServiceAccount serviceAccount = ServiceAccount.Builder.create(stack, "aws-ack-s3-controller-sa")
                .name(CDK_ACK_S3_CONTROLLER_SA)
                .namespace(ACK_SYSTEM_NAMESPACE)
                .annotations(Map.of(
                        "eks.amazonaws.com/role-arn", role.getRoleArn()
                ))
                .cluster(cluster)
                .build();

        Map<String, Object> values = new HashMap<>();
        values.put("aws", Map.of(
                "region", stack.getRegion()
        ));
        values.put("serviceAccount", Map.of(
                "create", false,
                "name", serviceAccount.getServiceAccountName()
        )
        );

        HelmChartProps helmChartProps = HelmChartProps.builder()
                .release("aws-s3-controller")
                .cluster(cluster)
                .chart("aws-s3-controller")
                .createNamespace(false)
                .namespace(ACK_SYSTEM_NAMESPACE)
                .repository("oci://public.ecr.aws/aws-controllers-k8s/s3-chart")
                .version("v1.0.3")
                .values(values)
                .build();

        HelmChart ackS3HelmChart = new HelmChart(stack, "s3-ack-helm-chart", helmChartProps);
        ackS3HelmChart.getNode().addDependency(serviceAccount);

        serviceAccount.getNode().addDependency(ackNamespace);



    }


}
