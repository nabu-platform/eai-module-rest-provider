# Artifact: REST service

Fragments:
- `metadata.xml`: repository metadata around the artifact
- `rest-interface.xml`: REST contract and REST-facing behavior
- `pipeline.xml`: Blox pipeline, you MUST NOT edit input or output via the pipeline, only other fields.
- `service.xml`: Blox implementation steps
- `input.xml`: derived request shape, read-only
- `output.xml`: derived response shape, read-only

Load skill `artifact:restInterface` before editing `rest-interface.xml`.
Load skill `artifact:blox` before editing `pipeline.xml` or `service.xml`
The input/output is determined by the rest-interface and can not be edited for a rest service.