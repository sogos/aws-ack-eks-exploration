# AWS ACK Experimentation

This is a project that aims to explore AWS ACK (Amazon Controller for Kubernetes) and test its operation.

The goal is to create a Kubernetes cluster with custom AWS resources which are managed by AWS ACK.
(S3 Buckets, RDS Instances, etc.)

The `cdk.json` file tells the CDK Toolkit how to execute your app.

It is a [Maven](https://maven.apache.org/) based project, so you can open this project with any Maven compatible Java IDE to build and run tests.

## Useful commands

 * `mvn package`     compile and run tests
 * `cdk ls`          list all stacks in the app
 * `cdk synth`       emits the synthesized CloudFormation template
 * `cdk deploy`      deploy this stack to your default AWS account/region
 * `cdk diff`        compare deployed stack with current state
 * `cdk docs`        open CDK documentation

Enjoy! and feel free to contribute.
