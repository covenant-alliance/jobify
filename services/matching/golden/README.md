# Golden files

Shared examples of the `jobify.matching.v1` messages, in protobuf's canonical JSON form
(`JsonFormat`: camelCase field names, enum names as strings, int64 as strings).

- Naming: `<case>.request.json` and `<case>.response.json` for the same rpc call.
- Both sides read the same files: `matching-api` (`GoldenContractTest`) parses the requests,
  `matching-service` (`GoldenScoreTest`) checks the scorer reproduces the responses, and Jobify's adapter
  tests (M6) will reuse them.
- A response file holds a hand-checked number, never one copied from the program's own output.
  `score-basic`: skills (1.0 + 0.8 + 0) / 3 = 0.6 x 60 = 36, plus 10 + 8 + 7 + 10 + 5 = 40, total 76 of 100.
- Doubles are compared with a tolerance of 1e-9.
