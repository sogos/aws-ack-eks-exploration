package net.in.devops.ack.tools;

import software.amazon.awscdk.CfnJson;
import software.amazon.awscdk.CfnJsonProps;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.eks.Cluster;
import software.amazon.awscdk.services.eks.HelmChart;
import software.amazon.awscdk.services.eks.ServiceAccount;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.WebIdentityPrincipal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FluentBit {

    private final String FLUENTBIT_SERVICE_ACCOUNT_NAME = "aws-for-fluent-bit-sa";
    private final String KUBE_SYSTEM_NAMESPACE = "kube-system";

    public FluentBit(Stack stack, Cluster cluster) {


        CfnJson AssumePolicyConditions = new CfnJson(stack, "aws-fluentbit-policy-conditions-json", CfnJsonProps.builder()
                .value(Map.of(
                        "oidc.eks.%s.amazonaws.com/id/%s:sub".formatted(stack.getRegion(), Fn.split("/", cluster.getClusterOpenIdConnectIssuerUrl(), 5).get(4)),
                        "system:serviceaccount:%s:%s".formatted(KUBE_SYSTEM_NAMESPACE, FLUENTBIT_SERVICE_ACCOUNT_NAME),

                        "oidc.eks.%s.amazonaws.com/id/%s:aud".formatted(stack.getRegion(), Fn.split("/", cluster.getClusterOpenIdConnectIssuerUrl(), 5).get(4)),
                        "sts.amazonaws.com"))
                .build());

        Role role = Role.Builder.create(stack, "aws-fluentbit-policy-role")
                .roleName("cdk-fluentbit-role")
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
                        ManagedPolicy.fromAwsManagedPolicyName("CloudWatchAgentServerPolicy")
                ))
                .build();

        ServiceAccount serviceAccount = ServiceAccount.Builder.create(stack, "aws-for-fluent-bit-sa")
                .name(FLUENTBIT_SERVICE_ACCOUNT_NAME)
                .namespace(KUBE_SYSTEM_NAMESPACE)
                .annotations(Map.of(
                        "eks.amazonaws.com/role-arn", role.getRoleArn()
                ))
                .cluster(cluster)
                .build();

        Map<String, Object> values = new HashMap<>();
        values.put("cloudWatchLogs", Map.of(
                "region", stack.getRegion(),
                "logRetentionDays", 7
        ));
        values.put("aws", Map.of(
                "region", stack.getRegion()
        ));
        values.put("serviceAccount", Map.of(
                        "create", false,
                        "name", serviceAccount.getServiceAccountName()
                )
        );

        values.put("tolerations", List.of(
                Map.of(
                        "key", "batch.amazonaws.com/batch-node",
                        "operator", "Exists"

                )
        ));


        HelmChart.Builder.create(stack, "fluentbit-helm-chart")
                .cluster(cluster)
                .chart("aws-for-fluent-bit")
                .repository("https://aws.github.io/eks-charts")
                .version("0.1.24")
                .namespace("kube-system")
                .createNamespace(false)
                .values(values)
                .build();


    }
}
