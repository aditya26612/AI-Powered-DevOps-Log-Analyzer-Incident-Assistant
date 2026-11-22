# Log explorer

Search normalized source messages through a bounded literal match. Use application ID, range, level, source, environment, V2 classification, ML status, trace, correlation or incident filters. The list uses a lightweight projection and stable server-side paging, with 25 or 50 rows shown per page.

Selecting a row preserves the existing log investigation workflow. The source disclosure reads the original event only when expanded. It applies best-effort secret masking, preserves line breaks and renders as text. Reported trace context and correlation IDs can open related rows. Failed scoring is visibly distinct from a V2 normal classification.
