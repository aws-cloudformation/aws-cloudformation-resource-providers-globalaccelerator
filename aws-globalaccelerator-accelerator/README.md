# AWS::GlobalAccelerator::Accelerator

#### Building and testing the project
There are a couple options for building and testing the project.

1. "mvn clean package" - this will build clean and build the project.
2. "cfn submit -v --region <desired_region> - This will deploy the resource as a private resource to your aws account.  This may take a couple minutes.
3. "aws cloudformation set-type-default-version --type "RESOURCE" --type-name AWS::GlobalAccelerator::Accelerator --version-id "xxxxxxxxx" - This will set the version that is used when submitted new cloud formation templates against the given resource type.  you can list versions using the aws cloudformation list-type-versions command.

##### Files:

1. aws-globalaccelerator-accelerator.json - This file contains the schema definition for the accelerator resource.  It also drives auto generated classes from rpdk.
2. `inputs/inputs_1_create.json` - This file is used during the contract tests which are run when using the "cfn submit" or "cfn test" commands.
3. `inputs/inputs_1_invalid.json` - This file is used during the contract tests which are run when using the "cfn submit" or "cfn test" commands.
4. `inputs/inputs_1_update.json` - This file is used during the contract tests which are run when using the "cfn submit" or "cfn test" commands.

#### How to test locally
2 ways of testing:
1. sam local invoke
2. Deploy stack in your local (cfn submit and aws cloudformation create-stack)
3. This test suite includes the usage of a BYOIP CIDR block shared with a cross-account attachment. In order to be able to successfully run this test locally, be sure to add your aws account ID to the developer cross account attachment in the 331523066813 account.

Please don't modify files under `target/generated-sources/rpdk`, as they will be
automatically overwritten.
