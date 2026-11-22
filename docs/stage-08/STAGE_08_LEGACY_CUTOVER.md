# Automatic sender cutover

The accepted diagnosis path no longer calls a notification callback. SlackNotificationService and CompositeNotificationService remain unregistered compatibility classes for existing direct tests. The runtime injection graph has one sender for automatic Slack delivery.

The current automatic path has no email delegate. Email configuration remains inert for this feature. The managed Windows launcher forces the new transport OFF and preserves the existing Slack/email disabled settings.
