# AWS::GlobalAccelerator::Accelerator

Building and testing the project
There are a couple options for building and testing the project.

1. "mvn clean package" - this will build clean and build the project.
2. "cfn submit -v --region <desired_region> - This will deploy the resource as a private resource to your aws account.  This may take a couple minutes.
3. "cfn cloudformation set-type-default-version --type "RESOURCE" --type-name AWS::GlobalAccelerator::Accelerator --version-id "xxxxxxxxx" - This will set the version that is used when submitted new cloud formation templates against the given resource type.  you can list versions using the aws cloudformation list-type-versions command.

Files:
1. aws-globalaccelerator-accelerator.json - This file contains the schema definition for the accelerator resource.  It also drives auto generated classes from rpdk.

# How to test locally
2 ways of testing:
1. Sam local invoke
2. Deploy stack in your local (cfn submit and aws cfn create-stack)

Please don't modify files under `target/generated-sources/rpdk`, as they will be
automatically overwritten.
