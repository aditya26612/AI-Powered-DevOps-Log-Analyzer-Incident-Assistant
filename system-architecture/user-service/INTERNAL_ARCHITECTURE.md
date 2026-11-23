# User Service internals

User Service owns account registration and login. Its Spring controllers call the account service, which persists users through JPA in the `devinsight` database. The authoritative user ID is numeric. Roles supplied in public signup payloads are ignored and new accounts receive `DEVELOPER`. Organization membership and tenant roles are managed by Log Layer.

## Login and identity

Passwords are hashed with BCrypt. Login verifies credentials and issues a JWT through `JwtService`. Current tokens retain the email-based subject and include signed numeric `user_id`. Other services verify the signature before using the ID. Older tokens without a numeric claim need a fresh login for tenant routes.

## Database and deployment

Flyway V1 establishes the user schema. Normal application startup validates the schema. Run the deployment migration separately with an operator-controlled connection. Configure the database and JWT signing key before startup. The minimal `GET /actuator/health` endpoint supports startup and readiness checks.
