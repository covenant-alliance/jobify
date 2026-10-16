# Golden files

Shared examples of the `jobify.matching.v1` messages, in protobuf's canonical JSON form
(`JsonFormat`: camelCase field names, enum names as strings, int64 as strings).

- Naming: `<case>.request.json` and `<case>.response.json` for the same rpc call.
- Both sides read the same files: `matching-api` (`GoldenContractTest`) today, `matching-service`
  (scoring expectations, M3) and Jobify's adapter tests (M6) later.
- A response file holds a real, hand-checked score. `score-basic.response.json` is added in M3, when
  scoring exists; a placeholder is not committed because it would assert a wrong number.
