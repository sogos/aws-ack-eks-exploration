package net.in.devops.ack;

import net.in.devops.ack.addons.ebs.EbsCsiAddon;
import net.in.devops.ack.controllers.S3AckController;
import net.in.devops.ack.tools.ArgoCd;
import software.amazon.awscdk.*;
import software.amazon.awscdk.cdk.lambdalayer.kubectl.v25.KubectlV25Layer;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.eks.*;
import software.amazon.awscdk.services.iam.*;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

public class EksAckStack extends Stack {

    public static final String ACK_SYSTEM_NAMESPACE = "ack-system";

    public EksAckStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public EksAckStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // The code that defines your stack goes here
        Vpc vpc = Vpc.Builder.create(this, "VPC")
                .maxAzs(3)
                .build();

        Role masterRole = Role.Builder.create(this, "eks-ack-master-role")
                .assumedBy(new AccountRootPrincipal())
                .build();


        masterRole.addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("AmazonEKSClusterPolicy"));
        masterRole.attachInlinePolicy(
                Policy.Builder.create(this, "eks-ack-master-policy").statements(
                        List.of(
                                PolicyStatement.Builder.create()
                                        .actions(
                                                List.of("eks:DescribeCluster"))
                                        .resources(
                                                List.of("*"))
                                        .build())
                ).build()
        );


        Cluster eksCluster = Cluster.Builder.create(this, "eks-ack")
                .vpc(vpc)
                .clusterName("eks-ack")
                .version(KubernetesVersion.V1_25)
                .endpointAccess(EndpointAccess.PUBLIC_AND_PRIVATE)
                .defaultCapacity(0)
                .defaultCapacityType(DefaultCapacityType.NODEGROUP)
                .mastersRole(masterRole)
                .kubectlLayer(new KubectlV25Layer(this, "kubectl-layer"))
                .build();


        eksCluster.addNodegroupCapacity("eks-ack-arm-ng", NodegroupOptions
                .builder()
                .capacityType(CapacityType.SPOT)
                .instanceTypes(List.of(InstanceType.of(InstanceClass.T4G, InstanceSize.LARGE)))
                .desiredSize(1)
                .diskSize(50)
                .maxSize(3)
                .minSize(1)
                .amiType(NodegroupAmiType.AL2_ARM_64)
                .taints(List.of(
                        TaintSpec.builder().key("arm64").value("true").effect(TaintEffect.NO_SCHEDULE).build()
                        )
                )
                .build());

        eksCluster.addNodegroupCapacity("eks-ack-amd64-ng", NodegroupOptions
                .builder()
                .capacityType(CapacityType.SPOT)
                .instanceTypes(List.of(InstanceType.of(InstanceClass.T3, InstanceSize.LARGE)))
                .desiredSize(1)
                .diskSize(50)
                .maxSize(3)
                .minSize(1)
                .amiType(NodegroupAmiType.AL2_X86_64)
                .build());


        new CfnOutput(this, "Cluster-OpenIdConnect-Issuer", CfnOutputProps.builder()
                .value(Fn.split("/", eksCluster.getClusterOpenIdConnectIssuerUrl(), 5).get(4))
                .build());

        new EbsCsiAddon(this, eksCluster);


        KubernetesManifest ackNamespace = eksCluster.addManifest("ACK_NAMESPACE",
                Map.of(
                        "apiVersion", "v1",
                        "kind", "Namespace",
                        "metadata", Map.of("name", ACK_SYSTEM_NAMESPACE)
                )
        );

        new S3AckController(this, eksCluster, ackNamespace);
        new ArgoCd(this, eksCluster);
    }
}
