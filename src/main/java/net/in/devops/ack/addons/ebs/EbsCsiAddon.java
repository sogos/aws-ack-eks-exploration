package net.in.devops.ack.addons.ebs;

import software.amazon.awscdk.CfnJson;
import software.amazon.awscdk.CfnJsonProps;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.eks.CfnAddon;
import software.amazon.awscdk.services.eks.CfnAddonProps;
import software.amazon.awscdk.services.eks.Cluster;
import software.amazon.awscdk.services.iam.*;

import java.util.List;
import java.util.Map;

public class EbsCsiAddon {

    public EbsCsiAddon(Stack stack, Cluster cluster) {


        Policy ebsCsiPolicy = new Policy(stack, "aws-ebs-csi-driver-policy", PolicyProps.builder()
                .policyName("cdk-aws-ebs-csi-driver-policy")
                .statements(
                        List.of(
                                PolicyStatement.Builder.create()
                                        .actions(
                                                List.of(
                                                        "ec2:CreateSnapshot",
                                                        "ec2:AttachVolume",
                                                        "ec2:DetachVolume",
                                                        "ec2:ModifyVolume",
                                                        "ec2:DescribeAvailabilityZones",
                                                        "ec2:DescribeInstances",
                                                        "ec2:DescribeSnapshots",
                                                        "ec2:DescribeTags",
                                                        "ec2:DescribeVolumes",
                                                        "ec2:DescribeVolumesModifications"
                                                )
                                        )
                                        .resources(List.of("*"))
                                        .build(),
                                PolicyStatement.Builder.create()
                                        .actions(
                                                List.of(
                                                        "ec2:CreateTags",
                                                        "ec2:DeleteTags"
                                                ))
                                        .resources(
                                                List.of(
                                                        "arn:%s:ec2:*:*:snapshot/*" .formatted(stack.getPartition()),
                                                        "arn:%s:ec2:*:*:volume/*" .formatted(stack.getPartition())
                                                ))
                                        .build(),
                                PolicyStatement.Builder.create()
                                        .actions(
                                                List.of(
                                                        "ec2:CreateVolume",
                                                        "ec2:DeleteVolume",
                                                        "ec2:DeleteSnapshot"
                                                ))
                                        .resources(
                                                List.of("*"))
                                        .conditions(
                                                Map.of(
                                                        "StringLike", Map.of(
                                                                "aws:RequestTag/ebs.csi.aws.com/cluster", "true"
                                                        )
                                                )
                                        )
                                        .build(),
                                PolicyStatement.Builder.create()
                                        .actions(
                                                List.of(
                                                        "ec2:CreateVolume",
                                                        "ec2:DeleteVolume",
                                                        "ec2:DeleteSnapshot"
                                                ))
                                        .resources(
                                                List.of("*"))
                                        .conditions(
                                                Map.of(
                                                        "StringLike", Map.of(
                                                                "aws:RequestTag/CSIVolumeName", "*"
                                                        )
                                                )
                                        )
                                        .build(),
                                PolicyStatement.Builder.create()
                                        .actions(
                                                List.of(
                                                        "ec2:CreateVolume",
                                                        "ec2:DeleteVolume"

                                                ))
                                        .resources(
                                                List.of("*"))
                                        .conditions(
                                                Map.of(
                                                        "StringLike", Map.of(
                                                                "aws:RequestTag/kubernetes.io/cluster/*", "owned"
                                                        )
                                                )
                                        )
                                        .build()

                        )
                ).build());

        CfnJson ebsCsiPolicyJson = new CfnJson(stack, "aws-ebs-csi-driver-policy-json", CfnJsonProps.builder()
                .value(Map.of(
                        "oidc.eks.%s.amazonaws.com/id/%s:sub" .formatted(stack.getRegion(), Fn.split("/", cluster.getClusterOpenIdConnectIssuerUrl(), 5).get(4)),
                        "system:serviceaccount:kube-system:ebs-csi-controller-sa",

                        "oidc.eks.%s.amazonaws.com/id/%s:aud" .formatted(stack.getRegion(), Fn.split("/", cluster.getClusterOpenIdConnectIssuerUrl(), 5).get(4)),
                        "sts.amazonaws.com"))
                .build());

        Role role = Role.Builder.create(stack, "aws-ebs-csi-driver-role")
                .roleName("cdk-aws-ebs-csi-driver-role")
                .assumedBy(
                        new FederatedPrincipal("arn:aws:iam::%s:oidc-provider/oidc.eks.%s.amazonaws.com/id/%s"
                                .formatted(stack.getAccount(), stack.getRegion(), Fn.split("/", cluster.getClusterOpenIdConnectIssuerUrl(), 5).get(4)))
                                .withConditions(
                                        Map.of(
                                                "StringEquals", ebsCsiPolicyJson
                                        )
                                )
                )
                .build();


        role.attachInlinePolicy(ebsCsiPolicy);


        new CfnAddon(stack, "aws-ebs-csi-driver", CfnAddonProps.builder()
                .addonName("aws-ebs-csi-driver")
                .addonVersion("v1.17.0-eksbuild.1")
                .clusterName(cluster.getClusterName())
                .serviceAccountRoleArn(role.getRoleArn())
                .resolveConflicts("OVERWRITE").build());

    }
}

