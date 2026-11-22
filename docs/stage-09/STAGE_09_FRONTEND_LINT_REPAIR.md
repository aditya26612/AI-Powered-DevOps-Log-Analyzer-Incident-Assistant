# Frontend lint repair

The Stage 08 baseline reproduced five errors. The auth file exported a hook from the component module. The old dashboard synchronized a navigation message through an effect and retained an unused anomaly count. LogInspector imported two unused icons.

The auth hook now lives in its own module, the shell derives the flash from router state, and dashboard counts come from real summary data. Both unused icon imports were removed. The complete frontend ESLint run passed without disabling rules.
