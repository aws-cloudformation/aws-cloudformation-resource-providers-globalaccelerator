# AWS::GlobalAccelerator::Accelerator

1. Write the JSON schema describing your resource, `aws-globalaccelerator-accelerator.json`
2. The RPDK will automatically generate the correct resource model from the
   schema whenever the project is built via Maven. You can also do this manually
   with the following command: `cfn generate`
3. Implement your resource handlers

# How to test locally
2 ways of testing:
1. Sam local invoke
2. Deploy stack in your local (cfn submit and aws cfn create-stack)

Please don't modify files under `target/generated-sources/rpdk`, as they will be
automatically overwritten.
