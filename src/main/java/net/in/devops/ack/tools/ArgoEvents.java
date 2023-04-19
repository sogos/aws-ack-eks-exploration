package net.in.devops.ack.tools;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.eks.Cluster;
import software.amazon.awscdk.services.eks.HelmChart;

public class ArgoEvents {
    public ArgoEvents(Stack stack, Cluster cluster) {
        HelmChart.Builder.create(stack, "argo-events-helm-chart")
                .release("argo-events")
                .cluster(cluster)
                .chart("argo-events")
                .repository("https://argoproj.github.io/argo-helm")
                .version("2.2.0")
                .namespace("argo-events")
                .createNamespace(true)
                .build();
    }
}
