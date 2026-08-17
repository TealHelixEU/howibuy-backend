# TealHelix Keycloak

This project builds the Docker image for the Keycloak IDM of TealHelix.

> **NOTE/WARNING:** As of the date of this writing, the Docker images are for development purposes only!

## Building


## Configuring


## Recreating the dev Realm

Login as user `admin`/`admin`. (Credentials configured in `tealhelix-docker/tealhelix-docker-keycloak/src/main/docker/Dockerfile`,
`KC_BOOTSTRAP_ADMIN_USERNAME`, `KC_BOOTSTRAP_ADMIN_PASSWORD`).

### Create the Realm

Create a new enabled realm, "tealhelix". Configure:

- Realm Settings:
	- General
		- Display name: "TealHelix"
		- Save
	- Login
		- User registration, Forgot password, Remember me: On
		- Email as username, Login with email: On
		- Verify email: **should be On, TODO**
	- Events: **TODO, for when we implement the user management events**
    - Sessions: Make sure "Offline session settings" -> "Offline Session Idle" is set to something like 30 Days
	- User profile:
		- Row `firstName` -> Edit -> "Required field": No -> Save
		- Row `lastName`, the same

### Create a client for HowiBuy

Create a new client in the Realm:

- General settings:
	- Client type: OpenID Connect
	- Client ID: howibuy
	- Name: HowiBuy
- Capability config:
	- Client authentication: Off
	- Authorization: Off
	- Authentication flow: Check *only* "Standard flow"
	- Require PKCE: On
	- PKCE Method: S256
- Login settings:
	- Valid redirect URIs:
		- `http://localhost:8180/howibuy/` (also specified in `keycloak-auth.sh`)
		- http://localhost:5175/*
		- **TODO**
	- Valid post logout redirect URIs:
		- A plain plus sign: `+`
		- **TODO**
	- Web origins:
		- A plain plus sign: `+`
		- **TODO**
- Save & finish the new client wizard

### Create a client for Claims Buster

Create a new client in the Realm:

- General settings:
	- Client type: OpenID Connect
	- Client ID: claimsbuster
	- Name: Claims Buster
- Capability config:
	- Client authentication: Off
	- Authorization: Off
	- Authentication flow: Check *only* "Standard flow"
- Login settings:
	- Root URL: **TODO**
	- Home URL: **TODO**
	- Valid redirect URIs:
		- `http://localhost:8100/authcallback` (**TODO**: also adjust `keycloak-auth.sh`)
		- **TODO (if we need any more)**
	- Valid post logout redirect URIs:
		- `http://localhost:8100/endsession`
		- **TODO (if we need any more)**
	- Web origins:
		- `http://localhost:8100`
		- **TODO (if we need any more)**
- Save & finish the new client wizard
- Advanced tab, Advanced settings, "Proof Key for Code Exchange Code Challenge Method": S256

### Create the test client

Create a new client in the Realm, for our fictional retailer used for testing and demos:

- General settings:
	- Client type: OpenID Connect
	- Client ID: `lime_fresh`
	- Name: Lime Fresh
- Capability config:
	- Client authentication: On (because this is intended to be a service account)
	- Authorization: Off
	- Authentication flow: Check *only* "Service accounts roles"
- Login settings (leave blank, as long as this is not intended to be used by people)
- Save & finish the new client wizard

In the client details view, go to the "Credentials" tab:

- Client Authenticator: Client Id and Secret
- Client Secret: it is `GrZ4Vd8xWAthuLFOXe1tlYvAtXo8INv1` for the test client

**TODO:** Advanced tab → "Advanced settings", set timeouts.

Now you should be able to use the `keycloak-auth-service.sh` script under `tealhelix-architecture/src/scripts/` to test
the client:

```bash
./keycloak-auth-service.sh -u lime_fresh -p GrZ4Vd8xWAthuLFOXe1tlYvAtXo8INv1
```

**DON'T FORGET** to create the retailer user in the DB, using the actual UUID from the JWT:

```bash
# to find the UUID run the following and keep the `sub` value
./keycloak-auth-service.sh -u lime_fresh -p GrZ4Vd8xWAthuLFOXe1tlYvAtXo8INv1 --decoded
```

```sql
INSERT INTO th_retailer (id, name) VALUES ('...uuid...', 'lime_fresh');
```

### Create some test users

Go to Manage → Users in the left menu, press "Create new user"

- Email verified: On
- General
	- Email: bob@krusty-krab.com
	- First name: Bob
	- Last name: Squarepants
- Create
- Role mapping -> Assign Role -> Filter by realm roles (dropdown, top-left of the popup) -> `offline_access`

The ID is `72fa53e0-50a9-4bc7-b6ae-07ebe1e28a2d`. Go to the "Credentials" tab, "Set password" to `bob`. Make sure
"Temporary" is Off and press "Save".

Test:

```bash
./keycloak-auth.sh -u bob@krusty-krab.com -p bob
```

### Export the configuration

Exporting from the UI does not include users in the export file; you have to use the CLI:

```bash
docker exec -it tealhelix-keycloak-1 bash
# in the container
/opt/keycloak/bin/kc.sh export --dir /opt/keycloak/data/import --users realm_file --realm tealhelix
exit # return to host
# in the host
docker cp tealhelix-keycloak-1:/opt/keycloak/data/import/tealhelix-realm.json tealhelix-docker/tealhelix-docker-keycloak/src/main/docker/
```
