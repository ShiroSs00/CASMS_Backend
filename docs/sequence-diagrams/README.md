# CASMS API Sequence Diagrams

This directory records Spring MVC routes and their request-processing traces. `API-INVENTORY.md` is the endpoint index; module folders group the Mermaid diagrams by Java package under `com.casms`.

## Structure and lookup

- `API-INVENTORY.md`: one row per handler mapping, with method, path, controller, handler, detected direct calls, permission annotation, diagram link and review status.
- `<module>/*.md`: one Mermaid sequence diagram per mapping. Open the Markdown page to see the rendered diagram; the fenced Mermaid source remains editable below the rendering.
- `system-overview/system-sequence.md`: shared HTTP/security/application/database path.
- `VERIFICATION-REPORT.md`: source scan counts, verification scope and known limits.

Search the inventory by URL, verb, controller or handler. Names use lowercase kebab-case derived from method and endpoint. If a path maps to the same filename, disambiguate the names and keep inventory links unique.

## Render Mermaid

GitHub renders Mermaid in Markdown code fences when you open a diagram page. In an editor, use Markdown preview to see the diagram instead of the fenced source. The inventory links directly to these rendered pages.

## Update workflow

After changing a controller mapping, request DTO, validation, security, service/repository logic, transaction, integration or exception behavior:

1. Run `python scripts/sequence_docs.py` to refresh endpoint records and diagram registrations.
2. Review the affected diagram(s) against the implementation, following Controller → Service → Repository → database and any internal/external components. Replace generic placeholders with actual class/method names and meaningful business branches.
3. Update `API-INVENTORY.md` where route, handler, permission or mapping changes. Mark semantic review status accurately.
4. Review related end-to-end diagrams and run `python scripts/sequence_docs.py --check` (route inventory, diagram registration and basic Mermaid block structure).
5. Include code and docs in the same change. CI coverage is structural and does not certify business semantics; reviewers must inspect diagrams for behavior changes.

The generator follows handler bodies and a service method plus immediate helper methods, and extracts direct repository calls, simple branch conditions and thrown exception types. This is a static trace, not a complete semantic proof: indirect calls, complex control flow, query semantics, DTO transformations, exception-to-HTTP mapping, runtime filter behavior and transaction propagation still need human review. The check command validates route coverage and basic Mermaid block structure, but does not run a full Mermaid renderer. Inventory status records this distinction.

## Review checklist

- [ ] Each changed/new/removed mapping is reflected in the inventory and diagram links.
- [ ] Security filter rules and method-level authorization are represented accurately.
- [ ] DTO validation, service calls, repository operations and transaction boundaries match code.
- [ ] Important business failures and successful HTTP response are documented.
- [ ] Mermaid syntax and inventory links are valid.
- [ ] End-to-end flows affected by shared service changes are reviewed.

## Current baseline limits

The refreshed baseline contains 478 mappings and 478 registered diagrams after integrating main and the deck assignment endpoints. Diagrams marked `Deck change source-reviewed 2026-10-29; scoped behavior documented` were rewritten against the affected implementation for deck persistence and manual service assignments. Other generated traces remain pending semantic review. See `VERIFICATION-REPORT.md` for scope and limits.
