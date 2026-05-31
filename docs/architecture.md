# Architecture

Diagrams are stored in `docs/diagrams/` as PlantUML source and exported PNG files.

## Regenerating PNG diagrams

From the repository root, re-render all diagram PNGs after editing any `.puml` source:

```powershell
plantuml -tpng docs\diagrams\*.puml
```

If PlantUML is not installed locally, use Docker:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace plantuml/plantuml -tpng docs/diagrams/*.puml
```

## System context

![JWT demo system context](./diagrams/system-context.png)

Source: [`docs/diagrams/system-context.puml`](./diagrams/system-context.puml)

## Sequence: Authentication (login / refresh / logout)

![Authentication sequence](./diagrams/sequence-auth-lifecycle.png)

Source: [`docs/diagrams/sequence-auth-lifecycle.puml`](./diagrams/sequence-auth-lifecycle.puml)

## Sequence: Protected endpoint authorization (Bearer / DPoP)

![Protected authorization sequence](./diagrams/sequence-protected-authorization.png)

Source: [`docs/diagrams/sequence-protected-authorization.puml`](./diagrams/sequence-protected-authorization.puml)

## Observability pipeline

![Observability pipeline](./diagrams/observability-pipeline.png)

Source: [`docs/diagrams/observability-pipeline.puml`](./diagrams/observability-pipeline.puml)
