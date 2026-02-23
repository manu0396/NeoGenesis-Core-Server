Integration Readiness

Purpose
- Define stable contracts for future HL7/DICOM adapters without shipping full integrations.

Interfaces
- `IntegrationEventPublisher` for outbound events.
- `ClinicalAdapter` for HL7/DICOM handoff.
- `DeviceCommandAdapter` for device command dispatch.

Outbox Pattern
- Events should be enqueued into the outbox before dispatch.
- Use the existing serverless outbox pipeline for reliable delivery.

Next Steps
- Implement HL7/DICOM adapters as separate services or modules.
