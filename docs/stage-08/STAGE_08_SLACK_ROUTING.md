# Slack destination routing

The diagnosis owner's immutable tenant and application IDs select the only delivery destination. A second application never causes fan-out, even if both apps have webhooks. If the owner lacks a destination, the work is skipped even when another incident member has a configured webhook.

V9 coordinates independent registrations of the same canonical webhook using a keyed digest. The shared PostgreSQL gate holds one sender lease and honors the same provider cooldown for both registrations. The runtime role cannot read the gate table. Distinct webhook URLs may still target the same physical Slack channel.

Replacing or clearing the webhook increments the revision. Preference updates and application deactivation invalidate previously bound work. The worker verifies the app and incident under locks before committing the dispatch marker. Changes after the marker can no longer recall an in-flight request.
