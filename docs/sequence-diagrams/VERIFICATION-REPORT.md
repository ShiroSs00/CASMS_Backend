# Sequence diagram source verification report

Date: 2026-10-29

## Results

- **478** handler mappings found across **43** Spring controller classes after main integration and deck assignment API changes.
- **478** endpoint diagrams are registered in the inventory; every link resolves to a Markdown page containing a fenced Mermaid diagram.
- Generated traces were refreshed for the merged source. **25** affected endpoint diagrams were rewritten and reviewed for the deck persistence change, including import transaction/job handling, technical deck UUIDs, manual assignments, read responses, cabin restrictions, deletion foreign keys and passenger-deck limit checks. The other **453** endpoint diagrams remain static traces requiring semantic review.
- **0** permission fields remain unresolved: handler-level `@PreAuthorize` is recorded; routes without it use the matching public `SecurityConfig` matcher or the global authenticated fallback.
- Mermaid structural check passed for the registered diagrams (sequence declaration and balanced `alt`/`opt`/loop blocks).
- `python scripts/sequence_docs.py --check` passed: **478** routes and diagram registrations match source. The checker now handles escaped Markdown pipes in regex route alternatives introduced on main.

## What this verifies

The route, handler, security annotation or global route policy, and the statically discoverable direct call path were compared with the current Java source. The diagrams are useful source traces and expose conditional logic and exception types found at the trace depth.

## Remaining semantic review

Static source analysis cannot establish the complete runtime call graph or prove every business outcome. In particular, indirect/deep helper calls, repository query semantics, DTO field mapping, validation failure responses, security filter behavior and transaction semantics outside the documented deck-change scope still need review. Scoped review labels certify only the changed deck behavior described in those pages, not every transitive behavior of each endpoint. No unreviewed generated stub is marked semantically verified.

This means endpoint coverage and source tracing are complete, while a claim that all 394 business flows are fully semantically verified would exceed the evidence available from this automated pass.
