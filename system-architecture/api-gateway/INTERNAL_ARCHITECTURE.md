# API Gateway internals

The Gateway is a Spring Cloud Gateway application on WebFlux and Netty. `GatewayConfig` routes authentication and user endpoints to User Service. Log, analyzer, integration, organization and v2 batch routes go to Log Layer. ML, LLM, PostgreSQL and Kafka are internal dependencies accessed by downstream services.

## Authentication path

`JwtAuthenticationWebFilter` removes client-supplied trusted identity headers before selecting the authentication path. `JwtUtil` validates the issuer's supported signature algorithm, claims and expiration. The numeric `user_id` in current tokens is verified as an integral claim. Tenant access still depends on downstream membership and authorization.

On ingestion routes the Gateway verifies the application credential with Log Layer. Log Layer validates the corresponding binding and ingestion scope. Registration and login remain public. The internal service key does not grant application ingestion or operator access.

## Runtime and health

`USER_SERVICE_URL` and `LOG_SERVICE_URL` select upstream hosts. Gateway, User Service and Log Layer share the deployment JWT signing configuration. Internal service authentication uses `INTERNAL_API_KEY`. `GET /actuator/health` exposes minimal readiness status. In containers the browser uses the same origin through Nginx, while local collectors can target the loopback Gateway port directly.
