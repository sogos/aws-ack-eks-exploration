package net.in.devops.ack.tools;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.eks.Cluster;
import software.amazon.awscdk.services.eks.HelmChart;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArgoWorkflows {

    public ArgoWorkflows(Stack stack, Cluster cluster) {

        Map<String, Object> values = new HashMap<>();
        values.put("server", Map.of(
                "extraArgs", List.of(
                        "--auth-mode=server"
                )
        ));

        HelmChart.Builder.create(stack, "argo-workflows-helm-chart")
                .release("argo-workflows")
                .cluster(cluster)
                .chart("argo-workflows")
                .repository("https://argoproj.github.io/argo-helm")
                .version("0.24.1")
                .namespace("argo-workflows")
                .createNamespace(true)
                .values(values)
                .build();
    }
}
