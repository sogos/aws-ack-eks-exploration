package net.in.devops.ack.tools;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.eks.Cluster;
import software.amazon.awscdk.services.eks.HelmChart;

public class ArgoCd {

    public ArgoCd(Stack stack, Cluster cluster) {

        HelmChart.Builder.create(stack, "argocd")
                .cluster(cluster)
                .chart("argo-cd")
                .repository("https://argoproj.github.io/argo-helm")
                .version("5.28.2")
                .namespace("argocd")
                .createNamespace(true)
                .build();
    }
}
