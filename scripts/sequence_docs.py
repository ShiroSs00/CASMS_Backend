#!/usr/bin/env python3
"""Generate source-level API traces and check Spring route/documentation coverage.

Static tracing is not a replacement for semantic review.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src/main/java"
OUT = ROOT / "docs/sequence-diagrams"
HTTP = {"Get": "GET", "Post": "POST", "Put": "PUT", "Patch": "PATCH", "Delete": "DELETE", "Request": "ANY"}
MAP_RE = re.compile(r"@(Get|Post|Put|Patch|Delete|Request)Mapping\s*(?:\((.*?)\))?", re.S)


def annotation_path(args):
    if not args:
        return ""
    m = re.search(r'(?:value\s*=\s*)?"([^"\n]*)"', args)
    return m.group(1) if m else ""


def method_after(text, end):
    # Skip other method annotations (e.g. @ResponseStatus) and capture the declaration.
    tail = text[end:]
    m = re.search(r"(?:\s*@\w+(?:\([^)]*\))?\s*)*\s*(?:public|protected)\s+[^;{}]+?\s+(\w+)\s*\(([^)]*)\)", tail, re.S)
    if not m:
        return None
    declaration = m.group(0)
    body_start = tail.find("{", m.end())
    if body_start >= 0:
        depth = 0
        for i in range(body_start, len(tail)):
            if tail[i] == "{": depth += 1
            elif tail[i] == "}":
                depth -= 1
                if depth == 0:
                    declaration += tail[body_start:i + 1]
                    break
    return m.group(1), m.group(2), declaration


def clean_path(s):
    return re.sub(r"//+", "/", "/" + s).rstrip("/") or "/"


def effective_permission(verb, path, handler_permission):
    if handler_permission != "Not declared on handler; verify SecurityConfig":
        return handler_permission
    if path.startswith("/auth/"):
        return "permitAll (SecurityConfig /auth/**)"
    if verb == "POST" and path == "/booking":
        return "permitAll (SecurityConfig POST /booking)"
    if path in {"/booking/available-cabins", "/booking/3d-available-cabins", "/booking/lookup"}:
        return "permitAll (SecurityConfig public booking matcher)"
    if verb == "GET" and any(path == base or path.startswith(base + "/") for base in (
        "/cruise", "/destinations", "/ports", "/ships", "/itineraries",
        "/cruise-stops", "/decks", "/cabins", "/servicepackage/packages"
    )):
        return "permitAll (SecurityConfig GET matcher)"
    return "authenticated (SecurityConfig anyRequest); no handler authority annotation"


def slug(s):
    s = re.sub(r"\{\*?([^}]+)\}", r"\1", s)
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-") or "root"


def extract_method(text, name):
    match = re.search(r"\b(?:public|protected|private)\s+[^;{}]+?\b" + re.escape(name) + r"\s*\(", text, re.S)
    if not match:
        return ""
    depth = 1
    close = match.end()
    while close < len(text) and depth:
        if text[close] == "(": depth += 1
        elif text[close] == ")": depth -= 1
        close += 1
    if depth:
        return ""
    body_start = text.find("{", close)
    if body_start < 0 or ";" in text[close:body_start]:
        return ""
    start = body_start
    depth = 0
    for i in range(start, len(text)):
        if text[i] == "{": depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return text[start + 1:i]
    return ""


def type_for_import(source, simple_name):
    m = re.search(r"^import\s+([\w.]+\." + re.escape(simple_name) + r");", source, re.M)
    return ROOT / "src/main/java" / (m.group(1).replace(".", "/") + ".java") if m else None


def fields(text):
    return {name: typ for typ, name in re.findall(r"private\s+final\s+([\w<>?, .]+?)\s+(\w+)\s*;", text)}


def branch_summary(body):
    branches = []
    for cond in re.findall(r"\bif\s*\(([^\n{}]{1,130})\)", body):
        cond = re.sub(r"\s+", " ", cond).strip()
        branches.append("if " + cond)
    for kind in ("for", "while", "switch"):
        for cond in re.findall(r"\b" + kind + r"\s*\(([^\n{}]{1,100})\)", body):
            branches.append(kind + " " + re.sub(r"\s+", " ", cond).strip())
    errors = sorted(set(re.findall(r"throw\s+new\s+([\w.]+)", body)))
    return branches, errors


def trace_service(route, controller_source):
    # Trace direct service calls from the handler into its implementation and list its direct collaborator calls.
    controller_body = route["handler_body"]
    controller_fields = fields(controller_source)
    traces, repository_calls, branch_list, error_list = [], [], [], []
    for dep, dep_method in re.findall(r"\b(\w+)\.(\w+)\s*\(", controller_body):
        dep_type = controller_fields.get(dep, "")
        if "Repository" in dep_type:
            repository_calls.append((dep_type, dep_method, "controller"))
    for obj, method in route["calls"]:
        typ = controller_fields.get(obj, "")
        if not typ or not typ.endswith("Service"):
            continue
        service_file = type_for_import(controller_source, typ)
        if not service_file or not service_file.exists():
            traces.append((typ, method, "Implementation source unresolved", False))
            continue
        service_src = service_file.read_text(encoding="utf-8")
        body = extract_method(service_src, method)
        if not body:
            traces.append((typ, method, "Method body unresolved (overload or non-standard declaration)", False))
            continue
        signature = re.search(r"(?:public|protected|private)\s+[^;{}]+?\b" + re.escape(method) + r"\s*\(", service_src)
        prefix = service_src[max(0, signature.start() - 250):signature.start()] if signature else ""
        traces.append((typ, method, "body traced", "@Transactional" in prefix))
        branches, errors = branch_summary(body)
        branch_list.extend(branches)
        error_list.extend(errors)
        service_fields = fields(service_src)
        for dep, dep_method in re.findall(r"\b(\w+)\.(\w+)\s*\(", body):
            dep_type = service_fields.get(dep, "")
            if dep_type.endswith("Repository") or "Repository" in dep_type:
                repository_calls.append((dep_type, dep_method, "service"))
        # Include immediate private/helper calls and summarize their conditions/errors.
        for helper in sorted(set(re.findall(r"\b(?:this\.)?(\w+)\s*\(", body))):
            if helper in {method, "if", "for", "while", "switch", "catch", "return", "new"}:
                continue
            helper_body = extract_method(service_src, helper)
            if helper_body:
                hb, he = branch_summary(helper_body)
                branch_list.extend(hb)
                error_list.extend(he)
    return traces, sorted(set(repository_calls)), sorted(set(branch_list)), sorted(set(error_list))


def main():
    routes = []
    for file in sorted(SRC.rglob("*Controller.java")):
        text = file.read_text(encoding="utf-8")
        class_name = re.search(r"\bclass\s+(\w+)", text)
        if not class_name:
            continue
        class_mapping = ""
        for mm in MAP_RE.finditer(text[:class_name.start()]):
            if mm.group(1) == "Request":
                class_mapping = annotation_path(mm.group(2))
        for mm in MAP_RE.finditer(text[class_name.end():]):
            mapping_start = class_name.end() + mm.start()
            if re.search(r"\bclass\s+\w+", text[class_name.end():mapping_start]):
                break
            info = method_after(text, class_name.end() + mm.end())
            if not info:
                continue
            method, params, declaration = info
            route_path = clean_path(class_mapping + "/" + annotation_path(mm.group(2)))
            previous_method_end = text.rfind("}", 0, mapping_start)
            perm = re.search(r"@PreAuthorize\s*\(\s*\"([^\"]+)\"", text[previous_method_end + 1:mapping_start])
            permission = effective_permission(HTTP[mm.group(1)], route_path, perm.group(1) if perm else "Not declared on handler; verify SecurityConfig")
            calls = sorted(set(re.findall(r"\b(\w+)\.(\w+)\s*\(", declaration)))
            service_fields = fields(text)
            service_calls = [(obj, name) for obj, name in calls if service_fields.get(obj, "").endswith("Service")]
            svc = ", ".join(f"{obj}.{name}()" for obj, name in service_calls) or "Inline handler logic / no direct service call identified"
            module = file.relative_to(SRC).parts[2]
            handler_body = extract_method(text, method)
            routes.append(dict(module=module, verb=HTTP[mm.group(1)], path=route_path, controller=class_name.group(1), handler=method, params=params, permission=permission, service=svc, calls=calls, handler_body=handler_body, file=file, source=text))

    if not routes:
        print("No mappings found", file=sys.stderr)
        return 1
    if "--check" in sys.argv:
        inventory = OUT / "API-INVENTORY.md"
        recorded = set()
        if inventory.exists():
            for line in inventory.read_text(encoding="utf-8").splitlines():
                # Regex alternatives are escaped as \| by the Markdown generator.
                cells = [c.strip().replace("\\|", "|") for c in re.split(r"(?<!\\)\|", line.strip().strip("|"))]
                if len(cells) >= 8 and cells[1] in HTTP.values() and cells[2].startswith("/"):
                    recorded.add((cells[1], cells[2], cells[3], cells[4]))
        current = {(r["verb"], r["path"], r["controller"], r["handler"]) for r in routes}
        missing = current - recorded
        stale = recorded - current
        broken = [r["diagram"] for r in routes if not (OUT / r["module"] / (slug(r["verb"].lower() + "-" + r["path"]) + ".md")).exists()]
        syntax_errors = []
        for diagram in OUT.rglob("*.md"):
            markdown = diagram.read_text(encoding="utf-8")
            block = re.search(r"```mermaid\s*\n(.*?)\n```", markdown, re.S)
            if not block:
                continue
            source = block.group(1)
            stack = []
            if not source.startswith("sequenceDiagram"):
                syntax_errors.append(f"{diagram.relative_to(OUT)}: missing fenced Mermaid sequenceDiagram")
            for line_no, line in enumerate(source.splitlines(), 1):
                token = line.strip()
                if token.startswith(("alt ", "opt ", "loop ", "par ", "critical ")):
                    stack.append((token.split()[0], line_no))
                elif token == "end":
                    if stack:
                        stack.pop()
                    else:
                        syntax_errors.append(f"{diagram.relative_to(OUT)}:{line_no}: unexpected end")
            syntax_errors.extend(f"{diagram.relative_to(OUT)}:{line_no}: unclosed {kind}" for kind, line_no in stack)
        if missing or stale or broken or syntax_errors:
            print(f"API documentation check failed: {len(missing)} missing routes, {len(stale)} stale routes, {len(broken)} missing diagrams, {len(syntax_errors)} Mermaid structure errors")
            for row in sorted(missing): print("MISSING", row)
            for row in sorted(stale): print("STALE", row)
            for path in broken: print("MISSING DIAGRAM", path)
            for error in syntax_errors: print("MERMAID", error)
            return 1
        print(f"API documentation checks OK: {len(current)} routes, registered diagrams and Mermaid structure. Semantic accuracy still requires human review.")
        return 0
    OUT.mkdir(parents=True, exist_ok=True)
    for r in routes:
        rel_module = r["module"]
        name = slug(r["verb"].lower() + "-" + r["path"])
        target_dir = OUT / rel_module
        target_dir.mkdir(parents=True, exist_ok=True)
        r["diagram"] = f"{rel_module}/{name}.md"
        traces, repo_calls, branches, errors = trace_service(r, r["source"])
        service_lines = "\n".join(f"        Controller->>Service: {obj}.{method}()" for obj, method in r["calls"] if obj not in {"ResponseEntity", "ApiResponse", "HttpStatus", "List", "Set", "Map", "Objects"} and fields(r["source"]).get(obj, "").endswith("Service"))
        if not service_lines:
            service_lines = "        Note over Controller: Handler contains inline logic; no application service invocation detected"
        service_return = "        Service-->>Controller: Domain result" if "Controller->>Service:" in service_lines else ""
        controller_repo_lines = "\n".join(f"        Controller->>Repository: {typ}.{method}()" for typ, method, origin in repo_calls if origin == "controller")
        service_repo_lines = "\n".join(f"            Service->>Repository: {typ}.{method}()" for typ, method, origin in repo_calls if origin == "service")
        repo_lines = "\n".join(x for x in [controller_repo_lines, service_repo_lines] if x)
        if repo_calls:
            repo_block = f'''        opt Repository interactions found in traced handler/service code
{repo_lines}
            Repository->>DB: Database operation via repository
            DB-->>Repository: Result / persistence outcome
            Repository-->>Service: Result (caller may be Controller for inline repository use)
        end'''
        else:
            repo_block = "        Note over Controller,Repository: No direct repository interaction found in traced method body"
        handler_branches, handler_errors = branch_summary(r["handler_body"])
        branches = sorted(set(branches + handler_branches))
        errors = sorted(set(errors + handler_errors))
        branch_lines = "\n".join(f"    Note over Controller,Service: Source branch: {x}" for x in branches[:12])
        error_lines = "\n".join(f"    Note over Service,Client: Source throws {e}; HTTP mapping requires exception-handler review" for e in errors[:8])
        tx_label = "@Transactional present on traced service method" if any(item[3] for item in traces) else "No @Transactional found on traced service method; repository calls may still join an outer transaction"
        content = f'''# {r["verb"]} {r["path"]}

```mermaid
sequenceDiagram
    title {r["verb"]} {r["path"]} — {r["controller"]}.{r["handler"]}
    actor Client
    participant Security as Spring Security / JWT filters
    participant Controller as {r["controller"]}
    participant Service as Resolved application service
    participant Repository as Repository collaborators
    participant DB as Database

    Client->>Security: {r["verb"]} {r["path"]} + request
    alt Authentication or authorization rejected
        Security-->>Client: 401 / 403
    else Request reaches handler
        Security->>Controller: invoke {r["handler"]}(...)
        opt Bean validation when handler parameter has @Valid
            Controller->>Controller: Validate request constraints
        end
{service_lines}
{controller_repo_lines}
{branch_lines}
        Note over Service,Repository: {tx_label}. Static trace covers direct service body and immediate helper branches; deeper call paths may need review.
{repo_block}
{service_return}
        Controller-->>Client: HTTP response (verify DTO and status)
{error_lines}
    end
```
'''
        (target_dir / f"{name}.md").write_text(content, encoding="utf-8")
        old_mmd = target_dir / f"{name}.mmd"
        if old_mmd.exists():
            old_mmd.unlink()

    lines = ["# API Inventory", "", f"Source scan: `{len(routes)}` handler mappings in `{len(set(r['file'] for r in routes))}` controller files. Generated by [`scripts/sequence_docs.py`](../../scripts/sequence_docs.py).", "", "> Status measures static source trace depth, not human semantic approval. Permission records only handler-local `@PreAuthorize`; absent declarations require SecurityConfig and filter review. The trace follows the handler and one service/helper layer, and does not prove complete transitive behavior.", "", "| Module | HTTP Method | Endpoint | Controller | Handler Method | Service Method / Calls | Permission | Sequence Diagram | Status |", "|---|---|---|---|---|---|---|---|---|"]
    for r in sorted(routes, key=lambda x: (x["module"], x["path"], x["verb"])):
        link = f"[{r['diagram']}]({r['diagram']})"
        traces, repo_calls, branches, errors = trace_service(r, r["source"])
        semantic_status = "Static handler/source trace; semantic review pending" if r["handler_body"] and all(item[2] == "body traced" for item in traces) else "Partial source trace; manual review required"
        vals = [r["module"], r["verb"], r["path"], r["controller"], r["handler"], r["service"], r["permission"], link, semantic_status]
        lines.append("| " + " | ".join(v.replace("|", "\\|").replace("\n", " ") for v in vals) + " |")
    (OUT / "API-INVENTORY.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Generated {len(routes)} route records and {len(routes)} diagrams")


if __name__ == "__main__":
    raise SystemExit(main())
