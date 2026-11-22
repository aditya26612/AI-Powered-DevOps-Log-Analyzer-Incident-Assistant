# Notification policy

Policy version 1 creates work only at a new automatic diagnosis success when a notification transport is selected. OFF creates no history or backlog. A selected transport with no configured destination, disabled notifications, a resolved incident, a muted source signal, or insufficient severity records SKIPPED.

Existing alert-on-error and alert-on-anomaly settings determine source eligibility. Owners and admins can change the minimum medium, high, or critical severity and disable delivery for an application. Failed diagnostics and manual previews create no notification. The original incident policy and model threshold stay in effect.
