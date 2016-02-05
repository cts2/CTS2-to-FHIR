Welcome to the CTS2-to-FHIR project.

This is an exploratory project sponsored by the Mayo Clinic to look at ways of utilizing the FHIR build process and tooling stack to produce online documentation for the CTS2 specification.

In particular, this repository includes an Xsd-to-Fhir converter that was developed generically to be able to convert any Xsd into the equivalent FHIR StructureDefinition resources. This tool was then used to convert the CTS2 XSDs to a FHIR Logical Model, which is then built with the FHIR tooling stack into an Implementation Guide.